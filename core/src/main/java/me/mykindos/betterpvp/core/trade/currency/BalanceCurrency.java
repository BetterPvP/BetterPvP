package me.mykindos.betterpvp.core.trade.currency;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.mykindos.betterpvp.core.client.gamer.Gamer;
import me.mykindos.betterpvp.core.client.gamer.properties.GamerProperty;
import me.mykindos.betterpvp.core.framework.economy.ledger.Ledger;
import me.mykindos.betterpvp.core.framework.economy.ledger.LedgerParty;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.trade.TradeCurrency;
import me.mykindos.betterpvp.ledger.LedgerReceipt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Gold, as held in {@link GamerProperty#BALANCE}.
 * <p>
 * This is the currency that makes the trade window a complete transaction: with it on the table a
 * gold-for-items deal settles in one commit, instead of being split across the window and a separate
 * payment that nothing can roll back.
 */
@Singleton
public class BalanceCurrency implements TradeCurrency {

    private final Ledger ledger;

    @Inject
    public BalanceCurrency(@NotNull Ledger ledger) {
        this.ledger = ledger;
    }

    @Override
    public @NotNull String getKey() {
        return "balance";
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Translations.component("core.trade.currency.balance").color(NamedTextColor.GOLD);
    }

    @Override
    public @NotNull Material getIcon() {
        return Material.GOLD_INGOT;
    }

    @Override
    public int getBalance(@NotNull Gamer gamer) {
        return gamer.getBalance();
    }

    @Override
    public @NotNull CompletableFuture<Boolean> transfer(@NotNull Gamer from, @NotNull Gamer to, int amount) {
        return ledger.move(LedgerParty.of(from), LedgerParty.of(to), amount, "trade").thenApply(LedgerReceipt::isPosted);
    }
}
