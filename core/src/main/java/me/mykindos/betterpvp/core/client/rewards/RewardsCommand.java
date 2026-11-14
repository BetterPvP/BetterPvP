package me.mykindos.betterpvp.core.client.rewards;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.concurrent.CompletableFuture;
import lombok.CustomLog;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.client.repository.ClientSQLLayer;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.cooldowns.CooldownManager;
import me.mykindos.betterpvp.core.item.ItemFactory;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

@Singleton
@CustomLog
public class RewardsCommand extends BrigadierCommand {

    private final Core core;
    private final ClientSQLLayer clientSQLLayer;
    private final ItemFactory itemFactory;
    private final CooldownManager cooldownManager;

    @Inject
    public RewardsCommand(ClientManager clientManager, Core core, ClientSQLLayer clientSQLLayer, ItemFactory itemFactory, CooldownManager cooldownManager) {
        super(clientManager);
        this.core = core;
        this.clientSQLLayer = clientSQLLayer;
        this.itemFactory = itemFactory;
        this.cooldownManager = cooldownManager;
        getAliases().add("inventory");
    }

    @Override
    public String getName() {
        return "rewards";
    }

    @Override
    public String getDescription() {
        return "Open the rewards menu";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.rewards.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    execute(getPlayerFromExecutor(context), getClientFromExecutor(context));
                    return Command.SINGLE_SUCCESS;
                });
    }

    private void execute(Player player, Client client, String... args) {
        if (cooldownManager.use(player, "Inventory", 1, false, false)) {
            CompletableFuture.runAsync(() -> {
                RewardBox rewardBox = clientSQLLayer.getRewardBox(client);

                if(rewardBox.getContents().isEmpty()) {
                    UtilMessage.message(player, "core.prefix.inventory", "core.command.rewards.none");
                    return;
                }

                UtilServer.runTask(core, () -> {
                    new GuiRewardBox(rewardBox, itemFactory, null).show(player).addCloseHandler(() -> {
                        JavaPlugin.getPlugin(Core.class).getInjector().getInstance(ClientSQLLayer.class).updateClientRewards(client, rewardBox)
                                .whenComplete((unused, throwable) -> {
                                    if (throwable != null) {
                                        log.error("Failed to update client rewards for {}", throwable, client.getName());
                                        return;
                                    }

                                    rewardBox.setLocked(false);
                                });
                    });
                });
            }).exceptionally(ex -> {
                log.error("Error while opening rewards menu for " + player.getName(), ex).submit();
                return null;
            });
        } else {
            UtilMessage.message(player, "core.prefix.inventory", "core.command.rewards.cooldown");
        }
    }
}