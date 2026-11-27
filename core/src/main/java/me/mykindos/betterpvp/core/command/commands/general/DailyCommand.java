package me.mykindos.betterpvp.core.command.commands.general;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.gamer.Gamer;
import me.mykindos.betterpvp.core.command.Command;
import me.mykindos.betterpvp.core.config.Config;
import me.mykindos.betterpvp.core.cooldowns.CooldownManager;
import me.mykindos.betterpvp.core.framework.economy.ledger.Ledger;
import me.mykindos.betterpvp.core.framework.economy.ledger.LedgerParty;
import me.mykindos.betterpvp.core.utilities.UtilFormat;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import me.mykindos.betterpvp.ledger.LedgerAccounts;
import org.bukkit.entity.Player;

@Singleton
public class DailyCommand extends Command {

    @Inject
    @Config(path = "daily.coinsAmount", defaultValue = "5000")
    private int coinsAmount;

    public static String DAILY = "Daily";

    private final CooldownManager cooldownManager;
    private final Ledger ledger;

    @Inject
    public DailyCommand(CooldownManager cooldownManager, Ledger ledger) {
        this.cooldownManager = cooldownManager;
        this.ledger = ledger;
    }

    @Override
    public String getName() {
        return "daily";
    }

    @Override
    public String getDescription() {
        return "core.command.daily.description";
    }

    @Override
    public void execute(Player player, Client client, String... args) {
        if (!ledger.isAvailable()) {
            UtilMessage.message(player, COMMAND_PREFIX, "core.ledger.unavailable");
            return;
        }
        if(cooldownManager.use(player, DAILY, 86400000, false, false)) {
            Gamer gamer = client.getGamer();

            ledger.move(LedgerParty.system(LedgerAccounts.DAILY), LedgerParty.of(gamer), coinsAmount, "daily").thenAccept(receipt -> {
                if (receipt.isPosted()) {
                    UtilMessage.message(player, COMMAND_PREFIX, "core.command.daily.claimed",
                            Component.text(UtilFormat.formatNumber(coinsAmount)).color(NamedTextColor.GREEN));
                } else {
                    UtilMessage.message(player, COMMAND_PREFIX, "core.ledger.unavailable");
                }
            });

        } else {
            UtilMessage.message(player, COMMAND_PREFIX, "core.command.daily.already");
        }
    }
}
