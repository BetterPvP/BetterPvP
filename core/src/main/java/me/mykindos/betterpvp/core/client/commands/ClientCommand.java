package me.mykindos.betterpvp.core.client.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.Rank;
import me.mykindos.betterpvp.core.client.events.ClientAdministrateEvent;
import me.mykindos.betterpvp.core.client.events.ClientSearchEvent;
import me.mykindos.betterpvp.core.client.gamer.properties.GamerProperty;
import me.mykindos.betterpvp.core.client.properties.ClientProperty;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.command.brigadier.arguments.BPvPArgumentTypes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import me.mykindos.betterpvp.core.utilities.UtilTime;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Client administration: {@code admin}, {@code search}, {@code promote}, {@code demote}, {@code count} and
 * {@code setmediachannel}.
 */
@Singleton
public class ClientCommand extends BrigadierCommand {

    static final String PREFIX = "core.prefix.command";

    /** Never demoted by anyone but themselves. */
    private static final String PROTECTED_UUID = "e1f5d06b-685b-46a0-b22c-176d6aefffff";

    @Inject
    public ClientCommand(ClientManager clientManager) {
        super(clientManager);
    }

    static RequiredArgumentBuilder<CommandSourceStack, String> targetArgument() {
        return BrigadierNodes.argument("player", BPvPArgumentTypes.playerName());
    }

    /**
     * Moves a client one rank up or down, when the executor outranks the rank they would land on, and tells staff.
     *
     * @param action {@code promote} or {@code demote}, which picks the messages
     */
    static void changeRank(ClientManager clientManager, Player player, Client client, String name, int step, String action) {
        clientManager.search(player).offline(name).thenAcceptAsync(targetOptional -> {
            if (targetOptional.isEmpty()) {
                return;
            }
            final Client targetClient = targetOptional.get();
            if (step < 0 && targetClient.getUuid().equalsIgnoreCase(PROTECTED_UUID)
                    && !client.getUuid().equalsIgnoreCase(targetClient.getUuid())) {
                return;
            }
            final Rank targetRank = Rank.getRank(targetClient.getRank().getId() + step);
            if (targetRank == null) {
                UtilMessage.message(player, PREFIX, step > 0 ? "core.command.client.promote.highest" : "core.command.client.demote.lowest",
                        Component.text(targetClient.getName(), NamedTextColor.YELLOW));
                return;
            }
            if (client.getRank().getId() >= targetRank.getId() && !player.isOp()) {
                UtilMessage.message(player, PREFIX, "core.command.client." + action + ".not_allowed");
                return;
            }
            targetClient.setRank(targetRank);
            targetClient.saveProperty(ClientProperty.SHOW_TAG,
                    targetRank.equals(Rank.ADMIN) ? Rank.ShowTag.NONE.name() : Rank.ShowTag.SHORT.name());
            UtilMessage.message(player, PREFIX, "core.command.client." + action + ".success",
                    UtilMessage.deserialize("<alt2>%s</alt2>", targetClient.getName()), targetRank.getTag(Rank.ShowTag.LONG, true));
            clientManager.save(targetClient);
            clientManager.sendMessageToRank("core.prefix.client", Translations.component("core.command.client." + action + ".staff_broadcast",
                    Component.text(player.getName(), NamedTextColor.YELLOW),
                    Component.text(targetClient.getName(), NamedTextColor.YELLOW),
                    targetRank.getTag(Rank.ShowTag.LONG, true)), Rank.TRIAL_MOD);
            final Player target = Bukkit.getPlayer(targetClient.getUniqueId());
            if (target != null) {
                target.updateCommands();
            }
        });
    }

    @Override
    public String getName() {
        return "client";
    }

