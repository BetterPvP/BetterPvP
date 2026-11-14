package me.mykindos.betterpvp.clans.commands.commands;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import java.util.Optional;
import me.mykindos.betterpvp.clans.Clans;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.ClanManager;
import me.mykindos.betterpvp.clans.commands.arguments.exceptions.ClanArgumentException;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.components.clans.data.ClanMember;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a {@link Clans} {@link BrigadierCommand}, with some common methods using {@link ClanManager}
 */
public abstract class ClanBrigadierCommand extends BrigadierCommand {
    protected final ClanManager clanManager;
    protected ClanBrigadierCommand(ClientManager clientManager, ClanManager clanManager) {
        super(clientManager);
        this.clanManager = clanManager;
    }

    /**
     * checks if the {@link CommandSourceStack#getExecutor() executor} has a {@link Clan}
     * @param stack the {@link CommandSourceStack}
     * @return {@code true} if the {@link CommandSourceStack#getExecutor() executor} is in a {@link Clan},
     * {@code false} otherwise
     */
    protected boolean executorHasAClan(@NotNull CommandSourceStack stack) {
        if (stack.getExecutor() instanceof final Player player) {
            return clanManager.getClanByPlayer(player).isPresent();
        }
        return false;
    }


    /**
     * Gets the {@link Clan} by the {@link CommandSourceStack#getExecutor() executor}
     * @param context the {@link CommandContext}
     * @return the {@link Clan} of the {@link CommandSourceStack#getExecutor() executor}
     * @throws CommandSyntaxException if the {@link CommandSourceStack#getExecutor() executor} is not in a {@link Clan}
     * @see BrigadierCommand#getPlayerFromExecutor(CommandContext)
     */
    @NotNull
    protected Clan getClanByExecutor(@NotNull CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final Player player = getPlayerFromExecutor(context);
        return clanManager.getClanByPlayer(player).orElseThrow(() -> ClanArgumentException.NOT_IN_A_CLAN_EXCEPTION.create(player.getName()));

    }


    //Since we cannot throw a CommandSyntaxException in async contexts, this will pseudo throw on an empty optional.

    /**
     * Gets the requested Clan by Client, or informs the CommandSender that it does not exist
     * If the optional is empty, the client will be informed that a clan does not exist
     * @param client the client
     * @param commandSender the player sending the command
     */
    protected Optional<Clan> getClanByClient(@NotNull Client client, @NotNull CommandSender commandSender) {
        final Optional<Clan> clanOptional = clanManager.getClanByClient(client);
            if (clanOptional.isEmpty()) {
                UtilMessage.sendCommandSyntaxException(commandSender, ClanArgumentException.NOT_IN_A_CLAN_EXCEPTION.create(client.getName()));
            }
            return clanOptional;
    }

    /**
     * @param items   every entry, in order
     * @param page    the page number, starting at 1
     * @param perPage entries on a full page
     * @return the entries on that page, empty past the last page
     */
    protected static <T> List<T> page(List<T> items, int page, int perPage) {
        final int start = (page - 1) * perPage;
        if (start >= items.size()) {
            return List.of();
        }
        return items.subList(start, Math.min(start + perPage, items.size()));
    }

    protected static int pageCount(int size, int perPage) {
        return (size + perPage - 1) / perPage;
    }

    /**
     * The requirement-summary line for whether a command needs a clan, and whether the executor has one.
     */
    protected static Component clanRequirementLine(boolean needsClan, boolean inClan) {
        return Translations.component("clans.command.requirement.needs-clan", flag(needsClan)).color(NamedTextColor.WHITE)
                .append(Component.text(" | ", NamedTextColor.GRAY))
                .append(Translations.component("clans.command.requirement.in-clan", flag(inClan)).color(NamedTextColor.WHITE));
    }

    /**
     * The requirement-summary line for the clan rank a command needs, and whether the executor holds it.
     */
    protected static Component clanRankRequirementLine(ClanMember.MemberRank rank, boolean qualifies) {
        return Translations.component("clans.command.requirement.clan-rank", Component.text(rank.name(), NamedTextColor.GREEN)).color(NamedTextColor.WHITE)
                .append(Component.text(" | ", NamedTextColor.GRAY))
                .append(Translations.component("core.command.requirement.qualifies", flag(qualifies)).color(NamedTextColor.WHITE));
    }

    private static Component flag(boolean value) {
        return Component.text(value, value ? NamedTextColor.GREEN : NamedTextColor.RED);
    }

    /**
     * A menu title in the viewer's language. Menus take their title as plain text, so the server renders it.
     */
    protected static String menuTitle(Player viewer, String key, ComponentLike... args) {
        return PlainTextComponentSerializer.plainText().serialize(Translations.render(Translations.component(key, args), viewer.locale()));
    }
}
