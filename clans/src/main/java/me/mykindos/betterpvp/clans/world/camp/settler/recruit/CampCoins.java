package me.mykindos.betterpvp.clans.world.camp.settler.recruit;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.CustomLog;
import me.mykindos.betterpvp.clans.economy.PostingRetry;
import me.mykindos.betterpvp.core.client.gamer.Gamer;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.framework.economy.ledger.Ledger;
import me.mykindos.betterpvp.core.framework.economy.ledger.LedgerParty;
import me.mykindos.betterpvp.core.framework.economy.ledger.Movement;
import me.mykindos.betterpvp.ledger.LedgerAccounts;
import me.mykindos.betterpvp.ledger.LedgerReceipt;
import me.mykindos.betterpvp.ledger.LedgerStatus;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A player's coins, for anything in a camp that costs them.
 * <p>
 * A payment counts once the ledger has taken it, and anything bought with it is granted only then. A ledger that
 * answers at once answers in the same tick. A payment the ledger could not be asked about is refused, then sent again
 * under its reference until the ledger answers, and handed back if it turns out to have landed.
 */
@Singleton
@CustomLog
public class CampCoins {

    private final ClientManager clientManager;
    private final Ledger ledger;

    @Inject
    public CampCoins(@NotNull ClientManager clientManager, @NotNull Ledger ledger) {
        this.clientManager = clientManager;
        this.ledger = ledger;
    }

    /** Takes {@code amount} from {@code player}, answering false and taking nothing if they do not have it. */
    public @NotNull CompletableFuture<Boolean> take(@NotNull Player player, long amount) {
        return take(player, amount, "camp.spend");
    }

    /** Takes {@code amount} from {@code player} for {@code reason}, as {@link #take(Player, long)} does. */
    public @NotNull CompletableFuture<Boolean> take(@NotNull Player player, long amount, @NotNull String reason) {
        if (amount <= 0) {
            return CompletableFuture.completedFuture(true);
        }
        final Gamer gamer = clientManager.search().online(player).getGamer();
        if (gamer.getBalance() < amount || !ledger.isAvailable()) {
            return CompletableFuture.completedFuture(false);
        }
        final Movement payment = new Movement(LedgerParty.of(gamer), LedgerParty.system(LedgerAccounts.CAMP), amount,
                reason);
        final String reference = reason + ":" + UUID.randomUUID();
        return ledger.post(List.of(payment), reference).thenApply(receipt -> {
            if (receipt.getStatus() == LedgerStatus.UNAVAILABLE) {
                settle(player, gamer, payment, reference);
            }
            return receipt.isPosted();
        });
    }

    /** Hands {@code amount} back, such as when what it paid for fell through. */
    public void give(@NotNull Player player, long amount) {
        if (amount <= 0) {
            return;
        }
        final Gamer gamer = clientManager.search().online(player).getGamer();
        refund(player, new Movement(LedgerParty.system(LedgerAccounts.CAMP), LedgerParty.of(gamer), amount,
                "camp.refund"), "camp.refund:" + UUID.randomUUID());
    }

    private void settle(@NotNull Player player, @NotNull Gamer gamer, @NotNull Movement payment,
                        @NotNull String reference) {
        log.warn("The ledger did not answer a camp payment {} of {} for {}, asking again", reference,
                payment.getAmount(), player.getName()).submit();
        PostingRetry.untilAnswered(() -> ledger.post(List.of(payment), reference)).thenAccept(receipt -> {
            if (receipt.isPosted()) {
                refund(player, new Movement(LedgerParty.system(LedgerAccounts.CAMP), LedgerParty.of(gamer),
                        payment.getAmount(), "camp.refund"), reference + ":refund");
            }
        });
    }

    private void refund(@NotNull Player player, @NotNull Movement refund, @NotNull String reference) {
        final CompletableFuture<LedgerReceipt> answer = ledger.post(List.of(refund), reference)
                .thenCompose(receipt -> receipt.getStatus() == LedgerStatus.UNAVAILABLE
                        ? PostingRetry.untilAnswered(() -> ledger.post(List.of(refund), reference))
                        : CompletableFuture.completedFuture(receipt));
        answer.thenAccept(receipt -> {
            if (!receipt.isPosted()) {
                log.error("The ledger refused a camp refund {} of {} for {}: {}", reference, refund.getAmount(),
                        player.getName(), receipt.getStatus()).submit();
            }
        });
    }
}
