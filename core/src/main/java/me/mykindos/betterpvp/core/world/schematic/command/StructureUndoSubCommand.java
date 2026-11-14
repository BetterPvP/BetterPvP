package me.mykindos.betterpvp.core.world.schematic.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.world.schematic.SchematicRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * {@code /structure undo} takes the executor's most recent structure paste back out.
 */
@Singleton
@BrigadierSubCommand(StructureCommand.class)
public class StructureUndoSubCommand extends BrigadierCommand {

    private final SchematicRenderer renderer;
    private final StructurePasteHistory history;

    @Inject
    public StructureUndoSubCommand(ClientManager clientManager, SchematicRenderer renderer, StructurePasteHistory history) {
        super(clientManager);
        this.renderer = renderer;
        this.history = history;
    }

    @Override
    public String getName() {
        return "undo";
    }

    @Override
    public String getDescription() {
        return "Take your most recent structure paste back out";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.structure.undo.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    history.takeLatest(player.getUniqueId()).ifPresentOrElse(paste -> {
                        final World world = Bukkit.getWorld(paste.getWorld());
                        if (world == null) {
                            UtilMessage.message(player, StructureCommand.PREFIX,
                                    Translations.component("core.command.structure.undo.world-unloaded").color(NamedTextColor.RED));
                            return;
                        }
                        renderer.restore(world, paste.getUndo());
                        UtilMessage.message(player, StructureCommand.PREFIX, "core.command.structure.undo.undone",
                                Component.text(paste.getUndo().size()));
                    }, () -> UtilMessage.message(player, StructureCommand.PREFIX,
                            Translations.component("core.command.structure.undo.nothing").color(NamedTextColor.RED)));
                    return Command.SINGLE_SUCCESS;
                });
    }
}
