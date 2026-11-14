package me.mykindos.betterpvp.core.cutscene.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.cutscene.Cutscene;
import me.mykindos.betterpvp.core.cutscene.CutsceneManager;
import me.mykindos.betterpvp.core.cutscene.CutsceneRegistry;
import me.mykindos.betterpvp.core.cutscene.CutsceneValidator;
import me.mykindos.betterpvp.core.cutscene.camera.Beat;
import me.mykindos.betterpvp.core.cutscene.camera.CameraMarkers;
import me.mykindos.betterpvp.core.cutscene.skip.CutsceneViewStore;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Authoring and debugging tools for cutscenes.
 * <p>
 * {@code from} is the one that matters most while building: a shot that reads wrong forty seconds in is otherwise
 * forty seconds of watching per attempt, and jumping straight to it turns each iteration into a couple of seconds.
 * {@code markers} answers the other recurring question - whether the id a beat names is actually the id on the marker
 * in front of you.
 */
@Singleton
public class CutsceneCommand extends BrigadierCommand {

    private static final String PREFIX = "core.prefix.cutscene";

    private final CutsceneManager manager;
    private final CutsceneRegistry registry;
    private final CameraMarkers markers;
    private final CutsceneViewStore views;
    private final CutsceneValidator validator;

    @Inject
    public CutsceneCommand(ClientManager clientManager, CutsceneManager manager, CutsceneRegistry registry,
                           CameraMarkers markers, CutsceneViewStore views, CutsceneValidator validator) {
        super(clientManager);
        this.manager = manager;
        this.registry = registry;
        this.markers = markers;
        this.views = views;
        this.validator = validator;
    }

    @Override
    public String getName() {
        return "cutscene";
    }

