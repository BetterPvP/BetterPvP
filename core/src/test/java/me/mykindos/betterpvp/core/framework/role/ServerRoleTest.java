package me.mykindos.betterpvp.core.framework.role;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Reading the server role")
class ServerRoleTest {

    @Test
    @DisplayName("No role set runs as all")
    void unsetIsAll() {
        assertEquals(ServerRole.ALL, ServerRole.parse(null));
        assertEquals(ServerRole.ALL, ServerRole.parse(""));
        assertEquals(ServerRole.ALL, ServerRole.parse("   "));
    }

    @Test
    @DisplayName("Role names are read without regard to case or padding")
    void namesAreLenient() {
        assertEquals(ServerRole.ALL, ServerRole.parse("all"));
        assertEquals(ServerRole.CAMP, ServerRole.parse(" camp "));
        assertEquals(ServerRole.HUB, ServerRole.parse("HUB"));
        assertEquals(ServerRole.DUNGEON, ServerRole.parse("Dungeon"));
    }

    @Test
    @DisplayName("An unknown role runs as all")
    void unknownIsAll() {
        assertEquals(ServerRole.ALL, ServerRole.parse("lobby"));
    }

    @Test
    @DisplayName("A role's id is its lowercase name")
    void idIsLowercase() {
        assertEquals("resource", ServerRole.RESOURCE.getId());
    }

}
