package me.mykindos.betterpvp.clans.clans.writer;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.Builder;
import lombok.CustomLog;
import lombok.Value;
import me.mykindos.betterpvp.clans.Clans;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.ClanManager;
import me.mykindos.betterpvp.clans.clans.events.ClanDisbandedElsewhereEvent;
import me.mykindos.betterpvp.clans.clans.repository.ClanRepository;
import me.mykindos.betterpvp.clans.database.jooq.tables.records.ClanMetadataRecord;
import me.mykindos.betterpvp.clans.protocol.ClanChange;
import me.mykindos.betterpvp.clans.protocol.ClanOp;
import me.mykindos.betterpvp.clans.utilities.ClansNamespacedKeys;
import me.mykindos.betterpvp.clans.world.camp.CampPermissions;
import me.mykindos.betterpvp.core.components.clans.data.ClanAlliance;
import me.mykindos.betterpvp.core.components.clans.data.ClanEnemy;
import me.mykindos.betterpvp.core.components.clans.data.ClanMember;
import me.mykindos.betterpvp.core.components.clans.data.ClanTerritory;
import me.mykindos.betterpvp.core.framework.net.BusMessage;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import me.mykindos.betterpvp.core.utilities.UtilWorld;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jooq.Record2;
import org.jooq.Result;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
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
 * reference. Each change re-reads only the {@link Part}s its op touches.
 */
@CustomLog
@Singleton
public class ClanChangeFeed {

    /** The parts of a clan a change re-reads. */
    public enum Part {
        /** Name, members and relations, dominance included. Every change reads it. */
        CLAN,
        CAMP,
        PROPERTIES,
        HOME,
        BANNER,
        VAULT,
        MAILBOX,
        TERRITORY
    }

    private final Clans clans;
    private final ClanManager clanManager;
    private final ClanRepository repository;
    private final Provider<CampPermissions> campPermissions;
    private final Provider<ClanBank> bank;
    private final ExecutorService reader = Executors.newSingleThreadExecutor(
            runnable -> Thread.ofPlatform().name("clans-change-feed").daemon().unstarted(runnable));

    @Inject
    public ClanChangeFeed(@NotNull Clans clans, @NotNull ClanManager clanManager, @NotNull ClanRepository repository,
                          @NotNull Provider<CampPermissions> campPermissions, @NotNull Provider<ClanBank> bank) {
        this.clans = clans;
        this.clanManager = clanManager;
        this.repository = repository;
        this.campPermissions = campPermissions;
        this.bank = bank;
    }

    public static @NotNull Set<Part> partsOf(@NotNull ClanOp op) {
        return switch (op) {
            case CAMP_PERMISSION -> EnumSet.of(Part.CLAN, Part.CAMP);
            case PROPERTIES -> EnumSet.of(Part.CLAN, Part.PROPERTIES);
            case CORE -> EnumSet.of(Part.CLAN, Part.HOME);
            case BANNER -> EnumSet.of(Part.CLAN, Part.BANNER);
            case VAULT -> EnumSet.of(Part.CLAN, Part.VAULT);
            case MAILBOX -> EnumSet.of(Part.CLAN, Part.MAILBOX);
            case CLAIM, UNCLAIM -> EnumSet.of(Part.CLAN, Part.TERRITORY);
            default -> EnumSet.of(Part.CLAN);
        };
    }

    public void onChange(@NotNull BusMessage message) {
        final ClanChange change;
        try {
            change = ClanChange.fromPayload(message.getPayload());
        } catch (RuntimeException ex) {
            log.warn("Ignored an unreadable clan change {}", message.getPayload()).submit();
            return;
        }
        refresh(change.getClans(), partsOf(change.getOp()));
    }

    /** Re-reads {@code parts} of {@code clanIds} from Postgres and brings this server's copies in line with them. */
    public void refresh(@NotNull Collection<Long> clanIds, @NotNull Set<Part> parts) {
        final List<Long> ids = List.copyOf(clanIds);
        reader.execute(() -> {
            final List<Snapshot> snapshots = new ArrayList<>();
            for (long id : ids) {
                try {
                    snapshots.add(read(id, parts));
                } catch (RuntimeException ex) {
                    log.error("Failed to re-read clan {}, keeping this server's copy", id, ex).submit();
                }
            }
            UtilServer.runTask(clans, () -> snapshots.forEach(this::apply));
        });
    }

