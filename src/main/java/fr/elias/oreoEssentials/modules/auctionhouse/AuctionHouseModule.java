package fr.elias.oreoEssentials.modules.auctionhouse;

import fr.elias.oreoEssentials.OreoEssentials;
import fr.elias.oreoEssentials.modules.auctionhouse.gui.BrowseGUI;
import fr.elias.oreoEssentials.modules.auctionhouse.models.Auction;
import fr.elias.oreoEssentials.modules.auctionhouse.models.AuctionCategory;
import fr.elias.oreoEssentials.modules.auctionhouse.models.AuctionStatus;
import fr.elias.oreoEssentials.modules.auctionhouse.storage.AuctionStorage;
import fr.elias.oreoEssentials.modules.auctionhouse.storage.AuctionStorage.AuctionSnapshot;
import fr.elias.oreoEssentials.modules.auctionhouse.storage.JsonAuctionStorage;
import fr.elias.oreoEssentials.modules.auctionhouse.storage.MongoAuctionStorage;
import fr.elias.oreoEssentials.modules.auctionhouse.utils.DiscordWebhook;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;


public final class AuctionHouseModule {

    private final OreoEssentials plugin;
    private AuctionHouseConfig cfg;
    private AuctionStorage storage;
    private Economy economy;

    private final List<Auction> activeAuctions  = new CopyOnWriteArrayList<>();
    private final List<Auction> expiredAuctions = new CopyOnWriteArrayList<>();
    private final List<Auction> soldAuctions    = new CopyOnWriteArrayList<>();

    private int expirationTaskId = -1;
    private int autoSaveTaskId   = -1;

    public AuctionHouseModule(OreoEssentials plugin) {
        this.plugin = plugin;
        reload();
    }


    public synchronized void reload() {
        if (expirationTaskId != -1) Bukkit.getScheduler().cancelTask(expirationTaskId);
        if (autoSaveTaskId   != -1) Bukkit.getScheduler().cancelTask(autoSaveTaskId);

        cfg = new AuctionHouseConfig(plugin);
        if (!cfg.enabled()) {
            if (storage != null) { try { storage.flush(); } catch (Throwable ignored) {} }
            storage = null;
            plugin.getLogger().info("[AuctionHouse] Disabled by config.");
            return;
        }

        setupEconomy();

        setupStorage();

        loadAuctions();

        startTasks();

        plugin.getLogger().info("[AuctionHouse] Reloaded (storage=" +
                (storage instanceof MongoAuctionStorage ? "mongodb" : "json") + ", " +
                activeAuctions.size() + " active auctions).");
    }

    public void stop() {
        if (expirationTaskId != -1) Bukkit.getScheduler().cancelTask(expirationTaskId);
        if (autoSaveTaskId   != -1) Bukkit.getScheduler().cancelTask(autoSaveTaskId);
        saveAuctions();
        if (storage != null) try { storage.flush(); } catch (Throwable ignored) {}
    }

    public boolean enabled() { return cfg != null && cfg.enabled() && storage != null; }


    public void openBrowse(Player p) {
        if (!enabled()) return;
        BrowseGUI.getInventory(this).open(p);
    }

