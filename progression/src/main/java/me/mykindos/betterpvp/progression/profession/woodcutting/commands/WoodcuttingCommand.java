package me.mykindos.betterpvp.progression.profession.woodcutting.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.progression.profession.woodcutting.WoodcuttingHandler;
import me.mykindos.betterpvp.progression.profession.skill.ProfessionNodeManager;
import me.mykindos.betterpvp.progression.profession.skill.menu.WoodcuttingMenu;
import me.mykindos.betterpvp.progression.profile.ProfessionProfileManager;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Opens the executor's woodcutting skill tree, saving their build when it closes.
 */
@Singleton
public class WoodcuttingCommand extends BrigadierCommand {

    private final WoodcuttingHandler woodcuttingHandler;
    private final ProfessionProfileManager profileManager;
    private final ProfessionNodeManager professionNodeManager;

    @Inject
    public WoodcuttingCommand(ClientManager clientManager, WoodcuttingHandler woodcuttingHandler, ProfessionProfileManager profileManager,
                          ProfessionNodeManager professionNodeManager) {
        super(clientManager);
        this.woodcuttingHandler = woodcuttingHandler;
        this.profileManager = profileManager;
        this.professionNodeManager = professionNodeManager;
    }

    @Override
    public String getName() {
        return "woodcutting";
    }

    @Override
    public String getDescription() {
        return "Woodcutting! command";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("progression.command.woodcutting.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    profileManager.getObject(player.getUniqueId().toString()).ifPresent(profile ->
                            new WoodcuttingMenu(woodcuttingHandler, profile, professionNodeManager).show(player).addCloseHandler(() ->
                                    profileManager.getRepository().updateBuildForGamer(player.getUniqueId(),
                                            profile.getProfessionDataMap().get("Woodcutting").getBuild())));
                    return Command.SINGLE_SUCCESS;
                });
    }
}
