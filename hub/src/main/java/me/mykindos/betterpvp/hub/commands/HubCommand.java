package me.mykindos.betterpvp.hub.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.model.Reloadable;
import me.mykindos.betterpvp.hub.Hub;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * {@code /hub reload} reloads the Hub's config, reloadables and commands.
 */
@Singleton
public class HubCommand extends BrigadierCommand {

    private final Hub hub;
    private final HubCommandLoader commandLoader;
    private final BrigadierHubCommandLoader brigadierCommandLoader;

    @Inject
    public HubCommand(ClientManager clientManager, Hub hub, HubCommandLoader commandLoader, BrigadierHubCommandLoader brigadierCommandLoader) {
        super(clientManager);
        this.hub = hub;
        this.commandLoader = commandLoader;
        this.brigadierCommandLoader = brigadierCommandLoader;
    }

    @Override
    public String getName() {
        return "hub";
    }

    @Override
    public String getDescription() {
        return "Hub base command";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("hub.command.hub.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.literal("reload")
                        .executes(context -> {
                            hub.reload();
                            hub.getReloadables().forEach(Reloadable::reload);

                            brigadierCommandLoader.reload();
                            Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
                            commandLoader.reload(hub.getClass().getPackageName());

                            UtilMessage.message(context.getSource().getSender(), "core.prefix.hub", "hub.command.reload.success");
                            return Command.SINGLE_SUCCESS;
                        }));
    }
}
