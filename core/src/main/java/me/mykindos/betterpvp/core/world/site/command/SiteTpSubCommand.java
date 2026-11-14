package me.mykindos.betterpvp.core.world.site.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Optional;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.world.site.SiteInstance;
import me.mykindos.betterpvp.core.world.site.SiteInstances;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /site tp <id>} teleports to an instance's world by id prefix.
 */
@Singleton
@BrigadierSubCommand(SiteCommand.class)
public class SiteTpSubCommand extends BrigadierCommand {

    private final SiteInstances instances;

    @Inject
    public SiteTpSubCommand(ClientManager clientManager, @NotNull SiteInstances instances) {
        super(clientManager);
        this.instances = instances;
    }

    @Override
    public String getName() {
        return "tp";
    }

    @Override
    public String getDescription() {
        return "Teleport to a site instance";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.site.tp.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(SiteIds.argument(instances)
                        .executes(context -> {
                            final Player player = getPlayerFromExecutor(context);
                            final Optional<SiteInstance> found = SiteIds.resolve(player, instances, context.getArgument("id", String.class));
                            if (found.isEmpty()) {
                                return Command.SINGLE_SUCCESS;
                            }
                            final SiteInstance instance = found.get();
                            final World world = Bukkit.getWorld(instance.getWorldName());
                            if (world == null) {
                                UtilMessage.message(player, "core.prefix.sites",
                                        Translations.component("core.command.site.world-not-loaded").color(NamedTextColor.RED));
                                return Command.SINGLE_SUCCESS;
                            }
                            player.teleportAsync(world.getSpawnLocation());
                            UtilMessage.message(player, "core.prefix.sites", Translations.component("core.command.site.teleported",
                                    Component.text(instance.getShortId(), NamedTextColor.YELLOW)).color(NamedTextColor.GREEN));
                            return Command.SINGLE_SUCCESS;
                        }));
    }
}
