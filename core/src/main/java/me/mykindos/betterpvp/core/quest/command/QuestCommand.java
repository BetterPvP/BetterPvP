package me.mykindos.betterpvp.core.quest.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import java.util.Map;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.quest.QuestInstance;
import me.mykindos.betterpvp.core.quest.QuestManager;
import me.mykindos.betterpvp.core.quest.QuestRegistry;
import me.mykindos.betterpvp.core.quest.model.QuestDefinition;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * {@code /quest start <id>} starts a quest, {@code /quest list} shows the executor's active ones, and
 * {@code /quest debug} dumps the loaded catalogue and the executor's live instances.
 */
@Singleton
public class QuestCommand extends BrigadierCommand {

    private static final String PREFIX = "core.prefix.quest";

    private final QuestManager questManager;
    private final QuestRegistry questRegistry;

    @Inject
    public QuestCommand(ClientManager clientManager, QuestManager questManager, QuestRegistry questRegistry) {
        super(clientManager);
        this.questManager = questManager;
        this.questRegistry = questRegistry;
    }

    @Override
    public String getName() {
        return "quest";
    }

    @Override
    public String getDescription() {
        return "Start and track quests";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.quest.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    UtilMessage.message(context.getSource().getSender(), PREFIX, "core.command.quest.usage");
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierNodes.literal("start")
                        .then(BrigadierNodes.argument("id", StringArgumentType.greedyString())
                                .suggests((context, builder) -> {
                                    questRegistry.getLoaded().keySet().stream()
                                            .filter(id -> id.toLowerCase().startsWith(builder.getRemainingLowerCase()))
                                            .forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    questManager.startQuest(getPlayerFromExecutor(context), context.getArgument("id", String.class));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(BrigadierNodes.literal("list")
                        .executes(context -> {
                            list(getPlayerFromExecutor(context));
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(BrigadierNodes.literal("debug")
                        .executes(context -> {
                            debug(getPlayerFromExecutor(context));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private void list(Player player) {
        final List<QuestInstance> active = questManager.activeFor(player);
        if (active.isEmpty()) {
            UtilMessage.message(player, PREFIX, "core.command.quest.list.none");
            return;
        }
        for (QuestInstance instance : active) {
            final String name = questRegistry.get(instance.getQuestId()).map(QuestDefinition::getName).orElse(instance.getQuestId());
            UtilMessage.message(player, PREFIX, Translations.component("core.command.quest.list.entry",
                    Component.text(name, NamedTextColor.GREEN), Component.text(instance.getCurrentStages().size())).color(NamedTextColor.GRAY));
            sendProgress(player, instance, "  ");
        }
    }

    /** Dumps the loaded quest catalogue and this player's live instances and progress. */
    private void debug(Player player) {
        final Map<String, QuestDefinition> loaded = questRegistry.getLoaded();
        UtilMessage.message(player, PREFIX, Translations.component("core.command.quest.debug.loaded",
                Component.text(loaded.size(), NamedTextColor.WHITE)).color(NamedTextColor.GRAY));
        loaded.forEach((id, definition) -> UtilMessage.message(player, PREFIX, Component.text("  ")
                .append(Translations.component("core.command.quest.debug.quest", Component.text(id, NamedTextColor.YELLOW),
                        Component.text(definition.getName()), Component.text(definition.getNodes().size())).color(NamedTextColor.GRAY))));
        final List<QuestInstance> active = questManager.activeFor(player);
        UtilMessage.message(player, PREFIX, Translations.component("core.command.quest.debug.active",
                Component.text(active.size(), NamedTextColor.WHITE)).color(NamedTextColor.GRAY));
        for (QuestInstance instance : active) {
            UtilMessage.message(player, PREFIX, Component.text("  ")
                    .append(Translations.component("core.command.quest.debug.instance", Component.text(instance.getQuestId(), NamedTextColor.GREEN),
                            Component.text(String.valueOf(instance.getStatus())), Component.text(String.valueOf(instance.getCurrentStages())))
                            .color(NamedTextColor.GRAY)));
            sendProgress(player, instance, "    ");
        }
    }

    private void sendProgress(Player player, QuestInstance instance, String indent) {
        instance.getProgress().forEach((key, value) -> UtilMessage.message(player, PREFIX, Component.text(indent)
                .append(Translations.component("core.command.quest.progress", Component.text(key), Component.text(value),
                        Component.text(instance.getTargets().getOrDefault(key, 1))).color(NamedTextColor.GRAY))));
    }
}
