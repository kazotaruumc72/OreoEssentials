package fr.elias.oreoEssentials.modules.auctionhouse.gui;

import fr.elias.oreoEssentials.modules.auctionhouse.AuctionHouseModule;
import fr.elias.oreoEssentials.modules.auctionhouse.models.Auction;
import fr.elias.oreoEssentials.modules.auctionhouse.utils.TimeFormatter;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

import static fr.elias.oreoEssentials.modules.auctionhouse.gui.BrowseGUI.*;

public class SoldGUI implements InventoryProvider {

    private final AuctionHouseModule module;

    public SoldGUI(AuctionHouseModule module) { this.module = module; }

    public static SmartInventory getInventory(AuctionHouseModule module) {
        return SmartInventory.builder()
                .id("oe_ah_sold")
                .provider(new SoldGUI(module))
                .manager(module.getPlugin().getInvManager())
                .size(6, 9)
                .title(c("&a&lSold Items"))
                .build();
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        Pagination pg = contents.pagination();
        contents.fillBorders(ClickableItem.empty(glass(Material.GREEN_STAINED_GLASS_PANE)));

        List<Auction> sold = module.getPlayerSold(player.getUniqueId());

        if (sold.isEmpty()) {
            contents.set(2, 4, ClickableItem.empty(named(Material.BARRIER, "&e&lNo Sold Items Yet")));
        } else {
            ClickableItem[] items = sold.stream().map(this::soldItem).toArray(ClickableItem[]::new);
            pg.setItems(items);
            pg.setItemsPerPage(28);
            SlotIterator it = contents.newIterator(SlotIterator.Type.HORIZONTAL, 1, 1);
            it.blacklist(1,8).blacklist(2,0).blacklist(2,8).blacklist(3,0).blacklist(3,8).blacklist(4,1);
            pg.addToIterator(it);
        }

        if (!pg.isFirst()) contents.set(5, 3, ClickableItem.of(named(Material.ARROW, "&e&lPrevious"), e -> { click(player); getInventory(module).open(player, pg.previous().getPage()); }));
        if (!pg.isLast())  contents.set(5, 5, ClickableItem.of(named(Material.ARROW, "&e&lNext"), e -> { click(player); getInventory(module).open(player, pg.next().getPage()); }));

        contents.set(5, 4, ClickableItem.of(named(Material.BARRIER, "&c&lBack"), e -> {
            click(player); ManageGUI.getInventory(module).open(player);
        }));

        double total = sold.stream().mapToDouble(Auction::getPrice).sum();
        ItemStack stats = named(Material.EMERALD, "&a&lSales Statistics");
        ItemMeta sm = stats.getItemMeta();
        sm.setLore(List.of("", c("&7Total Sold: &a" + sold.size()),
                c("&7Total Earned: &a" + module.formatMoney(total)),
                c("&7Average: &e" + (sold.isEmpty() ? "$0" : module.formatMoney(total / sold.size()))), ""));
        stats.setItemMeta(sm);
        contents.set(0, 4, ClickableItem.empty(stats));
    }

    @Override public void update(Player player, InventoryContents contents) {}

    private ClickableItem soldItem(Auction a) {
        ItemStack display = a.getItem().clone();
        ItemMeta meta = display.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(c("&7Sold to: &e" + a.getBuyerName()));
        lore.add(c("&7Price: &a" + module.formatMoney(a.getPrice())));
        lore.add(c("&7Sold: &7" + TimeFormatter.format(System.currentTimeMillis() - a.getSoldTime()) + " ago"));
        lore.add("");
        lore.add(c("&8ID: " + a.getId().substring(0, 8)));
        meta.setLore(lore);
        display.setItemMeta(meta);
        return ClickableItem.empty(display);
    }
}