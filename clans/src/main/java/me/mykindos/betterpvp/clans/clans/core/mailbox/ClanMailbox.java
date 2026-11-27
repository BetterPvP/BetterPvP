package me.mykindos.betterpvp.clans.clans.core.mailbox;

import com.google.common.base.Preconditions;
import lombok.CustomLog;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import me.mykindos.betterpvp.clans.Clans;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.core.storage.ClanInventories;
import me.mykindos.betterpvp.core.item.ItemFactory;
import me.mykindos.betterpvp.core.menu.Windowed;
import me.mykindos.betterpvp.core.utilities.UtilItem;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * Represents a virtual inventory in which a clan can place items.
 */
@Setter
@Getter
@CustomLog
public final class ClanMailbox {

    private final Clan clan;
    private @NotNull ArrayList<ItemStack> contents;
    private String lockedBy;

    public ClanMailbox(Clan clan) {
        this.clan = clan;
        this.contents = new ArrayList<>();
    }

    public boolean isLocked() {
        return lockedBy != null;
    }

    @SneakyThrows
    public void read(String data) {
        contents.clear();
        contents.addAll(UtilItem.deserializeItemStackList(data));
    }

    @SneakyThrows
    public String serialize() {
        return UtilItem.serializeItemStackList(new ArrayList<>(contents));
    }

    /** Shows the mailbox once no one else has it open, here or on another server. */
    public void show(Player player, ItemFactory itemFactory, Windowed previous) throws IllegalStateException {
        Preconditions.checkState(!isLocked(), "Clan mailbox is locked");
        lockedBy = player.getName();
        final ClanInventories inventories = JavaPlugin.getPlugin(Clans.class).getInjector()
                .getInstance(ClanInventories.class);
        inventories.open(clan, ClanInventories.Inventory.MAILBOX, player.getName()).thenAccept(answer -> {
            if (!answer.isTaken()) {
                lockedBy = null;
                if (answer.getHolder() != null) {
                    UtilMessage.message(player, "clans.prefix", "clans.core.mailbox.locked",
                            Component.text(answer.getHolder(), NamedTextColor.DARK_RED));
                } else {
                    UtilMessage.message(player, "clans.prefix", "clans.core.storage.unavailable");
                }
                return;
            }
            if (!player.isOnline()) {
                close(inventories);
                return;
            }
            new GuiClanMailbox(this, itemFactory, previous).show(player).addCloseHandler(() -> close(inventories));
        });
    }

    private void close(@NotNull ClanInventories inventories) {
        inventories.close(clan, ClanInventories.Inventory.MAILBOX).whenComplete((unused, throwable) -> {
            if (throwable != null) {
                log.error("Failed to update clan mailbox", throwable).submit();
                return;
            }

            lockedBy = null;
        });
    }

    public void show(Player player, ItemFactory itemFactory) throws IllegalStateException {
        show(player, itemFactory, null);
    }
}
