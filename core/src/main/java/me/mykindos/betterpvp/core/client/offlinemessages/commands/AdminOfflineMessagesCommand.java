package me.mykindos.betterpvp.core.client.offlinemessages.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Arrays;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.offlinemessages.OfflineMessagesHandler;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.arguments.BPvPArgumentTypes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilTime;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

@Singleton
public class AdminOfflineMessagesCommand extends BrigadierCommand {

    private final OfflineMessagesHandler offlineMessagesHandler;

    @Inject
    public AdminOfflineMessagesCommand(ClientManager clientManager, OfflineMessagesHandler offlineMessagesHandler) {
        super(clientManager);
        this.offlineMessagesHandler = offlineMessagesHandler;
    }

    @Override
    public String getName() {
        return "adminofflinemessages";
    }

    @Override
    public String getDescription() {
        return "Retrieve another players offline messages from a given time period";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.admin-offline-messages.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    execute(getPlayerFromExecutor(context), getClientFromExecutor(context));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierNodes.argument("player", BPvPArgumentTypes.playerName())
                        .then(OfflineMessagesCommand.period(context -> {
                            execute(getPlayerFromExecutor(context), getClientFromExecutor(context),
                                    context.getArgument("player", String.class),
                                    String.valueOf(context.getArgument("number", Long.class)), context.getArgument("unit", String.class));
                            return Command.SINGLE_SUCCESS;
                        })));
    }

    private void execute(Player player, Client client, String... args) {
        if (args.length < 3) {
            UtilMessage.message(player, "core.prefix.offlinemessages", "core.command.offlinemessages.admin.usage");
            return;
        }

        clientManager.search().offline(args[0]).thenAcceptAsync((targetOptional) -> {
            if (targetOptional.isEmpty()) {
                UtilMessage.message(player, "core.prefix.core", "core.command.balance.player_not_found",
                        Component.text(args[0], NamedTextColor.YELLOW));
                return;
            }
            Client target = targetOptional.get();
            offlineMessagesHandler.showMenuForMessagesForClientAfterTime(player, target, UtilTime.parseTimeString(Arrays.copyOfRange(args, 1, 3)));
        });

    }

}
