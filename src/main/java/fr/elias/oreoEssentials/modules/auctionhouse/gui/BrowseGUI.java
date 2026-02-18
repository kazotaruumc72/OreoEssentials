package fr.elias.oreoEssentials.modules.auctionhouse.gui;

import fr.elias.oreoEssentials.modules.auctionhouse.AuctionHouseModule;
import fr.elias.oreoEssentials.modules.auctionhouse.models.Auction;
import fr.elias.oreoEssentials.modules.auctionhouse.models.AuctionCategory;
import fr.elias.oreoEssentials.modules.auctionhouse.utils.TimeFormatter;
import fr.elias.oreoEssentials.util.Lang;
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

public class BrowseGUI implements InventoryProvider {

    private final AuctionHouseModule module;
    private final AuctionCategory category;
    private final String searchQuery;

    public BrowseGUI(AuctionHouseModule module, AuctionCategory category, String searchQuery) {
        this.module = module;
        this.category = category;

        String q = (searchQuery == null ? null : searchQuery.trim());
        this.searchQuery = (q == null || q.isEmpty()) ? null : q.toLowerCase();
    }


    public static SmartInventory getInventory(AuctionHouseModule module) {
        return getInventory(module, null, null);
    }

    public static SmartInventory getInventory(AuctionHouseModule module, AuctionCategory category) {
        return getInventory(module, category, null);
    }

    public static SmartInventory getInventory(AuctionHouseModule module, String searchQuery) {
        return getInventory(module, null, searchQuery);
    }

    public static SmartInventory getInventory(AuctionHouseModule module, AuctionCategory category, String searchQuery) {
        String title;
        if (searchQuery != null && !searchQuery.isBlank()) {
            title = c("&6&lSearch: &f" + searchQuery + (category != null ? " &8- &e" + category.getDisplayName() : ""));
        } else {
            title = category == null
                    ? c("&6&lAuction House")
                    : c("&6&lAuction House &8- &e" + category.getDisplayName());
        }

        String id = "oe_ah_browse"
                + (category != null ? "_" + category.name() : "")
                + (searchQuery != null && !searchQuery.isBlank() ? "_search" : "");

        return SmartInventory.builder()
                .id(id)
                .provider(new BrowseGUI(module, category, searchQuery))
                .manager(module.getPlugin().getInvManager())
                .size(6, 9)
                .title(title)
                .build();
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        Pagination pagination = contents.pagination();
        contents.fillBorders(ClickableItem.empty(glass(Material.GRAY_STAINED_GLASS_PANE)));

        List<Auction> auctions = category == null
                ? module.getAllActiveAuctions()
                : module.getAuctionsByCategory(category);

        if (searchQuery != null) {
            auctions = auctions.stream()
                    .filter(a -> matchesSearch(a, searchQuery))
                    .toList();
        }

        if (auctions.isEmpty()) {
            contents.set(2, 4, ClickableItem.empty(named(Material.BARRIER,
                    searchQuery != null ? "&c&lNo results" : "&c&lNo Auctions Available")));
        } else {
            ClickableItem[] items = auctions.stream()
                    .map(a -> auctionItem(player, a))
                    .toArray(ClickableItem[]::new);

            pagination.setItems(items);
            pagination.setItemsPerPage(28);

            SlotIterator it = contents.newIterator(SlotIterator.Type.HORIZONTAL, 1, 1);
            it.blacklist(1, 8).blacklist(2, 0).blacklist(2, 8).blacklist(3, 0).blacklist(3, 8).blacklist(4, 1);
            pagination.addToIterator(it);
        }

        nav(player, contents, pagination);
        controls(player, contents);

        contents.set(0, 4, ClickableItem.empty(balanceHead(player)));
    }

    @Override
    public void update(Player player, InventoryContents contents) {
        contents.set(0, 4, ClickableItem.empty(balanceHead(player)));
    }

    private boolean matchesSearch(Auction a, String q) {
        if (a == null) return false;

        if (a.getSellerName() != null && a.getSellerName().toLowerCase().contains(q)) return true;

        ItemStack it = a.getItem();
        if (it == null) return false;

        if (it.getType().name().toLowerCase().contains(q)) return true;

        if (it.hasItemMeta() && it.getItemMeta() != null) {
            ItemMeta meta = it.getItemMeta();

            if (meta.hasDisplayName() && meta.getDisplayName() != null) {
                String dn = ChatColor.stripColor(meta.getDisplayName());
                if (dn != null && dn.toLowerCase().contains(q)) return true;
            }

            if (meta.hasLore() && meta.getLore() != null) {
                for (String line : meta.getLore()) {
                    if (line == null) continue;
                    String s = ChatColor.stripColor(line);
                    if (s != null && s.toLowerCase().contains(q)) return true;
                }
            }
        }

        if (a.getItemsAdderID() != null && a.getItemsAdderID().toLowerCase().contains(q)) return true;
        if (a.getNexoID() != null && a.getNexoID().toLowerCase().contains(q)) return true;
        if (a.getOraxenID() != null && a.getOraxenID().toLowerCase().contains(q)) return true;

        return false;
    }

