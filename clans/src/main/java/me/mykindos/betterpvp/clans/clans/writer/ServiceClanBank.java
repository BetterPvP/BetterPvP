package me.mykindos.betterpvp.clans.clans.writer;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.CustomLog;
import me.mykindos.betterpvp.clans.Clans;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.ClanManager;
import me.mykindos.betterpvp.clans.protocol.ClanBalance;
import me.mykindos.betterpvp.clans.protocol.ClanTopics;
import me.mykindos.betterpvp.core.framework.economy.ledger.Ledger;
import me.mykindos.betterpvp.core.framework.economy.ledger.LedgerParty;
import me.mykindos.betterpvp.core.framework.economy.ledger.Movement;
import me.mykindos.betterpvp.core.framework.net.BusMessage;
import me.mykindos.betterpvp.core.framework.net.MessageBus;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import me.mykindos.betterpvp.ledger.LedgerAccounts;
import me.mykindos.betterpvp.ledger.LedgerReceipt;
import me.mykindos.betterpvp.ledger.LedgerStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Posts to the ledger service, where every server's clan balances live.
 * <p>
 * A clan's account is opened with the balance this server has for it the first time it moves coins, which is how a
 * balance kept before the ledger moves into it. Nothing is posted for a clan whose account could not be opened,
 * since a posting would open it empty. After a posting lands, this server announces the clan balances the
 * ledger answered with, so every other server's cached balance follows.
 * <p>
 * The balance a clan loads with is only this server's last copy, so every clan's account is opened as soon as it
 * loads, which answers with the ledger's balance. A clan whose account could not be opened is tried again later.
 */
@CustomLog
@Singleton
public class ServiceClanBank implements ClanBank {

    private final Clans clans;
    private final Ledger ledger;
    private final MessageBus bus;
    private final ClanManager clanManager;
    private final Set<Long> opened = ConcurrentHashMap.newKeySet();
    private final Map<Long, Long> sequences = new ConcurrentHashMap<>();

    @Inject
    public ServiceClanBank(@NotNull Clans clans, @NotNull Ledger ledger, @NotNull MessageBus bus,
                           @NotNull ClanManager clanManager) {
        this.clans = clans;
        this.ledger = ledger;
        this.bus = bus;
        this.clanManager = clanManager;
        bus.subscribe(ClanTopics.BALANCE, this::onBalance);
    }

    @Override
    public @NotNull CompletableFuture<LedgerReceipt> post(@NotNull List<Movement> movements,
                                                          @Nullable String reference) {
        final List<CompletableFuture<Boolean>> openings = new ArrayList<>();
        for (Movement movement : movements) {
            open(movement.getFrom(), openings);
            open(movement.getTo(), openings);
        }
        return CompletableFuture.allOf(openings.toArray(CompletableFuture[]::new))
                .thenCompose(ignored -> openings.stream().allMatch(CompletableFuture::join)
                        ? ledger.post(movements, reference)
                        : CompletableFuture.completedFuture(LedgerReceipt.of(LedgerStatus.UNAVAILABLE)))
                .thenApply(receipt -> {
                    if (receipt.isPosted()) {
                        announce(receipt);
                    }
                    return receipt;
                });
    }

    @Override
    public void sync(@NotNull Collection<Clan> loaded) {
        for (Clan clan : List.copyOf(loaded)) {
            final List<CompletableFuture<Boolean>> openings = new ArrayList<>();
            open(account(clan), openings);
            openings.forEach(opening -> opening.thenAccept(opened -> {
                if (!opened && clans.isEnabled()) {
                    UtilServer.runTaskLater(clans, () -> clanManager.getClanById(clan.getId())
                            .ifPresent(current -> sync(List.of(current))), 20L * 30);
                }
            }));
        }
    }

    private void open(@NotNull LedgerParty party, @NotNull List<CompletableFuture<Boolean>> openings) {
        if (!LedgerAccounts.isClan(party.getAccount())) {
            return;
        }
        final long clan = LedgerAccounts.clanOf(party.getAccount());
        if (!opened.contains(clan)) {
            openings.add(ledger.open(party).thenApply(receipt -> {
                if (receipt.isPosted()) {
                    opened.add(clan);
                    sequences.merge(clan, receipt.getSequence(), Math::max);
                }
                return receipt.isPosted();
            }));
        }
    }

    private void announce(@NotNull LedgerReceipt receipt) {
        receipt.getBalances().forEach((account, balance) -> {
            if (LedgerAccounts.isClan(account)) {
                final long clan = LedgerAccounts.clanOf(account);
                sequences.merge(clan, receipt.getSequence(), Math::max);
                bus.publish(BusMessage.of(ClanTopics.BALANCE,
                        new ClanBalance(clan, balance, receipt.getSequence()).toPayload()));
            }
        });
    }

    private void onBalance(@NotNull BusMessage message) {
        final ClanBalance balance;
        try {
            balance = ClanBalance.fromPayload(message.getPayload());
        } catch (RuntimeException ex) {
            log.warn("Ignored an unreadable clan balance {}", message.getPayload()).submit();
            return;
        }
        UtilServer.runTask(clans, () -> {
            final Long seen = sequences.get(balance.getClan());
            if (seen != null && seen >= balance.getSequence()) {
                return;
            }
            sequences.put(balance.getClan(), balance.getSequence());
            clanManager.getClanById(balance.getClan()).ifPresent(clan ->
                    clan.setBalance((int) Math.min(Integer.MAX_VALUE, balance.getBalance())));
        });
    }
}
