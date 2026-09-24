package me.uc.hussein.ultrasdiscord.gui;

import me.uc.hussein.ultrasdiscord.UltrasDiscord;
import me.uc.hussein.ultrasdiscord.utility.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;

public final class MainMenu extends Menu {

    public MainMenu(UltrasDiscord plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected Component title() {
        return plugin.messages().c("gui.titles.main");
    }

    @Override
    protected int size() {
        return 27;
    }

    @Override
    protected void build() {
        var gui = plugin.gui();
        gui.fillBorder(inventory);
        int warnings = 0;
        for (var d : plugin.stats().all()) warnings += d.warnings.size();
        Map<String, String> ph = Text.map(
                "players", String.valueOf(plugin.stats().size()),
                "warnings", String.valueOf(warnings));
        set(10, gui.item("main-sword", Material.DIAMOND_SWORD, ph).build(), e -> gui.openKills(viewer, this));
        set(12, gui.item("main-ores", Material.DIAMOND_PICKAXE, ph).build(), e -> gui.openOres(viewer, null, this));
        set(14, gui.item("main-players", Material.PLAYER_HEAD, ph).build(), e -> gui.openPlayers(viewer, this));
        set(16, gui.item("main-warnings", Material.BOOK, ph).build(), e -> gui.openWarnings(viewer, null, this));
        set(22, gui.item("main-refresh", Material.CLOCK, ph).build(), e -> {
            plugin.refreshAll();
            refresh();
        });
        set(26, gui.item("close", Material.BARRIER, null).build(), e -> viewer.closeInventory());
    }
}
