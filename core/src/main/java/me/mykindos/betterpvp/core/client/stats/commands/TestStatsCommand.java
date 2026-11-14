package me.mykindos.betterpvp.core.client.stats.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import lombok.CustomLog;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.client.stats.RealmManager;
import me.mykindos.betterpvp.core.client.stats.StatFilterType;
import me.mykindos.betterpvp.core.client.stats.display.start.StartStatMenu;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.model.IStringName;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

@Singleton
@CustomLog
public class TestStatsCommand extends BrigadierCommand implements IStringName {
    private final RealmManager realmManager;

    @Inject
    public TestStatsCommand(ClientManager clientManager, RealmManager realmManager) {
        super(clientManager);
        this.realmManager = realmManager;
    }

    @Override
    public String getName() {
        return "statsmenu";
    }

    @Override
    public String getDescription() {
        return "Show stats for yourself";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.test-stats.description");
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
        new StartStatMenu(client, null, StatFilterType.ALL, null, realmManager).show(player);
    }
}
