package me.mykindos.betterpvp.core.command.commands.general;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.chat.events.ChatSentEvent;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.cooldowns.CooldownManager;
import me.mykindos.betterpvp.core.item.ItemFactory;
import me.mykindos.betterpvp.core.item.ItemInstance;
import me.mykindos.betterpvp.core.item.renderer.LorePages;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Shows the held item in chat, with the item as a hover. A BetterPvP item can be shown at a chosen lore page.
 */
@Singleton
public class ShowItemCommand extends BrigadierCommand {

    private final ItemFactory itemFactory;
    private final CooldownManager cooldownManager;
    private final Core core;

    @Inject
    public ShowItemCommand(ClientManager clientManager, ItemFactory itemFactory, CooldownManager cooldownManager, Core core) {
        super(clientManager);
        this.itemFactory = itemFactory;
        this.cooldownManager = cooldownManager;
        this.core = core;
    }

    @Override
    public String getName() {
        return "showitem";
    }

    @Override
    public String getDescription() {
        return "Sends the current held item to chat";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.show-item.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    showItem(getPlayerFromExecutor(context), getClientFromExecutor(context), null);
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierNodes.argument("page", IntegerArgumentType.integer(1))
                        .suggests((context, builder) -> {
                            if (context.getSource().getExecutor() instanceof Player player) {
                                itemFactory.fromItemStack(player.getInventory().getItemInMainHand()).ifPresent(instance -> {
                                    for (int page = 1; page <= LorePages.visiblePages(instance).size(); page++) {
                                        builder.suggest(page);
                                    }
                                });
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            showItem(getPlayerFromExecutor(context), getClientFromExecutor(context), context.getArgument("page", Integer.class));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    /**
     * @param page the 1-based lore page to show, or {@code null} for the item's most relevant page
     */
    private void showItem(Player player, Client client, Integer page) {
        if (!cooldownManager.use(player, getName(), 15d, false, true)) {
            UtilMessage.message(player, "core.prefix.command", "core.command.showitem.cooldown");
            return;
        }

        ItemStack itemStack = player.getInventory().getItemInMainHand();
        final Optional<ItemInstance> instanceOpt = itemFactory.fromItemStack(itemStack);
        final Component name;
        if (instanceOpt.isPresent()) {
            final ItemInstance instance = instanceOpt.get();
            name = instance.getBaseItem().getItemNameRenderer().createName(instance);
            final int shownPage;
            if (page == null) {
                shownPage = LorePages.mostRelevant(instance);
            } else {
                final List<Integer> visiblePages = LorePages.visiblePages(instance);
                if (page > visiblePages.size()) {
                    UtilMessage.message(player, "core.prefix.command", "core.command.showitem.page_range",
                            Component.text(visiblePages.size()));
                    return;
                }
                shownPage = visiblePages.get(page - 1);
            }
            itemStack = instance.getView().get(null, shownPage);
        } else if (itemStack.getItemMeta().hasDisplayName()) {
            name = Objects.requireNonNull(itemStack.getItemMeta().displayName());
        } else {
            name = Objects.requireNonNullElse(itemStack.getData(DataComponentTypes.ITEM_NAME),
                    Component.translatable(itemStack.getType().translationKey()));
        }

        // For BetterPvP items, itemStack was rebuilt from the translatable view above so name/lore are
        // reconstructed from translatable components; for vanilla items it is the held item (only existing
        // translatable name/lore will be resolved, literal text is left untouched).
        final ItemStack shownItem = itemStack;
        final Component itemName = Objects.requireNonNull(name);
        // Per-recipient renderer: the chat pipeline calls this for each allowed recipient, so the hover item
        // is localized into THAT recipient's locale (name + lore). Recipient selection, mute, ignore, routing,
        // formatting and logging all stay inside the chat system.
        final Function<Player, Component> messageRenderer = recipient -> Translations.component("core.command.showitem.holding",
                        itemName.hoverEvent(Translations.renderItemStack(shownItem, recipient.locale())))
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.BOLD, false);
        // Representative message (sender's locale) used by the pipeline for filtering/logging and as a fallback.
        final Component baseMessage = messageRenderer.apply(player);
        UtilServer.runTaskAsync(core, () -> UtilServer.callEvent(new ChatSentEvent(player,
                client.getGamer().getChatChannel(), UtilMessage.deserialize("<yellow>%s:</yellow>"), baseMessage, messageRenderer)));
    }
}
