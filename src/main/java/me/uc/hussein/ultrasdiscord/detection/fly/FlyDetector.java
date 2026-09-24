package me.uc.hussein.ultrasdiscord.detection.fly;

import me.uc.hussein.ultrasdiscord.UltrasDiscord;
import me.uc.hussein.ultrasdiscord.model.Detection;
import me.uc.hussein.ultrasdiscord.model.DetectionType;
import me.uc.hussein.ultrasdiscord.model.PlayerData;
import me.uc.hussein.ultrasdiscord.utility.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Anti-Fly for SURVIVAL/ADVENTURE only. Combines hover, impossible ascent and air-speed indicators
 * and ignores every legitimate cause of flight: allow-flight, elytra, riptide, vehicles, water,
 * ladders/vines/scaffolding, cobweb, slime/honey/beds, levitation, slow-falling, jump-boost,
 * knockback/explosions/wind-charges (velocity events), teleports, lag (TPS/ping).
 * It only raises alerts, never punishes.
 */
public final class FlyDetector {
    private static final double[] OFFS = {0.0, -0.3, 0.3};

    private static final class S {
        int airMoves;
        double lastGroundY;
        final double[] dy = new double[8];
        int dyIdx;
        int dyCount;
        int fastAir;
        boolean fHover, fAscend, fSpeed;
        double score;
        long lastDecay;
        long graceUntil;
        long bounceUntil;
        long lastAlert;
        long lastExemptLog;
        double lastRise;
        int envX, envY, envZ;
        boolean envExempt;
        boolean envValid;
        final LinkedHashSet<String> reasons = new LinkedHashSet<>();

        void resetAir(double y) {
            airMoves = 0;
            lastGroundY = y;
            dyCount = 0;
            dyIdx = 0;
            fastAir = 0;
            fHover = fAscend = fSpeed = false;
            envValid = false;
        }
    }

    private final UltrasDiscord plugin;
    private final Map<UUID, S> sessions = new HashMap<>();
    private boolean enabled = true;
    private boolean exemptOp = true;
    private boolean logExempt = false;
    private String exemptPerm = "ultras.fly.bypass";
    private double minTps = 16.0;
    private int maxPing = 400;
    private long cooldownMs = 60_000;
    private int hoverMoves = 14;
    private int repeatMoves = 40;
    private double hoverSlack = 0.30;
    private double ascendLeniency = 0.25;
    private double maxAirSpeed = 0.85;
    private double ptsHover = 16, ptsAscend = 24, ptsSpeed = 14;
    private double decay = 3.0;
    private double alertThreshold = 60;
    private volatile boolean lowTps = false;

    public FlyDetector(UltrasDiscord plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("fly.enabled", true);
        exemptOp = c.getBoolean("fly.exempt-op", true);
        logExempt = c.getBoolean("fly.log-exempt", false);
        exemptPerm = c.getString("fly.exempt-permission", "ultras.fly.bypass");
        minTps = c.getDouble("fly.min-tps", 16.0);
        maxPing = c.getInt("fly.max-ping", 400);
        cooldownMs = Math.max(0, c.getInt("fly.alerts.cooldown-seconds", 60)) * 1000L;
        hoverMoves = Math.max(6, c.getInt("fly.hover-air-moves", 14));
        repeatMoves = Math.max(hoverMoves + 4, c.getInt("fly.repeat-flag-moves", 40));
        hoverSlack = c.getDouble("fly.hover-slack", 0.30);
        ascendLeniency = c.getDouble("fly.ascend-leniency", 0.25);
        maxAirSpeed = c.getDouble("fly.max-air-speed", 0.85);
        ptsHover = c.getDouble("fly.points.hover", 16);
        ptsAscend = c.getDouble("fly.points.ascend", 24);
        ptsSpeed = c.getDouble("fly.points.speed", 14);
        decay = c.getDouble("fly.decay-per-second", 3.0);
        alertThreshold = c.getDouble("fly.alerts.threshold", 60);
    }

    // ------------------------------------------------------------------ grace periods
    public void grace(Player p, long ms) {
        if (!enabled) return;
        S s = sessions.computeIfAbsent(p.getUniqueId(), k -> new S());
        s.graceUntil = Math.max(s.graceUntil, System.currentTimeMillis() + ms);
        s.resetAir(p.getLocation().getY());
    }

