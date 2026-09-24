package me.uc.hussein.ultrasdiscord.detection;

import me.uc.hussein.ultrasdiscord.UltrasDiscord;
import me.uc.hussein.ultrasdiscord.model.Detection;
import me.uc.hussein.ultrasdiscord.model.DetectionType;
import me.uc.hussein.ultrasdiscord.model.PlayerData;
import me.uc.hussein.ultrasdiscord.utility.Text;
import me.uc.hussein.ultrasdiscord.utility.TimeUtil;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Kill / death statistics and an optional kill-spam warning (COMBAT type). Alerts only. */
public final class CombatTracker {
    private final UltrasDiscord plugin;
    private final Map<UUID, Long> lastSpamWarn = new HashMap<>();
    private int recentStored = 10;
    private boolean spamEnabled = true;
    private int spamKills = 8;
    private long spamWindowMs = 300_000;

    public CombatTracker(UltrasDiscord plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        var c = plugin.getConfig();
        recentStored = Math.max(1, c.getInt("kills.recent-stored", 10));
        spamEnabled = c.getBoolean("kills.spam.enabled", true);
        spamKills = Math.max(2, c.getInt("kills.spam.kills", 8));
        spamWindowMs = Math.max(30, c.getInt("kills.spam.window-seconds", 300)) * 1000L;
    }

    public void onDeath(Player victim, Player killer) {
        if (!plugin.cfg().module("kill-logs")) return;
        long now = System.currentTimeMillis();

        PlayerData vd = plugin.stats().get(victim);
        vd.deaths++;
        vd.lastUpdated = now;
        plugin.storage().markDirty(vd);
        if (killer == null || killer.equals(victim)) return;

        PlayerData kd = plugin.stats().get(killer);
        kd.kills++;
        kd.lastKillAt = now;
        kd.lastKillVictim = victim.getName();
        kd.killTimes.add(now);
        while (kd.killTimes.size() > 200) kd.killTimes.remove(0);
        kd.recentKills.add(0, victim.getName() + "|" + now);
        while (kd.recentKills.size() > recentStored) kd.recentKills.remove(kd.recentKills.size() - 1);
        kd.lastUpdated = now;
        plugin.storage().markDirty(kd);

        double ratio = kd.deaths == 0 ? kd.kills : kd.kills / (double) kd.deaths;
        plugin.discord().sendEvent("kills", Text.map(
                "killer", killer.getName(),
                "victim", victim.getName(),
                "uuid", killer.getUniqueId().toString(),
                "kills", String.valueOf(kd.kills),
                "deaths", String.valueOf(kd.deaths),
                "kd", Text.num(ratio, 2),
                "world", killer.getWorld().getName(),
                "time", TimeUtil.time(now),
                "date", TimeUtil.date(now)));

        if (!spamEnabled) return;
        long recent = kd.killsSince(now - spamWindowMs);
        Long last = lastSpamWarn.get(killer.getUniqueId());
        if (recent >= spamKills && (last == null || now - last > spamWindowMs)) {
            lastSpamWarn.put(killer.getUniqueId(), now);
            double susp = Math.min(100, 50 + (recent - spamKills) * 10.0);
            String reason = plugin.messages().plain("reasons.combat.kill-spam", Text.map("kills", String.valueOf(recent), "minutes", Text.num(spamWindowMs / 60000.0, 0)));
            plugin.alerts().raise(killer, DetectionType.COMBAT,
                    new Detection(susp, reason, List.of(reason), Text.map("kills", String.valueOf(recent), "window", Text.num(spamWindowMs / 60000.0, 0))));
        }
    }

    public void remove(UUID id) {
        lastSpamWarn.remove(id);
    }
}
