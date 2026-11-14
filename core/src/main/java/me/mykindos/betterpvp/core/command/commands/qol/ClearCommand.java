package me.mykindos.betterpvp.core.command.commands.qol;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.CustomLog;
import me.mykindos.betterpvp.core.client.Rank;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.arguments.BPvPArgumentTypes;
import me.mykindos.betterpvp.core.item.ItemFactory;
import me.mykindos.betterpvp.core.item.ItemInstance;
import me.mykindos.betterpvp.core.item.ItemRegistry;
import me.mykindos.betterpvp.core.item.component.impl.uuid.UUIDProperty;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.menu.impl.ConfirmationMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Clears the executor's inventory, or a named player's. Clearing tracked items asks for confirmation and logs each
 * one.
 */
@Singleton
@CustomLog
public class ClearCommand extends BrigadierCommand {

    private final ItemFactory itemFactory;
    private final ItemRegistry registry;

    @Inject
    public ClearCommand(ClientManager clientManager, ItemFactory itemFactory, ItemRegistry registry) {
        super(clientManager);
        this.itemFactory = itemFactory;
        this.registry = registry;
    }

    @Override
    public String getName() {
        return "clear";
    }

    @Override
    public String getDescription() {
        return "Clear your inventory";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.clear.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    doClear(player, player);
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierNodes.argument("player", BPvPArgumentTypes.playerName())
                        .executes(context -> {
                            final Player runner = getPlayerFromExecutor(context);
                            final Player target = Bukkit.getPlayer(context.getArgument("player", String.class));
                            if (target != null) {
                                doClear(runner, target);
                            }
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private Map<ItemInstance, UUID> getUUIDItems(Player target) {
        final Map<ItemInstance, UUID> map = new HashMap<>();
        for (ItemInstance item : itemFactory.fromArray(target.getInventory().getContents())) {
            if (item != null) {
                item.getComponent(UUIDProperty.class).ifPresent(uuidProperty -> map.put(item, uuidProperty.getUniqueId()));
            }
        }
        return map;
    }

    private void doClear(Player runner, Player target) {
        final Component successFeedback = Translations.component("core.command.clear.success",
                Component.text(runner.getName()),
                Component.text(target.getName()));
        if (getUUIDItems(target).isEmpty()) {
            target.getInventory().clear();
            clientManager.sendMessageToRank("core.prefix.clear", successFeedback, Rank.TRIAL_MOD);
            return;
        }

        // The menu takes its title as plain text, so it is rendered in the runner's language here
        final String title = PlainTextComponentSerializer.plainText().serialize(Translations.render(
                Translations.component("core.command.clear.confirmation_title"), runner.locale()));
        new ConfirmationMenu(title, success -> {
            if (Boolean.TRUE.equals(success)) {
                getUUIDItems(target).forEach((item, uuid) -> log.info("{} cleared ({}) from {}'s inventory",
                                runner.getName(), uuid, target.getName())
                        .setAction("ITEM_CLEAR")
                        .addClientContext(runner)
                        .addItemContext(registry, item)
                        .addClientContext(target, true)
                        .submit());
                target.getInventory().clear();
                clientManager.sendMessageToRank("core.prefix.clear", successFeedback, Rank.TRIAL_MOD);
            }
        }).show(runner);
    }
}
