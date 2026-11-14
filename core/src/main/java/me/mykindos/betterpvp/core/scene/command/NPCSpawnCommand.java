package me.mykindos.betterpvp.core.scene.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Arrays;
import java.util.Optional;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.scene.SceneObject;
import me.mykindos.betterpvp.core.scene.SceneObjectFactory;
import me.mykindos.betterpvp.core.scene.SceneObjectFactoryManager;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * {@code /npc spawn <factory> <type>} spawns a factory's default NPC of a type where the executor stands.
 */
@Singleton
@BrigadierSubCommand(NPCCommand.class)
public class NPCSpawnCommand extends BrigadierCommand {

    private final SceneObjectFactoryManager npcFactoryManager;

    @Inject
    public NPCSpawnCommand(ClientManager clientManager, SceneObjectFactoryManager npcFactoryManager) {
        super(clientManager);
        this.npcFactoryManager = npcFactoryManager;
    }

    @Override
    public String getName() {
        return "spawn";
    }

    @Override
    public String getDescription() {
        return "Spawns an NPC";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.n-p-c-spawn.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("factory", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            npcFactoryManager.getObjects().keySet().stream()
                                    .filter(name -> name.toLowerCase().startsWith(builder.getRemainingLowerCase()))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .then(BrigadierNodes.argument("type", StringArgumentType.greedyString())
                                .suggests((context, builder) -> {
                                    npcFactoryManager.getObject(context.getArgument("factory", String.class).toLowerCase())
                                            .ifPresent(factory -> Arrays.stream(factory.getTypes())
                                                    .filter(type -> type.toLowerCase().startsWith(builder.getRemainingLowerCase()))
                                                    .forEach(builder::suggest));
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    spawn(getPlayerFromExecutor(context), context.getArgument("factory", String.class),
                                            context.getArgument("type", String.class));
                                    return Command.SINGLE_SUCCESS;
                                })));
    }

    private void spawn(Player player, String factoryName, String type) {
        final Optional<SceneObjectFactory> found = npcFactoryManager.getObject(factoryName.toLowerCase());
        if (found.isEmpty()) {
            UtilMessage.message(player, "core.prefix.command", "core.command.npc.spawn.factory.unknown", Component.text(factoryName));
            UtilMessage.message(player, "core.prefix.command", "core.command.npc.spawn.factory.available",
                    Component.text(String.join(", ", npcFactoryManager.getObjects().keySet())));
            return;
        }
        final SceneObjectFactory factory = found.get();
        if (!Arrays.asList(factory.getTypes()).contains(type)) {
            UtilMessage.message(player, "core.prefix.command", "core.command.npc.spawn.type.unknown", Component.text(type));
            UtilMessage.message(player, "core.prefix.command", "core.command.npc.spawn.type.available_for",
                    Component.text(factoryName), Component.text(String.join(", ", factory.getTypes())));
            return;
        }
        final SceneObject spawned = factory.spawnDefault(player.getLocation(), type);
        UtilMessage.message(player, "core.prefix.command", "core.command.npc.spawn.success", Component.text(type), Component.text(spawned.getId()));
    }
}
