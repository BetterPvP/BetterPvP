package me.mykindos.betterpvp.core.command.commands.admin;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import lombok.CustomLog;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.arguments.BPvPArgumentTypes;
import me.mykindos.betterpvp.core.item.component.impl.uuid.UUIDItem;
import me.mykindos.betterpvp.core.logging.LogContext;
import me.mykindos.betterpvp.core.logging.menu.CachedLogMenu;
import me.mykindos.betterpvp.core.logging.repository.LogRepository;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Set;
import me.mykindos.betterpvp.core.client.Rank;
import me.mykindos.betterpvp.core.item.component.impl.uuid.UUIDController;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;

@Singleton
@CustomLog
public class BrigadierSearchCommand extends BrigadierCommand {

    private final LogRepository logRepository;
    private final UUIDController uuidController;

    @Inject
    public BrigadierSearchCommand(ClientManager clientManager, LogRepository logRepository, UUIDController uuidController) {
        super(clientManager);
        this.logRepository = logRepository;
        this.uuidController = uuidController;
    }

    @Override
    public String getName() {
        return "search";
    }

    @Override
    public String getDescription() {
        return "Search logs for a tracked item or a player, or list tracked items online players hold";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.search.description");
    }

    /**
     * Define the command, using normal rank based permissions
     *
     * @return the builder for the command
     */
    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                    .then(BrigadierNodes.literal("online")
                            .executes(context -> {
                                searchOnline(getPlayerFromExecutor(context));
                                return Command.SINGLE_SUCCESS;
                            })
                    ).then(BrigadierNodes.argument("Item Id", BPvPArgumentTypes.uuidItem())
                            .executes(context -> {
                                final UUIDItem item = context.getArgument("Item Id", UUIDItem.class);
                                final Player executor = getPlayerFromExecutor(context);
                                new CachedLogMenu(
                                        item.getIdentifier(),
                                        LogContext.ITEM,
                                        item.getUuid().toString(),
                                        "ITEM_",
                                        CachedLogMenu.ITEM,
                                        JavaPlugin.getPlugin(Core.class),
                                        logRepository,
                                        null)
                                            .show(executor);
                                return Command.SINGLE_SUCCESS;
                            }
                        )
                    ).then(BrigadierNodes.argument("Player Name", BPvPArgumentTypes.playerName())
                        .executes(context -> {
                            final String targetName = context.getArgument("Player Name", String.class);
                            final Player executor = getPlayerFromExecutor(context);
                            getOfflineClientByName(targetName, executor).thenAccept(clientOptional -> {
                                if (clientOptional.isEmpty()) return;
                                final Client targetClient = clientOptional.get();
                                CachedLogMenu cachedLogMenu = new CachedLogMenu(
                                        targetClient.getName(),
                                        LogContext.CLIENT,
                                        targetClient.getUniqueId().toString(),
                                        "ITEM_",
                                        CachedLogMenu.ITEM,
                                        JavaPlugin.getPlugin(Core.class),
                                        logRepository,
                                        null);
                                UtilServer.runTask(JavaPlugin.getPlugin(Core.class), () -> {
                                    cachedLogMenu.show(executor);
                                });
                            });
                            return Command.SINGLE_SUCCESS;
                        })
                );
    }

    /**
     * Lists the tracked items every online player holds, and tells staff who looked.
     */
    private void searchOnline(Player executor) {
        clientManager.sendMessageToRank("core.prefix.search", Translations.component("core.command.search.online.retrieving",
                Component.text(executor.getName())), Rank.TRIAL_MOD);

        boolean anyFound = false;
        for (Player online : Bukkit.getOnlinePlayers()) {
            final Set<UUIDItem> items = uuidController.getUUIDItems(online).keySet();
            if (items.isEmpty()) {
                continue;
            }
            anyFound = true;
            Component component = Translations.component("core.command.search.online.holding", Component.text(online.getName()));
            for (UUIDItem uuidItem : items) {
                component = component.appendNewline().append(Translations.component("core.command.search.online.entry",
                        Component.text(uuidItem.getIdentifier()), Component.text(uuidItem.getUuid().toString())));
            }
            UtilMessage.message(executor, "core.prefix.search", component);
        }
        if (!anyFound) {
            UtilMessage.message(executor, "core.prefix.search", "core.command.search.online.none");
        }
    }
}
