package me.mykindos.betterpvp.clans.commands.arguments;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.Dynamic3CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.commands.arguments.exceptions.Clan2CommandExceptionType;
import me.mykindos.betterpvp.clans.commands.arguments.exceptions.ClanArgumentException;
import me.mykindos.betterpvp.clans.commands.arguments.exceptions.ClanCommandExceptionType;
import net.kyori.adventure.text.TranslatableComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Clan argument errors")
class ClanArgumentExceptionTest {


    private EnglishClanCommandMessages messages;

    @BeforeEach
    void setUp() {
        messages = EnglishClanCommandMessages.open();
    }

    @AfterEach
    void tearDown() {
        messages.close();
    }

    private static Clan clan(String name) {
        final Clan clan = mock(Clan.class);
        when(clan.getName()).thenReturn(name);
        return clan;
    }

    /**
     * Throws every error type in {@link ClanArgumentException} with placeholder arguments. A number stands in for
     * each dynamic argument, since the claim cooldown reads its argument as a {@link Double}.
     */
    private static CommandSyntaxException create(Object type) {
        return switch (type) {
            case SimpleCommandExceptionType simple -> simple.create();
            case DynamicCommandExceptionType dynamic -> dynamic.create(1.0);
            case Dynamic2CommandExceptionType dynamic -> dynamic.create(1.0, 2.0);
            case Dynamic3CommandExceptionType dynamic -> dynamic.create(1.0, 2.0, 3.0);
            case ClanCommandExceptionType clanType -> clanType.create(clan("Foo"));
            case Clan2CommandExceptionType clanType -> clanType.create(clan("Foo"), clan("Bar"));
            default -> throw new IllegalArgumentException("Unhandled error type " + type.getClass());
        };
    }

    private static Set<String> errorKeys() throws IllegalAccessException {
        final Set<String> keys = new TreeSet<>();
        for (Field field : ClanArgumentException.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            final CommandSyntaxException exception = create(field.get(null));
            final EnglishClanCommandMessages.ComponentMessage message = assertInstanceOf(
                    EnglishClanCommandMessages.ComponentMessage.class, exception.getRawMessage(), field.getName());
            final TranslatableComponent component = assertInstanceOf(TranslatableComponent.class, message.component(), field.getName());
            assertTrue(component.key().startsWith("clans.command.error."), field.getName() + " uses " + component.key());
            keys.add(component.key());
        }
        return keys;
    }

    private static Properties load(String locale) throws IOException {
        final String path = "/translations/clans_" + locale + ".properties";
        try (InputStream stream = ClanArgumentExceptionTest.class.getResourceAsStream(path)) {
            final Properties properties = new Properties();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return properties;
        }
    }

    private static Set<String> localeErrorKeys(Properties properties) {
        final Set<String> keys = new TreeSet<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("clans.command.error.")) {
                keys.add(key);
            }
        }
        return keys;
    }

    @Test
    @DisplayName("AC1 every clan error is a translatable message under clans.command.error")
    void ac1_everyErrorIsTranslatable() throws IllegalAccessException {
        final Set<String> keys = errorKeys();
        assertEquals(ClanArgumentException.class.getFields().length, keys.size(), "each error has its own key");
    }

    @Test
    @DisplayName("AC2 every clan error key has text in all 12 locales")
    void ac2_everyKeyInEveryLocale() throws IllegalAccessException, IOException {
        final Set<String> keys = errorKeys();
        final Set<String> english = localeErrorKeys(load("en"));
        assertTrue(english.containsAll(keys), "missing from en: " + difference(keys, english));
        for (String locale : List.of("ar", "de", "en", "es", "fr", "ja", "ko", "ms", "nl", "pl", "ru", "zh")) {
            final Properties properties = load(locale);
            assertEquals(english, localeErrorKeys(properties), locale);
            english.forEach(key -> assertFalse(properties.getProperty(key).isBlank(), locale + " " + key));
        }
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        final Set<String> missing = new TreeSet<>(left);
        missing.removeAll(right);
        return missing;
    }

    @Test
    @DisplayName("AC2 a taken clan name is separated from the rest of the message")
    void nameAlreadyExistsHasSpace() {
        assertEquals("Foo is already in use by another Clan",
                ClanArgumentException.NAME_ALREADY_EXISTS.create("Foo").getRawMessage().getString());
    }

    @Test
    @DisplayName("AC2 arguments keep their place in the English text")
    void argumentsKeepTheirPlace() {
        assertEquals("Bob is not a member of Foo",
                ClanArgumentException.MEMBER_NOT_MEMBER_OF_CLAN.create("Foo", "Bob").getRawMessage().getString());
        assertEquals("Bar is not an Ally of Foo",
                ClanArgumentException.CLAN_NOT_ALLY_OF_CLAN.create(clan("Foo"), clan("Bar")).getRawMessage().getString());
        assertEquals("Foo is at the maximum squad size 8 and cannot invite Bob",
                ClanArgumentException.CLAN_AT_MAX_SQUAD_COUNT_INVITE.create("Foo", "Bob", 8).getRawMessage().getString());
    }

    @Test
    @DisplayName("AC2 a pillage error keeps a space after the clan name")
    void pillageErrorsHaveSpace() {
        assertEquals("Foo cannot do this action while pillaging or being pillaged by Bar",
                ClanArgumentException.CLAN_CANNOT_ACTION_CLAN_WHILE_PILLAGING.create(clan("Foo"), clan("Bar")).getRawMessage().getString());
    }
}
