package me.uc.hussein.ultrasdiscord.detection.autoclicker;

import me.uc.hussein.ultrasdiscord.UltrasDiscord;
import me.uc.hussein.ultrasdiscord.detection.SuspicionLevel;
import me.uc.hussein.ultrasdiscord.model.Detection;
import me.uc.hussein.ultrasdiscord.model.DetectionType;
import me.uc.hussein.ultrasdiscord.model.PlayerData;
import me.uc.hussein.ultrasdiscord.utility.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Analyses click timing (server tick resolution): CPS, interval variance (consistency),
 * repeating interval patterns and same-tick bursts. A single indicator never triggers an alert;
 * several must agree. It only alerts, never punishes.
 */
public final class AutoClickerDetector {
    private static final String[] IND = {"cps", "consistency", "repetition", "burst"};
    private static final int CAP = 128;

    private static final class S {
        final int[] t = new int[CAP];
        int n;
        int evalCounter;
        int lastUseTick = -100;
        double score;
        long lastDecay;
        long lastAlert;
        double lastCps, lastCv, lastRep, lastBurst;
        int lastWarnTick;
    }

    private final UltrasDiscord plugin;
    private final Map<UUID, S> sessions = new HashMap<>();
    private boolean enabled = true;
    private boolean ignoreCreative = false;
    private String exemptPerm = "ultras.autoclicker.bypass";
    private int windowTicks = 120;
    private int minSamples = 25;
    private double minCps = 9.0;
    private int minStrong = 2;
    private double decay = 6.0;
    private double threshold = 70;
    private long cooldownMs = 60_000;
    private final double[] normal = new double[4];
    private final double[] extreme = new double[4];
    private final double[] weight = new double[4];

    public AutoClickerDetector(UltrasDiscord plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("autoclicker.enabled", true);
        ignoreCreative = c.getBoolean("autoclicker.ignore-creative", false);
        exemptPerm = c.getString("autoclicker.exempt-permission", "ultras.autoclicker.bypass");
        windowTicks = Math.max(40, c.getInt("autoclicker.window-ticks", 120));
        minSamples = Math.max(10, Math.min(CAP - 1, c.getInt("autoclicker.min-samples", 25)));
        minCps = c.getDouble("autoclicker.min-cps", 9.0);
        minStrong = Math.max(1, c.getInt("autoclicker.min-strong-indicators", 2));
        decay = c.getDouble("autoclicker.decay-per-second", 6.0);
        threshold = c.getDouble("autoclicker.alerts.threshold", 70);
        cooldownMs = Math.max(0, c.getInt("autoclicker.alerts.cooldown-seconds", 60)) * 1000L;
        for (int i = 0; i < IND.length; i++) {
            String p = "autoclicker.indicators." + IND[i] + ".";
            normal[i] = c.getDouble(p + "normal", 0);
            extreme[i] = c.getDouble(p + "extreme", 1);
            weight[i] = Math.max(0, c.getDouble(p + "weight", 0.25));
        }
    }

    /** Right-click / place: the accompanying arm swing must not count as a click. */
    public void markUse(Player p) {
        if (!enabled) return;
        S s = sessions.computeIfAbsent(p.getUniqueId(), k -> new S());
        s.lastUseTick = Bukkit.getCurrentTick();
    }

    public void onClick(Player p) {
        if (!enabled || !plugin.cfg().module("autoclicker")) return;
        GameMode gm = p.getGameMode();
        if (gm == GameMode.SPECTATOR || (ignoreCreative && gm == GameMode.CREATIVE)) return;
        if (exemptPerm != null && !exemptPerm.isBlank() && p.hasPermission(exemptPerm)) return;

        int tick = Bukkit.getCurrentTick();
        S s = sessions.computeIfAbsent(p.getUniqueId(), k -> new S());
        if (tick - s.lastUseTick <= 1) return;

        // drop clicks outside the window
        int drop = 0;
        while (drop < s.n && tick - s.t[drop] > windowTicks) drop++;
        if (drop > 0) {
            System.arraycopy(s.t, drop, s.t, 0, s.n - drop);
            s.n -= drop;
        }
        if (s.n >= CAP) {
            System.arraycopy(s.t, 1, s.t, 0, s.n - 1);
            s.n--;
        }
        s.t[s.n++] = tick;

        if (s.n >= minSamples && (++s.evalCounter % 2 == 0)) {
            evaluate(p, s);
        }
    }

