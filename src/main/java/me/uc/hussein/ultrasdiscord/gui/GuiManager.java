package me.uc.hussein.ultrasdiscord.gui;

import me.uc.hussein.ultrasdiscord.UltrasDiscord;
import me.uc.hussein.ultrasdiscord.detection.SuspicionLevel;
import me.uc.hussein.ultrasdiscord.model.DetectionType;
import me.uc.hussein.ultrasdiscord.model.PlayerData;
import me.uc.hussein.ultrasdiscord.utility.ItemBuilder;
import me.uc.hussein.ultrasdiscord.utility.SoundUtil;
import me.uc.hussein.ultrasdiscord.utility.Text;
import me.uc.hussein.ultrasdiscord.utility.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Entry point for every menu plus shared item/placeholder helpers. */
public final class GuiManager {
    private final UltrasDiscord plugin;
    private Material border = Material.BLACK_STAINED_GLASS_PANE;
    private final Map<String, Material> mats = new HashMap<>();
    private String clickSound = "UI_BUTTON_CLICK";

    public GuiManager(UltrasDiscord plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        var c = plugin.getConfig();
        Material b = Material.matchMaterial(c.getString("gui.border-material", "BLACK_STAINED_GLASS_PANE"));
        border = b == null || !b.isItem() ? Material.BLACK_STAINED_GLASS_PANE : b;
        clickSound = c.getString("gui.click-sound", "UI_BUTTON_CLICK");
        mats.clear();
        ConfigurationSection s = c.getConfigurationSection("gui.materials");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                Material m = Material.matchMaterial(s.getString(k, ""));
                if (m != null && m.isItem()) mats.put(k, m);
            }
        }
    }

    public boolean enabled() {
        return plugin.cfg().module("gui");
    }

    public Material mat(String key, Material def) {
        return mats.getOrDefault(key, def);
    }

    /** Item whose name/lore come from messages: gui.items.KEY.name / .lore */
    public ItemBuilder item(String key, Material def, Map<String, String> ph) {
        var m = plugin.messages();
        String name = Text.fillMm(m.raw("gui.items." + key + ".name"), ph);
        List<String> lore = new ArrayList<>();
        for (String l : m.rawList("gui.items." + key + ".lore")) {
            lore.add(Text.fillMm(l, ph));
        }
        return ItemBuilder.of(mat(key, def)).name(name).lore(lore);
    }

    public ItemBuilder head(String key, PlayerData d, Map<String, String> ph) {
        var m = plugin.messages();
        String name = Text.fillMm(m.raw("gui.items." + key + ".name"), ph);
        List<String> lore = new ArrayList<>();
        for (String l : m.rawList("gui.items." + key + ".lore")) {
            lore.add(Text.fillMm(l, ph));
        }
        return ItemBuilder.head(Bukkit.getOfflinePlayer(d.uuid)).name(name).lore(lore);
    }

    public void fillBorder(Inventory inv) {
        ItemStack pane = ItemBuilder.of(border).name("<gray> ").build();
        int size = inv.getSize();
        int rows = size / 9;
        for (int i = 0; i < size; i++) {
            int r = i / 9, c = i % 9;
            if (r == 0 || r == rows - 1 || c == 0 || c == 8) {
                inv.setItem(i, pane);
            }
        }
    }

    public void playClick(Player p) {
        if (!plugin.getConfig().getBoolean("sounds.enabled", true)) return;
        SoundUtil.play(p, clickSound, 0.6f, 1.2f, plugin.getLogger());
    }

    // ------------------------------------------------------------------ navigation
    public void open(Menu m) {
        m.open();
    }

    public void openMain(Player p) {
        new MainMenu(plugin, p).open();
    }

    public void openKills(Player p, Menu parent) {
        new KillsMenu(plugin, p, parent).open();
    }

    public void openPlayers(Player p, Menu parent) {
        new PlayersMenu(plugin, p, parent).open();
    }

    public void openOres(Player p, PlayerData target, Menu parent) {
        new OresMenu(plugin, p, target, parent).open();
    }

    public void openWarnings(Player p, PlayerData target, Menu parent) {
        new WarningsMenu(plugin, p, target, parent).open();
    }

    public void openProfile(Player p, PlayerData target, Menu parent) {
        new ProfileMenu(plugin, p, target, parent).open();
    }

    public void openStats(Player p, PlayerData target, Menu parent) {
        new StatsMenu(plugin, p, target, parent).open();
    }

    public void refreshOpen() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu m) {
                m.refresh();
            }
        }
    }

    /** Runs the configurable punishment command as the clicking staff member (never bans by itself). */
    public void punish(Player viewer, PlayerData target) {
        if (!plugin.staff().isStaff(viewer) && !viewer.hasPermission("ultras.punish")) {
            plugin.messages().send(viewer, "general.no-permission");
            return;
        }
        String cmd = plugin.alerts().punishCommand(target.name, target.uuid.toString());
        if (cmd.isBlank()) {
            plugin.messages().send(viewer, "commands.punish-missing");
            return;
        }
        viewer.closeInventory();
        final String run = cmd;
        Bukkit.getScheduler().runTask(plugin, () -> viewer.performCommand(run));
    }

    // ------------------------------------------------------------------ placeholders
    public String levelMm(double v) {
        return plugin.messages().raw("levels." + SuspicionLevel.of(v).key());
    }

    private void susp(Map<String, String> ph, String key, double v) {
        ph.put(key, Text.num(v, 1));
        ph.put(key + "-level-mm", levelMm(v));
    }

    public Map<String, String> playerPh(PlayerData d) {
        var m = plugin.messages();
        long now = System.currentTimeMillis();
        Player on = Bukkit.getPlayer(d.uuid);
        Map<String, String> ph = new HashMap<>();
        String never = m.plain("gui.never");
        ph.put("player", d.name);
        ph.put("uuid", d.uuid.toString());
        ph.put("status", m.plain(on != null ? "gui.online" : "gui.offline"));
        ph.put("gamemode", on != null ? on.getGameMode().name() : "-");
        ph.put("world", on != null ? on.getWorld().getName() : "-");
        ph.put("playtime", TimeUtil.duration(d.playtimeMs));
        ph.put("blocks", String.valueOf(d.blocksMined));
        ph.put("ores", String.valueOf(d.oresMined));
        ph.put("rare", String.valueOf(d.rareOres));
        ph.put("kills", String.valueOf(d.kills));
        ph.put("deaths", String.valueOf(d.deaths));
        double kd = d.deaths == 0 ? d.kills : d.kills / (double) d.deaths;
        ph.put("kd", Text.num(kd, 2));
        ph.put("kill-rate", String.valueOf(d.killsSince(now - 3_600_000L)));
        ph.put("last-kill", d.lastKillAt == 0 ? never : d.lastKillVictim + " (" + TimeUtil.dateTime(d.lastKillAt) + ")");
        ph.put("updated", d.lastUpdated == 0 ? never : TimeUtil.dateTime(d.lastUpdated));
        ph.put("first-seen", d.firstSeen == 0 ? never : TimeUtil.dateTime(d.firstSeen));
        ph.put("last-seen", on != null ? m.plain("gui.online") : (d.lastSeen == 0 ? never : TimeUtil.dateTime(d.lastSeen)));
        ph.put("warnings", String.valueOf(d.warnings.size()));
        ph.put("combat-warnings", String.valueOf(d.combatWarnings()));
        susp(ph, "xray", d.xraySuspicion);
        susp(ph, "fly", d.flySuspicion);
        susp(ph, "click", d.clickSuspicion);
        susp(ph, "overall", d.overall());
        if (d.lastDetectionAt == 0) {
            ph.put("last-detection", never);
        } else {
            DetectionType t = DetectionType.fromKey(d.lastDetectionType);
            String tn = t == null ? d.lastDetectionType : m.plain("types." + t.key());
            ph.put("last-detection", tn + " - " + TimeUtil.dateTime(d.lastDetectionAt));
        }
        ph.put("last-reason", d.lastDetectionReason == null || d.lastDetectionReason.isBlank() ? "-" : d.lastDetectionReason);
        return ph;
    }

    public UUID uuidOf(PlayerData d) {
        return d.uuid;
    }
}
