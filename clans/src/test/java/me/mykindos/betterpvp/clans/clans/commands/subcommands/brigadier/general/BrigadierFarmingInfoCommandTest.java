package me.mykindos.betterpvp.clans.clans.commands.subcommands.brigadier.general;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.clans.Clans;
import me.mykindos.betterpvp.clans.clans.ClanManager;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.Rank;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.config.ExtendedYamlConfiguration;
import me.mykindos.betterpvp.core.utilities.search.SearchEngineBase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentIteratorType;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("/clan farminginfo")
class BrigadierFarmingInfoCommandTest {

    private final ClientManager clientManager = mock(ClientManager.class);
    private final ClanManager clanManager = mock(ClanManager.class);

    private BrigadierFarmingInfoCommand command(int baseY, boolean offlineGrowing) {
        final ExtendedYamlConfiguration config = new ExtendedYamlConfiguration();
        config.set("clans.farming.baseY", baseY);
        config.set("clans.farming.allowOfflineGrowing", offlineGrowing);
        final Clans clans = mock(Clans.class);
        when(clans.getConfig()).thenReturn(config);
        return new BrigadierFarmingInfoCommand(clientManager, clanManager, clans);
    }

    @Test
    @DisplayName("CLANS-42 AC2 farminglevels runs the same command")
    void hasFarmingLevelsAlias() {
        assertTrue(command(60, false).getAliases().contains("farminglevels"));
    }

    @Test
    @DisplayName("CLANS-42 AC2 a player needs a clan to use it")
    @SuppressWarnings("unchecked")
    void needsAClan() {
        final SearchEngineBase<Client> search = mock(SearchEngineBase.class);
        when(clientManager.search()).thenReturn(search);
        final Client client = mock(Client.class);
        when(client.hasRank(Rank.PLAYER)).thenReturn(true);
        final Player player = mock(Player.class);
        when(search.online(player)).thenReturn(client);
        when(clanManager.getClanByPlayer(player)).thenReturn(Optional.empty());
        final CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(player);
        final ExtendedYamlConfiguration config = new ExtendedYamlConfiguration();
        config.set("farminginfo.requiredRank", Rank.PLAYER.name());

        final BrigadierFarmingInfoCommand command = command(60, false);
        command.setConfig(config);

        assertFalse(command.requirement(source));
    }

    private static List<String> keys(Component message) {
        return StreamSupport.stream(message.spliterator(ComponentIteratorType.DEPTH_FIRST), false)
                .filter(TranslatableComponent.class::isInstance)
                .map(part -> ((TranslatableComponent) part).key())
                .toList();
    }

    @Test
    @DisplayName("CLANS-42 AC2 the message shows the farmable Y range and whether the clan must be online")
    void messageShowsRangeAndOnlineRule() {
        final Component message = command(60, false).farmingInfo(40);

        final TranslatableComponent range = (TranslatableComponent) StreamSupport.stream(message.spliterator(ComponentIteratorType.DEPTH_FIRST), false)
                .filter(part -> part instanceof TranslatableComponent translatable && translatable.key().equals("clans.command.clan.farming.y-range"))
                .findFirst().orElseThrow();
        assertEquals(List.of(Component.text(60), Component.text(40)), range.arguments().stream().map(TranslationArgument::asComponent).toList());
        assertTrue(keys(message).contains("clans.command.clan.farming.yes"), "crops need the clan online");
        assertTrue(keys(command(60, true).farmingInfo(40)).contains("clans.command.clan.farming.no"), "crops grow offline");
    }
}
