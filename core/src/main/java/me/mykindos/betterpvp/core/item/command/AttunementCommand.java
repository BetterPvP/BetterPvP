package me.mykindos.betterpvp.core.item.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Optional;
import java.util.function.UnaryOperator;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.item.ItemFactory;
import me.mykindos.betterpvp.core.item.ItemInstance;
import me.mykindos.betterpvp.core.item.attunement.GuiAttunement;
import me.mykindos.betterpvp.core.item.component.impl.purity.PurityComponent;
import me.mykindos.betterpvp.core.item.runeslot.RuneSlotDistributionRegistry;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Admin command to attune or unattune items.
 * <ul>
 *     <li>{@code /attune} opens the attunement menu</li>
 *     <li>{@code /attune set <true|false> [--all]} sets the attuned state of the held item, or every item</li>
 *     <li>{@code /attune toggle [--all]} flips the attuned state of the held item, or every item</li>
 * </ul>
 */
@Singleton
public class AttunementCommand extends BrigadierCommand {

    private static final String PREFIX = "core.prefix.attunement";
    private static final String ALL = "--all";

    private final ItemFactory itemFactory;
    private final RuneSlotDistributionRegistry runeSlotRegistry;

    @Inject
    public AttunementCommand(ClientManager clientManager, ItemFactory itemFactory, RuneSlotDistributionRegistry runeSlotRegistry) {
        super(clientManager);
        this.itemFactory = itemFactory;
        this.runeSlotRegistry = runeSlotRegistry;
    }

    @Override
    public String getName() {
        return "attune";
    }

    @Override
    public String getDescription() {
        return "Admin command to attune/unattune items";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.attunement.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    new GuiAttunement(player, itemFactory, runeSlotRegistry).show(player);
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierNodes.literal("toggle")
                        .executes(context -> apply(context, false, attuned -> !attuned))
                        .then(BrigadierNodes.literal(ALL)
                                .executes(context -> apply(context, true, attuned -> !attuned))))
                .then(BrigadierNodes.literal("set")
                        .then(BrigadierNodes.argument("attuned", BoolArgumentType.bool())
                                .executes(context -> apply(context, false, attuned -> context.getArgument("attuned", Boolean.class)))
                                .then(BrigadierNodes.literal(ALL)
                                        .executes(context -> apply(context, true, attuned -> context.getArgument("attuned", Boolean.class))))));
    }

    private int apply(CommandContext<CommandSourceStack> context, boolean all, UnaryOperator<Boolean> change) throws CommandSyntaxException {
        final Player player = getPlayerFromExecutor(context);
        if (all) {
            applyToInventory(player, change);
            return Command.SINGLE_SUCCESS;
        }
        final ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            UtilMessage.message(player, PREFIX, "core.command.attune.holding_item");
            return Command.SINGLE_SUCCESS;
        }
        final Optional<ItemInstance> instance = itemFactory.fromItemStack(held);
        if (instance.isEmpty()) {
            UtilMessage.message(player, PREFIX, "core.command.attune.read_error");
            return Command.SINGLE_SUCCESS;
        }
        final Optional<PurityComponent> purity = instance.get().getComponent(PurityComponent.class);
        if (purity.isEmpty()) {
            UtilMessage.message(player, PREFIX, "core.command.attune.no_purity");
            return Command.SINGLE_SUCCESS;
        }
        final boolean attuned = change.apply(purity.get().isAttuned());
        player.getInventory().setItemInMainHand(instance.get().withComponent(purity.get().withAttuned(attuned)).createItemStack());
        UtilMessage.message(player, PREFIX, "core.command.attune.success",
                Translations.component(attuned ? "core.command.attune.state.attuned" : "core.command.attune.state.unattuned")
                        .color(NamedTextColor.YELLOW));
        return Command.SINGLE_SUCCESS;
    }

    private void applyToInventory(Player player, UnaryOperator<Boolean> change) {
        int processed = 0;
        final ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            final ItemStack item = contents[slot];
            if (item == null || item.getType().isAir()) {
                continue;
            }
            final Optional<ItemInstance> instance = itemFactory.fromItemStack(item);
            final Optional<PurityComponent> purity = instance.flatMap(found -> found.getComponent(PurityComponent.class));
            if (purity.isEmpty()) {
                continue;
            }
            final PurityComponent changed = purity.get().withAttuned(change.apply(purity.get().isAttuned()));
            player.getInventory().setItem(slot, instance.get().withComponent(changed).createItemStack());
            processed++;
        }
        if (processed == 0) {
            UtilMessage.message(player, PREFIX, "core.command.attune.no_purity_found");
        } else {
            UtilMessage.message(player, PREFIX, "core.command.attune.processed", processed);
        }
    }
}
