package me.mykindos.betterpvp.core.command.menus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("Invsee menu")
class PlayerInventoryMenuTest {

    private static Properties load(String locale) throws IOException {
        final String path = "/translations/core_" + locale + ".properties";
        try (InputStream stream = PlayerInventoryMenuTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            final Properties properties = new Properties();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return properties;
        }
    }

    @Test
    @DisplayName("CLANS-54 AC2 the invsee title is a translatable component with a key in every core locale")
    void ac2_titleTranslated() throws IOException {
        final TranslatableComponent title = assertInstanceOf(TranslatableComponent.class, PlayerInventoryMenu.title("Steve"));

        assertEquals(Component.text("Steve"), title.arguments().getFirst().asComponent());
        for (String locale : List.of("ar", "de", "en", "es", "fr", "ja", "ko", "ms", "nl", "pl", "ru", "zh")) {
            assertNotNull(load(locale).getProperty(title.key()), locale);
        }
    }
}
