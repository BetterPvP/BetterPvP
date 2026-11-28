package me.mykindos.betterpvp.core.network;

import io.nats.client.Connection;
import lombok.Getter;
import me.mykindos.betterpvp.clans.protocol.ClanMutation;
import me.mykindos.betterpvp.clans.protocol.ClanReply;
import me.mykindos.betterpvp.clans.protocol.ClanTopics;
import me.mykindos.betterpvp.core.framework.economy.ledger.ServiceLedger;
import me.mykindos.betterpvp.core.framework.net.BusMessage;
import me.mykindos.betterpvp.core.framework.net.NatsMessageBus;
import me.mykindos.betterpvp.core.framework.net.NatsSiteDirectory;
import me.mykindos.betterpvp.core.framework.net.PlacementSiteHosts;
import me.mykindos.betterpvp.core.framework.net.RemoteInstance;
import me.mykindos.betterpvp.core.framework.net.ServerRoom;
import me.mykindos.betterpvp.core.playerstate.session.NatsPlayerLeases;
import me.mykindos.betterpvp.core.playerstate.session.PlayerHandoff;
import me.mykindos.betterpvp.ledger.NatsLedgerTransport;
import me.mykindos.betterpvp.sessions.SessionCalls;
import org.jetbrains.annotations.NotNull;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A game server reduced to what it says on the network: the plugin's own session handoff, placement, ledger and
 * site directory clients over one NATS connection, and clan writes sent the way {@code ServiceClanWriter} sends them.
 * Players are MockBukkit players on the one mocked server, so moving one means a new player object with the same id.
 */
@Getter
public final class FakeServer implements AutoCloseable {

    private final String name;
    private final Connection connection;
    private final NatsMessageBus bus;
    private final PlayerHandoff handoff;
    private final PlacementSiteHosts hosts;
    private final ServiceLedger ledger;
    private final NatsSiteDirectory directory;

    FakeServer(@NotNull Network network, @NotNull String name, @NotNull Connection connection) {
        this.name = name;
        this.connection = connection;
        this.bus = new NatsMessageBus(connection, network.getPrefix(), () -> name);
        bus.start();
        this.handoff = new PlayerHandoff(
                new NatsPlayerLeases(new SessionCalls(connection, network.getPrefix(), name), Duration.ofSeconds(2)),
                network.getFixture().getStore(), network.getFixture().getCapture());
        this.hosts = new PlacementSiteHosts(bus);
        this.ledger = new ServiceLedger(new NatsLedgerTransport(connection, network.getPrefix(), name,
                Duration.ofSeconds(2)), Runnable::run);
        this.directory = Objects.requireNonNull(NatsSiteDirectory.open(connection, network.getPrefix(), 1, () -> name));
    }

    /** Claims the player as their login would, and joins them once granted. */
    public @NotNull PlayerMock arrive(@NotNull UUID player) {
        assertTrue(handoff.arrive(player, Duration.ofSeconds(20)).join(), name + " could not claim " + player);
        return join(player);
    }

    public @NotNull PlayerMock join(@NotNull UUID player) {
        final PlayerMock joined = new PlayerMock(MockBukkit.getMock(), "Mover", player);
        MockBukkit.getMock().addPlayer(joined);
        handoff.join(joined);
        return joined;
    }

    /** Saves and releases the player, then disconnects them, as their quit would. */
    public void leave(@NotNull PlayerMock player) {
        handoff.leave(player).join();
        player.disconnect();
    }

    /** Where placement puts the site, as this server would ask before sending anyone there. */
    public @NotNull String hostOf(@NotNull String site, long owner, boolean owned) {
        return hosts.hostFor(site, owner, owned, name).join()
                .orElseThrow(() -> new AssertionError("Placement placed nothing for " + site + " " + owner));
    }

    /** Advertises an open instance of the site, which is how a host keeps its lease. */
    public void advertise(@NotNull String site, long owner, int occupants) {
        directory.publish(List.of(new RemoteInstance(UUID.nameUUIDFromBytes((site + owner).getBytes()), site, owner,
                name, site + "/" + owner, true, occupants)), new ServerRoom(name, occupants, 100));
    }

    /** Sends a clan write the way {@code ServiceClanWriter} does and waits for the service's answer. */
    public @NotNull ClanReply mutate(@NotNull ClanMutation mutation) {
        final List<BusMessage> replies = bus.request(BusMessage.of(ClanTopics.MUTATE, mutation.toPayload()),
                Duration.ofSeconds(3)).join();
        return replies.isEmpty()
                ? ClanReply.refused("The Clans service did not answer")
                : ClanReply.fromPayload(replies.getFirst().getPayload());
    }

    /** Dies without releasing anything or saving again. */
    public void crash() throws InterruptedException {
        bus.close();
        connection.close();
    }

    @Override
    public void close() {
        bus.close();
    }
}
