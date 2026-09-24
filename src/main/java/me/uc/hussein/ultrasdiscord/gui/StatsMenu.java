package me.uc.hussein.ultrasdiscord.gui;

import me.uc.hussein.ultrasdiscord.UltrasDiscord;
import me.uc.hussein.ultrasdiscord.model.DetectionType;
import me.uc.hussein.ultrasdiscord.model.PlayerData;
import me.uc.hussein.ultrasdiscord.model.WarningRecord;
import me.uc.hussein.ultrasdiscord.utility.ItemBuilder;
import me.uc.hussein.ultrasdiscord.utility.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Detailed statistics of one player. */
public final class StatsMenu extends Menu {
    private final PlayerData target;
    private final Menu parent;

    public StatsMenu(UltrasDiscord plugin, Player viewer, PlayerData target, Menu parent) {
        super(plugin, viewer);
        this.target = target;
        this.parent = parent;
    }

    @Override
    protected Component title() {
        return plugin.messages().c("gui.titles.stats", Text.map("player", target.name));
    }

    @Override
    protected int size() {
        return 45;
    }

    private static String pretty(String n) {
        StringBuilder sb = new StringBuilder();
        for (String p : n.toLowerCase(Locale.ROOT).split("_")) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    @Override
    protected void build() {
        var gui = plugin.gui();
        var m = plugin.messages();
        gui.fillBorder(inventory);
        Map<String, String> ph = gui.playerPh(target);

        double minutes = target.activeMiningMs / 60000.0;
        ph.put("blocks-min", Text.num(minutes <= 0 ? 0 : target.blocksMined / minutes, 1));
        ph.put("ores-min", Text.num(minutes <= 0 ? 0 : target.oresMined / minutes, 2));
        ph.put("rare-hour", Text.num(minutes <= 0 ? 0 : target.rareOres / (minutes / 60.0), 1));
        ph.put("active-mining", me.uc.hussein.ultrasdiscord.utility.TimeUtil.duration(target.activeMiningMs));
        int wx = 0, wf = 0, wc = 0, wk = 0;
        for (WarningRecord w : target.warnings) {
            if (w.type() == DetectionType.XRAY) wx++;
            else if (w.type() == DetectionType.FLY) wf++;
            else if (w.type() == DetectionType.AUTOCLICKER) wc++;
            else wk++;
        }
        ph.put("w-xray", String.valueOf(wx));
        ph.put("w-fly", String.valueOf(wf));
        ph.put("w-click", String.valueOf(wc));
        ph.put("w-combat", String.valueOf(wk));

        set(4, gui.head("profile-mini", target, ph).build());
        set(19, gui.item("stats-mining", Material.DIAMOND_PICKAXE, ph).build());
        set(21, gui.item("stats-combat", Material.IRON_SWORD, ph).build());
        set(23, gui.item("stats-detect", Material.BOOK, ph).build());
        set(25, gui.item("stats-time", Material.CLOCK, ph).build());

        // top blocks
        List<Map.Entry<String, Long>> top = new ArrayList<>(target.mined.entrySet());
        top.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        List<String> lore = new ArrayList<>();
        for (String l : m.rawList("gui.items.stats-top.lore")) lore.add(Text.fillMm(l, ph));
        for (int i = 0; i < Math.min(5, top.size()); i++) {
            lore.add(Text.fillMm(m.raw("gui.stats-top-line"), Text.map("block", pretty(top.get(i).getKey()), "amount", String.valueOf(top.get(i).getValue()))));
        }
        set(31, ItemBuilder.of(gui.mat("stats-top", Material.CHEST)).name(Text.fillMm(m.raw("gui.items.stats-top.name"), ph)).lore(lore).build());

        // areas
        List<Map.Entry<String, Long>> areas = new ArrayList<>(target.areas.entrySet());
        areas.sort((a, b) -> {
            int c = Long.compare(b.getValue(), a.getValue());
            return c != 0 ? c : a.getKey().compareTo(b.getKey());
        });
        List<String> alore = new ArrayList<>();
        for (String l : m.rawList("gui.items.stats-areas.lore")) alore.add(Text.fillMm(l, ph));
        for (int i = 0; i < Math.min(5, areas.size()); i++) {
            String[] parts = areas.get(i).getKey().split("\\|");
            String world = parts.length > 0 ? parts[0] : "?";
            String cx = parts.length > 1 ? parts[1] : "?";
            String cz = parts.length > 2 ? parts[2] : "?";
            alore.add(Text.fillMm(m.raw("gui.stats-area-line"), Text.map("world", world, "cx", cx, "cz", cz, "ores", String.valueOf(areas.get(i).getValue()))));
        }
        set(29, ItemBuilder.of(gui.mat("stats-areas", Material.COMPASS)).name(Text.fillMm(m.raw("gui.items.stats-areas.name"), ph)).lore(alore).build());

        set(36, gui.item("back", Material.ARROW, null).build(), e -> {
            if (parent != null) parent.open();
            else gui.openMain(viewer);
        });
        set(44, gui.item("close", Material.BARRIER, null).build(), e -> viewer.closeInventory());
    }
}
