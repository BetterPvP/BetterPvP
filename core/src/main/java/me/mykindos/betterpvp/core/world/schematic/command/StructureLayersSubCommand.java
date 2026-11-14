package me.mykindos.betterpvp.core.world.schematic.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.world.schematic.LayerPlan;
import me.mykindos.betterpvp.core.world.schematic.Schematic;
import me.mykindos.betterpvp.core.world.schematic.SchematicPlacement;
import me.mykindos.betterpvp.core.world.schematic.SchematicRenderer;
import me.mykindos.betterpvp.core.world.schematic.SchematicService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * {@code /structure layers <name> <count>} pastes only the first {@code count} layers of a structure where you stand,
 * the way it would look part way through being built, so a builder can check how their {@code layer:<n>} markers order
 * it. Taken back out with {@code /structure undo}.
 */
@Singleton
@BrigadierSubCommand(StructureCommand.class)
public class StructureLayersSubCommand extends BrigadierCommand {

    private final SchematicService schematics;
    private final SchematicRenderer renderer;
    private final StructurePasteHistory history;

    @Inject
    public StructureLayersSubCommand(ClientManager clientManager, SchematicService schematics, SchematicRenderer renderer,
                                     StructurePasteHistory history) {
        super(clientManager);
        this.schematics = schematics;
        this.renderer = renderer;
        this.history = history;
    }

    @Override
    public String getName() {
        return "layers";
    }

    @Override
    public String getDescription() {
        return "Paste the first few layers of a structure, as it looks part way built";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.structure.layers.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(StructureCommand.nameArgument()
                        .then(BrigadierNodes.argument("count", IntegerArgumentType.integer(0))
                                .executes(context -> {
                                    final Player player = getPlayerFromExecutor(context);
                                    final String name = context.getArgument("name", String.class);
                                    final Optional<Schematic> loaded = schematics.load(name);
                                    if (loaded.isEmpty()) {
                                        UtilMessage.message(player, StructureCommand.PREFIX, Translations.component("core.command.structure.not-found",
                                                Component.text(name)).color(NamedTextColor.RED));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    final LayerPlan plan = LayerPlan.of(loaded.get());
                                    final SchematicPlacement placement = SchematicPlacement.facing(loaded.get(), player.getLocation());
                                    final int shown = Math.clamp(context.getArgument("count", Integer.class), 0, plan.size());
                                    final List<Schematic.PlacedBlock> undo = new ArrayList<>();
                                    for (int layer = 0; layer < shown; layer++) {
                                        undo.addAll(renderer.paste(placement, plan.layer(layer)));
                                    }
                                    history.record(player.getUniqueId(), player.getWorld(), undo);
                                    UtilMessage.message(player, StructureCommand.PREFIX, "core.command.structure.layers.pasted",
                                            Component.text(shown, NamedTextColor.GREEN), Component.text(plan.size(), NamedTextColor.GREEN),
                                            Component.text(name, NamedTextColor.GREEN));
                                    return Command.SINGLE_SUCCESS;
                                })));
    }
}