    @Override
    public String getDescription() {
        return "Play, inspect and debug cutscenes";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.cutscene.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    UtilMessage.message(context.getSource().getSender(), PREFIX, "core.command.cutscene.usage");
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierNodes.literal("play")
                        .then(idArgument(StringArgumentType.greedyString())
                                .executes(context -> {
                                    final String id = context.getArgument("id", String.class);
                                    final Player player = getPlayerFromExecutor(context);
                                    if (!manager.start(player, id)) {
                                        UtilMessage.message(player, PREFIX, "core.command.cutscene.play.failed", green(id));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(BrigadierNodes.literal("from")
                        .then(idArgument(StringArgumentType.string())
                                .then(BrigadierNodes.argument("beat", StringArgumentType.greedyString())
                                        .suggests((context, builder) -> {
                                            if (context.getSource().getExecutor() instanceof Player player) {
                                                registry.build(player, context.getArgument("id", String.class))
                                                        .filter(Cutscene::hasCamera)
                                                        .ifPresent(cutscene -> cutscene.getCamera().getBeats().stream()
                                                                .map(Beat::getId)
                                                                .filter(beat -> beat.toLowerCase().startsWith(builder.getRemainingLowerCase()))
                                                                .forEach(builder::suggest));
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(this::from))))
                .then(BrigadierNodes.literal("markers")
                        .executes(context -> {
                            listMarkers(getPlayerFromExecutor(context));
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(BrigadierNodes.literal("list")
                        .executes(context -> {
                            list(getPlayerFromExecutor(context));
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(BrigadierNodes.literal("stop")
                        .executes(context -> {
                            final Player player = getPlayerFromExecutor(context);
                            if (!manager.isWatching(player)) {
                                UtilMessage.message(player, PREFIX, "core.command.cutscene.stop.not-watching");
                                return Command.SINGLE_SUCCESS;
                            }
                            manager.stop(player);
                            return Command.SINGLE_SUCCESS;
                        }))
                // Re-reads camera markers, for a builder who has just moved one and saved
                .then(BrigadierNodes.literal("reload")
                        .executes(context -> {
                            final Player player = getPlayerFromExecutor(context);
                            markers.invalidate(player.getWorld());
                            UtilMessage.message(player, PREFIX, "core.command.cutscene.reload.done", green(player.getWorld().getName()));
                            return Command.SINGLE_SUCCESS;
                        }))
                // Clears a cutscene from this player's history, so a seen-before skip policy treats it as new again
                .then(BrigadierNodes.literal("forget")
                        .then(idArgument(StringArgumentType.greedyString())
                                .executes(context -> {
                                    final String id = context.getArgument("id", String.class);
                                    final Player player = getPlayerFromExecutor(context);
                                    views.forget(player.getUniqueId(), id);
                                    UtilMessage.message(player, PREFIX, "core.command.cutscene.forget.done", green(id));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(BrigadierNodes.literal("validate")
                        .executes(context -> {
                            validate(getPlayerFromExecutor(context), List.copyOf(registry.all()));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(idArgument(StringArgumentType.greedyString())
                                .executes(context -> {
                                    validate(getPlayerFromExecutor(context), registry.get(context.getArgument("id", String.class))
                                            .map(List::of).orElseGet(List::of));
                                    return Command.SINGLE_SUCCESS;
                                })));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> idArgument(StringArgumentType type) {
        return BrigadierNodes.argument("id", type)
                .suggests((context, builder) -> {
                    registry.all().stream()
                            .map(CutsceneRegistry.CutsceneScript::getId)
                            .filter(id -> id.toLowerCase().startsWith(builder.getRemainingLowerCase()))
                            .forEach(builder::suggest);
                    return builder.buildFuture();
                });
    }

    private static Component green(String text) {
        return Component.text(text, NamedTextColor.GREEN);
    }

    /** Plays a cutscene from a named beat, so a shot deep in the timeline can be re-watched on its own. */
    private int from(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final Player player = getPlayerFromExecutor(context);
        final String id = context.getArgument("id", String.class);
        final String beat = context.getArgument("beat", String.class);
        final Optional<Cutscene> built = registry.build(player, id);
        if (built.isEmpty()) {
            UtilMessage.message(player, PREFIX, "core.command.cutscene.not-found", green(id));
            return Command.SINGLE_SUCCESS;
        }
        final Cutscene cutscene = built.get();
        if (!cutscene.hasCamera() || cutscene.getCamera().indexOf(beat) < 0) {
            UtilMessage.message(player, PREFIX, "core.command.cutscene.no-beat", green(id), green(beat));
            return Command.SINGLE_SUCCESS;
        }
        if (manager.start(player, cutscene)) {
            manager.jumpTo(player, beat);
        }
        return Command.SINGLE_SUCCESS;
    }

    /** Every camera marker this world declares, which is the list a beat id has to come from. */
    private void listMarkers(@NotNull Player player) {
        final List<String> ids = new ArrayList<>(markers.ids(player.getWorld()));
        if (ids.isEmpty()) {
            UtilMessage.message(player, PREFIX, "core.command.cutscene.markers.none");
            return;
        }
        ids.sort(String::compareTo);
        UtilMessage.message(player, PREFIX, "core.command.cutscene.markers.header", green(String.valueOf(ids.size())));
        for (String id : ids) {
            UtilMessage.message(player, PREFIX, Component.text(" - ", NamedTextColor.GRAY).append(green(id)));
        }
    }

    private void list(@NotNull Player player) {
        if (registry.all().isEmpty()) {
            UtilMessage.message(player, PREFIX, "core.command.cutscene.list.none");
            return;
        }
        for (CutsceneRegistry.CutsceneScript script : registry.all()) {
            Component line = green(script.getId())
                    .append(Component.text(" - ", NamedTextColor.GRAY))
                    .append(Component.text(script.getDisplayName()));
            if (views.hasSeen(player.getUniqueId(), script.getId())) {
                line = line.appendSpace().append(Translations.component("core.command.cutscene.list.seen").color(NamedTextColor.GRAY));
            }
            UtilMessage.message(player, PREFIX, line);
        }
    }

    /**
     * Checks cutscenes against this world - waits nothing announces, duplicate beat ids, and camera markers this world
     * does not declare.
     */
    private void validate(@NotNull Player player, List<CutsceneRegistry.CutsceneScript> scripts) {
        if (scripts.isEmpty()) {
            UtilMessage.message(player, PREFIX, "core.command.cutscene.validate.nothing");
            return;
        }
        int problems = 0;
        for (CutsceneRegistry.CutsceneScript script : scripts) {
            final List<String> issues = validator.validate(script.getFactory().apply(player), player.getWorld());
            problems += issues.size();
            for (String issue : issues) {
                UtilMessage.message(player, PREFIX, Component.text(script.getId(), NamedTextColor.RED)
                        .append(Component.text(" - ", NamedTextColor.GRAY))
                        .append(Component.text(issue)));
            }
        }
        if (problems == 0) {
            UtilMessage.message(player, PREFIX, "core.command.cutscene.validate.ok", green(String.valueOf(scripts.size())));
        }
    }
}
