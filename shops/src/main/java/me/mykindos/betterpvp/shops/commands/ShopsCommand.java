package me.mykindos.betterpvp.shops.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.model.Reloadable;
import me.mykindos.betterpvp.shops.Shops;
import me.mykindos.betterpvp.shops.commands.loader.BrigadierShopsCommandLoader;
import me.mykindos.betterpvp.shops.commands.loader.ShopsCommandLoader;
import me.mykindos.betterpvp.shops.shops.ShopManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * {@code /shops reload} reloads Shops' config, reloadables, shop items and commands.
 */
@Singleton
public class ShopsCommand extends BrigadierCommand {

    private final Shops shops;
    private final ShopsCommandLoader commandLoader;
    private final BrigadierShopsCommandLoader brigadierCommandLoader;
    private final ShopManager shopManager;

    @Inject
    public ShopsCommand(ClientManager clientManager, Shops shops, ShopsCommandLoader commandLoader,
                        BrigadierShopsCommandLoader brigadierCommandLoader, ShopManager shopManager) {
        super(clientManager);
        this.shops = shops;
        this.commandLoader = commandLoader;
        this.brigadierCommandLoader = brigadierCommandLoader;
        this.shopManager = shopManager;
        getAliases().add("shop");
    }

    @Override
    public String getName() {
        return "shops";
    }

    @Override
    public String getDescription() {
        return "Shops base command";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("shops.command.shops.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.literal("reload")
                        .executes(context -> {
                            shops.reload();
                            shops.getReloadables().forEach(Reloadable::reload);

                            shopManager.loadShopItems();
                            brigadierCommandLoader.reload();
                            Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
                            commandLoader.reload(shops.getClass().getPackageName());

                            UtilMessage.message(context.getSource().getSender(), "shops.prefix", "shops.command.reload.success");
                            return Command.SINGLE_SUCCESS;
                        }));
    }
}
