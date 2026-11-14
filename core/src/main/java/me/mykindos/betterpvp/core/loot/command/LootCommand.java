package me.mykindos.betterpvp.core.loot.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import lombok.CustomLog;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.loot.LootTable;
import me.mykindos.betterpvp.core.loot.LootTableRegistry;
import me.mykindos.betterpvp.core.loot.menu.LootTableMenu;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * {@code /loot <table>} opens a loaded loot table's contents.
 */
@Singleton
@CustomLog
public class LootCommand extends BrigadierCommand {

    private final LootTableRegistry lootTableRegistry;

    @Inject
    public LootCommand(ClientManager clientManager, LootTableRegistry lootTableRegistry) {
        super(clientManager);
        this.lootTableRegistry = lootTableRegistry;
        getAliases().add("droptable");
        getAliases().add("loottable");
    }

    @Override
    public String getName() {
        return "loot";
    }

    @Override
    public String getDescription() {
        return "View a specific loot table";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.loot.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        // Loot table ids are namespaced keys, so a single word cannot hold them
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("table", StringArgumentType.greedyString())
                        .suggests((context, builder) -> {
                            lootTableRegistry.getLoaded().keySet().stream()
                                    .filter(id -> id.toLowerCase().startsWith(builder.getRemainingLowerCase()))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            final Player player = getPlayerFromExecutor(context);
                            final String id = context.getArgument("table", String.class);
                            final LootTable table = lootTableRegistry.getLoaded().get(id);
                            if (table == null) {
                                UtilMessage.message(player, "core.prefix.command", "core.command.loot.not_found");
                                return Command.SINGLE_SUCCESS;
                            }
                            log.info("{} opened the loot table for {}", player.getName(), id).submit();
                            new LootTableMenu(table, null).show(player);
                            return Command.SINGLE_SUCCESS;
                        }));
    }
}
