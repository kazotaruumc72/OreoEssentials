package fr.elias.oreoEssentials.modgui.util;

import fr.elias.oreoEssentials.util.Lang;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;


public final class ItemBuilder {
    private final ItemStack item;

    public ItemBuilder(Material m) {
        this.item = new ItemStack(m);
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, amount));
        return this;
    }

    public ItemBuilder name(String s) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Lang.toComponent(s));
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder lore(String... ls) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<Component> lines = Arrays.stream(ls)
                    .map(Lang::toComponent)
                    .toList();
            meta.lore(lines);
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder enchant(Enchantment ench, int level, boolean unsafe) {
        if (unsafe) {
            item.addUnsafeEnchantment(ench, level);
        } else {
            item.addEnchantment(ench, level);
        }
        return this;
    }

    public ItemBuilder flags(ItemFlag... flags) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(flags);
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder hideAllFlags() {
        return flags(ItemFlag.values());
    }

    /**
     * Visual glow without showing enchants.
     * Uses LUCK_OF_THE_SEA (exists since long time) and hides flags.
     */
    public ItemBuilder glow(boolean enable) {
        if (enable) {
            enchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            hideAllFlags();
        } else {
            // remove the fake glow enchant if present
            item.removeEnchantment(Enchantment.LUCK_OF_THE_SEA);
        }
        return this;
    }

    public ItemStack build() {
        return item;
    }
}