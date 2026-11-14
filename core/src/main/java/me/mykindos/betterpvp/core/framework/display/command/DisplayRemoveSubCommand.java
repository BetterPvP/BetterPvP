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
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;

/**
 * {@code /display remove} removes the display the executor is editing.
 */
@Singleton
@BrigadierSubCommand(DisplayCommand.class)
public class DisplayRemoveSubCommand extends BrigadierCommand {

    private final DisplayEditorManager displayEditorManager;

    @Inject
    public DisplayRemoveSubCommand(ClientManager clientManager, DisplayEditorManager displayEditorManager) {
        super(clientManager);
        this.displayEditorManager = displayEditorManager;
    }

    @Override
    public String getName() {
        return "remove";
    }

    @Override
    public String getDescription() {
        return "Remove a display entity";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.display-remove.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    final Display selectedDisplay = displayEditorManager.getSelectedDisplay(player);
                    if (!displayEditorManager.selectDisplay(player, null)) {
                        UtilMessage.message(player, "core.prefix.display", "core.display.not_selecting");
                        return Command.SINGLE_SUCCESS;
                    }
                    selectedDisplay.remove();
                    UtilMessage.message(player, "core.prefix.display", "core.display.despawned");
                    return Command.SINGLE_SUCCESS;
                });
    }
}
