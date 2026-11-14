package me.mykindos.betterpvp.core.client.gamer.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.UUID;
import java.util.function.BiConsumer;
import lombok.CustomLog;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.gamer.Gamer;
import me.mykindos.betterpvp.core.client.gamer.properties.GamerProperty;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.command.brigadier.arguments.BPvPArgumentTypes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /balance} shows the executor's balance. Its {@code give}, {@code set} and {@code get} subcommands change or
 * read another player's.
 */
@CustomLog
@Singleton
public class BalanceCommand extends BrigadierCommand {

    private static final String ECONOMY_PREFIX = "core.prefix.economy";

    @Inject
    public BalanceCommand(ClientManager clientManager) {
        super(clientManager);
        getAliases().add("bal");
        getAliases().add("money");
    }

    private static Component money(int amount) {
        return Component.text("$" + String.format("%,d", amount), NamedTextColor.GREEN);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> playerArgument() {
        return BrigadierNodes.argument("player", BPvPArgumentTypes.playerName());
    }

    /**
     * Looks up a player by name, online or not, telling the sender when there is no such player.
     */
    private static void withTarget(ClientManager clientManager, CommandSender sender, String name, BiConsumer<Client, Gamer> action) {
        clientManager.search(sender).offline(name).thenAcceptAsync(target -> {
            if (target.isEmpty()) {
                UtilMessage.message(sender, ECONOMY_PREFIX, "core.command.balance.player_not_found",
                        Component.text(name, NamedTextColor.YELLOW));
                return;
            }
            action.accept(target.get(), target.get().getGamer());
        });
    }

    @Override
    public String getName() {
        return "balance";
    }

    @Override
    public String getDescription() {
        return "View your balance";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.balance.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Gamer gamer = getClientFromExecutor(context).getGamer();
                    final int balance = (Integer) gamer.getProperty(GamerProperty.BALANCE).orElse(0);
                    UtilMessage.message(getPlayerFromExecutor(context), ECONOMY_PREFIX, "core.command.balance.view", money(balance));
                    return Command.SINGLE_SUCCESS;
                });
    }

    /**
     * {@code /balance give <player> <amount>} adds to a player's balance and tells them who paid them.
     */
    @Singleton
    @BrigadierSubCommand(BalanceCommand.class)
    public static class GiveBalanceSubCommand extends BrigadierCommand {

        @Inject
        public GiveBalanceSubCommand(ClientManager clientManager) {
            super(clientManager);
        }

        @Override
        public String getName() {
            return "give";
        }

        @Override
        public String getDescription() {
            return "Give money to another player";
        }

        @Override
        public Component getDescriptionComponent() {
            return Translations.component("core.command.give-balance.description");
        }

        @Override
        public LiteralArgumentBuilder<CommandSourceStack> define() {
            return BrigadierNodes.literal(getName())
                    .then(playerArgument()
                            .then(BrigadierNodes.argument("amount", IntegerArgumentType.integer())
                                    .executes(context -> {
                                        final CommandSender sender = context.getSource().getSender();
                                        final int amount = context.getArgument("amount", Integer.class);
                                        withTarget(clientManager, sender, context.getArgument("player", String.class), (target, gamer) -> {
                                            gamer.saveProperty(GamerProperty.BALANCE, gamer.getBalance() + amount);
                                            UtilMessage.message(sender, ECONOMY_PREFIX, "core.command.balance.give.success",
                                                    Component.text(target.getName(), NamedTextColor.YELLOW), money(amount));
                                            final Player targetPlayer = Bukkit.getPlayer(UUID.fromString(gamer.getUuid()));
                                            if (targetPlayer != null) {
                                                UtilMessage.message(targetPlayer, ECONOMY_PREFIX, "core.command.balance.pay.received",
                                                        money(amount), Component.text(sender.getName(), NamedTextColor.YELLOW));
                                            }
                                            log.info("{} gave {} ${}", sender.getName(), target.getName(), amount).submit();
                                        });
                                        return Command.SINGLE_SUCCESS;
                                    })));
        }
    }

    /**
     * {@code /balance set <player> <amount>} sets a player's balance.
     */
    @Singleton
    @BrigadierSubCommand(BalanceCommand.class)
    public static class SetBalanceSubCommand extends BrigadierCommand {

        @Inject
        public SetBalanceSubCommand(ClientManager clientManager) {
            super(clientManager);
        }

        @Override
        public String getName() {
            return "set";
        }

        @Override
        public String getDescription() {
            return "Set a player's balance";
        }

        @Override
        public Component getDescriptionComponent() {
            return Translations.component("core.command.set-balance.description");
        }

        @Override
        public LiteralArgumentBuilder<CommandSourceStack> define() {
            return BrigadierNodes.literal(getName())
                    .then(playerArgument()
                            .then(BrigadierNodes.argument("amount", IntegerArgumentType.integer())
                                    .executes(context -> {
                                        final CommandSender sender = context.getSource().getSender();
                                        final int amount = context.getArgument("amount", Integer.class);
                                        withTarget(clientManager, sender, context.getArgument("player", String.class), (target, gamer) -> {
                                            gamer.saveProperty(GamerProperty.BALANCE, amount);
                                            UtilMessage.message(sender, ECONOMY_PREFIX, "core.command.balance.set.success",
                                                    Component.text(target.getName(), NamedTextColor.YELLOW), money(amount));
                                            log.info("{} set {}'s balance to ${}", sender.getName(), target.getName(), amount).submit();
                                        });
                                        return Command.SINGLE_SUCCESS;
                                    })));
        }
    }

    /**
     * {@code /balance get <player>} shows a player's balance.
     */
    @Singleton
    @BrigadierSubCommand(BalanceCommand.class)
    public static class GetBalanceSubCommand extends BrigadierCommand {

        @Inject
        public GetBalanceSubCommand(ClientManager clientManager) {
            super(clientManager);
        }

        @Override
        public String getName() {
            return "get";
        }

        @Override
        public String getDescription() {
            return "Get a player's balance";
        }

        @Override
        public Component getDescriptionComponent() {
            return Translations.component("core.command.get-balance.description");
        }

        @Override
        public LiteralArgumentBuilder<CommandSourceStack> define() {
            return BrigadierNodes.literal(getName())
                    .then(playerArgument()
                            .executes(context -> {
                                final CommandSender sender = context.getSource().getSender();
                                withTarget(clientManager, sender, context.getArgument("player", String.class), (target, gamer) ->
                                        UtilMessage.message(sender, ECONOMY_PREFIX, "core.command.balance.get.success",
                                                Component.text(target.getName(), NamedTextColor.YELLOW), money(gamer.getBalance())));
                                return Command.SINGLE_SUCCESS;
                            }));
        }
    }
}
