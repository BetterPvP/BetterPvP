package me.mykindos.betterpvp.core.playerstate;

import lombok.Builder;
import lombok.Value;
import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A snapshot of everything a player carries between servers. Inventory slots follow Bukkit's player inventory
 * numbering, so armour and the offhand are included.
 */
@Value
@Builder
public class PlayerState {

    UUID playerId;
    long version;
    Map<Integer, ItemStack> inventory;
    Map<Integer, ItemStack> enderChest;
    List<StoredEffect> effects;
    double health;
    int foodLevel;
    float saturation;
    float exhaustion;
    int level;
    float exp;
    GameMode gameMode;
    int heldSlot;
    @Nullable StoredPosition position;
}
