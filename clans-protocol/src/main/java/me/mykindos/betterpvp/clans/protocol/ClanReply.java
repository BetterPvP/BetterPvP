package me.mykindos.betterpvp.clans.protocol;

import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** The service's answer to a {@link ClanMutation}: applied at a sequence, or refused with a reason. */
@Value
public class ClanReply {

    boolean applied;
    long sequence;
    @Nullable String error;

    public static @NotNull ClanReply applied(long sequence) {
        return new ClanReply(true, sequence, null);
    }

    public static @NotNull ClanReply refused(@NotNull String error) {
        return new ClanReply(false, -1, error);
    }

    public @NotNull Map<String, String> toPayload() {
        final Map<String, String> payload = new HashMap<>();
        payload.put("applied", Boolean.toString(applied));
        payload.put("sequence", Long.toString(sequence));
        if (error != null) {
            payload.put("error", error);
        }
        return payload;
    }

    public static @NotNull ClanReply fromPayload(@NotNull Map<String, String> payload) {
        return new ClanReply(Boolean.parseBoolean(payload.get("applied")),
                Long.parseLong(payload.getOrDefault("sequence", "-1")),
                payload.get("error"));
    }
}
