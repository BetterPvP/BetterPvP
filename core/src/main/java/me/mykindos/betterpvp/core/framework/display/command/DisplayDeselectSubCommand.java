package me.mykindos.betterpvp.core.framework.display.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.framework.display.DisplayEditorManager;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * {@code /display deselect} drops the display the executor is editing.
 */
@Singleton
@BrigadierSubCommand(DisplayCommand.class)
public class DisplayDeselectSubCommand extends BrigadierCommand {

    private final DisplayEditorManager displayEditorManager;

    @Inject
    public DisplayDeselectSubCommand(ClientManager clientManager, DisplayEditorManager displayEditorManager) {
        super(clientManager);
        this.displayEditorManager = displayEditorManager;
    }

    @Override
    public String getName() {
        return "deselect";
    }

    @Override
    public String getDescription() {
        return "Deselect a display entity";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.display-deselect.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    if (!displayEditorManager.selectDisplay(player, null)) {
                        UtilMessage.message(player, "core.prefix.display", "core.display.not_selecting");
                        return Command.SINGLE_SUCCESS;
                    }
                    UtilMessage.message(player, "core.prefix.display", "core.display.deselected");
                    return Command.SINGLE_SUCCESS;
                });
    }
}
