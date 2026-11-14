package me.mykindos.betterpvp.game.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import java.util.Optional;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.model.SoundEffect;
import me.mykindos.betterpvp.game.framework.AbstractGame;
import me.mykindos.betterpvp.game.framework.GameRegistry;
import me.mykindos.betterpvp.game.framework.ServerController;
import me.mykindos.betterpvp.game.framework.listener.state.TransitionHandler;
import me.mykindos.betterpvp.game.framework.manager.MapManager;
import me.mykindos.betterpvp.game.framework.model.attribute.GameAttribute;
import me.mykindos.betterpvp.game.framework.model.attribute.GameAttributeManager;
import me.mykindos.betterpvp.game.framework.model.world.MappedWorld;
import me.mykindos.betterpvp.game.framework.state.GameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * {@code /game} runs the current game: {@code start}, {@code end}, {@code set <game>} while waiting, and
 * {@code attribute <attribute> <set <value>|get|reset>}.
 */
@Singleton
public class GameCommand extends BrigadierCommand {

    private final ServerController serverController;
    private final GameRegistry gameRegistry;
    private final GameAttributeManager attributeManager;
    private final TransitionHandler transitionHandler;
    private final MapManager mapManager;

    @Inject
    public GameCommand(ClientManager clientManager, ServerController serverController, GameRegistry gameRegistry,
                       GameAttributeManager attributeManager, TransitionHandler transitionHandler, MapManager mapManager) {
        super(clientManager);
        this.serverController = serverController;
        this.gameRegistry = gameRegistry;
        this.attributeManager = attributeManager;
        this.transitionHandler = transitionHandler;
        this.mapManager = mapManager;
        getAliases().add("g");
    }

    @Override
    public String getName() {
        return "game";
    }

