package me.mykindos.betterpvp.shops.auctionhouse;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.CustomLog;
import lombok.Getter;
import lombok.Setter;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.framework.economy.ledger.Ledger;
import me.mykindos.betterpvp.core.framework.economy.ledger.LedgerParty;
import me.mykindos.betterpvp.core.framework.economy.ledger.Movement;
import me.mykindos.betterpvp.core.item.ItemFactory;
import me.mykindos.betterpvp.core.item.ItemRegistry;
import me.mykindos.betterpvp.core.item.component.impl.uuid.UUIDProperty;
import me.mykindos.betterpvp.core.logging.LogContext;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import me.mykindos.betterpvp.ledger.LedgerAccounts;
import me.mykindos.betterpvp.shops.auctionhouse.events.AuctionBuyEvent;
import me.mykindos.betterpvp.shops.auctionhouse.events.AuctionCancelEvent;
import me.mykindos.betterpvp.shops.auctionhouse.repository.AuctionRepository;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@Getter
@CustomLog
public class AuctionManager {

    private final ItemRegistry itemRegistry;
    private final ItemFactory itemFactory;
    private final ClientManager clientManager;
    private final AuctionRepository auctionRepository;
    private final Ledger ledger;

    private final List<Auction> activeAuctions = new ArrayList<>();
    /** Listings whose purchase is waiting on the ledger, so a second click cannot buy them again. */
    private final Set<Long> buying = ConcurrentHashMap.newKeySet();

    @Setter
    private IAuctionDeliveryService deliveryService = new DefaultAuctionDeliveryService();

    @Inject
    public AuctionManager(ItemRegistry itemRegistry, ItemFactory itemFactory, ClientManager clientManager,
                          AuctionRepository auctionRepository, Ledger ledger) {
        this.ledger = ledger;
        this.itemRegistry = itemRegistry;
        this.itemFactory = itemFactory;
        this.clientManager = clientManager;
        this.auctionRepository = auctionRepository;
        activeAuctions.addAll(auctionRepository.getAll());
    }

    /**
     * Lists an item on the auction house
     * @param player the player listing the auction
     * @param auction the auction being listed
     */
    public void addNewAuction(@NotNull Player player, @NotNull Auction auction) {
        auction.setExpiryTime(System.currentTimeMillis() + auction.getListingDuration().getDuration());
        getAuctionRepository().save(auction);
        getActiveAuctions().add(auction);

        itemFactory.fromItemStack(auction.getItemStack()).ifPresent(itemInstance -> {
            itemInstance.getComponent(UUIDProperty.class).ifPresent(uuidProperty -> {
                final UUID uuid = uuidProperty.getUniqueId();
                log.info("{} listed ({}) on the auction house for ${}", player.getName(), uuid, auction.getSellPrice())
                        .setAction("ITEM_AUCTION_CREATE")
                        .addItemContext(itemRegistry, itemInstance)
                        .addContext(LogContext.CURRENCY, String.valueOf(auction.getSellPrice()))
                        .addClientContext(player).submit();
            });
        });
        UtilMessage.message(player, "core.prefix.auction-house", "shops.auctionhouse.listing-created");
        log.info("{} has created a listing for {} for ${}", player.getName(), auction.getItemStack().getType().name(), auction.getSellPrice()).setAction("ITEM_AUCTION_LIST").submit();
    }

    public void buyAuction(Player player, Auction auction) {
        AuctionBuyEvent auctionBuyEvent = UtilServer.callEvent(new AuctionBuyEvent(player, auction));
        if (auctionBuyEvent.isCancelled()) {
            UtilMessage.message(player, "core.prefix.auction-house", auctionBuyEvent.getCancelReason());
            return;
        }

        if(auctionBuyEvent.getAuction().isCancelled()) {
            UtilMessage.message(player, "core.prefix.auction-house", "shops.auctionhouse.auction-cancelled");
            return;
        }

        if (auction.isSold() || !buying.add(auction.getAuctionID())) {
            UtilMessage.message(player, "core.prefix.auction-house", "shops.auctionhouse.already-sold");
            return;
        }

        // The reference makes the listing sell once across every server.
        final Client buyer = clientManager.search().online(player);
        final Movement payment = new Movement(LedgerParty.of(buyer.getGamer()), LedgerParty.system(LedgerAccounts.AUCTION_HOUSE),
                auction.getSellPrice(), "auction.buy");
        ledger.post(List.of(payment), "auction:" + auction.getAuctionID()).thenAccept(receipt -> {
            buying.remove(auction.getAuctionID());
            if (receipt.isPosted() && !auction.isSold()) {
                completePurchase(player, auction);
                return;
            }
            final String reason = switch (receipt.getStatus()) {
                case DUPLICATE, POSTED -> "shops.auctionhouse.already-sold";
                case INSUFFICIENT_FUNDS -> "shops.listener.auctionhouse.insufficient-funds";
                default -> "core.ledger.unavailable";
            };
            UtilMessage.message(player, "core.prefix.auction-house", reason);
        });
    }

