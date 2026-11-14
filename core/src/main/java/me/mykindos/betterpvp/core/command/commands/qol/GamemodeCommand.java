package me.mykindos.betterpvp.core.command.commands.qol;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import java.util.Map;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;

/**
 * Sets the executor's game mode by name, letter or number.
 */
@Singleton
public class GamemodeCommand extends BrigadierCommand {

    @Inject
    public GamemodeCommand(ClientManager clientManager) {
        super(clientManager);
        getAliases().add("gm");
    }

    @Override
    public String getName() {
        return "gamemode";
    }

    @Override
    public String getDescription() {
        return "Quickly change gamemode";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.gamemode.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        final LiteralArgumentBuilder<CommandSourceStack> root = BrigadierNodes.literal(getName())
                .executes(context -> {
                    UtilMessage.message(context.getSource().getSender(), "core.prefix.command", "core.command.gamemode.invalid");
                    return Command.SINGLE_SUCCESS;
                });
        final Map<GameMode, List<String>> names = Map.of(
                GameMode.SURVIVAL, List.of("survival", "s", "0"),
                GameMode.CREATIVE, List.of("creative", "c", "1"),
                GameMode.ADVENTURE, List.of("adventure", "a", "2"),
                GameMode.SPECTATOR, List.of("spectator", "sp", "3"));
        names.forEach((mode, literals) -> literals.forEach(literal -> root.then(BrigadierNodes.literal(literal)
                .executes(context -> {
                    getPlayerFromExecutor(context).setGameMode(mode);
                    return Command.SINGLE_SUCCESS;
                }))));
        return root;
    }
}
