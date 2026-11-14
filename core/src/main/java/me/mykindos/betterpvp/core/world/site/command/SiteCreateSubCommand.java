package me.mykindos.betterpvp.core.world.site.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.world.site.Party;
import me.mykindos.betterpvp.core.world.site.Site;
import me.mykindos.betterpvp.core.world.site.SiteInstances;
import me.mykindos.betterpvp.core.world.site.SiteKey;
import me.mykindos.betterpvp.core.world.site.SiteRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /site create <site> [owner]} opens an instance without travelling to it.
 */
@Singleton
@BrigadierSubCommand(SiteCommand.class)
public class SiteCreateSubCommand extends BrigadierCommand {

    private final SiteRegistry registry;
    private final SiteInstances instances;

    @Inject
    public SiteCreateSubCommand(ClientManager clientManager, @NotNull SiteRegistry registry, @NotNull SiteInstances instances) {
        super(clientManager);
        this.registry = registry;
        this.instances = instances;
    }

    @Override
    public String getName() {
        return "create";
    }

    @Override
    public String getDescription() {
        return "Open an instance of a site";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.site.create.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("site", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            registry.all().stream()
                                    .map(Site::getId)
                                    .filter(id -> id.startsWith(builder.getRemainingLowerCase()))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> create(context, null))
                        .then(BrigadierNodes.argument("owner", LongArgumentType.longArg())
                                .executes(context -> create(context, context.getArgument("owner", Long.class)))));
    }

    private int create(CommandContext<CommandSourceStack> context, @Nullable Long owner) throws CommandSyntaxException {
        final Player player = getPlayerFromExecutor(context);
        final String siteId = context.getArgument("site", String.class);
        final Site site = registry.get(siteId).orElse(null);
        if (site == null) {
            UtilMessage.message(player, "core.prefix.sites", Translations.component("core.command.site.no-site",
                    Component.text(siteId, NamedTextColor.YELLOW)).color(NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        final SiteKey key = owner != null ? site.keyFor(owner) : site.key();
        instances.locate(key, Party.solo(player.getUniqueId())).whenComplete((instance, ex) -> {
            if (ex != null) {
                UtilMessage.message(player, "core.prefix.sites", Translations.component("core.command.site.open-failed",
                        Component.text(String.valueOf(ex.getMessage()))).color(NamedTextColor.RED));
                return;
            }
            // Nobody is going there yet, so the reservation locating it made has to be handed back.
            instances.abandon(instance, Party.solo(player.getUniqueId()));
            UtilMessage.message(player, "core.prefix.sites", Translations.component("core.command.site.opened",
                    Component.text(instance.getShortId(), NamedTextColor.YELLOW),
                    Component.text(instance.getWorldName(), NamedTextColor.GRAY)).color(NamedTextColor.GREEN));
        });
        return Command.SINGLE_SUCCESS;
    }
}
