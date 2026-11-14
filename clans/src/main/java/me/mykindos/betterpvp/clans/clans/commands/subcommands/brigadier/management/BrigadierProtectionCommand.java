package me.mykindos.betterpvp.clans.clans.commands.subcommands.brigadier.management;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.ClanManager;
import me.mykindos.betterpvp.clans.clans.ClanProperty;
import me.mykindos.betterpvp.clans.clans.commands.BrigadierClansCommand;
import me.mykindos.betterpvp.clans.clans.commands.subcommands.brigadier.BrigadierClanSubCommand;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.components.clans.data.ClanMember;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.menu.impl.ConfirmationMenu;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Ends the executor's clan's new-clan protection early, after a confirmation.
 */
@Singleton
@BrigadierSubCommand(BrigadierClansCommand.class)
public class BrigadierProtectionCommand extends BrigadierClanSubCommand {

    @Inject
    protected BrigadierProtectionCommand(ClientManager clientManager, ClanManager clanManager) {
        super(clientManager, clanManager);
    }

    @Override
    protected ClanMember.MemberRank requiredMemberRank() {
        return ClanMember.MemberRank.ADMIN;
    }

    @Override
    public String getName() {
        return "protection";
    }

    @Override
    public String getDescription() {
        return "Remove new clan protection";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("clans.command.protection.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player executor = getPlayerFromExecutor(context);
                    final Clan clan = getClanByExecutor(context);
                    if (!clan.isNoDominanceCooldownActive()) {
                        UtilMessage.message(executor, "clans.prefix", "clans.command.clan.protection.not-active");
                        return Command.SINGLE_SUCCESS;
                    }
                    new ConfirmationMenu(menuTitle(executor, "clans.command.clan.protection.confirmation"), success -> {
                        if (Boolean.TRUE.equals(success)) {
                            clan.saveProperty(ClanProperty.NO_DOMINANCE_COOLDOWN, 0L);
                            UtilMessage.message(executor, "clans.prefix", "clans.command.clan.protection.success");
                        }
                    }).show(executor);
                    return Command.SINGLE_SUCCESS;
                });
    }
}