    private boolean isExempt(Player p) {
        if (exemptOp && p.isOp()) return true;
        return exemptPerm != null && !exemptPerm.isBlank() && p.hasPermission(exemptPerm);
    }

    private static boolean bedrock(Player p) {
        return p.getUniqueId().getMostSignificantBits() == 0L;
    }

    // ------------------------------------------------------------------ movement
    public void onMove(Player p, Location from, Location to) {
        if (!enabled || !plugin.cfg().module("fly")) return;
        if (to.getWorld() == null || from.getWorld() != to.getWorld()) return;
        GameMode gm = p.getGameMode();
        if (gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE) return;
        boolean exempt = isExempt(p);
        if (exempt && !logExempt) return;

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        if (dx == 0 && dy == 0 && dz == 0) return;

        S s = sessions.computeIfAbsent(p.getUniqueId(), k -> new S());
        long now = System.currentTimeMillis();
        decay(s, now);
        double y = to.getY();

        if (now < s.graceUntil || lowTps || p.getAllowFlight() || p.isFlying() || p.isGliding()
                || p.isInsideVehicle() || p.isSwimming() || p.isRiptiding() || p.isDead() || p.isSleeping()) {
            s.resetAir(y);
            return;
        }

        if (grounded(to)) {
            Material below = to.getWorld().getType(to.getBlockX(), (int) Math.floor(y - 0.1), to.getBlockZ());
            if (below == Material.SLIME_BLOCK || Tag.BEDS.isTagged(below)) {
                s.bounceUntil = now + 4000;
            }
            s.resetAir(y);
            return;
        }

        s.airMoves++;
        s.dy[s.dyIdx] = dy;
        s.dyIdx = (s.dyIdx + 1) % s.dy.length;
        if (s.dyCount < s.dy.length) s.dyCount++;

        if (now < s.bounceUntil) return;
        if (p.hasPotionEffect(PotionEffectType.LEVITATION) || p.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            s.resetAir(y);
            return;
        }
        if (envExempt(s, to)) {
            s.resetAir(y);
            return;
        }

        PotionEffect jb = p.getPotionEffect(PotionEffectType.JUMP_BOOST);
        int jl = jb == null ? 0 : jb.getAmplifier() + 1;
        PotionEffect sp = p.getPotionEffect(PotionEffectType.SPEED);
        int sl = sp == null ? 0 : sp.getAmplifier() + 1;
        double leniency = bedrock(p) ? 1.25 : 1.0;

        // repeat flags during sustained flight
        if (s.airMoves > 0 && s.airMoves % repeatMoves == 0) {
            s.fHover = s.fAscend = s.fSpeed = false;
        }

        // 1) impossible ascent
        double rise = y - s.lastGroundY;
        s.lastRise = rise;
        double allowed = (1.30 + 0.60 * jl + 0.15 * jl * jl + ascendLeniency) * leniency;
        double maxDy = (0.85 + 0.15 * jl) * leniency;
        if (!s.fAscend && s.airMoves >= 4 && (rise > allowed || dy > maxDy)) {
            s.fAscend = true;
            flag(p, s, exempt, ptsAscend, "ascend");
        }

        // 2) hover (not falling for a long time)
        if (!s.fHover && s.airMoves >= hoverMoves && s.dyCount >= s.dy.length) {
            double sum = 0;
            for (double v : s.dy) sum += v;
            if (sum > -hoverSlack) {
                s.fHover = true;
                flag(p, s, exempt, ptsHover, "hover");
            }
        }

        // 3) air speed
        double hs = Math.hypot(dx, dz);
        double airMax = (maxAirSpeed + 0.12 * sl) * leniency;
        if (hs > airMax) {
            s.fastAir++;
        } else if (s.fastAir > 0) {
            s.fastAir--;
        }
        if (!s.fSpeed && s.fastAir >= 6) {
            s.fSpeed = true;
            flag(p, s, exempt, ptsSpeed, "speed");
        }
    }

