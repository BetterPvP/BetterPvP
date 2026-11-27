package me.mykindos.betterpvp.core.playerstate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.papermc.paper.persistence.PersistentDataContainerView;
import lombok.CustomLog;
import me.mykindos.betterpvp.core.framework.CoreNamespaceKeys;
import me.mykindos.betterpvp.core.item.BaseItem;
import me.mykindos.betterpvp.core.item.ItemFactory;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Writes items as their item key, amount and persistent data. The item is rebuilt from its registered model, so
 * everything the custom item system keeps (components, uuid, properties) survives, while vanilla item data does not.
 * Each persistent data value is stored with a tag naming its type, so it is written back with the same type.
 */
@CustomLog
@Singleton
public class ItemDataCodec {

    private final ItemFactory itemFactory;

    @Inject
    public ItemDataCodec(@NotNull ItemFactory itemFactory) {
        this.itemFactory = itemFactory;
    }

    /**
     * @return the encoded item, or null for an empty slot
     */
    public @Nullable JsonObject encode(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        final PersistentDataContainerView data = stack.getPersistentDataContainer();
        final String key = data.get(CoreNamespaceKeys.CUSTOM_ITEM_KEY, PersistentDataType.STRING);
        final JsonObject json = new JsonObject();
        json.addProperty("item", key != null ? key : stack.getType().getKey().toString());
        json.addProperty("amount", stack.getAmount());
        json.add("data", writeContainer(data));
        return json;
    }

    /**
     * @return the rebuilt item, or null if its item key resolves to no item
     */
    public @Nullable ItemStack decode(@NotNull JsonObject json) {
        final String id = json.get("item").getAsString();
        final ItemStack stack = model(id);
        if (stack == null) {
            log.warn("Dropping saved item {}, its key resolves to no item", id).submit();
            return null;
        }

        stack.setAmount(json.get("amount").getAsInt());
        final JsonObject data = json.getAsJsonObject("data");
        stack.editPersistentDataContainer(container -> {
            for (NamespacedKey existing : new ArrayList<>(container.getKeys())) {
                container.remove(existing);
            }
            readContainer(data, container);
        });
        return stack;
    }

    private @Nullable ItemStack model(@NotNull String id) {
        final NamespacedKey key = NamespacedKey.fromString(id);
        final BaseItem baseItem = key == null ? null : itemFactory.getItemRegistry().getItem(key);
        if (baseItem != null) {
            return baseItem.getModel().clone();
        }

        final Material material = Material.matchMaterial(id);
        if (material == null || !material.isItem()) {
            return null;
        }
        return itemFactory.getFallbackItem(material).getModel().clone();
    }

    private JsonObject writeContainer(@NotNull PersistentDataContainerView container) {
        final JsonObject json = new JsonObject();
        for (NamespacedKey key : container.getKeys()) {
            final JsonObject value = writeValue(container, key);
            if (value == null) {
                log.warn("Skipping persistent data {} of an unsupported type", key).submit();
                continue;
            }
            json.add(key.toString(), value);
        }
        return json;
    }

    private @Nullable JsonObject writeValue(@NotNull PersistentDataContainerView container, @NotNull NamespacedKey key) {
        if (container.has(key, PersistentDataType.TAG_CONTAINER)) {
            return tagged("container", writeContainer(container.get(key, PersistentDataType.TAG_CONTAINER)));
        }
        if (container.has(key, PersistentDataType.STRING)) {
            return tagged("string", container.get(key, PersistentDataType.STRING));
        }
        if (container.has(key, PersistentDataType.BYTE)) {
            return tagged("byte", container.get(key, PersistentDataType.BYTE));
        }
        if (container.has(key, PersistentDataType.SHORT)) {
            return tagged("short", container.get(key, PersistentDataType.SHORT));
        }
        if (container.has(key, PersistentDataType.INTEGER)) {
            return tagged("int", container.get(key, PersistentDataType.INTEGER));
        }
        if (container.has(key, PersistentDataType.LONG)) {
            return tagged("long", container.get(key, PersistentDataType.LONG));
        }
        if (container.has(key, PersistentDataType.FLOAT)) {
            return tagged("float", container.get(key, PersistentDataType.FLOAT));
        }
        if (container.has(key, PersistentDataType.DOUBLE)) {
            return tagged("double", container.get(key, PersistentDataType.DOUBLE));
        }
        if (container.has(key, PersistentDataType.BYTE_ARRAY)) {
            return tagged("bytes", Base64.getEncoder().encodeToString(container.get(key, PersistentDataType.BYTE_ARRAY)));
        }
        if (container.has(key, PersistentDataType.INTEGER_ARRAY)) {
            final JsonArray array = new JsonArray();
            for (int value : container.get(key, PersistentDataType.INTEGER_ARRAY)) {
                array.add(value);
            }
            return tagged("ints", array);
        }
        if (container.has(key, PersistentDataType.LONG_ARRAY)) {
            final JsonArray array = new JsonArray();
            for (long value : container.get(key, PersistentDataType.LONG_ARRAY)) {
                array.add(value);
            }
            return tagged("longs", array);
        }
        return writeList(container, key);
    }

