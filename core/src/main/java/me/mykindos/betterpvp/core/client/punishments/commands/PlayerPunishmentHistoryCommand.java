package me.mykindos.betterpvp.core.client.punishments.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Comparator;
import java.util.List;
import lombok.CustomLog;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.Rank;
import me.mykindos.betterpvp.core.client.punishments.Punishment;
import me.mykindos.betterpvp.core.client.punishments.PunishmentHandler;
import me.mykindos.betterpvp.core.client.punishments.menu.PunishmentItem;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.arguments.BPvPArgumentTypes;
import me.mykindos.betterpvp.core.inventory.item.Item;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.menu.impl.ViewCollectionMenu;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

@Singleton
@CustomLog
public class PlayerPunishmentHistoryCommand extends BrigadierCommand {
    private final Core core;
    private final PunishmentHandler punishmentHandler;

    @Inject
    public PlayerPunishmentHistoryCommand(ClientManager clientManager, Core core, PunishmentHandler punishmentHandler) {
        super(clientManager);
        this.core = core;
        this.punishmentHandler = punishmentHandler;
        getAliases().add("punishhistory");
        getAliases().add("ph");
    }

    @Override
    public String getName() {
        return "punishmenthistory";
    }

    @Override
    public String getDescription() {
        return "Shows your own punishment history";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.player-punishment-history.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    execute(getPlayerFromExecutor(context), getClientFromExecutor(context));
                    return Command.SINGLE_SUCCESS;
                })
                // Only staff can look up someone else
                .then(BrigadierNodes.argument("player", BPvPArgumentTypes.playerName())
                        .requires(source -> senderHasRank(source, Rank.TRIAL_MOD))
                        .executes(context -> {
                            execute(getPlayerFromExecutor(context), getClientFromExecutor(context),
                                    context.getArgument("player", String.class));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private void execute(Player player, Client client, String... args) {
        try {
            if (args.length >= 1 && client.hasRank(Rank.TRIAL_MOD)) {
                punishmentHandler.getClientManager().search().offline(args[0]).thenAcceptAsync(clientOptional -> {
                    try {
                        clientOptional.ifPresent(target -> {
                            List<Item> items = target.getPunishments().stream()
                                    .sorted(Comparator.comparingLong(Punishment::getApplyTime).reversed())
                                    .sorted(Comparator.comparing(Punishment::isActive).reversed())
                                    .map(punishment -> new PunishmentItem(
                                            punishment,
                                            punishmentHandler,
                                            punishmentHandler.getClientManager().search().offline(punishment.getPunisher()).join().map(Client::getName).orElse(null),
                                            punishmentHandler.getClientManager().search().offline(punishment.getRevoker()).join().map(Client::getName).orElse(null),
                                            client.hasRank(Rank.TRIAL_MOD),
                                            punishment.getRevokeReason(),
                                            null))
                                    .map(Item.class::cast).toList();
                            ViewCollectionMenu viewCollectionMenu = new ViewCollectionMenu(target.getName() + "'s Punish History", items, null);
                            UtilServer.runTask(core, () -> viewCollectionMenu.show(player));
                        });
                    } catch (Exception e) {
                        log.error("Failed to display punishment history for {}", args[0], e).submit();
                    }
                });
            } else {
                UtilServer.runTaskAsync(core, () -> {
                    try {
                        boolean isStaff = client.hasRank(Rank.TRIAL_MOD);
                        List<Item> items = client.getPunishments().stream()
                                .sorted(Comparator.comparingLong(Punishment::getApplyTime).reversed())
                                .sorted(Comparator.comparing(Punishment::isActive).reversed())
                                .map(punishment -> new PunishmentItem(
                                        punishment,
                                        punishmentHandler,
                                        isStaff ? punishmentHandler.getClientManager().search().offline(punishment.getPunisher()).join().map(Client::getName).orElse(null) : null,
                                        isStaff ? punishmentHandler.getClientManager().search().offline(punishment.getRevoker()).join().map(Client::getName).orElse(null) : null,
                                        isStaff,
                                        punishment.getRevokeReason(),
                                        null))
                                .map(Item.class::cast).toList();
                        UtilServer.runTask(core, () -> new ViewCollectionMenu(client.getName() + "'s Punish History", items, null).show(player));
                    } catch (Exception e) {
                        log.error("Failed to display punishment history for {}", client.getName(), e).submit();
                    }
                });
            }
        } catch (Exception e) {
            log.error("Failed to execute punishment history command for {}", player.getName(), e).submit();
        }

    }
}
