package me.mykindos.betterpvp.clans.clans.writer;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.CustomLog;
import org.jetbrains.annotations.NotNull;

/**
 * Picks the clan writer from the server's role. A server with role {@code all}, or none set, is a network of one and
 * writes clans itself. Every other role is one of many servers, and they all write through the Clans service.
 */
@CustomLog
@Singleton
public class ClanWriterProvider implements Provider<ClanWriter> {

    private final Provider<LocalClanWriter> local;
    private final Provider<ServiceClanWriter> service;

    @Inject
    public ClanWriterProvider(@NotNull Provider<LocalClanWriter> local, @NotNull Provider<ServiceClanWriter> service) {
        this.local = local;
        this.service = service;
    }

    @Override
    public ClanWriter get() {
        final String role = System.getenv("BPVP_ROLE");
        if (role == null || role.isBlank() || role.equalsIgnoreCase("all")) {
            return local.get();
        }
        log.info("Role {} writes clans through the Clans service", role).submit();
        return service.get();
    }
}
