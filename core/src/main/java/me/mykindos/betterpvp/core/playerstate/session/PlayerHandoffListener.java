package me.mykindos.betterpvp.core.playerstate.session;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.CustomLog;
import me.mykindos.betterpvp.core.framework.agones.ServerDrainEvent;
import me.mykindos.betterpvp.core.framework.updater.UpdateEvent;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import me.mykindos.betterpvp.core.locale.Translations;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Runs the {@link PlayerHandoff} around logins, joins and quits. Does nothing while players are not shared. */
@CustomLog
@Singleton
@BPvPListener
public class PlayerHandoffListener implements Listener {

    private final PlayerHandoff handoff;

    @Inject
    public PlayerHandoffListener(@NotNull PlayerHandoff handoff) {
        this.handoff = handoff;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!handoff.isActive() || event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        boolean claimed;
        try {
            claimed = handoff.arrive(event.getUniqueId(), Duration.ofSeconds(20)).join();
        } catch (RuntimeException failure) {
            log.error("Could not take over {} from the sessions service", event.getName(), failure).submit();
            claimed = false;
        }
        if (!claimed) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Translations.component("core.session.held"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        if (handoff.isActive()) {
            handoff.join(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!handoff.isActive()) {
            return;
        }
        final var leaving = handoff.leave(event.getPlayer());
        // The process may end before an async save would, so a stopping server waits for it
        if (Bukkit.isStopping()) {
            leaving.join();
        }
    }

    // Released before the drain moves anyone, because the next server waits for the release before it lets them in
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrain(ServerDrainEvent event) {
        if (!handoff.isActive()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            handoff.leave(player);
        }
    }

    @UpdateEvent(delay = 5000, isAsync = true)
    public void heartbeat() {
        if (!handoff.isActive()) {
            return;
        }
        handoff.heartbeat();
        final Set<UUID> online = Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).collect(Collectors.toSet());
        handoff.releaseAbandoned(online, Duration.ofSeconds(30));
    }

    @UpdateEvent(delay = 60_000)
    public void autosave() {
        if (!handoff.isActive()) {
            return;
        }
        for (UUID id : handoff.owned()) {
            final Player player = Bukkit.getPlayer(id);
            if (player != null) {
                handoff.save(player);
            }
        }
    }
}
