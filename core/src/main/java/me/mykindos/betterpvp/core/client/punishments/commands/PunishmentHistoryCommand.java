package me.mykindos.betterpvp.core.client.punishments.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Comparator;
import java.util.List;
import lombok.CustomLog;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.punishments.Punishment;
import me.mykindos.betterpvp.core.client.punishments.PunishmentHandler;
import me.mykindos.betterpvp.core.client.punishments.menu.PunishmentItem;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.inventory.item.Item;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.menu.impl.ViewCollectionMenu;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

@Singleton
@CustomLog
@BrigadierSubCommand(LegacyPunishmentCommand.class)
public class PunishmentHistoryCommand extends PunishmentSubCommand {
    private final PunishmentHandler punishmentHandler;

    @Inject
    public PunishmentHistoryCommand(ClientManager clientManager, PunishmentHandler punishmentHandler) {
        super(clientManager);
        this.punishmentHandler = punishmentHandler;
        getAliases().add("h");
    }

    @Override
    public String getName() {
        return "history";
    }

    @Override
    public String getDescription() {
        return "Punish History Command";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.punishment-history.description");
    }

    @Override
    protected void execute(Player player, Client client, String... args) {
        if (args.length < 1) {
            UtilMessage.message(player, "core.prefix.command", "core.command.punishment.history.usage");
            return;
        }

        clientManager.search().offline(args[0]).thenAcceptAsync(clientOptional -> {
            if (clientOptional.isPresent()) {
                Client target = clientOptional.get();
                processHistory(player, target);

                List<Item> items = target.getPunishments().stream()
                        .sorted(Comparator.comparingLong(Punishment::getApplyTime).reversed())
                        .sorted(Comparator.comparing(Punishment::isActive).reversed())
                        .map(punishment -> new PunishmentItem(
                                punishment,
                                punishmentHandler,
                                punishmentHandler.getClientManager().search().offline(punishment.getPunisher()).join().map(Client::getName).orElse(null),
                                punishmentHandler.getClientManager().search().offline(punishment.getRevoker()).join().map(Client::getName).orElse(null),
                                true,
                                punishment.getRevokeReason(),
                                null))
                        .map(Item.class::cast).toList();
                // Note: Menu title currently requires a String; leaving literal until API accepts translated Components
                UtilServer.runTask(JavaPlugin.getPlugin(Core.class), () -> new ViewCollectionMenu(target.getName() + "'s Punish History", items, null).show(player));

            } else {
                UtilMessage.message(player, "core.prefix.command", "core.command.punishment.client.not_found");
            }
        });
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            UtilMessage.message(sender, "core.prefix.command", "core.command.punishment.history.usage");
            return;
        }

        clientManager.search().offline(args[0]).thenAcceptAsync(clientOptional -> {
            if (clientOptional.isPresent()) {
                Client target = clientOptional.get();

                processHistory(sender, target);
            } else {
                UtilMessage.message(sender, "core.prefix.command", "core.command.punishment.client.not_found");
            }
        });
    }

    protected void processHistory(CommandSender sender, Client target) {
        UtilMessage.message(sender, "core.prefix.command", "core.command.punishment.history.header", Component.text(target.getName()));
        target.getPunishments().sort(Comparator.comparingLong(Punishment::getExpiryTime).reversed());
        target.getPunishments().forEach(punishment -> {
            UtilMessage.message(sender, "", punishment.getPunishmentInformation(clientManager));
        });
    }

    @Override
    protected List<String> suggestions(CommandSender sender, String[] args) {
        return args.length == 1 ? Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase())).toList() : List.of();
    }
}
