package me.mykindos.betterpvp.clans.clans.core.vault;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import me.mykindos.betterpvp.clans.Clans;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.leveling.ClanPerkManager;
import me.mykindos.betterpvp.clans.clans.leveling.perk.model.ClanVaultSlot;
import me.mykindos.betterpvp.clans.clans.core.storage.ClanInventories;
import me.mykindos.betterpvp.clans.clans.core.storage.LockAnswer;
import me.mykindos.betterpvp.core.components.clans.data.ClanMember;
import me.mykindos.betterpvp.core.menu.Windowed;
import me.mykindos.betterpvp.core.utilities.UtilItem;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Represents a virtual inventory in which a clan can place items.
 */
@Setter
@Getter
public final class ClanVault {

    private final Clans clans;
    private final Clan clan;
    private @NotNull Map<Integer, @NotNull ItemStack> contents;
    private String lockedBy;

    public ClanVault(Clan clan) {
        this.clans = JavaPlugin.getPlugin(Clans.class);
        this.clan = clan;
        this.contents = new Int2ObjectOpenHashMap<>();
    }

    public boolean hasPermission(Player player) {
        return clan.getMember(player.getUniqueId()).hasRank(ClanMember.MemberRank.ADMIN);
    }

    public boolean isLocked() {
        return lockedBy != null;
    }

    public int getSize() {
        final int baseSize = clans.getConfig().getOrSaveInt("clans.clan.vault.base-size", 9);
        return baseSize + ClanPerkManager.getInstance().getPerks(clan).stream()
                .filter(ClanVaultSlot.class::isInstance)
                .map(ClanVaultSlot.class::cast)
                .mapToInt(ClanVaultSlot::getSlots)
                .sum();
    }

    @SneakyThrows
    public void read(String data) {
        contents.clear();
        contents = UtilItem.deserializeItemStackMap(data);
    }

    @SneakyThrows
    public String serialize() {
        return UtilItem.serializeItemStackMap(new Int2ObjectOpenHashMap<>(contents));
    }

    /** Shows the vault once no one else has it open, here or on another server. */
    public void show(Player player, Windowed previous) throws IllegalStateException {
        Preconditions.checkState(!isLocked(), "Clan vault is locked");
        lockedBy = player.getName();
        final ClanInventories inventories = clans.getInjector().getInstance(ClanInventories.class);
        inventories.open(clan, ClanInventories.Inventory.VAULT, player.getName()).thenAccept(answer -> {
            if (!answer.isTaken()) {
                lockedBy = null;
                refuse(player, answer);
                return;
            }
            if (!player.isOnline()) {
                lockedBy = null;
                inventories.close(clan, ClanInventories.Inventory.VAULT);
                return;
            }
            new GuiClanVault(player, this, previous).show(player).addCloseHandler(() -> {
                lockedBy = null;
                inventories.close(clan, ClanInventories.Inventory.VAULT);
            });
        });
    }

    private static void refuse(@NotNull Player player, @NotNull LockAnswer answer) {
        if (answer.getHolder() != null) {
            UtilMessage.message(player, "clans.prefix", "clans.core.vault.locked",
                    Component.text(answer.getHolder(), NamedTextColor.DARK_RED));
        } else {
            UtilMessage.message(player, "clans.prefix", "clans.core.storage.unavailable");
        }
    }

    public void show(Player player) throws IllegalStateException {
        show(player, null);
    }
}
