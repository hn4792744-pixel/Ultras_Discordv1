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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /ucsecurity reload | refresh | resetlogs all [confirm] | resetlogs <player> | alerts | status | discordtest */
public final class UcSecurityCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUB = List.of("reload", "refresh", "resetlogs", "alerts", "status", "discordtest", "help");
    private static final long CONFIRM_MS = 30_000L;

    private final UltrasDiscord plugin;
    private final Map<String, Long> pending = new HashMap<>();

    public UcSecurityCommand(UltrasDiscord plugin) {
        this.plugin = plugin;
    }

    private boolean perm(CommandSender s, String node) {
        return s.hasPermission(node) || s.hasPermission("ultras.admin");
    }

    private static String who(CommandSender s) {
        return s instanceof Player p ? p.getUniqueId().toString() : "console";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var m = plugin.messages();
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            if (!plugin.staff().isStaff(sender) && !perm(sender, "ultras.logs")) {
                m.send(sender, "general.no-permission");
                return true;
            }
            m.sendList(sender, "commands.help", null);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (!perm(sender, "ultras.reload")) {
                    m.send(sender, "general.no-permission");
                    return true;
                }
                plugin.reloadAll();
                plugin.messages().send(sender, "commands.reloaded");
            }
            case "refresh" -> {
                if (!perm(sender, "ultras.logs")) {
                    m.send(sender, "general.no-permission");
                    return true;
                }
                plugin.refreshAll();
                m.send(sender, "commands.refreshed");
            }
            case "resetlogs" -> resetLogs(sender, args);
            case "alerts" -> {
                if (!(sender instanceof Player p)) {
                    m.send(sender, "general.player-only");
                    return true;
                }
                if (!plugin.staff().isStaff(p)) {
                    m.send(sender, "general.no-permission");
                    return true;
                }
                boolean on = plugin.staff().toggleAlerts(p.getUniqueId());
                m.send(sender, on ? "commands.alerts-on" : "commands.alerts-off");
            }
            case "status" -> {
                if (!plugin.staff().isStaff(sender) && !perm(sender, "ultras.logs")) {
                    m.send(sender, "general.no-permission");
                    return true;
                }
                var c = plugin.cfg();
                var d = plugin.discord();
                m.sendList(sender, "commands.status", Text.map(
                        "version", plugin.getPluginMeta().getVersion(),
                        "language", m.language(),
                        "xray", onOff(c.module("xray")),
                        "fly", onOff(c.module("fly")),
                        "autoclicker", onOff(c.module("autoclicker")),
                        "kills", onOff(c.module("kill-logs")),
                        "discord", d.isReady() ? (d.isWebhookReady() ? "webhook" : "bot") + (d.isBotReady() && d.isWebhookReady() ? "+bot" : "") : onOff(false),
                        "players", String.valueOf(plugin.stats().size())));
            }
            case "discordtest" -> {
                if (!perm(sender, "ultras.admin")) {
                    m.send(sender, "general.no-permission");
                    return true;
                }
                if (!plugin.discord().isReady()) {
                    m.send(sender, "commands.discord-not-ready");
                    return true;
                }
                plugin.discord().sendForced("security", Text.map("title", "Discord test", "message", "Test message sent by " + sender.getName()));
                m.send(sender, "commands.discord-test-sent");
            }
            default -> m.sendList(sender, "commands.help", null);
        }
        return true;
    }

    private static String onOff(boolean b) {
        return b ? "ON" : "OFF";
    }

    private void resetLogs(CommandSender sender, String[] args) {
        var m = plugin.messages();
        if (!perm(sender, "ultras.resetlogs")) {
            m.send(sender, "general.no-permission");
            return;
        }
        if (args.length < 2) {
            m.send(sender, "commands.resetlogs-usage");
            return;
        }
        if (args[1].equalsIgnoreCase("all")) {
            String key = who(sender);
            boolean confirm = args.length >= 3 && args[2].equalsIgnoreCase("confirm");
            Long until = pending.get(key);
            if (!confirm) {
                pending.put(key, System.currentTimeMillis() + CONFIRM_MS);
                m.send(sender, "commands.resetlogs-confirm", Text.map("seconds", String.valueOf(CONFIRM_MS / 1000)));
                return;
            }
            if (until == null || until < System.currentTimeMillis()) {
                pending.remove(key);
                m.send(sender, "commands.resetlogs-none-pending");
                return;
            }
            pending.remove(key);
            int n = plugin.stats().size();
            plugin.stats().resetAll();
            m.send(sender, "commands.resetlogs-all-done", Text.map("count", String.valueOf(n)));
            plugin.discord().sendEvent("security", Text.map("title", "Logs reset", "message", "All logs were deleted by " + sender.getName()));
            plugin.getLogger().warning("All logs were reset by " + sender.getName());
            return;
        }
        PlayerData d = plugin.stats().find(args[1]);
        if (d == null) {
            m.send(sender, "general.unknown-player", Text.map("player", args[1]));
            return;
        }
        String name = d.name;
        plugin.stats().resetPlayer(d.uuid);
        m.send(sender, "commands.resetlogs-player-done", Text.map("player", name));
        plugin.discord().sendEvent("security", Text.map("title", "Player logs reset", "message", "Logs of " + name + " were deleted by " + sender.getName()));
        plugin.getLogger().warning("Logs of " + name + " were reset by " + sender.getName());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!plugin.staff().isStaff(sender)) return out;
        if (args.length == 1) {
            for (String s : SUB) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("resetlogs")) {
            if ("all".startsWith(args[1].toLowerCase(Locale.ROOT))) out.add("all");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("resetlogs") && args[1].equalsIgnoreCase("all")) {
            out.add("confirm");
        }
        return out;
    }
}