    @Override
    public String getDescription() {
        return "Game management commands";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("game.command.game.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    attributeUsage(player);
                    UtilMessage.message(player, "core.prefix.game", "game.command.usage.set", Component.text("/game set <game>", NamedTextColor.YELLOW));
                    UtilMessage.message(player, "core.prefix.game", "game.command.usage.start", Component.text("/game start", NamedTextColor.YELLOW));
                    UtilMessage.message(player, "core.prefix.game", "game.command.usage.end", Component.text("/game end", NamedTextColor.YELLOW));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierNodes.literal("start").executes(this::start))
                .then(BrigadierNodes.literal("end").executes(this::end))
                .then(BrigadierNodes.literal("set")
                        .then(BrigadierNodes.argument("game", StringArgumentType.greedyString())
                                .suggests((context, builder) -> {
                                    gameRegistry.getRegisteredGames().stream()
                                            .map(game -> game.getConfiguration().getName())
                                            .filter(name -> name.toLowerCase().startsWith(builder.getRemainingLowerCase()))
                                            .forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(context -> setGame(getPlayerFromExecutor(context), context.getArgument("game", String.class)))))
                .then(BrigadierNodes.literal("attribute")
                        .executes(context -> {
                            attributeUsage(getPlayerFromExecutor(context));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(BrigadierNodes.argument("attribute", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    attributeManager.getAttributes().stream()
                                            .map(GameAttribute::getKey)
                                            .filter(key -> key.toLowerCase().startsWith(builder.getRemainingLowerCase()))
                                            .forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .then(BrigadierNodes.literal("get").executes(context ->
                                        withAttribute(context, attribute -> getAttributeValue(getPlayerFromExecutor(context), attribute))))
                                .then(BrigadierNodes.literal("reset").executes(context ->
                                        withAttribute(context, attribute -> resetAttributeValue(getPlayerFromExecutor(context), attribute))))
                                .then(BrigadierNodes.literal("set")
                                        .then(BrigadierNodes.argument("value", StringArgumentType.greedyString())
                                                .suggests((context, builder) -> {
                                                    final GameAttribute<?> attribute = attributeManager.getAttribute(context.getArgument("attribute", String.class));
                                                    if (attribute != null) {
                                                        possibleValues(attribute).stream()
                                                                .filter(value -> value.toLowerCase().startsWith(builder.getRemainingLowerCase()))
                                                                .forEach(builder::suggest);
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> withAttribute(context, attribute ->
                                                        setAttributeValue(getPlayerFromExecutor(context), attribute, context.getArgument("value", String.class))))))));
    }

    private int start(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final Player player = getPlayerFromExecutor(context);
        if (serverController.getCurrentState() == GameState.IN_GAME || serverController.getCurrentState() == GameState.ENDING) {
            UtilMessage.message(player, "core.prefix.game", "game.command.start.error-in-game", errorLabel());
            return Command.SINGLE_SUCCESS;
        }
        if (!transitionHandler.checkStateRequirements(true)) {
            UtilMessage.message(player, "core.prefix.game", "game.command.start.error-not-enough-players", errorLabel());
            return Command.SINGLE_SUCCESS;
        }
        serverController.transitionTo(GameState.IN_GAME);
        return Command.SINGLE_SUCCESS;
    }

    private int end(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final Player player = getPlayerFromExecutor(context);
        if (serverController.getCurrentState() != GameState.IN_GAME) {
            UtilMessage.message(player, "core.prefix.game", "game.command.end.error-not-in-game", errorLabel());
            return Command.SINGLE_SUCCESS;
        }
        serverController.getCurrentGame().forceEnd();
        serverController.transitionTo(GameState.ENDING);
        return Command.SINGLE_SUCCESS;
    }

    private int setGame(Player player, String gameName) {
        if (serverController.getCurrentState() != GameState.WAITING) {
            UtilMessage.message(player, "core.prefix.game", "game.command.set.error-only-waiting", errorLabel());
            return Command.SINGLE_SUCCESS;
        }
        final Optional<AbstractGame<?, ?>> foundOpt = gameRegistry.getRegisteredGames().stream()
                .filter(game -> game.getConfiguration().getName().equalsIgnoreCase(gameName))
                .findFirst();
        if (foundOpt.isEmpty()) {
            UtilMessage.message(player, "core.prefix.game", "game.command.set.error-unknown-game",
                    errorLabel(), Component.text(gameName, NamedTextColor.GREEN));
            return Command.SINGLE_SUCCESS;
        }
        final AbstractGame<?, ?> game = foundOpt.get();
        final Optional<MappedWorld> mapOpt = mapManager.selectRandomMap(game);
        if (mapOpt.isEmpty()) {
            UtilMessage.message(player, "core.prefix.game", "game.command.set.error-no-maps",
                    errorLabel(), Component.text(gameName, NamedTextColor.GREEN));
            return Command.SINGLE_SUCCESS;
        }
        if (serverController.getCurrentGame() == game) {
            UtilMessage.message(player, "core.prefix.game", "game.command.set.error-already-set",
                    errorLabel(), Component.text(gameName, NamedTextColor.GREEN));
            return Command.SINGLE_SUCCESS;
        }
        serverController.setGame(game);
        mapManager.setCurrentMap(mapOpt.get());
        UtilMessage.message(player, "core.prefix.game", "game.command.set.success", Component.text(gameName, NamedTextColor.GREEN));
        SoundEffect.HIGH_PITCH_PLING.play(player);
        return Command.SINGLE_SUCCESS;
    }

    private interface AttributeAction {
        void run(GameAttribute<?> attribute) throws CommandSyntaxException;
    }

    /**
     * Runs {@code action} on the named attribute, or tells the executor there is no such attribute.
     */
    private int withAttribute(CommandContext<CommandSourceStack> context, AttributeAction action) throws CommandSyntaxException {
        final String attributeName = context.getArgument("attribute", String.class);
        final GameAttribute<?> attribute = attributeManager.getAttribute(attributeName);
        if (attribute == null) {
            UtilMessage.message(getPlayerFromExecutor(context), "core.prefix.game", "game.command.attribute.unknown-attribute",
                    Component.text(attributeName, NamedTextColor.YELLOW));
            return Command.SINGLE_SUCCESS;
        }
        action.run(attribute);
        return Command.SINGLE_SUCCESS;
    }

    private static void attributeUsage(Player player) {
        UtilMessage.message(player, "core.prefix.game", "game.command.usage.attribute-set", Component.text("/game attribute <attribute> set <value>", NamedTextColor.YELLOW));
        UtilMessage.message(player, "core.prefix.game", "game.command.usage.attribute-get", Component.text("/game attribute <attribute> get", NamedTextColor.YELLOW));
        UtilMessage.message(player, "core.prefix.game", "game.command.usage.attribute-reset", Component.text("/game attribute <attribute> reset", NamedTextColor.YELLOW));
    }

    private static <T> List<String> possibleValues(GameAttribute<T> attribute) {
        return attribute.getPossibleValues().stream()
                .map(attribute::formatValue)
                .toList();
    }

    private static <T> void getAttributeValue(Player player, GameAttribute<T> attribute) {
        if (!attribute.canGet(player)) {
            UtilMessage.message(player, "core.prefix.game", "game.command.attribute.error-cannot-get", Component.text(attribute.getCannotGetMessage(), NamedTextColor.RED));
            return;
        }
        final String value = attribute.formatValue(attribute.getValue());
        UtilMessage.message(player, "core.prefix.game", "game.command.attribute.get-value",
                Component.text(attribute.getKey(), NamedTextColor.GREEN), Component.text(value, NamedTextColor.YELLOW));
    }

    private static void resetAttributeValue(Player player, GameAttribute<?> attribute) {
        if (!attribute.canSet(player)) {
            UtilMessage.message(player, "core.prefix.game", "game.command.attribute.error-cannot-set", Component.text(attribute.getCannotSetMessage(), NamedTextColor.RED));
            return;
        }
        if (attribute.resetToDefault()) {
            UtilMessage.message(player, "core.prefix.game", "game.command.attribute.reset-success", Component.text(attribute.getKey(), NamedTextColor.GREEN));
            getAttributeValue(player, attribute);
            SoundEffect.HIGH_PITCH_PLING.play(player);
        } else {
            UtilMessage.message(player, "core.prefix.game", "game.command.attribute.error-reset-failed",
                    errorLabel(), Component.text(attribute.getKey(), NamedTextColor.GREEN));
        }
    }

    private static <T> void setAttributeValue(Player player, GameAttribute<T> attribute, String valueString) {
        if (!attribute.canSet(player)) {
            UtilMessage.message(player, "core.prefix.game", "game.command.attribute.error-cannot-set", Component.text(attribute.getCannotSetMessage(), NamedTextColor.RED));
            return;
        }
        final T value = attribute.parseValue(valueString);
        if (value == null || !attribute.isValidValue(value)) {
            UtilMessage.message(player, "core.prefix.game", "game.command.attribute.error-invalid-value",
                    errorLabel(), Component.text(attribute.getKey(), NamedTextColor.GREEN));
            return;
        }
        if (attribute.setValue(value)) {
            UtilMessage.message(player, "core.prefix.game", "game.command.attribute.set-success",
                    Component.text(attribute.getKey(), NamedTextColor.GREEN), Component.text(attribute.formatValue(attribute.getValue()), NamedTextColor.YELLOW));
            SoundEffect.HIGH_PITCH_PLING.play(player);
        } else {
            UtilMessage.message(player, "core.prefix.game", "game.command.attribute.error-set-failed",
                    errorLabel(), Component.text(attribute.getKey(), NamedTextColor.GREEN));
        }
    }

    private static Component errorLabel() {
        return Translations.component("game.command.error-label").color(NamedTextColor.DARK_RED);
    }
}
