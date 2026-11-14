package me.mykindos.betterpvp.core.command.commands.general;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.List;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.world.site.Presence;
import me.mykindos.betterpvp.core.world.site.Site;
import me.mykindos.betterpvp.core.world.site.SiteRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Lists everybody online, grouped by the site they are in.
 * <p>
 * Deliberately network-wide rather than presence-scoped: a player cannot see or speak to somebody on another site,
 * but knowing how busy the server is has never been something to hide.
 */
@Singleton
public class ListCommand extends BrigadierCommand {

    private final SiteRegistry registry;
    private final Presence presence;

    @Inject
    public ListCommand(ClientManager clientManager, SiteRegistry registry, Presence presence) {
        super(clientManager);
        this.registry = registry;
        this.presence = presence;
    }

    @Override
    public String getName() {
        return "list";
    }

    @Override
    public String getDescription() {
        return "get a list of all players online";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.list.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final CommandSender sender = context.getSource().getSender();
                    UtilMessage.message(sender, "core.prefix.list", "core.command.list.online_count",
                            Component.text(clientManager.getOnline().size()));
                    for (Site site : registry.all()) {
                        final List<Player> present = presence.onSite(site);
                        if (!present.isEmpty()) {
                            UtilMessage.message(sender, "core.prefix.list", "core.command.list.group",
                                    site.getDisplayName(), names(present));
                        }
                    }
                    final List<Player> elsewhere = presence.offSite();
                    if (!elsewhere.isEmpty()) {
                        UtilMessage.message(sender, "core.prefix.list", "core.command.list.group",
                                Component.translatable("core.command.list.elsewhere"), names(elsewhere));
                    }
                    return Command.SINGLE_SUCCESS;
                });
    }

    private Component names(List<Player> players) {
        final List<Component> names = new ArrayList<>();
        for (Player online : players) {
            final Client onlineClient = clientManager.search().online(online);
            names.add(Component.text(online.getName(), onlineClient.getRank().getColor()));
        }
        return Component.join(JoinConfiguration.separator(Component.text(", ", NamedTextColor.GRAY)), names);
    }
}
