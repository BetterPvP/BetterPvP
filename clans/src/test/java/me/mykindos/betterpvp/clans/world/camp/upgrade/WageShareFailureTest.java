package me.mykindos.betterpvp.clans.world.camp.upgrade;

import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.ClanManager;
import me.mykindos.betterpvp.clans.world.camp.Camp;
import me.mykindos.betterpvp.clans.world.camp.CampConfig;
import me.mykindos.betterpvp.clans.world.camp.CampStore;
import me.mykindos.betterpvp.clans.world.camp.Camps;
import me.mykindos.betterpvp.clans.world.camp.structure.CampUpgrades;
import me.mykindos.betterpvp.core.client.gamer.Gamer;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.components.clans.data.ClanMember;
import me.mykindos.betterpvp.core.framework.economy.ledger.Ledger;
import me.mykindos.betterpvp.core.world.settler.wage.CoinAccount;
import me.mykindos.betterpvp.core.world.site.SiteKey;
import me.mykindos.betterpvp.ledger.LedgerReceipt;
import me.mykindos.betterpvp.ledger.LedgerStatus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WageShareFailureTest {

    private static final SiteKey CAMP = Camps.keyFor(11);

    @Test
    @DisplayName("a wage share the ledger refuses is logged and the member is told")
    void aRefusedShareIsReported() {
        final UUID member = UUID.randomUUID();
        final Player player = mock(Player.class);
        when(player.getName()).thenReturn("Steve");
        final Camp camp = new Camp();
        camp.getWageContributors().add(member);
        final CampStore store = mock(CampStore.class);
        when(store.cached(CAMP.getOwnerId())).thenReturn(Optional.of(camp));
        final CampUpgrades upgrades = mock(CampUpgrades.class);
        when(upgrades.has(any(), any(), any())).thenReturn(true);
        final Clan clan = mock(Clan.class);
        when(clan.getMemberByUUID(member)).thenReturn(Optional.of(mock(ClanMember.class)));
        final ClanManager clanManager = mock(ClanManager.class);
        when(clanManager.getClanById(CAMP.getOwnerId())).thenReturn(Optional.of(clan));
        final Gamer gamer = mock(Gamer.class);
        when(gamer.getBalance()).thenReturn(1_000);
        final ClientManager clients = mock(ClientManager.class, RETURNS_DEEP_STUBS);
        when(clients.search().online(player).getGamer()).thenReturn(gamer);
        final Ledger ledger = mock(Ledger.class);
        when(ledger.move(any(), any(), anyLong(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(LedgerReceipt.of(LedgerStatus.UNAVAILABLE)));
        final WagePolicy policy = new WagePolicy(upgrades, store, mock(CampConfig.class), clanManager, clients, ledger);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(member)).thenReturn(player);
            policy.backing(mock(CoinAccount.class)).withdraw(CAMP, 100);
        }

        final ArgumentCaptor<Component> told = ArgumentCaptor.forClass(Component.class);
        verify(player).sendMessage(told.capture());
        assertTrue(mentions(told.getValue(), "clans.camp.upgrade.wage_policy.share_failed"),
                "the member was not told their share failed");
        assertTrue(camp.getWageContributions().isEmpty(), "a share that never landed was counted as paid");
    }

    private static boolean mentions(Component component, String key) {
        if (component instanceof TranslatableComponent translatable && translatable.key().equals(key)) {
            return true;
        }
        return component.children().stream().anyMatch(child -> mentions(child, key));
    }
}