    public boolean createAuction(Player seller, ItemStack item, double price,
                                 long durationHours, AuctionCategory category) {
        if (!enabled()) return false;

        if (price < cfg.minPrice() || price > cfg.maxPrice()) {
            seller.sendMessage(cfg.getMessage("errors.invalid-price",
                    Map.of("min", String.valueOf(cfg.minPrice()), "max", String.valueOf(cfg.maxPrice()))));
            return false;
        }
        if (durationHours > cfg.maxDurationHours()) durationHours = cfg.maxDurationHours();
        if (getPlayerActiveListings(seller.getUniqueId()).size() >= cfg.maxListingsPerPlayer()) {
            seller.sendMessage(cfg.getMessage("errors.max-listings",
                    Map.of("max", String.valueOf(cfg.maxListingsPerPlayer()))));
            return false;
        }

        double fee = (price * cfg.listingFeePercent() / 100.0) + cfg.listingFeeFlat();
        if (fee > 0 && economy != null) {
            if (!economy.has(seller, fee)) {
                seller.sendMessage(cfg.getMessage("errors.not-enough-money"));
                return false;
            }
            economy.withdrawPlayer(seller, fee);
        }

        Auction auction = new Auction(seller.getUniqueId(), seller.getName(), item,
                price, durationHours * 3_600_000L, category);
        detectCustomItem(auction, item);

        activeAuctions.add(auction);
        seller.sendMessage(cfg.getMessage("listing.created",
                Map.of("price", formatMoney(price), "duration", durationHours + "h")));

        if (cfg.discordEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                    new DiscordWebhook(cfg.discordWebhookUrl())
                            .setBotName(cfg.discordBotName())
                            .setBotAvatarUrl(cfg.discordBotAvatar())
                            .setTitle("New Listing")
                            .setDescription("**" + seller.getName() + "** listed **" +
                                    item.getType().name() + " x" + item.getAmount() +
                                    "** for **" + formatMoney(price) + "**")
                            .setColor(0x00FF00)
                            .execute());
        }
        return true;
    }

    public boolean purchaseAuction(Player buyer, String auctionId) {
        Auction auction = activeAuctions.stream()
                .filter(a -> a.getId().equals(auctionId))
                .findFirst().orElse(null);
        if (auction == null) {
            buyer.sendMessage(cfg.getMessage("errors.auction-not-found"));
            return false;
        }
        if (auction.getSeller().equals(buyer.getUniqueId())) {
            buyer.sendMessage(cfg.getMessage("errors.cannot-buy-own"));
            return false;
        }
        if (auction.isExpired()) {
            buyer.sendMessage(cfg.getMessage("errors.auction-expired"));
            return false;
        }
        if (economy == null) return false;

        double price = auction.getPrice();
        if (!economy.has(buyer, price)) {
            buyer.sendMessage(cfg.getMessage("errors.not-enough-money"));
            return false;
        }

        economy.withdrawPlayer(buyer, price);
        double tax = price * cfg.taxPercent() / 100.0;
        double sellerReceives = price - tax;
        economy.depositPlayer(Bukkit.getOfflinePlayer(auction.getSeller()), sellerReceives);

        Map<Integer, ItemStack> overflow = buyer.getInventory().addItem(auction.getItem());
        if (!overflow.isEmpty()) {
            overflow.values().forEach(i ->
                    buyer.getWorld().dropItemNaturally(buyer.getLocation(), i));
        }

        auction.markAsSold(buyer.getUniqueId(), buyer.getName());
        activeAuctions.remove(auction);
        soldAuctions.add(auction);

        buyer.sendMessage(cfg.getMessage("purchase.success",
                Map.of("item", auction.getItem().getType().name(), "price", formatMoney(price))));

        Player sellerOnline = Bukkit.getPlayer(auction.getSeller());
        if (sellerOnline != null) {
            sellerOnline.sendMessage(cfg.getMessage("listing.sold",
                    Map.of("item", auction.getItem().getType().name(),
                            "price", formatMoney(sellerReceives),
                            "buyer", buyer.getName())));
            playSound(sellerOnline, Sound.ENTITY_PLAYER_LEVELUP);
        }

        if (cfg.discordEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                    new DiscordWebhook(cfg.discordWebhookUrl())
                            .setBotName(cfg.discordBotName())
                            .setTitle("Item Sold!")
                            .setDescription("**" + buyer.getName() + "** purchased **" +
                                    auction.getItem().getType().name() + "** from **" +
                                    auction.getSellerName() + "** for **" + formatMoney(price) + "**")
                            .setColor(0xFFD700)
                            .execute());
        }
        return true;
    }

    public boolean cancelAuction(Player owner, String auctionId) {
        Auction auction = activeAuctions.stream()
                .filter(a -> a.getId().equals(auctionId) && a.getSeller().equals(owner.getUniqueId()))
                .findFirst().orElse(null);
        if (auction == null) return false;

        auction.markAsCancelled();
        activeAuctions.remove(auction);

        Map<Integer, ItemStack> overflow = owner.getInventory().addItem(auction.getItem());
        if (!overflow.isEmpty()) {
            overflow.values().forEach(i ->
                    owner.getWorld().dropItemNaturally(owner.getLocation(), i));
        }
        owner.sendMessage(cfg.getMessage("listing.cancelled"));
        return true;
    }

    public boolean reclaimExpired(Player owner, String auctionId) {
        Auction auction = expiredAuctions.stream()
                .filter(a -> a.getId().equals(auctionId) && a.getSeller().equals(owner.getUniqueId()))
                .findFirst().orElse(null);
        if (auction == null) return false;

        Map<Integer, ItemStack> overflow = owner.getInventory().addItem(auction.getItem());
        if (!overflow.isEmpty()) {
            owner.sendMessage(cfg.getMessage("errors.inventory-full"));
            return false;
        }
        expiredAuctions.remove(auction);
        owner.sendMessage(cfg.getMessage("listing.reclaimed"));
        return true;
    }


    public List<Auction> getAllActiveAuctions() {
        return Collections.unmodifiableList(activeAuctions);
    }

    public List<Auction> getAuctionsByCategory(AuctionCategory cat) {
        if (cat == AuctionCategory.ALL) return getAllActiveAuctions();
        return activeAuctions.stream().filter(a -> a.getCategory() == cat).collect(Collectors.toList());
    }

    public List<Auction> getPlayerActiveListings(UUID playerId) {
        return activeAuctions.stream().filter(a -> a.getSeller().equals(playerId)).collect(Collectors.toList());
    }

    public List<Auction> getPlayerExpired(UUID playerId) {
        return expiredAuctions.stream().filter(a -> a.getSeller().equals(playerId)).collect(Collectors.toList());
    }

    public List<Auction> getPlayerSold(UUID playerId) {
        return soldAuctions.stream().filter(a -> a.getSeller().equals(playerId)).collect(Collectors.toList());
    }


    public int clearAllAuctions() {
        int count = activeAuctions.size();

        activeAuctions.clear();
        expiredAuctions.clear();
        soldAuctions.clear();

        saveAuctions();
        return count;
    }


    public void checkExpiredAuctions() {
        List<Auction> nowExpired = activeAuctions.stream()
                .filter(Auction::isExpired)
                .collect(Collectors.toList());

        for (Auction a : nowExpired) {
            a.markAsExpired();
            activeAuctions.remove(a);
            expiredAuctions.add(a);

            Player p = Bukkit.getPlayer(a.getSeller());
            if (p != null) {
                p.sendMessage(cfg.getMessage("listing.expired",
                        Map.of("item", a.getItem().getType().name())));
            }
        }
    }


    public void loadAuctions() {
        if (storage == null) return;
        AuctionSnapshot snap = storage.loadAll();
        activeAuctions.clear();  activeAuctions.addAll(snap.active());
        expiredAuctions.clear(); expiredAuctions.addAll(snap.expired());
        soldAuctions.clear();    soldAuctions.addAll(snap.sold());
    }

    public void saveAuctions() {
        if (storage == null) return;
        storage.saveAll(new AuctionSnapshot(
                new ArrayList<>(activeAuctions),
                new ArrayList<>(expiredAuctions),
                new ArrayList<>(soldAuctions)));
    }


    public OreoEssentials   getPlugin()  { return plugin; }
    public AuctionHouseConfig getConfig(){ return cfg; }
    public Economy          getEconomy() { return economy; }

    public String formatMoney(double amount) {
        return economy != null ? economy.format(amount) : String.format("$%.2f", amount);
    }


    private void setupEconomy() {
        try {
            RegisteredServiceProvider<Economy> rsp =
                    Bukkit.getServicesManager().getRegistration(Economy.class);
            economy = rsp != null ? rsp.getProvider() : null;
        } catch (Throwable t) {
            economy = null;
        }
        if (economy == null) plugin.getLogger().warning("[AuctionHouse] Vault economy not found!");
    }

    private void setupStorage() {
        String mode = cfg.storageType();
        String mainMode = plugin.getConfig().getString("essentials.storage", "yaml").toLowerCase();
        boolean wantMongo = mode.equals("mongodb") || (mode.equals("auto") && "mongodb".equals(mainMode));

        if (wantMongo) {
            com.mongodb.client.MongoClient client = getMongoClient();
            if (client != null) {
                String db = plugin.getConfig().getString("storage.mongo.database", "oreo");
                storage = new MongoAuctionStorage(client, db, cfg.mongoCollection(), plugin.getLogger());
                return;
            }
            plugin.getLogger().warning("[AuctionHouse] MongoDB unavailable, falling back to JSON.");
        }
        storage = new JsonAuctionStorage(cfg.folder(), plugin.getLogger());
    }

    private void startTasks() {
        expirationTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                this::checkExpiredAuctions, 20L * 60, 20L * 60).getTaskId();
        autoSaveTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                this::saveAuctions, 20L * 300, 20L * 300).getTaskId();
    }

    private com.mongodb.client.MongoClient getMongoClient() {
        try {
            Field f = OreoEssentials.class.getDeclaredField("homesMongoClient");
            f.setAccessible(true);
            Object obj = f.get(plugin);
            if (obj instanceof com.mongodb.client.MongoClient mc) return mc;
        } catch (Throwable ignored) {}
        return null;
    }

    private void detectCustomItem(Auction auction, ItemStack item) {
        try {
            Class<?> cs = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object stack = cs.getMethod("byItemStack", ItemStack.class).invoke(null, item);
            if (stack != null) {
                String id = (String) cs.getMethod("getNamespacedID").invoke(stack);
                if (id != null) auction.setItemsAdderID(id);
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> nx = Class.forName("com.nexomc.nexo.api.NexoItems");
            Boolean exists = (Boolean) nx.getMethod("exists", ItemStack.class).invoke(null, item);
            if (Boolean.TRUE.equals(exists)) {
                String id = (String) nx.getMethod("idFromItem", ItemStack.class).invoke(null, item);
                if (id != null) auction.setNexoID(id);
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> ox = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            String id = (String) ox.getMethod("getIdByItem", ItemStack.class).invoke(null, item);
            if (id != null) auction.setOraxenID(id);
        } catch (Throwable ignored) {}

        if (item.hasItemMeta() && item.getItemMeta().hasCustomModelData()) {
            auction.setCustomModelData(item.getItemMeta().getCustomModelData());
        }
    }

    private static void playSound(Player p, Sound s) {
        try { p.playSound(p.getLocation(), s, 1f, 1f); } catch (Throwable ignored) {}
    }
}