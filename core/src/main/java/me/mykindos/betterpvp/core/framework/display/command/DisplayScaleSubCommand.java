package me.mykindos.betterpvp.core.framework.display.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
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
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

/**
 * {@code /display transform scale <set|add> <x> <y> <z>} sets or adds to the selected display's scale.
 */
@Singleton
@BrigadierSubCommand(DisplayTransformSubCommand.class)
public class DisplayScaleSubCommand extends BrigadierCommand {

    private final DisplayEditorManager displayEditorManager;

    @Inject
    public DisplayScaleSubCommand(ClientManager clientManager, DisplayEditorManager displayEditorManager) {
        super(clientManager);
        this.displayEditorManager = displayEditorManager;
    }

    @Override
    public String getName() {
        return "scale";
    }

    @Override
    public String getDescription() {
        return "Scale a display entity";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.display-scale.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.literal("set").then(xyz(true)))
                .then(BrigadierNodes.literal("add").then(xyz(false)));
    }

    private RequiredArgumentBuilder<CommandSourceStack, Float> xyz(boolean set) {
        return BrigadierNodes.argument("x", FloatArgumentType.floatArg())
                .then(BrigadierNodes.argument("y", FloatArgumentType.floatArg())
                        .then(BrigadierNodes.argument("z", FloatArgumentType.floatArg())
                                .executes(context -> apply(context, set))));
    }

    private int apply(CommandContext<CommandSourceStack> context, boolean set) throws CommandSyntaxException {
        final Player player = getPlayerFromExecutor(context);
        final Display selectedDisplay = displayEditorManager.getSelectedDisplay(player);
        if (selectedDisplay == null) {
            UtilMessage.message(player, "core.prefix.display", "core.display.not_selecting");
            return Command.SINGLE_SUCCESS;
        }
        final float x = context.getArgument("x", Float.class);
        final float y = context.getArgument("y", Float.class);
        final float z = context.getArgument("z", Float.class);
        final Transformation transformation = selectedDisplay.getTransformation();
        final Vector3f vector = transformation.getScale();
        if (set) {
            vector.set(x, y, z);
        } else {
            vector.add(x, y, z);
        }
        selectedDisplay.setTransformation(transformation);
        UtilMessage.message(player, "core.prefix.display", "core.display.scale.success");
        return Command.SINGLE_SUCCESS;
    }
}
