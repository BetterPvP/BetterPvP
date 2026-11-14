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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

/**
 * {@code /zone discovery reset <player>} wipes every discovery a player has, so each discoverable zone notifies again.
 */
@Singleton
@BrigadierSubCommand(ZoneDiscoverySubCommand.class)
public class ZoneDiscoveryResetSubCommand extends AbstractZoneDiscoverySubCommand {

    @Inject
    public ZoneDiscoveryResetSubCommand(ClientManager clientManager, ZoneDiscoveryService discoveryService, ZoneManager zoneManager, Core core) {
        super(clientManager, discoveryService, zoneManager, core);
    }

    @Override
    public String getName() {
        return "reset";
    }

    @Override
    public String getDescription() {
        return "Wipe all of a player's zone discoveries";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.zone.discovery.reset.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("player", BPvPArgumentTypes.playerName())
                        .executes(context -> {
                            final CommandSender sender = context.getSource().getSender();
                            withPlayer(sender, context.getArgument("player", String.class), target ->
                                    discoveryService.reset(target).thenRun(() -> UtilServer.runTask(core, () ->
                                            UtilMessage.message(sender, "core.prefix.zones", "core.command.zone.discovery.reset",
                                                    Component.text(target.getName(), NamedTextColor.YELLOW)))));
                            return Command.SINGLE_SUCCESS;
                        }));
    }
}
