package me.mykindos.betterpvp.clans.world.camp.hall;

import me.mykindos.betterpvp.clans.clans.ClanManager;
import me.mykindos.betterpvp.clans.world.camp.CampPermissions;
import me.mykindos.betterpvp.clans.world.camp.CampStore;
import me.mykindos.betterpvp.clans.world.camp.Camps;
import me.mykindos.betterpvp.clans.world.camp.menu.StructureMenus;
import me.mykindos.betterpvp.clans.world.camp.settler.CampWageFund;
import me.mykindos.betterpvp.clans.world.camp.settler.FarmWorkplace;
import me.mykindos.betterpvp.clans.world.camp.settler.menu.CrewMenus;
import me.mykindos.betterpvp.clans.world.camp.settler.menu.SettlerCards;
import me.mykindos.betterpvp.clans.world.camp.settler.prosperity.CampProsperity;
import me.mykindos.betterpvp.clans.world.camp.settler.recruit.CampRecruitment;
import me.mykindos.betterpvp.clans.world.camp.settler.recruit.RecruitConfig;
import me.mykindos.betterpvp.clans.world.camp.structure.CampStructures;
import me.mykindos.betterpvp.clans.world.camp.structure.CampUpgrades;
import me.mykindos.betterpvp.clans.world.camp.upgrade.GuestQuarters;
import me.mykindos.betterpvp.clans.world.camp.upgrade.Harbourmaster;
import me.mykindos.betterpvp.clans.world.camp.upgrade.WagePolicy;
import me.mykindos.betterpvp.core.client.gamer.Gamer;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.framework.economy.ledger.Ledger;
import me.mykindos.betterpvp.core.world.construction.ConstructionChecks;
import me.mykindos.betterpvp.core.world.construction.ConstructionService;
import me.mykindos.betterpvp.core.world.construction.StructureCatalogue;
import me.mykindos.betterpvp.core.world.construction.StructureStatusTracker;
import me.mykindos.betterpvp.core.world.construction.blueprint.BlueprintSessions;
import me.mykindos.betterpvp.core.world.settler.ProfessionRegistry;
import me.mykindos.betterpvp.core.world.settler.SettlerAction;
import me.mykindos.betterpvp.core.world.settler.SettlerService;
import me.mykindos.betterpvp.core.world.settler.wage.Payroll;
import me.mykindos.betterpvp.core.world.site.SiteKey;
import me.mykindos.betterpvp.ledger.LedgerReceipt;
import me.mykindos.betterpvp.ledger.LedgerStatus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HallWagePaymentTest {

    private static final SiteKey CAMP = Camps.keyFor(9);

    private final ClientManager clients = mock(ClientManager.class, RETURNS_DEEP_STUBS);
    private final CampPermissions permissions = mock(CampPermissions.class);
    private final CampWageFund wageFund = mock(CampWageFund.class);
    private final Ledger ledger = mock(Ledger.class);
    private final Player player = mock(Player.class);

    @Test
    @DisplayName("a wage payment the ledger finds the player cannot cover tells them so")
    void aPaymentThePlayerCannotCoverSaysSo() {
        final Gamer gamer = mock(Gamer.class);
        when(gamer.getBalance()).thenReturn(1_000);
        when(clients.search().online(player).getGamer()).thenReturn(gamer);
        when(permissions.allows(player, CAMP.getOwnerId(), SettlerAction.PAY)).thenReturn(true);
        // This server's copy of the balance is behind what another server already spent
        when(ledger.move(any(), any(), anyLong(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(LedgerReceipt.of(LedgerStatus.INSUFFICIENT_FUNDS)));

        menus().pay(player, CAMP, 500);

        final ArgumentCaptor<Component> told = ArgumentCaptor.forClass(Component.class);
        verify(player).sendMessage(told.capture());
        assertEquals("clans.camp.hall.wages.cannot_afford", ((TranslatableComponent) told.getValue()).key());
        verify(wageFund, never()).deposit(any(), anyLong());
    }

    private HallMenus menus() {
        return new HallMenus(mock(ClanManager.class), clients, permissions, mock(CampStructures.class),
                mock(ConstructionService.class), mock(ConstructionChecks.class), mock(StructureStatusTracker.class),
                mock(StructureCatalogue.class), mock(BlueprintSessions.class), mock(CrewMenus.class),
                mock(SettlerService.class), mock(Payroll.class), wageFund, mock(SettlerCards.class),
                mock(ProfessionRegistry.class), mock(CampRecruitment.class), mock(RecruitConfig.class),
                mock(FarmWorkplace.class), mock(CampProsperity.class), mock(CampStore.class),
                mock(CampUpgrades.class), mock(StructureMenus.class), mock(Harbourmaster.class),
                mock(GuestQuarters.class), mock(WagePolicy.class), ledger);
    }
}
