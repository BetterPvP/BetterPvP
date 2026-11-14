package me.mykindos.betterpvp.core.world.site.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.world.site.Site;
import me.mykindos.betterpvp.core.world.site.SiteInstance;
import me.mykindos.betterpvp.core.world.site.SiteInstances;
import me.mykindos.betterpvp.core.world.site.SiteRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /site list} shows every configured site, and under each one its live instances.
 */
@Singleton
@BrigadierSubCommand(SiteCommand.class)
public class SiteListSubCommand extends BrigadierCommand {

    private final SiteRegistry registry;
    private final SiteInstances instances;

    @Inject
    public SiteListSubCommand(ClientManager clientManager, @NotNull SiteRegistry registry, @NotNull SiteInstances instances) {
        super(clientManager);
        this.registry = registry;
        this.instances = instances;
    }

    @Override
    public String getName() {
        return "list";
    }

    @Override
    public String getDescription() {
        return "List every site and its live instances";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.site.list.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final CommandSender sender = context.getSource().getSender();
                    if (registry.all().isEmpty()) {
                        UtilMessage.message(sender, "core.prefix.sites", "core.command.site.none-configured");
                        return Command.SINGLE_SUCCESS;
                    }
                    for (Site site : registry.all()) {
                        UtilMessage.message(sender, "core.prefix.sites", Component.text(site.getId(), NamedTextColor.WHITE)
                                .appendSpace()
                                .append(Component.text(String.valueOf(site.getPolicy().getLifecycle()), NamedTextColor.YELLOW))
                                .appendSpace()
                                .append(Translations.component("core.command.site.list.policy",
                                        Component.text(site.getPolicy().getMin()), Component.text(site.getPolicy().getMax()),
                                        Component.text(site.getPolicy().getCapacity())).color(NamedTextColor.GRAY)));
                        for (SiteInstance instance : instances.forKey(site.key())) {
                            UtilMessage.message(sender, "core.prefix.sites", Component.text("  " + instance.getShortId(), NamedTextColor.AQUA)
                                    .append(Component.text(" " + instance.getWorldName(), NamedTextColor.GRAY))
                                    .append(Component.text(" " + instance.getState(), NamedTextColor.YELLOW))
                                    .appendSpace()
                                    .append(Translations.component("core.command.site.list.occupants",
                                            Component.text(instance.getOccupants().size())).color(NamedTextColor.GRAY)));
                        }
                    }
                    return Command.SINGLE_SUCCESS;
                });
    }
}