    private @NotNull Snapshot read(long id, @NotNull Set<Part> parts) {
        final Optional<String> name = repository.getName(id);
        final Snapshot.SnapshotBuilder snapshot = Snapshot.builder().id(id);
        if (name.isEmpty()) {
            return snapshot.build();
        }
        snapshot.name(name.get())
                .loaded(clanManager.getClanById(id).isPresent() ? null : loadNew(id))
                .members(repository.getMembers(id))
                .alliances(repository.getAllianceIds(id))
                .enemies(repository.getEnemyIds(id));
        if (parts.contains(Part.CAMP)) {
            snapshot.grants(repository.getCampGrants(id));
        }
        if (parts.contains(Part.PROPERTIES)) {
            snapshot.properties(repository.getProperties(id));
        }
        if (parts.contains(Part.HOME)) {
            snapshot.home(repository.getHome(id));
        }
        if (parts.contains(Part.BANNER) || parts.contains(Part.VAULT) || parts.contains(Part.MAILBOX)) {
            final ClanMetadataRecord metadata = repository.getMetadata(id);
            if (metadata != null) {
                snapshot.banner(parts.contains(Part.BANNER) ? metadata.getBanner() : null)
                        .vault(parts.contains(Part.VAULT) ? metadata.getVault() : null)
                        .mailbox(parts.contains(Part.MAILBOX) ? metadata.getMailbox() : null);
            }
        }
        if (parts.contains(Part.TERRITORY)) {
            snapshot.territory(repository.getTerritoryChunks(id));
        }
        return snapshot.build();
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
            bank.get().sync(List.of(clan));
            final Set<Long> related = new HashSet<>(snapshot.getAlliances().keySet());
            related.addAll(snapshot.getEnemies().keySet());
            related.removeIf(id -> clanManager.getClanById(id).isEmpty());
            if (!related.isEmpty()) {
                refresh(related, EnumSet.of(Part.CLAN));
            }
        } else {
            return;
        }

        clan.setName(snapshot.getName());
        applyMembers(clan, snapshot.getMembers());
        applyRelations(clan, snapshot);
        snapshot.getGrants().forEach(grant -> campPermissions.get().apply(clan.getId(), grant));
        if (snapshot.getProperties() != null) {
            repository.readProperties(clan, snapshot.getProperties());
        }
        if (snapshot.getHome() != null) {
            final String home = snapshot.getHome();
            clan.getCore().setPosition(home.isEmpty() ? null : UtilWorld.stringToLocation(home));
        }
        if (snapshot.getBanner() != null && !snapshot.getBanner().isEmpty()) {
            ClanRepository.readBanner(clan, snapshot.getBanner());
        }
        // An open vault or mailbox is saved when it closes, which is newer than anything stored.
        if (snapshot.getVault() != null && !clan.getCore().getVault().isLocked()) {
            clan.getCore().getVault().read(snapshot.getVault());
        }
        if (snapshot.getMailbox() != null && !clan.getCore().getMailbox().isLocked()) {
            clan.getCore().getMailbox().read(snapshot.getMailbox());
        }
        if (snapshot.getTerritory() != null) {
            applyTerritory(clan, snapshot.getTerritory());
        }
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

        clan.getEnemies().removeIf(enemy -> !snapshot.getEnemies().containsKey(enemy.getClan().getId()));
        snapshot.getEnemies().forEach((otherId, dominance) -> clanManager.getClanById(otherId).ifPresentOrElse(other ->
                clan.getEnemy(other).ifPresentOrElse(enemy -> enemy.setDominance(dominance),
                        () -> clan.getEnemies().add(new ClanEnemy(other, dominance))),
                () -> missing.add(otherId)));

        if (!missing.isEmpty()) {
            // Loading a clan refreshes the clans it relates to, which fills in this side of the relation.
            refresh(missing, EnumSet.of(Part.CLAN));
        }
    }

    private void applyTerritory(@NotNull Clan clan, @NotNull List<String> stored) {
        final Set<String> wanted = new HashSet<>(stored);
        clan.getTerritory().removeIf(territory -> {
            if (wanted.remove(territory.getChunk())) {
                return false;
            }
            final Chunk chunk = UtilWorld.stringToChunk(territory.getChunk(), false);
            if (chunk != null && clanManager.getClanByChunk(chunk).filter(clan::equals).isPresent()) {
                chunk.getPersistentDataContainer().remove(ClansNamespacedKeys.CLAN);
            }
            return true;
        });
        for (String added : wanted) {
            clan.getTerritory().add(new ClanTerritory(added));
            final Chunk chunk = UtilWorld.stringToChunk(added, false);
            if (chunk != null) {
                chunk.getPersistentDataContainer().set(ClansNamespacedKeys.CLAN, PersistentDataType.LONG, clan.getId());
            }
        }
    }

    /** Drops a clan that was disbanded somewhere else. */
    private void forget(@NotNull Clan clan) {
        UtilServer.callEvent(new ClanDisbandedElsewhereEvent(clan));
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

    /** What was read of one clan. A part that was not read is null, or empty for the lists and maps. */
    @Value
    @Builder
    private static class Snapshot {
        long id;
        /** Null when the clan does not exist. */
        @Nullable String name;
        /** The full clan, read only when this server did not have it yet. */
        @Nullable Clan loaded;
        @Builder.Default List<ClanMember> members = List.of();
        @Builder.Default Map<Long, Boolean> alliances = Map.of();
        @Builder.Default Map<Long, Integer> enemies = Map.of();
        @Builder.Default List<CampGrant> grants = List.of();
        @Nullable Result<Record2<String, String>> properties;
        @Nullable String home;
        @Nullable String banner;
        @Nullable String vault;
        @Nullable String mailbox;
        @Nullable List<String> territory;
    }
}
