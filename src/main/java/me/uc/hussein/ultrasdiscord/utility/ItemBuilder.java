package me.uc.hussein.ultrasdiscord.utility;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/** Small fluent ItemStack builder using MiniMessage strings. */
public final class ItemBuilder {
    private final ItemStack item;
    private String name;
    private List<String> lore;
    private boolean glow;
    private OfflinePlayer owner;

    private ItemBuilder(ItemStack item) {
        this.item = item;
    }

    public static ItemBuilder of(Material material) {
        Material m = material == null || !material.isItem() ? Material.PAPER : material;
        return new ItemBuilder(new ItemStack(m));
    }

    public static ItemBuilder head(OfflinePlayer player) {
        ItemBuilder b = new ItemBuilder(new ItemStack(Material.PLAYER_HEAD));
        b.owner = player;
        return b;
    }

    public ItemBuilder name(String miniMessage) {
        this.name = miniMessage;
        return this;
    }

    public ItemBuilder lore(List<String> miniMessageLines) {
        this.lore = miniMessageLines == null ? null : new ArrayList<>(miniMessageLines);
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        this.glow = glow;
        return this;
    }

    public ItemStack build() {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        if (name != null) {
            meta.displayName(Text.item(name));
        }
        if (lore != null) {
            List<Component> lines = new ArrayList<>(lore.size());
            for (String l : lore) {
                lines.add(Text.item(l));
            }
            meta.lore(lines);
        }
        if (glow) {
            meta.setEnchantmentGlintOverride(true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        if (owner != null && meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(owner);
        }
        item.setItemMeta(meta);
        return item;
    }
}
