package me.mykindos.betterpvp.core.playerstate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Turns a {@link PlayerState} into the JSON document kept in the {@code player_states} table and back. */
@Singleton
public class PlayerStateCodec {

    private final ItemDataCodec itemCodec;

    @Inject
    public PlayerStateCodec(@NotNull ItemDataCodec itemCodec) {
        this.itemCodec = itemCodec;
    }

    public @NotNull String encode(@NotNull PlayerState state) {
        final JsonObject json = new JsonObject();
        json.add("inventory", encodeItems(state.getInventory()));
        json.add("enderChest", encodeItems(state.getEnderChest()));
        json.add("effects", encodeEffects(state.getEffects()));
        json.addProperty("health", state.getHealth());
        json.addProperty("foodLevel", state.getFoodLevel());
        json.addProperty("saturation", state.getSaturation());
        json.addProperty("exhaustion", state.getExhaustion());
        json.addProperty("level", state.getLevel());
        json.addProperty("exp", state.getExp());
        json.addProperty("gameMode", state.getGameMode().name());
        json.addProperty("heldSlot", state.getHeldSlot());

        final StoredPosition position = state.getPosition();
        if (position != null) {
            final JsonObject location = new JsonObject();
            location.addProperty("world", position.getWorld());
            location.addProperty("x", position.getX());
            location.addProperty("y", position.getY());
            location.addProperty("z", position.getZ());
            location.addProperty("yaw", position.getYaw());
            location.addProperty("pitch", position.getPitch());
            json.add("position", location);
        }
        return json.toString();
    }

    public @NotNull PlayerState decode(@NotNull UUID playerId, long version, @NotNull String document) {
        final JsonObject json = JsonParser.parseString(document).getAsJsonObject();
        final PlayerState.PlayerStateBuilder builder = PlayerState.builder()
                .playerId(playerId)
                .version(version)
                .inventory(decodeItems(json.getAsJsonObject("inventory")))
                .enderChest(decodeItems(json.getAsJsonObject("enderChest")))
                .effects(decodeEffects(json.getAsJsonArray("effects")))
                .health(json.get("health").getAsDouble())
                .foodLevel(json.get("foodLevel").getAsInt())
                .saturation(json.get("saturation").getAsFloat())
                .exhaustion(json.get("exhaustion").getAsFloat())
                .level(json.get("level").getAsInt())
                .exp(json.get("exp").getAsFloat())
                .gameMode(GameMode.valueOf(json.get("gameMode").getAsString()))
                .heldSlot(json.get("heldSlot").getAsInt());

        final JsonObject location = json.getAsJsonObject("position");
        if (location != null) {
            builder.position(new StoredPosition(location.get("world").getAsString(),
                    location.get("x").getAsDouble(),
                    location.get("y").getAsDouble(),
                    location.get("z").getAsDouble(),
                    location.get("yaw").getAsFloat(),
                    location.get("pitch").getAsFloat()));
        }
        return builder.build();
    }

    private JsonObject encodeItems(@NotNull Map<Integer, ItemStack> items) {
        final JsonObject json = new JsonObject();
        items.forEach((slot, stack) -> {
            final JsonObject item = itemCodec.encode(stack);
            if (item != null) {
                json.add(String.valueOf(slot), item);
            }
        });
        return json;
    }

    private Map<Integer, ItemStack> decodeItems(@NotNull JsonObject json) {
        final Map<Integer, ItemStack> items = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            final ItemStack stack = itemCodec.decode(entry.getValue().getAsJsonObject());
            if (stack != null) {
                items.put(Integer.parseInt(entry.getKey()), stack);
            }
        }
        return items;
    }

    private JsonArray encodeEffects(@NotNull List<StoredEffect> effects) {
        final JsonArray array = new JsonArray();
        for (StoredEffect effect : effects) {
            final JsonObject json = new JsonObject();
            json.addProperty("type", effect.getType());
            json.addProperty("name", effect.getName());
            json.addProperty("amplifier", effect.getAmplifier());
            json.addProperty("length", effect.getLength());
            json.addProperty("permanent", effect.isPermanent());
            json.addProperty("showParticles", effect.isShowParticles());
            array.add(json);
        }
        return array;
    }

    private List<StoredEffect> decodeEffects(@NotNull JsonArray array) {
        final List<StoredEffect> effects = new ArrayList<>();
        for (JsonElement element : array) {
            final JsonObject json = element.getAsJsonObject();
            effects.add(new StoredEffect(json.get("type").getAsString(),
                    json.get("name").getAsString(),
                    json.get("amplifier").getAsInt(),
                    json.get("length").getAsLong(),
                    json.get("permanent").getAsBoolean(),
                    json.get("showParticles").getAsBoolean()));
        }
        return effects;
    }
}
