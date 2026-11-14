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
import me.mykindos.betterpvp.core.item.menu.viewer.GuiItemViewer;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.recipe.RecipeRegistries;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * {@code /items} opens the item and recipe viewer.
 */
@Singleton
public class ItemsCommand extends BrigadierCommand {

    private final ItemFactory itemFactory;
    private final RecipeRegistries recipeRegistries;

    @Inject
    public ItemsCommand(ClientManager clientManager, ItemFactory itemFactory, RecipeRegistries recipeRegistries) {
        super(clientManager);
        this.itemFactory = itemFactory;
        this.recipeRegistries = recipeRegistries;
        getAliases().add("recipes");
        getAliases().add("recipe");
    }

    @Override
    public String getName() {
        return "items";
    }

    @Override
    public String getDescription() {
        return "View all items and their recipes";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.items.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    new GuiItemViewer(itemFactory, recipeRegistries).show(player);
                    return Command.SINGLE_SUCCESS;
                });
    }
}
