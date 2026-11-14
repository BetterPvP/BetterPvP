package me.mykindos.betterpvp.core.client.achievements.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.achievements.display.AchievementScopeMenu;
import me.mykindos.betterpvp.core.client.achievements.repository.AchievementManager;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.client.stats.RealmManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /achievements} opens the executor's achievements. From the console it prints the loaded achievements.
 */
@Singleton
public class AchievementsCommand extends BrigadierCommand {

    private final AchievementManager achievementManager;
    private final RealmManager realmManager;

    @Inject
    public AchievementsCommand(ClientManager clientManager, AchievementManager achievementManager, RealmManager realmManager) {
        super(clientManager);
        this.achievementManager = achievementManager;
        this.realmManager = realmManager;
    }

    @Override
    public String getName() {
        return "achievements";
    }

    @Override
    public String getDescription() {
        return "Show achievements";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.achievements.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final CommandSender sender = context.getSource().getSender();
                    if (!(context.getSource().getExecutor() instanceof Player player)) {
                        sender.sendMessage(achievementManager.getObjects().toString());
                        return Command.SINGLE_SUCCESS;
                    }
                    new AchievementScopeMenu(getClientFromExecutor(context), achievementManager, realmManager, null).show(player);
                    return Command.SINGLE_SUCCESS;
                });
    }
}
