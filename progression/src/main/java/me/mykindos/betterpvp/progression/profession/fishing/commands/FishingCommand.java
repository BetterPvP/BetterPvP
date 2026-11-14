package me.mykindos.betterpvp.progression.profession.fishing.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.progression.profession.fishing.FishingHandler;
import me.mykindos.betterpvp.progression.profession.skill.ProfessionNodeManager;
import me.mykindos.betterpvp.progression.profession.skill.menu.FishingMenu;
import me.mykindos.betterpvp.progression.profile.ProfessionProfileManager;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Opens the executor's fishing skill tree, saving their build when it closes.
 */
@Singleton
public class FishingCommand extends BrigadierCommand {

    private final FishingHandler fishingHandler;
    private final ProfessionProfileManager profileManager;
    private final ProfessionNodeManager professionNodeManager;

    @Inject
    public FishingCommand(ClientManager clientManager, FishingHandler fishingHandler, ProfessionProfileManager profileManager,
                          ProfessionNodeManager professionNodeManager) {
        super(clientManager);
        this.fishingHandler = fishingHandler;
        this.profileManager = profileManager;
        this.professionNodeManager = professionNodeManager;
    }

    @Override
    public String getName() {
        return "fishing";
    }

    @Override
    public String getDescription() {
        return "Fishing command";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("progression.command.fishing.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    profileManager.getObject(player.getUniqueId().toString()).ifPresent(profile ->
                            new FishingMenu(fishingHandler, profile, professionNodeManager).show(player).addCloseHandler(() ->
                                    profileManager.getRepository().updateBuildForGamer(player.getUniqueId(),
                                            profile.getProfessionDataMap().get("Fishing").getBuild())));
                    return Command.SINGLE_SUCCESS;
                });
    }
}
