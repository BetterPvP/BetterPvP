package me.mykindos.betterpvp.clans;

import me.mykindos.betterpvp.clans.clans.commands.chatcommands.ClanChatCommand;
import me.mykindos.betterpvp.clans.clans.listeners.ClanBankListener;
import me.mykindos.betterpvp.core.framework.role.RoleGate;
import me.mykindos.betterpvp.core.framework.role.ServerRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Which roles install Clans features")
class ClansRoleTest {

    @Test
    @DisplayName("A hub server installs no Clans feature")
    void hubHasNoClans() {
        final RoleGate hub = new RoleGate(ServerRole.HUB);
        assertFalse(hub.allows(ClanBankListener.class));
        assertFalse(hub.allows(ClanChatCommand.class));
    }

    @Test
    @DisplayName("Every gameplay role and role all install Clans features")
    void gameplayRolesHaveClans() {
        for (ServerRole role : List.of(ServerRole.RESOURCE, ServerRole.CAMP, ServerRole.DUNGEON, ServerRole.EVENT,
                ServerRole.ALL)) {
            final RoleGate gate = new RoleGate(role);
            assertTrue(gate.allows(ClanBankListener.class), role.getId());
            assertTrue(gate.allows(ClanChatCommand.class), role.getId());
        }
    }

}
