package me.mykindos.betterpvp.clans.clans.writer;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.CustomLog;
import me.mykindos.betterpvp.clans.Clans;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.ClanProperty;
import me.mykindos.betterpvp.clans.clans.repository.ClanRepository;
import me.mykindos.betterpvp.clans.protocol.ClanMutation;
import me.mykindos.betterpvp.clans.protocol.ClanOp;
import me.mykindos.betterpvp.clans.protocol.ClanReply;
import me.mykindos.betterpvp.clans.protocol.ClanTopics;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.components.clans.IClan;
import me.mykindos.betterpvp.core.components.clans.data.ClanEnemy;
import me.mykindos.betterpvp.core.components.clans.data.ClanMember;
import me.mykindos.betterpvp.core.framework.net.BusMessage;
import me.mykindos.betterpvp.core.framework.net.MessageBus;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends every clan write to the Clans service, the network's only writer of clans.
 * <p>
 * The caller's in-memory change shows on this server straight away. The service applies writes in the order it
 * receives them and announces each one, and every server, this one included, re-reads the clans it names through
 * {@link ClanChangeFeed}. A write the service refuses, or never answers, is undone the same way: the clans are re-read
 * and this server's copy goes back to what is stored.
 * <p>
 * Property writes for a clan are gathered for a few ticks and sent together. A clan's balance is never sent: the
 * ledger keeps it.
 */
@CustomLog
@Singleton
public class ServiceClanWriter implements ClanWriter {

    private final Clans clans;
    private final MessageBus bus;
    private final ClanChangeFeed feed;
    private final ClanRepository repository;
    private final Map<Long, Map<String, String>> queuedProperties = new ConcurrentHashMap<>();

    @Inject
    public ServiceClanWriter(@NotNull Clans clans, @NotNull MessageBus bus, @NotNull ClanChangeFeed feed,
                             @NotNull ClanRepository repository) {
        this.clans = clans;
        this.bus = bus;
        this.feed = feed;
        this.repository = repository;
        bus.subscribe(ClanTopics.CHANGED, feed::onChange);
    }

    @Override
    public void create(@NotNull Clan clan) {
        final ClanMutation.ClanMutationBuilder mutation = mutation(ClanOp.CREATE, clan)
                .name(clan.getName())
                .realm(Core.getCurrentRealm().getId());
        clan.getMembers().stream()
                .filter(member -> member.getRank() == ClanMember.MemberRank.LEADER)
                .findFirst()
                .ifPresent(leader -> mutation.member(leader.getUuid()));
        send(mutation.build());
    }

    @Override
    public void disband(@NotNull Clan clan) {
        repository.forgetQueuedProperties(clan);
        queuedProperties.remove(clan.getId());
        send(mutation(ClanOp.DISBAND, clan).build());
    }

    @Override
    public void rename(@NotNull Clan clan) {
        send(mutation(ClanOp.RENAME, clan).name(clan.getName()).build());
    }

    @Override
    public void addMember(@NotNull Clan clan, @NotNull ClanMember member) {
        send(mutation(ClanOp.MEMBER_ADD, clan).member(member.getUuid()).rank(member.getRank().name()).build());
    }

    @Override
    public void removeMember(@NotNull Clan clan, @NotNull ClanMember member) {
        send(mutation(ClanOp.MEMBER_REMOVE, clan).member(member.getUuid()).build());
    }

    @Override
    public void promote(@NotNull Clan clan, @NotNull ClanMember member) {
        send(mutation(ClanOp.MEMBER_PROMOTE, clan).member(member.getUuid()).build());
    }

    @Override
    public void demote(@NotNull Clan clan, @NotNull ClanMember member) {
        send(mutation(ClanOp.MEMBER_DEMOTE, clan).member(member.getUuid()).build());
    }

    @Override
    public void ally(@NotNull Clan clan, @NotNull Clan other) {
        send(mutation(ClanOp.ALLY, clan).other(other.getId()).build());
    }

    @Override
    public void removeAlliance(@NotNull Clan clan, @NotNull Clan other) {
        send(mutation(ClanOp.UNALLY, clan).other(other.getId()).build());
    }

    @Override
    public void setTrust(@NotNull Clan clan, @NotNull Clan other, boolean trusted) {
        send(mutation(ClanOp.TRUST, clan).other(other.getId()).flag(trusted).build());
    }

    @Override
    public void enemy(@NotNull Clan clan, @NotNull Clan other) {
        send(mutation(ClanOp.ENEMY, clan).other(other.getId()).build());
    }

    @Override
    public void removeEnemy(@NotNull Clan clan, @NotNull Clan other) {
        send(mutation(ClanOp.UNENEMY, clan).other(other.getId()).build());
    }

