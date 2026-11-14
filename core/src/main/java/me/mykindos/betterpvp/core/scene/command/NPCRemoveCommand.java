package me.mykindos.betterpvp.core.scene.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.scene.SceneObjectRegistry;
import me.mykindos.betterpvp.core.scene.npc.NPC;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

/**
 * {@code /npc remove <id>} removes one NPC by its id.
 */
@Singleton
@BrigadierSubCommand(NPCCommand.class)
public class NPCRemoveCommand extends BrigadierCommand {

    private final SceneObjectRegistry registry;

    @Inject
    public NPCRemoveCommand(ClientManager clientManager, SceneObjectRegistry registry) {
        super(clientManager);
        this.registry = registry;
    }

    @Override
    public String getName() {
        return "remove";
    }

    @Override
    public String getDescription() {
        return "Removes an NPC";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.n-p-c-remove.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("id", IntegerArgumentType.integer())
                        .suggests((context, builder) -> {
                            registry.getObjects(NPC.class).forEach(npc -> builder.suggest(npc.getId()));
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            final CommandSender sender = context.getSource().getSender();
                            final int id = context.getArgument("id", Integer.class);
                            final NPC npc = registry.getObject(id, NPC.class);
                            if (npc == null) {
                                UtilMessage.message(sender, "core.prefix.command", "core.command.npc.remove.not_found", Component.text(id));
                                return Command.SINGLE_SUCCESS;
                            }
                            npc.remove();
                            UtilMessage.message(sender, "core.prefix.command", "core.command.npc.remove.success", Component.text(id));
                            return Command.SINGLE_SUCCESS;
                        }));
    }
}