    // Element types are checked one by one because a list type alone does not say what the list holds
    private @Nullable JsonObject writeList(@NotNull PersistentDataContainerView container, @NotNull NamespacedKey key) {
        if (container.has(key, PersistentDataType.LIST.strings())) {
            final List<?> list = container.get(key, PersistentDataType.LIST.strings());
            if (list.stream().allMatch(String.class::isInstance)) {
                final JsonArray array = new JsonArray();
                list.forEach(value -> array.add((String) value));
                return tagged("strings", array);
            }
        }
        if (container.has(key, PersistentDataType.LIST.dataContainers())) {
            final List<?> list = container.get(key, PersistentDataType.LIST.dataContainers());
            if (list.stream().allMatch(PersistentDataContainerView.class::isInstance)) {
                final JsonArray array = new JsonArray();
                list.forEach(value -> array.add(writeContainer((PersistentDataContainerView) value)));
                return tagged("containers", array);
            }
        }
        return null;
    }

    private void readContainer(@NotNull JsonObject json, @NotNull PersistentDataContainer container) {
        for (var entry : json.entrySet()) {
            final NamespacedKey key = NamespacedKey.fromString(entry.getKey());
            if (key == null) {
                continue;
            }
            readValue(entry.getValue().getAsJsonObject(), key, container);
        }
    }

    private void readValue(@NotNull JsonObject tagged, @NotNull NamespacedKey key, @NotNull PersistentDataContainer container) {
        final JsonElement value = tagged.get("v");
        switch (tagged.get("t").getAsString()) {
            case "container" -> container.set(key, PersistentDataType.TAG_CONTAINER, newContainer(value.getAsJsonObject(), container));
            case "string" -> container.set(key, PersistentDataType.STRING, value.getAsString());
            case "byte" -> container.set(key, PersistentDataType.BYTE, value.getAsByte());
            case "short" -> container.set(key, PersistentDataType.SHORT, value.getAsShort());
            case "int" -> container.set(key, PersistentDataType.INTEGER, value.getAsInt());
            case "long" -> container.set(key, PersistentDataType.LONG, value.getAsLong());
            case "float" -> container.set(key, PersistentDataType.FLOAT, value.getAsFloat());
            case "double" -> container.set(key, PersistentDataType.DOUBLE, value.getAsDouble());
            case "bytes" -> container.set(key, PersistentDataType.BYTE_ARRAY, Base64.getDecoder().decode(value.getAsString()));
            case "ints" -> {
                final JsonArray array = value.getAsJsonArray();
                final int[] values = new int[array.size()];
                for (int i = 0; i < values.length; i++) {
                    values[i] = array.get(i).getAsInt();
                }
                container.set(key, PersistentDataType.INTEGER_ARRAY, values);
            }
            case "longs" -> {
                final JsonArray array = value.getAsJsonArray();
                final long[] values = new long[array.size()];
                for (int i = 0; i < values.length; i++) {
                    values[i] = array.get(i).getAsLong();
                }
                container.set(key, PersistentDataType.LONG_ARRAY, values);
            }
            case "strings" -> {
                final List<String> values = new ArrayList<>();
                value.getAsJsonArray().forEach(element -> values.add(element.getAsString()));
                container.set(key, PersistentDataType.LIST.strings(), values);
            }
            case "containers" -> {
                final List<PersistentDataContainer> values = new ArrayList<>();
                value.getAsJsonArray().forEach(element -> values.add(newContainer(element.getAsJsonObject(), container)));
                container.set(key, PersistentDataType.LIST.dataContainers(), values);
            }
            default -> log.warn("Skipping persistent data {} of unknown type {}", key, tagged.get("t")).submit();
        }
    }

    private PersistentDataContainer newContainer(@NotNull JsonObject json, @NotNull PersistentDataContainer parent) {
        final PersistentDataContainer child = parent.getAdapterContext().newPersistentDataContainer();
        readContainer(json, child);
        return child;
    }

    private static JsonObject tagged(@NotNull String type, @NotNull Object value) {
        final JsonObject json = new JsonObject();
        json.addProperty("t", type);
        switch (value) {
            case JsonElement element -> json.add("v", element);
            case Number number -> json.addProperty("v", number);
            default -> json.addProperty("v", value.toString());
        }
        return json;
    }
}
