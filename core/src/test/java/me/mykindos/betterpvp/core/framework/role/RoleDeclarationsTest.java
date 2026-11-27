package me.mykindos.betterpvp.core.framework.role;

import me.mykindos.betterpvp.core.command.Command;
import me.mykindos.betterpvp.core.framework.adapter.PluginAdapter;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.reflections.Reflections;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Which core features each role installs")
class RoleDeclarationsTest {

    private static final String CORE = "me.mykindos.betterpvp.core.";

    private static Set<Class<?>> features;
    private static Set<Class<?>> mythicMobsFeatures;

    @BeforeAll
    static void scan() {
        final Reflections reflections = new Reflections("me.mykindos.betterpvp.core");
        features = new HashSet<>(reflections.getTypesAnnotatedWith(BPvPListener.class));
        features.addAll(reflections.getSubTypesOf(Command.class));
        features.addAll(reflections.getTypesAnnotatedWith(PluginAdapter.class));

        mythicMobsFeatures = features.stream()
                .filter(clazz -> Arrays.stream(clazz.getAnnotationsByType(PluginAdapter.class))
                        .anyMatch(adapter -> adapter.value().equals("MythicMobs")))
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("A camp server installs no MythicMobs feature")
    void campHasNoMythicMobs() {
        assertFalse(mythicMobsFeatures.isEmpty());
        final RoleGate camp = new RoleGate(ServerRole.CAMP);
        mythicMobsFeatures.forEach(clazz -> assertFalse(camp.allows(clazz), clazz.getName()));
    }

    @Test
    @DisplayName("MythicMobs features install on the resource, dungeon and event roles")
    void mythicMobsWhereMobsLive() {
        for (ServerRole role : List.of(ServerRole.RESOURCE, ServerRole.DUNGEON, ServerRole.EVENT)) {
            final RoleGate gate = new RoleGate(role);
            mythicMobsFeatures.forEach(clazz -> assertTrue(gate.allows(clazz), role.getId() + " " + clazz.getName()));
        }
    }

    @Test
    @DisplayName("A hub server installs no world gameplay and no MythicMobs feature")
    void hubHasNoWorldGameplay() {
        final RoleGate hub = new RoleGate(ServerRole.HUB);
        final Set<Class<?>> worldFeatures = features.stream().filter(RoleDeclarationsTest::isWorldGameplay)
                .collect(Collectors.toSet());

        assertFalse(worldFeatures.isEmpty());
        worldFeatures.forEach(clazz -> assertFalse(hub.allows(clazz), clazz.getName()));
        mythicMobsFeatures.forEach(clazz -> assertFalse(hub.allows(clazz), clazz.getName()));
    }

    @Test
    @DisplayName("A hub server keeps chat, clients and the rest of core")
    void hubKeepsChatAndClients() {
        final RoleGate hub = new RoleGate(ServerRole.HUB);
        final Set<Class<?>> kept = features.stream()
                .filter(clazz -> !isWorldGameplay(clazz) && !mythicMobsFeatures.contains(clazz))
                .collect(Collectors.toSet());

        assertTrue(kept.stream().anyMatch(clazz -> clazz.getPackageName().startsWith(CORE + "chat")));
        assertTrue(kept.stream().anyMatch(clazz -> clazz.getPackageName().startsWith(CORE + "client")));
        kept.forEach(clazz -> assertTrue(hub.allows(clazz), clazz.getName()));
    }

    @Test
    @DisplayName("Role all installs every core feature")
    void allInstallsEveryFeature() {
        final RoleGate all = new RoleGate(ServerRole.ALL);
        features.forEach(clazz -> assertTrue(all.allows(clazz), clazz.getName()));
    }

    private static boolean isWorldGameplay(Class<?> clazz) {
        final String packageName = clazz.getPackageName();
        return List.of("anvil", "imbuement", "metal", "repair", "supplycrate", "loot", "trade", "map", "quest",
                        "world.construction", "world.settler", "world.terrain").stream().anyMatch(excluded -> packageName.equals(CORE + excluded)
                || packageName.startsWith(CORE + excluded + "."));
    }

}
