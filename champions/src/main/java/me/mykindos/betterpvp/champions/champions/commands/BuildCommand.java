package me.mykindos.betterpvp.champions.champions.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.champions.champions.builds.BuildManager;
import me.mykindos.betterpvp.champions.champions.builds.menus.ClassSelectionMenu;
import me.mykindos.betterpvp.champions.champions.roles.RoleManager;
import me.mykindos.betterpvp.champions.champions.skills.ChampionsSkillManager;
import me.mykindos.betterpvp.champions.combat.RoleBowService;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Opens the build editor.
 */
@Singleton
public class BuildCommand extends BrigadierCommand {

    private final BuildManager buildManager;
    private final ChampionsSkillManager championsSkillManager;
    private final RoleManager roleManager;
    private final RoleBowService roleBowService;

    @Inject
    public BuildCommand(ClientManager clientManager, BuildManager buildManager, ChampionsSkillManager championsSkillManager,
                        RoleManager roleManager, RoleBowService roleBowService) {
        super(clientManager);
        this.buildManager = buildManager;
        this.championsSkillManager = championsSkillManager;
        this.roleManager = roleManager;
        this.roleBowService = roleBowService;
    }

    @Override
    public String getName() {
        return "build";
    }

    @Override
    public String getDescription() {
        return "Open the build editor";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("champions.command.build.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    new ClassSelectionMenu(player, buildManager, championsSkillManager, roleManager, roleBowService, null).show(player);
                    return Command.SINGLE_SUCCESS;
                });
    }
}
