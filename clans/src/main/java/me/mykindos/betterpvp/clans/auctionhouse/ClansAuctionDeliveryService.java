package me.mykindos.betterpvp.clans.auctionhouse;

import lombok.CustomLog;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.ClanManager;
import me.mykindos.betterpvp.clans.clans.core.mailbox.ClanMailbox;
import me.mykindos.betterpvp.clans.clans.core.storage.ClanInventories;
import me.mykindos.betterpvp.clans.clans.writer.ClanBank;
import me.mykindos.betterpvp.clans.economy.PostingRetry;
import me.mykindos.betterpvp.core.framework.economy.ledger.LedgerParty;
import me.mykindos.betterpvp.core.framework.economy.ledger.Movement;
import me.mykindos.betterpvp.core.item.ItemFactory;
import me.mykindos.betterpvp.core.item.ItemInstance;
import me.mykindos.betterpvp.core.utilities.UtilFormat;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilSound;
import me.mykindos.betterpvp.ledger.LedgerAccounts;
import me.mykindos.betterpvp.ledger.LedgerStatus;
import me.mykindos.betterpvp.shops.auctionhouse.Auction;
import me.mykindos.betterpvp.shops.auctionhouse.IAuctionDeliveryService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@CustomLog
public class ClansAuctionDeliveryService implements IAuctionDeliveryService {

    private final ClanManager clanManager;
    private final ItemFactory itemFactory;
    private final ClanInventories inventories;
    private final ClanBank bank;

    public ClansAuctionDeliveryService(ClanManager clanManager, ItemFactory itemFactory, ClanInventories inventories,
                                       ClanBank bank) {
        this.clanManager = clanManager;
        this.itemFactory = itemFactory;
        this.inventories = inventories;
        this.bank = bank;
    }

    /** Puts the item in the target's clan mailbox once no one has the mailbox open, here or on another server. */
    @Override
    public CompletableFuture<Boolean> deliverAuction(UUID target, Auction auction) {

        if (auction.isDelivered()) {
            log.error("Tried to re-deliver an already delivered auction?").submit();
            return CompletableFuture.completedFuture(true);
        }

        Optional<Clan> clanOptional = clanManager.getClanByPlayer(target);
        if (clanOptional.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        Clan clan = clanOptional.get();
        ClanMailbox mailbox = clan.getCore().getMailbox();
        if (mailbox.isLocked()) {
            return CompletableFuture.completedFuture(false);
        }

        final String holder = Objects.requireNonNullElse(Bukkit.getOfflinePlayer(target).getName(), target.toString());
        return inventories.open(clan, ClanInventories.Inventory.MAILBOX, holder).thenApply(answer -> {
            if (!answer.isTaken()) {
                return false;
            }
            if (!mailbox.getContents().add(auction.getItemStack())) {
                log.error("Failed to add items to clan mailbox").submit();
                inventories.close(clan, ClanInventories.Inventory.MAILBOX);
                return false;
            }

            inventories.close(clan, ClanInventories.Inventory.MAILBOX);

            Player player = Bukkit.getPlayer(target);
            if (player != null) {
                final ItemStack itemStack = auction.getItemStack();
                final ItemInstance instance = itemFactory.fromItemStack(itemStack).orElseThrow();
                final Component name = instance.getBaseItem().getItemNameRenderer().createName(instance);
                if (auction.isCancelled()) {
                    UtilMessage.message(player, "core.prefix.auction-house", "clans.auction.listing-cancelled", name);
                } else if (auction.hasExpired()) {
                    UtilMessage.message(player, "core.prefix.auction-house", "clans.auction.listing-expired", name);
                } else {
                    UtilMessage.message(player, "core.prefix.auction-house", "clans.auction.purchase-delivered");
                    UtilSound.playSound(player, Sound.BLOCK_NOTE_BLOCK_BELL, 2f, 1f, false);
                }
            }

            return true;
        });
    }

    /**
     * Pays the seller's clan once the ledger lands it. While the ledger cannot be reached the payment is sent again
     * under its reference, so it lands once however many times it is sent.
     */
    @Override
    public CompletableFuture<Boolean> deliverCurrency(UUID target, int amount, String reference) {
        Optional<Clan> clanOptional = clanManager.getClanByPlayer(target);
        if (clanOptional.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        Clan clan = clanOptional.get();
        final Movement proceeds = new Movement(LedgerParty.system(LedgerAccounts.AUCTION_HOUSE), bank.account(clan),
                amount, "auction.proceeds");
        return bank.post(List.of(proceeds), reference)
                .thenCompose(receipt -> {
                    if (receipt.getStatus() != LedgerStatus.UNAVAILABLE) {
                        return CompletableFuture.completedFuture(receipt);
                    }
                    log.warn("The ledger did not answer auction proceeds {}, asking again", reference).submit();
                    return PostingRetry.untilAnswered(() -> bank.post(List.of(proceeds), reference));
                })
                .thenApply(receipt -> {
                    if (!receipt.isPosted()) {
                        log.error("Auction proceeds {} of ${} to clan {} did not land: {}", reference, amount,
                                clan.getId(), receipt.getStatus()).submit();
                        return false;
                    }
                    clan.messageClan("clans.auction.currency-received", new Object[]{Component.text("$" + UtilFormat.formatNumber(amount), NamedTextColor.GREEN)}, null, true);
                    clan.messageClan("clans.auction.currency-withdraw-info", null, null, true);
                    return true;
                });
    }
}
