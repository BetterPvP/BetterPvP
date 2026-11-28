package me.mykindos.betterpvp.core.trade;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.CustomLog;
import lombok.Value;
import me.mykindos.betterpvp.core.client.gamer.Gamer;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.utilities.UtilItem;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Performs the swap.
 * <p>
 * Everything that could refuse the trade is checked before anything moves. Currencies move first, while both
 * escrows are still held, and the session sits in {@link TradeState#SETTLING} so nothing can back out meanwhile.
 * If any currency fails to move, the ones that did are moved back and the trade is cancelled with both escrows
 * intact. Items are handed over only once every currency has landed, in one block on the main thread.
 */
@CustomLog
@Singleton
public class TradeSettlement {

    private final ClientManager clientManager;

    @Inject
    public TradeSettlement(ClientManager clientManager) {
        this.clientManager = clientManager;
    }

    /**
     * Settles the session if it still can be.
     *
     * @return completes on the main thread with {@code null} if both sides were paid, otherwise the reason the trade
     * must be cancelled. Nothing has moved when a reason is returned.
     */
    @NotNull
    public CompletableFuture<@Nullable TradeCancelReason> settle(@NotNull TradeSession session) {
        final TradeOffer initiatorOffer = session.getInitiator();
        final TradeOffer targetOffer = session.getTarget();

        final Player initiator = Bukkit.getPlayer(initiatorOffer.getPlayer());
        final Player target = Bukkit.getPlayer(targetOffer.getPlayer());
        if (initiator == null || target == null) {
            return CompletableFuture.completedFuture(TradeCancelReason.DISCONNECTED);
        }

        final Gamer initiatorGamer = clientManager.search().online(initiator).getGamer();
        final Gamer targetGamer = clientManager.search().online(target).getGamer();

        if (!canAfford(initiatorGamer, initiatorOffer) || !canAfford(targetGamer, targetOffer)) {
            return CompletableFuture.completedFuture(TradeCancelReason.INSUFFICIENT_FUNDS);
        }

        // Each player receives the other's items, so each is checked against the opposite offer.
        if (!hasRoomFor(initiator, targetOffer) || !hasRoomFor(target, initiatorOffer)) {
            return CompletableFuture.completedFuture(TradeCancelReason.NO_SPACE);
        }

        final List<Payment> payments = new ArrayList<>();
        payments(initiatorGamer, targetGamer, initiatorOffer, payments);
        payments(targetGamer, initiatorGamer, targetOffer, payments);

        session.setState(TradeState.SETTLING);
        return pay(payments, 0).thenCompose(paid -> {
            if (!paid) {
                session.setState(TradeState.ACKNOWLEDGING);
                return CompletableFuture.completedFuture(TradeCancelReason.INSUFFICIENT_FUNDS);
            }
            if (Bukkit.getPlayer(initiatorOffer.getPlayer()) == null || Bukkit.getPlayer(targetOffer.getPlayer()) == null) {
                return refund(payments, payments.size()).thenApply(ignored -> {
                    session.setState(TradeState.ACKNOWLEDGING);
                    return TradeCancelReason.DISCONNECTED;
                });
            }
            swapItems(session, initiator, target);
            return CompletableFuture.completedFuture(null);
        });
    }

    private void swapItems(@NotNull TradeSession session, @NotNull Player initiator, @NotNull Player target) {
        final TradeOffer initiatorOffer = session.getInitiator();
        final TradeOffer targetOffer = session.getTarget();
        final List<ItemStack> fromInitiator = initiatorOffer.drainItems();
        final List<ItemStack> fromTarget = targetOffer.drainItems();

        for (ItemStack item : fromInitiator) {
            UtilItem.insert(target, item);
        }
        for (ItemStack item : fromTarget) {
            UtilItem.insert(initiator, item);
        }

        session.setState(TradeState.SETTLED);
        log.info("{} traded with {} ({} item(s) and {} currencies for {} item(s) and {} currencies)",
                initiator.getName(), target.getName(),
                fromInitiator.size(), initiatorOffer.getCurrencies().size(),
                fromTarget.size(), targetOffer.getCurrencies().size()).submit();
    }

    private boolean canAfford(@NotNull Gamer gamer, @NotNull TradeOffer offer) {
        for (Map.Entry<TradeCurrency, Integer> entry : offer.getCurrencies().entrySet()) {
            if (entry.getKey().getBalance(gamer) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private void payments(@NotNull Gamer from, @NotNull Gamer to, @NotNull TradeOffer offer,
                          @NotNull List<Payment> into) {
        offer.getCurrencies().forEach((currency, amount) -> {
            if (amount > 0) {
                into.add(new Payment(currency, from, to, amount));
            }
        });
    }

    /** Makes the payments from {@code index} on, in order, moving back the ones that landed if a later one fails. */
    private @NotNull CompletableFuture<Boolean> pay(@NotNull List<Payment> payments, int index) {
        if (index == payments.size()) {
            return CompletableFuture.completedFuture(true);
        }
        final Payment payment = payments.get(index);
        return payment.getCurrency().transfer(payment.getFrom(), payment.getTo(), payment.getAmount()).thenCompose(paid -> {
            if (!paid) {
                return CompletableFuture.completedFuture(false);
            }
            return pay(payments, index + 1).thenCompose(rest -> rest
                    ? CompletableFuture.completedFuture(true)
                    : moveBack(payment).thenApply(ignored -> false));
        });
    }

    /** Moves back the first {@code count} payments, newest first. */
    private @NotNull CompletableFuture<Void> refund(@NotNull List<Payment> payments, int count) {
        CompletableFuture<Void> refunded = CompletableFuture.completedFuture(null);
        for (int i = count - 1; i >= 0; i--) {
            final Payment payment = payments.get(i);
            refunded = refunded.thenCompose(ignored -> moveBack(payment));
        }
        return refunded;
    }

    private @NotNull CompletableFuture<Void> moveBack(@NotNull Payment payment) {
        return payment.getCurrency().transfer(payment.getTo(), payment.getFrom(), payment.getAmount()).thenAccept(back -> {
            if (!back) {
                log.error("Could not move {} {} back from {} to {} after a failed trade", payment.getAmount(),
                        payment.getCurrency().getKey(), payment.getTo().getUuid(), payment.getFrom().getUuid()).submit();
            }
        });
    }

    /**
     * Deliberately counts whole free slots and ignores partial stacks that the incoming items could
     * merge into. Refusing a trade that would in fact have fitted is a message; letting one through
     * that does not fit means dropping traded goods on the floor at spawn.
     */
    private boolean hasRoomFor(@NotNull Player player, @NotNull TradeOffer incoming) {
        int free = 0;
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                free++;
            }
        }
        return free >= incoming.getOccupiedSlots();
    }

    @Value
    private static class Payment {
        TradeCurrency currency;
        Gamer from;
        Gamer to;
        int amount;
    }
}
