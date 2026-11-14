package me.mykindos.betterpvp.champions.champions.commands.admin;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Arrays;
import me.mykindos.betterpvp.champions.champions.builds.BuildManager;
import me.mykindos.betterpvp.champions.champions.builds.RoleBuild;
import me.mykindos.betterpvp.champions.champions.builds.menus.ClassSelectionMenu;
import me.mykindos.betterpvp.champions.champions.roles.RoleManager;
import me.mykindos.betterpvp.champions.champions.skills.ChampionsSkillManager;
import me.mykindos.betterpvp.champions.combat.RoleBowService;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.components.champions.Role;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMath;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Opens the build editor on a random build for a role, a random role unless one is named. For testing.
 */
@Singleton
public class PromptBuildCommand extends BrigadierCommand {

    private final BuildManager buildManager;
    private final ChampionsSkillManager championsSkillManager;
    private final RoleManager roleManager;
    private final RoleBowService roleBowService;

    @Inject
    public PromptBuildCommand(ClientManager clientManager, BuildManager buildManager, ChampionsSkillManager championsSkillManager,
                              RoleManager roleManager, RoleBowService roleBowService) {
        super(clientManager);
        this.buildManager = buildManager;
        this.championsSkillManager = championsSkillManager;
        this.roleManager = roleManager;
        this.roleBowService = roleBowService;
    }

    @Override
    public String getName() {
        return "promptbuild";
    }

    @Override
    public String getDescription() {
        return "Open a prompt build (for testing)";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("champions.command.prompt-build.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    prompt(getPlayerFromExecutor(context), "");
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierNodes.argument("role", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            Arrays.stream(Role.values())
                                    .map(role -> role.getName().toLowerCase())
                                    .filter(name -> name.startsWith(builder.getRemainingLowerCase()))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            prompt(getPlayerFromExecutor(context), context.getArgument("role", String.class));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    /**
     * @param roleName a role's name in any case, or anything else for a random role
     */
    private void prompt(Player player, String roleName) {
        final Role role = Arrays.stream(Role.values())
                .filter(candidate -> candidate.getName().equalsIgnoreCase(roleName) || candidate.name().equalsIgnoreCase(roleName))
                .findFirst()
                .orElse(Role.values()[UtilMath.randomInt(0, Role.values().length)]);
        final RoleBuild promptBuild = buildManager.getRandomBuild(player, role, 4);
        new ClassSelectionMenu(player, buildManager, championsSkillManager, roleManager, roleBowService, promptBuild).show(player);
    }
}
