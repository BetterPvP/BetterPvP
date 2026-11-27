package me.mykindos.betterpvp.clans.clans.writer;

import me.mykindos.betterpvp.clans.Clans;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.ClanProperty;
import me.mykindos.betterpvp.clans.clans.repository.ClanRepository;
import me.mykindos.betterpvp.clans.protocol.ClanMutation;
import me.mykindos.betterpvp.clans.protocol.ClanOp;
import me.mykindos.betterpvp.clans.protocol.ClanRanks;
import me.mykindos.betterpvp.clans.protocol.ClanReply;
import me.mykindos.betterpvp.clans.protocol.ClanTopics;
import me.mykindos.betterpvp.core.components.clans.data.ClanEnemy;
import me.mykindos.betterpvp.core.components.clans.data.ClanMember;
import me.mykindos.betterpvp.core.framework.net.BusMessage;
import me.mykindos.betterpvp.core.framework.net.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceClanWriterTest {

    private final ServiceBus bus = new ServiceBus();
    private final ClanChangeFeed feed = mock(ClanChangeFeed.class);
    private ServiceClanWriter writer;

    @BeforeEach
    void setUp() {
        writer = new ServiceClanWriter(mock(Clans.class), bus, feed, mock(ClanRepository.class));
    }

    @Test
    void listensForChangesFromTheStart() {
        assertTrue(bus.subscribed.contains(ClanTopics.CHANGED));
    }

    @Test
    void aPromotionIsSentAsAStepSoTwoServersPromotingBothLand() {
        final ClanMember member = new ClanMember(UUID.randomUUID(), ClanMember.MemberRank.MEMBER, "Steve");

        writer.promote(clan(5L), member);

        final ClanMutation sent = bus.lastMutation();
        assertEquals(ClanOp.MEMBER_PROMOTE, sent.getOp());
        assertEquals(5L, sent.getClan());
        assertEquals(member.getUuid(), sent.getMember());
        assertNull(sent.getRank());
    }

    @Test
    void anAllianceNamesBothClans() {
        writer.ally(clan(1L), clan(2L));

        final ClanMutation sent = bus.lastMutation();
        assertEquals(ClanOp.ALLY, sent.getOp());
        assertEquals(2L, sent.getOther());
    }

    @Test
    void anAppliedWriteLeavesTheLocalCopyAlone() {
        bus.answer = ClanReply.applied(1L);

        writer.enemy(clan(1L), clan(2L));

        verify(feed, never()).refresh(anyCollection(), anySet());
    }

    @Test
    void aRefusedWriteRereadsTheClansItTouched() {
        bus.answer = ClanReply.refused("not allied");

        writer.setTrust(clan(1L), clan(2L), true);

        verify(feed).refresh(List.of(1L, 2L), EnumSet.of(ClanChangeFeed.Part.CLAN));
    }

    @Test
    void anUnansweredWriteRereadsTheClan() {
        bus.answer = null;

        writer.addMember(clan(3L), new ClanMember(UUID.randomUUID(), ClanMember.MemberRank.RECRUIT, "Alex"));

        verify(feed).refresh(List.of(3L), EnumSet.of(ClanChangeFeed.Part.CLAN));
    }

    @Test
    void propertiesAreSentTogetherPerClanAndTheBalanceNever() {
        final Clan clan = clan(4L);
        writer.saveProperty(clan, ClanProperty.ENERGY.name(), 1200);
        writer.saveProperty(clan, ClanProperty.POINTS.name(), 7);
        writer.saveProperty(clan, ClanProperty.ENERGY.name(), 1100);
        writer.saveProperty(clan, ClanProperty.BALANCE.name(), 900);

        writer.flushProperties(true);

        assertEquals(1, bus.requests.size());
        final ClanMutation sent = bus.lastMutation();
        assertEquals(ClanOp.PROPERTIES, sent.getOp());
        assertEquals(Map.of("ENERGY", "1100", "POINTS", "7"), sent.getProperties());
    }

    @Test
    void dominanceNamesTheEnemyAndTheValue() {
        writer.setDominance(clan(1L), new ClanEnemy(clan(2L), 35));

        final ClanMutation sent = bus.lastMutation();
        assertEquals(ClanOp.DOMINANCE, sent.getOp());
        assertEquals(2L, sent.getOther());
        assertEquals("35", sent.getValue());
    }

    @Test
    void theServiceStepsRanksInThePluginsOrder() {
        final List<String> pluginOrder = Arrays.stream(ClanMember.MemberRank.values())
                .sorted(Comparator.comparingInt(ClanMember.MemberRank::getPrivilege))
                .map(Enum::name)
                .toList();

        assertEquals(pluginOrder, ClanRanks.ORDER);
    }

    private static Clan clan(long id) {
        final Clan clan = mock(Clan.class);
        when(clan.getId()).thenReturn(id);
        return clan;
    }

    /** Answers every request at once with {@link #answer}, or with silence when it is null. */
    private static final class ServiceBus implements MessageBus {

        private final List<BusMessage> requests = new ArrayList<>();
        private final List<String> subscribed = new ArrayList<>();
        private ClanReply answer = ClanReply.applied(1L);

        ClanMutation lastMutation() {
            return ClanMutation.fromPayload(requests.getLast().getPayload());
        }

        @Override
        public void publish(@NotNull BusMessage message) {
        }

        @Override
        public @NotNull CompletableFuture<List<BusMessage>> request(@NotNull BusMessage message, @NotNull Duration timeout) {
            requests.add(message);
            if (answer == null) {
                return CompletableFuture.completedFuture(List.of());
            }
            final Map<String, String> payload = answer.toPayload();
            return CompletableFuture.completedFuture(List.of(message.replyWith(payload)));
        }

        @Override
        public void subscribe(@NotNull String topic, @NotNull Consumer<BusMessage> handler) {
            subscribed.add(topic);
        }

        @Override
        public void answer(@NotNull String topic, @NotNull Function<BusMessage, Optional<BusMessage>> responder) {
        }

        @Override
        public boolean isAvailable() {
            return true;
        }
    }
}
