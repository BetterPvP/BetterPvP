package me.mykindos.betterpvp.core.framework.role;

import lombok.CustomLog;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The job a server does on the network, read from the {@code BPVP_ROLE} environment variable. {@link #ALL} runs every
 * feature on one server and is what an unset or unknown value resolves to.
 */
@CustomLog
public enum ServerRole {

    HUB,
    RESOURCE,
    CAMP,
    DUNGEON,
    EVENT,
    ALL;

    public static final String ENVIRONMENT_VARIABLE = "BPVP_ROLE";

    public String getId() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ServerRole parse(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }

        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("Unknown {} '{}', running as all", ENVIRONMENT_VARIABLE, value).submit();
            return ALL;
        }
    }

}
