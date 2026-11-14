package me.mykindos.betterpvp.core.chat.filter.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Set;
import me.mykindos.betterpvp.core.chat.filter.ShadowChatFilterManager;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

/**
 * {@code /shadowfilter} manages the shadow filter's words: {@code add}, {@code remove}, {@code list} and
 * {@code reload}.
 */
@Singleton
public class ShadowFilterCommand extends BrigadierCommand {

    @Inject
    public ShadowFilterCommand(ClientManager clientManager) {
        super(clientManager);
    }

    @Override
    public String getName() {
        return "shadowfilter";
    }

    @Override
    public String getDescription() {
        return "Manage shadow chat filter words";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.shadow-filter.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    UtilMessage.message(context.getSource().getSender(), ChatFilterCommand.PREFIX, "core.command.shadowfilter.usage");
                    return Command.SINGLE_SUCCESS;
                });
    }

    @Singleton
    @BrigadierSubCommand(ShadowFilterCommand.class)
    public static class ShadowFilterAddCommand extends BrigadierCommand {

        private final ShadowChatFilterManager filterManager;

        @Inject
        public ShadowFilterAddCommand(ClientManager clientManager, ShadowChatFilterManager filterManager) {
            super(clientManager);
            this.filterManager = filterManager;
        }

        @Override
        public String getName() {
            return "add";
        }

        @Override
        public String getDescription() {
            return "Add a word to the shadow filter list";
        }

        @Override
        public Component getDescriptionComponent() {
            return Translations.component("core.command.shadow-filter-add.description");
        }

        @Override
        public LiteralArgumentBuilder<CommandSourceStack> define() {
            return BrigadierNodes.literal(getName())
                    .then(BrigadierNodes.argument("word", StringArgumentType.greedyString())
                            .executes(context -> {
                                final CommandSender sender = context.getSource().getSender();
                                final String word = context.getArgument("word", String.class).toLowerCase();
                                filterManager.addFilteredWord(word, getClientFromExecutor(context)).thenAccept(success ->
                                        UtilMessage.message(sender, ChatFilterCommand.PREFIX,
                                                success ? "core.command.shadowfilter.add.success" : "core.command.shadowfilter.add.exists",
                                                Component.text(word)));
                                return Command.SINGLE_SUCCESS;
                            }));
        }
    }

    @Singleton
    @BrigadierSubCommand(ShadowFilterCommand.class)
    public static class ShadowFilterRemoveCommand extends BrigadierCommand {

        private final ShadowChatFilterManager filterManager;

        @Inject
        public ShadowFilterRemoveCommand(ClientManager clientManager, ShadowChatFilterManager filterManager) {
            super(clientManager);
            this.filterManager = filterManager;
        }

        @Override
        public String getName() {
            return "remove";
        }

        @Override
        public String getDescription() {
            return "Remove a word from the shadow filter list";
        }

        @Override
        public Component getDescriptionComponent() {
            return Translations.component("core.command.shadow-filter-remove.description");
        }

        @Override
        public LiteralArgumentBuilder<CommandSourceStack> define() {
            return BrigadierNodes.literal(getName())
                    .then(BrigadierNodes.argument("word", StringArgumentType.greedyString())
                            .suggests((context, builder) -> {
                                filterManager.getFilteredWords().stream()
                                        .filter(word -> word.startsWith(builder.getRemainingLowerCase()))
                                        .sorted()
                                        .forEach(builder::suggest);
                                return builder.buildFuture();
                            })
                            .executes(context -> {
                                final CommandSender sender = context.getSource().getSender();
                                final String word = context.getArgument("word", String.class).toLowerCase();
                                filterManager.removeFilteredWord(word).thenAccept(success -> UtilMessage.message(sender, ChatFilterCommand.PREFIX,
                                        success ? "core.command.shadowfilter.remove.success" : "core.command.shadowfilter.remove.not_found",
                                        Component.text(word)));
                                return Command.SINGLE_SUCCESS;
                            }));
        }
    }

    @Singleton
    @BrigadierSubCommand(ShadowFilterCommand.class)
    public static class ShadowFilterListCommand extends BrigadierCommand {

        private final ShadowChatFilterManager filterManager;

        @Inject
        public ShadowFilterListCommand(ClientManager clientManager, ShadowChatFilterManager filterManager) {
            super(clientManager);
            this.filterManager = filterManager;
        }

        @Override
        public String getName() {
            return "list";
        }

        @Override
        public String getDescription() {
            return "List all shadow filtered words";
        }

        @Override
        public Component getDescriptionComponent() {
            return Translations.component("core.command.shadow-filter-list.description");
        }

        @Override
        public LiteralArgumentBuilder<CommandSourceStack> define() {
            return BrigadierNodes.literal(getName())
                    .executes(context -> {
                        final CommandSender sender = context.getSource().getSender();
                        final Set<String> filteredWords = filterManager.getFilteredWords();
                        if (filteredWords.isEmpty()) {
                            UtilMessage.message(sender, ChatFilterCommand.PREFIX, "core.command.shadowfilter.list.empty");
                            return Command.SINGLE_SUCCESS;
                        }
                        UtilMessage.message(sender, ChatFilterCommand.PREFIX,
                                ChatFilterCommand.wordList("core.command.shadowfilter.list.header", filteredWords));
                        return Command.SINGLE_SUCCESS;
                    });
        }
    }

    @Singleton
    @BrigadierSubCommand(ShadowFilterCommand.class)
    public static class ShadowFilterReloadCommand extends BrigadierCommand {

        private final ShadowChatFilterManager filterManager;

        @Inject
        public ShadowFilterReloadCommand(ClientManager clientManager, ShadowChatFilterManager filterManager) {
            super(clientManager);
            this.filterManager = filterManager;
        }

        @Override
        public String getName() {
            return "reload";
        }

        @Override
        public String getDescription() {
            return "Reload the shadow filter list from the database";
        }

        @Override
        public Component getDescriptionComponent() {
            return Translations.component("core.command.shadow-filter-reload.description");
        }

        @Override
        public LiteralArgumentBuilder<CommandSourceStack> define() {
            return BrigadierNodes.literal(getName())
                    .executes(context -> {
                        filterManager.loadFilteredWords();
                        UtilMessage.message(context.getSource().getSender(), ChatFilterCommand.PREFIX, "core.command.shadowfilter.reload.done");
                        return Command.SINGLE_SUCCESS;
                    });
        }
    }
}
