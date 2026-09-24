package me.uc.hussein.ultrasdiscord.gui;

import me.uc.hussein.ultrasdiscord.UltrasDiscord;
import me.uc.hussein.ultrasdiscord.model.PlayerData;
import me.uc.hussein.ultrasdiscord.utility.ItemBuilder;
import me.uc.hussein.ultrasdiscord.utility.Text;
import me.uc.hussein.ultrasdiscord.utility.TimeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every tracked block (discovered dynamically from the Material registry) with amount, percentage,
 * mining rate, last mined and its contribution to the X-Ray suspicion.
 * target == null -> whole server, otherwise a single player.
 */
public final class OresMenu extends PagedMenu<OresMenu.Row> {

    public record Row(String name, Material material, long amount, double percent, double perMin, long last, double contribution) {
    }

    private final PlayerData target;
    private boolean oresOnly = false;

    public OresMenu(UltrasDiscord plugin, Player viewer, PlayerData target, Menu parent) {
        super(plugin, viewer, parent);
        this.target = target;
    }

    @Override
    protected Component title() {
        if (target == null) return plugin.messages().c("gui.titles.ores");
        return plugin.messages().c("gui.titles.ores-player", Text.map("player", target.name));
    }

    @Override
    protected List<Row> entries() {
        Collection<PlayerData> sources = target == null ? plugin.stats().all() : List.of(target);
        Map<String, Long> amounts = new HashMap<>();
        Map<String, Long> lasts = new HashMap<>();
        Map<String, Double> contrib = new HashMap<>();
        long total = 0;
        long activeMs = 0;
        int contribPlayers = 0;
        var xray = plugin.xray();
        for (PlayerData d : sources) {
            activeMs += d.activeMiningMs;
            for (Map.Entry<String, Long> e : d.mined.entrySet()) {
                amounts.merge(e.getKey(), e.getValue(), Long::sum);
                total += e.getValue();
            }
            for (Map.Entry<String, Long> e : d.lastMined.entrySet()) {
                lasts.merge(e.getKey(), e.getValue(), Math::max);
            }
            if (d.xraySuspicion > 0) {
                double weighted = 0;
                Map<String, Double> parts = new HashMap<>();
                for (Map.Entry<String, Long> e : d.mined.entrySet()) {
                    Material m = Material.matchMaterial(e.getKey());
                    if (m == null) continue;
                    double w = xray.weightOf(m);
                    if (w <= 0) continue;
                    double part = w * e.getValue();
                    parts.put(e.getKey(), part);
                    weighted += part;
                }
                if (weighted > 0) {
                    contribPlayers++;
                    for (Map.Entry<String, Double> e : parts.entrySet()) {
                        contrib.merge(e.getKey(), d.xraySuspicion * e.getValue() / weighted, Double::sum);
                    }
                }
            }
        }
        double minutes = activeMs / 60000.0;
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, Long> e : amounts.entrySet()) {
            Material m = Material.matchMaterial(e.getKey());
            if (m == null) continue;
            if (oresOnly && !xray.isOre(m)) continue;
            double pct = total == 0 ? 0 : e.getValue() * 100.0 / total;
            double rate = minutes <= 0 ? 0 : e.getValue() / minutes;
            double c = contrib.getOrDefault(e.getKey(), 0.0);
            if (target == null && contribPlayers > 0) c = c / contribPlayers;
            rows.add(new Row(e.getKey(), m, e.getValue(), pct, rate, lasts.getOrDefault(e.getKey(), 0L), c));
        }
        rows.sort(Comparator.comparingLong(Row::amount).reversed().thenComparing(Row::name));
        return rows;
    }

    private static String pretty(String materialName) {
        StringBuilder sb = new StringBuilder();
        for (String part : materialName.toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    @Override
    protected ItemStack icon(Row r) {
        var m = plugin.messages();
        Map<String, String> ph = Text.map(
                "block", pretty(r.name()),
                "amount", String.valueOf(r.amount()),
                "percent", Text.num(r.percent(), 2),
                "rate-min", Text.num(r.perMin(), 2),
                "rate-hour", Text.num(r.perMin() * 60, 1),
                "last", r.last() == 0 ? m.plain("gui.never") : TimeUtil.dateTime(r.last()),
                "contribution", Text.num(r.contribution(), 1),
                "ore", plugin.xray().isOre(r.material()) ? m.plain("gui.answer-yes") : m.plain("gui.answer-no"));
        return plugin.gui().item("ore-entry", r.material(), ph).build();
    }

    @Override
    protected void onEntry(Row r, InventoryClickEvent e) {
        // informational only
    }

    @Override
    protected void extra() {
        var gui = plugin.gui();
        var m = plugin.messages();
        set(46, gui.item("toggle-ores", Material.HOPPER, Text.map("mode", m.plain(oresOnly ? "gui.mode-ores" : "gui.mode-all"))).build(), e -> {
            oresOnly = !oresOnly;
            page = 0;
            refresh();
        });
        if (target != null) {
            set(4, gui.head("profile-mini", target, gui.playerPh(target)).build());
        } else {
            set(4, gui.item("ores-info", Material.DIAMOND_PICKAXE, null).build());
        }
    }
}
