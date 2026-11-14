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
import me.mykindos.betterpvp.core.utilities.UtilMath;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * {@code /display transform rotate <left|right> <set|add> <degrees> <x> <y> <z> [pivot]} turns the selected display about an axis, optionally around its centre.
 */
@Singleton
@BrigadierSubCommand(DisplayTransformSubCommand.class)
public class DisplayRotateSubCommand extends BrigadierCommand {

    private final DisplayEditorManager displayEditorManager;

    @Inject
    public DisplayRotateSubCommand(ClientManager clientManager, DisplayEditorManager displayEditorManager) {
        super(clientManager);
        this.displayEditorManager = displayEditorManager;
    }

    @Override
    public String getName() {
        return "rotate";
    }

    @Override
    public String getDescription() {
        return "Rotate a display entity";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.display-rotate.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.literal("left")
                        .then(BrigadierNodes.literal("set").then(angle(false, true)))
                        .then(BrigadierNodes.literal("add").then(angle(false, false))))
                .then(BrigadierNodes.literal("right")
                        .then(BrigadierNodes.literal("set").then(angle(true, true)))
                        .then(BrigadierNodes.literal("add").then(angle(true, false))));
    }

    private RequiredArgumentBuilder<CommandSourceStack, Float> angle(boolean right, boolean set) {
        return BrigadierNodes.argument("degrees", FloatArgumentType.floatArg())
                .then(BrigadierNodes.argument("x", FloatArgumentType.floatArg())
                        .then(BrigadierNodes.argument("y", FloatArgumentType.floatArg())
                                .then(BrigadierNodes.argument("z", FloatArgumentType.floatArg())
                                        .executes(context -> rotate(context, right, set, false))
                                        .then(BrigadierNodes.literal("pivot")
                                                .executes(context -> rotate(context, right, set, true))))));
    }

    private int rotate(CommandContext<CommandSourceStack> context, boolean right, boolean set, boolean pivot) throws CommandSyntaxException {
        final Player player = getPlayerFromExecutor(context);
        final Display selectedDisplay = displayEditorManager.getSelectedDisplay(player);
        if (selectedDisplay == null) {
            UtilMessage.message(player, "core.prefix.display", "core.display.not_selecting");
            return Command.SINGLE_SUCCESS;
        }
        final float degrees = context.getArgument("degrees", Float.class);
        float x = context.getArgument("x", Float.class);
        float y = context.getArgument("y", Float.class);
        float z = context.getArgument("z", Float.class);
        final Transformation transformation = selectedDisplay.getTransformation();
        final Quaternionf rotation = right ? transformation.getRightRotation() : transformation.getLeftRotation();
        if (pivot) {
            x += transformation.getTranslation().x + transformation.getScale().x / 2;
            y += transformation.getTranslation().y;
            z += transformation.getTranslation().z + transformation.getScale().z / 2;
        }
        if (set) {
            rotation.setAngleAxis(Math.toRadians(degrees), x, y, z);
            UtilMath.rotateAround(transformation, transformation.getLeftRotation(), transformation.getRightRotation(), new Vector3f(x, y, z));
        } else {
            rotation.rotateAxis((float) Math.toRadians(degrees), x, y, z);
        }
        selectedDisplay.setTransformation(transformation);
        UtilMessage.message(player, "core.prefix.display", "core.display.rotate.success");
        return Command.SINGLE_SUCCESS;
    }
}
