package me.mykindos.betterpvp.clans.clans.alert;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.mykindos.betterpvp.clans.Clans;
import me.mykindos.betterpvp.clans.clans.ClanManager;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.framework.net.BusMessage;
import me.mykindos.betterpvp.core.framework.net.MessageBus;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Tells every online member of a clan something, on whichever server they are. Members here hear it at once and every
 * other server is told over the {@link MessageBus} to tell its own. An alert nobody hears is lost, so it is only ever
 * a notice about something already written down.
 */
@Singleton
public class ClanAlerts {

    private final Clans clans;
    private final ClanManager clanManager;
    private final MessageBus bus;

    @Inject
    public ClanAlerts(@NotNull Clans clans, @NotNull ClanManager clanManager, @NotNull MessageBus bus) {
        this.clans = clans;
        this.clanManager = clanManager;
        this.bus = bus;
        bus.subscribe("clans.alert", this::receive);
    }

    /** Tells every member of the clan, here and on every other server. */
    public void alert(long clanId, @NotNull Component message, @Nullable Sound sound) {
        deliver(clanId, message, sound);
        alertElsewhere(clanId, message, sound);
    }

    /** Tells the clan's members on every other server, for a caller that has already told everyone here. */
    public void alertElsewhere(long clanId, @NotNull Component message, @Nullable Sound sound) {
        if (!bus.isAvailable()) {
            return;
        }
        final Map<String, String> payload = new HashMap<>();
        payload.put("clan", Long.toString(clanId));
        payload.put("message", GsonComponentSerializer.gson().serialize(message));
        if (sound != null) {
            payload.put("sound", sound.name().asString() + " " + sound.volume() + " " + sound.pitch());
        }
        bus.publish(BusMessage.of("clans.alert", payload));
    }

    void receive(@NotNull BusMessage incoming) {
        if (Core.getCurrentRealm().getServer().getName().equals(incoming.getOrigin())) {
            return;
        }
        final Optional<Long> clanId = incoming.get("clan").map(Long::parseLong);
        final Optional<Component> message = incoming.get("message").map(GsonComponentSerializer.gson()::deserialize);
        if (clanId.isEmpty() || message.isEmpty()) {
            return;
        }
        final Sound sound = incoming.get("sound").map(ClanAlerts::sound).orElse(null);
        UtilServer.runTask(clans, () -> deliver(clanId.get(), message.get(), sound));
    }

    private void deliver(long clanId, @NotNull Component message, @Nullable Sound sound) {
        clanManager.getClanById(clanId).ifPresent(clan -> clan.getMembers().forEach(member -> {
            final Player online = Bukkit.getPlayer(member.getUuid());
            if (online == null) {
                return;
            }
            UtilMessage.plain(online, message);
            if (sound != null) {
                online.playSound(sound);
            }
        }));
    }

    private static @NotNull Sound sound(@NotNull String written) {
        final String[] parts = written.split(" ");
        return Sound.sound(Key.key(parts[0]), Sound.Source.MASTER, Float.parseFloat(parts[1]), Float.parseFloat(parts[2]));
    }
}
