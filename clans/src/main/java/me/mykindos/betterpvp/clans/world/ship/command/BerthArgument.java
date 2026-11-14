package me.mykindos.betterpvp.clans.world.ship.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import java.util.Locale;
import lombok.experimental.UtilityClass;
import me.mykindos.betterpvp.clans.world.ship.Berth;
import me.mykindos.betterpvp.clans.world.ship.ShipService;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * The {@code <berth|*>} argument the disable and enable subcommands share, resolved against the world the executor
 * is standing in. {@code *} is a literal beside the berth argument and means every mooring here.
 */
@UtilityClass
class BerthArgument {

    static final String ALL = "*";

    /**
     * A berth id argument that suggests the moorings in the executor's world.
     */
    static RequiredArgumentBuilder<CommandSourceStack, String> berth(@NotNull ShipService ships) {
        return BrigadierNodes.argument("berth", StringArgumentType.word())
                .suggests((context, builder) -> {
                    if (context.getSource().getExecutor() instanceof Player player) {
                        ships.berths(player.getWorld()).stream()
                                .map(Berth::getId)
                                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase()))
                                .forEach(builder::suggest);
                    }
                    return builder.buildFuture();
                });
    }

    /**
     * @return the berth ids {@code arg} names, or an empty list after telling the player why nothing matched
     */
    static @NotNull List<String> resolve(@NotNull Player player, @NotNull ShipService ships, @NotNull String arg) {
        final List<Berth> berths = ships.berths(player.getWorld());
        if (berths.isEmpty()) {
            UtilMessage.message(player, "clans.prefix.ships", Translations.component("clans.command.ship.no-moorings").color(NamedTextColor.RED));
            return List.of();
        }
        if (ALL.equals(arg)) {
            return berths.stream().map(Berth::getId).toList();
        }
        final List<String> matched = berths.stream()
                .map(Berth::getId)
                .filter(id -> id.equalsIgnoreCase(arg))
                .toList();
        if (matched.isEmpty()) {
            UtilMessage.message(player, "clans.prefix.ships", Translations.component("clans.command.ship.unknown-berth",
                    Component.text(arg, NamedTextColor.YELLOW)).color(NamedTextColor.RED));
        }
        return matched;
    }
}
