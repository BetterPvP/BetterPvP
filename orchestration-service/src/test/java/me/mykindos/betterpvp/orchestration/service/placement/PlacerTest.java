package me.mykindos.betterpvp.orchestration.service.placement;

import me.mykindos.betterpvp.network.protocol.PlacementMessages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Deciding which server hosts a site")
class PlacerTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final MemoryLeases leases = new MemoryLeases();
    private final FakePool pool = new FakePool();
    private final Placer placer = new Placer(leases, pool, site -> "camp", Duration.ofSeconds(60), now::get);

    /** Leases in a map, with the same write-against-what-you-read rule as the real store. */
    private static class MemoryLeases implements LeaseStore {
        final Map<String, Lease> stored = new HashMap<>();
        final Set<String> advertised = new HashSet<>();
        long revision;

        @Override
        public Optional<Lease> read(String siteId, long ownerId) {
            return Optional.ofNullable(stored.get(siteId + "." + ownerId));
        }

        @Override
        public boolean claim(String siteId, long ownerId, String server, long placed, Lease replacing) {
            final String key = siteId + "." + ownerId;
            final Lease current = stored.get(key);
            if (current != null && (replacing == null || current.getRevision() != replacing.getRevision())) {
                return false;
            }
            stored.put(key, new Lease(server, placed, ++revision));
            return true;
        }

        @Override
        public boolean release(String siteId, long ownerId, Lease current) {
            final String key = siteId + "." + ownerId;
            final Lease stored = this.stored.get(key);
            if (stored == null || stored.getRevision() != current.getRevision()) {
                return false;
            }
            this.stored.remove(key);
            return true;
        }

        @Override
        public boolean isAdvertised(String siteId, long ownerId, String server) {
            return advertised.contains(siteId + "." + ownerId + "@" + server);
        }
    }

    private static class FakePool implements ServerPool {
        List<Slots> servers = new ArrayList<>();
        final List<String> allocations = new ArrayList<>();
        String nextAllocation = "camp-new";

        @Override
        public List<Slots> servers(String role) {
            return servers;
        }

        @Override
        public Optional<String> allocate(String role) {
            if (nextAllocation == null) {
                return Optional.empty();
            }
            allocations.add(nextAllocation);
            return Optional.of(nextAllocation);
        }
    }

    @Test
    @DisplayName("a camp with no lease goes to the fullest server that still has room")
    void fillsTheFullestServerWithRoom() {
        pool.servers = List.of(new ServerPool.Slots("camp-a", 5), new ServerPool.Slots("camp-b", 1),
                new ServerPool.Slots("camp-c", 0));

        final PlacementDecision decision = placer.place("camp", 42, true);

        assertEquals(PlacementMessages.PLACED, decision.getOutcome());
        assertEquals("camp-b", decision.getServer());
        assertEquals("camp-b", leases.stored.get("camp.42").getServer());
    }

    @Test
    @DisplayName("a live lease answers with its host")
    void liveLeaseIsHosted() {
        pool.servers = List.of(new ServerPool.Slots("camp-a", 5));
        placer.place("camp", 42, true);
        pool.servers = List.of(new ServerPool.Slots("camp-b", 5));

        final PlacementDecision again = placer.place("camp", 42, true);

        assertEquals(PlacementMessages.HOSTED, again.getOutcome());
        assertEquals("camp-a", again.getServer());
    }

    @Test
    @DisplayName("with no free slots anywhere, a new server is allocated and the camp leased to it")
    void allocatesWhenFull() {
        pool.servers = List.of(new ServerPool.Slots("camp-a", 0));

        final PlacementDecision decision = placer.place("camp", 42, true);

        assertEquals(PlacementMessages.ALLOCATED, decision.getOutcome());
        assertEquals("camp-new", decision.getServer());
        assertEquals(List.of("camp-new"), pool.allocations);
        assertEquals("camp-new", leases.stored.get("camp.42").getServer());
    }

    @Test
    @DisplayName("a server's last slot is not handed out twice before it has counted the first")
    void promisedSlotsCount() {
        pool.servers = List.of(new ServerPool.Slots("camp-a", 1));

        assertEquals("camp-a", placer.place("camp", 1, true).getServer());
        assertEquals("camp-new", placer.place("camp", 2, true).getServer());
    }

    @Test
    @DisplayName("nowhere to put it says so and writes no lease")
    void unavailableWritesNothing() {
        pool.nextAllocation = null;

        final PlacementDecision decision = placer.place("camp", 42, true);

        assertEquals(PlacementMessages.UNAVAILABLE, decision.getOutcome());
        assertNull(decision.getServer());
        assertTrue(leases.stored.isEmpty());
    }

    @Test
    @DisplayName("a lease nobody renews lapses after its time and the camp can open elsewhere")
    void unrenewedLeaseLapses() {
        pool.servers = List.of(new ServerPool.Slots("camp-a", 5));
        placer.place("camp", 42, true);
        pool.servers = List.of(new ServerPool.Slots("camp-b", 5));

        now.addAndGet(Duration.ofSeconds(61).toMillis());
        final PlacementDecision moved = placer.place("camp", 42, true);

        assertEquals(PlacementMessages.PLACED, moved.getOutcome());
        assertEquals("camp-b", moved.getServer());
    }

    @Test
    @DisplayName("a host advertising the camp keeps it however old the lease is")
    void advertisingRenews() {
        pool.servers = List.of(new ServerPool.Slots("camp-a", 5));
        placer.place("camp", 42, true);
        leases.advertised.add("camp.42@camp-a");
        pool.servers = List.of(new ServerPool.Slots("camp-b", 5));

        now.addAndGet(Duration.ofHours(1).toMillis());

        assertEquals("camp-a", placer.place("camp", 42, true).getServer());
    }

    @Test
    @DisplayName("time spent with NATS unreachable never counts against a lease")
    void unreachableTimeDoesNotCount() {
        pool.servers = List.of(new ServerPool.Slots("camp-a", 5));
        placer.place("camp", 42, true);
        pool.servers = List.of(new ServerPool.Slots("camp-b", 5));

        // NATS is down for ten minutes. No host could advertise, and nothing could be renewed.
        now.addAndGet(Duration.ofMinutes(10).toMillis());
        placer.markReachable();
        now.addAndGet(Duration.ofSeconds(30).toMillis());

        assertEquals("camp-a", placer.place("camp", 42, true).getServer(),
                "the host gets a full lease time to advertise again before anything moves");
    }

    @Test
    @DisplayName("a site that is not owned gets a server for one instance and no lease")
    void unownedSitesTakeNoLease() {
        pool.servers = List.of(new ServerPool.Slots("camp-a", 5));

        final PlacementDecision decision = placer.place("dungeon", 0, false);

        assertEquals("camp-a", decision.getServer());
        assertFalse(leases.read("dungeon", 0).isPresent());
    }

    @Test
    @DisplayName("a draining host releases its camp, which then goes to another server and never back to it")
    void releasedCampsMoveOffTheDrainingServer() {
        pool.servers = List.of(new ServerPool.Slots("camp-a", 5));
        placer.place("camp", 42, true);
        leases.advertised.add("camp.42@camp-a");

        assertTrue(placer.release("camp", 42, "camp-a"));
        leases.advertised.clear();
        pool.servers = List.of(new ServerPool.Slots("camp-a", 5), new ServerPool.Slots("camp-b", 3));

        final PlacementDecision moved = placer.place("camp", 42, true);
        assertEquals("camp-b", moved.getServer());
        assertEquals("camp-b", leases.stored.get("camp.42").getServer());
    }

    @Test
    @DisplayName("releasing a camp another server now holds leaves that lease alone")
    void releaseOnlyDropsTheReleasersLease() {
        pool.servers = List.of(new ServerPool.Slots("camp-b", 5));
        placer.place("camp", 42, true);

        assertFalse(placer.release("camp", 42, "camp-a"));
        assertEquals("camp-b", leases.stored.get("camp.42").getServer());
    }
}
