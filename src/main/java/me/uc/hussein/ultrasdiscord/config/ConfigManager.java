package me.uc.hussein.ultrasdiscord.config;

import me.uc.hussein.ultrasdiscord.UltrasDiscord;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Thin wrapper over config.yml with cached hot-path values. Missing keys fall back to the jar defaults. */
public final class ConfigManager {
    private final UltrasDiscord plugin;
    private volatile boolean debug;
    private volatile boolean opIsStaff = true;
    private volatile Set<String> staffPermissions = Set.of();

    public ConfigManager(UltrasDiscord plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        debug = c.getBoolean("debug", false);
        opIsStaff = c.getBoolean("staff.op-is-staff", true);
        List<String> list = c.getStringList("staff.staff-permissions");
        Set<String> perms = new HashSet<>();
        for (String s : list) {
            if (s != null && !s.isBlank()) perms.add(s.trim());
        }
        staffPermissions = Set.copyOf(perms);
    }

    public FileConfiguration get() {
        return plugin.getConfig();
    }

    public boolean debug() {
        return debug;
    }

    public boolean opIsStaff() {
        return opIsStaff;
    }

    public Set<String> staffPermissions() {
        return staffPermissions;
    }

    /** modules.<name> switch (defaults to true when missing). */
    public boolean module(String name) {
        return plugin.getConfig().getBoolean("modules." + name, true);
    }

    public String language() {
        String l = plugin.getConfig().getString("language", "en");
        l = l == null ? "en" : l.toLowerCase(Locale.ROOT).trim();
        return l.equals("ar") ? "ar" : "en";
    }
}
