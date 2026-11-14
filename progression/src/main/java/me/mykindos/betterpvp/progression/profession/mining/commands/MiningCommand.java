package me.mykindos.betterpvp.progression.profession.mining.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.progression.profession.mining.MiningHandler;
import me.mykindos.betterpvp.progression.profession.skill.ProfessionNodeManager;
import me.mykindos.betterpvp.progression.profession.skill.menu.MiningMenu;
import me.mykindos.betterpvp.progression.profile.ProfessionProfileManager;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Opens the executor's mining skill tree, saving their build when it closes.
 */
@Singleton
public class MiningCommand extends BrigadierCommand {

    private final MiningHandler miningHandler;
    private final ProfessionProfileManager profileManager;
    private final ProfessionNodeManager professionNodeManager;

    @Inject
    public MiningCommand(ClientManager clientManager, MiningHandler miningHandler, ProfessionProfileManager profileManager,
                          ProfessionNodeManager professionNodeManager) {
        super(clientManager);
        this.miningHandler = miningHandler;
        this.profileManager = profileManager;
        this.professionNodeManager = professionNodeManager;
    }

    @Override
    public String getName() {
        return "mining";
    }

    @Override
    public String getDescription() {
        return "Mining command";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("progression.command.mining.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    profileManager.getObject(player.getUniqueId().toString()).ifPresent(profile ->
                            new MiningMenu(miningHandler, profile, professionNodeManager).show(player).addCloseHandler(() ->
                                    profileManager.getRepository().updateBuildForGamer(player.getUniqueId(),
                                            profile.getProfessionDataMap().get("Mining").getBuild())));
                    return Command.SINGLE_SUCCESS;
                });
    }
}
