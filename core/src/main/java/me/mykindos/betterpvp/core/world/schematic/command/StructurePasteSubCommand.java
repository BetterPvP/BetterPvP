package me.mykindos.betterpvp.core.world.schematic.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Optional;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.world.schematic.Schematic;
import me.mykindos.betterpvp.core.world.schematic.SchematicPlacement;
import me.mykindos.betterpvp.core.world.schematic.SchematicRenderer;
import me.mykindos.betterpvp.core.world.schematic.SchematicService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * {@code /structure paste <name>} places a structure where you stand, turned to match the way you face.
 * <p>
 * A builder's check on what a berth will do at runtime: the rotation is worked out exactly as a real paste does, from
 * the difference between the structure's captured facing and the target's. The data-points are reported rather than
 * registered, since they belong to whoever pasted the structure, not to the world's Mapper file. {@code /structure undo}
 * takes it back out.
 */
@Singleton
@BrigadierSubCommand(StructureCommand.class)
public class StructurePasteSubCommand extends BrigadierCommand {

    private final SchematicService schematics;
    private final SchematicRenderer renderer;
    private final StructurePasteHistory history;

    @Inject
    public StructurePasteSubCommand(ClientManager clientManager, SchematicService schematics, SchematicRenderer renderer,
                                    StructurePasteHistory history) {
        super(clientManager);
        this.schematics = schematics;
        this.renderer = renderer;
        this.history = history;
    }

    @Override
    public String getName() {
        return "paste";
    }

    @Override
    public String getDescription() {
        return "Paste a structure where you stand, rotated to your facing";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.structure.paste.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(StructureCommand.nameArgument()
                        .executes(context -> {
                            final Player player = getPlayerFromExecutor(context);
                            final String name = context.getArgument("name", String.class);
                            final Optional<Schematic> loaded = schematics.load(name);
                            if (loaded.isEmpty()) {
                                UtilMessage.message(player, StructureCommand.PREFIX, Translations.component("core.command.structure.not-found",
                                        Component.text(name)).color(NamedTextColor.RED));
                                return Command.SINGLE_SUCCESS;
                            }
                            final SchematicPlacement placement = SchematicPlacement.facing(loaded.get(), player.getLocation());
                            history.record(player.getUniqueId(), player.getWorld(), renderer.paste(placement));
                            UtilMessage.message(player, StructureCommand.PREFIX, "core.command.structure.paste.pasted",
                                    Component.text(name, NamedTextColor.GREEN), Component.text(placement.getBlocks().size()),
                                    Component.text(placement.markers().size()), Component.text(placement.getQuarterTurns()));
                            return Command.SINGLE_SUCCESS;
                        }));
    }
}
