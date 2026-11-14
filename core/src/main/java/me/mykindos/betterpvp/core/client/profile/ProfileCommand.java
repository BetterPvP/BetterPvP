package me.mykindos.betterpvp.core.client.profile;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.achievements.repository.AchievementManager;
import me.mykindos.betterpvp.core.client.profile.menu.ProfileMenu;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.client.stats.RealmManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * {@code /profile} opens the executor's profile.
 */
@Singleton
public class ProfileCommand extends BrigadierCommand {

    private final AchievementManager achievementManager;
    private final RealmManager realmManager;

    @Inject
    public ProfileCommand(ClientManager clientManager, AchievementManager achievementManager, RealmManager realmManager) {
        super(clientManager);
        this.achievementManager = achievementManager;
        this.realmManager = realmManager;
    }

    @Override
    public String getName() {
        return "profile";
    }

    @Override
    public String getDescription() {
        return "Opens a player profile";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.profile.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    new ProfileMenu(getClientFromExecutor(context), achievementManager, realmManager).show(player);
                    return Command.SINGLE_SUCCESS;
                });
    }
}
