package me.mykindos.betterpvp.core.client.rewards;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import lombok.CustomLog;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.client.repository.ClientSQLLayer;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.framework.server.CrossServerMessageService;
import me.mykindos.betterpvp.core.framework.server.ServerMessage;
import me.mykindos.betterpvp.core.item.BaseItem;
import me.mykindos.betterpvp.core.item.ItemFactory;
import me.mykindos.betterpvp.core.locale.Translations;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@Singleton
@CustomLog
public class RewardsTestCommand extends BrigadierCommand {

    private final ClientSQLLayer clientSQLLayer;
    private final ItemFactory itemFactory;
    private final CrossServerMessageService crossServerMessageService;

    @Inject
    public RewardsTestCommand(ClientManager clientManager, ClientSQLLayer clientSQLLayer, ItemFactory itemFactory, CrossServerMessageService crossServerMessageService) {
        super(clientManager);
        this.clientSQLLayer = clientSQLLayer;
        this.itemFactory = itemFactory;
        this.crossServerMessageService = crossServerMessageService;
    }

    @Override
    public String getName() {
        return "rewardstest";
    }

    @Override
    public String getDescription() {
        return "Open the rewards menu";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.rewards-test.description");
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
        final BaseItem baseItem = Objects.requireNonNull(itemFactory.getItemRegistry().getItem("dungeons:dungeon_token"));
        ItemStack itemStack = itemFactory.create(baseItem).createItemStack();
        CompletableFuture.runAsync(() -> {
            crossServerMessageService.broadcast(ServerMessage.builder()
                    .channel("ChampionsWinsReward")
                    .message("TEST")
                    .metadata("uuid", player.getUniqueId().toString())
                    .build());

            RewardBox rewardBox = clientSQLLayer.getRewardBox(client);
            rewardBox.getContents().add(itemStack);
            clientSQLLayer.updateClientRewards(client, rewardBox);
        }).exceptionally(ex -> {
            log.info("Error while executing rewards command", ex).submit();
            return null;
        });
    }
}
