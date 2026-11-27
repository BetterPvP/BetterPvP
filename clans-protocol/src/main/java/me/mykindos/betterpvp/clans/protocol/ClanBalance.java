package me.mykindos.betterpvp.clans.protocol;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/** A clan's balance as the ledger answered it, with the ledger's sequence so a server keeps only the newest. */
@Value
public class ClanBalance {

    long clan;
    long balance;
    long sequence;

    public @NotNull Map<String, String> toPayload() {
        return Map.of(
                "clan", Long.toString(clan),
                "balance", Long.toString(balance),
                "sequence", Long.toString(sequence));
    }

    public static @NotNull ClanBalance fromPayload(@NotNull Map<String, String> payload) {
        return new ClanBalance(Long.parseLong(payload.get("clan")), Long.parseLong(payload.get("balance")),
                Long.parseLong(payload.get("sequence")));
    }
}
