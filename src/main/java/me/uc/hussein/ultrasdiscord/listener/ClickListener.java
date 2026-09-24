package me.uc.hussein.ultrasdiscord.listener;

import me.uc.hussein.ultrasdiscord.UltrasDiscord;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;

public final class ClickListener implements Listener {
    private final UltrasDiscord plugin;

    public ClickListener(UltrasDiscord plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) {
            plugin.clicker().markUse(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        plugin.clicker().onClick(e.getPlayer());
    }
}
