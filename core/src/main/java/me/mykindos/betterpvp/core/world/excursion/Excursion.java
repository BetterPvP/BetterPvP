package me.mykindos.betterpvp.core.world.excursion;

import lombok.Value;
import me.mykindos.betterpvp.core.framework.net.BusMessage;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A party's trip from one server to another: where it goes, who is in it, and what the destination needs to know to
 * receive them. The first member leads.
 */
@Value
public class Excursion {

    private static final String DETAIL = "detail.";

    @NotNull UUID id;
    /** The site Placement was asked about, such as {@code dungeon}. */
    @NotNull String site;
    @NotNull String origin;
    @NotNull String destination;
    @NotNull List<UUID> members;
    /** Whatever the feature sending the party wants the destination to have. */
    @NotNull Map<String, String> details;

    public @NotNull UUID getLeader() {
        return members.getFirst();
    }

    public @NotNull String detail(@NotNull String key, @NotNull String fallback) {
        return details.getOrDefault(key, fallback);
    }

    @NotNull Map<String, String> toPayload() {
        final Map<String, String> payload = new HashMap<>();
        payload.put("id", id.toString());
        payload.put("site", site);
        payload.put("origin", origin);
        payload.put("destination", destination);
        payload.put("members", members.stream().map(UUID::toString).collect(Collectors.joining(",")));
        details.forEach((key, value) -> payload.put(DETAIL + key, value));
        return payload;
    }

    static @NotNull Excursion fromPayload(@NotNull BusMessage message) {
        final Map<String, String> details = new HashMap<>();
        message.getPayload().forEach((key, value) -> {
            if (key.startsWith(DETAIL)) {
                details.put(key.substring(DETAIL.length()), value);
            }
        });

        final List<UUID> members = Arrays.stream(message.getOrDefault("members", "").split(","))
                .filter(token -> !token.isBlank())
                .map(UUID::fromString)
                .toList();

        return new Excursion(UUID.fromString(message.getOrDefault("id", "")), message.getOrDefault("site", ""),
                message.getOrDefault("origin", ""), message.getOrDefault("destination", ""), members,
                Map.copyOf(details));
    }
}
