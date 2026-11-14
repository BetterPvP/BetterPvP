package me.mykindos.betterpvp.clans.clans.commands;

import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.reflections.Reflections;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("/clan keeps every legacy subcommand")
class ClanCommandParityTest {

    private static BrigadierCommand instantiate(Class<?> type) throws ReflectiveOperationException {
        final Constructor<?> constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        final Object[] args = new Object[constructor.getParameterCount()];
        for (int i = 0; i < args.length; i++) {
            args[i] = mock(constructor.getParameterTypes()[i], Answers.RETURNS_DEEP_STUBS);
        }
        return (BrigadierCommand) constructor.newInstance(args);
    }

    /**
     * @return every name and alias a player can type after /clan, with a nested subcommand as "parent child"
     */
    private static Set<String> brigadierPaths() throws ReflectiveOperationException {
        final Map<Class<?>, BrigadierCommand> commands = new HashMap<>();
        final Map<Class<?>, Class<?>> parents = new HashMap<>();
        for (Class<?> type : new Reflections("me.mykindos.betterpvp.clans").getTypesAnnotatedWith(BrigadierSubCommand.class)) {
            if (Modifier.isAbstract(type.getModifiers())) {
                continue;
            }
            commands.put(type, instantiate(type));
            parents.put(type, type.getAnnotation(BrigadierSubCommand.class).value());
        }

        final Set<String> paths = new TreeSet<>();
        commands.forEach((type, command) -> {
            final Class<?> parent = parents.get(type);
            final List<String> names = new ArrayList<>(command.getAliases());
            names.add(command.getName());
            if (parent == BrigadierClansCommand.class) {
                paths.addAll(names);
            } else if (parents.get(parent) == BrigadierClansCommand.class) {
                final BrigadierCommand parentCommand = commands.get(parent);
                names.forEach(name -> paths.add(parentCommand.getName() + " " + name));
            }
        });
        return paths;
    }

    @Test
    @DisplayName("CLANS-43 AC3 every legacy /clan subcommand and alias has a Brigadier /clan twin")
    void everyLegacyPathExists() throws ReflectiveOperationException {
        final Set<String> paths = brigadierPaths();
        // Every subcommand path and alias the legacy /legacyclan tree answered to
        final Set<String> missing = new TreeSet<>(List.of(
                "addoutskirts", "adminlogs", "ally", "bank", "bank deposit", "bank withdraw", "claim", "c", "clanplayers",
                "clanrecovery", "core", "home", "create", "demote", "disband", "enemy", "farminginfo", "farminglevels",
                "help", "?", "h", "info", "invite", "join", "kick", "killlogs", "leave", "list", "logs", "neutral",
                "playerclans", "promote", "protection", "rename", "setbanner", "banner", "setclanbanner", "setcore",
                "sethome", "setdominance", "setenergy", "setexp", "stuck", "trust", "tutorial", "unclaim", "untrust"));
        missing.removeAll(paths);
        assertTrue(missing.isEmpty(), "missing from Brigadier /clan: " + missing);
    }
}
