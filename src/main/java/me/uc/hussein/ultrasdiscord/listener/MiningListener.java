package me.uc.hussein.ultrasdiscord.listener;

import me.uc.hussein.ultrasdiscord.UltrasDiscord;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public final class MiningListener implements Listener {
    private final UltrasDiscord plugin;

    public MiningListener(UltrasDiscord plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        plugin.xray().onBreak(e.getPlayer(), e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        plugin.xray().onPlace(e.getBlockPlaced());
        plugin.clicker().markUse(e.getPlayer());
    }
}
