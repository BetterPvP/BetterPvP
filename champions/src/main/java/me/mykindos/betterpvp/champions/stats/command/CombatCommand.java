package me.mykindos.betterpvp.champions.stats.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import lombok.CustomLog;
import me.mykindos.betterpvp.champions.Champions;
import me.mykindos.betterpvp.champions.champions.roles.RoleManager;
import me.mykindos.betterpvp.champions.stats.impl.ChampionsFilter;
import me.mykindos.betterpvp.champions.stats.repository.ChampionsStatsRepository;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.combat.stats.impl.GlobalCombatStatsRepository;
import me.mykindos.betterpvp.core.combat.stats.model.CombatData;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.arguments.BPvPArgumentTypes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Shows combat stats for the executor or a named player, for their current role or a named filter.
 */
@Singleton
@CustomLog
public class CombatCommand extends BrigadierCommand {

    private final Champions champions;
    private final GlobalCombatStatsRepository globalRepository;
    private final ChampionsStatsRepository championsRepository;
    private final RoleManager roleManager;

    @Inject
    public CombatCommand(ClientManager clientManager, Champions champions, GlobalCombatStatsRepository globalRepository,
                         ChampionsStatsRepository championsRepository, RoleManager roleManager) {
        super(clientManager);
        this.champions = champions;
        this.globalRepository = globalRepository;
        this.championsRepository = championsRepository;
        this.roleManager = roleManager;
    }

    @Override
    public String getName() {
        return "combat";
    }

    @Override
    public String getDescription() {
        return "View combat stats for a player";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("champions.command.combat.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    final Client client = getClientFromExecutor(context);
                    UtilServer.runTaskAsync(champions, () -> run(player, client, null));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierNodes.argument("role", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            Arrays.stream(ChampionsFilter.values())
                                    .map(filter -> filter.name().toLowerCase())
                                    .filter(name -> name.contains(builder.getRemainingLowerCase()))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            final Player player = getPlayerFromExecutor(context);
                            final Client client = getClientFromExecutor(context);
                            final String role = context.getArgument("role", String.class);
                            UtilServer.runTaskAsync(champions, () -> run(player, client, role));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(BrigadierNodes.argument("player", BPvPArgumentTypes.playerName())
                                .executes(context -> {
                                    final Player player = getPlayerFromExecutor(context);
                                    final String role = context.getArgument("role", String.class);
                                    clientManager.search(player).offline(context.getArgument("player", String.class))
                                            .thenAcceptAsync(result -> result.ifPresent(target -> run(player, target, role)));
                                    return Command.SINGLE_SUCCESS;
                                })));
    }

    /**
     * @param roleName a {@link ChampionsFilter} name, or {@code null} for the target's current role
     */
    private void run(Player caster, Client target, @Nullable String roleName) {
        final CompletableFuture<CombatData> loaded;
        final ChampionsFilter filter;
        try {
            if (roleName == null) {
                final Player player = target.getGamer().getPlayer();
                filter = player == null || !player.isValid() ? ChampionsFilter.NONE : ChampionsFilter.fromRole(roleManager.getRole(player).orElse(null));
                loaded = championsRepository.getDataAsync(target.getUniqueId()).thenApply(roleStats -> roleStats.getCombatData(filter));
            } else {
                filter = ChampionsFilter.valueOf(roleName.toUpperCase());
                if (filter == ChampionsFilter.GLOBAL) {
                    loaded = globalRepository.getDataAsync(target.getUniqueId()).thenApply(global -> global);
                } else {
                    loaded = championsRepository.getDataAsync(target.getUniqueId()).thenApply(roleStats -> roleStats.getCombatData(filter));
                }
            }
        } catch (IllegalArgumentException exception) {
            UtilMessage.message(caster, "core.prefix.combat", "champions.combat.invalid-role");
            return;
        }

        if (!loaded.isDone()) {
            UtilMessage.message(caster, "core.prefix.combat", "champions.combat.retrieving");
        }
        final String targetName = target.getName();
        loaded.whenComplete((data, throwable) -> {
            if (throwable != null) {
                UtilMessage.message(caster, "core.prefix.combat", "champions.combat.error");
                log.error("There was an error retrieving player data for {}", targetName, throwable).submit();
                return;
            }
            UtilMessage.message(caster, "core.prefix.combat", "champions.combat.data-for", Component.text(targetName, NamedTextColor.YELLOW));
            UtilMessage.message(caster, "core.prefix.combat", "champions.combat.type", Component.text(filter.getName(), NamedTextColor.GREEN));
            for (Component component : data.getDescription()) {
                UtilMessage.message(caster, "core.prefix.combat", component);
            }
        });
    }
}
