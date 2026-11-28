package me.mykindos.betterpvp.ledger;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Account names.
 * <p>
 * A player or clan account holds real coins and never goes below zero. Every other account stands for something
 * outside the economy, such as a shop or the world, and may go negative, so all balances together always add up to
 * zero.
 */
public final class LedgerAccounts {

    private static final String PLAYER_PREFIX = "player:";
    private static final String CLAN_PREFIX = "clan:";

    /** Where a player's first balance comes from. */
    public static final String OPENING = "system:opening";
    public static final String SHOP = "system:shop";
    public static final String AUCTION_HOUSE = "system:auction-house";
    /** Coins dropped into and picked up from the world. */
    public static final String WORLD = "system:world";
    public static final String LOOT = "system:loot";
    public static final String DAILY = "system:daily";
    public static final String ADMIN = "system:admin";
    public static final String CAMP = "system:camp";

    private LedgerAccounts() {
    }

    public static @NotNull String player(@NotNull UUID player) {
        return PLAYER_PREFIX + player;
    }

    public static @NotNull String clan(long clan) {
        return CLAN_PREFIX + clan;
    }

    public static boolean isPlayer(@NotNull String account) {
        return account.startsWith(PLAYER_PREFIX);
    }

    public static boolean isClan(@NotNull String account) {
        return account.startsWith(CLAN_PREFIX);
    }

    /** The clan id in a {@link #clan(long)} account name. */
    public static long clanOf(@NotNull String account) {
        return Long.parseLong(account.substring(CLAN_PREFIX.length()));
    }

    /** Whether the account holds coins someone owns: a player's or a clan's. */
    public static boolean isHeld(@NotNull String account) {
        return isPlayer(account) || isClan(account);
    }
}
