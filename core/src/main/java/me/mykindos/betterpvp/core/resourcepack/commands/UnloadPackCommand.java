package me.mykindos.betterpvp.core.resourcepack.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.arguments.BPvPArgumentTypes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.resourcepack.ResourcePackHandler;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /unloadpack <player> <pack>} takes a resource pack back off a player.
 */
@Singleton
public class UnloadPackCommand extends BrigadierCommand {

    private final ResourcePackHandler resourcePackHandler;

    @Inject
    public UnloadPackCommand(ClientManager clientManager, ResourcePackHandler resourcePackHandler) {
        super(clientManager);
        this.resourcePackHandler = resourcePackHandler;
    }

    @Override
    public String getName() {
        return "unloadpack";
    }

    @Override
    public String getDescription() {
        return "Unload a resource pack by name";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.unload-pack.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("player", BPvPArgumentTypes.playerName())
                        .then(LoadPackCommand.packArgument(resourcePackHandler)
                                .executes(context -> {
                                    final CommandSender sender = context.getSource().getSender();
                                    final Player target = Bukkit.getPlayer(context.getArgument("player", String.class));
                                    if (target == null) {
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    resourcePackHandler.getResourcePack(context.getArgument("pack", String.class)).thenAccept(pack -> {
                                        if (pack == null) {
                                            UtilMessage.message(sender, Translations.component("core.command.resourcepack.not_found"));
                                            return;
                                        }
                                        target.removeResourcePack(pack.getUuid());
                                    });
                                    return Command.SINGLE_SUCCESS;
                                })));
    }
}
