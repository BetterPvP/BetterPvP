package me.mykindos.betterpvp.clans.network;

import me.mykindos.betterpvp.clans.auctionhouse.ClansAuctionDeliveryService;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.core.storage.ClanInventories;
import me.mykindos.betterpvp.clans.world.camp.settler.recruit.CampCoins;
import me.mykindos.betterpvp.core.client.gamer.Gamer;
import me.mykindos.betterpvp.core.client.gamer.properties.GamerProperty;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.framework.economy.ledger.LedgerParty;
import me.mykindos.betterpvp.core.framework.economy.ledger.ServiceLedger;
import me.mykindos.betterpvp.core.item.ItemFactory;
import me.mykindos.betterpvp.core.network.Network;
import me.mykindos.betterpvp.ledger.LedgerAccounts;
import me.mykindos.betterpvp.ledger.LedgerReceipt;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Clan and camp coins across servers and outages")
class ClanCoinsTest {

    private static final Duration OUTAGE = Duration.ofSeconds(4);

    private static Network network;

    @BeforeAll
    static void start() throws Exception {
        MockBukkit.mock();
        network = new Network();
    }

    @AfterAll
    static void stop() throws Exception {
        if (network != null) {
            network.close();
        }
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a server that starts later takes each clan's balance from the ledger")
    void aLaterServerTakesTheLedgersBalance() throws Exception {
        final long id = 7000;
        final ClanServer first = new ClanServer(network, "bank-1");
        final Clan here = first.hold(id, 500);
        assertTrue(first.bank.move(LedgerParty.system(LedgerAccounts.CAMP), first.bank.account(here), 200,
                "deposit").join().isPosted());
        assertEquals(700, here.getBalance());

        // The balance this server loaded is the one stored before the ledger kept it
        final ClanServer restarted = new ClanServer(network, "bank-2");
        final Clan there = restarted.hold(id, 500);
        restarted.bank.sync(List.of(there));

        Network.await("the ledger's balance to arrive", () -> there.getBalance() == 700);
    }

    @Test
    @DisplayName("a camp purchase made on two servers at once is paid for once")
    void aPurchaseIsPaidForOnce() throws Exception {
        final UUID player = UUID.randomUUID();
        final CampCoins first = coins(new ClanServer(network, "camp-1"), player, 100);
        final CampCoins second = coins(new ClanServer(network, "camp-2"), player, 100);

        final List<CompletableFuture<Boolean>> purchases = List.of(
                first.take(mock(Player.class), 100), second.take(mock(Player.class), 100));

        assertEquals(1, purchases.stream().filter(CompletableFuture::join).count());
    }

    @Test
    @DisplayName("a camp purchase the ledger cannot answer is refused, and anything it took is handed back")
    void anUnansweredPurchaseLosesNothing() throws Exception {
        final UUID player = UUID.randomUUID();
        final ClanServer server = new ClanServer(network, "camp-3");
        final CampCoins coins = coins(server, player, 100);

        network.stopPostgres();
        final CompletableFuture<Boolean> purchase = coins.take(mock(Player.class), 60);
        assertFalse(purchase.join(), "a purchase the ledger did not confirm went through");
        Thread.sleep(OUTAGE.toMillis());
        network.startPostgres();

        Network.await("the purchase to be settled", () -> balance(server, player) == 100);
        Thread.sleep(2_000);
        assertEquals(100, balance(server, player));
    }

    @Test
    @DisplayName("auction proceeds are only delivered once the ledger lands them, through an outage")
    void proceedsWaitForTheLedger() throws Exception {
        final long id = 7001;
        final UUID seller = UUID.randomUUID();
        final ClanServer server = new ClanServer(network, "auction-1");
        final Clan clan = server.hold(id, 0);
        when(server.clanManager.getClanByPlayer(seller)).thenReturn(Optional.of(clan));
        final ClansAuctionDeliveryService delivery = new ClansAuctionDeliveryService(server.clanManager,
                mock(ItemFactory.class), mock(ClanInventories.class), server.bank);
        server.bank.sync(List.of(clan));
        Network.await("the clan's account to open", () -> server.bank.move(LedgerParty.system(LedgerAccounts.CAMP),
                server.bank.account(clan), 1, "open").join().isPosted());

        network.stopPostgres();
        final CompletableFuture<Boolean> proceeds = delivery.deliverCurrency(seller, 300, "auction-proceeds:1");
        Thread.sleep(OUTAGE.toMillis());
        assertFalse(proceeds.isDone(), "proceeds counted as delivered before the ledger landed them");
        network.startPostgres();

        assertTrue(proceeds.join());
        assertEquals(301, clan.getBalance());
        assertTrue(delivery.deliverCurrency(seller, 300, "auction-proceeds:1").join());
        assertEquals(301, clan.getBalance(), "the same proceeds landed twice");
    }

    private static @NotNull CampCoins coins(@NotNull ClanServer server, @NotNull UUID player, int balance) {
        final AtomicInteger coins = new AtomicInteger(balance);
        final Gamer gamer = mock(Gamer.class);
        when(gamer.getUniqueId()).thenReturn(player);
        when(gamer.getBalance()).thenAnswer(ask -> coins.get());
        doAnswer(ask -> {
            coins.set(((Number) ask.getArgument(1)).intValue());
            return null;
        }).when(gamer).saveProperty(eq(GamerProperty.BALANCE), any());
        final ClientManager clients = mock(ClientManager.class, RETURNS_DEEP_STUBS);
        when(clients.search().online(any(Player.class)).getGamer()).thenReturn(gamer);
        assertTrue(server.server.getLedger().open(LedgerParty.of(gamer)).join().isPosted());
        return new CampCoins(clients, server.server.getLedger());
    }

    /** The player's balance as the ledger keeps it. */
    private static long balance(@NotNull ClanServer server, @NotNull UUID player) {
        final ServiceLedger ledger = server.server.getLedger();
        final AtomicInteger seen = new AtomicInteger();
        final LedgerReceipt receipt = ledger.open(LedgerParty.held(LedgerAccounts.player(player),
                ClanServer.balance(seen))).join();
        return receipt.isPosted() ? seen.get() : -1;
    }
}
