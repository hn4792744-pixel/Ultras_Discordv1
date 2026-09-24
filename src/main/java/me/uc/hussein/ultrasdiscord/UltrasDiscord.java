package me.uc.hussein.ultrasdiscord;

import me.uc.hussein.ultrasdiscord.command.UcSecurityCommand;
import me.uc.hussein.ultrasdiscord.command.XryListCommand;
import me.uc.hussein.ultrasdiscord.config.ConfigManager;
import me.uc.hussein.ultrasdiscord.config.Messages;
import me.uc.hussein.ultrasdiscord.detection.CombatTracker;
import me.uc.hussein.ultrasdiscord.detection.SuspicionLevel;
import me.uc.hussein.ultrasdiscord.detection.autoclicker.AutoClickerDetector;
import me.uc.hussein.ultrasdiscord.detection.fly.FlyDetector;
import me.uc.hussein.ultrasdiscord.detection.xray.XrayDetector;
import me.uc.hussein.ultrasdiscord.discord.DiscordManager;
import me.uc.hussein.ultrasdiscord.gui.GuiManager;
import me.uc.hussein.ultrasdiscord.listener.ClickListener;
import me.uc.hussein.ultrasdiscord.listener.CombatListener;
import me.uc.hussein.ultrasdiscord.listener.ConnectionListener;
import me.uc.hussein.ultrasdiscord.listener.GuiListener;
import me.uc.hussein.ultrasdiscord.listener.MiningListener;
import me.uc.hussein.ultrasdiscord.listener.MovementListener;
import me.uc.hussein.ultrasdiscord.manager.AlertManager;
import me.uc.hussein.ultrasdiscord.manager.StaffManager;
import me.uc.hussein.ultrasdiscord.manager.StatsManager;
import me.uc.hussein.ultrasdiscord.storage.StorageManager;
import me.uc.hussein.ultrasdiscord.utility.SoundUtil;
import me.uc.hussein.ultrasdiscord.utility.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Level;

/** Ultras_discord by UC_Hussein - X-Ray / Fly / AutoClicker alerts, statistics GUI and Discord logging. */
public final class UltrasDiscord extends JavaPlugin {

    private ConfigManager configManager;
    private Messages messages;
    private StatsManager stats;
    private StorageManager storage;
    private StaffManager staff;
    private DiscordManager discord;
    private AlertManager alerts;
    private XrayDetector xray;
    private FlyDetector fly;
    private AutoClickerDetector clicker;
    private CombatTracker combat;
    private GuiManager gui;
    private BukkitTask mainTask;
    private long seconds;

