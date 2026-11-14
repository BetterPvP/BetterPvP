package me.mykindos.betterpvp.champions.champions.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.champions.champions.commands.menu.KitMenu;
import me.mykindos.betterpvp.champions.champions.roles.RoleManager;
import me.mykindos.betterpvp.core.client.Rank;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.config.Config;
import me.mykindos.betterpvp.core.cooldowns.CooldownManager;
import me.mykindos.betterpvp.core.item.ItemFactory;
import me.mykindos.betterpvp.core.locale.Translations;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Opens the kit menu. Below admin, it can only be opened once per cooldown.
 */
@Singleton
public class KitCommand extends BrigadierCommand {

    @Inject
    @Config(path = "command.kit.kitCooldown", defaultValue = "true")
    private boolean kitCooldown;

    @Inject
    @Config(path = "command.kit.kitCooldownMinutes", defaultValue = "60.0")
    private double kitCooldownMinutes;

    private final CooldownManager cooldownManager;
    private final RoleManager roleManager;
    private final ItemFactory itemFactory;

    @Inject
    public KitCommand(ClientManager clientManager, CooldownManager cooldownManager, RoleManager roleManager, ItemFactory itemFactory) {
        super(clientManager);
        this.cooldownManager = cooldownManager;
        this.roleManager = roleManager;
        this.itemFactory = itemFactory;
    }

    @Override
    public String getName() {
        return "kit";
    }

    @Override
    public String getDescription() {
        return "Equip a kit";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("champions.command.kit.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    final KitMenu kitMenu = new KitMenu(roleManager, itemFactory, true);
                    if (getClientFromExecutor(context).hasRank(Rank.ADMIN)
                            || cooldownManager.use(player, "Kit", kitCooldownMinutes * 60.0, true, false, false, false)) {
                        kitMenu.show(player);
                    }
                    return Command.SINGLE_SUCCESS;
                });
    }
}
