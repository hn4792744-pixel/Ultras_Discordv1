package me.uc.hussein.ultrasdiscord.manager;

import me.uc.hussein.ultrasdiscord.UltrasDiscord;
import me.uc.hussein.ultrasdiscord.model.DetectionType;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Decides who is staff (OP and/or permission nodes from config.yml) and who gets alerts. */
public final class StaffManager {
    private final UltrasDiscord plugin;
    private final Set<UUID> alertsOff = ConcurrentHashMap.newKeySet();

    public StaffManager(UltrasDiscord plugin) {
        this.plugin = plugin;
    }

    public boolean isStaff(CommandSender sender) {
        if (!(sender instanceof Player p)) return true;
        if (plugin.cfg().opIsStaff() && p.isOp()) return true;
        for (String perm : plugin.cfg().staffPermissions()) {
            if (p.hasPermission(perm)) return true;
        }
        return false;
    }

    public boolean alertsEnabled(UUID id) {
        return !alertsOff.contains(id);
    }

    /** @return new state (true = alerts on). */
    public boolean toggleAlerts(UUID id) {
        if (alertsOff.remove(id)) return true;
        alertsOff.add(id);
        return false;
    }

    public List<Player> recipients(DetectionType type) {
        boolean needType = plugin.getConfig().getBoolean("staff.require-type-permission", false);
        List<Player> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!isStaff(p) || !alertsEnabled(p.getUniqueId())) continue;
            if (needType && !p.hasPermission(type.permission())) continue;
            out.add(p);
        }
        return out;
    }
}
