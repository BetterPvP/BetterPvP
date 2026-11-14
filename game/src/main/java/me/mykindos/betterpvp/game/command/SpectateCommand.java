package me.mykindos.betterpvp.game.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.model.SoundEffect;
import me.mykindos.betterpvp.game.framework.ServerController;
import me.mykindos.betterpvp.game.framework.model.player.Participant;
import me.mykindos.betterpvp.game.framework.model.player.PlayerController;
import me.mykindos.betterpvp.game.framework.state.GameState;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Toggles whether the executor spectates the next game. During a game it moves them to spectating, unless late joins
 * are off or they are alive and in combat.
 */
@Singleton
public class SpectateCommand extends BrigadierCommand {

    private final PlayerController playerController;
    private final ServerController serverController;

    @Inject
    public SpectateCommand(ClientManager clientManager, PlayerController playerController, ServerController serverController) {
        super(clientManager);
        this.playerController = playerController;
        this.serverController = serverController;
        getAliases().add("spec");
    }

    @Override
    public String getName() {
        return "spectate";
    }

    @Override
    public String getDescription() {
        return "Spectate the next game";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("game.command.spectate.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    spectate(getPlayerFromExecutor(context), getClientFromExecutor(context));
                    return Command.SINGLE_SUCCESS;
                });
    }

    private void spectate(Player player, Client client) {
        final boolean inGame = serverController.getCurrentState() == GameState.IN_GAME || serverController.getCurrentState() == GameState.ENDING;
        if (inGame && !serverController.getCurrentGame().getConfiguration().getAllowLateJoinsAttribute().getValue()) {
            UtilMessage.message(player, "core.prefix.game", "game.command.spectate.in-progress");
            return;
        }

        final Participant participant = playerController.getParticipant(player);
        if (participant.isAlive() && client.getGamer().isInCombat()) {
            UtilMessage.message(player, "core.prefix.game", "game.command.spectate.in-combat");
            return;
        }

        boolean toSpectate = !participant.isSpectateNextGame();
        boolean shouldPersist = true;
        if (inGame) {
            // Mid-game the player always becomes a spectator, and the toggle decides whether that carries on
            toSpectate = true;
            if (participant.isSpectating()) {
                shouldPersist = !participant.isSpectateNextGame();
            }
        }

        playerController.setSpectating(player, participant, toSpectate, shouldPersist);
        if (participant.isSpectateNextGame()) {
            UtilMessage.message(player, "core.prefix.game", "game.command.spectate.now");
            new SoundEffect(Sound.UI_BUTTON_CLICK, 1.0f, 1.0f).play(player);
        } else if (!inGame || !participant.isAlive()) {
            UtilMessage.message(player, "core.prefix.game", "game.command.spectate.no-longer");
            new SoundEffect(Sound.UI_BUTTON_CLICK, 0.75f, 1.0f).play(player);
        }
    }
}
