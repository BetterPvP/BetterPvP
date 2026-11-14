package me.mykindos.betterpvp.core.item.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.item.ItemFactory;
import me.mykindos.betterpvp.core.item.purity.bias.PurityReforgeBiasRegistry;
import me.mykindos.betterpvp.core.item.reforging.GuiReforge;
import me.mykindos.betterpvp.core.locale.Translations;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * {@code /reforge} opens the reforge menu.
 */
@Singleton
public class ReforgeCommand extends BrigadierCommand {

    private final ItemFactory itemFactory;
    private final PurityReforgeBiasRegistry biasRegistry;

    @Inject
    public ReforgeCommand(ClientManager clientManager, ItemFactory itemFactory, PurityReforgeBiasRegistry biasRegistry) {
        super(clientManager);
        this.itemFactory = itemFactory;
        this.biasRegistry = biasRegistry;
    }

    @Override
    public String getName() {
        return "reforge";
    }

    @Override
    public String getDescription() {
        return "Admin command to reforge items";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.reforge.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    new GuiReforge(player, itemFactory, biasRegistry).show(player);
                    return Command.SINGLE_SUCCESS;
                });
    }
}
