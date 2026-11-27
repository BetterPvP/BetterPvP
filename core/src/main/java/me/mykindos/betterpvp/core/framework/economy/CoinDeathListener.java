package me.mykindos.betterpvp.core.framework.economy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.mykindos.betterpvp.core.client.gamer.Gamer;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.config.Config;
import me.mykindos.betterpvp.core.framework.economy.ledger.Ledger;
import me.mykindos.betterpvp.core.framework.economy.ledger.LedgerParty;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilFormat;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import me.mykindos.betterpvp.ledger.LedgerAccounts;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

@BPvPListener
@Singleton
public class CoinDeathListener implements Listener {

    @Inject
    @Config(path = "clans.coins.percentCoinsDroppedOnDeath", defaultValue = "0.10")
    private double percentCoinsDroppedOnDeath;

    @Inject
    @Config(path = "clans.coins.dropCoinsOnDeath", defaultValue = "true")
    private boolean dropCoinsOnDeath;

    private final ClientManager clientManager;
    private final Ledger ledger;

    @Inject
    public CoinDeathListener(ClientManager clientManager, Ledger ledger) {
        this.clientManager = clientManager;
        this.ledger = ledger;
    }

    public ItemStack generateDrops(int coinAmount) {
        if (coinAmount <= 0) {
            return null; //dont drop any coins
        }

        CoinItem coinItem = CoinItem.SMALL_NUGGET;

        if (coinAmount >= 20000) {
            coinItem = CoinItem.BAR;
        } else if (coinAmount >= 5000) {
            coinItem = CoinItem.LARGE_NUGGET;
        }

        return coinItem.generateItem(coinAmount);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!dropCoinsOnDeath) {
            return;
        }

        Player player = event.getPlayer();
        final Gamer gamer = clientManager.search().online(player).getGamer();

        // Fire the CoinDropEvent to allow runes and other modifiers to adjust the drop amount
        final CoinDropEvent coinDropEvent = UtilServer.callEvent(new CoinDropEvent(player, gamer.getBalance()));

        // Calculate the final drop amount including all bonuses
        final int dropAmount = coinDropEvent.getTotalDropAmount(percentCoinsDroppedOnDeath);
        final long lost = Math.min(dropAmount, gamer.getBalance());
        if (lost <= 0) {
            return;
        }
        final Location location = player.getLocation();
        ledger.move(LedgerParty.of(gamer), LedgerParty.system(LedgerAccounts.WORLD), lost, "death.drop").thenAccept(receipt -> {
            if (!receipt.isPosted()) {
                return;
            }
            UtilMessage.message(player, "core.prefix.death", "core.economy.coins_lost",
                    Translations.component("core.economy.coins_amount", Component.text(UtilFormat.formatNumber(dropAmount))).color(NamedTextColor.YELLOW));

            ItemStack coinItem = generateDrops(dropAmount);
            if (coinItem != null) {
                location.getWorld().dropItemNaturally(location, coinItem);
            }
        });
    }
}
