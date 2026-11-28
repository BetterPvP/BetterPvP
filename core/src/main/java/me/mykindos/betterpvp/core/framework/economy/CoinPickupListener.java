package me.mykindos.betterpvp.core.framework.economy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.mykindos.betterpvp.core.client.gamer.Gamer;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.framework.CoreNamespaceKeys;
import me.mykindos.betterpvp.core.framework.economy.ledger.Ledger;
import me.mykindos.betterpvp.core.framework.economy.ledger.LedgerParty;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import me.mykindos.betterpvp.core.utilities.UtilFormat;
import me.mykindos.betterpvp.core.utilities.model.SoundEffect;
import me.mykindos.betterpvp.core.utilities.model.display.component.TimedComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import me.mykindos.betterpvp.ledger.LedgerAccounts;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;
import java.util.OptionalInt;

@BPvPListener
@Singleton
public class CoinPickupListener implements Listener {

    /**
     * Notifies a player with an action-bar message and sound when they receive coins.
     */
    public static void notify(Gamer gamer, int coins) {
        final Player player = Objects.requireNonNull(gamer.getPlayer());
        new SoundEffect(Sound.ENTITY_ARROW_HIT_PLAYER, 2.0f, 2f).play(player);
        final TextComponent text = Component.text("+" + UtilFormat.formatNumber(coins) + " Coins!", TextColor.color(255, 215, 0));
        gamer.getActionBar().add(5, new TimedComponent(2, true, gmr -> text));
    }


    private final ClientManager clientManager;
    private final Ledger ledger;

    @Inject
    public CoinPickupListener(ClientManager clientManager, Ledger ledger) {
        this.clientManager = clientManager;
        this.ledger = ledger;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPickup(InventoryPickupItemEvent event) {
        final PersistentDataContainer pdc = event.getItem().getItemStack().getItemMeta().getPersistentDataContainer();
        if (pdc.has(CoreNamespaceKeys.COIN_AMOUNT, PersistentDataType.INTEGER)) {
            event.setCancelled(true);
            //Stop hoppers from picking up coins
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPickup(EntityPickupItemEvent event) {

        if (event.isCancelled()) {
            return;
        }

        ItemStack itemStack = event.getItem().getItemStack();
        final OptionalInt coinsOpt = CoinItem.getCoinAmount(itemStack);
        if (coinsOpt.isEmpty()) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            event.setCancelled(true); // Only players can pick up coins
            return;
        }

        event.setCancelled(true);
        // Coins stay on the ground while they could not be credited.
        if (!ledger.isAvailable()) {
            return;
        }

        final Gamer gamer = this.clientManager.search().online(player).getGamer();
        final int coins = coinsOpt.getAsInt();
        event.getItem().remove();

        ledger.move(LedgerParty.system(LedgerAccounts.WORLD), LedgerParty.of(gamer), coins, "coin.pickup").thenAccept(receipt -> {
            if (receipt.isPosted() && player.isOnline()) {
                notify(gamer, coins);
            }
        });
    }

}
