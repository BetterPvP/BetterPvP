package me.mykindos.betterpvp.clans.clans.commands.subcommands.brigadier.admin;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.ClanManager;
import me.mykindos.betterpvp.clans.clans.commands.BrigadierClansCommand;
import me.mykindos.betterpvp.clans.commands.arguments.BPvPClansArgumentTypes;
import me.mykindos.betterpvp.clans.commands.commands.ClanBrigadierCommand;
import me.mykindos.betterpvp.core.client.Rank;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import me.mykindos.betterpvp.core.locale.Translations;


@Singleton
@BrigadierSubCommand(BrigadierClansCommand.class)
public class BrigadierSetEnergyCommand extends ClanBrigadierCommand {

    @Inject
    protected BrigadierSetEnergyCommand(ClientManager clientManager, ClanManager clanManager) {
        super(clientManager, clanManager);
    }

    /**
     * Used in retrieving path for config options
     *
     * @return the name of this command
     */
    @Override
    public String getName() {
        return "setenergy";
    }

    /**
     * Gets the description of this command, used in registration
     *
     * @return the description
     */
    @Override
    public String getDescription() {
        return "Force set the energy of a clan";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("clans.command.set-energy.description");
    }

    /**
     * Define the command, using normal rank based permissions
     * Requires sender to have required rank and executor to be a player
     *
     * @return the builder to be used in Build
     */
    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("Clan", BPvPClansArgumentTypes.clan())
                        .then(BrigadierNodes.argument("energy", IntegerArgumentType.integer(1))
                                .executes(context -> {
                                    final CommandSender sender = context.getSource().getSender();
                                    final Clan target = context.getArgument("Clan", Clan.class);
                                    final int energy = context.getArgument("energy", Integer.class);

                                    int oldEnergy = target.getEnergy();
                                    target.setEnergy(energy);

                                    UtilMessage.message(sender, "clans.prefix", "clans.command.clan.set-energy.success",
                                            Component.text(target.getEnergy(), NamedTextColor.YELLOW),
                                            Component.text(target.getEnergyTimeRemaining(), NamedTextColor.GOLD),
                                            Component.text(oldEnergy, NamedTextColor.YELLOW));
                                    clientManager.sendMessageToRank("clans.prefix", Translations.component("clans.command.clan.set-energy.mod-notification",
                                            Component.text(sender.getName(), NamedTextColor.YELLOW),
                                            Component.text(target.getName(), NamedTextColor.YELLOW),
                                            Component.text(target.getEnergy(), NamedTextColor.GREEN),
                                            Component.text(target.getEnergyTimeRemaining(), NamedTextColor.YELLOW)), Rank.HELPER);

                                    return Command.SINGLE_SUCCESS;
                                })
                        )

                );
    }

}
