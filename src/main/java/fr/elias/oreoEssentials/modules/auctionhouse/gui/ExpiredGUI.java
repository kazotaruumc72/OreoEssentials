package fr.elias.oreoEssentials.modules.auctionhouse.gui;

import fr.elias.oreoEssentials.modules.auctionhouse.AuctionHouseModule;
import fr.elias.oreoEssentials.modules.auctionhouse.models.Auction;
import fr.elias.oreoEssentials.modules.auctionhouse.utils.TimeFormatter;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.*;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

import static fr.elias.oreoEssentials.modules.auctionhouse.gui.BrowseGUI.*;

public class ExpiredGUI implements InventoryProvider {

    private final AuctionHouseModule module;

    public ExpiredGUI(AuctionHouseModule module) { this.module = module; }

    public static SmartInventory getInventory(AuctionHouseModule module) {
        return SmartInventory.builder()
                .id("oe_ah_expired")
                .provider(new ExpiredGUI(module))
                .manager(module.getPlugin().getInvManager())
                .size(6, 9)
                .title(c("&c&lExpired Listings"))
                .build();
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        Pagination pg = contents.pagination();
        contents.fillBorders(ClickableItem.empty(glass(Material.RED_STAINED_GLASS_PANE)));

        List<Auction> expired = module.getPlayerExpired(player.getUniqueId());

        if (expired.isEmpty()) {
            contents.set(2, 4, ClickableItem.empty(named(Material.BARRIER, "&a&lNo Expired Items!")));
        } else {
            ClickableItem[] items = expired.stream().map(a -> expiredItem(player, a)).toArray(ClickableItem[]::new);
            pg.setItems(items);
            pg.setItemsPerPage(28);
            SlotIterator it = contents.newIterator(SlotIterator.Type.HORIZONTAL, 1, 1);
            it.blacklist(1,8).blacklist(2,0).blacklist(2,8).blacklist(3,0).blacklist(3,8).blacklist(4,1);
            pg.addToIterator(it);

            ItemStack reclaimAll = named(Material.CHEST, "&a&lReclaim All (" + expired.size() + ")");
            contents.set(5, 1, ClickableItem.of(reclaimAll, e -> {
                click(player);
                int count = 0;
                for (Auction a : new ArrayList<>(expired)) {
                    if (module.reclaimExpired(player, a.getId())) count++;
                    else break;
                }
                if (count > 0) {
                    player.sendMessage(c("&aReclaimed " + count + " items!"));
                    getInventory(module).open(player);
                }
            }));
        }

        if (!pg.isFirst()) contents.set(5, 3, ClickableItem.of(named(Material.ARROW, "&e&lPrevious"), e -> { click(player); getInventory(module).open(player, pg.previous().getPage()); }));
        if (!pg.isLast())  contents.set(5, 5, ClickableItem.of(named(Material.ARROW, "&e&lNext"), e -> { click(player); getInventory(module).open(player, pg.next().getPage()); }));

        contents.set(5, 4, ClickableItem.of(named(Material.BARRIER, "&c&lBack"), e -> {
            click(player); ManageGUI.getInventory(module).open(player);
        }));

        ItemStack info = named(Material.CLOCK, "&c&lExpired Items: " + expired.size());
        contents.set(0, 4, ClickableItem.empty(info));
    }

    @Override public void update(Player player, InventoryContents contents) {}

    private ClickableItem expiredItem(Player player, Auction a) {
        ItemStack display = a.getItem().clone();
        ItemMeta meta = display.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(c("&7Was priced at: &c" + module.formatMoney(a.getPrice())));
        lore.add(c("&7Expired: &7" + TimeFormatter.format(System.currentTimeMillis() - a.getExpirationTime()) + " ago"));
        lore.add("");
        lore.add(c("&a&lClick to reclaim!"));
        meta.setLore(lore);
        display.setItemMeta(meta);

        return ClickableItem.of(display, e -> {
            click(player);
            if (module.reclaimExpired(player, a.getId())) {
                try { player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f); } catch (Throwable ignored) {}
                getInventory(module).open(player);
            }
        });
    }
}