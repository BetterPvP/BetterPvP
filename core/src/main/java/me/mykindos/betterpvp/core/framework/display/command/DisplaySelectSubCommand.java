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
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * {@code /display select} starts picking a display by looking at it.
 */
@Singleton
@BrigadierSubCommand(DisplayCommand.class)
public class DisplaySelectSubCommand extends BrigadierCommand {

    private final DisplayEditorManager displayEditorManager;

    @Inject
    public DisplaySelectSubCommand(ClientManager clientManager, DisplayEditorManager displayEditorManager) {
        super(clientManager);
        this.displayEditorManager = displayEditorManager;
    }

    @Override
    public String getName() {
        return "select";
    }

    @Override
    public String getDescription() {
        return "Select a display entity";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.display-select.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    displayEditorManager.startSelecting(player);
                    UtilMessage.message(player, "core.prefix.display", "core.display.select.prompt", Component.text("15", NamedTextColor.YELLOW), Translations.component("core.display.select.action").color(NamedTextColor.GREEN));
                    return Command.SINGLE_SUCCESS;
                });
    }
}
