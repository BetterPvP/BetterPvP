package me.mykindos.betterpvp.clans.clans.commands.subcommands.brigadier.admin;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.ClanManager;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.Rank;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.config.ExtendedYamlConfiguration;
import me.mykindos.betterpvp.core.logging.repository.LogRepository;
import me.mykindos.betterpvp.core.utilities.search.SearchEngineBase;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("/clan adminlogs")
class BrigadierAdminLogsCommandTest {

    private final ClientManager clientManager = mock(ClientManager.class);
    private final ClanManager clanManager = mock(ClanManager.class);
    private final BrigadierAdminLogsCommand command = new BrigadierAdminLogsCommand(clientManager, clanManager, mock(LogRepository.class));

    @Test
    @DisplayName("CLANS-42 AC1 a clan name opens that clan's logs by its id")
    void clanNameResolvesToId() {
        final Clan clan = mock(Clan.class);
        when(clan.getId()).thenReturn(42L);
        when(clanManager.getClanByName("Red")).thenReturn(Optional.of(clan));

        assertEquals(Optional.of("42"), command.clanId("Red"));
    }

    @Test
    @DisplayName("CLANS-42 AC1 an id with no clan behind it still opens its logs, so disbanded clans can be read")
    void numericIdResolvesToItself() {
        when(clanManager.getClanByName(any())).thenReturn(Optional.empty());

        assertEquals(Optional.of("17"), command.clanId("17"));
        assertEquals(Optional.empty(), command.clanId("Nobody"));
    }

    @Test
    @DisplayName("CLANS-42 AC1 an admin with no clan can use it, and a player cannot")
    @SuppressWarnings("unchecked")
    void adminWithoutClanCanUse() {
        final SearchEngineBase<Client> search = mock(SearchEngineBase.class);
        when(clientManager.search()).thenReturn(search);
        command.setConfig(new ExtendedYamlConfiguration());

        assertTrue(command.requirement(source(search, Rank.ADMIN)));
        assertFalse(command.requirement(source(search, Rank.PLAYER)));
    }

    private static CommandSourceStack source(SearchEngineBase<Client> search, Rank rank) {
        final Player player = mock(Player.class);
        final Client client = mock(Client.class);
        when(client.hasRank(any())).thenAnswer(invocation -> rank.getId() >= invocation.<Rank>getArgument(0).getId());
        when(search.online(player)).thenReturn(client);
        final CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(player);
        when(source.getExecutor()).thenReturn(player);
        return source;
    }
}
