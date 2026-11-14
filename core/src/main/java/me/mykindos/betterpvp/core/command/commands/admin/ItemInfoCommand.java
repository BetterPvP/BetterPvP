package me.mykindos.betterpvp.core.command.commands.admin;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.Objects;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.arguments.BPvPArgumentTypes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Shows the held item's type, name, model data, durability and persistent data, and its data components when asked.
 */
@Singleton
public class ItemInfoCommand extends BrigadierCommand {

    @Inject
    public ItemInfoCommand(ClientManager clientManager) {
        super(clientManager);
    }

    @Override
    public String getName() {
        return "iteminfo";
    }

    @Override
    public String getDescription() {
        return "Displays hidden information for this item";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.item-info.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    showInfo(getPlayerFromExecutor(context), false);
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierNodes.argument("components", BPvPArgumentTypes.booleanType())
                        .executes(context -> {
                            showInfo(getPlayerFromExecutor(context), context.getArgument("components", Boolean.class));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private static void showInfo(Player player, boolean showComponents) {
        final ItemStack item = player.getInventory().getItemInMainHand();
        final ItemMeta itemMeta = item.getItemMeta();
        UtilMessage.message(player, "core.prefix.command", "core.command.iteminfo.type", item.getType().name());
        if (itemMeta == null) {
            UtilMessage.message(player, "core.prefix.command", "core.command.iteminfo.no_meta");
            return;
        }
        if (itemMeta.hasDisplayName()) {
            UtilMessage.message(player, "core.prefix.command", "core.command.iteminfo.name", Objects.requireNonNull(itemMeta.displayName()));
        }
        if (itemMeta.hasCustomModelData()) {
            UtilMessage.message(player, "core.prefix.command", "core.command.iteminfo.model_data", itemMeta.getCustomModelData());
        }
        if (item.hasData(DataComponentTypes.MAX_DAMAGE)) {
            final int maxDamage = item.getData(DataComponentTypes.MAX_DAMAGE);
            UtilMessage.message(player, "core.prefix.command", "core.command.iteminfo.max_durability", maxDamage);
            if (item.hasData(DataComponentTypes.DAMAGE)) {
                UtilMessage.message(player, "core.prefix.command", "core.command.iteminfo.durability", maxDamage - item.getData(DataComponentTypes.DAMAGE));
            }
        }
        if (showComponents) {
            for (DataComponentType dataType : item.getDataTypes()) {
                final Component value = dataType instanceof DataComponentType.Valued<?> valued
                        ? Component.text(item.getData(valued).toString(), NamedTextColor.GRAY)
                        : Translations.component("core.command.iteminfo.true").color(NamedTextColor.GREEN);
                UtilMessage.message(player, "core.prefix.command", "core.command.iteminfo.data_entry",
                        Component.text(String.valueOf(dataType.getKey()), NamedTextColor.YELLOW), value);
            }
        }
        final CraftPersistentDataContainer persistentData = (CraftPersistentDataContainer) itemMeta.getPersistentDataContainer();
        persistentData.getRaw().forEach((key, value) -> UtilMessage.message(player, "core.prefix.command", "core.command.iteminfo.data_entry",
                Component.text(String.valueOf(key), NamedTextColor.YELLOW),
                Component.text(value.toString(), NamedTextColor.GRAY)));
    }
}
