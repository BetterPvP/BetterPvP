package me.mykindos.betterpvp.core.tips.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.client.gamer.Gamer;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.tips.TipEvent;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * {@code /tip [amount]} shows the executor up to 20 tips.
 */
@Singleton
public class TipCommand extends BrigadierCommand {

    private static final int MAX_TIPS = 20;

    private final Core core;

    @Inject
    public TipCommand(ClientManager clientManager, Core core) {
        super(clientManager);
        this.core = core;
        getAliases().add("tips");
    }

    @Override
    public String getName() {
        return "tip";
    }

    @Override
    public String getDescription() {
        return "Get a valid tip";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.tip.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> sendTips(context, 1))
                .then(BrigadierNodes.argument("amount", IntegerArgumentType.integer(1))
                        .executes(context -> sendTips(context, context.getArgument("amount", Integer.class))));
    }

    private int sendTips(CommandContext<CommandSourceStack> context, int amount) throws CommandSyntaxException {
        final Player player = getPlayerFromExecutor(context);
        if (amount > MAX_TIPS) {
            UtilMessage.message(player, "core.prefix.command", "core.command.tip.limit");
            return Command.SINGLE_SUCCESS;
        }
        final Gamer gamer = getClientFromExecutor(context).getGamer();
        for (int i = 0; i < amount; i++) {
            UtilServer.runTaskAsync(core, () -> UtilServer.callEvent(new TipEvent(player, gamer)));
        }
        return Command.SINGLE_SUCCESS;
    }
}
