package me.uc.hussein.ultrasdiscord.listener;

import me.uc.hussein.ultrasdiscord.UltrasDiscord;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerVelocityEvent;

/** Feeds the Fly detector and registers every "legitimate movement" grace event. */
public final class MovementListener implements Listener {
    private final UltrasDiscord plugin;

    public MovementListener(UltrasDiscord plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!e.hasChangedPosition()) return;
        plugin.fly().onMove(e.getPlayer(), e.getFrom(), e.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent e) {
        plugin.fly().grace(e.getPlayer(), 1500);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVelocity(PlayerVelocityEvent e) {
        plugin.fly().grace(e.getPlayer(), 2500);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) {
            plugin.fly().grace(p, 1500);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGlide(EntityToggleGlideEvent e) {
        if (e.getEntity() instanceof Player p) {
            plugin.fly().grace(p, 2500);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRiptide(PlayerRiptideEvent e) {
        plugin.fly().grace(e.getPlayer(), 5000);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGameMode(PlayerGameModeChangeEvent e) {
        plugin.fly().grace(e.getPlayer(), 3000);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFlightToggle(PlayerToggleFlightEvent e) {
        plugin.fly().grace(e.getPlayer(), 3000);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        plugin.fly().grace(e.getPlayer(), 3000);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent e) {
        plugin.fly().grace(e.getPlayer(), 2500);
    }
}
