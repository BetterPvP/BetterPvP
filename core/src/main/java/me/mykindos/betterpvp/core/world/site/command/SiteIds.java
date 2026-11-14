package me.mykindos.betterpvp.core.world.site.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.world.site.SiteInstance;
import me.mykindos.betterpvp.core.world.site.SiteInstances;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * The instance-id argument the site subcommands share, matched by prefix against the live instances.
 */
final class SiteIds {

    private SiteIds() {
    }

    /**
     * An instance id argument that suggests the live instances' short ids.
     */
    static RequiredArgumentBuilder<CommandSourceStack, String> argument(@NotNull SiteInstances instances) {
        return BrigadierNodes.argument("id", StringArgumentType.word())
                .suggests((context, builder) -> {
                    instances.all().stream()
                            .map(SiteInstance::getShortId)
                            .filter(id -> id.startsWith(builder.getRemainingLowerCase()))
                            .forEach(builder::suggest);
                    return builder.buildFuture();
                });
    }

    /**
     * Resolves a typed id prefix against the live instances, telling the sender when it matches none or several.
     */
    static @NotNull Optional<SiteInstance> resolve(@NotNull CommandSender sender, @NotNull SiteInstances instances,
                                                   @NotNull String prefix) {
        final List<SiteInstance> matches = instances.matchIdPrefix(prefix);
        if (matches.isEmpty()) {
            UtilMessage.message(sender, "core.prefix.sites", Translations.component("core.command.site.no-match",
                    Component.text(prefix, NamedTextColor.YELLOW)).color(NamedTextColor.RED));
            return Optional.empty();
        }
        if (matches.size() > 1) {
            final String ids = matches.stream().map(SiteInstance::getShortId).collect(Collectors.joining(", "));
            UtilMessage.message(sender, "core.prefix.sites", Translations.component("core.command.site.ambiguous",
                    Component.text(ids, NamedTextColor.YELLOW)).color(NamedTextColor.RED));
            return Optional.empty();
        }
        return Optional.of(matches.getFirst());
    }
}
