package me.mykindos.betterpvp.proxy.network;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.Optional;

/** Velocity's side of {@link NetworkLogins} and {@link HubRouter}. */
public class NetworkListener {

    private final ProxyServer proxy;
    private final NetworkLogins logins;
    private final HubRouter hubs;
    private final Logger logger;

    public NetworkListener(ProxyServer proxy, NetworkLogins logins, HubRouter hubs, Logger logger) {
        this.proxy = proxy;
        this.logins = logins;
        this.hubs = hubs;
        this.logger = logger;
    }

    @Subscribe
    public EventTask onLogin(LoginEvent event) {
        final Player player = event.getPlayer();
        return EventTask.resumeWhenComplete(logins.login(player.getUniqueId(), player.getUsername())
                .thenAccept(verdict -> {
                    switch (verdict) {
                        case ACCEPTED -> {
                        }
                        case ONLINE_ELSEWHERE -> event.setResult(ResultedEvent.ComponentResult.denied(
                                Component.translatable("proxy.network.online-elsewhere")));
                        case UNAVAILABLE -> event.setResult(ResultedEvent.ComponentResult.denied(
                                Component.translatable("proxy.network.unavailable")));
                    }
                }));
    }

    /** Puts a joining player on the hub Placement picks, or leaves Velocity's own choice when it has none. */
    @Subscribe
    public EventTask onChooseInitialServer(PlayerChooseInitialServerEvent event) {
        return EventTask.resumeWhenComplete(hubs.hub().thenAccept(hub -> {
            final Optional<RegisteredServer> server = hub.flatMap(proxy::getServer);
            if (server.isPresent()) {
                event.setInitialServer(server.get());
            } else if (hub.isPresent()) {
                logger.warn("Placement chose hub {}, which this proxy has not registered", hub.get());
            }
        }));
    }

    @Subscribe
    public EventTask onDisconnect(DisconnectEvent event) {
        return EventTask.resumeWhenComplete(logins.logout(event.getPlayer().getUniqueId()));
    }
}