    private void completePurchase(Player player, Auction auction) {
        auction.setSold(true);
        getAuctionRepository().setSold(auction, true);
        getAuctionRepository().saveAuctionTransaction(player.getUniqueId(), auction);

        getDeliveryService().deliverCurrency(auction.getSeller(), (int) (auction.getSellPrice() * 0.95),
                "auction-proceeds:" + auction.getAuctionID());

        if (getDeliveryService().deliverAuction(player.getUniqueId(), auction)) {
            auction.setDelivered(true);
            getAuctionRepository().setDelivered(auction, true);
            getActiveAuctions().remove(auction);
        }

        itemFactory.fromItemStack(auction.getItemStack()).ifPresent(itemInstance -> {
            itemInstance.getComponent(UUIDProperty.class).ifPresent(uuidProperty -> {
                final UUID uuid = uuidProperty.getUniqueId();
                log.info("{} bought ({}) on the auction house for ${}", player.getName(), uuid, auction.getSellPrice())
                        .setAction("ITEM_AUCTION_BUY")
                        .addItemContext(itemRegistry, itemInstance)
                        .addContext(LogContext.CURRENCY, String.valueOf(auction.getSellPrice()))
                        .addClientContext(player).submit();
            });
        });
    }

    public void cancelAuction(Player player, Auction auction) {
        AuctionCancelEvent auctionCancelEvent = UtilServer.callEvent(new AuctionCancelEvent(player, auction));
        if (auctionCancelEvent.isCancelled()) {
            UtilMessage.message(player, "core.prefix.auction-house", auctionCancelEvent.getCancelReason());
            return;
        }

        if (auction.isSold() || auction.isDelivered()) {
            UtilMessage.message(player, "core.prefix.auction-house", "shops.auctionhouse.already-sold");
            return;
        }

        if(auction.isCancelled()) {
            UtilMessage.message(player, "core.prefix.auction-house", "shops.auctionhouse.already-cancelled");
            return;
        }

        auction.setCancelled(true);
        getAuctionRepository().setCancelled(auction, true);

        if (getDeliveryService().deliverAuction(auction.getSeller(), auction)) {
            auction.setDelivered(true);
            getAuctionRepository().setDelivered(auction, true);
            getActiveAuctions().remove(auction);
        } else {
            log.warn("Failed to deliver cancelled auction {}", auction.getAuctionID()).submit();
        }

        itemFactory.fromItemStack(auction.getItemStack()).ifPresent(itemInstance -> {
            itemInstance.getComponent(UUIDProperty.class).ifPresent(uuidProperty -> {
                final UUID uuid = uuidProperty.getUniqueId();
                log.info("{} canceled ({}) on the auction house", player.getName(), uuid, auction.getSellPrice())
                        .setAction("ITEM_AUCTION_CANCEL")
                        .addItemContext(itemRegistry, itemInstance)
                        .addClientContext(player).submit();
            });
        });
    }

    public void cancelAllAuctions(UUID seller) {
        Iterator<Auction> auctionIterator = activeAuctions.iterator();
        while (auctionIterator.hasNext()) {
            Auction auction = auctionIterator.next();
            if (!auction.getSeller().equals(seller)) {
                continue;
            }

            getAuctionRepository().setCancelled(auction, true);
            auction.setCancelled(true);

            if (getDeliveryService().deliverAuction(auction.getSeller(), auction)) {
                auction.setDelivered(true);
                getAuctionRepository().setDelivered(auction, true);
                auctionIterator.remove();
            }

            itemFactory.fromItemStack(auction.getItemStack()).ifPresent(itemInstance -> {
                itemInstance.getComponent(UUIDProperty.class).ifPresent(uuidProperty -> {
                    final UUID uuid = uuidProperty.getUniqueId();
                    log.info("({})'s Auction was cancelled", uuid)
                            .setAction("ITEM_AUCTION_CANCEL")
                            .addItemContext(itemRegistry, itemInstance)
                            .submit();
                });
            });
        }
    }

    public boolean deliverAuction(UUID target, Auction auction) {
        if (deliveryService.deliverAuction(target, auction)) {
            auction.setDelivered(true);
            auctionRepository.setDelivered(auction, true);

            return true;
        }

        return false;
    }

}
