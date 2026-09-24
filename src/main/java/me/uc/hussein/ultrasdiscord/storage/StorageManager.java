package me.uc.hussein.ultrasdiscord.storage;

import me.uc.hussein.ultrasdiscord.UltrasDiscord;
import me.uc.hussein.ultrasdiscord.model.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * YAML storage. Serialisation happens on the main thread (cheap, only dirty players), the actual disk
 * IO runs on a dedicated single IO thread, so the server never blocks on the disk and no
 * ConcurrentModificationException is possible.
 *
 * Layout (per-player-files: true):  data/players/UUID.yml   data/warnings/UUID.yml
 * Layout (per-player-files: false): data/players.yml         data/warnings.yml
 */
public final class StorageManager {
    private final UltrasDiscord plugin;
    private final Set<UUID> dirty = new HashSet<>();
    private ExecutorService io;
    private boolean perPlayer = true;
    private Path root;
    private Path playersDir;
    private Path warningsDir;
    private Path centralPlayers;
    private Path centralWarnings;
    private YamlConfiguration cPlayers;
    private YamlConfiguration cWarnings;
    private BukkitTask flushTask;

    public StorageManager(UltrasDiscord plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ lifecycle
    public void init() {
        io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "UltrasDiscord-IO");
            t.setDaemon(true);
            return t;
        });
        root = plugin.getDataFolder().toPath().resolve("data");
        playersDir = root.resolve("players");
        warningsDir = root.resolve("warnings");
        centralPlayers = root.resolve("players.yml");
        centralWarnings = root.resolve("warnings.yml");
        perPlayer = plugin.getConfig().getBoolean("storage.per-player-files", true);
        try {
            Files.createDirectories(root);
            if (perPlayer) {
                Files.createDirectories(playersDir);
                Files.createDirectories(warningsDir);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Cannot create data folder: " + e.getMessage());
        }
        if (perPlayer) {
            loadPerPlayerAsync();
        } else {
            loadCentralSync();
        }
        scheduleFlush();
    }

    /** Re-read storage settings after /ucsecurity reload. */
    public void reload() {
        boolean wanted = plugin.getConfig().getBoolean("storage.per-player-files", true);
        if (wanted != perPlayer) {
            flush(true);
            perPlayer = wanted;
            try {
                if (perPlayer) {
                    Files.createDirectories(playersDir);
                    Files.createDirectories(warningsDir);
                } else {
                    Files.createDirectories(root);
                    cPlayers = new YamlConfiguration();
                    cWarnings = new YamlConfiguration();
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Cannot switch storage mode: " + e.getMessage());
            }
            for (PlayerData d : plugin.stats().all()) {
                dirty.add(d.uuid);
            }
            flush(false);
            plugin.getLogger().info("Storage mode switched: per-player-files=" + perPlayer
                    + " (old files are left untouched; delete them manually if not needed).");
        }
        scheduleFlush();
    }

    private void scheduleFlush() {
        if (flushTask != null) {
            flushTask.cancel();
        }
        long ticks = Math.max(5, plugin.getConfig().getInt("storage.save-interval-seconds", 60)) * 20L;
        flushTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> flush(false), ticks, ticks);
    }

    public void shutdown() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = plugin.stats().get(p.getUniqueId());
            if (d != null) {
                plugin.stats().touch(p, d);
                dirty.add(d.uuid);
            }
        }
        flush(true);
        if (io != null) {
            io.shutdown();
            try {
                if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
                    plugin.getLogger().warning("Storage IO thread did not finish in time.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ------------------------------------------------------------------ dirty tracking (main thread)
    public void markDirty(PlayerData d) {
        if (d != null) dirty.add(d.uuid);
    }

    public void markDirty(UUID id) {
        if (id != null) dirty.add(id);
    }

    // ------------------------------------------------------------------ flush
    public void flush(boolean sync) {
        if (dirty.isEmpty()) return;
        List<UUID> ids = new ArrayList<>(dirty);
        dirty.clear();
        Map<Path, String> writes = new LinkedHashMap<>();
        try {
            if (perPlayer) {
                for (UUID id : ids) {
                    PlayerData d = plugin.stats().get(id);
                    if (d == null) continue;
                    YamlConfiguration py = new YamlConfiguration();
                    PlayerCodec.writePlayer(d, py);
                    writes.put(playersDir.resolve(id + ".yml"), py.saveToString());
                    Path wp = warningsDir.resolve(id + ".yml");
                    if (d.warnings.isEmpty()) {
                        writes.put(wp, null);
                    } else {
                        YamlConfiguration wy = new YamlConfiguration();
                        PlayerCodec.writeWarnings(d, wy);
                        writes.put(wp, wy.saveToString());
                    }
                }
            } else {
                if (cPlayers == null) cPlayers = new YamlConfiguration();
                if (cWarnings == null) cWarnings = new YamlConfiguration();
                for (UUID id : ids) {
                    PlayerData d = plugin.stats().get(id);
                    if (d == null) continue;
                    cPlayers.set(id.toString(), null);
                    PlayerCodec.writePlayer(d, cPlayers.createSection(id.toString()));
                    cWarnings.set(id.toString(), null);
                    if (!d.warnings.isEmpty()) {
                        PlayerCodec.writeWarnings(d, cWarnings.createSection(id.toString()));
                    }
                }
                writes.put(centralPlayers, cPlayers.saveToString());
                writes.put(centralWarnings, cWarnings.saveToString());
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to serialise player data", e);
            plugin.discord().sendError("Storage", "Failed to serialise player data: " + e.getClass().getSimpleName());
            return;
        }
        submit(() -> writeAll(writes), sync);
    }

    private void writeAll(Map<Path, String> writes) {
        for (Map.Entry<Path, String> e : writes.entrySet()) {
            try {
                if (e.getValue() == null) {
                    SafeFiles.delete(e.getKey());
                } else {
                    SafeFiles.write(e.getKey(), e.getValue());
                }
            } catch (IOException ex) {
                plugin.getLogger().severe("Could not write " + e.getKey().getFileName() + ": " + ex.getMessage());
                plugin.discord().sendError("Storage", "Could not write " + e.getKey().getFileName() + ": " + ex.getMessage());
            }
        }
    }

    private void submit(Runnable job, boolean sync) {
        if (sync || io == null || io.isShutdown()) {
            job.run();
            return;
        }
        try {
            io.execute(job);
        } catch (RejectedExecutionException ex) {
            job.run();
        }
    }

    // ------------------------------------------------------------------ delete
    public void deletePlayer(UUID id) {
        dirty.remove(id);
        if (perPlayer) {
            submit(() -> {
                try {
                    SafeFiles.delete(playersDir.resolve(id + ".yml"));
                    SafeFiles.delete(warningsDir.resolve(id + ".yml"));
                } catch (IOException e) {
                    plugin.getLogger().warning("Could not delete data of " + id + ": " + e.getMessage());
                }
            }, false);
        } else {
            if (cPlayers != null) cPlayers.set(id.toString(), null);
            if (cWarnings != null) cWarnings.set(id.toString(), null);
            writeCentralNow();
        }
    }

    public void deleteAll() {
        dirty.clear();
        if (perPlayer) {
            submit(() -> {
                deleteYmlIn(playersDir);
                deleteYmlIn(warningsDir);
            }, false);
        } else {
            cPlayers = new YamlConfiguration();
            cWarnings = new YamlConfiguration();
            writeCentralNow();
        }
    }

    private void writeCentralNow() {
        Map<Path, String> writes = new LinkedHashMap<>();
        writes.put(centralPlayers, cPlayers == null ? "" : cPlayers.saveToString());
        writes.put(centralWarnings, cWarnings == null ? "" : cWarnings.saveToString());
        submit(() -> writeAll(writes), false);
    }

    private void deleteYmlIn(Path dir) {
        if (!Files.isDirectory(dir)) return;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not clear " + dir.getFileName() + ": " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ load
    private void loadPerPlayerAsync() {
        submit(() -> {
            List<PlayerData> loaded = new ArrayList<>();
            int problems = 0;
            if (Files.isDirectory(playersDir)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(playersDir, "*.yml")) {
                    for (Path p : ds) {
                        String fn = p.getFileName().toString();
                        UUID id;
                        try {
                            id = UUID.fromString(fn.substring(0, fn.length() - 4));
                        } catch (IllegalArgumentException ex) {
                            continue;
                        }
                        YamlConfiguration y = SafeFiles.read(p, plugin.getLogger(), msg -> plugin.discord().sendError("Storage", msg));
                        if (y == null) {
                            problems++;
                            continue;
                        }
                        try {
                            PlayerData d = PlayerCodec.readPlayer(id, y);
                            YamlConfiguration wy = SafeFiles.read(warningsDir.resolve(fn), plugin.getLogger(),
                                    msg -> plugin.discord().sendError("Storage", msg));
                            if (wy != null) PlayerCodec.readWarnings(d, wy);
                            loaded.add(d);
                        } catch (RuntimeException ex) {
                            problems++;
                            plugin.getLogger().severe("Skipping unreadable player file " + fn + ": " + ex.getMessage());
                        }
                    }
                } catch (IOException e) {
                    plugin.getLogger().severe("Cannot list player data: " + e.getMessage());
                }
            }
            final int count = loaded.size();
            final int bad = problems;
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.stats().merge(loaded);
                    plugin.getLogger().info("Loaded data of " + count + " players" + (bad > 0 ? " (" + bad + " unreadable files skipped)" : "") + ".");
                });
            } catch (RuntimeException ignored) {
                // plugin disabled while loading
            }
        }, false);
    }

    private void loadCentralSync() {
        cPlayers = SafeFiles.read(centralPlayers, plugin.getLogger(), msg -> plugin.discord().sendError("Storage", msg));
        cWarnings = SafeFiles.read(centralWarnings, plugin.getLogger(), msg -> plugin.discord().sendError("Storage", msg));
        if (cPlayers == null) cPlayers = new YamlConfiguration();
        if (cWarnings == null) cWarnings = new YamlConfiguration();
        List<PlayerData> loaded = new ArrayList<>();
        for (String key : cPlayers.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                ConfigurationSection s = cPlayers.getConfigurationSection(key);
                if (s == null) continue;
                PlayerData d = PlayerCodec.readPlayer(id, s);
                ConfigurationSection ws = cWarnings.getConfigurationSection(key);
                if (ws != null) PlayerCodec.readWarnings(d, ws);
                loaded.add(d);
            } catch (RuntimeException e) {
                plugin.getLogger().severe("Skipping unreadable entry " + key + ": " + e.getMessage());
            }
        }
        plugin.stats().merge(loaded);
        plugin.getLogger().info("Loaded data of " + loaded.size() + " players (central file).");
    }
}
