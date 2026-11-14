package me.mykindos.betterpvp.core.command.commands.admin.zone;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.time.format.DateTimeFormatter;
import java.util.List;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.command.brigadier.arguments.BPvPArgumentTypes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import me.mykindos.betterpvp.core.world.zone.ZoneManager;
import me.mykindos.betterpvp.core.world.zone.discovery.ZoneDiscoveryRecord;
import me.mykindos.betterpvp.core.world.zone.discovery.ZoneDiscoveryService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

/**
 * {@code /zone discovery list <player>} prints the zones a player has discovered, with timestamps.
 */
@Singleton
@BrigadierSubCommand(ZoneDiscoverySubCommand.class)
public class ZoneDiscoveryListSubCommand extends AbstractZoneDiscoverySubCommand {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Inject
    public ZoneDiscoveryListSubCommand(ClientManager clientManager, ZoneDiscoveryService discoveryService, ZoneManager zoneManager, Core core) {
        super(clientManager, discoveryService, zoneManager, core);
    }

    @Override
    public String getName() {
        return "list";
    }

    @Override
    public String getDescription() {
        return "List the zones a player has discovered";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.zone.discovery.list.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("player", BPvPArgumentTypes.playerName())
                        .executes(context -> {
                            final CommandSender sender = context.getSource().getSender();
                            withPlayer(sender, context.getArgument("player", String.class), target ->
                                    discoveryService.list(target).thenAccept(records ->
                                            UtilServer.runTask(core, () -> printDiscoveries(sender, target, records))));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private static void printDiscoveries(CommandSender sender, Client target, List<ZoneDiscoveryRecord> records) {
        final Component name = Component.text(target.getName(), NamedTextColor.YELLOW);
        if (records.isEmpty()) {
            UtilMessage.message(sender, "core.prefix.zones", "core.command.zone.discovery.none", name);
            return;
        }
        UtilMessage.message(sender, "core.prefix.zones", "core.command.zone.discovery.header",
                name, Component.text(records.size(), NamedTextColor.YELLOW));
        for (ZoneDiscoveryRecord record : records) {
            UtilMessage.message(sender, "core.prefix.zones", Component.text("  ")
                    .append(Component.text(record.getDisplayName(), NamedTextColor.WHITE))
                    .append(Component.text(" (" + record.getZoneKey() + ") ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(TIMESTAMP_FORMAT.format(record.getDiscoveredAt()), NamedTextColor.GRAY)));
        }
    }
}
