package me.uc.hussein.ultrasdiscord.manager;

import me.uc.hussein.ultrasdiscord.UltrasDiscord;
import me.uc.hussein.ultrasdiscord.model.PlayerData;
import me.uc.hussein.ultrasdiscord.model.WarningRecord;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory cache of every known player's data (single source of truth, mutated on the main thread). */
public final class StatsManager {
    private final UltrasDiscord plugin;
    private final Map<UUID, PlayerData> players = new ConcurrentHashMap<>();
    private final Map<String, UUID> names = new ConcurrentHashMap<>();
    private final Map<UUID, Long> checkpoints = new HashMap<>();

    public StatsManager(UltrasDiscord plugin) {
        this.plugin = plugin;
    }

    public PlayerData get(UUID id) {
        return players.get(id);
    }

    public PlayerData get(Player p) {
        return getOrCreate(p.getUniqueId(), p.getName());
    }

    public PlayerData getOrCreate(UUID id, String name) {
        PlayerData d = players.get(id);
        if (d == null) {
            d = new PlayerData(id, name);
            long now = System.currentTimeMillis();
            d.firstSeen = now;
            d.lastSeen = now;
            d.lastUpdated = now;
            players.put(id, d);
            index(d);
        }
        return d;
    }

    private void index(PlayerData d) {
        if (d.name != null) names.put(d.name.toLowerCase(Locale.ROOT), d.uuid);
    }

    public Collection<PlayerData> all() {
        return players.values();
    }

    public int size() {
        return players.size();
    }

    /** Finds by UUID string or (case-insensitive) name. */
    public PlayerData find(String arg) {
        if (arg == null || arg.isBlank()) return null;
        try {
            PlayerData d = players.get(UUID.fromString(arg));
            if (d != null) return d;
        } catch (IllegalArgumentException ignored) {
            // not a uuid
        }
        UUID id = names.get(arg.toLowerCase(Locale.ROOT));
        if (id != null) return players.get(id);
        Player online = Bukkit.getPlayerExact(arg);
        return online == null ? null : get(online);
    }

    /** Called once the async load has finished. */
    public void merge(Collection<PlayerData> loaded) {
        for (PlayerData d : loaded) {
            PlayerData existing = players.get(d.uuid);
            if (existing != null && existing.name != null && !existing.name.equals("Unknown")) {
                d.name = existing.name;
            }
            players.put(d.uuid, d);
            index(d);
        }
    }

    // ------------------------------------------------------------------ session / playtime
    public void onJoin(Player p) {
        PlayerData d = get(p);
        d.name = p.getName();
        index(d);
        long now = System.currentTimeMillis();
        d.lastSeen = now;
        checkpoints.put(p.getUniqueId(), now);
        plugin.storage().markDirty(d);
    }

    public void onQuit(Player p) {
        PlayerData d = players.get(p.getUniqueId());
        if (d != null) {
            touch(p, d);
            plugin.storage().markDirty(d);
        }
        checkpoints.remove(p.getUniqueId());
    }

    /** Updates play time/last seen for an online player. */
    public void touch(Player p, PlayerData d) {
        long now = System.currentTimeMillis();
        Long cp = checkpoints.get(p.getUniqueId());
        if (cp != null && now > cp) {
            d.playtimeMs += now - cp;
        }
        checkpoints.put(p.getUniqueId(), now);
        d.lastSeen = now;
        d.name = p.getName();
    }

    public void updateOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = players.get(p.getUniqueId());
            if (d != null) {
                touch(p, d);
                plugin.storage().markDirty(d);
            }
        }
    }

    // ------------------------------------------------------------------ cleanup
    /** Removes only expired warnings. Returns how many were removed. */
    public int cleanupWarnings() {
        int days = plugin.getConfig().getInt("storage.warning-expire-days", 3);
        if (days <= 0) return 0;
        long cutoff = System.currentTimeMillis() - days * 86_400_000L;
        int removed = 0;
        for (PlayerData d : players.values()) {
            int before = d.warnings.size();
            d.warnings.removeIf((WarningRecord w) -> w.time() < cutoff);
            int diff = before - d.warnings.size();
            if (diff > 0) {
                removed += diff;
                plugin.storage().markDirty(d);
            }
        }
        return removed;
    }

    // ------------------------------------------------------------------ reset
    public void resetPlayer(UUID id) {
        PlayerData d = players.get(id);
        if (d == null) return;
        d.clear();
        plugin.xray().resetSession(id);
        plugin.fly().reset(id);
        plugin.clicker().reset(id);
        plugin.storage().deletePlayer(id);
        if (Bukkit.getPlayer(id) == null) {
            players.remove(id);
            if (d.name != null) names.remove(d.name.toLowerCase(Locale.ROOT), id);
        } else {
            plugin.storage().markDirty(d);
        }
    }

    public void resetAll() {
        for (PlayerData d : players.values()) {
            d.clear();
        }
        plugin.xray().resetAllSessions();
        plugin.fly().resetAll();
        plugin.clicker().resetAll();
        plugin.storage().deleteAll();
        for (PlayerData d : new java.util.ArrayList<>(players.values())) {
            if (Bukkit.getPlayer(d.uuid) == null) {
                players.remove(d.uuid);
                if (d.name != null) names.remove(d.name.toLowerCase(Locale.ROOT), d.uuid);
            } else {
                plugin.storage().markDirty(d);
            }
        }
    }
}
