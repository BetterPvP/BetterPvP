package me.mykindos.betterpvp.progression.commands;

import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.progression.booster.commands.BoosterCommand;
import me.mykindos.betterpvp.progression.profession.fishing.commands.FishingCommand;
import me.mykindos.betterpvp.progression.profession.fishing.commands.FishingExperienceCommand;
import me.mykindos.betterpvp.progression.profession.mining.commands.MiningCommand;
import me.mykindos.betterpvp.progression.profession.mining.commands.MiningExperienceCommand;
import me.mykindos.betterpvp.progression.profession.woodcutting.commands.TreeCompactorCommand;
import me.mykindos.betterpvp.progression.profession.woodcutting.commands.WoodcuttingCommand;
import me.mykindos.betterpvp.progression.profession.woodcutting.commands.WoodcuttingExperienceCommand;
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

@DisplayName("Progression commands on Brigadier")
class ProgressionCommandsParityTest {

    private static BrigadierCommand instantiate(Class<? extends BrigadierCommand> type) throws ReflectiveOperationException {
        final Constructor<?> constructor = type.getDeclaredConstructors()[0];
        final Object[] args = new Object[constructor.getParameterCount()];
        for (int i = 0; i < args.length; i++) {
            args[i] = mock(constructor.getParameterTypes()[i], Answers.RETURNS_DEEP_STUBS);
        }
        return (BrigadierCommand) constructor.newInstance(args);
    }

    @Test
    @DisplayName("CLANS-48 every migrated Progression command keeps its legacy name, aliases and rank")
    void namesAliasesAndRanks() throws Exception {
        final YamlConfiguration config;
        try (InputStream stream = ProgressionCommandsParityTest.class.getResourceAsStream("/configs/permissions/commands.yml")) {
            config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        // The legacy name, aliases and effective rank of each command
        final Map<Class<? extends BrigadierCommand>, Object[]> expected = Map.of(
                BoosterCommand.class, new Object[]{"booster", Set.of(), "PLAYER"},
                FishingCommand.class, new Object[]{"fishing", Set.of(), "PLAYER"},
                MiningCommand.class, new Object[]{"mining", Set.of(), "PLAYER"},
                WoodcuttingCommand.class, new Object[]{"woodcutting", Set.of(), "PLAYER"},
                TreeCompactorCommand.class, new Object[]{"treecompactor", Set.of(), "PLAYER"},
                FishingExperienceCommand.class, new Object[]{"fishingexp", Set.of(), "ADMIN"},
                MiningExperienceCommand.class, new Object[]{"miningexp", Set.of(), "ADMIN"},
                WoodcuttingExperienceCommand.class, new Object[]{"woodcuttingexp", Set.of(), "ADMIN"},
                ProgressionCommand.class, new Object[]{"progression", Set.of(), "ADMIN"});

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
