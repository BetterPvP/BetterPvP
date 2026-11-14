package me.mykindos.betterpvp.clans.commands;

import me.mykindos.betterpvp.clans.clans.commands.ChunkPDCCommand;
import me.mykindos.betterpvp.clans.clans.commands.chatcommands.AllyChatCommand;
import me.mykindos.betterpvp.clans.clans.commands.chatcommands.ClanChatCommand;
import me.mykindos.betterpvp.clans.clans.map.commands.MapCommand;
import me.mykindos.betterpvp.clans.clans.transport.commands.TransportCommand;
import me.mykindos.betterpvp.clans.commands.commands.ClansCommand;
import me.mykindos.betterpvp.clans.world.TreeRespawnCommand;
import me.mykindos.betterpvp.clans.world.ship.command.ShipCommand;
import me.mykindos.betterpvp.clans.world.terrain.TerrainCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("Clans commands outside /clan on Brigadier")
class ClansCommandsParityTest {

    private static BrigadierCommand instantiate(Class<? extends BrigadierCommand> type) throws ReflectiveOperationException {
        final Constructor<?> constructor = type.getDeclaredConstructors()[0];
        final Object[] args = new Object[constructor.getParameterCount()];
        for (int i = 0; i < args.length; i++) {
            args[i] = mock(constructor.getParameterTypes()[i], Answers.RETURNS_DEEP_STUBS);
        }
        return (BrigadierCommand) constructor.newInstance(args);
    }

    @Test
    @DisplayName("CLANS-47 every migrated Clans command keeps its legacy name, aliases and rank")
    void namesAliasesAndRanks() throws Exception {
        final YamlConfiguration config;
        try (InputStream stream = ClansCommandsParityTest.class.getResourceAsStream("/configs/permissions/commands.yml")) {
            config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        // The legacy name, aliases and effective rank of each command
        final Map<Class<? extends BrigadierCommand>, Object[]> expected = Map.of(
                AllyChatCommand.class, new Object[]{"allychat", Set.of("ac"), "PLAYER"},
                ClanChatCommand.class, new Object[]{"clanchat", Set.of("cc"), "PLAYER"},
                ChunkPDCCommand.class, new Object[]{"chunkpdc", Set.of(), "ADMIN"},
                TransportCommand.class, new Object[]{"transport", Set.of(), "ADMIN"},
                ClansCommand.class, new Object[]{"clans", Set.of(), "ADMIN"},
                MapCommand.class, new Object[]{"map", Set.of(), "PLAYER"},
                TreeRespawnCommand.class, new Object[]{"treerespawn", Set.of(), "ADMIN"},
                ShipCommand.class, new Object[]{"ship", Set.of(), "ADMIN"},
                TerrainCommand.class, new Object[]{"terrain", Set.of(), "ADMIN"});

        for (Map.Entry<Class<? extends BrigadierCommand>, Object[]> entry : expected.entrySet()) {
            final BrigadierCommand command = instantiate(entry.getKey());
            final String name = (String) entry.getValue()[0];
            assertEquals(name, command.getName());
            assertEquals(entry.getValue()[1], command.getAliases(), name);
            assertTrue(config.isSet(name + ".requiredRank"), name + " has no commands.yml entry");
            assertEquals(entry.getValue()[2], config.getString(name + ".requiredRank"), name);
        }
    }
}
