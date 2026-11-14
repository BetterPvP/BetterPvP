package me.mykindos.betterpvp.core.command.commands.admin.zone;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.command.brigadier.arguments.BPvPArgumentTypes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import me.mykindos.betterpvp.core.world.zone.ZoneManager;
import me.mykindos.betterpvp.core.world.zone.discovery.ZoneDiscoveryService;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

/**
 * {@code /zone discovery remove <player> <zoneKey>} removes one discovery, so re-entering the zone notifies again.
 */
@Singleton
@BrigadierSubCommand(ZoneDiscoverySubCommand.class)
public class ZoneDiscoveryRemoveSubCommand extends AbstractZoneDiscoverySubCommand {

    @Inject
    public ZoneDiscoveryRemoveSubCommand(ClientManager clientManager, ZoneDiscoveryService discoveryService, ZoneManager zoneManager, Core core) {
        super(clientManager, discoveryService, zoneManager, core);
    }

    @Override
    public String getName() {
        return "remove";
    }

    @Override
    public String getDescription() {
        return "Remove a single zone discovery from a player";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.zone.discovery.remove.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("player", BPvPArgumentTypes.playerName())
                        .then(zoneArgument("zone")
                                .executes(context -> {
                                    final CommandSender sender = context.getSource().getSender();
                                    zone(sender, context.getArgument("zone", Key.class)).ifPresent(zone ->
                                            withPlayer(sender, context.getArgument("player", String.class), target ->
                                                    discoveryService.remove(target, zone).thenRun(() -> UtilServer.runTask(core, () ->
                                                            UtilMessage.message(sender, "core.prefix.zones", "core.command.zone.discovery.removed",
                                                                    Component.text(zone.getKey().asString(), NamedTextColor.WHITE),
                                                                    Component.text(target.getName(), NamedTextColor.YELLOW))))));
                                    return Command.SINGLE_SUCCESS;
                                })));
    }
}
