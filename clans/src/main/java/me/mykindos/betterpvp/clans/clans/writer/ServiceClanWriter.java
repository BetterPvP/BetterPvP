package me.mykindos.betterpvp.clans.clans.writer;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.CustomLog;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.repository.ClanRepository;
import me.mykindos.betterpvp.clans.protocol.ClanMutation;
import me.mykindos.betterpvp.clans.protocol.ClanOp;
import me.mykindos.betterpvp.clans.protocol.ClanReply;
import me.mykindos.betterpvp.clans.protocol.ClanTopics;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.components.clans.data.ClanMember;
import me.mykindos.betterpvp.core.framework.net.BusMessage;
import me.mykindos.betterpvp.core.framework.net.MessageBus;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;

/**
 * Sends every clan write to the Clans service, the network's only writer of clans.
 * <p>
 * The caller's in-memory change shows on this server straight away. The service applies writes in the order it
 * receives them and announces each one, and every server, this one included, re-reads the clans it names through
 * {@link ClanChangeFeed}. A write the service refuses, or never answers, is undone the same way: the clans are re-read
 * and this server's copy goes back to what is stored.
 */
@CustomLog
@Singleton
public class ServiceClanWriter implements ClanWriter {

    private final MessageBus bus;
    private final ClanChangeFeed feed;
    private final ClanRepository repository;

    @Inject
    public ServiceClanWriter(@NotNull MessageBus bus, @NotNull ClanChangeFeed feed, @NotNull ClanRepository repository) {
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

    private static @NotNull ClanMutation.ClanMutationBuilder mutation(@NotNull ClanOp op, @NotNull Clan clan) {
        return ClanMutation.builder().op(op).clan(clan.getId());
    }

    private void send(@NotNull ClanMutation mutation) {
        final List<Long> affected = mutation.getOther() == null
                ? List.of(mutation.getClan())
                : List.of(mutation.getClan(), mutation.getOther());

        bus.request(BusMessage.of(ClanTopics.MUTATE, mutation.toPayload()), Duration.ofSeconds(3))
                .whenComplete((replies, error) -> {
                    final ClanReply reply = error != null || replies.isEmpty()
                            ? ClanReply.refused("The Clans service did not answer")
                            : ClanReply.fromPayload(replies.getFirst().getPayload());
                    if (!reply.isApplied()) {
                        log.warn("The Clans service did not apply {} to clan {}: {}", mutation.getOp(),
                                mutation.getClan(), reply.getError()).submit();
                        feed.refresh(affected, mutation.getOp() == ClanOp.CAMP_PERMISSION);
                    }
                });
    }
}
