package me.mykindos.betterpvp.core.command.commands.general;

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

@DisplayName("Core general commands on Brigadier")
class GeneralCommandsParityTest {

    private static BrigadierCommand instantiate(Class<? extends BrigadierCommand> type) throws ReflectiveOperationException {
        final Constructor<?> constructor = type.getDeclaredConstructors()[0];
        final Object[] args = new Object[constructor.getParameterCount()];
        for (int i = 0; i < args.length; i++) {
            args[i] = mock(constructor.getParameterTypes()[i], Answers.RETURNS_DEEP_STUBS);
        }
        return (BrigadierCommand) constructor.newInstance(args);
    }

    private static YamlConfiguration commandsConfig() throws Exception {
        try (InputStream stream = GeneralCommandsParityTest.class.getResourceAsStream("/configs/permissions/commands.yml")) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    @Test
    @DisplayName("CLANS-44 every general command keeps its legacy name, aliases and rank")
    void namesAliasesAndRanks() throws Exception {
        final YamlConfiguration config = commandsConfig();
        // The legacy name, aliases and effective rank of each command
        final Map<Class<? extends BrigadierCommand>, Object[]> expected = Map.ofEntries(
                Map.entry(DailyCommand.class, new Object[]{"daily", Set.of(), "PLAYER"}),
                Map.entry(DiscordCommand.class, new Object[]{"discord", Set.of(), "PLAYER"}),
                Map.entry(IgnoreCommand.class, new Object[]{"ignore", Set.of(), "PLAYER"}),
                Map.entry(KillCommand.class, new Object[]{"kill", Set.of(), "PLAYER"}),
                Map.entry(ListCommand.class, new Object[]{"list", Set.of(), "ADMIN"}),
                Map.entry(MessageCommand.class, new Object[]{"message", Set.of("m", "msg", "tell", "whisper", "w"), "PLAYER"}),
                Map.entry(PingCommand.class, new Object[]{"ping", Set.of(), "PLAYER"}),
                Map.entry(PlayTimeCommand.class, new Object[]{"playtime", Set.of("timeplayed"), "PLAYER"}),
                Map.entry(PlayerAdminCommand.class, new Object[]{"admin", Set.of("a"), "PLAYER"}),
                Map.entry(ReplyCommand.class, new Object[]{"reply", Set.of("r"), "PLAYER"}),
                Map.entry(ShowEffectsCommand.class, new Object[]{"showeffects", Set.of(), "PLAYER"}),
                Map.entry(ShowItemCommand.class, new Object[]{"showitem", Set.of(), "PLAYER"}),
                Map.entry(SpawnCommand.class, new Object[]{"spawn", Set.of(), "ADMIN"}));

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
