package me.mykindos.betterpvp.core.command.commands.admin.zone;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.world.zone.Zone;
import me.mykindos.betterpvp.core.world.zone.ZoneManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /zone list [namespace]} lists every registered zone, provider-sourced ones such as clan territories included,
 * grouped by key namespace. A namespace with more than {@link #FOLD_THRESHOLD} zones folds to a count line unless it
 * is the one asked for.
 */
@Singleton
@BrigadierSubCommand(ZoneCommand.class)
public class ZoneListSubCommand extends BrigadierCommand {

    /** Above this many zones in a namespace, the group is folded to a count line unless explicitly expanded. */
    private static final int FOLD_THRESHOLD = 10;

    private final ZoneManager zoneManager;

    @Inject
    public ZoneListSubCommand(ClientManager clientManager, ZoneManager zoneManager) {
        super(clientManager);
        this.zoneManager = zoneManager;
    }

    @Override
    public String getName() {
        return "list";
    }

    @Override
    public String getDescription() {
        return "List all registered zones, grouped by namespace";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.zone.list.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    list(context.getSource().getSender(), null);
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierNodes.argument("namespace", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            zoneManager.getAllZones().stream()
                                    .map(zone -> zone.getKey().namespace())
                                    .distinct()
                                    .filter(namespace -> namespace.toLowerCase().startsWith(builder.getRemainingLowerCase()))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            list(context.getSource().getSender(), context.getArgument("namespace", String.class).toLowerCase());
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private void list(CommandSender sender, @Nullable String filter) {
        final Map<String, List<Zone>> byNamespace = new TreeMap<>();
        for (Zone zone : zoneManager.getAllZones()) {
            byNamespace.computeIfAbsent(zone.getKey().namespace(), namespace -> new ArrayList<>()).add(zone);
        }
        if (byNamespace.isEmpty()) {
            UtilMessage.message(sender, "core.prefix.zones", "core.command.zone.list.none");
            return;
        }
        if (filter != null && !byNamespace.containsKey(filter)) {
            UtilMessage.message(sender, "core.prefix.zones", "core.command.zone.list.no_namespace", Component.text(filter, NamedTextColor.YELLOW));
            return;
        }

        final int total = byNamespace.values().stream().mapToInt(List::size).sum();
        Component header = Translations.component("core.command.zone.list.header",
                Component.text(total, NamedTextColor.YELLOW), Component.text(byNamespace.size(), NamedTextColor.YELLOW));
        if (filter != null) {
            header = header.append(Translations.component("core.command.zone.list.filtered", Component.text(filter)).color(NamedTextColor.GRAY));
        }
        UtilMessage.message(sender, "core.prefix.zones", header);

        for (Map.Entry<String, List<Zone>> entry : byNamespace.entrySet()) {
            final String namespace = entry.getKey();
            if (filter != null && !namespace.equals(filter)) {
                continue;
            }
            final List<Zone> zones = entry.getValue();
            if (filter == null && zones.size() > FOLD_THRESHOLD) {
                UtilMessage.message(sender, "core.prefix.zones", "core.command.zone.list.folded",
                        Component.text(namespace, NamedTextColor.AQUA), Component.text(zones.size()));
                continue;
            }
            UtilMessage.message(sender, "core.prefix.zones", Component.text(namespace, NamedTextColor.AQUA)
                    .append(Component.text(" (" + zones.size() + ")", NamedTextColor.GRAY)));
            zones.sort(Comparator.comparing(zone -> zone.getKey().value()));
            for (Zone zone : zones) {
                UtilMessage.message(sender, "core.prefix.zones", zoneLine(zone));
            }
        }
    }

    private static Component zoneLine(Zone zone) {
        Component line = Component.text("  ");
        if (zone.isDiscoverable()) {
            line = line.append(Component.text("✦ ", NamedTextColor.GOLD));
        }
        line = line.append(Component.text(zone.getKey().value(), NamedTextColor.WHITE))
                .appendSpace()
                .append(zone.getDisplayName().colorIfAbsent(NamedTextColor.GRAY));
        if (!zone.getTags().isEmpty()) {
            line = line.append(Component.text(" " + zone.getTags(), NamedTextColor.DARK_GRAY));
        }
        if (zone.getWorld() != null) {
            line = line.append(Component.text(" (" + zone.getWorld().getName() + ")", NamedTextColor.DARK_GRAY));
        }
        return line;
    }
}
