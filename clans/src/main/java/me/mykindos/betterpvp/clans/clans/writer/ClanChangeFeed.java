package me.mykindos.betterpvp.clans.clans.writer;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.CustomLog;
import lombok.Value;
import me.mykindos.betterpvp.clans.Clans;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.ClanManager;
import me.mykindos.betterpvp.clans.clans.repository.ClanRepository;
import me.mykindos.betterpvp.clans.protocol.ClanChange;
import me.mykindos.betterpvp.clans.protocol.ClanOp;
import me.mykindos.betterpvp.clans.world.camp.CampPermissions;
import me.mykindos.betterpvp.core.components.clans.data.ClanAlliance;
import me.mykindos.betterpvp.core.components.clans.data.ClanEnemy;
import me.mykindos.betterpvp.core.components.clans.data.ClanMember;
import me.mykindos.betterpvp.core.framework.net.BusMessage;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Keeps this server's clans in step with Postgres when the Clans service writes them.
 * <p>
 * Every change the service announces names the clans it touched. Those are re-read off the main thread, one change at
 * a time so reads land in the order the service applied them, and folded into the in-memory clans on the main thread.
 * Objects that already exist are updated in place, so code holding a {@link Clan} or {@link ClanMember} keeps a live
 * reference.
 */
@CustomLog
@Singleton
public class ClanChangeFeed {

    private final Clans clans;
    private final ClanManager clanManager;
    private final ClanRepository repository;
    private final Provider<CampPermissions> campPermissions;
    private final ExecutorService reader = Executors.newSingleThreadExecutor(
            runnable -> Thread.ofPlatform().name("clans-change-feed").daemon().unstarted(runnable));

    @Inject
    public ClanChangeFeed(@NotNull Clans clans, @NotNull ClanManager clanManager, @NotNull ClanRepository repository,
                          @NotNull Provider<CampPermissions> campPermissions) {
        this.clans = clans;
        this.clanManager = clanManager;
        this.repository = repository;
        this.campPermissions = campPermissions;
    }

    public void onChange(@NotNull BusMessage message) {
        final ClanChange change;
        try {
            change = ClanChange.fromPayload(message.getPayload());
        } catch (RuntimeException ex) {
            log.warn("Ignored an unreadable clan change {}", message.getPayload()).submit();
            return;
        }
        refresh(change.getClans(), change.getOp() == ClanOp.CAMP_PERMISSION);
    }

    /** Re-reads {@code clanIds} from Postgres and brings this server's copies in line with what is stored. */
    public void refresh(@NotNull Collection<Long> clanIds, boolean campPermissionsChanged) {
        final List<Long> ids = List.copyOf(clanIds);
        reader.execute(() -> {
            final List<Snapshot> snapshots = new ArrayList<>();
            for (long id : ids) {
                try {
                    snapshots.add(read(id, campPermissionsChanged));
                } catch (RuntimeException ex) {
                    log.error("Failed to re-read clan {}, keeping this server's copy", id, ex).submit();
                }
            }
            UtilServer.runTask(clans, () -> snapshots.forEach(this::apply));
        });
    }

    private @NotNull Snapshot read(long id, boolean campPermissionsChanged) {
        final Optional<String> name = repository.getName(id);
        if (name.isEmpty()) {
            return new Snapshot(id, null, null, List.of(), Map.of(), Map.of(), List.of());
        }
        final Clan loaded = clanManager.getClanById(id).isPresent() ? null : loadNew(id);
        return new Snapshot(id, name.get(), loaded, repository.getMembers(id), repository.getAllianceIds(id),
                repository.getEnemyIds(id), campPermissionsChanged ? repository.getCampGrants(id) : List.of());
    }

    private @Nullable Clan loadNew(long id) {
        return repository.getById(id).map(clan -> {
            clan.setTerritory(repository.getTerritory(clan));
            clan.setInsurance(repository.getInsurance(clan));
            return clan;
        }).orElse(null);
    }

