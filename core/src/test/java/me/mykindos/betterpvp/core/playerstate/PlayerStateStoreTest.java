package me.mykindos.betterpvp.core.playerstate;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The store against a real Postgres. Skipped when Docker is not running. */
class PlayerStateStoreTest {

    private ServerMock server;
    private PlayerStateFixture fixture;
    private PlayerStateStore store;
    private PlayerStateCapture capture;

    @BeforeAll
    static void startDatabase() {
        PlayerStateFixture.startPostgres();
    }

    @AfterAll
    static void stopDatabase() {
        PlayerStateFixture.stopPostgres();
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        fixture = new PlayerStateFixture();
        store = fixture.getStore();
        capture = fixture.getCapture();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void saveThenLoadKeepsEveryCustomItemIdentical() {
        final PlayerMock player = server.addPlayer();
        final ItemStack sword = fixture.blade(17);
        assertNotNull(fixture.uuid(sword));
        final ItemStack helmet = fixture.blade(3);
        final ItemStack offhand = fixture.blade(0);
        final ItemStack stored = fixture.blade(40);
        final ItemStack dirt = new ItemStack(Material.DIRT, 23);
        player.getInventory().setItem(0, sword);
        player.getInventory().setItem(5, dirt);
        player.getInventory().setItem(39, helmet);
        player.getInventory().setItem(40, offhand);
        player.getEnderChest().setItem(12, stored);
        player.setFoodLevel(7);
        player.setLevel(31);
        player.setGameMode(GameMode.ADVENTURE);

        assertTrue(store.save(capture.capture(player, 1)).join());

        final PlayerMock other = server.addPlayer();
        final PlayerState loaded = store.load(player.getUniqueId()).join().orElseThrow();
        capture.apply(other, loaded);

        fixture.assertSameItem(sword, other.getInventory().getItem(0));
        fixture.assertSameItem(dirt, other.getInventory().getItem(5));
        fixture.assertSameItem(helmet, other.getInventory().getItem(39));
        fixture.assertSameItem(offhand, other.getInventory().getItem(40));
        fixture.assertSameItem(stored, other.getEnderChest().getItem(12));
        assertEquals(1, loaded.getVersion());
        assertEquals(7, other.getFoodLevel());
        assertEquals(31, other.getLevel());
        assertEquals(GameMode.ADVENTURE, other.getGameMode());
    }

    @Test
    void staleSaveIsRejected() {
        final PlayerMock player = server.addPlayer();
        player.setLevel(1);
        assertTrue(store.save(capture.capture(player, 1)).join());

        player.setLevel(2);
        assertTrue(store.save(capture.capture(player, 2)).join());

        player.setLevel(99);
        assertFalse(store.save(capture.capture(player, 1)).join());
        assertFalse(store.save(capture.capture(player, 2)).join());

        final PlayerState loaded = store.load(player.getUniqueId()).join().orElseThrow();
        assertEquals(2, loaded.getVersion());
        assertEquals(2, loaded.getLevel());
    }

    @Test
    void fenceRejectsEverySaveBelowIt() {
        final PlayerMock player = server.addPlayer();
        player.setLevel(1);
        assertTrue(store.save(capture.capture(player, 1)).join());

        store.fence(player.getUniqueId(), 50).join();
        player.setLevel(2);
        assertFalse(store.save(capture.capture(player, 50)).join());
        assertTrue(store.save(capture.capture(player, 51)).join());
        assertEquals(2, store.load(player.getUniqueId()).join().orElseThrow().getLevel());
    }

    @Test
    void unsavedPlayerLoadsNothing() {
        assertTrue(store.load(UUID.randomUUID()).join().isEmpty());
    }
}
