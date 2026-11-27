package me.mykindos.betterpvp.clans.world.camp.settler.recruit;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.CustomLog;
import me.mykindos.betterpvp.core.client.gamer.Gamer;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.framework.economy.ledger.Ledger;
import me.mykindos.betterpvp.core.framework.economy.ledger.LedgerParty;
import me.mykindos.betterpvp.ledger.LedgerAccounts;
import me.mykindos.betterpvp.ledger.LedgerReceipt;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * A player's coins, for anything in a camp that costs them.
 * <p>
 * Answers at once. A ledger that answers later is trusted on the balance it last reported, and a refusal that
 * arrives after the fact is logged.
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

    /** Takes {@code amount} from {@code player}, or nothing and false if they do not have it. */
    public boolean take(@NotNull Player player, long amount) {
        if (amount <= 0) {
            return true;
        }
        final Gamer gamer = clientManager.search().online(player).getGamer();
        if (gamer.getBalance() < amount || !ledger.isAvailable()) {
            return false;
        }
        return landed(player, ledger.move(LedgerParty.of(gamer), LedgerParty.system(LedgerAccounts.CAMP), amount, "camp.spend"));
    }

    /** Hands {@code amount} back, such as when what it paid for fell through. */
    public void give(@NotNull Player player, long amount) {
        if (amount <= 0) {
            return;
        }
        final Gamer gamer = clientManager.search().online(player).getGamer();
        landed(player, ledger.move(LedgerParty.system(LedgerAccounts.CAMP), LedgerParty.of(gamer), amount, "camp.refund"));
    }

    private boolean landed(@NotNull Player player, @NotNull CompletableFuture<LedgerReceipt> answer) {
        if (answer.isDone()) {
            return answer.join().isPosted();
        }
        answer.thenAccept(receipt -> {
            if (!receipt.isPosted()) {
                log.warn("The ledger refused a camp payment for {}: {}", player.getName(), receipt.getStatus()).submit();
            }
        });
        return true;
    }
}