    private void flag(Player p, S s, boolean exempt, double pts, String reasonKey) {
        double mult = 1.0;
        try {
            if (p.getPing() > maxPing) mult = 0.5;
        } catch (RuntimeException ignored) {
            // ping not available
        }
        s.score = Math.min(100.0, s.score + pts * mult);
        s.reasons.add(reasonKey);
        PlayerData d = plugin.stats().get(p);
        d.flySuspicion = Math.round(s.score * 10.0) / 10.0;
        d.lastUpdated = System.currentTimeMillis();
        plugin.storage().markDirty(d);

        long now = System.currentTimeMillis();
        if (s.score < alertThreshold || now - s.lastAlert < cooldownMs) return;
        if (exempt) {
            if (now - s.lastExemptLog > 30_000L) {
                s.lastExemptLog = now;
                plugin.getLogger().info("[Fly-exempt] " + p.getName() + " would be flagged (" + Text.num(s.score, 0) + "%): " + String.join(",", s.reasons));
            }
            return;
        }
        s.lastAlert = now;
        var msg = plugin.messages();
        List<String> reasons = new ArrayList<>();
        for (String r : s.reasons) {
            reasons.add(msg.plain("reasons.fly." + r, Text.map("rise", Text.num(s.lastRise, 2), "moves", String.valueOf(s.airMoves))));
        }
        s.reasons.clear();
        Map<String, String> extra = Text.map("rise", Text.num(s.lastRise, 2), "air-moves", String.valueOf(s.airMoves));
        plugin.alerts().raise(p, DetectionType.FLY, new Detection(Math.round(s.score * 10.0) / 10.0, reasons.get(0), reasons, extra));
    }

    private void decay(S s, long now) {
        if (s.lastDecay == 0) {
            s.lastDecay = now;
            return;
        }
        double dt = (now - s.lastDecay) / 1000.0;
        s.lastDecay = now;
        if (s.score > 0) s.score = Math.max(0, s.score - decay * dt);
    }

    // ------------------------------------------------------------------ world checks
    private boolean grounded(Location l) {
        World w = l.getWorld();
        double x = l.getX(), y = l.getY(), z = l.getZ();
        int by = (int) Math.floor(y - 0.1);
        for (double ox : OFFS) {
            for (double oz : OFFS) {
                int bx = (int) Math.floor(x + ox);
                int bz = (int) Math.floor(z + oz);
                Block b = w.getBlockAt(bx, by, bz);
                if (!b.isPassable()) return true;
            }
        }
        int lower = (int) Math.floor(y - 0.6);
        Material lm = w.getType((int) Math.floor(x), lower, (int) Math.floor(z));
        return Tag.FENCES.isTagged(lm) || Tag.WALLS.isTagged(lm) || Tag.FENCE_GATES.isTagged(lm);
    }

    private boolean envExempt(S s, Location to) {
        int bx = to.getBlockX(), by = to.getBlockY(), bz = to.getBlockZ();
        if (s.envValid && s.envX == bx && s.envY == by && s.envZ == bz) return s.envExempt;
        World w = to.getWorld();
        boolean ex = false;
        outer:
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    Material m = w.getType(bx + dx, by + dy, bz + dz);
                    if (m == Material.WATER || m == Material.LAVA || m == Material.BUBBLE_COLUMN
                            || m == Material.COBWEB || m == Material.POWDER_SNOW || m == Material.HONEY_BLOCK
                            || m == Material.SLIME_BLOCK || m == Material.SWEET_BERRY_BUSH
                            || Tag.CLIMBABLE.isTagged(m)) {
                        ex = true;
                        break outer;
                    }
                }
            }
        }
        s.envX = bx;
        s.envY = by;
        s.envZ = bz;
        s.envExempt = ex;
        s.envValid = true;
        return ex;
    }

    // ------------------------------------------------------------------ periodic / reset
    public void refreshTps() {
        try {
            double[] t = Bukkit.getTPS();
            lowTps = minTps > 0 && t.length > 0 && t[0] < minTps;
        } catch (RuntimeException e) {
            lowTps = false;
        }
    }

    public void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, S> e : sessions.entrySet()) {
            S s = e.getValue();
            decay(s, now);
            PlayerData d = plugin.stats().get(e.getKey());
            if (d != null) {
                double v = Math.round(s.score * 10.0) / 10.0;
                if (Math.abs(v - d.flySuspicion) >= 0.1) {
                    d.flySuspicion = v;
                    plugin.storage().markDirty(d);
                }
            }
        }
    }

    public void remove(UUID id) {
        sessions.remove(id);
    }

    public void reset(UUID id) {
        sessions.remove(id);
    }

    public void resetAll() {
        sessions.clear();
    }
}
