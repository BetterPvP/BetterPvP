package me.mykindos.betterpvp.core.client.punishments.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Arrays;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.punishments.PunishmentHandler;
import me.mykindos.betterpvp.core.client.punishments.menu.PunishmentMenu;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.arguments.BPvPArgumentTypes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

@Singleton
public class PunishCommand extends BrigadierCommand {

    private final Core core;
    private final PunishmentHandler punishmentHandler;

    @Inject
    public PunishCommand(ClientManager clientManager, Core core, PunishmentHandler punishmentHandler) {
        super(clientManager);
        this.core = core;
        this.punishmentHandler = punishmentHandler;
    }

    @Override
    public String getName() {
        return "punish";
    }

    @Override
    public String getDescription() {
        return "Base command for punishing system";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.punish.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    execute(getPlayerFromExecutor(context), getClientFromExecutor(context));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierNodes.argument("player", BPvPArgumentTypes.playerName())
                        .then(BrigadierNodes.argument("reason", StringArgumentType.greedyString())
                                .executes(context -> {
                                    execute(getPlayerFromExecutor(context), getClientFromExecutor(context),
                                            context.getArgument("player", String.class), context.getArgument("reason", String.class));
                                    return Command.SINGLE_SUCCESS;
                                })));
    }

    private void execute(Player player, Client client, String... args) {
        if (args.length < 2) {
            UtilMessage.message(player, "core.prefix.command", "core.command.punish.usage");
            return;
        }

        clientManager.search().offline(args[0]).thenAcceptAsync(clientOptional -> {
            if (clientOptional.isEmpty()) {
                UtilMessage.message(player, "core.prefix.command", "core.command.punish.client.not_found",
                        Component.text(args[0], NamedTextColor.YELLOW));
                return;
            }
            Client target = clientOptional.get();
            if (target.getRank().getId() >= client.getRank().getId()) {
                UtilMessage.message(player, "core.prefix.command", "core.command.punish.rank.too_high");
                return;
            }

            PunishmentMenu punishmentMenu = new PunishmentMenu(client,
                    target,
                    String.join(" ", Arrays.copyOfRange(args, 1, args.length)),
                    punishmentHandler,
                    null);
            UtilServer.runTask(core, () -> punishmentMenu.show(player));
        });

    }

}
