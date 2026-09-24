package me.uc.hussein.ultrasdiscord.gui;

import me.uc.hussein.ultrasdiscord.UltrasDiscord;
import me.uc.hussein.ultrasdiscord.model.PlayerData;
import me.uc.hussein.ultrasdiscord.utility.ItemBuilder;
import me.uc.hussein.ultrasdiscord.utility.Text;
import me.uc.hussein.ultrasdiscord.utility.TimeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Sword section: kill statistics per player (sorted by kills). */
public final class KillsMenu extends PagedMenu<PlayerData> {

    public KillsMenu(UltrasDiscord plugin, Player viewer, Menu parent) {
        super(plugin, viewer, parent);
    }

    @Override
    protected Component title() {
        return plugin.messages().c("gui.titles.kills");
    }

    @Override
    protected List<PlayerData> entries() {
        List<PlayerData> list = new ArrayList<>();
        for (PlayerData d : plugin.stats().all()) {
            if (d.kills > 0 || d.deaths > 0) list.add(d);
        }
        list.sort(Comparator.comparingLong((PlayerData d) -> d.kills).reversed().thenComparing(d -> d.name, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    @Override
    protected ItemStack icon(PlayerData d) {
        var gui = plugin.gui();
        var ph = gui.playerPh(d);
        var m = plugin.messages();
        List<String> lore = new ArrayList<>();
        for (String l : m.rawList("gui.items.kill-entry.lore")) {
            lore.add(Text.fillMm(l, ph));
        }
        if (!d.recentKills.isEmpty()) {
            lore.add(Text.fillMm(m.raw("gui.recent-kills-header"), ph));
            int n = 0;
            for (String rk : d.recentKills) {
                if (n++ >= 5) break;
                int i = rk.lastIndexOf('|');
                String victim = i > 0 ? rk.substring(0, i) : rk;
                String when = "-";
                if (i > 0) {
                    try {
                        when = TimeUtil.dateTime(Long.parseLong(rk.substring(i + 1)));
                    } catch (NumberFormatException ignored) {
                        // keep dash
                    }
                }
                lore.add(Text.fillMm(m.raw("gui.recent-kill-line"), Text.map("victim", victim, "when", when)));
            }
        }
        lore.add(Text.fillMm(m.raw("gui.click-profile"), ph));
        return ItemBuilder.head(Bukkit.getOfflinePlayer(d.uuid))
                .name(Text.fillMm(m.raw("gui.items.kill-entry.name"), ph))
                .lore(lore).build();
    }

    @Override
    protected void onEntry(PlayerData d, InventoryClickEvent e) {
        plugin.gui().openProfile(viewer, d, this);
    }

    @Override
    protected void extra() {
        set(4, plugin.gui().item("sword-info", Material.DIAMOND_SWORD, null).build());
    }
}
