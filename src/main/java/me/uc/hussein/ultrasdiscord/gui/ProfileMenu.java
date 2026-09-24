package me.uc.hussein.ultrasdiscord.gui;

import me.uc.hussein.ultrasdiscord.UltrasDiscord;
import me.uc.hussein.ultrasdiscord.model.PlayerData;
import me.uc.hussein.ultrasdiscord.utility.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;

/** Player profile: head + every statistic + navigation to statistics, warnings, blocks, punishments. */
public final class ProfileMenu extends Menu {
    private final PlayerData target;
    private final Menu parent;

    public ProfileMenu(UltrasDiscord plugin, Player viewer, PlayerData target, Menu parent) {
        super(plugin, viewer);
        this.target = target;
        this.parent = parent;
    }

    @Override
    protected Component title() {
        return plugin.messages().c("gui.titles.profile", Text.map("player", target.name));
    }

    @Override
    protected int size() {
        return 54;
    }

    @Override
    protected void build() {
        var gui = plugin.gui();
        gui.fillBorder(inventory);
        Map<String, String> ph = gui.playerPh(target);

        set(4, gui.head("profile-head", target, ph).build());

        set(19, gui.item("p-blocks", Material.DIAMOND_PICKAXE, ph).build());
        set(20, gui.item("p-ores", Material.IRON_ORE, ph).build());
        set(21, gui.item("p-rare", Material.DIAMOND, ph).build());
        set(22, gui.item("p-kills", Material.IRON_SWORD, ph).build());
        set(23, gui.item("p-deaths", Material.SKELETON_SKULL, ph).build());
        set(24, gui.item("p-warnings", Material.BOOK, ph).build());
        set(25, gui.item("p-playtime", Material.CLOCK, ph).build());

        set(29, gui.item("p-xray", Material.DIAMOND_ORE, ph).build());
        set(30, gui.item("p-fly", Material.FEATHER, ph).build());
        set(31, gui.item("p-click", Material.TRIPWIRE_HOOK, ph).build());
        set(32, gui.item("p-overall", Material.NETHER_STAR, ph).glow(target.overall() >= 50).build());
        set(33, gui.item("p-last", Material.CLOCK, ph).build());

        set(45, gui.item("back", Material.ARROW, null).build(), e -> {
            if (parent != null) parent.open();
            else gui.openMain(viewer);
        });
        set(47, gui.item("btn-stats", Material.WRITABLE_BOOK, ph).build(), e -> gui.openStats(viewer, target, this));
        set(48, gui.item("btn-warnings", Material.BOOK, ph).build(), e -> gui.openWarnings(viewer, target, this));
        set(49, gui.item("btn-blocks", Material.DIAMOND_PICKAXE, ph).build(), e -> gui.openOres(viewer, target, this));
        set(50, gui.item("btn-punish", Material.ANVIL, ph).build(), e -> gui.punish(viewer, target));
        set(53, gui.item("close", Material.BARRIER, null).build(), e -> viewer.closeInventory());
    }
}
