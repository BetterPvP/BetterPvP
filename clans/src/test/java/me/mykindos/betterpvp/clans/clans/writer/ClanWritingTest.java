package me.mykindos.betterpvp.clans.clans.writer;

import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.spi.DefaultBindingScopingVisitor;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.spi.LinkedKeyBinding;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.repository.ClanRepository;
import me.mykindos.betterpvp.core.client.gamer.Gamer;
import me.mykindos.betterpvp.core.client.gamer.properties.GamerProperty;
import me.mykindos.betterpvp.core.components.clans.data.ClanEnemy;
import me.mykindos.betterpvp.core.framework.economy.ledger.InProcessLedger;
import me.mykindos.betterpvp.core.framework.economy.ledger.LedgerParty;
import me.mykindos.betterpvp.core.framework.role.ServerRole;
import me.mykindos.betterpvp.ledger.LedgerPosting;
import me.mykindos.betterpvp.ledger.LedgerReceipt;
import me.mykindos.betterpvp.ledger.LedgerStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClanWritingTest {

    @Test
    @DisplayName("Role all writes clans and their coins on the server itself")
    void roleAllWritesInProcess() {
        final List<Element> elements = Elements.getElements(new ClanWritingModule(ServerRole.ALL));

        assertEquals(LocalClanWriter.class, target(elements, ClanWriter.class));
        assertEquals(LocalClanBank.class, target(elements, ClanBank.class));
    }

    @Test
    @DisplayName("Every other role writes through the services and listens from the start")
    void networkedRolesWriteThroughTheServices() {
        for (ServerRole role : List.of(ServerRole.RESOURCE, ServerRole.CAMP, ServerRole.DUNGEON, ServerRole.EVENT,
                ServerRole.HUB)) {
            final List<Element> elements = Elements.getElements(new ClanWritingModule(role));

            assertEquals(ServiceClanWriter.class, target(elements, ClanWriter.class), role.getId());
            assertEquals(ServiceClanBank.class, target(elements, ClanBank.class), role.getId());
            assertTrue(eager(elements, ClanWriter.class) && eager(elements, ClanBank.class), role.getId());
        }
    }

    @Test
    @DisplayName("The in-process writer stores every clan record in the clan tables")
    @SuppressWarnings("unchecked")
    void theInProcessWriterStoresEveryRecord() {
        final ClanRepository repository = mock(ClanRepository.class);
        final LocalClanWriter writer = new LocalClanWriter(repository, mock(Provider.class));
        final Clan clan = clan(1L);
        final ClanEnemy enemy = new ClanEnemy(clan(2L), 40);

        writer.saveProperty(clan, "ENERGY", 1200);
        writer.flushProperties(false);
        writer.setCore(clan);
        writer.setBanner(clan);
        writer.saveVault(clan);
        writer.saveMailbox(clan);
        writer.claim(clan, "world/ 1/ 2");
        writer.unclaim(clan, "world/ 1/ 2");
        writer.setDominance(clan, enemy);

        verify(repository).saveProperty(clan, "ENERGY", 1200);
        verify(repository).processPropertyUpdates(false);
        verify(repository).updateClanCore(clan);
        verify(repository).updateClanBanner(clan);
        verify(repository).updateClanVault(clan);
        verify(repository).updateClanMailbox(clan);
        verify(repository).saveClanTerritory(clan, "world/ 1/ 2");
        verify(repository).deleteClanTerritory(clan, "world/ 1/ 2");
        verify(repository).updateDominance(clan, enemy);
    }

    @Test
    @DisplayName("The in-process bank moves a deposit at once and keeps the clan's balance in its properties")
    void theInProcessBankMovesADepositAtOnce() {
        final List<LedgerPosting> journal = new ArrayList<>();
        final LocalClanBank bank = new LocalClanBank(new InProcessLedger(journal::add));
        final Clan clan = clan(3L);
        when(clan.getBalance()).thenReturn(500);
        final Gamer gamer = gamer(200);

        final var answer = bank.move(LedgerParty.of(gamer), bank.account(clan), 150, "clan.bank.deposit");

        assertTrue(answer.isDone());
        assertTrue(answer.join().isPosted());
        verify(clan).setBalance(650);
        verify(gamer).saveProperty(GamerProperty.BALANCE, 50);
        assertEquals(1, journal.size());
    }

    @Test
    @DisplayName("The in-process bank refuses to withdraw more than the clan holds")
    void theInProcessBankRefusesAnOverdraw() {
        final LocalClanBank bank = new LocalClanBank(new InProcessLedger(posting -> { }));
        final Clan clan = clan(4L);
        when(clan.getBalance()).thenReturn(100);

        final LedgerReceipt receipt = bank.move(bank.account(clan), LedgerParty.of(gamer(0)), 101, "clan.bank.withdraw")
                .join();

        assertEquals(LedgerStatus.INSUFFICIENT_FUNDS, receipt.getStatus());
        verify(clan, never()).setBalance(anyInt());
    }

    private static Class<?> target(List<Element> elements, Class<?> type) {
        return binding(elements, type) instanceof LinkedKeyBinding<?> linked
                ? linked.getLinkedKey().getTypeLiteral().getRawType()
                : null;
    }

    private static boolean eager(List<Element> elements, Class<?> type) {
        final Binding<?> binding = binding(elements, type);
        return binding != null && binding.acceptScopingVisitor(new DefaultBindingScopingVisitor<Boolean>() {
            @Override
            public Boolean visitEagerSingleton() {
                return true;
            }

            @Override
            protected Boolean visitOther() {
                return false;
            }
        });
    }

    private static Binding<?> binding(List<Element> elements, Class<?> type) {
        final Key<?> key = Key.get(type);
        for (Element element : elements) {
            if (element instanceof Binding<?> binding && binding.getKey().equals(key)) {
                return binding;
            }
        }
        return fail("No binding for " + type.getSimpleName());
    }

    private static Clan clan(long id) {
        final Clan clan = mock(Clan.class);
        when(clan.getId()).thenReturn(id);
        return clan;
    }

    private static Gamer gamer(int balance) {
        final Gamer gamer = mock(Gamer.class);
        when(gamer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(gamer.getBalance()).thenReturn(balance);
        return gamer;
    }
}
