package me.mykindos.betterpvp.progression.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.command.brigadier.arguments.BPvPArgumentTypes;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.progression.profile.ProfessionProfileManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

/**
 * {@code /progression wipe <player>} deletes a player's profession builds and clears the ones loaded for them.
 */
@Singleton
@BrigadierSubCommand(ProgressionCommand.class)
public class WipeSubCommand extends BrigadierCommand {

    private final ProfessionProfileManager professionProfileManager;

    @Inject
    public WipeSubCommand(ClientManager clientManager, ProfessionProfileManager professionProfileManager) {
        super(clientManager);
        this.professionProfileManager = professionProfileManager;
    }

    @Override
    public String getName() {
        return "wipe";
    }

    @Override
    public String getDescription() {
        return "Wipe a player's profession builds";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("player", BPvPArgumentTypes.playerName())
                        .executes(context -> {
                            final CommandSender sender = context.getSource().getSender();
                            final String targetName = context.getArgument("player", String.class);
                            clientManager.search().offline(targetName).thenAcceptAsync(targetOptional -> {
                                if (targetOptional.isEmpty()) {
                                    UtilMessage.message(sender, "core.prefix.command", "progression.command.wipe.player-not-found",
                                            Component.text(targetName, NamedTextColor.YELLOW));
                                    return;
                                }
                                final Client target = targetOptional.get();
                                professionProfileManager.getRepository().deleteBuildsForClient(target);
                                professionProfileManager.getObject(target.getUniqueId()).ifPresent(profile ->
                                        profile.getProfessionDataMap().forEach((profession, data) -> data.getBuild().getNodes().clear()));
                                UtilMessage.message(sender, "core.prefix.command", "progression.command.wipe.success",
                                        Component.text(target.getName(), NamedTextColor.YELLOW));
                            });
                            return Command.SINGLE_SUCCESS;
                        }));
    }
}
