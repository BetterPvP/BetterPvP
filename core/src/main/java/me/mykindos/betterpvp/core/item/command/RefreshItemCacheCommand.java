package me.mykindos.betterpvp.core.item.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.item.menu.viewer.GuiItemViewer;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;

/**
 * {@code /refreshitemcache} drops the item viewer cache so the next viewer rebuilds it.
 */
@Singleton
public class RefreshItemCacheCommand extends BrigadierCommand {

    @Inject
    public RefreshItemCacheCommand(ClientManager clientManager) {
        super(clientManager);
    }

    @Override
    public String getName() {
        return "refreshitemcache";
    }

    @Override
    public String getDescription() {
        return "Rebuild the item viewer cache (use after editing a registered item's shape)";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.refresh-item-cache.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    GuiItemViewer.invalidateCache();
                    UtilMessage.message(context.getSource().getSender(), "core.prefix.command", "core.command.refreshitemcache.success");
                    return Command.SINGLE_SUCCESS;
                });
    }
}
