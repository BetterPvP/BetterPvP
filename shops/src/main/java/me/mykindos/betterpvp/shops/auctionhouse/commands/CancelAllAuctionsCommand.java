package me.mykindos.betterpvp.shops.auctionhouse.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.shops.auctionhouse.AuctionManager;
import net.kyori.adventure.text.Component;

/**
 * Cancels every active auction and sends each item back to its seller.
 */
@Singleton
public class CancelAllAuctionsCommand extends BrigadierCommand {

    private final AuctionManager auctionManager;

    @Inject
    public CancelAllAuctionsCommand(ClientManager clientManager, AuctionManager auctionManager) {
        super(clientManager);
        this.auctionManager = auctionManager;
    }

    @Override
    public String getName() {
        return "cancelallauctions";
    }

    @Override
    public String getDescription() {
        return "Cancel all auctions";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("shops.command.cancel-all-auctions.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    auctionManager.getActiveAuctions().forEach(auction -> {
                        auction.setCancelled(true);
                        auctionManager.getAuctionRepository().setCancelled(auction, true);
                        if (auctionManager.getDeliveryService().deliverAuction(auction.getSeller(), auction)) {
                            auctionManager.getAuctionRepository().setDelivered(auction, true);
                        }
                    });
                    auctionManager.getActiveAuctions().clear();
                    return Command.SINGLE_SUCCESS;
                });
    }
}
