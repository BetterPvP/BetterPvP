package me.mykindos.betterpvp.ledger;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Account names.
 * <p>
 * A player account never goes below zero. Every other account stands for something outside the economy, such as a
 * shop or the world, and may go negative, so all balances together always add up to zero.
 */
public final class LedgerAccounts {

    private static final String PLAYER_PREFIX = "player:";

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

    public static boolean isPlayer(@NotNull String account) {
        return account.startsWith(PLAYER_PREFIX);
    }
}
