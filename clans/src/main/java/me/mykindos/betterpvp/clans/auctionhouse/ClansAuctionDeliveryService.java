package me.mykindos.betterpvp.clans.auctionhouse;

import lombok.CustomLog;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.ClanManager;
import me.mykindos.betterpvp.clans.clans.core.mailbox.ClanMailbox;
import me.mykindos.betterpvp.clans.clans.writer.ClanBank;
import me.mykindos.betterpvp.clans.clans.writer.ClanWriter;
import me.mykindos.betterpvp.core.framework.economy.ledger.LedgerParty;
import me.mykindos.betterpvp.core.framework.economy.ledger.Movement;
import me.mykindos.betterpvp.core.item.ItemFactory;
import me.mykindos.betterpvp.core.item.ItemInstance;
import me.mykindos.betterpvp.core.utilities.UtilFormat;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilSound;
import me.mykindos.betterpvp.ledger.LedgerAccounts;
import me.mykindos.betterpvp.shops.auctionhouse.Auction;
import me.mykindos.betterpvp.shops.auctionhouse.IAuctionDeliveryService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@CustomLog
public class ClansAuctionDeliveryService implements IAuctionDeliveryService {

    private final ClanManager clanManager;
    private final ItemFactory itemFactory;
    private final ClanWriter writer;
    private final ClanBank bank;

    public ClansAuctionDeliveryService(ClanManager clanManager, ItemFactory itemFactory, ClanWriter writer,
                                       ClanBank bank) {
        this.clanManager = clanManager;
        this.itemFactory = itemFactory;
        this.writer = writer;
        this.bank = bank;
    }

    @Override
    public boolean deliverAuction(UUID target, Auction auction) {

        if (auction.isDelivered()) {
            log.error("Tried to re-deliver an already delivered auction?").submit();
            return true;
        }

        Optional<Clan> clanOptional = clanManager.getClanByPlayer(target);
        if (clanOptional.isPresent()) {
            Clan clan = clanOptional.get();
            ClanMailbox mailbox = clan.getCore().getMailbox();
            if (mailbox.isLocked()) {
                return false;
            }

            if (!mailbox.getContents().add(auction.getItemStack())) {
                log.error("Failed to add items to clan mailbox").submit();
                return false;
            }

            writer.saveMailbox(clan);

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
        }

        return false;
    }

    @Override
    public boolean deliverCurrency(UUID target, int amount, String reference) {
        Optional<Clan> clanOptional = clanManager.getClanByPlayer(target);
        if (clanOptional.isPresent()) {
            Clan clan = clanOptional.get();
            final Movement proceeds = new Movement(LedgerParty.system(LedgerAccounts.AUCTION_HOUSE), bank.account(clan),
                    amount, "auction.proceeds");
            bank.post(List.of(proceeds), reference).thenAccept(receipt -> {
                if (!receipt.isPosted()) {
                    log.error("Auction proceeds {} of ${} to clan {} did not land: {}", reference, amount, clan.getId(),
                            receipt.getStatus()).submit();
                    return;
                }
                clan.messageClan("clans.auction.currency-received", new Object[]{Component.text("$" + UtilFormat.formatNumber(amount), NamedTextColor.GREEN)}, null, true);
                clan.messageClan("clans.auction.currency-withdraw-info", null, null, true);
            });
            return true;
        }

        return false;
    }
}
