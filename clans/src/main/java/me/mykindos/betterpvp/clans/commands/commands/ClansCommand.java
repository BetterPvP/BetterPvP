package me.mykindos.betterpvp.clans.commands.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.clans.Clans;
import me.mykindos.betterpvp.clans.clans.core.vault.restriction.ClanVaultRestrictions;
import me.mykindos.betterpvp.clans.commands.BrigadierClansCommandLoader;
import me.mykindos.betterpvp.clans.commands.ClansCommandLoader;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.tips.TipManager;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.model.Reloadable;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * {@code /clans reload} reloads Clans' config, reloadables, tips, vault restrictions and commands.
 */
@Singleton
public class ClansCommand extends BrigadierCommand {

    private final Clans clans;
    private final ClansCommandLoader commandLoader;
    private final BrigadierClansCommandLoader brigadierClansCommandLoader;
    private final TipManager tipManager;
    private final ClanVaultRestrictions clanVaultRestrictions;

    @Inject
    public ClansCommand(ClientManager clientManager, Clans clans, ClansCommandLoader commandLoader,
                        BrigadierClansCommandLoader brigadierClansCommandLoader, TipManager tipManager,
                        ClanVaultRestrictions clanVaultRestrictions) {
        super(clientManager);
        this.clans = clans;
        this.commandLoader = commandLoader;
        this.brigadierClansCommandLoader = brigadierClansCommandLoader;
        this.tipManager = tipManager;
        this.clanVaultRestrictions = clanVaultRestrictions;
    }

    @Override
    public String getName() {
        return "clans";
    }

    @Override
    public String getDescription() {
        return "Base clans command";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("clans.command.clans.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.literal("reload")
                        .executes(context -> {
                            clans.reload();
                            clans.getReloadables().forEach(Reloadable::reload);

                            brigadierClansCommandLoader.reload();
                            Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
                            commandLoader.reload(clans.getClass().getPackageName());
                            tipManager.reloadTips(clans);
                            clanVaultRestrictions.reload();

                            UtilMessage.message(context.getSource().getSender(), "clans.prefix", "clans.command.clans.reload.success");
                            return Command.SINGLE_SUCCESS;
                        }));
    }
}
