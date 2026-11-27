package me.mykindos.betterpvp.shops.auctionhouse;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DefaultAuctionDeliveryService implements IAuctionDeliveryService {

    @Override
    public CompletableFuture<Boolean> deliverAuction(UUID target, Auction auction) {
        return CompletableFuture.completedFuture(false);
    }

}
