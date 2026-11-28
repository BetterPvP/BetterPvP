package me.mykindos.betterpvp.clans.clans.alert;

import me.mykindos.betterpvp.clans.Clans;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.ClanManager;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.components.clans.data.ClanMember;
import me.mykindos.betterpvp.core.framework.net.BusMessage;
import me.mykindos.betterpvp.core.framework.net.MessageBus;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Clan alerts reaching members on every server")
class ClanAlertsTest {

    private static final long CLAN = 5;

    /** The server whose code is running, which is what {@code Core.getCurrentRealm()} answers. */
    private String running = "resource";
    private final Network network = new Network();
    private final Player onResource = player();
    private final Player inDungeon = player();
    private final Map<String, Set<Player>> online = Map.of("resource", Set.of(onResource), "dungeon", Set.of(inDungeon));

    private MockedStatic<Core> core;
    private MockedStatic<Bukkit> bukkit;
    private ClanAlerts resource;
    private ClanAlerts dungeon;

    @BeforeEach
    void setUp() {
        core = mockStatic(Core.class, RETURNS_DEEP_STUBS);
        core.when(() -> Core.getCurrentRealm().getServer().getName()).thenAnswer(call -> running);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenAnswer(call -> online.get(running).stream()
                .filter(player -> player.getUniqueId().equals(call.getArgument(0)))
                .findFirst().orElse(null));

        final ClanManager clanManager = mock(ClanManager.class);
        final Clan clan = mock(Clan.class);
        final List<ClanMember> members = new ArrayList<>();
        for (Player player : List.of(onResource, inDungeon)) {
            final UUID id = player.getUniqueId();
            final ClanMember member = mock(ClanMember.class);
            when(member.getUuid()).thenReturn(id);
            members.add(member);
        }
        when(clan.getMembers()).thenReturn(members);
        when(clanManager.getClanById(CLAN)).thenReturn(Optional.of(clan));

        resource = new ClanAlerts(mock(Clans.class), clanManager, network.on("resource"));
        dungeon = new ClanAlerts(mock(Clans.class), clanManager, network.on("dungeon"));
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
        core.close();
    }

    private static Player player() {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }

    @Test
    @DisplayName("a member on another server hears the alert and its sound, and nobody hears it twice")
    void aMemberElsewhereHearsIt() {
        final Sound horn = Sound.sound(Key.key("item.goat_horn.sound.1"), Sound.Source.MASTER, 1f, 0.8f);

        resource.alert(CLAN, Component.text("Your clan is being pillaged"), horn);

        final ArgumentCaptor<Component> heard = ArgumentCaptor.forClass(Component.class);
        verify(inDungeon).sendMessage(heard.capture());
        assertEquals(Component.text("Your clan is being pillaged", NamedTextColor.GRAY), heard.getValue());
        final ArgumentCaptor<Sound> played = ArgumentCaptor.forClass(Sound.class);
        verify(inDungeon).playSound(played.capture());
        assertEquals(horn.name(), played.getValue().name());
        assertEquals(0.8f, played.getValue().pitch());
        verify(onResource, times(1)).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("an alert for elsewhere leaves the members here to whoever already told them")
    void alertElsewhereSkipsThisServer() {
        resource.alertElsewhere(CLAN, Component.text("Pillage"), null);

        verify(onResource, never()).sendMessage(any(Component.class));
        verify(inDungeon).sendMessage(any(Component.class));
        verify(inDungeon, never()).playSound(any(Sound.class));
    }

    @Test
    @DisplayName("with no network, only the members here hear it")
    void aLoneServerTellsOnlyItsOwn() {
        network.up = false;
        running = "dungeon";

        dungeon.alert(CLAN, Component.text("Raider landed"), null);

        verify(onResource, never()).sendMessage(any(Component.class));
        verify(inDungeon).sendMessage(any(Component.class));
    }

    /** Servers sharing one bus. A publish reaches every server, the sender included, as the real transports do. */
    private final class Network {

        private final Map<String, List<Consumer<BusMessage>>> handlers = new HashMap<>();
        private boolean up = true;

        MessageBus on(@NotNull String server) {
            return new MessageBus() {
                @Override
                public void publish(@NotNull BusMessage message) {
                    final BusMessage sent = BusMessage.builder().topic(message.getTopic()).origin(server)
                            .payload(message.getPayload()).build();
                    final String sender = running;
                    handlers.forEach((receiver, subscribed) -> {
                        running = receiver;
                        subscribed.forEach(handler -> handler.accept(sent));
                    });
                    running = sender;
                }

                @Override
                public @NotNull CompletableFuture<List<BusMessage>> request(@NotNull BusMessage message,
                                                                            @NotNull Duration timeout) {
                    return CompletableFuture.completedFuture(List.of());
                }

                @Override
                public void subscribe(@NotNull String topic, @NotNull Consumer<BusMessage> handler) {
                    handlers.computeIfAbsent(server, unused -> new ArrayList<>()).add(handler);
                }

                @Override
                public void answer(@NotNull String topic,
                                   @NotNull Function<BusMessage, Optional<BusMessage>> responder) {
                }

                @Override
                public boolean isAvailable() {
                    return up;
                }
            };
        }
    }
}