    private void apply(@NotNull Snapshot snapshot) {
        final Optional<Clan> local = clanManager.getClanById(snapshot.getId());
        if (snapshot.getName() == null) {
            local.ifPresent(this::forget);
            return;
        }

        final Clan clan;
        if (local.isPresent()) {
            clan = local.get();
        } else if (snapshot.getLoaded() != null) {
            clan = snapshot.getLoaded();
            clanManager.addObject(clan.getId(), clan);
            clanManager.getLeaderboard().forceUpdate();
            final Set<Long> related = new HashSet<>(snapshot.getAlliances().keySet());
            related.addAll(snapshot.getEnemies().keySet());
            related.removeIf(id -> clanManager.getClanById(id).isEmpty());
            if (!related.isEmpty()) {
                refresh(related, false);
            }
        } else {
            return;
        }

        clan.setName(snapshot.getName());
        applyMembers(clan, snapshot.getMembers());
        applyRelations(clan, snapshot);
        snapshot.getGrants().forEach(grant -> campPermissions.get().apply(clan.getId(), grant));
    }

    private void applyMembers(@NotNull Clan clan, @NotNull List<ClanMember> stored) {
        final Map<UUID, ClanMember> wanted = new HashMap<>();
        stored.forEach(member -> wanted.put(member.getUuid(), member));

        clan.getMembers().removeIf(member -> {
            if (wanted.containsKey(member.getUuid())) {
                return false;
            }
            final Player player = Bukkit.getPlayer(member.getUuid());
            if (player != null && clanManager.getClanByPlayer(player).filter(clan::equals).isPresent()) {
                player.removeMetadata("clan", clans);
            }
            return true;
        });

        for (ClanMember member : wanted.values()) {
            final Optional<ClanMember> existing = clan.getMemberByUUID(member.getUuid());
            if (existing.isPresent()) {
                existing.get().setRank(member.getRank());
            } else {
                clan.getMembers().add(member);
            }
            final Player player = Bukkit.getPlayer(member.getUuid());
            if (player != null) {
                player.setMetadata("clan", new FixedMetadataValue(clans, clan.getId()));
            }
        }
        clan.setOnline(clan.getMembers().stream().anyMatch(ClanMember::isOnline));
    }

    private void applyRelations(@NotNull Clan clan, @NotNull Snapshot snapshot) {
        final Set<Long> missing = new HashSet<>();

        clan.getAlliances().removeIf(alliance -> !snapshot.getAlliances().containsKey(alliance.getClan().getId()));
        snapshot.getAlliances().forEach((otherId, trusted) -> clanManager.getClanById(otherId).ifPresentOrElse(other ->
                clan.getAlliance(other).ifPresentOrElse(alliance -> alliance.setTrusted(trusted),
                        () -> clan.getAlliances().add(new ClanAlliance(other, trusted))),
                () -> missing.add(otherId)));

        // Dominance is still written by whichever server saw the kill, so a local value is never replaced here.
        clan.getEnemies().removeIf(enemy -> !snapshot.getEnemies().containsKey(enemy.getClan().getId()));
        snapshot.getEnemies().forEach((otherId, dominance) -> clanManager.getClanById(otherId).ifPresentOrElse(other -> {
            if (clan.getEnemy(other).isEmpty()) {
                clan.getEnemies().add(new ClanEnemy(other, dominance));
            }
        }, () -> missing.add(otherId)));

        if (!missing.isEmpty()) {
            // Loading a clan refreshes the clans it relates to, which fills in this side of the relation.
            refresh(missing, false);
        }
    }

    /** Drops a clan that was disbanded somewhere else. */
    private void forget(@NotNull Clan clan) {
        repository.forgetQueuedProperties(clan);
        clanManager.getObjects().values().forEach(other -> {
            other.getAlliances().removeIf(alliance -> alliance.getClan().getId() == clan.getId());
            other.getEnemies().removeIf(enemy -> enemy.getClan().getId() == clan.getId());
        });
        clan.getMembers().forEach(member -> {
            final Player player = Bukkit.getPlayer(member.getUuid());
            if (player != null) {
                player.removeMetadata("clan", clans);
            }
        });
        clanManager.getObjects().remove(clan.getId());
        clanManager.getLeaderboard().forceUpdate();
    }

    @Value
    private static class Snapshot {
        long id;
        /** Null when the clan does not exist. */
        @Nullable String name;
        /** The full clan, read only when this server did not have it yet. */
        @Nullable Clan loaded;
        List<ClanMember> members;
        Map<Long, Boolean> alliances;
        Map<Long, Integer> enemies;
        List<CampGrant> grants;
    }
}
