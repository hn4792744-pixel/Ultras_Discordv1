package me.uc.hussein.ultrasdiscord.gui;

import me.uc.hussein.ultrasdiscord.UltrasDiscord;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Base class of every GUI. The holder identifies our inventories so clicks can be cancelled safely. */
public abstract class Menu implements InventoryHolder {
    protected final UltrasDiscord plugin;
    protected final Player viewer;
    protected Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    protected Menu(UltrasDiscord plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    protected abstract Component title();

    protected abstract int size();

    protected abstract void build();

    public Player viewer() {
        return viewer;
    }

    public void open() {
        inventory = Bukkit.createInventory(this, size(), title());
        render();
        viewer.openInventory(inventory);
    }

    public void refresh() {
        if (inventory != null) render();
    }

    private void render() {
        actions.clear();
        inventory.clear();
        build();
    }

    protected void set(int slot, ItemStack item) {
        inventory.setItem(slot, item);
    }

    protected void set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inventory.setItem(slot, item);
        actions.put(slot, action);
    }

    public void click(InventoryClickEvent e) {
        Consumer<InventoryClickEvent> a = actions.get(e.getRawSlot());
        if (a != null) {
            plugin.gui().playClick(viewer);
            a.accept(e);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
