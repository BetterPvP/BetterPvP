package me.mykindos.betterpvp.champions.commands;

import me.mykindos.betterpvp.champions.champions.commands.BuildCommand;
import me.mykindos.betterpvp.champions.champions.commands.KitCommand;
import me.mykindos.betterpvp.champions.champions.commands.SendSkillsCommand;
import me.mykindos.betterpvp.champions.champions.commands.SkillDescription;
import me.mykindos.betterpvp.champions.champions.commands.admin.LegendsCommand;
import me.mykindos.betterpvp.champions.champions.commands.admin.PromptBuildCommand;
import me.mykindos.betterpvp.champions.champions.commands.admin.RunesCommand;
import me.mykindos.betterpvp.champions.stats.command.CombatCommand;
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

@DisplayName("Champions commands on Brigadier")
class ChampionsCommandsParityTest {

    private static BrigadierCommand instantiate(Class<? extends BrigadierCommand> type) throws ReflectiveOperationException {
        final Constructor<?> constructor = type.getDeclaredConstructors()[0];
        final Object[] args = new Object[constructor.getParameterCount()];
        for (int i = 0; i < args.length; i++) {
            args[i] = mock(constructor.getParameterTypes()[i], Answers.RETURNS_DEEP_STUBS);
        }
        return (BrigadierCommand) constructor.newInstance(args);
    }

    @Test
    @DisplayName("CLANS-46 every migrated Champions command keeps its legacy name, aliases and rank")
    void namesAliasesAndRanks() throws Exception {
        final YamlConfiguration config;
        try (InputStream stream = ChampionsCommandsParityTest.class.getResourceAsStream("/configs/permissions/commands.yml")) {
            config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        // The legacy name, aliases and effective rank of each command
        final Map<Class<? extends BrigadierCommand>, Object[]> expected = Map.of(
                BuildCommand.class, new Object[]{"build", Set.of(), "ADMIN"},
                KitCommand.class, new Object[]{"kit", Set.of(), "ADMIN"},
                SendSkillsCommand.class, new Object[]{"sendskills", Set.of("sendskill", "sendbuild"), "PLAYER"},
                SkillDescription.class, new Object[]{"skilldescription", Set.of(), "ADMIN"},
                LegendsCommand.class, new Object[]{"legends", Set.of(), "ADMIN"},
                RunesCommand.class, new Object[]{"runes", Set.of(), "ADMIN"},
                PromptBuildCommand.class, new Object[]{"promptbuild", Set.of(), "ADMIN"},
                ChampionsCommand.class, new Object[]{"champions", Set.of(), "ADMIN"},
                CombatCommand.class, new Object[]{"combat", Set.of(), "PLAYER"});

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
