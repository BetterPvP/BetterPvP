package me.mykindos.betterpvp.sessions.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaseBookTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final LeaseBook book = new LeaseBook(10_000, 5_000, now::get);
    private final UUID player = UUID.randomUUID();

    @Test
    void anOwnedPlayerIsHeldUntilReleased() {
        final Claim first = book.claim(player, "A");
        assertTrue(first.isGranted());

        final Claim second = book.claim(player, "B");
        assertFalse(second.isGranted());
        assertEquals("A", second.getHolder());
        assertFalse(book.claim(player, "A").isGranted());

        assertFalse(book.release(player, "B", first.getEpoch()));
        assertTrue(book.release(player, "A", first.getEpoch()));
        final Claim third = book.claim(player, "B");
        assertTrue(third.isGranted());
        assertTrue(third.getEpoch() > first.getEpoch());
    }

    @Test
    void aSilentOwnerLosesThePlayerWhenTheLeaseRunsOut() {
        final Claim first = book.claim(player, "A");
        now.addAndGet(9_000);
        assertFalse(book.claim(player, "B").isGranted());

        now.addAndGet(1_000);
        final Claim taken = book.claim(player, "B");
        assertTrue(taken.isGranted());
        assertFalse(book.release(player, "A", first.getEpoch()));
    }

    @Test
    void aHeartbeatKeepsTheLease() {
        book.claim(player, "A");
        now.addAndGet(9_000);
        book.heartbeat("A");
        now.addAndGet(9_000);
        assertFalse(book.claim(player, "B").isGranted());
    }

    @Test
    void epochsKeepRisingWhenTheClockDoesNot() {
        final Claim first = book.claim(player, "A");
        book.release(player, "A", first.getEpoch());
        final Claim second = book.claim(player, "A");
        assertTrue(second.getEpoch() > first.getEpoch());
    }

    @Test
    void aSecondLoginOfTheSameAccountIsRefused() {
        assertTrue(book.login(player, "proxy-1"));
        assertFalse(book.login(player, "proxy-2"));
        assertTrue(book.login(player, "proxy-1"));

        book.logout(player, "proxy-1");
        assertTrue(book.login(player, "proxy-2"));
    }

    @Test
    void anAccountOnADeadProxyCanLogInElsewhere() {
        assertTrue(book.login(player, "proxy-1"));
        now.addAndGet(4_000);
        assertFalse(book.login(player, "proxy-2"));
        now.addAndGet(1_000);
        assertTrue(book.login(player, "proxy-2"));
    }
}
