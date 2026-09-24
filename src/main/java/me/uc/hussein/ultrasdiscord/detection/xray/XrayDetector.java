package me.uc.hussein.ultrasdiscord.detection.xray;

import me.uc.hussein.ultrasdiscord.UltrasDiscord;
import me.uc.hussein.ultrasdiscord.detection.SuspicionLevel;
import me.uc.hussein.ultrasdiscord.model.Detection;
import me.uc.hussein.ultrasdiscord.model.DetectionType;
import me.uc.hussein.ultrasdiscord.model.PlayerData;
import me.uc.hussein.ultrasdiscord.utility.Text;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Multi-factor X-Ray analysis over a rolling window. Ores are discovered dynamically from the Bukkit
 * Material API (nothing hard-coded except optional weight overrides in config.yml).
 * Never punishes: it only produces a suspicion percentage and alerts.
 */
public final class XrayDetector {
    private static final String[] IND = {"rare-rate", "ore-ratio", "rare-share", "reveal-ratio", "find-interval", "vein-hopping"};
    private static final BlockFace[] FACES = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    private static final class Ev {
        final long t;
        final boolean ore;
        final boolean rare;
        final boolean reveal;
        final int x, y, z;

        Ev(long t, boolean ore, boolean rare, boolean reveal, int x, int y, int z) {
            this.t = t;
            this.ore = ore;
            this.rare = rare;
            this.reveal = reveal;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class Session {
        final ArrayDeque<Ev> events = new ArrayDeque<>();
        long lastAlert;
        double lastAlertScore;
    }

    private static final class Result {
        double score;
        int ores, blocks, rares;
        double oresPerMin;
        final double[] ind = new double[6];
        final double[] val = new double[6];
    }

    private final UltrasDiscord plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final LinkedHashMap<Long, Boolean> placed = new LinkedHashMap<>(256, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) {
            return size() > 4000;
        }
    };

    private double[] weightByOrdinal = new double[0];
    private boolean[] trackedByOrdinal = new boolean[0];
    private boolean enabled = true;
    private boolean ignoreCreative = true;
    private boolean hiddenCheck = true;
    private double rareMin = 2.5;
    private double minWindowMinutes = 2.0;
    private double alertMin = 50;
    private double reAlertDelta = 10;
    private long windowMs = 600_000;
    private long cooldownMs = 120_000;
    private int minBlocks = 40;
    private int minOres = 6;
    private int minStrong = 3;
    private final double[] normal = new double[6];
    private final double[] extreme = new double[6];
    private final double[] weight = new double[6];

    public XrayDetector(UltrasDiscord plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("xray.enabled", true);
        ignoreCreative = c.getBoolean("tracking.ignore-creative", true);
        hiddenCheck = c.getBoolean("xray.hidden-ore-check", true);
        rareMin = c.getDouble("xray.rare-min-weight", 2.5);
        minWindowMinutes = Math.max(0.5, c.getDouble("xray.min-window-minutes", 2.0));
        windowMs = Math.max(60, c.getInt("xray.window-seconds", 600)) * 1000L;
        cooldownMs = Math.max(0, c.getInt("xray.alerts.cooldown-seconds", 120)) * 1000L;
        reAlertDelta = c.getDouble("xray.alerts.re-alert-delta", 10);
        alertMin = c.getDouble("xray.alerts.min-score", c.getDouble("xray.thresholds.suspicious", 50));
        minBlocks = Math.max(1, c.getInt("xray.min-blocks", 40));
        minOres = Math.max(1, c.getInt("xray.min-ores", 6));
        minStrong = Math.max(1, c.getInt("xray.min-strong-indicators", 3));
        for (int i = 0; i < IND.length; i++) {
            String p = "xray.indicators." + IND[i] + ".";
            normal[i] = c.getDouble(p + "normal", 0);
            extreme[i] = c.getDouble(p + "extreme", 1);
            weight[i] = Math.max(0, c.getDouble(p + "weight", 0.1));
        }

        Map<Material, Double> configured = new EnumMap<>(Material.class);
        ConfigurationSection ws = c.getConfigurationSection("xray.ore-weights");
        if (ws != null) {
            for (String k : ws.getKeys(false)) {
                Material m = Material.matchMaterial(k);
                if (m == null) {
                    plugin.getLogger().warning("xray.ore-weights: unknown material " + k);
                } else {
                    configured.put(m, ws.getDouble(k));
                }
            }
        }
        double defW = c.getDouble("xray.default-ore-weight", 1.0);
        String mode = c.getString("tracking.mode", "ALL").toUpperCase(Locale.ROOT);
        Set<Material> list = parseMaterials(c.getStringList("tracking.list"));
        Set<Material> excl = parseMaterials(c.getStringList("tracking.exclude"));

        Material[] all = Material.values();
        weightByOrdinal = new double[all.length];
        trackedByOrdinal = new boolean[all.length];
        for (Material m : all) {
            if (m.isLegacy() || !m.isBlock() || m.isAir()) continue;
            double w = configured.containsKey(m) ? configured.get(m) : (isOreName(m) ? defW : 0.0);
            weightByOrdinal[m.ordinal()] = w;
            boolean t;
            switch (mode) {
                case "ORES_ONLY" -> t = w > 0;
                case "LIST" -> t = list.contains(m) || w > 0;
                default -> t = true;
            }
            if (excl.contains(m)) t = false;
            trackedByOrdinal[m.ordinal()] = t;
        }
    }

    private Set<Material> parseMaterials(List<String> names) {
        Set<Material> out = new HashSet<>();
        for (String n : names) {
            Material m = Material.matchMaterial(n);
            if (m != null) out.add(m);
        }
        return out;
    }

    private static boolean isOreName(Material m) {
        String n = m.name();
        return n.endsWith("_ORE") || m == Material.ANCIENT_DEBRIS;
    }

    public double weightOf(Material m) {
        return weightByOrdinal[m.ordinal()];
    }

    public boolean isOre(Material m) {
        return weightOf(m) > 0;
    }

    // ------------------------------------------------------------------ events
    public void onPlace(Block b) {
        if (weightOf(b.getType()) > 0) {
            placed.put(key(b), Boolean.TRUE);
        }
    }

    private static long key(Block b) {
        return b.getBlockKey() ^ ((long) b.getWorld().getUID().hashCode() << 40);
    }

    public void onBreak(Player p, Block block) {
        boolean stats = plugin.cfg().module("statistics");
        boolean analyse = enabled && plugin.cfg().module("xray");
        if (!stats && !analyse) return;
        GameMode gm = p.getGameMode();
        if (gm == GameMode.SPECTATOR || (ignoreCreative && gm == GameMode.CREATIVE)) return;
        Material m = block.getType();
        if (m.isAir() || !trackedByOrdinal[m.ordinal()]) return;

        long now = System.currentTimeMillis();
        PlayerData d = plugin.stats().get(p);
        double w = weightOf(m);
        boolean ore = w > 0;
        if (ore && placed.remove(key(block)) != null) {
            ore = false; // an ore the player placed themselves is just a block
        }
        boolean rare = ore && w >= rareMin;

        d.blocksMined++;
        d.mined.merge(m.name(), 1L, Long::sum);
        d.lastMined.put(m.name(), now);
        if (d.lastBreakAt > 0) {
            long gap = now - d.lastBreakAt;
            if (gap >= 0 && gap <= 60_000L) d.activeMiningMs += gap;
        }
        d.lastBreakAt = now;
        if (ore) {
            d.oresMined++;
            if (rare) d.rareOres++;
            addArea(d, block);
        }
        d.lastUpdated = now;
        plugin.storage().markDirty(d);

        if (!analyse) return;
        Session s = sessions.computeIfAbsent(p.getUniqueId(), k -> new Session());
        boolean reveal = hiddenCheck && revealsHiddenOre(block);
        s.events.addLast(new Ev(now, ore, rare, reveal, block.getX(), block.getY(), block.getZ()));
        prune(s, now);
        if (ore) {
            Result r = compute(s, now);
            d.xraySuspicion = round1(r.score);
            maybeAlert(p, d, s, r, now);
        }
    }

    private void addArea(PlayerData d, Block b) {
        String k = b.getWorld().getName() + "|" + (b.getX() >> 4) + "|" + (b.getZ() >> 4);
        d.areas.merge(k, 1L, Long::sum);
        if (d.areas.size() > 60) {
            String minKey = null;
            long min = Long.MAX_VALUE;
            for (Map.Entry<String, Long> e : d.areas.entrySet()) {
                if (e.getValue() < min && !e.getKey().equals(k)) {
                    min = e.getValue();
                    minKey = e.getKey();
                }
            }
            if (minKey != null) d.areas.remove(minKey);
        }
    }

    /** True when breaking this block uncovered an ore that had no other air/liquid contact (hidden ore). */
    private boolean revealsHiddenOre(Block b) {
        World w = b.getWorld();
        int bx = b.getX(), by = b.getY(), bz = b.getZ();
        for (BlockFace f : FACES) {
            int nx = bx + f.getModX(), ny = by + f.getModY(), nz = bz + f.getModZ();
            if (!loaded(w, nx, ny, nz)) continue;
            Material nm = w.getType(nx, ny, nz);
            if (weightOf(nm) <= 0) continue;
            boolean hidden = true;
            for (BlockFace g : FACES) {
                int ox = nx + g.getModX(), oy = ny + g.getModY(), oz = nz + g.getModZ();
                if (ox == bx && oy == by && oz == bz) continue;
                if (!loaded(w, ox, oy, oz) || !w.getType(ox, oy, oz).isOccluding()) {
                    hidden = false;
                    break;
                }
            }
            if (hidden) return true;
        }
        return false;
    }

    private static boolean loaded(World w, int x, int y, int z) {
        return y >= w.getMinHeight() && y < w.getMaxHeight() && w.isChunkLoaded(x >> 4, z >> 4);
    }

    // ------------------------------------------------------------------ analysis
    private void prune(Session s, long now) {
        long cutoff = now - windowMs;
        while (!s.events.isEmpty() && s.events.peekFirst().t < cutoff) {
            s.events.pollFirst();
        }
    }

    private static double ind(double v, double normal, double extreme) {
        double span = extreme - normal;
        if (span == 0) return 0;
        return Text.clamp01((v - normal) / span);
    }

    private Result compute(Session s, long now) {
        Result r = new Result();
        int blocks = 0, ores = 0, rares = 0, reveals = 0;
        long first = now;
        List<Ev> rareList = new ArrayList<>();
        for (Ev e : s.events) {
            blocks++;
            if (e.t < first) first = e.t;
            if (e.reveal) reveals++;
            if (e.ore) {
                ores++;
                if (e.rare) {
                    rares++;
                    rareList.add(e);
                }
            }
        }
        r.blocks = blocks;
        r.ores = ores;
        r.rares = rares;
        if (blocks == 0) return r;

        double minutes = Math.max(minWindowMinutes, (now - first) / 60000.0);
        r.oresPerMin = ores / minutes;

        r.val[0] = rares / minutes;
        r.val[1] = ores / (double) blocks;
        r.val[2] = ores >= 5 ? rares / (double) ores : 0;
        r.val[3] = reveals / (double) blocks;
        r.ind[0] = ind(r.val[0], normal[0], extreme[0]);
        r.ind[1] = ind(r.val[1], normal[1], extreme[1]);
        r.ind[2] = ind(r.val[2], normal[2], extreme[2]);
        r.ind[3] = ind(r.val[3], normal[3], extreme[3]);

        if (rares >= 4) {
            int from = Math.max(0, rareList.size() - 8);
            double total = 0;
            int n = 0;
            for (int i = from + 1; i < rareList.size(); i++) {
                total += (rareList.get(i).t - rareList.get(i - 1).t) / 1000.0;
                n++;
            }
            double avg = n == 0 ? normal[4] : total / n;
            r.val[4] = avg;
            r.ind[4] = ind(avg, normal[4], extreme[4]); // normal > extreme -> inverse scale
        }
        if (rares >= 3) {
            Set<Long> cells = new HashSet<>();
            for (Ev e : rareList) {
                long cell = ((long) (e.x >> 3) & 0x1FFFFFL) | (((long) (e.z >> 3) & 0x1FFFFFL) << 21) | (((long) ((e.y + 64) >> 3) & 0x3FFL) << 42);
                cells.add(cell);
            }
            r.val[5] = cells.size() / minutes;
            r.ind[5] = ind(r.val[5], normal[5], extreme[5]);
        }

        double sum = 0, wsum = 0;
        int strong = 0;
        for (int i = 0; i < IND.length; i++) {
            sum += weight[i] * r.ind[i];
            wsum += weight[i];
            if (r.ind[i] >= 0.5) strong++;
        }
        double score = wsum <= 0 ? 0 : (sum / wsum) * 100.0;
        double conf = Math.min(1.0, blocks / (double) minBlocks) * Math.min(1.0, ores / (double) minOres);
        score *= conf;
        if (strong < minStrong) {
            score = Math.min(score, SuspicionLevel.suspiciousThreshold() - 1);
        }
        r.score = Math.max(0, Math.min(100, score));
        return r;
    }

    private void maybeAlert(Player p, PlayerData d, Session s, Result r, long now) {
        if (r.score < alertMin) return;
        boolean cooled = now - s.lastAlert >= cooldownMs;
        boolean worse = r.score >= s.lastAlertScore + reAlertDelta;
        if (!cooled && !worse) return;
        s.lastAlert = now;
        s.lastAlertScore = r.score;

        var msg = plugin.messages();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < IND.length; i++) {
            if (r.ind[i] >= 0.5) order.add(i);
        }
        order.sort((a, b) -> Double.compare(r.ind[b], r.ind[a]));
        List<String> reasons = new ArrayList<>();
        for (int i : order) {
            reasons.add(msg.plain("reasons.xray." + IND[i], Text.map("value", fmt(i, r.val[i]))));
        }
        if (reasons.isEmpty()) reasons.add(msg.plain("reasons.xray.generic"));

        long diamond = d.count("DIAMOND_ORE") + d.count("DEEPSLATE_DIAMOND_ORE");
        long emerald = d.count("EMERALD_ORE") + d.count("DEEPSLATE_EMERALD_ORE");
        long debris = d.count("ANCIENT_DEBRIS");
        Map<String, String> extra = Text.map(
                "diamond", String.valueOf(diamond),
                "emerald", String.valueOf(emerald),
                "debris", String.valueOf(debris),
                "ores", String.valueOf(d.oresMined),
                "blocks", String.valueOf(d.blocksMined),
                "rate", Text.num(r.oresPerMin, 1),
                "window-ores", String.valueOf(r.ores),
                "window-blocks", String.valueOf(r.blocks));
        plugin.alerts().raise(p, DetectionType.XRAY, new Detection(round1(r.score), reasons.get(0), reasons, extra));
    }

    private static String fmt(int i, double v) {
        return switch (i) {
            case 1, 2, 3 -> Text.num(v * 100, 0) + "%";
            case 4 -> Text.num(v, 1) + "s";
            default -> Text.num(v, 1) + "/min";
        };
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    // ------------------------------------------------------------------ periodic / reset
    /** Recomputes scores quietly so they decay when the player stops mining. */
    public void tick() {
        if (!enabled || !plugin.cfg().module("xray")) return;
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Session> e : sessions.entrySet()) {
            PlayerData d = plugin.stats().get(e.getKey());
            if (d == null) continue;
            Session s = e.getValue();
            prune(s, now);
            double v = s.events.isEmpty() ? 0 : round1(compute(s, now).score);
            if (Math.abs(v - d.xraySuspicion) >= 0.1) {
                d.xraySuspicion = v;
                plugin.storage().markDirty(d);
            }
        }
        sessions.values().removeIf(s -> s.events.isEmpty());
    }

    public void removeSession(UUID id) {
        sessions.remove(id);
    }

    public void resetSession(UUID id) {
        sessions.remove(id);
    }

    public void resetAllSessions() {
        sessions.clear();
        placed.clear();
    }
}