    @Override
    public String getDescription() {
        return "Base client command";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.client.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    UtilMessage.message(context.getSource().getSender(), PREFIX, "core.command.client.subcommand.required");
                    return Command.SINGLE_SUCCESS;
                });
    }

    /**
     * {@code /client admin} toggles the executor's administration mode and tells staff.
     */
    @Singleton
    @BrigadierSubCommand(ClientCommand.class)
    public static class AdminSubCommand extends BrigadierCommand {

        @Inject
        public AdminSubCommand(ClientManager clientManager) {
            super(clientManager);
        }

        @Override
        public String getName() {
            return "admin";
        }

        @Override
        public String getDescription() {
            return "Enable administration mode";
        }

        @Override
        public Component getDescriptionComponent() {
            return Translations.component("core.command.admin.description");
        }

        @Override
        public LiteralArgumentBuilder<CommandSourceStack> define() {
            return BrigadierNodes.literal(getName())
                    .executes(context -> {
                        final Player player = getPlayerFromExecutor(context);
                        final Client client = getClientFromExecutor(context);
                        client.setAdministrating(!client.isAdministrating());
                        new ClientAdministrateEvent(client, player, client.isAdministrating()).callEvent();
                        clientManager.save(client);
                        final Component status = client.isAdministrating()
                                ? Translations.component("core.command.client.status.enabled").color(NamedTextColor.GREEN)
                                : Translations.component("core.command.client.status.disabled").color(NamedTextColor.RED);
                        UtilMessage.message(player, PREFIX, "core.command.client.admin.status", status);
                        clientManager.sendMessageToRank("core.prefix.core", Component.text(player.getName(), NamedTextColor.YELLOW)
                                .appendSpace()
                                .append(status)
                                .appendSpace()
                                .append(Translations.component("core.command.client.admin.mode_suffix").color(NamedTextColor.GRAY)), Rank.TRIAL_MOD);
                        player.updateCommands();
                        return Command.SINGLE_SUCCESS;
                    });
        }
    }

    /**
     * {@code /client search <player>} shows a client's previous names, playtime and whatever other modules add.
     */
    @Singleton
    @BrigadierSubCommand(ClientCommand.class)
    public static class SearchSubCommand extends BrigadierCommand {

        @Inject
        public SearchSubCommand(ClientManager clientManager) {
            super(clientManager);
        }

        @Override
        public String getName() {
            return "search";
        }

        @Override
        public String getDescription() {
            return "Search for a client by name";
        }

        @Override
        public Component getDescriptionComponent() {
            return Translations.component("core.command.client-search.description");
        }

        @Override
        public LiteralArgumentBuilder<CommandSourceStack> define() {
            return BrigadierNodes.literal(getName())
                    .then(targetArgument()
                            .executes(context -> {
                                search(context.getSource().getSender(), context.getArgument("player", String.class));
                                return Command.SINGLE_SUCCESS;
                            }));
        }

        private void search(CommandSender sender, String name) {
            clientManager.search(sender).offline(name).thenAcceptAsync(targetOptional -> targetOptional.ifPresentOrElse(target -> {
                final List<Component> result = new ArrayList<>();
                result.add(Translations.component("core.command.client.search.header", UtilMessage.deserialize("<alt2>%s</alt2>", target.getName())));
                final List<String> previousNames = clientManager.getSqlLayer().getPreviousNames(target);
                if (!previousNames.isEmpty()) {
                    result.add(Translations.component("core.command.client.search.previous_names",
                            Component.text(String.join(", ", previousNames), NamedTextColor.WHITE)).color(NamedTextColor.YELLOW));
                }
                final String totalTimePlayed = UtilTime.humanReadableFormat(Duration.ofMillis((Long) target.getProperty(ClientProperty.TIME_PLAYED).orElse(0L)));
                final String seasonTimePlayed = UtilTime.humanReadableFormat(Duration.ofMillis((Long) target.getGamer().getProperty(GamerProperty.TIME_PLAYED).orElse(0L)));
                result.add(Translations.component("core.command.client.search.playtime_total", Component.text(totalTimePlayed, NamedTextColor.WHITE)).color(NamedTextColor.YELLOW));
                result.add(Translations.component("core.command.client.search.playtime_season", Component.text(seasonTimePlayed, NamedTextColor.WHITE)).color(NamedTextColor.YELLOW));
                final ClientSearchEvent searchEvent = UtilServer.callEvent(new ClientSearchEvent(target));
                searchEvent.getAdditionalData().forEach((key, value) ->
                        result.add(Component.text(key + ": ", NamedTextColor.YELLOW).append(Component.text(value.toString(), NamedTextColor.WHITE))));
                result.forEach(message -> UtilMessage.message(sender, message));
            }, () -> UtilMessage.message(sender, PREFIX, "core.command.client.search.not_found")));
        }
    }

    /**
     * {@code /client promote <player>} moves a client one rank up.
     */
    @Singleton
    @BrigadierSubCommand(ClientCommand.class)
    public static class PromoteSubCommand extends BrigadierCommand {

        @Inject
        public PromoteSubCommand(ClientManager clientManager) {
            super(clientManager);
        }

        @Override
        public String getName() {
            return "promote";
        }

        @Override
        public String getDescription() {
            return "Promote a client to a higher rank";
        }

        @Override
        public Component getDescriptionComponent() {
            return Translations.component("core.command.promote.description");
        }

        @Override
        public LiteralArgumentBuilder<CommandSourceStack> define() {
            return BrigadierNodes.literal(getName())
                    .then(targetArgument()
                            .executes(context -> {
                                changeRank(clientManager, getPlayerFromExecutor(context), getClientFromExecutor(context),
                                        context.getArgument("player", String.class), 1, "promote");
                                return Command.SINGLE_SUCCESS;
                            }));
        }
    }

    /**
     * {@code /client demote <player>} moves a client one rank down.
     */
    @Singleton
    @BrigadierSubCommand(ClientCommand.class)
    public static class DemoteSubCommand extends BrigadierCommand {

        @Inject
        public DemoteSubCommand(ClientManager clientManager) {
            super(clientManager);
        }

        @Override
        public String getName() {
            return "demote";
        }

        @Override
        public String getDescription() {
            return "Demote a client to a lower rank";
        }

        @Override
        public Component getDescriptionComponent() {
            return Translations.component("core.command.demote.description");
        }

        @Override
        public LiteralArgumentBuilder<CommandSourceStack> define() {
            return BrigadierNodes.literal(getName())
                    .then(targetArgument()
                            .executes(context -> {
                                changeRank(clientManager, getPlayerFromExecutor(context), getClientFromExecutor(context),
                                        context.getArgument("player", String.class), -1, "demote");
                                return Command.SINGLE_SUCCESS;
                            }));
        }
    }

    /**
     * {@code /client count} shows how many clients the database holds.
     */
    @Singleton
    @BrigadierSubCommand(ClientCommand.class)
    public static class CountSubCommand extends BrigadierCommand {

        @Inject
        public CountSubCommand(ClientManager clientManager) {
            super(clientManager);
        }

        @Override
        public String getName() {
            return "count";
        }

        @Override
        public String getDescription() {
            return "See total client count";
        }

        @Override
        public Component getDescriptionComponent() {
            return Translations.component("core.command.count.description");
        }

        @Override
        public LiteralArgumentBuilder<CommandSourceStack> define() {
            return BrigadierNodes.literal(getName())
                    .executes(context -> {
                        final CommandSender sender = context.getSource().getSender();
                        UtilServer.runTaskAsync(JavaPlugin.getPlugin(Core.class), () ->
                                UtilMessage.message(sender, PREFIX, "core.command.client.count",
                                        Component.text(clientManager.getSqlLayer().getTotalClients(), NamedTextColor.YELLOW)));
                        return Command.SINGLE_SUCCESS;
                    });
        }
    }

    /**
     * {@code /client setmediachannel <player> [url]} sets or clears a client's media channel.
     */
    @Singleton
    @BrigadierSubCommand(ClientCommand.class)
    public static class SetMediaChannelSubCommand extends BrigadierCommand {

        @Inject
        public SetMediaChannelSubCommand(ClientManager clientManager) {
            super(clientManager);
        }

        @Override
        public String getName() {
            return "setmediachannel";
        }

        @Override
        public String getDescription() {
            return "Set the media channel for a client (URL)";
        }

        @Override
        public Component getDescriptionComponent() {
            return Translations.component("core.command.set-media-channel.description");
        }

        @Override
        public LiteralArgumentBuilder<CommandSourceStack> define() {
            return BrigadierNodes.literal(getName())
                    .then(targetArgument()
                            .executes(context -> {
                                setChannel(context.getSource().getSender(), context.getArgument("player", String.class), "");
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(BrigadierNodes.argument("url", StringArgumentType.greedyString())
                                    .executes(context -> {
                                        setChannel(context.getSource().getSender(), context.getArgument("player", String.class),
                                                context.getArgument("url", String.class));
                                        return Command.SINGLE_SUCCESS;
                                    })));
        }

        private void setChannel(CommandSender sender, String name, String url) {
            clientManager.search(sender).offline(name).thenAcceptAsync(target ->
                    target.ifPresent(client -> client.saveProperty(ClientProperty.MEDIA_CHANNEL, url)));
        }
    }
}
