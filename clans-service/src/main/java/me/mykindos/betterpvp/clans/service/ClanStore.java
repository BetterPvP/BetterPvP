package me.mykindos.betterpvp.clans.service;

import me.mykindos.betterpvp.clans.protocol.ClanMutation;
import me.mykindos.betterpvp.clans.protocol.ClanOp;
import me.mykindos.betterpvp.clans.protocol.ClanRanks;
import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Writes clan mutations to Postgres, each in its own transaction. Callers apply one mutation at a time, which is what
 * makes concurrent edits from different servers land in the order they arrived.
 */
public class ClanStore {

    private final DataSource dataSource;

    public ClanStore(@NotNull DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void apply(@NotNull ClanMutation mutation) throws ClanRefusal, SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                write(connection, mutation);
                connection.commit();
            } catch (ClanRefusal | SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            }
        }
    }

    private void write(@NotNull Connection connection, @NotNull ClanMutation mutation) throws ClanRefusal, SQLException {
        final long clan = mutation.getClan();
        if (mutation.getOp() != ClanOp.CREATE) {
            requireClan(connection, clan);
        }

        switch (mutation.getOp()) {
            case CREATE -> create(connection, mutation);
            case DISBAND -> update(connection, "DELETE FROM clans WHERE id = ?", clan);
            case RENAME -> update(connection, "UPDATE clans SET name = ? WHERE id = ?", required(mutation.getName()), clan);
            case MEMBER_ADD -> addMember(connection, clan, required(mutation.getMember()), required(mutation.getRank()));
            case MEMBER_REMOVE -> update(connection, "DELETE FROM clan_members WHERE clan = ? AND member = ?",
                    clan, required(mutation.getMember()).toString());
            case MEMBER_PROMOTE -> stepRank(connection, clan, required(mutation.getMember()), 1);
            case MEMBER_DEMOTE -> stepRank(connection, clan, required(mutation.getMember()), -1);
            case ALLY -> pair(connection, mutation, """
                    INSERT INTO clan_alliances (clan, ally_clan, trusted) VALUES (?, ?, 0)
                    ON CONFLICT (clan, ally_clan) DO NOTHING""");
            case UNALLY -> pair(connection, mutation, "DELETE FROM clan_alliances WHERE clan = ? AND ally_clan = ?");
            case TRUST -> trust(connection, mutation);
            case ENEMY -> pair(connection, mutation, """
                    INSERT INTO clan_enemies (clan, enemy_clan, dominance) VALUES (?, ?, 0)
                    ON CONFLICT (clan, enemy_clan) DO NOTHING""");
            case UNENEMY -> pair(connection, mutation, "DELETE FROM clan_enemies WHERE clan = ? AND enemy_clan = ?");
            case CAMP_PERMISSION -> update(connection, """
                            INSERT INTO clan_camp_permissions (clan, scope, rank, action, allowed) VALUES (?, ?, ?, ?, ?)
                            ON CONFLICT (clan, scope, rank, action) DO UPDATE SET allowed = EXCLUDED.allowed""",
                    clan, required(mutation.getScope()), Objects.requireNonNullElse(mutation.getRank(), ""),
                    Objects.requireNonNullElse(mutation.getAction(), ""), Boolean.TRUE.equals(mutation.getFlag()));
            case PROPERTIES -> properties(connection, clan, required(mutation.getProperties()));
            case CORE -> update(connection, "UPDATE clans SET home = ? WHERE id = ?",
                    Objects.requireNonNullElse(mutation.getValue(), ""), clan);
            case BANNER -> update(connection, "UPDATE clan_metadata SET banner = ? WHERE clan = ?",
                    required(mutation.getValue()), clan);
            case VAULT -> update(connection, "UPDATE clan_metadata SET vault = ? WHERE clan = ?",
                    required(mutation.getValue()), clan);
            case MAILBOX -> update(connection, "UPDATE clan_metadata SET mailbox = ? WHERE clan = ?",
                    required(mutation.getValue()), clan);
            case CLAIM -> claim(connection, clan, required(mutation.getValue()));
            case UNCLAIM -> update(connection, "DELETE FROM clan_territory WHERE clan = ? AND chunk = ?",
                    clan, required(mutation.getValue()));
            case DOMINANCE -> dominance(connection, clan, required(mutation.getOther()), required(mutation.getValue()));
            case DOMINANCE_KILL -> killDominance(connection, clan, required(mutation.getOther()),
                    required(mutation.getValue()));
        }
    }

    private void properties(@NotNull Connection connection, long clan, @NotNull Map<String, String> properties)
            throws SQLException {
        for (Map.Entry<String, String> property : properties.entrySet()) {
            update(connection, """
                    INSERT INTO clan_properties (clan, property, value) VALUES (?, ?, ?)
                    ON CONFLICT (clan, property) DO UPDATE SET value = EXCLUDED.value""",
                    clan, property.getKey(), property.getValue());
        }
    }

    private void claim(@NotNull Connection connection, long clan, @NotNull String chunk)
            throws ClanRefusal, SQLException {
        final boolean taken = exists(connection, """
                        SELECT 1 FROM clan_territory t JOIN clans c ON c.id = t.clan
                        WHERE t.chunk = ? AND t.clan <> ? AND c.realm = (SELECT realm FROM clans WHERE id = ?)""",
                chunk, clan, clan);
        if (taken) {
            throw new ClanRefusal(chunk + " is claimed by another clan");
        }
        update(connection, """
                INSERT INTO clan_territory (clan, chunk) VALUES (?, ?)
                ON CONFLICT (clan, chunk) DO NOTHING""", clan, chunk);
    }

    private void dominance(@NotNull Connection connection, long clan, long other, @NotNull String value)
            throws ClanRefusal, SQLException {
        final int dominance;
        try {
            dominance = Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new ClanRefusal("Dominance " + value + " is not a number");
        }
        final int changed = update(connection,
                "UPDATE clan_enemies SET dominance = ? WHERE clan = ? AND enemy_clan = ?", dominance, clan, other);
        if (changed == 0) {
            throw new ClanRefusal("Clans " + clan + " and " + other + " are not enemies");
        }
    }

    private void killDominance(@NotNull Connection connection, long killer, long killed, @NotNull String value)
            throws ClanRefusal, SQLException {
        final double amount;
        try {
            amount = Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            throw new ClanRefusal("Dominance " + value + " is not a number");
        }
        final int killerOver = dominanceOver(connection, killer, killed);
        final int killedOver = dominanceOver(connection, killed, killer);
        final double gained = killedOver == 0 ? Math.min(100, killerOver + amount) : killerOver;
        final double lost = Math.max(0, killedOver - amount);
        update(connection, "UPDATE clan_enemies SET dominance = ? WHERE clan = ? AND enemy_clan = ?",
                (int) gained, killer, killed);
        update(connection, "UPDATE clan_enemies SET dominance = ? WHERE clan = ? AND enemy_clan = ?",
                (int) lost, killed, killer);
    }

    private int dominanceOver(@NotNull Connection connection, long clan, long other) throws ClanRefusal, SQLException {
        try (PreparedStatement select = prepare(connection,
                "SELECT dominance FROM clan_enemies WHERE clan = ? AND enemy_clan = ? FOR UPDATE", clan, other);
             ResultSet result = select.executeQuery()) {
            if (!result.next()) {
                throw new ClanRefusal("Clans " + clan + " and " + other + " are not enemies");
            }
            return result.getInt(1);
        }
    }

    private void create(@NotNull Connection connection, @NotNull ClanMutation mutation) throws ClanRefusal, SQLException {
        final long clan = mutation.getClan();
        final int realm = required(mutation.getRealm());
        final String name = required(mutation.getName());
        if (exists(connection, "SELECT 1 FROM clans WHERE realm = ? AND LOWER(name) = LOWER(?)", realm, name)) {
            throw new ClanRefusal("A clan named " + name + " already exists");
        }
        update(connection, "INSERT INTO clans (id, realm, name) VALUES (?, ?, ?)", clan, realm, name);
        update(connection, "INSERT INTO clan_metadata (clan, banner, vault, mailbox) VALUES (?, '', '', '')", clan);
        if (mutation.getMember() != null) {
            addMember(connection, clan, mutation.getMember(), ClanRanks.ORDER.getLast());
        }
    }

    private void addMember(@NotNull Connection connection, long clan, @NotNull UUID member, @NotNull String rank)
            throws ClanRefusal, SQLException {
        final boolean elsewhere = exists(connection, """
                        SELECT 1 FROM clan_members m JOIN clans c ON c.id = m.clan
                        WHERE m.member = ? AND m.clan <> ? AND c.realm = (SELECT realm FROM clans WHERE id = ?)""",
                member.toString(), clan, clan);
        if (elsewhere) {
            throw new ClanRefusal(member + " is already in another clan");
        }
        update(connection, """
                INSERT INTO clan_members (clan, member, rank) VALUES (?, ?, ?)
                ON CONFLICT (clan, member) DO UPDATE SET rank = EXCLUDED.rank""", clan, member.toString(), rank);
    }

    private void stepRank(@NotNull Connection connection, long clan, @NotNull UUID member, int steps)
            throws ClanRefusal, SQLException {
        final String current;
        try (PreparedStatement select = prepare(connection,
                "SELECT rank FROM clan_members WHERE clan = ? AND member = ? FOR UPDATE", clan, member.toString());
             ResultSet result = select.executeQuery()) {
            if (!result.next()) {
                throw new ClanRefusal(member + " is not in clan " + clan);
            }
            current = result.getString(1);
        }
        update(connection, "UPDATE clan_members SET rank = ? WHERE clan = ? AND member = ?",
                ClanRanks.step(current, steps), clan, member.toString());
    }

    private void trust(@NotNull Connection connection, @NotNull ClanMutation mutation) throws ClanRefusal, SQLException {
        final long clan = mutation.getClan();
        final long other = requireClan(connection, required(mutation.getOther()));
        final int trusted = Boolean.TRUE.equals(mutation.getFlag()) ? 1 : 0;
        final String sql = "UPDATE clan_alliances SET trusted = ? WHERE clan = ? AND ally_clan = ?";
        final int changed = update(connection, sql, trusted, clan, other) + update(connection, sql, trusted, other, clan);
        if (changed == 0) {
            throw new ClanRefusal("Clans " + clan + " and " + other + " are not allied");
        }
    }

    /** Runs {@code sql} for the relation both ways, since every relation is stored once from each side. */
    private void pair(@NotNull Connection connection, @NotNull ClanMutation mutation, @NotNull String sql)
            throws ClanRefusal, SQLException {
        final long other = requireClan(connection, required(mutation.getOther()));
        update(connection, sql, mutation.getClan(), other);
        update(connection, sql, other, mutation.getClan());
    }

    private long requireClan(@NotNull Connection connection, long clan) throws ClanRefusal, SQLException {
        if (!exists(connection, "SELECT 1 FROM clans WHERE id = ?", clan)) {
            throw new ClanRefusal("No clan " + clan);
        }
        return clan;
    }

    private static <T> @NotNull T required(T value) throws ClanRefusal {
        if (value == null) {
            throw new ClanRefusal("The mutation is missing a field its op needs");
        }
        return value;
    }

    private static boolean exists(@NotNull Connection connection, @NotNull String sql, Object... arguments)
            throws SQLException {
        try (PreparedStatement statement = prepare(connection, sql, arguments);
             ResultSet result = statement.executeQuery()) {
            return result.next();
        }
    }

    private static int update(@NotNull Connection connection, @NotNull String sql, Object... arguments)
            throws SQLException {
        try (PreparedStatement statement = prepare(connection, sql, arguments)) {
            return statement.executeUpdate();
        }
    }

    private static @NotNull PreparedStatement prepare(@NotNull Connection connection, @NotNull String sql,
                                                      Object... arguments) throws SQLException {
        final PreparedStatement statement = connection.prepareStatement(sql);
        for (int i = 0; i < arguments.length; i++) {
            statement.setObject(i + 1, arguments[i]);
        }
        return statement;
    }
}
