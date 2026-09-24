package me.uc.hussein.ultrasdiscord.command;

import me.uc.hussein.ultrasdiscord.UltrasDiscord;
import me.uc.hussein.ultrasdiscord.model.PlayerData;
import me.uc.hussein.ultrasdiscord.utility.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /xry_list [refresh | open|stats|warnings|blocks|punish <player|uuid> | sword | ores | players | warnings-all] */
public final class XryListCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUB = List.of("refresh", "open", "stats", "warnings", "blocks", "punish", "sword", "ores", "players");
    private final UltrasDiscord plugin;

    public XryListCommand(UltrasDiscord plugin) {
        this.plugin = plugin;
    }

    private boolean allowed(CommandSender s) {
        return s.hasPermission("ultras.gui") || s.hasPermission("ultras.admin") || plugin.staff().isStaff(s);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var m = plugin.messages();
        if (!allowed(sender)) {
            m.send(sender, "general.no-permission");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("refresh")) {
            plugin.refreshAll();
            m.send(sender, "commands.refreshed");
            return true;
        }
        if (!(sender instanceof Player p)) {
            m.send(sender, "general.player-only");
            return true;
        }
        if (!plugin.gui().enabled()) {
            m.send(sender, "general.module-disabled", Text.map("module", "gui"));
            return true;
        }
        var gui = plugin.gui();
        if (args.length == 0) {
            gui.openMain(p);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "sword" -> gui.openKills(p, null);
            case "ores" -> gui.openOres(p, null, null);
            case "players" -> gui.openPlayers(p, null);
            case "open", "stats", "warnings", "blocks", "punish" -> {
                if (args.length < 2) {
                    m.send(sender, "commands.xry-usage");
                    return true;
                }
                PlayerData d = plugin.stats().find(args[1]);
                if (d == null) {
                    m.send(sender, "general.unknown-player", Text.map("player", args[1]));
                    return true;
                }
                switch (sub) {
                    case "open" -> gui.openProfile(p, d, null);
                    case "stats" -> gui.openStats(p, d, null);
                    case "warnings" -> gui.openWarnings(p, d, null);
                    case "blocks" -> gui.openOres(p, d, null);
                    default -> gui.punish(p, d);
                }
            }
            default -> m.send(sender, "commands.xry-usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!allowed(sender)) return out;
        if (args.length == 1) {
            for (String s : SUB) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
        } else if (args.length == 2 && List.of("open", "stats", "warnings", "blocks", "punish").contains(args[0].toLowerCase(Locale.ROOT))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
            }
        }
        return out;
    }
}
