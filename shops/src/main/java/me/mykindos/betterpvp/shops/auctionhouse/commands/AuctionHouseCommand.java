package me.mykindos.betterpvp.shops.auctionhouse.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.world.model.BPvPWorld;
import me.mykindos.betterpvp.shops.auctionhouse.AuctionManager;
import me.mykindos.betterpvp.shops.auctionhouse.menu.AuctionHouseMenu;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Opens the auction house, out of combat and in the main world unless the executor is administrating.
 */
@Singleton
public class AuctionHouseCommand extends BrigadierCommand {

    private final AuctionManager auctionManager;

    @Inject
    public AuctionHouseCommand(ClientManager clientManager, AuctionManager auctionManager) {
        super(clientManager);
        this.auctionManager = auctionManager;
        getAliases().add("ah");
    }

    @Override
    public String getName() {
        return "auctionhouse";
    }

    @Override
    public String getDescription() {
        return "Open the auction house menu";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("shops.command.auction-house.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    final Client client = getClientFromExecutor(context);
                    if (!client.isAdministrating()) {
                        if (client.getGamer().isInCombat()) {
                            UtilMessage.message(player, "shops.prefix", "shops.command.auctionhouse.no-combat");
                            return Command.SINGLE_SUCCESS;
                        }
                        if (!player.getWorld().getName().equalsIgnoreCase(BPvPWorld.MAIN_WORLD_NAME)) {
                            UtilMessage.message(player, "shops.prefix", "shops.command.auctionhouse.invalid-world");
                            return Command.SINGLE_SUCCESS;
                        }
                    }
                    new AuctionHouseMenu(auctionManager).show(player);
                    return Command.SINGLE_SUCCESS;
                });
    }
}
