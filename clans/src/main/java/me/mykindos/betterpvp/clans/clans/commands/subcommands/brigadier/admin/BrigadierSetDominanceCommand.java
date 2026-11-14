package me.mykindos.betterpvp.clans.clans.commands.subcommands.brigadier.admin;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.ClanManager;
import me.mykindos.betterpvp.clans.clans.commands.BrigadierClansCommand;
import me.mykindos.betterpvp.clans.commands.arguments.BPvPClansArgumentTypes;
import me.mykindos.betterpvp.clans.commands.arguments.exceptions.ClanArgumentException;
import me.mykindos.betterpvp.clans.commands.commands.ClanBrigadierCommand;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.components.clans.data.ClanEnemy;
import me.mykindos.betterpvp.core.locale.Translations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;


@Singleton
@BrigadierSubCommand(BrigadierClansCommand.class)
public class BrigadierSetDominanceCommand extends ClanBrigadierCommand {

    @Inject
    protected BrigadierSetDominanceCommand(ClientManager clientManager, ClanManager clanManager) {
        super(clientManager, clanManager);
    }

    /**
     * Used in retrieving path for config options
     *
     * @return the name of this command
     */
    @Override
    public String getName() {
        return "setdominance";
    }

    /**
     * Gets the description of this command, used in registration
     *
     * @return the description
     */
    @Override
    public String getDescription() {
        return "sets the dominance of the current clan against the target clan";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("clans.command.set-dominance.description");
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
                .then(Commands.argument("Target Clan", BPvPClansArgumentTypes.enemyClan())
                        //do it by clan you are in
                        .then(Commands.argument("Dominance", DoubleArgumentType.doubleArg(-100.0, 100.0))
                                .executes(context -> {
                                    Clan targetClan = context.getArgument("Target Clan", Clan.class);
                                    double newDominance = context.getArgument("Dominance", double.class);

                                    Clan originClan = getClanByExecutor(context);
                                    applyNewDominance(targetClan, originClan, newDominance);
                                    return Command.SINGLE_SUCCESS;
                                })
                                .requires(this::executorHasAClan)
                        )
                );
    }

    private void applyNewDominance(Clan target, Clan origin, double newDominance) throws CommandSyntaxException {
        //the clan the DOM will be applied to
        final Clan applyClan = newDominance > 0 ? target : origin;
        //the Clan the DOM will be zeroed to
        final Clan zeroClan = newDominance > 0 ? origin : target;

        final double actualDominance = Math.abs(newDominance);

        final ClanEnemy applyClanEnemy = applyClan.getEnemy(zeroClan).orElseThrow(() -> ClanArgumentException.CLAN_NOT_ENEMY_OF_CLAN.create(applyClan, zeroClan));
        final ClanEnemy zeroClanEnemy = zeroClan.getEnemy(applyClan).orElseThrow(() -> ClanArgumentException.CLAN_NOT_ENEMY_OF_CLAN.create(zeroClan, applyClan));

        applyClanEnemy.setDominance(actualDominance);
        zeroClanEnemy.setDominance(0);

        clanManager.getRepository().updateDominance(applyClan, applyClanEnemy);
        clanManager.getRepository().updateDominance(zeroClan, zeroClanEnemy);

        zeroClan.messageClan("clans.command.clan.set-dominance.clan-notification", new Object[]{
                Component.text(applyClan.getName(), NamedTextColor.RED), Component.text(actualDominance + "%", NamedTextColor.GREEN)}, null, true);
        applyClan.messageClan("clans.command.clan.set-dominance.target-notification", new Object[]{
                Component.text(zeroClan.getName(), NamedTextColor.RED), Component.text("-" + actualDominance + "%", NamedTextColor.RED)}, null, true);

    }


}
