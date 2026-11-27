package me.mykindos.betterpvp.core.world.site.command;

import me.mykindos.betterpvp.core.world.site.Party;
import me.mykindos.betterpvp.core.world.site.Site;
import me.mykindos.betterpvp.core.world.site.SiteInstance;
import me.mykindos.betterpvp.core.world.site.SiteInstances;
import me.mykindos.betterpvp.core.world.site.SiteKey;
import me.mykindos.betterpvp.core.world.site.SiteRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SiteCreateSubCommandTest {

    private final SiteRegistry registry = mock(SiteRegistry.class);
    private final SiteInstances instances = mock(SiteInstances.class);
    private final Site site = mock(Site.class);
    private final SiteInstance instance = mock(SiteInstance.class);
    private final SiteKey key = SiteKey.of("camp", 42L);
    private final SiteCreateSubCommand command = new SiteCreateSubCommand(registry, instances);

    @BeforeEach
    void setUp() {
        when(registry.get("camp")).thenReturn(Optional.of(site));
        when(registry.get("nowhere")).thenReturn(Optional.empty());
        when(site.keyFor(42L)).thenReturn(key);
        when(instances.locate(any(), any())).thenReturn(CompletableFuture.completedFuture(instance));
        when(instance.getShortId()).thenReturn("a1b2");
        when(instance.getWorldName()).thenReturn("camp_42");
    }

    @Test
    @DisplayName("The console opens an owned site the way a player does, then hands its reservation back")
    void consoleOpensThroughLocate() {
        final CommandSender console = mock(CommandSender.class);

        command.execute(console, new String[]{"camp", "42"});

        final Party placeholder = Party.solo(new UUID(0L, 0L));
        verify(instances).locate(key, placeholder);
        verify(instances).abandon(instance, placeholder);
        verify(console).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("A player still opens the site as themselves")
    void playerOpensAsThemselves() {
        final Player player = mock(Player.class);
        final UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);

        command.execute(player, null, "camp", "42");

        verify(instances).locate(key, Party.solo(id));
        verify(instances).abandon(instance, Party.solo(id));
    }

    @Test
    @DisplayName("An unknown site opens nothing and says so")
    void unknownSiteOpensNothing() {
        final CommandSender console = mock(CommandSender.class);

        command.execute(console, new String[]{"nowhere"});

        verify(instances, never()).locate(any(), any());
        verify(console).sendMessage(any(Component.class));
    }
}
