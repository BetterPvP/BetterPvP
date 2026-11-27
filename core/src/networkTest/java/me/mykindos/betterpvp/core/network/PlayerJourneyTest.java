package me.mykindos.betterpvp.core.network;

import me.mykindos.betterpvp.core.framework.economy.ledger.LedgerBalance;
import me.mykindos.betterpvp.core.framework.economy.ledger.LedgerParty;
import me.mykindos.betterpvp.core.framework.economy.ledger.Movement;
import me.mykindos.betterpvp.core.playerstate.PlayerStateFixture;
import me.mykindos.betterpvp.ledger.LedgerAccounts;
import me.mykindos.betterpvp.ledger.LedgerStatus;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("A player travelling across servers")
class PlayerJourneyTest {

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
    @DisplayName("goes hub, camp, dungeon and back to the hub with every item, level and coin")
    void hubCampDungeonHub() throws Exception {
        network.offer("camp", "camp-a");
        network.offer("dungeon", "dungeon-1");
        final FakeServer hub = network.server("hub-1");
        final FakeServer camp = network.server("camp-a");
        final FakeServer dungeon = network.server("dungeon-1");
        final PlayerStateFixture items = network.getFixture();
        final UUID id = UUID.randomUUID();
        final String account = LedgerAccounts.player(id);

        PlayerMock player = hub.arrive(id);
        final ItemStack sword = items.blade(4);
        player.getInventory().setItem(0, sword);
        player.setLevel(5);
        final AtomicLong coins = new AtomicLong(100);
        assertTrue(hub.getLedger().open(LedgerParty.held(account, balance(coins))).join().isPosted());

        assertEquals("camp-a", hub.hostOf("camp", 7, true));
        hub.leave(player);
        player = camp.arrive(id);
        camp.advertise("camp", 7, 1);
        items.assertSameItem(sword, player.getInventory().getItem(0));
        assertEquals(5, player.getLevel());
        final ItemStack stored = items.blade(9);
        player.getEnderChest().setItem(3, stored);

        assertEquals("dungeon-1", camp.hostOf("dungeon", 0, false));
        camp.leave(player);
        player = dungeon.arrive(id);
        items.assertSameItem(sword, player.getInventory().getItem(0));
        items.assertSameItem(stored, player.getEnderChest().getItem(3));
        final ItemStack loot = items.blade(1);
        player.getInventory().setItem(1, loot);
        player.setLevel(12);
        final Movement reward = new Movement(LedgerParty.system(LedgerAccounts.LOOT), LedgerParty.held(account, balance(coins)),
                50, "dungeon reward");
        assertTrue(dungeon.getLedger().post(List.of(reward), "reward:" + id).join().isPosted());
        assertEquals(150, coins.get());

        dungeon.leave(player);
        player = hub.arrive(id);

        items.assertSameItem(sword, player.getInventory().getItem(0));
        items.assertSameItem(loot, player.getInventory().getItem(1));
        items.assertSameItem(stored, player.getEnderChest().getItem(3));
        assertEquals(12, player.getLevel());
        assertEquals(3, distinctItems(items, player), "an item was lost or copied on the way");
        assertEquals(Set.of(id), hub.getHandoff().owned());
        assertEquals(Set.of(), camp.getHandoff().owned());
        assertEquals(Set.of(), dungeon.getHandoff().owned());

        final Movement spendAll = new Movement(LedgerParty.held(account, balance(coins)), LedgerParty.system(LedgerAccounts.SHOP),
                150, "spend");
        assertTrue(hub.getLedger().post(List.of(spendAll), null).join().isPosted(), "the reward did not arrive");
        final Movement oneMore = new Movement(LedgerParty.held(account, balance(coins)), LedgerParty.system(LedgerAccounts.SHOP),
                1, "spend");
        assertEquals(LedgerStatus.INSUFFICIENT_FUNDS, hub.getLedger().post(List.of(oneMore), null).join().getStatus());
    }

    private static int distinctItems(PlayerStateFixture items, PlayerMock player) {
        final List<ItemStack> all = new ArrayList<>();
        all.addAll(Arrays.asList(player.getInventory().getContents()));
        all.addAll(Arrays.asList(player.getEnderChest().getContents()));
        final List<UUID> ids = all.stream().filter(item -> item != null && !item.isEmpty()).map(items::uuid)
                .filter(Objects::nonNull).toList();
        assertEquals(new HashSet<>(ids).size(), ids.size(), "two items share one id");
        return ids.size();
    }

    static LedgerBalance balance(AtomicLong holder) {
        return new LedgerBalance() {
            @Override
            public long get() {
                return holder.get();
            }

            @Override
            public void set(long balance) {
                holder.set(balance);
            }
        };
    }
}
