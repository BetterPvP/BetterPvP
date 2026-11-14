package me.mykindos.betterpvp.core.world.site.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
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
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /site delete <id> [force]} destroys an instance and its world. Without {@code force} it refuses one that
 * still has occupants.
 */
@Singleton
@BrigadierSubCommand(SiteCommand.class)
public class SiteDeleteSubCommand extends BrigadierCommand {

    private final SiteInstances instances;

    @Inject
    public SiteDeleteSubCommand(ClientManager clientManager, @NotNull SiteInstances instances) {
        super(clientManager);
        this.instances = instances;
    }

    @Override
    public String getName() {
        return "delete";
    }

    @Override
    public String getDescription() {
        return "Destroy a site instance and its world";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.site.delete.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(SiteIds.argument(instances)
                        .executes(context -> delete(context, false))
                        .then(BrigadierNodes.literal("force")
                                .executes(context -> delete(context, true))));
    }

    private int delete(CommandContext<CommandSourceStack> context, boolean force) {
        final CommandSender sender = context.getSource().getSender();
        final Optional<SiteInstance> found = SiteIds.resolve(sender, instances, context.getArgument("id", String.class));
        if (found.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        final SiteInstance instance = found.get();
        if (!force && !instance.isEmpty()) {
            UtilMessage.message(sender, "core.prefix.sites", Translations.component("core.command.site.occupied",
                    Component.text(instance.getOccupants().size(), NamedTextColor.YELLOW)).color(NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        instances.release(instance.getId(), force).thenRun(() ->
                UtilMessage.message(sender, "core.prefix.sites", Translations.component("core.command.site.released",
                        Component.text(instance.getShortId(), NamedTextColor.YELLOW)).color(NamedTextColor.GREEN)));
        return Command.SINGLE_SUCCESS;
    }
}
