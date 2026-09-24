package me.uc.hussein.ultrasdiscord.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * All persistent data of one player. Only ever mutated on the main server thread
 * (the storage layer serialises it on the main thread and writes the text asynchronously).
 */
public final class PlayerData {
    public final UUID uuid;
    public String name;

    public long firstSeen;
    public long lastSeen;
    public long lastUpdated;
    public long playtimeMs;

    // ---- mining ----
    public long blocksMined;
    public long oresMined;
    public long rareOres;
    public long activeMiningMs;
    public long lastBreakAt;
    /** Material name -> amount mined. */
    public final Map<String, Long> mined = new HashMap<>();
    /** Material name -> last mined timestamp. */
    public final Map<String, Long> lastMined = new HashMap<>();
    /** "world|chunkX|chunkZ" -> ores mined there (capped). */
    public final Map<String, Long> areas = new HashMap<>();

    // ---- combat ----
    public long kills;
    public long deaths;
    public long lastKillAt;
    public String lastKillVictim = "";
    public final List<Long> killTimes = new ArrayList<>();
    /** newest first, "victim|timestamp". */
    public final List<String> recentKills = new ArrayList<>();

    // ---- detections ----
    public double xraySuspicion;
    public double flySuspicion;
    public double clickSuspicion;
    public long lastDetectionAt;
    public String lastDetectionType = "";
    public String lastDetectionReason = "";
    public int nextWarningId = 1;
    public final List<WarningRecord> warnings = new ArrayList<>();

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name == null ? "Unknown" : name;
    }

    public double overall() {
        double a = xraySuspicion, b = flySuspicion, c = clickSuspicion;
        double max = Math.max(a, Math.max(b, c));
        double min = Math.min(a, Math.min(b, c));
        double mid = a + b + c - max - min;
        return Math.min(100.0, max + 0.25 * mid + 0.10 * min);
    }

    public long count(String material) {
        return mined.getOrDefault(material, 0L);
    }

    public long killsSince(long since) {
        long n = 0;
        for (long t : killTimes) {
            if (t >= since) n++;
        }
        return n;
    }

    public int combatWarnings() {
        int n = 0;
        for (WarningRecord w : warnings) {
            if (w.type() == DetectionType.COMBAT || w.type() == DetectionType.AUTOCLICKER) n++;
        }
        return n;
    }

    /** Wipe every tracked statistic, keeping identity (uuid, name, first seen). */
    public void clear() {
        playtimeMs = 0;
        blocksMined = oresMined = rareOres = activeMiningMs = lastBreakAt = 0;
        mined.clear();
        lastMined.clear();
        areas.clear();
        kills = deaths = lastKillAt = 0;
        lastKillVictim = "";
        killTimes.clear();
        recentKills.clear();
        xraySuspicion = flySuspicion = clickSuspicion = 0;
        lastDetectionAt = 0;
        lastDetectionType = "";
        lastDetectionReason = "";
        nextWarningId = 1;
        warnings.clear();
        lastUpdated = System.currentTimeMillis();
    }
}
