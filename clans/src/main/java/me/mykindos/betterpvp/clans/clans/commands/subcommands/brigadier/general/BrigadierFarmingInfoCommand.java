package me.mykindos.betterpvp.clans.clans.commands.subcommands.brigadier.general;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.clans.Clans;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.ClanManager;
import me.mykindos.betterpvp.clans.clans.commands.BrigadierClansCommand;
import me.mykindos.betterpvp.clans.clans.commands.subcommands.brigadier.BrigadierClanSubCommand;
import me.mykindos.betterpvp.clans.clans.leveling.ClanPerkManager;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.components.clans.data.ClanMember;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * Shows the Y levels the executor's clan can farm at, and whether its crops grow while it is offline.
 */
@Singleton
@BrigadierSubCommand(BrigadierClansCommand.class)
public class BrigadierFarmingInfoCommand extends BrigadierClanSubCommand {

    private final int baseFarmingY;
    private final int baseFarmingLevels;
    private final boolean offlineGrowing;

    @Inject
    protected BrigadierFarmingInfoCommand(ClientManager clientManager, ClanManager clanManager, Clans clans) {
        super(clientManager, clanManager);
        getAliases().add("farminglevels");
        baseFarmingY = clans.getConfig().getInt("clans.farming.baseY", 0);
        baseFarmingLevels = clans.getConfig().getInt("clans.farming.baseFarmingLevels", 0);
        offlineGrowing = clans.getConfig().getBoolean("clans.farming.allowOfflineGrowing", false);
    }

    @Override
    protected ClanMember.MemberRank requiredMemberRank() {
        return ClanMember.MemberRank.RECRUIT;
    }

    @Override
    public String getName() {
        return "farminginfo";
    }

    @Override
    public String getDescription() {
        return "See information about your clan's farming capabilities.";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("clans.command.farming-info.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player executor = getPlayerFromExecutor(context);
                    final Clan clan = getClanByExecutor(context);
                    final int minY = ClanPerkManager.getInstance().computeFarmingLevelMinY(clan, baseFarmingY, baseFarmingLevels);
                    UtilMessage.message(executor, "clans.prefix", farmingInfo(minY));
                    return Command.SINGLE_SUCCESS;
                });
    }

    Component farmingInfo(int minY) {
        final Component onlineRequired = offlineGrowing
                ? Translations.component("clans.command.clan.farming.no").color(NamedTextColor.RED)
                : Translations.component("clans.command.clan.farming.yes").color(NamedTextColor.GREEN);
        return Translations.component("clans.command.clan.farming.header")
                .appendNewline()
                .append(Translations.component("clans.command.clan.farming.y-levels"))
                .append(Translations.component("clans.command.clan.farming.y-range",
                        Component.text(baseFarmingY), Component.text(minY)).color(NamedTextColor.GREEN))
                .appendNewline()
                .append(Translations.component("clans.command.clan.farming.online-growing-required"))
                .append(onlineRequired);
    }
}
