package me.mykindos.betterpvp.clans.clans.commands.subcommands.brigadier.management;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.ClanManager;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.Rank;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.components.clans.data.ClanMember;
import me.mykindos.betterpvp.core.config.ExtendedYamlConfiguration;
import me.mykindos.betterpvp.core.utilities.search.SearchEngineBase;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("/clan protection")
class BrigadierProtectionCommandTest {

    private final ClientManager clientManager = mock(ClientManager.class);
    private final ClanManager clanManager = mock(ClanManager.class);

    @SuppressWarnings("unchecked")
    private CommandSourceStack member(ClanMember.MemberRank memberRank) {
        final SearchEngineBase<Client> search = mock(SearchEngineBase.class);
        when(clientManager.search()).thenReturn(search);
        final Client client = mock(Client.class);
        when(client.hasRank(Rank.PLAYER)).thenReturn(true);
        final UUID id = UUID.randomUUID();
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(search.online(player)).thenReturn(client);
        final Clan clan = mock(Clan.class);
        when(clan.getMember(id)).thenReturn(new ClanMember(id, memberRank, "Bob"));
        when(clanManager.getClanByPlayer(player)).thenReturn(Optional.of(clan));
        final CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(player);
        return source;
    }

    @Test
    @DisplayName("CLANS-42 AC3 a clan admin can remove protection, a member below admin cannot")
    void needsClanAdmin() {
        final ExtendedYamlConfiguration config = new ExtendedYamlConfiguration();
        config.set("protection.requiredRank", Rank.PLAYER.name());
        final BrigadierProtectionCommand command = new BrigadierProtectionCommand(clientManager, clanManager);
        command.setConfig(config);

        assertTrue(command.requirement(member(ClanMember.MemberRank.ADMIN)));
        assertFalse(command.requirement(member(ClanMember.MemberRank.MEMBER)));
    }
}
