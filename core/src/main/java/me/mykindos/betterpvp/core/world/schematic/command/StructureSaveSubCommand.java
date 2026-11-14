package me.mykindos.betterpvp.core.world.schematic.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.io.File;
import java.util.Optional;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.world.schematic.Schematic;
import me.mykindos.betterpvp.core.world.schematic.SchematicService;
import me.mykindos.betterpvp.core.world.schematic.StructureAnchor;
import me.mykindos.betterpvp.core.world.schematic.StructureCapture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * {@code /structure save <name>} captures the WorldEdit selection and every data-point inside it.
 * <p>
 * Where you stand and which way you face becomes the structure's anchor, so stand on the block that should land on the
 * marker and face the way the build should point.
 */
@Singleton
@BrigadierSubCommand(StructureCommand.class)
public class StructureSaveSubCommand extends BrigadierCommand {

    private final StructureCapture capture;
    private final SchematicService schematics;

    @Inject
    public StructureSaveSubCommand(ClientManager clientManager, StructureCapture capture, SchematicService schematics) {
        super(clientManager);
        this.capture = capture;
        this.schematics = schematics;
    }

    @Override
    public String getName() {
        return "save";
    }

    @Override
    public String getDescription() {
        return "Capture your selection and its data-points into one structure";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.structure.save.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(StructureCommand.nameArgument()
                        .executes(context -> {
                            save(getPlayerFromExecutor(context), context.getArgument("name", String.class));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private void save(Player player, String name) {
        final Optional<Schematic> captured = capture.capture(player);
        if (captured.isEmpty()) {
            UtilMessage.message(player, StructureCommand.PREFIX, Translations.component("core.command.structure.save.no-selection").color(NamedTextColor.RED));
            return;
        }
        final Schematic schematic = captured.get();
        final Optional<File> saved = schematics.save(name, schematic);
        if (saved.isEmpty()) {
            UtilMessage.message(player, StructureCommand.PREFIX, Translations.component("core.command.structure.save.write-failed").color(NamedTextColor.RED));
            return;
        }
        UtilMessage.message(player, StructureCommand.PREFIX, "core.command.structure.save.saved",
                Component.text(saved.get().getName(), NamedTextColor.GREEN), Component.text(schematic.blockCount()),
                Component.text(schematic.getRegions().size()), Component.text(String.format("%.0f", schematic.getAnchorYaw())));
        // Which spot the structure will land on is the one thing a builder cannot see afterwards, and getting it wrong
        // is invisible until a paste is several blocks out.
        final Component point = Component.text(StructureAnchor.POINT, NamedTextColor.WHITE);
        if (StructureAnchor.isAnchored(schematic)) {
            UtilMessage.message(player, StructureCommand.PREFIX, Translations.component("core.command.structure.save.anchored", point).color(NamedTextColor.GRAY));
        } else {
            UtilMessage.message(player, StructureCommand.PREFIX, Translations.component("core.command.structure.save.unanchored", point).color(NamedTextColor.YELLOW));
        }
    }
}
