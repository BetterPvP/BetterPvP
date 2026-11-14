package me.mykindos.betterpvp.core.client.punishments.rules.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.punishments.menu.RuleMenu;
import me.mykindos.betterpvp.core.client.punishments.rules.RuleManager;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * {@code /rules} opens the server rules.
 */
@Singleton
public class RuleCommand extends BrigadierCommand {

    private final RuleManager ruleManager;

    @Inject
    public RuleCommand(ClientManager clientManager, RuleManager ruleManager) {
        super(clientManager);
        this.ruleManager = ruleManager;
    }

    @Override
    public String getName() {
        return "rules";
    }

    @Override
    public String getDescription() {
        return "Lists the rules of the server";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.rule.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    new RuleMenu(ruleManager, null).show(player);
                    return Command.SINGLE_SUCCESS;
                });
    }
}
