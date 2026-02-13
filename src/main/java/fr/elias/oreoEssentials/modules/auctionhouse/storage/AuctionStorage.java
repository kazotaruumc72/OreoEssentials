package fr.elias.oreoEssentials.modules.auctionhouse.storage;

import fr.elias.oreoEssentials.modules.auctionhouse.models.Auction;

import java.util.List;


public interface AuctionStorage {

    AuctionSnapshot loadAll();

    void saveAll(AuctionSnapshot snapshot);

    default void flush() {}

    record AuctionSnapshot(
            List<Auction> active,
            List<Auction> expired,
            List<Auction> sold
    ) {}
}