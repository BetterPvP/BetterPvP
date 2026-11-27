package me.mykindos.betterpvp.clans.protocol;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One change to a clan, as a flat map of strings so any bus can carry it. Which fields an op reads is up to the op:
 * relations name {@code other}, member ops name {@code member}, and so on. Unused fields stay {@code null}.
 */
@Value
@Builder
public class ClanMutation {

    @NotNull ClanOp op;
    long clan;
    @Nullable Long other;
    @Nullable UUID member;
    @Nullable String rank;
    @Nullable String name;
    @Nullable Integer realm;
    /** Trusted for {@link ClanOp#TRUST}, allowed for {@link ClanOp#CAMP_PERMISSION}. */
    @Nullable Boolean flag;
    /** Which permission table a {@link ClanOp#CAMP_PERMISSION} edits. */
    @Nullable String scope;
    /** The permitted action a {@link ClanOp#CAMP_PERMISSION} edits. */
    @Nullable String action;

    public @NotNull Map<String, String> toPayload() {
        final Map<String, String> payload = new HashMap<>();
        payload.put("op", op.name());
        payload.put("clan", Long.toString(clan));
        putIfPresent(payload, "other", other);
        putIfPresent(payload, "member", member);
        putIfPresent(payload, "rank", rank);
        putIfPresent(payload, "name", name);
        putIfPresent(payload, "realm", realm);
        putIfPresent(payload, "flag", flag);
        putIfPresent(payload, "scope", scope);
        putIfPresent(payload, "action", action);
        return payload;
    }

    public static @NotNull ClanMutation fromPayload(@NotNull Map<String, String> payload) {
        return ClanMutation.builder()
                .op(ClanOp.valueOf(required(payload, "op")))
                .clan(Long.parseLong(required(payload, "clan")))
                .other(Optional.ofNullable(payload.get("other")).map(Long::parseLong).orElse(null))
                .member(Optional.ofNullable(payload.get("member")).map(UUID::fromString).orElse(null))
                .rank(payload.get("rank"))
                .name(payload.get("name"))
                .realm(Optional.ofNullable(payload.get("realm")).map(Integer::parseInt).orElse(null))
                .flag(Optional.ofNullable(payload.get("flag")).map(Boolean::parseBoolean).orElse(null))
                .scope(payload.get("scope"))
                .action(payload.get("action"))
                .build();
    }

    private static void putIfPresent(@NotNull Map<String, String> payload, @NotNull String key, @Nullable Object value) {
        if (value != null) {
            payload.put(key, value.toString());
        }
    }

    private static @NotNull String required(@NotNull Map<String, String> payload, @NotNull String key) {
        final String value = payload.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Clan mutation is missing " + key);
        }
        return value;
    }
}
