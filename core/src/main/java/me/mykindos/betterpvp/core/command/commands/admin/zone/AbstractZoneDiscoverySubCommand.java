package me.mykindos.betterpvp.core.command.commands.admin.zone;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import java.util.Optional;
import java.util.function.Consumer;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import me.mykindos.betterpvp.core.world.zone.Zone;
import me.mykindos.betterpvp.core.world.zone.ZoneManager;
import me.mykindos.betterpvp.core.world.zone.discovery.ZoneDiscoveryService;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

/**
 * Shared base for the {@code /zone discovery} leaf subcommands: holds their services, builds the discoverable-zone
 * argument and looks up the named player.
 */
abstract class AbstractZoneDiscoverySubCommand extends BrigadierCommand {

    protected final ZoneDiscoveryService discoveryService;
    protected final ZoneManager zoneManager;
    protected final Core core;

    protected AbstractZoneDiscoverySubCommand(ClientManager clientManager, ZoneDiscoveryService discoveryService,
                                              ZoneManager zoneManager, Core core) {
        super(clientManager);
        this.discoveryService = discoveryService;
        this.zoneManager = zoneManager;
        this.core = core;
    }

    /**
     * An argument for a zone key, suggesting every discoverable zone.
     */
    protected RequiredArgumentBuilder<CommandSourceStack, Key> zoneArgument(String name) {
        final ArgumentType<Key> type = ArgumentTypes.key();
        return BrigadierNodes.argument(name, type)
                .suggests((context, builder) -> {
                    zoneManager.getAllZones().stream()
                            .filter(Zone::isDiscoverable)
                            .map(zone -> zone.getKey().asString())
                            .filter(key -> key.toLowerCase().startsWith(builder.getRemainingLowerCase()))
                            .forEach(builder::suggest);
                    return builder.buildFuture();
                });
    }

    /**
     * @return the zone with this key, after telling the sender when there is none
     */
    protected Optional<Zone> zone(CommandSender sender, Key key) {
        final Optional<Zone> zone = zoneManager.getZone(key);
        if (zone.isEmpty()) {
            UtilMessage.message(sender, "core.prefix.zones", "core.command.zone.unknown_zone",
                    Component.text(key.asString(), NamedTextColor.YELLOW));
        }
        return zone;
    }

    /**
     * Looks up a player by name, offline included, and runs {@code action} on the main thread when they exist.
     */
    protected void withPlayer(CommandSender sender, String targetName, Consumer<Client> action) {
        clientManager.search(sender).offline(targetName).thenAccept(result -> UtilServer.runTask(core, () -> {
            if (result.isEmpty()) {
                UtilMessage.message(sender, "core.prefix.zones", "core.command.zone.player_not_found",
                        Component.text(targetName, NamedTextColor.YELLOW));
                return;
            }
            action.accept(result.get());
        }));
    }
}
