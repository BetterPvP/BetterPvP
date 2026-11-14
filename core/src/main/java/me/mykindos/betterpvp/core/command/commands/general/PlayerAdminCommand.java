package me.mykindos.betterpvp.core.command.commands.general;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.punishments.PunishmentTypes;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.cooldowns.CooldownManager;
import me.mykindos.betterpvp.core.framework.server.CrossServerMessageService;
import me.mykindos.betterpvp.core.framework.server.ServerMessage;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.model.SoundEffect;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Sends a message to staff on every server. A muted player can still use it, at most once every two minutes.
 */
@Singleton
public class PlayerAdminCommand extends BrigadierCommand {

    private final CooldownManager cooldownManager;
    private final CrossServerMessageService crossServerMessageService;

    @Inject
    public PlayerAdminCommand(ClientManager clientManager, CooldownManager cooldownManager, CrossServerMessageService crossServerMessageService) {
        super(clientManager);
        this.cooldownManager = cooldownManager;
        this.crossServerMessageService = crossServerMessageService;
        getAliases().add("a");
    }

    @Override
    public String getName() {
        return "admin";
    }

    @Override
    public String getDescription() {
        return "Send a message to staff";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.player-admin.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    UtilMessage.message(context.getSource().getSender(), "core.prefix.core", "core.command.admin.message_required");
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierNodes.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            final Player player = getPlayerFromExecutor(context);
                            final Client client = getClientFromExecutor(context);
                            if (client.hasPunishment(PunishmentTypes.MUTE) && !cooldownManager.use(player, getName(), 120, false, false)) {
                                UtilMessage.message(player, "core.prefix.core", "core.command.admin.cooldown");
                                return Command.SINGLE_SUCCESS;
                            }
                            crossServerMessageService.broadcast(ServerMessage.builder()
                                    .channel("AdminMessage")
                                    .message(context.getArgument("message", String.class))
                                    .metadata("sender", player.getUniqueId().toString())
                                    .build());
                            new SoundEffect("minecraft", "block.amethyst_block.resonate", 1.0F).play(player);
                            return Command.SINGLE_SUCCESS;
                        }));
    }
}
