package me.mykindos.betterpvp.clans.clans.listeners;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.mykindos.betterpvp.clans.clans.ClanManager;
import me.mykindos.betterpvp.clans.clans.core.events.ClanCoreDestroyedEvent;
import me.mykindos.betterpvp.clans.clans.writer.ClanBank;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.config.Config;
import me.mykindos.betterpvp.core.framework.economy.CoinItem;
import me.mykindos.betterpvp.core.framework.economy.ledger.LedgerParty;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import me.mykindos.betterpvp.ledger.LedgerAccounts;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;

@BPvPListener
@Singleton
public class ClanBankListener extends ClanListener {

    @Inject
    @Config(path = "clans.bank.interestIntervalInHours", defaultValue = "24")
    private int interestIntervalInHours;

    @Inject
    @Config(path = "clans.bank.interestIntervalDeviation", defaultValue = "6")
    private int interestIntervalDeviation;

    @Inject
    @Config(path = "clans.bank.interestRate", defaultValue = "2.5")
    private double interestRate;

    private final ClanBank bank;

    @Inject
    public ClanBankListener(ClanManager clanManager, ClientManager clientManager, ClanBank bank) {
        super(clanManager, clientManager);
        this.bank = bank;
    }

    @EventHandler
    public void onClanCoreDestroyed(ClanCoreDestroyedEvent event) {
        int amount = event.getClan().getBalance();
        Location position = event.getClan().getCore().getPosition();
        if (amount <= 0 || position == null) return;
        final Location drop = position.clone().add(0, 1, 0);
        final LedgerParty world = LedgerParty.system(LedgerAccounts.WORLD);
        bank.move(bank.account(event.getClan()), world, amount, "clan.core-destroyed").thenAccept(receipt -> {
            if (receipt.isPosted()) {
                ItemStack coin = CoinItem.LARGE_NUGGET.generateItem(amount);
                drop.getWorld().dropItemNaturally(drop, coin);
            }
        });
    }

}
