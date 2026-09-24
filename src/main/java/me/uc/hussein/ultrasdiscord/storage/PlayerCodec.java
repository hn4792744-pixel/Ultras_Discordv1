package me.uc.hussein.ultrasdiscord.storage;

import me.uc.hussein.ultrasdiscord.model.DetectionType;
import me.uc.hussein.ultrasdiscord.model.PlayerData;
import me.uc.hussein.ultrasdiscord.model.WarningRecord;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** (De)serialises PlayerData to/from a YAML section. Shared by per-player and central storage. */
final class PlayerCodec {
    private PlayerCodec() {
    }

    // ------------------------------------------------------------------ write
    static void writePlayer(PlayerData d, ConfigurationSection s) {
        s.set("name", d.name);
        s.set("first-seen", d.firstSeen);
        s.set("last-seen", d.lastSeen);
        s.set("last-updated", d.lastUpdated);
        s.set("playtime-ms", d.playtimeMs);

        ConfigurationSection m = s.createSection("mining");
        m.set("blocks", d.blocksMined);
        m.set("ores", d.oresMined);
        m.set("rare", d.rareOres);
        m.set("active-ms", d.activeMiningMs);
        m.set("last-break", d.lastBreakAt);
        ConfigurationSection mined = m.createSection("mined");
        for (Map.Entry<String, Long> e : d.mined.entrySet()) {
            mined.set(e.getKey(), e.getValue());
        }
        ConfigurationSection last = m.createSection("last");
        for (Map.Entry<String, Long> e : d.lastMined.entrySet()) {
            last.set(e.getKey(), e.getValue());
        }
        List<String> areas = new ArrayList<>();
        for (Map.Entry<String, Long> e : d.areas.entrySet()) {
            areas.add(e.getKey() + "|" + e.getValue());
        }
        m.set("areas", areas);

        ConfigurationSection k = s.createSection("kills");
        k.set("total", d.kills);
        k.set("deaths", d.deaths);
        k.set("last-at", d.lastKillAt);
        k.set("last-victim", d.lastKillVictim);
        k.set("times", new ArrayList<>(d.killTimes));
        k.set("recent", new ArrayList<>(d.recentKills));

        ConfigurationSection x = s.createSection("suspicion");
        x.set("xray", d.xraySuspicion);
        x.set("fly", d.flySuspicion);
        x.set("autoclicker", d.clickSuspicion);
        ConfigurationSection ld = s.createSection("last-detection");
        ld.set("time", d.lastDetectionAt);
        ld.set("type", d.lastDetectionType);
        ld.set("reason", d.lastDetectionReason);
        s.set("next-warning-id", d.nextWarningId);
    }

    static void writeWarnings(PlayerData d, ConfigurationSection s) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (WarningRecord w : d.warnings) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", w.id());
            m.put("type", w.type().key());
            m.put("time", w.time());
            m.put("suspicion", w.suspicion());
            m.put("reason", w.reason());
            m.put("details", new ArrayList<>(w.details()));
            out.add(m);
        }
        s.set("warnings", out);
    }

    // ------------------------------------------------------------------ read
    static PlayerData readPlayer(UUID uuid, ConfigurationSection s) {
        PlayerData d = new PlayerData(uuid, s.getString("name", "Unknown"));
        d.firstSeen = s.getLong("first-seen");
        d.lastSeen = s.getLong("last-seen");
        d.lastUpdated = s.getLong("last-updated");
        d.playtimeMs = s.getLong("playtime-ms");

        ConfigurationSection m = s.getConfigurationSection("mining");
        if (m != null) {
            d.blocksMined = m.getLong("blocks");
            d.oresMined = m.getLong("ores");
            d.rareOres = m.getLong("rare");
            d.activeMiningMs = m.getLong("active-ms");
            d.lastBreakAt = m.getLong("last-break");
            ConfigurationSection mined = m.getConfigurationSection("mined");
            if (mined != null) {
                for (String key : mined.getKeys(false)) {
                    d.mined.put(key, mined.getLong(key));
                }
            }
            ConfigurationSection last = m.getConfigurationSection("last");
            if (last != null) {
                for (String key : last.getKeys(false)) {
                    d.lastMined.put(key, last.getLong(key));
                }
            }
            for (String line : m.getStringList("areas")) {
                int i = line.lastIndexOf('|');
                if (i <= 0) continue;
                try {
                    d.areas.put(line.substring(0, i), Long.parseLong(line.substring(i + 1)));
                } catch (NumberFormatException ignored) {
                    // skip damaged entry
                }
            }
        }
        ConfigurationSection k = s.getConfigurationSection("kills");
        if (k != null) {
            d.kills = k.getLong("total");
            d.deaths = k.getLong("deaths");
            d.lastKillAt = k.getLong("last-at");
            d.lastKillVictim = k.getString("last-victim", "");
            d.killTimes.addAll(k.getLongList("times"));
            d.recentKills.addAll(k.getStringList("recent"));
        }
        ConfigurationSection x = s.getConfigurationSection("suspicion");
        if (x != null) {
            d.xraySuspicion = x.getDouble("xray");
            d.flySuspicion = x.getDouble("fly");
            d.clickSuspicion = x.getDouble("autoclicker");
        }
        ConfigurationSection ld = s.getConfigurationSection("last-detection");
        if (ld != null) {
            d.lastDetectionAt = ld.getLong("time");
            d.lastDetectionType = ld.getString("type", "");
            d.lastDetectionReason = ld.getString("reason", "");
        }
        d.nextWarningId = Math.max(1, s.getInt("next-warning-id", 1));
        return d;
    }

    static void readWarnings(PlayerData d, ConfigurationSection s) {
        for (Map<?, ?> m : s.getMapList("warnings")) {
            try {
                DetectionType type = DetectionType.fromKey(String.valueOf(m.get("type")));
                if (type == null) continue;
                int id = ((Number) m.get("id")).intValue();
                long time = ((Number) m.get("time")).longValue();
                double susp = ((Number) m.get("suspicion")).doubleValue();
                String reason = m.get("reason") == null ? "" : String.valueOf(m.get("reason"));
                List<String> details = new ArrayList<>();
                Object o = m.get("details");
                if (o instanceof List<?> l) {
                    for (Object e : l) {
                        details.add(String.valueOf(e));
                    }
                }
                d.warnings.add(new WarningRecord(id, type, time, susp, reason, details));
                if (id >= d.nextWarningId) d.nextWarningId = id + 1;
            } catch (RuntimeException ignored) {
                // skip damaged warning entry
            }
        }
        d.warnings.sort(Comparator.comparingLong(WarningRecord::time));
    }
}
