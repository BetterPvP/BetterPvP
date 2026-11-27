package me.mykindos.betterpvp.clans.world.camp;

import me.mykindos.betterpvp.core.config.Config;
import me.mykindos.betterpvp.core.framework.agones.AgonesLifecycle;
import me.mykindos.betterpvp.core.framework.net.ServerRoom;
import me.mykindos.betterpvp.core.framework.role.RoleGate;
import me.mykindos.betterpvp.core.framework.role.ServerRole;
import org.bukkit.Bukkit;
import org.bukkit.event.server.ServerLoadEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@DisplayName("A camp server's player limit")
class CampServerLimitTest {

    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    /** A limit built from the defaults every setting ships with. */
    private static CampServerLimit withDefaults(ServerRole role) throws ReflectiveOperationException {
        final AgonesLifecycle agones = mock(AgonesLifecycle.class);
        when(agones.getCampSlots()).thenReturn(defaultOf(AgonesLifecycle.class, "campSlots"));
        final CampServerLimit limit = new CampServerLimit(agones, new RoleGate(role));
        setToDefault(limit, "clanSize");
        setToDefault(limit, "visitors");
        return limit;
    }

    private static void setToDefault(CampServerLimit limit, String name) throws ReflectiveOperationException {
        final Field field = CampServerLimit.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(limit, defaultOf(CampServerLimit.class, name));
    }

    private static int defaultOf(Class<?> owner, String name) throws NoSuchFieldException {
        return Integer.parseInt(owner.getDeclaredField(name).getAnnotation(Config.class).defaultValue());
    }

    private static void startUp(CampServerLimit limit) {
        limit.onServerLoad(new ServerLoadEvent(ServerLoadEvent.LoadType.STARTUP));
    }

    @Test
    @DisplayName("is camp slots times a full clan and its visitors, 72 by default")
    void campSlotsTimesAFullCamp() throws ReflectiveOperationException {
        final CampServerLimit limit = withDefaults(ServerRole.CAMP);

        startUp(limit);

        assertEquals(72, limit.limit());
        bukkit.verify(() -> Bukkit.setMaxPlayers(72));
    }

    @Test
    @DisplayName("has room for six full clans and their visitors, and not one player more")
    void sixFullCampsFit() throws ReflectiveOperationException {
        final int limit = withDefaults(ServerRole.CAMP).limit();
        final int sixFullCamps = 6 * (8 + 4);

        assertTrue(new ServerRoom("camp-a", 0, limit).fits(sixFullCamps));
        assertFalse(new ServerRoom("camp-a", sixFullCamps, limit).fits(1));
    }

    @Test
    @DisplayName("is left alone on role all and every other role")
    void otherRolesKeepTheirLimit() throws ReflectiveOperationException {
        for (ServerRole role : ServerRole.values()) {
            if (role != ServerRole.CAMP) {
                startUp(withDefaults(role));
            }
        }

        bukkit.verify(() -> Bukkit.setMaxPlayers(anyInt()), never());
    }
}
