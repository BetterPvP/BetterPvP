package me.mykindos.betterpvp.champions.champions.commands.admin;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import lombok.CustomLog;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.arguments.BPvPArgumentTypes;
import me.mykindos.betterpvp.core.item.ItemFactory;
import me.mykindos.betterpvp.core.item.ItemInstance;
import me.mykindos.betterpvp.core.item.ItemRegistry;
import me.mykindos.betterpvp.core.item.component.impl.uuid.UUIDProperty;
import me.mykindos.betterpvp.core.item.component.impl.socketables.runes.RuneItem;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Gives a player one of every rune, logging each tracked item it spawns.
 */
@Singleton
@CustomLog
public class RunesCommand extends BrigadierCommand {

    private final ItemRegistry itemRegistry;
    private final ItemFactory itemFactory;

    @Inject
    public RunesCommand(ClientManager clientManager, ItemRegistry itemRegistry, ItemFactory itemFactory) {
        super(clientManager);
        this.itemRegistry = itemRegistry;
        this.itemFactory = itemFactory;
    }

    @Override
    public String getName() {
        return "runes";
    }

    @Override
    public String getDescription() {
        return "give the target all runes of the specified tier";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("champions.command.runes.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("player", BPvPArgumentTypes.playerName())
                        .executes(context -> {
                            final Player sender = getPlayerFromExecutor(context);
                            final String targetName = context.getArgument("player", String.class);
                            final Player target = Bukkit.getPlayer(targetName);
                            if (target == null) {
                                UtilMessage.message(sender, "core.prefix.command", "champions.command.runes.invalid-player",
                                        Component.text(targetName, NamedTextColor.YELLOW));
                                return Command.SINGLE_SUCCESS;
                            }
                            final List<ItemInstance> runes = itemRegistry.getItems().values().stream()
                                    .filter(item -> item instanceof RuneItem)
                                    .map(itemFactory::create)
                                    .toList();
                            for (ItemInstance item : runes) {
                                item.getComponent(UUIDProperty.class).ifPresent(uuidProperty ->
                                        log.info("{} spawned and gave ({}) to {}", sender.getName(), uuidProperty.getUniqueId(), target.getName())
                                                .setAction("ITEM_SPAWN")
                                                .addClientContext(sender)
                                                .addClientContext(target, true)
                                                .addItemContext(itemRegistry, item)
                                                .submit());
                                target.getInventory().addItem(item.createItemStack());
                            }
                            return Command.SINGLE_SUCCESS;
                        }));
    }
}