    private ClickableItem auctionItem(Player viewer, Auction a) {
        ItemStack display = a.getItem().clone();
        ItemMeta meta = display.getItemMeta();

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(c("&7Seller: &e" + a.getSellerName()));
        lore.add(c("&7Price: &a" + module.formatMoney(a.getPrice())));
        lore.add(c("&7Time Left: &e" + TimeFormatter.format(a.getTimeRemaining())));
        lore.add(c("&7Category: &b" + a.getCategory().getDisplayName()));
        if (a.getItemsAdderID() != null) lore.add(c("&d&lItemsAdder Item"));
        else if (a.getNexoID() != null) lore.add(c("&d&lNexo Item"));
        else if (a.getOraxenID() != null) lore.add(c("&d&lOraxen Item"));
        lore.add("");
        lore.add(a.getSeller().equals(viewer.getUniqueId())
                ? c("&e&lYOUR LISTING — click to manage")
                : c("&a&lClick to purchase!"));

        meta.setLore(lore);
        display.setItemMeta(meta);

        return ClickableItem.of(display, e -> {
            click(viewer);
            if (a.getSeller().equals(viewer.getUniqueId())) {
                ManageGUI.getInventory(module).open(viewer);
            } else {
                ConfirmGUI.getInventory(module, a).open(viewer);
            }
        });
    }

    private void nav(Player p, InventoryContents c, Pagination pg) {
        if (!pg.isFirst()) {
            c.set(5, 3, ClickableItem.of(named(Material.ARROW, "&e&lPrevious Page"), e -> {
                click(p);
                getInventory(module, category, searchQuery).open(p, pg.previous().getPage());
            }));
        }

        if (!pg.isLast()) {
            c.set(5, 5, ClickableItem.of(named(Material.ARROW, "&e&lNext Page"), e -> {
                click(p);
                getInventory(module, category, searchQuery).open(p, pg.next().getPage());
            }));
        }

        c.set(5, 4, ClickableItem.empty(named(Material.PAPER, "&ePage " + (pg.getPage() + 1))));
    }

    private void controls(Player p, InventoryContents c) {
        if (searchQuery != null) {
            c.set(5, 0, ClickableItem.of(named(Material.MAP, "&f&lClear Search"), e -> {
                click(p);
                getInventory(module, category, null).open(p);
            }));
        }

        if (category == null) {
            c.set(5, 1, ClickableItem.of(named(Material.COMPASS, "&b&lCategories"), e -> {
                click(p);
                CategoryGUI.getInventory(module).open(p);
            }));
        } else {
            c.set(5, 1, ClickableItem.of(named(Material.BARRIER, "&c&lBack to All"), e -> {
                click(p);
                getInventory(module, null, searchQuery).open(p);
            }));
        }

        c.set(5, 7, ClickableItem.of(named(Material.CHEST, "&6&lYour Listings"), e -> {
            click(p);
            ManageGUI.getInventory(module).open(p);
        }));

        c.set(5, 8, ClickableItem.of(named(Material.BARRIER, "&c&lClose"), e -> {
            click(p);
            p.closeInventory();
        }));
    }

    private ItemStack balanceHead(Player p) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta m = head.getItemMeta();
        m.setDisplayName(c("&6&lYour Balance"));
        m.setLore(List.of("",
                c("&7Balance: &a" + module.formatMoney(module.getEconomy().getBalance(p))),
                c("&7Your Listings: &e" + module.getPlayerActiveListings(p.getUniqueId()).size()),
                ""));
        head.setItemMeta(m);
        return head;
    }

    static String c(String s) { return Lang.color(s); }
    static ItemStack glass(Material m) { ItemStack i=new ItemStack(m); ItemMeta meta=i.getItemMeta(); meta.setDisplayName(" "); i.setItemMeta(meta); return i; }
    static ItemStack named(Material m, String name) { ItemStack i=new ItemStack(m); ItemMeta meta=i.getItemMeta(); meta.setDisplayName(c(name)); i.setItemMeta(meta); return i; }
    static void click(Player p) { try { p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, .5f, 1f); } catch(Throwable ignored){} }
}
