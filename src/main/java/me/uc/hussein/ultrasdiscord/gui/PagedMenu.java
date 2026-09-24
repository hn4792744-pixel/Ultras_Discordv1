package me.uc.hussein.ultrasdiscord.gui;

import me.uc.hussein.ultrasdiscord.UltrasDiscord;
import me.uc.hussein.ultrasdiscord.utility.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** 54-slot menu with 28 content slots, borders, back / previous / next / close. */
public abstract class PagedMenu<T> extends Menu {
    protected static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    protected final Menu parent;
    protected int page = 0;

    protected PagedMenu(UltrasDiscord plugin, Player viewer, Menu parent) {
        super(plugin, viewer);
        this.parent = parent;
    }

    protected abstract List<T> entries();

    protected abstract ItemStack icon(T entry);

    protected abstract void onEntry(T entry, InventoryClickEvent e);

    /** Extra buttons (slots 46, 47 ...). */
    protected void extra() {
    }

    @Override
    protected int size() {
        return 54;
    }

    @Override
    protected void build() {
        var gui = plugin.gui();
        gui.fillBorder(inventory);
        List<T> all = entries();
        int pages = Math.max(1, (int) Math.ceil(all.size() / (double) SLOTS.length));
        if (page >= pages) page = pages - 1;
        if (page < 0) page = 0;
        int from = page * SLOTS.length;
        for (int i = 0; i < SLOTS.length && from + i < all.size(); i++) {
            T entry = all.get(from + i);
            set(SLOTS[i], icon(entry), e -> onEntry(entry, e));
        }
        final int lastPage = pages - 1;
        set(45, gui.item("back", Material.ARROW, null).build(), e -> {
            if (parent != null) parent.open();
            else gui.openMain(viewer);
        });
        if (page > 0) {
            set(48, gui.item("prev", Material.ARROW, null).build(), e -> {
                page--;
                refresh();
            });
        }
        set(49, gui.item("page", Material.PAPER, Text.map("page", String.valueOf(page + 1), "pages", String.valueOf(pages), "total", String.valueOf(all.size()))).build());
        if (page < lastPage) {
            set(50, gui.item("next", Material.ARROW, null).build(), e -> {
                page++;
                refresh();
            });
        }
        set(53, gui.item("close", Material.BARRIER, null).build(), e -> viewer.closeInventory());
        extra();
    }
}
