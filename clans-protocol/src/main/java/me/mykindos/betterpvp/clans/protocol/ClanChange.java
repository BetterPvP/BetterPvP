package me.mykindos.betterpvp.clans.protocol;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Announces that the service applied a mutation. It carries no clan state, only which clans to re-read, so losing
 * one costs a stale copy until the next change and never a wrong write.
 */
@Value
public class ClanChange {

    /** Increases by one per applied mutation, in the order the service applied them. */
    long sequence;
    @NotNull ClanOp op;
    @NotNull List<Long> clans;

    public @NotNull Map<String, String> toPayload() {
        return Map.of(
                "sequence", Long.toString(sequence),
                "op", op.name(),
                "clans", clans.stream().map(String::valueOf).collect(Collectors.joining(",")));
    }

    public static @NotNull ClanChange fromPayload(@NotNull Map<String, String> payload) {
        final List<Long> clans = Arrays.stream(payload.getOrDefault("clans", "").split(","))
                .filter(id -> !id.isBlank())
                .map(Long::parseLong)
                .toList();
        return new ClanChange(Long.parseLong(payload.get("sequence")), ClanOp.valueOf(payload.get("op")), clans);
    }
}
