package me.mykindos.betterpvp.core.framework.display.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
 * {@code /display interpolation <delay|duration> <ticks>} sets the selected display's interpolation delay or duration.
 */
@Singleton
@BrigadierSubCommand(DisplayCommand.class)
public class DisplayInterpolationSubCommand extends BrigadierCommand {

    private final DisplayEditorManager displayEditorManager;

    @Inject
    public DisplayInterpolationSubCommand(ClientManager clientManager, DisplayEditorManager displayEditorManager) {
        super(clientManager);
        this.displayEditorManager = displayEditorManager;
    }

    @Override
    public String getName() {
        return "interpolation";
    }

    @Override
    public String getDescription() {
        return "Change the interpolation of a display entity";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.display-interpolation.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.literal("delay")
                        .then(BrigadierNodes.argument("ticks", IntegerArgumentType.integer())
                                .executes(context -> interpolate(context, true))))
                .then(BrigadierNodes.literal("duration")
                        .then(BrigadierNodes.argument("ticks", IntegerArgumentType.integer())
                                .executes(context -> interpolate(context, false))));
    }

    private int interpolate(CommandContext<CommandSourceStack> context, boolean delay) throws CommandSyntaxException {
        final Player player = getPlayerFromExecutor(context);
        final Display selectedDisplay = displayEditorManager.getSelectedDisplay(player);
        if (selectedDisplay == null) {
            UtilMessage.message(player, "core.prefix.display", "core.display.not_selecting");
            return Command.SINGLE_SUCCESS;
        }
        final int ticks = context.getArgument("ticks", Integer.class);
        if (delay) {
            selectedDisplay.setInterpolationDelay(ticks);
        } else {
            selectedDisplay.setInterpolationDuration(ticks);
        }
        UtilMessage.message(player, "core.prefix.display", "core.display.interpolation.set", Component.text(delay ? "delay" : "duration"), Component.text(ticks));
        return Command.SINGLE_SUCCESS;
    }
}
