package me.mykindos.betterpvp.core.command.commands.admin;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.arguments.BPvPArgumentTypes;
import me.mykindos.betterpvp.core.command.menus.PlayerInventoryMenu;
import me.mykindos.betterpvp.core.item.ItemRegistry;
import me.mykindos.betterpvp.core.item.service.ComponentLookupService;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilInventory;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import net.kyori.adventure.text.Component;
import org.bukkit.craftbukkit.inventory.CraftInventoryPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Opens a player's inventory, live if they are online and from their saved data if not.
 */
@Singleton
public class InvSeeCommand extends BrigadierCommand {

    private final ItemRegistry registry;
    private final ComponentLookupService lookupService;

    @Inject
    public InvSeeCommand(ClientManager clientManager, ItemRegistry registry, ComponentLookupService lookupService) {
        super(clientManager);
        this.registry = registry;
        this.lookupService = lookupService;
        getAliases().add("openinv");
    }

    @Override
    public String getName() {
        return "invsee";
    }

    @Override
    public String getDescription() {
        return "Opens a target players inventory";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.inv-see.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("player", BPvPArgumentTypes.playerName())
                        .executes(context -> {
                            final Player player = getPlayerFromExecutor(context);
                            final String targetName = context.getArgument("player", String.class);
                            clientManager.search().offline(targetName).thenAcceptAsync(clientOptional -> {
                                if (clientOptional.isEmpty()) {
                                    UtilMessage.message(player, "core.prefix.command", "core.command.invsee.target_not_found", targetName);
                                    return;
                                }
                                final Client target = clientOptional.get();
                                final Player targetPlayer = target.getGamer().getPlayer();
                                if (targetPlayer != null) {
                                    UtilServer.runTask(JavaPlugin.getPlugin(Core.class), () -> new PlayerInventoryMenu(registry, lookupService,
                                            targetPlayer, targetPlayer.getName(), targetPlayer.getUniqueId(),
                                            (CraftInventoryPlayer) targetPlayer.getInventory(), false).show(player));
                                    return;
                                }
                                final CraftInventoryPlayer inventory = UtilInventory.getOfflineInventory(target.getName(), target.getUniqueId());
                                UtilServer.runTask(JavaPlugin.getPlugin(Core.class), () -> new PlayerInventoryMenu(registry, lookupService,
                                        null, target.getName(), target.getUniqueId(), inventory, true).show(player));
                            });
                            return Command.SINGLE_SUCCESS;
                        }));
    }
}
