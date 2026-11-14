package me.mykindos.betterpvp.core.chat.filter.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Set;
import me.mykindos.betterpvp.core.chat.filter.IFilterService;
import me.mykindos.betterpvp.core.chat.filter.impl.DatabaseFilterService;
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
 * {@code /chatfilter} manages the words the general chat filter blocks: {@code add}, {@code remove}, {@code list} and
 * {@code reload}.
 */
@Singleton
public class ChatFilterCommand extends BrigadierCommand {

    static final String PREFIX = "core.prefix.command";

    @Inject
    public ChatFilterCommand(ClientManager clientManager) {
        super(clientManager);
    }

    /**
     * Lists filtered words under a header, one per line in alphabetical order.
     */
    static Component wordList(String headerKey, Set<String> words) {
        Component message = Translations.component(headerKey).color(NamedTextColor.GREEN);
        for (String word : words.stream().sorted().toList()) {
            message = message.appendNewline().append(Component.text("- ", NamedTextColor.GRAY))
                    .append(Component.text(word, NamedTextColor.YELLOW));
        }
        return message;
    }

    @Override
    public String getName() {
        return "chatfilter";
    }

    @Override
    public String getDescription() {
        return "Manage general chat filter words";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.chat-filter.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    UtilMessage.message(context.getSource().getSender(), PREFIX, "core.command.chatfilter.usage");
                    return Command.SINGLE_SUCCESS;
                });
    }

    @Singleton
    @BrigadierSubCommand(ChatFilterCommand.class)
    public static class ChatFilterAddCommand extends BrigadierCommand {

        private final IFilterService filterService;

        @Inject
        public ChatFilterAddCommand(ClientManager clientManager, IFilterService filterService) {
            super(clientManager);
            this.filterService = filterService;
        }

        @Override
        public String getName() {
            return "add";
        }

        @Override
        public String getDescription() {
            return "Add a word to the chat filter list";
        }

        @Override
        public Component getDescriptionComponent() {
            return Translations.component("core.command.chat-filter-add.description");
        }

        @Override
        public LiteralArgumentBuilder<CommandSourceStack> define() {
            return BrigadierNodes.literal(getName())
                    .then(BrigadierNodes.argument("word", StringArgumentType.greedyString())
                            .executes(context -> {
                                final CommandSender sender = context.getSource().getSender();
                                final String word = context.getArgument("word", String.class).toLowerCase();
                                filterService.addFilteredWord(word).thenAccept(success -> UtilMessage.message(sender, PREFIX,
                                        success ? "core.command.chatfilter.add.success" : "core.command.chatfilter.add.exists",
                                        Component.text(word, NamedTextColor.YELLOW)));
                                return Command.SINGLE_SUCCESS;
                            }));
        }
    }

    @Singleton
    @BrigadierSubCommand(ChatFilterCommand.class)
    public static class ChatFilterRemoveCommand extends BrigadierCommand {

        private final IFilterService filterService;

        @Inject
        public ChatFilterRemoveCommand(ClientManager clientManager, IFilterService filterService) {
            super(clientManager);
            this.filterService = filterService;
        }

        @Override
        public String getName() {
            return "remove";
        }

        @Override
        public String getDescription() {
            return "Remove a word from the chat filter list";
        }

        @Override
        public Component getDescriptionComponent() {
            return Translations.component("core.command.chat-filter-remove.description");
        }

        @Override
        public LiteralArgumentBuilder<CommandSourceStack> define() {
            return BrigadierNodes.literal(getName())
                    .then(BrigadierNodes.argument("word", StringArgumentType.greedyString())
                            .suggests((context, builder) -> {
                                filterService.getFilteredWords().stream()
                                        .filter(word -> word.startsWith(builder.getRemainingLowerCase()))
                                        .sorted()
                                        .forEach(builder::suggest);
                                return builder.buildFuture();
                            })
                            .executes(context -> {
                                final CommandSender sender = context.getSource().getSender();
                                final String word = context.getArgument("word", String.class).toLowerCase();
                                filterService.removeFilteredWord(word).thenAccept(success -> UtilMessage.message(sender, PREFIX,
                                        success ? "core.command.chatfilter.remove.success" : "core.command.chatfilter.remove.notfound",
                                        Component.text(word, NamedTextColor.YELLOW)));
                                return Command.SINGLE_SUCCESS;
                            }));
        }
    }

    @Singleton
    @BrigadierSubCommand(ChatFilterCommand.class)
    public static class ChatFilterListCommand extends BrigadierCommand {

        private final IFilterService filterService;

        @Inject
        public ChatFilterListCommand(ClientManager clientManager, IFilterService filterService) {
            super(clientManager);
            this.filterService = filterService;
        }

        @Override
        public String getName() {
            return "list";
        }

        @Override
        public String getDescription() {
            return "List all chat filtered words";
        }

        @Override
        public Component getDescriptionComponent() {
            return Translations.component("core.command.chat-filter-list.description");
        }

        @Override
        public LiteralArgumentBuilder<CommandSourceStack> define() {
            return BrigadierNodes.literal(getName())
                    .executes(context -> {
                        final CommandSender sender = context.getSource().getSender();
                        final Set<String> filteredWords = filterService.getFilteredWords();
                        if (filteredWords.isEmpty()) {
                            UtilMessage.message(sender, PREFIX, "core.command.chatfilter.list.empty");
                            return Command.SINGLE_SUCCESS;
                        }
                        UtilMessage.message(sender, PREFIX, wordList("core.command.chatfilter.list.header", filteredWords));
                        return Command.SINGLE_SUCCESS;
                    });
        }
    }

    @Singleton
    @BrigadierSubCommand(ChatFilterCommand.class)
    public static class ChatFilterReloadCommand extends BrigadierCommand {

        private final IFilterService filterService;

        @Inject
        public ChatFilterReloadCommand(ClientManager clientManager, IFilterService filterService) {
            super(clientManager);
            this.filterService = filterService;
        }

        @Override
        public String getName() {
            return "reload";
        }

        @Override
        public String getDescription() {
            return "Reload the chat filter list from the database";
        }

        @Override
        public Component getDescriptionComponent() {
            return Translations.component("core.command.chat-filter-reload.description");
        }

        @Override
        public LiteralArgumentBuilder<CommandSourceStack> define() {
            return BrigadierNodes.literal(getName())
                    .executes(context -> {
                        if (filterService instanceof DatabaseFilterService databaseFilterService) {
                            databaseFilterService.loadFilteredWords();
                            UtilMessage.message(context.getSource().getSender(), PREFIX, "core.command.chatfilter.reload.done");
                        }
                        return Command.SINGLE_SUCCESS;
                    });
        }
    }
}
