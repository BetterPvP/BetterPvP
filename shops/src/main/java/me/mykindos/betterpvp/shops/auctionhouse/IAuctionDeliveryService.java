package me.mykindos.betterpvp.shops.auctionhouse;

import java.util.UUID;

public interface IAuctionDeliveryService {

    default boolean deliverAuction(UUID target, Auction auction) {
        return false;
    }

    /**
     * Pays {@code amount} to {@code target}.
     *
     * @param reference names the payment so it lands at most once, however many times it is delivered
     */
    default boolean deliverCurrency(UUID target, int amount, String reference) {
        return false;
    }

}
