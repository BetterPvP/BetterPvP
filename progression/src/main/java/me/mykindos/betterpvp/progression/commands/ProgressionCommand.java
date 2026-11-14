package me.mykindos.betterpvp.progression.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.UUID;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.model.Reloadable;
import me.mykindos.betterpvp.progression.Progression;
import me.mykindos.betterpvp.progression.commands.loader.BrigadierProgressionCommandLoader;
import me.mykindos.betterpvp.progression.commands.loader.ProgressionCommandLoader;
import me.mykindos.betterpvp.progression.profession.fishing.FishingHandler;
import me.mykindos.betterpvp.progression.profession.mining.MiningHandler;
import me.mykindos.betterpvp.progression.profession.skill.ProfessionNodeManager;
import me.mykindos.betterpvp.progression.profession.woodcutting.WoodcuttingHandler;
import me.mykindos.betterpvp.progression.profile.ProfessionProfileManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /progression reload} reloads Progression's config, skill trees, profession settings, online profiles and
 * commands. {@code /progression wipe} is its own subcommand.
 */
@Singleton
public class ProgressionCommand extends BrigadierCommand {

    private final Progression progression;
    private final ProgressionCommandLoader commandLoader;
    private final BrigadierProgressionCommandLoader brigadierCommandLoader;
    private final ProfessionNodeManager professionNodeManager;
    private final FishingHandler fishingHandler;
    private final MiningHandler miningHandler;
    private final WoodcuttingHandler woodcuttingHandler;
    private final ProfessionProfileManager professionProfileManager;

    @Inject
    public ProgressionCommand(ClientManager clientManager, Progression progression, ProgressionCommandLoader commandLoader,
                              BrigadierProgressionCommandLoader brigadierCommandLoader, ProfessionNodeManager professionNodeManager,
                              FishingHandler fishingHandler, MiningHandler miningHandler, WoodcuttingHandler woodcuttingHandler,
                              ProfessionProfileManager professionProfileManager) {
        super(clientManager);
        this.progression = progression;
        this.commandLoader = commandLoader;
        this.brigadierCommandLoader = brigadierCommandLoader;
        this.professionNodeManager = professionNodeManager;
        this.fishingHandler = fishingHandler;
        this.miningHandler = miningHandler;
        this.woodcuttingHandler = woodcuttingHandler;
        this.professionProfileManager = professionProfileManager;
    }

    @Override
    public String getName() {
        return "progression";
    }

    @Override
    public String getDescription() {
        return "Progression base command";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("progression.command.progression.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.literal("reload")
                        .executes(context -> {
                            reload(context.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private void reload(CommandSender sender) {
        progression.reload();
        progression.getReloadables().forEach(Reloadable::reload);

        brigadierCommandLoader.reload();
        Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
        commandLoader.reload(progression.getClass().getPackageName());
        professionNodeManager.reload();
        fishingHandler.loadConfig();
        woodcuttingHandler.loadConfig();
        miningHandler.loadConfig();

        for (Client client : clientManager.getOnline()) {
            professionProfileManager.loadProfile(UUID.fromString(client.getUuid()));
        }

        UtilMessage.message(sender, "core.prefix.progression", "progression.command.progression.reload-success");
    }
}
