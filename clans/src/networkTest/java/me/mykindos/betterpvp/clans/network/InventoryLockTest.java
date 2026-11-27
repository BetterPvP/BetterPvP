package me.mykindos.betterpvp.clans.network;

import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.core.storage.ClanInventories;
import me.mykindos.betterpvp.clans.clans.core.storage.LockAnswer;
import me.mykindos.betterpvp.clans.clans.core.storage.NatsClanLocks;
import me.mykindos.betterpvp.clans.clans.core.storage.ServiceClanInventories;
import me.mykindos.betterpvp.clans.clans.core.vault.ClanVault;
import me.mykindos.betterpvp.clans.protocol.ClanMutation;
import me.mykindos.betterpvp.clans.protocol.ClanOp;
import me.mykindos.betterpvp.core.network.Network;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("A clan vault opened from two servers")
class InventoryLockTest {

    private static final Duration LEASE = Duration.ofSeconds(2);

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
    @DisplayName("opens on one server at a time, and the next one sees what the last one saved")
    void opensOnOneServerAtATime() throws Exception {
        final long id = 6000;
        final ClanServer first = new ClanServer(network, "vault-1");
        final ClanServer second = new ClanServer(network, "vault-2");
        create(first, id);
        final Clan here = vault(first, id, "saved by Alice");
        final Clan there = vault(second, id, "saved by Bob");
        final ClanInventories firstInventories = inventories(first);
        final ClanInventories secondInventories = inventories(second);

        assertTrue(firstInventories.open(here, ClanInventories.Inventory.VAULT, "Alice").join().isTaken());
        assertEquals(LockAnswer.held("Alice"),
                secondInventories.open(there, ClanInventories.Inventory.VAULT, "Bob").join());

        firstInventories.close(here, ClanInventories.Inventory.VAULT).join();
        Network.await("the vault to be free", () ->
                secondInventories.open(there, ClanInventories.Inventory.VAULT, "Bob").join().isTaken());
        verify(there.getCore().getVault()).read("saved by Alice");
    }

    @Test
    @DisplayName("frees itself when the server holding it dies")
    void freesItselfWhenTheHolderDies() throws Exception {
        final long id = 6001;
        final ClanServer first = new ClanServer(network, "vault-3");
        final ClanServer second = new ClanServer(network, "vault-4");
        create(first, id);
        final Clan here = vault(first, id, "");
        final Clan there = vault(second, id, "");
        final ClanInventories secondInventories = inventories(second);
        assertTrue(inventories(first).open(here, ClanInventories.Inventory.VAULT, "Alice").join().isTaken());

        first.server.crash();

        assertEquals(LockAnswer.held("Alice"),
                secondInventories.open(there, ClanInventories.Inventory.VAULT, "Bob").join());
        Network.await("the dead server's lock to expire", () ->
                secondInventories.open(there, ClanInventories.Inventory.VAULT, "Bob").join().isTaken());
    }

    private static void create(@NotNull ClanServer server, long id) {
        assertTrue(server.server.mutate(ClanMutation.builder().op(ClanOp.CREATE).clan(id).name("Vault" + id).realm(1)
                .member(UUID.randomUUID()).build()).isApplied());
    }

    private static @NotNull Clan vault(@NotNull ClanServer server, long id, @NotNull String contents) {
        final Clan clan = server.hold(id, 0);
        final ClanVault vault = mock(ClanVault.class);
        when(vault.serialize()).thenReturn(contents);
        // Reported as open, so the change feed leaves it alone and only opening it reads what was stored
        when(vault.isLocked()).thenReturn(true);
        when(clan.getCore().getVault()).thenReturn(vault);
        return clan;
    }

    private static @NotNull ClanInventories inventories(@NotNull ClanServer server) {
        final NatsClanLocks locks = Objects.requireNonNull(
                NatsClanLocks.open(server.server.getConnection(), network.getPrefix(), 1, LEASE));
        return new ServiceClanInventories(locks, server.writer, server.repository, Runnable::run);
    }
}