    @Override
    public void setCampPermission(long clanId, @NotNull CampGrant grant) {
        send(ClanMutation.builder()
                .op(ClanOp.CAMP_PERMISSION)
                .clan(clanId)
                .scope(grant.getScope().name())
                .rank(grant.getRank() == null ? null : grant.getRank().name())
                .action(grant.getAction())
                .flag(grant.isAllowed())
                .build());
    }

    @Override
    public void saveProperty(@NotNull Clan clan, @NotNull String property, @NotNull Object value) {
        if (property.equals(ClanProperty.BALANCE.name())) {
            return;
        }
        final boolean first = queuedProperties.isEmpty();
        queuedProperties.computeIfAbsent(clan.getId(), id -> new ConcurrentHashMap<>()).put(property, value.toString());
        if (first && clans.isEnabled()) {
            UtilServer.runTaskLater(clans, () -> flushProperties(true), 5L);
        }
    }

    @Override
    public void flushProperties(boolean async) {
        for (Long clanId : List.copyOf(queuedProperties.keySet())) {
            final Map<String, String> properties = queuedProperties.remove(clanId);
            if (properties != null && !properties.isEmpty()) {
                send(ClanMutation.builder().op(ClanOp.PROPERTIES).clan(clanId).properties(Map.copyOf(properties))
                        .build());
            }
        }
    }

    @Override
    public void setCore(@NotNull Clan clan) {
        send(mutation(ClanOp.CORE, clan).value(ClanRepository.encodeHome(clan)).build());
    }

    @Override
    public void setBanner(@NotNull Clan clan) {
        send(mutation(ClanOp.BANNER, clan).value(ClanRepository.encodeBanner(clan)).build());
    }

    @Override
    public @NotNull CompletableFuture<Void> saveVault(@NotNull Clan clan) {
        return send(mutation(ClanOp.VAULT, clan).value(clan.getCore().getVault().serialize()).build())
                .thenApply(reply -> null);
    }

    @Override
    public @NotNull CompletableFuture<Void> saveMailbox(@NotNull Clan clan) {
        return send(mutation(ClanOp.MAILBOX, clan).value(clan.getCore().getMailbox().serialize()).build())
                .thenApply(reply -> null);
    }

    @Override
    public void claim(@NotNull Clan clan, @NotNull String chunk) {
        send(mutation(ClanOp.CLAIM, clan).value(chunk).build());
    }

    @Override
    public void unclaim(@NotNull Clan clan, @NotNull String chunk) {
        send(mutation(ClanOp.UNCLAIM, clan).value(chunk).build());
    }

    @Override
    public void setDominance(@NotNull IClan clan, @NotNull ClanEnemy enemy) {
        send(ClanMutation.builder().op(ClanOp.DOMINANCE).clan(clan.getId())
                .other(enemy.getClan().getId())
                .value(Integer.toString((int) enemy.getDominance()))
                .build());
    }

    /** Sent as the kill's worth rather than either side's result, so kills on two servers at once both count. */
    @Override
    public void killDominance(@NotNull IClan killer, @NotNull ClanEnemy killerEnemy, @NotNull IClan killed,
                              @NotNull ClanEnemy killedEnemy, double amount) {
        send(ClanMutation.builder().op(ClanOp.DOMINANCE_KILL).clan(killer.getId())
                .other(killed.getId())
                .value(Double.toString(amount))
                .build());
    }

    private static @NotNull ClanMutation.ClanMutationBuilder mutation(@NotNull ClanOp op, @NotNull Clan clan) {
        return ClanMutation.builder().op(op).clan(clan.getId());
    }

    /** Completes with the service's answer. A write it refused is undone by reading the clans it touched again. */
    private @NotNull CompletableFuture<ClanReply> send(@NotNull ClanMutation mutation) {
        final List<Long> affected = mutation.getOther() == null
                ? List.of(mutation.getClan())
                : List.of(mutation.getClan(), mutation.getOther());

        return bus.request(BusMessage.of(ClanTopics.MUTATE, mutation.toPayload()), Duration.ofSeconds(3))
                .handle((replies, error) -> {
                    final ClanReply reply = error != null || replies.isEmpty()
                            ? ClanReply.refused("The Clans service did not answer")
                            : ClanReply.fromPayload(replies.getFirst().getPayload());
                    if (!reply.isApplied()) {
                        log.warn("The Clans service did not apply {} to clan {}: {}", mutation.getOp(),
                                mutation.getClan(), reply.getError()).submit();
                        feed.refresh(affected, ClanChangeFeed.partsOf(mutation.getOp()));
                    }
                    return reply;
                });
    }
}
