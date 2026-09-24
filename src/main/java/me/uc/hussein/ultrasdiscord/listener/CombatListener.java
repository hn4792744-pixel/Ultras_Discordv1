package me.uc.hussein.ultrasdiscord.listener;

import me.uc.hussein.ultrasdiscord.UltrasDiscord;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class CombatListener implements Listener {
    private final UltrasDiscord plugin;

    public CombatListener(UltrasDiscord plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        plugin.combat().onDeath(victim, victim.getKiller());
    }
}
