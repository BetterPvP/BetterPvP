package me.mykindos.betterpvp.clans.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClanMutationTest {

    @Test
    void aMutationSurvivesTheWire() {
        final ClanMutation mutation = ClanMutation.builder()
                .op(ClanOp.CAMP_PERMISSION)
                .clan(42L)
                .member(UUID.randomUUID())
                .rank("ADMIN")
                .flag(true)
                .scope("RANK_CONSTRUCTION")
                .action("BUILD")
                .build();

        assertEquals(mutation, ClanMutation.fromPayload(mutation.toPayload()));
    }

    @Test
    void propertiesSurviveTheWire() {
        final ClanMutation mutation = ClanMutation.builder()
                .op(ClanOp.PROPERTIES)
                .clan(42L)
                .properties(Map.of("ENERGY", "1200", "POINTS", "5"))
                .build();

        assertEquals(mutation, ClanMutation.fromPayload(mutation.toPayload()));
    }

    @Test
    void aBalanceSurvivesTheWire() {
        final ClanBalance balance = new ClanBalance(42L, 900L, 17L);
        assertEquals(balance, ClanBalance.fromPayload(balance.toPayload()));
    }

    @Test
    void unusedFieldsStayOffTheWire() {
        final ClanMutation mutation = ClanMutation.builder().op(ClanOp.DISBAND).clan(7L).build();

        assertEquals(2, mutation.toPayload().size());
        assertNull(ClanMutation.fromPayload(mutation.toPayload()).getOther());
    }

    @Test
    void changesAndRepliesSurviveTheWire() {
        final ClanChange change = new ClanChange(3L, ClanOp.ALLY, List.of(1L, 2L));
        assertEquals(change, ClanChange.fromPayload(change.toPayload()));

        assertTrue(ClanReply.fromPayload(ClanReply.applied(9L).toPayload()).isApplied());
        final ClanReply refused = ClanReply.fromPayload(ClanReply.refused("taken").toPayload());
        assertFalse(refused.isApplied());
        assertEquals("taken", refused.getError());
    }

    @Test
    void ranksStepAndStopAtTheEnds() {
        assertEquals("MEMBER", ClanRanks.step("RECRUIT", 1));
        assertEquals("LEADER", ClanRanks.step("LEADER", 1));
        assertEquals("RECRUIT", ClanRanks.step("RECRUIT", -1));
    }
}
