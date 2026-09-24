package me.uc.hussein.ultrasdiscord.manager;

import me.uc.hussein.ultrasdiscord.UltrasDiscord;
import me.uc.hussein.ultrasdiscord.detection.SuspicionLevel;
import me.uc.hussein.ultrasdiscord.model.Detection;
import me.uc.hussein.ultrasdiscord.model.DetectionType;
import me.uc.hussein.ultrasdiscord.model.PlayerData;
import me.uc.hussein.ultrasdiscord.model.WarningRecord;
import me.uc.hussein.ultrasdiscord.utility.SoundUtil;
import me.uc.hussein.ultrasdiscord.utility.Text;
import me.uc.hussein.ultrasdiscord.utility.TimeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Single entry point every detector uses: stores the warning, notifies staff (chat + sound),
 * writes to console and sends the Discord embed. Never bans - only alerts.
 */
public final class AlertManager {
    private final UltrasDiscord plugin;

    public AlertManager(UltrasDiscord plugin) {
        this.plugin = plugin;
    }

    private static String section(DetectionType t) {
        return t == DetectionType.COMBAT ? "kills.spam" : t.key();
    }

    public void raise(Player subject, DetectionType type, Detection det) {
        long now = System.currentTimeMillis();
        FileConfiguration cfg = plugin.getConfig();
        PlayerData d = plugin.stats().get(subject);
        var msg = plugin.messages();

        Map<String, String> ph = basePlaceholders(subject, type, det, now);

        // ---- 1. store warning
        if (plugin.cfg().module("warnings")) {
            int id = d.nextWarningId++;
            ph.put("warning-id", String.valueOf(id));
            List<String> details = msg.plainList("alerts.details." + type.key(), ph);
            d.warnings.add(new WarningRecord(id, type, now, det.suspicion(), det.reason(), details));
            int max = Math.max(10, cfg.getInt("storage.max-warnings-per-player", 200));
            while (d.warnings.size() > max) {
                d.warnings.remove(0);
            }
        } else {
            ph.put("warning-id", "-");
        }
        d.lastDetectionAt = now;
        d.lastDetectionType = type.key();
        d.lastDetectionReason = det.reason();
        d.lastUpdated = now;
        plugin.storage().markDirty(d);

        // ---- 2. staff chat + sound
        boolean chat = plugin.cfg().module("alerts") && cfg.getBoolean(section(type) + ".alerts.enabled", true);
        if (chat) {
            List<Component> lines = msg.list("alerts." + type.key(), ph);
            boolean sound = cfg.getBoolean("sounds.enabled", true) && cfg.getBoolean(section(type) + ".alerts.sound", true);
            String soundName = cfg.getString("sounds." + type.key(), "");
            float vol = (float) cfg.getDouble("sounds.volume", 1.0);
            float pitch = (float) cfg.getDouble("sounds.pitch", 1.0);
            for (Player staff : plugin.staff().recipients(type)) {
                for (Component c : lines) {
                    staff.sendMessage(c);
                }
                if (sound) {
                    SoundUtil.play(staff, soundName, vol, pitch, plugin.getLogger());
                }
            }
        }

        // ---- 3. console
        if (cfg.getBoolean("alerts.console", true)) {
            plugin.getLogger().info(msg.plain("console.alert", ph));
        }

        // ---- 4. Discord
        var discord = plugin.discord();
        discord.sendEvent(type.key(), ph);
        if (plugin.staff().isStaff(subject)) {
            discord.sendEvent("staff-alerts", ph);
        }
        if (!discord.eventEnabled(type.key())) {
            discord.sendEvent("warnings", ph);
        }
    }

    /** Placeholders shared by chat, console and Discord. */
    public Map<String, String> basePlaceholders(Player subject, DetectionType type, Detection det, long now) {
        var msg = plugin.messages();
        Map<String, String> ph = new HashMap<>();
        SuspicionLevel lvl = SuspicionLevel.of(det.suspicion());
        ph.put("player", subject.getName());
        ph.put("uuid", subject.getUniqueId().toString());
        ph.put("type", msg.plain("types." + type.key()));
        ph.put("suspicion", Text.num(det.suspicion(), 0));
        ph.put("level", msg.plain("levels." + lvl.key()));
        ph.put("level-mm", msg.raw("levels." + lvl.key()));
        ph.put("reason", det.reason());
        ph.put("reasons", String.join(", ", det.reasons()));
        ph.put("time", TimeUtil.time(now));
        ph.put("date", TimeUtil.date(now));
        ph.put("datetime", TimeUtil.dateTime(now));
        ph.put("gamemode", subject.getGameMode().name());
        ph.put("world", subject.getWorld().getName());
        ph.put("server", plugin.getConfig().getString("general.server-name", "Server"));
        ph.put("punish-command", punishCommand(subject.getName(), subject.getUniqueId().toString()));
        if (det.extra() != null) {
            ph.putAll(det.extra());
        }
        return ph;
    }

    /** The configurable punishment command, placeholders filled, without a leading slash. */
    public String punishCommand(String name, String uuid) {
        String cmd = plugin.getConfig().getString("punishment-command", "uc gui");
        cmd = Text.fillPlain(cmd == null ? "" : cmd, Text.map("player", name, "uuid", uuid)).trim();
        while (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }
        return cmd;
    }
}
