package me.mykindos.betterpvp.clans.clans.commands.subcommands.brigadier.admin;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.ClanManager;
import me.mykindos.betterpvp.clans.clans.commands.BrigadierClansCommand;
import me.mykindos.betterpvp.clans.commands.arguments.BPvPClansArgumentTypes;
import me.mykindos.betterpvp.clans.commands.commands.ClanBrigadierCommand;
import me.mykindos.betterpvp.core.client.Rank;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.locale.Translations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;


@Singleton
@BrigadierSubCommand(BrigadierClansCommand.class)
public class BrigadierRecoveryCommand extends ClanBrigadierCommand {

    @Inject
    protected BrigadierRecoveryCommand(ClientManager clientManager, ClanManager clanManager) {
        super(clientManager, clanManager);
        getAliases().add("clanrecovery");
    }

    /**
     * Used in retrieving path for config options
     *
     * @return the name of this command
     */
    @Override
    public String getName() {
        return "triggerrecovery";
    }

    /**
     * Gets the description of this command, used in registration
     *
     * @return the description
     */
    @Override
    public String getDescription() {
        return "Trigger clan recovery for a clan";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("clans.command.clan-recovery.description");
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
                        .executes(context -> {
                            final Clan target = context.getArgument("Clan", Clan.class);
                            clientManager.sendMessageToRank("clans.prefix", Translations.component("clans.command.clan.recovery.notification",
                                    Component.text(context.getSource().getSender().getName(), NamedTextColor.YELLOW),
                                    Component.text(target.getName(), NamedTextColor.YELLOW)), Rank.HELPER);

                            clanManager.startInsuranceRollback(target);
                            return Command.SINGLE_SUCCESS;
                        })
                );
    }
}
