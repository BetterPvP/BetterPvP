package me.mykindos.betterpvp.core.command.commands.admin;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.chat.filter.IFilterService;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilSound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Sends a filtered, emphasised message to every online player, signed by the sender or by the server from console.
 */
@Singleton
public class BroadcastCommand extends BrigadierCommand {

    private final IFilterService filterService;

    @Inject
    public BroadcastCommand(ClientManager clientManager, IFilterService filterService) {
        super(clientManager);
        this.filterService = filterService;
        getAliases().add("bc");
    }

    @Override
    public String getName() {
        return "broadcast";
    }

    @Override
    public String getDescription() {
        return "Send an emphasized message to the server";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.broadcast.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    UtilMessage.message(context.getSource().getSender(), "core.prefix.core", "core.command.broadcast.message_required");
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierNodes.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            broadcast(context.getSource().getSender(), context.getArgument("message", String.class));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private void broadcast(CommandSender sender, String text) {
        final Component message;
        if (sender instanceof Player player) {
            final Client client = clientManager.search(player).online(player);
            message = Component.empty().append(client.getRank().getPlayerNameMouseOver(player.getName()).decorate(TextDecoration.BOLD))
                    .append(Translations.component("core.command.broadcast.player_message", Component.text(text, NamedTextColor.RED)));
        } else {
            message = Translations.component("core.command.broadcast.server_message",
                    Translations.component("core.command.broadcast.server_label").color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD),
                    Component.text(text, NamedTextColor.RED));
        }
        filterService.filterMessage(message).thenAcceptAsync(filteredMessage -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                UtilMessage.message(online, filteredMessage);
                UtilSound.playSound(online, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f, true);
            }
        });
    }
}
