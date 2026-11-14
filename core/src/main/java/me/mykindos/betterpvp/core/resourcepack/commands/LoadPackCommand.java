package me.mykindos.betterpvp.core.resourcepack.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
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
 * {@code /loadpack <player> <pack>} sends a player a resource pack by name.
 */
@Singleton
public class LoadPackCommand extends BrigadierCommand {

    private final ResourcePackHandler resourcePackHandler;

    @Inject
    public LoadPackCommand(ClientManager clientManager, ResourcePackHandler resourcePackHandler) {
        super(clientManager);
        this.resourcePackHandler = resourcePackHandler;
    }

    /**
     * A resource pack name argument that suggests the packs the handler knows.
     */
    static RequiredArgumentBuilder<CommandSourceStack, String> packArgument(ResourcePackHandler resourcePackHandler) {
        return BrigadierNodes.argument("pack", StringArgumentType.word())
                .suggests((context, builder) -> {
                    resourcePackHandler.getResourcePacks().keySet().stream()
                            .filter(name -> name.toLowerCase().startsWith(builder.getRemainingLowerCase()))
                            .forEach(builder::suggest);
                    return builder.buildFuture();
                });
    }

    @Override
    public String getName() {
        return "loadpack";
    }

    @Override
    public String getDescription() {
        return "Load a resource pack by name";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.load-pack.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("player", BPvPArgumentTypes.playerName())
                        .then(packArgument(resourcePackHandler)
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
                                        target.addResourcePack(pack.getUuid(), pack.getUrl(), pack.getHashBytes(), null, true);
                                    });
                                    return Command.SINGLE_SUCCESS;
                                })));
    }
}
