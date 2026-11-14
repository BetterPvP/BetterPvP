package me.mykindos.betterpvp.champions.champions.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import java.util.Optional;
import me.mykindos.betterpvp.champions.Champions;
import me.mykindos.betterpvp.champions.champions.builds.BuildManager;
import me.mykindos.betterpvp.champions.champions.builds.GamerBuilds;
import me.mykindos.betterpvp.champions.champions.builds.RoleBuild;
import me.mykindos.betterpvp.champions.champions.roles.RoleManager;
import me.mykindos.betterpvp.core.chat.events.ChatSentEvent;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.components.champions.Role;
import me.mykindos.betterpvp.core.cooldowns.CooldownManager;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

/**
 * Posts the executor's active build to their chat channel, with the build as a hover.
 */
@Singleton
public class SendSkillsCommand extends BrigadierCommand {

    private final Champions champions;
    private final RoleManager roleManager;
    private final BuildManager buildManager;
    private final CooldownManager cooldownManager;

    @Inject
    public SendSkillsCommand(ClientManager clientManager, Champions champions, RoleManager roleManager, BuildManager buildManager,
                             CooldownManager cooldownManager) {
        super(clientManager);
        this.champions = champions;
        this.roleManager = roleManager;
        this.buildManager = buildManager;
        this.cooldownManager = cooldownManager;
        getAliases().addAll(List.of("sendskill", "sendbuild"));
    }

    @Override
    public String getName() {
        return "sendskills";
    }

    @Override
    public String getDescription() {
        return "Send your skills as a formatted message in your chat channel";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("champions.command.send-skills.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    final Client client = getClientFromExecutor(context);
                    if (!cooldownManager.use(player, getName(), 15d, false, true)) {
                        UtilMessage.message(player, "core.prefix.skills", "champions.command.sendskills.wait");
                        return Command.SINGLE_SUCCESS;
                    }
                    final Optional<GamerBuilds> builds = buildManager.getObject(player.getUniqueId().toString());
                    final Role role = roleManager.getRole(player).orElse(null);
                    final RoleBuild build = builds.isEmpty() || role == null ? null : builds.get().getActiveBuilds().get(role.getName());
                    if (build != null) {
                        final Component message = Translations.component("champions.command.sendskills.my-build", role.getDisplayName())
                                .color(NamedTextColor.WHITE)
                                .decoration(TextDecoration.UNDERLINED, true)
                                .hoverEvent(HoverEvent.showText(build.getBuildComponent()));
                        UtilServer.runTaskAsync(champions, () -> UtilServer.callEvent(new ChatSentEvent(player,
                                client.getGamer().getChatChannel(), Component.empty(), message)));
                    }
                    return Command.SINGLE_SUCCESS;
                });
    }
}
