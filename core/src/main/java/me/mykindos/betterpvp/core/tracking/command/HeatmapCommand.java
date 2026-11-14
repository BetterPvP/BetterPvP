package me.mykindos.betterpvp.core.tracking.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.tracking.ActivitySnapshot;
import me.mykindos.betterpvp.core.tracking.PlayerActivityService;
import me.mykindos.betterpvp.core.tracking.model.GridKey;
import me.mykindos.betterpvp.core.tracking.model.HeatCell;
import me.mykindos.betterpvp.core.tracking.model.ZoneClassification;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Player activity heatmap tooling: {@code top} lists the hottest cells, {@code here} shows the executor's own cell.
 */
@Singleton
public class HeatmapCommand extends BrigadierCommand {

    @Inject
    public HeatmapCommand(ClientManager clientManager) {
        super(clientManager);
    }

    @Override
    public String getName() {
        return "heatmap";
    }

    @Override
    public String getDescription() {
        return "Inspect the player activity heatmap";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.heatmap.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    UtilMessage.message(context.getSource().getSender(), "core.prefix.heatmap",
                            Translations.component("core.heatmap.usage").color(NamedTextColor.GREEN));
                    return Command.SINGLE_SUCCESS;
                });
    }

    private static Component zoneColor(ZoneClassification zone) {
        return switch (zone) {
            case HOTSPOT -> Translations.component("core.heatmap.zone.hotspot").color(NamedTextColor.RED);
            case ACTIVE -> Translations.component("core.heatmap.zone.active").color(NamedTextColor.GOLD);
            case QUIET -> Translations.component("core.heatmap.zone.quiet").color(NamedTextColor.YELLOW);
            case EMPTY -> Translations.component("core.heatmap.zone.empty").color(NamedTextColor.GRAY);
        };
    }

    /**
     * {@code /heatmap top [limit]} lists the hottest cells of the latest snapshot, each one clickable to teleport to.
     */
    @Singleton
    @BrigadierSubCommand(HeatmapCommand.class)
    public static class TopSubCommand extends BrigadierCommand {

        private static final int DEFAULT_LIMIT = 10;

        private final PlayerActivityService activityService;

        @Inject
        public TopSubCommand(ClientManager clientManager, PlayerActivityService activityService) {
            super(clientManager);
            this.activityService = activityService;
        }

        @Override
        public String getName() {
            return "top";
        }

        @Override
        public String getDescription() {
            return "List the hottest grid cells";
        }

        @Override
        public Component getDescriptionComponent() {
            return Translations.component("core.command.top.description");
        }

        @Override
        public LiteralArgumentBuilder<CommandSourceStack> define() {
            return BrigadierNodes.literal(getName())
                    .executes(context -> top(context, DEFAULT_LIMIT))
                    .then(BrigadierNodes.argument("limit", IntegerArgumentType.integer(1, 50))
                            .executes(context -> top(context, context.getArgument("limit", Integer.class))));
        }

        private int top(CommandContext<CommandSourceStack> context, int limit) {
            final CommandSender sender = context.getSource().getSender();
            final ActivitySnapshot snapshot = activityService.getSnapshot();
            if (snapshot == null) {
                UtilMessage.message(sender, "core.prefix.heatmap", "core.heatmap.no_snapshot");
                return Command.SINGLE_SUCCESS;
            }
            final List<ActivitySnapshot.Entry> top = snapshot.entries().stream().limit(limit).toList();
            if (top.isEmpty()) {
                UtilMessage.message(sender, "core.prefix.heatmap", "core.heatmap.no_activity");
                return Command.SINGLE_SUCCESS;
            }
            UtilMessage.message(sender, "core.prefix.heatmap", "core.heatmap.top.header",
                    Component.text(top.size(), NamedTextColor.YELLOW),
                    Component.text((System.currentTimeMillis() - snapshot.timestamp()) / 1000, NamedTextColor.YELLOW));
            for (int i = 0; i < top.size(); i++) {
                final ActivitySnapshot.Entry entry = top.get(i);
                final int blockX = entry.key().chunkX() * 16 + 8;
                final int blockZ = entry.key().chunkZ() * 16 + 8;
                final Component line = Translations.component("core.heatmap.top.entry",
                                Component.text("#" + (i + 1), NamedTextColor.GRAY),
                                Component.text(entry.key().world(), NamedTextColor.YELLOW),
                                Component.text(blockX, NamedTextColor.AQUA),
                                Component.text(blockZ, NamedTextColor.AQUA),
                                zoneColor(entry.classification()),
                                Component.text(String.format("%.1f", entry.heatValue()), NamedTextColor.YELLOW),
                                Component.text(entry.currentPlayers(), NamedTextColor.GREEN),
                                Component.text(entry.combatEvents(), NamedTextColor.RED))
                        .clickEvent(ClickEvent.suggestCommand(String.format("/minecraft:tp %d ~ %d", blockX, blockZ)))
                        .hoverEvent(HoverEvent.showText(Translations.component("core.heatmap.teleport_to").color(NamedTextColor.GRAY)
                                .append(Component.text(blockX + ", " + blockZ, NamedTextColor.AQUA))));
                UtilMessage.message(sender, "core.prefix.heatmap", line);
            }
            return Command.SINGLE_SUCCESS;
        }
    }

    /**
     * {@code /heatmap here} shows the heat data for the cell the executor stands in.
     */
    @Singleton
    @BrigadierSubCommand(HeatmapCommand.class)
    public static class HereSubCommand extends BrigadierCommand {

        private final PlayerActivityService activityService;

        @Inject
        public HereSubCommand(ClientManager clientManager, PlayerActivityService activityService) {
            super(clientManager);
            this.activityService = activityService;
        }

        @Override
        public String getName() {
            return "here";
        }

        @Override
        public String getDescription() {
            return "Show heat data for the cell you are standing in";
        }

        @Override
        public Component getDescriptionComponent() {
            return Translations.component("core.command.here.description");
        }

        @Override
        public LiteralArgumentBuilder<CommandSourceStack> define() {
            return BrigadierNodes.literal(getName())
                    .executes(context -> {
                        final Player player = getPlayerFromExecutor(context);
                        final GridKey key = GridKey.of(player.getLocation());
                        final HeatCell cell = activityService.getCells().get(key);
                        UtilMessage.message(player, "core.prefix.heatmap", "core.heatmap.here.cell",
                                Component.text(key.world(), NamedTextColor.YELLOW),
                                Component.text(key.chunkX() * 16 + 8, NamedTextColor.AQUA),
                                Component.text(key.chunkZ() * 16 + 8, NamedTextColor.AQUA),
                                Component.text(key.chunkX(), NamedTextColor.GRAY),
                                Component.text(key.chunkZ(), NamedTextColor.GRAY));
                        if (cell == null) {
                            UtilMessage.message(player, "core.prefix.heatmap", "core.heatmap.here.empty",
                                    Translations.component("core.heatmap.zone.empty").color(NamedTextColor.GRAY));
                            return Command.SINGLE_SUCCESS;
                        }
                        UtilMessage.message(player, "core.prefix.heatmap", "core.heatmap.here.stats",
                                zoneColor(activityService.classify(cell.getHeatValue())),
                                Component.text(String.format("%.2f", cell.getHeatValue()), NamedTextColor.YELLOW),
                                Component.text(String.format("%.2f", cell.getPeakHeat()), NamedTextColor.YELLOW),
                                Component.text(cell.getTotalVisits(), NamedTextColor.GREEN),
                                Component.text(cell.getCombatEvents(), NamedTextColor.RED));
                        return Command.SINGLE_SUCCESS;
                    });
        }
    }
}
