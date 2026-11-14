package me.mykindos.betterpvp.game.framework.model.attribute;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.mykindos.betterpvp.game.framework.model.attribute.global.CurrentMapAttribute;
import me.mykindos.betterpvp.game.framework.model.attribute.global.StartPausedAttribute;
import me.mykindos.betterpvp.game.framework.model.attribute.global.StartingCountdownAttribute;

/**
 * Module for registering game attributes.
 */
@Singleton
public class GlobalAttributeModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(GameAttributeManager.class).asEagerSingleton();

        // Bind attribute implementations
        bind(StartingCountdownAttribute.class).asEagerSingleton();
        bind(StartPausedAttribute.class).asEagerSingleton();
        bind(CurrentMapAttribute.class).asEagerSingleton();

        // Bind initializer
        bind(Initializer.class).asEagerSingleton();
    }

    /**
     * Initializes the game attributes.
     */
    @Singleton
    public static class Initializer {

        @Inject
        public Initializer(GameAttributeManager attributeManager,
                           StartingCountdownAttribute startingCountdownAttribute,
                           StartPausedAttribute startPausedAttribute,
                           CurrentMapAttribute mapAttribute) {

            // Register attributes
            attributeManager.registerAttribute(startingCountdownAttribute);
            attributeManager.registerAttribute(startPausedAttribute);
            attributeManager.registerAttribute(mapAttribute);
        }
    }
}
