package me.mykindos.betterpvp.champions.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.champions.Champions;
import me.mykindos.betterpvp.champions.champions.builds.BuildManager;
import me.mykindos.betterpvp.champions.champions.skills.ChampionsSkillManager;
import me.mykindos.betterpvp.champions.stats.repository.ChampionsStatsRepository;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.arguments.BPvPArgumentTypes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.tips.TipManager;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.model.Reloadable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /champions reload} reloads Champions' config, skills, builds, tips and commands.
 * {@code /champions validate <player> <valid>} marks whether a player's stats count.
 */
@Singleton
public class ChampionsCommand extends BrigadierCommand {

    private final Champions champions;
    private final ChampionsCommandLoader commandLoader;
    private final BrigadierChampionsCommandLoader brigadierChampionsCommandLoader;
    private final ChampionsSkillManager skillManager;
    private final BuildManager buildManager;
    private final TipManager tipManager;
    private final ChampionsStatsRepository stats;

    @Inject
    public ChampionsCommand(ClientManager clientManager, Champions champions, ChampionsCommandLoader commandLoader,
                            BrigadierChampionsCommandLoader brigadierChampionsCommandLoader, ChampionsSkillManager skillManager,
                            BuildManager buildManager, TipManager tipManager, ChampionsStatsRepository stats) {
        super(clientManager);
        this.champions = champions;
        this.commandLoader = commandLoader;
        this.brigadierChampionsCommandLoader = brigadierChampionsCommandLoader;
        this.skillManager = skillManager;
        this.buildManager = buildManager;
        this.tipManager = tipManager;
        this.stats = stats;
    }

    @Override
    public String getName() {
        return "champions";
    }

    @Override
    public String getDescription() {
        return "Base champions command";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("champions.command.champions.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.literal("reload")
                        .executes(context -> {
                            reload(context.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(BrigadierNodes.literal("validate")
                        .then(BrigadierNodes.argument("player", BPvPArgumentTypes.playerName())
                                .then(BrigadierNodes.argument("valid", BPvPArgumentTypes.booleanType())
                                        .executes(context -> {
                                            validate(context.getSource().getSender(), context.getArgument("player", String.class),
                                                    context.getArgument("valid", Boolean.class));
                                            return Command.SINGLE_SUCCESS;
                                        }))));
    }

    private void reload(CommandSender sender) {
        champions.reload();
        champions.getReloadables().forEach(Reloadable::reload);

        brigadierChampionsCommandLoader.reload();
        Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
        commandLoader.reload(champions.getClass().getPackageName());
        skillManager.reloadSkills();
        buildManager.reloadBuilds();
        tipManager.reloadTips(champions);

        UtilMessage.message(sender, "core.prefix.champions", "champions.command.reloaded");
    }

    private void validate(CommandSender sender, String targetName, boolean valid) {
        clientManager.search().offline(targetName).thenAcceptAsync(targetOptional -> {
            if (targetOptional.isPresent()) {
                final Client target = targetOptional.get();
                stats.validate(target, valid);
                UtilMessage.message(sender, "core.prefix.champions", "champions.command.invalidated",
                        Component.text(target.getName() + "'s", NamedTextColor.YELLOW));
            }
        });
    }
}
