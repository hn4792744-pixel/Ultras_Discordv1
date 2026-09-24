package me.uc.hussein.ultrasdiscord.listener;

import me.uc.hussein.ultrasdiscord.UltrasDiscord;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ConnectionListener implements Listener {
    private final UltrasDiscord plugin;

    public ConnectionListener(UltrasDiscord plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.stats().onJoin(p);
        plugin.fly().grace(p, 3000);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        plugin.stats().onQuit(p);
        plugin.xray().removeSession(p.getUniqueId());
        plugin.fly().remove(p.getUniqueId());
        plugin.clicker().remove(p.getUniqueId());
        plugin.combat().remove(p.getUniqueId());
    }
}
