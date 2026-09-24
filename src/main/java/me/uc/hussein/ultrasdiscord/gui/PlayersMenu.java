package me.uc.hussein.ultrasdiscord.gui;

import me.uc.hussein.ultrasdiscord.UltrasDiscord;
import me.uc.hussein.ultrasdiscord.model.PlayerData;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Players sorted by overall suspicion (highest first). */
public final class PlayersMenu extends PagedMenu<PlayerData> {

    public PlayersMenu(UltrasDiscord plugin, Player viewer, Menu parent) {
        super(plugin, viewer, parent);
    }

    @Override
    protected Component title() {
        return plugin.messages().c("gui.titles.players");
    }

    @Override
    protected List<PlayerData> entries() {
        List<PlayerData> list = new ArrayList<>(plugin.stats().all());
        list.sort(Comparator.comparingDouble(PlayerData::overall).reversed().thenComparing(d -> d.name, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    @Override
    protected ItemStack icon(PlayerData d) {
        return plugin.gui().head("player-entry", d, plugin.gui().playerPh(d)).build();
    }

    @Override
    protected void onEntry(PlayerData d, InventoryClickEvent e) {
        plugin.gui().openProfile(viewer, d, this);
    }

    @Override
    protected void extra() {
        set(4, plugin.gui().item("players-info", Material.PLAYER_HEAD, null).build());
    }
}