    @Override
    public void onEnable() {
        try {
            configManager = new ConfigManager(this);
            configManager.load();
            messages = new Messages(this);
            messages.load();
            applyStaticSettings();

            stats = new StatsManager(this);
            staff = new StaffManager(this);
            storage = new StorageManager(this);
            discord = new DiscordManager(this);
            alerts = new AlertManager(this);
            xray = new XrayDetector(this);
            fly = new FlyDetector(this);
            clicker = new AutoClickerDetector(this);
            combat = new CombatTracker(this);
            gui = new GuiManager(this);

            storage.init();
            discord.start();
            xray.reload();
            fly.reload();
            clicker.reload();
            combat.reload();
            gui.reload();

            var pm = getServer().getPluginManager();
            pm.registerEvents(new ConnectionListener(this), this);
            pm.registerEvents(new MiningListener(this), this);
            pm.registerEvents(new MovementListener(this), this);
            pm.registerEvents(new ClickListener(this), this);
            pm.registerEvents(new CombatListener(this), this);
            pm.registerEvents(new GuiListener(), this);

            XryListCommand xc = new XryListCommand(this);
            bind("xry_list", xc, xc);
            UcSecurityCommand uc = new UcSecurityCommand(this);
            bind("ucsecurity", uc, uc);

            // players already online (e.g. after /reload)
            for (var p : Bukkit.getOnlinePlayers()) {
                stats.onJoin(p);
            }
            startTasks();
            logModules();
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Ultras_discord failed to start and will be disabled", t);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void bind(String name, org.bukkit.command.CommandExecutor ex, org.bukkit.command.TabCompleter tc) {
        PluginCommand c = getCommand(name);
        if (c == null) {
            getLogger().severe("Command missing from plugin.yml: " + name);
            return;
        }
        c.setExecutor(ex);
        c.setTabCompleter(tc);
    }

    @Override
    public void onDisable() {
        if (mainTask != null) {
            mainTask.cancel();
            mainTask = null;
        }
        try {
            if (storage != null) storage.shutdown();
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Error while saving data on shutdown", t);
        }
        if (discord != null) discord.shutdown();
    }

    // ------------------------------------------------------------------ lifecycle helpers
    private void applyStaticSettings() {
        FileConfiguration c = getConfig();
        TimeUtil.configure(c.getString("general.time-zone", "SYSTEM"),
                c.getString("general.date-time-format", "yyyy-MM-dd HH:mm"),
                c.getString("general.date-format", "yyyy-MM-dd"),
                c.getString("general.time-format", "HH:mm"));
        SuspicionLevel.configure(
                c.getDouble("xray.thresholds.monitor", 30),
                c.getDouble("xray.thresholds.suspicious", 50),
                c.getDouble("xray.thresholds.high", 70),
                c.getDouble("xray.thresholds.critical", 85));
        SoundUtil.clearCache();
    }

    private void startTasks() {
        mainTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            seconds++;
            try {
                fly.refreshTps();
                fly.tick();
                clicker.tick();
                if (seconds % 5 == 0) xray.tick();
                if (seconds % 30 == 0) stats.updateOnline();
                long cleanup = Math.max(1, getConfig().getInt("storage.cleanup-interval-minutes", 10)) * 60L;
                if (seconds % cleanup == 0) {
                    int removed = stats.cleanupWarnings();
                    if (removed > 0 && configManager.debug()) debug("Expired warnings removed: " + removed);
                }
                long gui = Math.max(0, getConfig().getInt("gui.auto-refresh-seconds", 5));
                if (gui > 0 && seconds % gui == 0) this.gui.refreshOpen();
            } catch (Throwable t) {
                getLogger().log(Level.WARNING, "Periodic task error", t);
            }
        }, 20L, 20L);
    }

    private void logModules() {
        var c = configManager;
        getLogger().info("Ultras_discord v" + getPluginMeta().getVersion() + " by UC_Hussein enabled. Language: " + messages.language());
        getLogger().info("Modules - xray:" + c.module("xray") + " fly:" + c.module("fly") + " autoclicker:" + c.module("autoclicker")
                + " kill-logs:" + c.module("kill-logs") + " discord:" + c.module("discord") + " gui:" + c.module("gui"));
    }

    /** /ucsecurity reload */
    public void reloadAll() {
        configManager.load();
        messages.load();
        applyStaticSettings();
        storage.reload();
        discord.reload();
        xray.reload();
        fly.reload();
        clicker.reload();
        combat.reload();
        gui.reload();
        debug("Configuration reloaded");
    }

    /** /xry_list refresh, /ucsecurity refresh and the GUI refresh button. */
    public void refreshAll() {
        stats.updateOnline();
        xray.tick();
        fly.tick();
        clicker.tick();
        stats.cleanupWarnings();
        storage.flush(false);
        gui.refreshOpen();
    }

    public void debug(String msg) {
        if (configManager != null && configManager.debug()) getLogger().info("[debug] " + msg);
    }

    // ------------------------------------------------------------------ accessors
    public ConfigManager cfg() { return configManager; }
    public Messages messages() { return messages; }
    public StatsManager stats() { return stats; }
    public StorageManager storage() { return storage; }
    public StaffManager staff() { return staff; }
    public DiscordManager discord() { return discord; }
    public AlertManager alerts() { return alerts; }
    public XrayDetector xray() { return xray; }
    public FlyDetector fly() { return fly; }
    public AutoClickerDetector clicker() { return clicker; }
    public CombatTracker combat() { return combat; }
    public GuiManager gui() { return gui; }
}
