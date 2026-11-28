package me.mykindos.betterpvp.shops.auctionhouse;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface IAuctionDeliveryService {

    /** Hands the auction's item to {@code target}, completing with whether it was handed over. */
    default CompletableFuture<Boolean> deliverAuction(UUID target, Auction auction) {
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Pays {@code amount} to {@code target}, completing with whether the payment landed.
     *
     * @param reference names the payment so it lands at most once, however many times it is delivered
     */
    default CompletableFuture<Boolean> deliverCurrency(UUID target, int amount, String reference) {
        return CompletableFuture.completedFuture(false);
    }

}
