package me.mykindos.betterpvp.core.framework.role;

import me.mykindos.betterpvp.core.framework.BPvPPlugin;
import me.mykindos.betterpvp.core.framework.adapter.Adapters;
import me.mykindos.betterpvp.core.framework.role.fixture.PlainFeature;
import me.mykindos.betterpvp.core.framework.role.fixture.dungeon.CampOverride;
import me.mykindos.betterpvp.core.framework.role.fixture.dungeon.DungeonFeature;
import me.mykindos.betterpvp.core.framework.role.fixture.dungeon.EverywhereFeature;
import me.mykindos.betterpvp.core.framework.role.fixture.dungeon.inner.InnerFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("Deciding which features install on a role")
class RoleGateTest {

    @Test
    @DisplayName("Role all installs every feature, declared or not")
    void allInstallsEverything() {
        final RoleGate gate = new RoleGate(ServerRole.ALL);
        List.of(PlainFeature.class, DungeonFeature.class, DungeonFeature.Nested.class, CampOverride.class,
                CampOverride.Nested.class, InnerFeature.class, EverywhereFeature.class)
                .forEach(clazz -> assertTrue(gate.allows(clazz), clazz.getSimpleName()));
    }

    @Test
    @DisplayName("An undeclared feature installs on every role")
    void undeclaredRunsEverywhere() {
        for (ServerRole role : ServerRole.values()) {
            assertTrue(new RoleGate(role).allows(PlainFeature.class), role.getId());
        }
    }

    @Test
    @DisplayName("A package declaration covers its classes, nested classes and subpackages")
    void packageCoversItsTree() {
        final RoleGate camp = new RoleGate(ServerRole.CAMP);
        assertFalse(camp.allows(DungeonFeature.class));
        assertFalse(camp.allows(DungeonFeature.Nested.class));
        assertFalse(camp.allows(InnerFeature.class));

        final RoleGate dungeon = new RoleGate(ServerRole.DUNGEON);
        assertTrue(dungeon.allows(DungeonFeature.class));
        assertTrue(dungeon.allows(InnerFeature.class));
    }

    @Test
    @DisplayName("A class declaration wins over its package and covers its nested classes")
    void classOverridesPackage() {
        final RoleGate camp = new RoleGate(ServerRole.CAMP);
        assertTrue(camp.allows(CampOverride.class));
        assertTrue(camp.allows(CampOverride.Nested.class));

        final RoleGate dungeon = new RoleGate(ServerRole.DUNGEON);
        assertFalse(dungeon.allows(CampOverride.class));
    }

    @Test
    @DisplayName("Declaring all installs on every role")
    void declaredAllRunsEverywhere() {
        for (ServerRole role : ServerRole.values()) {
            assertTrue(new RoleGate(role).allows(EverywhereFeature.class), role.getId());
        }
    }

    @Test
    @DisplayName("A feature off this role still has its dependencies, so its items, blocks and config load")
    void offRoleKeepsDependencies() {
        final Adapters adapters = new Adapters(mock(BPvPPlugin.class), new RoleGate(ServerRole.CAMP));
        assertTrue(adapters.hasDependencies(DungeonFeature.class));
        assertFalse(adapters.runsOnThisRole(DungeonFeature.class));
        assertFalse(adapters.canLoad(DungeonFeature.class));
        assertTrue(adapters.canLoad(PlainFeature.class));
    }

}