    private static double ind(double v, double normal, double extreme) {
        double span = extreme - normal;
        if (span == 0) return 0;
        return Text.clamp01((v - normal) / span);
    }

    private void evaluate(Player p, S s) {
        int n = s.n;
        int span = s.t[n - 1] - s.t[0];
        double cps = span <= 0 ? 40.0 : (n - 1) * 20.0 / span;
        if (cps < minCps) return;

        int m = n - 1;
        int[] iv = new int[m];
        double mean = 0;
        int zeros = 0;
        for (int i = 0; i < m; i++) {
            iv[i] = s.t[i + 1] - s.t[i];
            mean += iv[i];
            if (iv[i] == 0) zeros++;
        }
        mean /= m;
        double var = 0;
        for (int v : iv) var += (v - mean) * (v - mean);
        double std = Math.sqrt(var / m);
        double cv = mean > 0 ? std / mean : 0;

        double best = 0;
        for (int per = 1; per <= 6; per++) {
            if (m - per < 8) break;
            int match = 0;
            for (int i = per; i < m; i++) {
                if (iv[i] == iv[i - per]) match++;
            }
            best = Math.max(best, match / (double) (m - per));
        }
        double burst = zeros / (double) m;

        double[] val = {cps, cv, best, burst};
        double[] in = new double[4];
        double sum = 0, wsum = 0;
        int strong = 0;
        for (int i = 0; i < 4; i++) {
            in[i] = ind(val[i], normal[i], extreme[i]);
            sum += weight[i] * in[i];
            wsum += weight[i];
            if (in[i] >= 0.5) strong++;
        }
        double raw = wsum <= 0 ? 0 : sum / wsum * 100.0;
        if (strong < minStrong) raw = Math.min(raw, Math.min(threshold, SuspicionLevel.suspiciousThreshold()) - 1);
        s.score = Math.max(0, Math.min(100, s.score * 0.6 + raw * 0.4));
        s.lastCps = cps;
        s.lastCv = cv;
        s.lastRep = best;
        s.lastBurst = burst;

        PlayerData d = plugin.stats().get(p);
        double rounded = Math.round(s.score * 10.0) / 10.0;
        if (Math.abs(rounded - d.clickSuspicion) >= 0.1) {
            d.clickSuspicion = rounded;
            plugin.storage().markDirty(d);
        }

        long now = System.currentTimeMillis();
        if (s.score < threshold || now - s.lastAlert < cooldownMs) return;
        s.lastAlert = now;

        var msg = plugin.messages();
        List<String> reasons = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (in[i] >= 0.5) {
                String v = i == 0 ? Text.num(val[i], 1) : i == 1 ? Text.num(val[i], 2) : Text.num(val[i] * 100, 0) + "%";
                reasons.add(msg.plain("reasons.autoclicker." + IND[i], Text.map("value", v)));
            }
        }
        if (reasons.isEmpty()) reasons.add(msg.plain("reasons.autoclicker.generic"));
        String patternKey = in[1] >= 0.6 ? "consistent" : in[2] >= 0.6 ? "repeating" : in[3] >= 0.6 ? "burst" : "mixed";
        Map<String, String> extra = Text.map(
                "cps", Text.num(cps, 1),
                "cv", Text.num(cv, 2),
                "pattern", msg.plain("patterns." + patternKey));
        plugin.alerts().raise(p, DetectionType.AUTOCLICKER, new Detection(rounded, reasons.get(0), reasons, extra));
    }

    public void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, S> e : sessions.entrySet()) {
            S s = e.getValue();
            if (s.lastDecay == 0) {
                s.lastDecay = now;
                continue;
            }
            double dt = (now - s.lastDecay) / 1000.0;
            s.lastDecay = now;
            if (s.score > 0) {
                s.score = Math.max(0, s.score - decay * dt);
                PlayerData d = plugin.stats().get(e.getKey());
                if (d != null) {
                    double v = Math.round(s.score * 10.0) / 10.0;
                    if (Math.abs(v - d.clickSuspicion) >= 0.1) {
                        d.clickSuspicion = v;
                        plugin.storage().markDirty(d);
                    }
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
