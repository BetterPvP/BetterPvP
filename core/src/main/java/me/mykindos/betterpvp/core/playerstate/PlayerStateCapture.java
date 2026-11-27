package me.mykindos.betterpvp.core.playerstate;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.CustomLog;
import me.mykindos.betterpvp.core.effects.Effect;
import me.mykindos.betterpvp.core.effects.EffectManager;
import me.mykindos.betterpvp.core.effects.EffectType;
import me.mykindos.betterpvp.core.effects.EffectTypes;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Reads a {@link PlayerState} off an online player and puts one back onto them. Runs on the main thread. */
@CustomLog
@Singleton
public class PlayerStateCapture {

    private final EffectManager effectManager;

    @Inject
    public PlayerStateCapture(@NotNull EffectManager effectManager) {
        this.effectManager = effectManager;
    }

    public @NotNull PlayerState capture(@NotNull Player player, long version) {
        final Location location = player.getLocation();
        return PlayerState.builder()
                .playerId(player.getUniqueId())
                .version(version)
                .inventory(read(player.getInventory()))
                .enderChest(read(player.getEnderChest()))
                .effects(captureEffects(player))
                .health(player.getHealth())
                .foodLevel(player.getFoodLevel())
                .saturation(player.getSaturation())
                .exhaustion(player.getExhaustion())
                .level(player.getLevel())
                .exp(player.getExp())
                .gameMode(player.getGameMode())
                .heldSlot(player.getInventory().getHeldItemSlot())
                .position(new StoredPosition(location.getWorld().getName(),
                        location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch()))
                .build();
    }

    public void apply(@NotNull Player player, @NotNull PlayerState state) {
        write(player.getInventory(), state.getInventory());
        write(player.getEnderChest(), state.getEnderChest());
        player.getInventory().setHeldItemSlot(state.getHeldSlot());
        applyEffects(player, state.getEffects());

        final AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        final double cap = maxHealth == null ? state.getHealth() : maxHealth.getValue();
        player.setHealth(Math.min(state.getHealth(), cap));
        player.setFoodLevel(state.getFoodLevel());
        player.setSaturation(state.getSaturation());
        player.setExhaustion(state.getExhaustion());
        player.setLevel(state.getLevel());
        player.setExp(state.getExp());
        player.setGameMode(state.getGameMode());

        final StoredPosition position = state.getPosition();
        if (position == null) {
            return;
        }
        final World world = Bukkit.getWorld(position.getWorld());
        if (world == null) {
            log.warn("Not moving {}, world {} is not loaded", player.getName(), position.getWorld()).submit();
            return;
        }
        player.teleport(new Location(world, position.getX(), position.getY(), position.getZ(), position.getYaw(), position.getPitch()));
    }

    private Map<Integer, ItemStack> read(@NotNull Inventory inventory) {
        final Map<Integer, ItemStack> items = new HashMap<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            final ItemStack stack = inventory.getItem(slot);
            if (stack != null && !stack.isEmpty()) {
                items.put(slot, stack.clone());
            }
        }
        return items;
    }

    private void write(@NotNull Inventory inventory, @NotNull Map<Integer, ItemStack> items) {
        inventory.clear();
        items.forEach((slot, stack) -> {
            if (slot < inventory.getSize()) {
                inventory.setItem(slot, stack.clone());
            }
        });
    }

    // Effects with a removal predicate are bound to something on this server, so they stay behind
    private List<StoredEffect> captureEffects(@NotNull Player player) {
        final List<StoredEffect> effects = new ArrayList<>();
        for (Effect effect : effectManager.getAllEffects(player.getUniqueId())) {
            if (effect.getRemovalPredicate() != null || effect.hasExpired()) {
                continue;
            }
            final long length = effect.getRawLength() < 0 ? effect.getRawLength() - 50 : effect.getRemainingDuration();
            effects.add(new StoredEffect(effect.getEffectType().getName(), effect.getName(), effect.getAmplifier(),
                    length, effect.isPermanent(), effect.isShowParticles()));
        }
        return effects;
    }

    private void applyEffects(@NotNull Player player, @NotNull List<StoredEffect> effects) {
        effectManager.removeAllEffects(player);
        for (StoredEffect effect : effects) {
            final Optional<EffectType> type = EffectTypes.getEffectTypeByName(effect.getType());
            if (type.isEmpty()) {
                log.warn("Dropping saved effect {} of {}, its type is not registered", effect.getType(), player.getName()).submit();
                continue;
            }
            effectManager.addEffect(player, null, type.get(), effect.getName(), effect.getAmplifier(), effect.getLength(),
                    false, effect.isPermanent(), effect.isShowParticles(), null);
        }
    }
}
