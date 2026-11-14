package me.mykindos.betterpvp.core.world.logger.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import net.kyori.adventure.text.Component;

/**
 * Block change logging tools: {@code inspect}, {@code page}, {@code rollback}, {@code restore} and
 * {@code restorechunk}.
 */
@Singleton
public class WorldLoggerCommand extends BrigadierCommand {

    @Inject
    public WorldLoggerCommand(ClientManager clientManager) {
        super(clientManager);
        getAliases().add("wl");
        getAliases().add("co");
    }

    /**
     * The {@code radius:<n> time:<t> [player:<name>]} filters rollback and restore take, suggesting each filter not
     * typed yet.
     */
    public static RequiredArgumentBuilder<CommandSourceStack, String> filtersArgument() {
        return BrigadierNodes.argument("filters", StringArgumentType.greedyString())
                .suggests((context, builder) -> {
                    final String typed = builder.getRemaining();
                    final int lastSpace = typed.lastIndexOf(' ');
                    final String current = typed.substring(lastSpace + 1).toLowerCase();
                    final SuggestionsBuilder next = builder.createOffset(builder.getStart() + lastSpace + 1);
                    suggestFilter(next, typed, current, "radius:");
                    suggestFilter(next, typed, current, "time:");
                    suggestFilter(next, typed, current, "player:");
                    return next.buildFuture();
                });
    }

    private static void suggestFilter(SuggestionsBuilder builder, String typed, String current, String filter) {
        if (filter.startsWith(current) && !typed.contains(filter)) {
            builder.suggest(filter);
        }
    }

    @Override
    public String getName() {
        return "worldlogger";
    }

    @Override
    public String getDescription() {
        return "Base command for world logging";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.world-logger.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName());
    }
}
