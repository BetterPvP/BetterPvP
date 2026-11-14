package me.mykindos.betterpvp.core.item.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.persistence.PersistentDataContainerView;
import java.util.Map;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.item.BaseItem;
import me.mykindos.betterpvp.core.item.ItemFactory;
import me.mykindos.betterpvp.core.item.ItemInstance;
import me.mykindos.betterpvp.core.item.ItemRegistry;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /mockitem <item>} turns the held item into the named base item by copying that item's data onto it, keeping
 * the held item's look.
 */
@Singleton
public class MockItemCommand extends BrigadierCommand {

    private final ItemFactory itemFactory;
    private final ItemRegistry registry;

    @Inject
    public MockItemCommand(ClientManager clientManager, ItemFactory itemFactory, ItemRegistry registry) {
        super(clientManager);
        this.itemFactory = itemFactory;
        this.registry = registry;
    }

    @Override
    public String getName() {
        return "mockitem";
    }

    @Override
    public String getDescription() {
        return "Mock an item by setting your currently held item to a base item's ID";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.mock-item.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        // A namespaced key has a colon, which a single word cannot hold
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("item", StringArgumentType.greedyString())
                        .suggests((context, builder) -> {
                            registry.getItems().keySet().stream()
                                    .map(NamespacedKey::toString)
                                    .filter(key -> key.startsWith(builder.getRemainingLowerCase()))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            mock(getPlayerFromExecutor(context), context.getArgument("item", String.class));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private void mock(Player player, String name) {
        final ItemStack mainHand = player.getEquipment().getItemInMainHand();
        if (mainHand.getType().isAir()) {
            UtilMessage.message(player, "core.prefix.mock", "core.command.mockitem.holding_item");
            return;
        }
        final BaseItem baseItem = resolve(player, name);
        if (baseItem == null) {
            return;
        }
        final ItemInstance newInstance = itemFactory.create(baseItem);
        final PersistentDataContainerView pdc = newInstance.getItemStack().getPersistentDataContainer();
        final ItemMeta meta = mainHand.getItemMeta();
        pdc.copyTo(meta.getPersistentDataContainer(), true);
        mainHand.setItemMeta(meta);
        player.getInventory().setItemInMainHand(mainHand);
        UtilMessage.message(player, "core.prefix.mock", "core.command.mockitem.success",
                Component.text(registry.getKey(baseItem).toString(), NamedTextColor.YELLOW));
    }

    /**
     * Finds the base item a full key or a bare key names, telling the player when it names none or several.
     */
    private @Nullable BaseItem resolve(Player player, String name) {
        final BaseItem exact = registry.getItem(name);
        if (exact != null) {
            return exact;
        }
        final Map<NamespacedKey, BaseItem> options = registry.getItemsByKey(name);
        if (options.isEmpty()) {
            UtilMessage.message(player, "core.prefix.command", "core.command.mockitem.invalid_item", Component.text(name));
            return null;
        }
        if (options.size() > 1) {
            UtilMessage.message(player, "core.prefix.command", "core.command.mockitem.too_many_matches", Component.text(name));
            return null;
        }
        return options.values().iterator().next();
    }
}
