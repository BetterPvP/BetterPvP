package me.mykindos.betterpvp.clans.clans.writer;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.repository.ClanRepository;
import me.mykindos.betterpvp.clans.world.camp.CampPermissions;
import me.mykindos.betterpvp.core.components.clans.IClan;
import me.mykindos.betterpvp.core.components.clans.data.ClanAlliance;
import me.mykindos.betterpvp.core.components.clans.data.ClanEnemy;
import me.mykindos.betterpvp.core.components.clans.data.ClanMember;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/** Writes straight to Postgres from this server, for a network of one server where nothing else writes clans. */
@Singleton
public class LocalClanWriter implements ClanWriter {

    private final ClanRepository repository;
    private final Provider<CampPermissions> campPermissions;

    @Inject
    public LocalClanWriter(@NotNull ClanRepository repository, @NotNull Provider<CampPermissions> campPermissions) {
        this.repository = repository;
        this.campPermissions = campPermissions;
    }

    @Override
    public void create(@NotNull Clan clan) {
        repository.save(clan);
    }

    @Override
    public void disband(@NotNull Clan clan) {
        repository.delete(clan);
    }

    @Override
    public void rename(@NotNull Clan clan) {
        repository.updateClanName(clan);
    }

    @Override
    public void addMember(@NotNull Clan clan, @NotNull ClanMember member) {
        repository.saveClanMember(clan, member);
    }

    @Override
    public void removeMember(@NotNull Clan clan, @NotNull ClanMember member) {
        repository.deleteClanMember(clan, member);
    }

    @Override
    public void promote(@NotNull Clan clan, @NotNull ClanMember member) {
        repository.updateClanMemberRank(clan, member);
    }

    @Override
    public void demote(@NotNull Clan clan, @NotNull ClanMember member) {
        repository.updateClanMemberRank(clan, member);
    }

    @Override
    public void ally(@NotNull Clan clan, @NotNull Clan other) {
        repository.saveClanAlliance(clan, new ClanAlliance(other, false));
        repository.saveClanAlliance(other, new ClanAlliance(clan, false));
    }

    @Override
    public void removeAlliance(@NotNull Clan clan, @NotNull Clan other) {
        repository.deleteClanAlliance(clan, new ClanAlliance(other, false));
        repository.deleteClanAlliance(other, new ClanAlliance(clan, false));
    }

    @Override
    public void setTrust(@NotNull Clan clan, @NotNull Clan other, boolean trusted) {
        repository.saveTrust(clan, new ClanAlliance(other, trusted));
        repository.saveTrust(other, new ClanAlliance(clan, trusted));
    }

    @Override
    public void enemy(@NotNull Clan clan, @NotNull Clan other) {
        repository.saveClanEnemy(clan, new ClanEnemy(other, 0));
        repository.saveClanEnemy(other, new ClanEnemy(clan, 0));
    }

    @Override
    public void removeEnemy(@NotNull Clan clan, @NotNull Clan other) {
        repository.deleteClanEnemy(clan, new ClanEnemy(other, 0));
        repository.deleteClanEnemy(other, new ClanEnemy(clan, 0));
    }

    @Override
    public void setCampPermission(long clanId, @NotNull CampGrant grant) {
        campPermissions.get().apply(clanId, grant);
    }

    @Override
    public void saveProperty(@NotNull Clan clan, @NotNull String property, @NotNull Object value) {
        repository.saveProperty(clan, property, value);
    }

    @Override
    public void flushProperties(boolean async) {
        repository.processPropertyUpdates(async);
    }

    @Override
    public void setCore(@NotNull Clan clan) {
        repository.updateClanCore(clan);
    }

    @Override
    public void setBanner(@NotNull Clan clan) {
        repository.updateClanBanner(clan);
    }

    @Override
    public @NotNull CompletableFuture<Void> saveVault(@NotNull Clan clan) {
        repository.updateClanVault(clan);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public @NotNull CompletableFuture<Void> saveMailbox(@NotNull Clan clan) {
        return repository.updateClanMailbox(clan);
    }

    @Override
    public void claim(@NotNull Clan clan, @NotNull String chunk) {
        repository.saveClanTerritory(clan, chunk);
    }

    @Override
    public void unclaim(@NotNull Clan clan, @NotNull String chunk) {
        repository.deleteClanTerritory(clan, chunk);
    }

    @Override
    public void setDominance(@NotNull IClan clan, @NotNull ClanEnemy enemy) {
        repository.updateDominance(clan, enemy);
    }

    @Override
    public void killDominance(@NotNull IClan killer, @NotNull ClanEnemy killerEnemy, @NotNull IClan killed,
                              @NotNull ClanEnemy killedEnemy, double amount) {
        repository.updateDominance(killed, killedEnemy);
        repository.updateDominance(killer, killerEnemy);
    }
}
