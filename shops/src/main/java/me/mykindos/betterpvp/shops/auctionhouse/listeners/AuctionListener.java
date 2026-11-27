package me.mykindos.betterpvp.shops.auctionhouse.listeners;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.framework.updater.UpdateEvent;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import me.mykindos.betterpvp.shops.auctionhouse.Auction;
import me.mykindos.betterpvp.shops.auctionhouse.AuctionManager;
import me.mykindos.betterpvp.shops.auctionhouse.events.AuctionBuyEvent;
import me.mykindos.betterpvp.shops.auctionhouse.events.AuctionCancelEvent;
import me.mykindos.betterpvp.shops.auctionhouse.events.AuctionCreateEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@BPvPListener
@Singleton
public class AuctionListener implements Listener {

    private final ClientManager clientManager;
    private final AuctionManager auctionManager;

    @Inject
    public AuctionListener(ClientManager clientManager, AuctionManager auctionManager) {
        this.clientManager = clientManager;
        this.auctionManager = auctionManager;
    }

    @UpdateEvent(delay = 10000)
    public void processAuctions() {
        for (Auction auction : List.copyOf(auctionManager.getActiveAuctions())) {
            if (auction.isDelivered()) {
                auctionManager.getActiveAuctions().remove(auction);
                continue;
            }
            if (auction.hasExpired() || auction.isCancelled()) {
                removeOnceDelivered(auction, auctionManager.deliverAuction(auction.getSeller(), auction));
            } else if ((auction.isSold())) {
                if (auction.getTransaction() != null) {
                    removeOnceDelivered(auction,
                            auctionManager.deliverAuction(auction.getTransaction().getBuyer(), auction));
                }
            }
        }
    }

    private void removeOnceDelivered(Auction auction, CompletableFuture<Boolean> delivery) {
        delivery.thenAccept(delivered -> {
            if (delivered) {
                auctionManager.getActiveAuctions().remove(auction);
            }
        });
    }

    @EventHandler
    public void onAuctionBuy(AuctionBuyEvent event) {
        Client client = clientManager.search().online(event.getPlayer());
        if (client.getGamer().getBalance() < event.getAuction().getSellPrice()) {
            event.cancel("shops.listener.auctionhouse.insufficient-funds");
            return;
        }

        if (!auctionManager.getActiveAuctions().contains(event.getAuction())) {
            event.cancel("shops.listener.auctionhouse.no-exists");
        }
    }

    @EventHandler
    public void onAuctionCreate(AuctionCreateEvent event) {
        if (event.getAuction().getSellPrice() <= 0) {
            event.cancel("shops.listener.auctionhouse.invalid-price");
            return;
        }

        if (event.getAuction().getSellPrice() > 1_000_000_000) {
            event.cancel("shops.listener.auctionhouse.price-too-high");
            return;
        }
    }

    @EventHandler
    public void onCancel(AuctionCancelEvent event) {
        if (event.getAuction().isDelivered()) {
            event.cancel("shops.listener.auctionhouse.already-delivered");
        }
    }

}
