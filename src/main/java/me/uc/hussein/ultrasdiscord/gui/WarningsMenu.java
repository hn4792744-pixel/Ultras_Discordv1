package me.uc.hussein.ultrasdiscord.gui;

import me.uc.hussein.ultrasdiscord.UltrasDiscord;
import me.uc.hussein.ultrasdiscord.detection.SuspicionLevel;
import me.uc.hussein.ultrasdiscord.model.PlayerData;
import me.uc.hussein.ultrasdiscord.model.WarningRecord;
import me.uc.hussein.ultrasdiscord.utility.Text;
import me.uc.hussein.ultrasdiscord.utility.TimeUtil;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Warnings as Book items (newest first). Clicking a book opens it with the full details. */
public final class WarningsMenu extends PagedMenu<WarningsMenu.Entry> {

    public record Entry(PlayerData owner, WarningRecord warning) {
    }

    private final PlayerData target;

    public WarningsMenu(UltrasDiscord plugin, Player viewer, PlayerData target, Menu parent) {
        super(plugin, viewer, parent);
        this.target = target;
    }

    @Override
    protected Component title() {
        if (target == null) return plugin.messages().c("gui.titles.warnings");
        return plugin.messages().c("gui.titles.warnings-player", Text.map("player", target.name));
    }

    @Override
    protected List<Entry> entries() {
        List<Entry> list = new ArrayList<>();
        if (target != null) {
            for (WarningRecord w : target.warnings) list.add(new Entry(target, w));
        } else {
            for (PlayerData d : plugin.stats().all()) {
                for (WarningRecord w : d.warnings) list.add(new Entry(d, w));
            }
        }
        list.sort(Comparator.comparingLong((Entry e) -> e.warning().time()).reversed());
        if (list.size() > 500) return new ArrayList<>(list.subList(0, 500));
        return list;
    }

    private Map<String, String> ph(Entry e) {
        WarningRecord w = e.warning();
        var m = plugin.messages();
        SuspicionLevel lvl = SuspicionLevel.of(w.suspicion());
        return Text.map(
                "id", String.valueOf(w.id()),
                "player", e.owner().name,
                "type", m.plain("types." + w.type().key()),
                "date", TimeUtil.date(w.time()),
                "time", TimeUtil.time(w.time()),
                "suspicion", Text.num(w.suspicion(), 0),
                "level", m.plain("levels." + lvl.key()),
                "level-mm", m.raw("levels." + lvl.key()),
                "reason", w.reason().isBlank() ? "-" : w.reason());
    }

    @Override
    protected ItemStack icon(Entry e) {
        return plugin.gui().item("warning-entry", Material.WRITTEN_BOOK, ph(e)).build();
    }

    @Override
    protected void onEntry(Entry e, InventoryClickEvent ev) {
        var m = plugin.messages();
        Map<String, String> ph = ph(e);
        List<Component> lines = m.list("gui.warning-book", ph);
        List<Component> pages = new ArrayList<>();
        pages.add(Component.join(JoinConfiguration.newlines(), lines));
        if (!e.warning().details().isEmpty()) {
            List<Component> detail = new ArrayList<>();
            detail.add(m.c("gui.warning-book-details-header"));
            for (String d : e.warning().details()) {
                detail.add(Text.mm(Text.escape(d)));
            }
            pages.add(Component.join(JoinConfiguration.newlines(), detail));
        }
        Book book = Book.book(m.c("gui.warning-book-title", ph), Text.mm("UC_Hussein"), pages);
        viewer.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> viewer.openBook(book));
    }

    @Override
    protected void extra() {
        var gui = plugin.gui();
        if (target != null) {
            set(4, gui.head("profile-mini", target, gui.playerPh(target)).build());
        } else {
            set(4, gui.item("warnings-info", Material.BOOK, null).build());
        }
    }
}
