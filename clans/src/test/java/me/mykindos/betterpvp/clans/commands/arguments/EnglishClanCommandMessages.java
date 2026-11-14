package me.mykindos.betterpvp.clans.commands.arguments;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.Message;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import me.mykindos.betterpvp.core.locale.TranslationService;
import me.mykindos.betterpvp.core.locale.Translations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.translation.GlobalTranslator;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Stands in for Paper's {@link MessageComponentSerializer}, which only exists on a running server. A serialized
 * message keeps its component and reads as the English text from the Clans translation bundle.
 * <p>
 * The Clans error types build their messages when their class first loads, so every test that touches them opens
 * this first, or a simple error keeps whatever message the first loader made.
 */
public final class EnglishClanCommandMessages implements AutoCloseable {

    private static boolean bundleRegistered;
    private final MockedStatic<MessageComponentSerializer> serializerStatic;

    /**
     * A message that remembers the component it was made from, as Paper's own does.
     */
    public static final class ComponentMessage extends LiteralMessage {
        private final Component component;

        ComponentMessage(Component component) {
            super(english(component));
            this.component = component;
        }

        public Component component() {
            return component;
        }
    }

    private EnglishClanCommandMessages() {
        registerBundle();
        final MessageComponentSerializer serializer = mock(MessageComponentSerializer.class);
        when(serializer.serialize(any(Component.class))).thenAnswer(invocation -> new ComponentMessage(invocation.getArgument(0)));
        when(serializer.deserialize(any(Message.class))).thenAnswer(invocation -> {
            final Message message = invocation.getArgument(0);
            return message instanceof ComponentMessage componentMessage ? componentMessage.component() : Component.text(message.getString());
        });
        serializerStatic = Mockito.mockStatic(MessageComponentSerializer.class);
        serializerStatic.when(MessageComponentSerializer::message).thenReturn(serializer);
    }

    public static EnglishClanCommandMessages open() {
        return new EnglishClanCommandMessages();
    }

    private static synchronized void registerBundle() {
        if (!bundleRegistered) {
            GlobalTranslator.translator().addSource(TranslationService.translator());
            TranslationService.translator().registerBundle(EnglishClanCommandMessages.class.getClassLoader(), "translations.clans");
            bundleRegistered = true;
        }
    }

    /**
     * The component as an English player would read it. Flattened by hand, because the Clans test classpath holds two
     * plain-text serializer providers and {@code PlainTextComponentSerializer} refuses to load.
     */
    public static String english(Component component) {
        registerBundle();
        final StringBuilder text = new StringBuilder();
        ComponentFlattener.basic().flatten(Translations.render(component, (String) null), text::append);
        return text.toString();
    }

    @Override
    public void close() {
        serializerStatic.close();
    }
}
