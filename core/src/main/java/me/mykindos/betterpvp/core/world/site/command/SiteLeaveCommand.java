package me.mykindos.betterpvp.core.world.site.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.world.site.SiteExitService;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /leavesite} returns the executor to where they set out from.
 */
@Singleton
public class SiteLeaveCommand extends BrigadierCommand {

    private final SiteExitService exitService;

    @Inject
    public SiteLeaveCommand(ClientManager clientManager, @NotNull SiteExitService exitService) {
        super(clientManager);
        this.exitService = exitService;
    }

    @Override
    public String getName() {
        return "leavesite";
    }

    @Override
    public String getDescription() {
        return "Leave where you are and return to where you set out from";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.leavesite.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    exitService.leave(getPlayerFromExecutor(context));
                    return Command.SINGLE_SUCCESS;
                });
    }
}
