package me.mykindos.betterpvp.core.client.offlinemessages.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Arrays;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.offlinemessages.OfflineMessagesHandler;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilTime;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

@Singleton
public class OfflineMessagesCommand extends BrigadierCommand {

    private final OfflineMessagesHandler offlineMessagesHandler;

    @Inject
    public OfflineMessagesCommand(ClientManager clientManager, OfflineMessagesHandler offlineMessagesHandler) {
        super(clientManager);
        this.offlineMessagesHandler = offlineMessagesHandler;
        getAliases().add("om");
    }

    @Override
    public String getName() {
        return "offlinemessages";
    }

    @Override
    public String getDescription() {
        return "Retrieve your offline messages from a given time period";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.offline-messages.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    execute(getPlayerFromExecutor(context), getClientFromExecutor(context));
                    return Command.SINGLE_SUCCESS;
                })
                .then(period(context -> {
                    execute(getPlayerFromExecutor(context), getClientFromExecutor(context),
                            String.valueOf(context.getArgument("number", Long.class)), context.getArgument("unit", String.class));
                    return Command.SINGLE_SUCCESS;
                }));
    }

    /**
     * A {@code <number> <unit>} period, suggesting the unit names {@link UtilTime#parseTimeString(String[])} accepts.
     */
    static RequiredArgumentBuilder<CommandSourceStack, Long> period(Command<CommandSourceStack> command) {
        return BrigadierNodes.argument("number", LongArgumentType.longArg(0))
                .then(BrigadierNodes.argument("unit", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            Arrays.stream(UtilTime.TimeUnit.values())
                                    .filter(unit -> unit != UtilTime.TimeUnit.BEST)
                                    .map(unit -> unit.name().toLowerCase())
                                    .filter(unit -> unit.startsWith(builder.getRemainingLowerCase()))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(command));
    }

    private void execute(Player player, Client client, String... args) {
        if (args.length < 2) {
            UtilMessage.message(player, "core.prefix.offlinemessages", "core.command.offlinemessages.usage");
            return;
        }
        offlineMessagesHandler.showMenuForMessagesForClientAfterTime(player, client, UtilTime.parseTimeString(args));

    }
}
