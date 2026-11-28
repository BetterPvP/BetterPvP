package me.mykindos.betterpvp.core.world.site;

import com.google.inject.AbstractModule;
import me.mykindos.betterpvp.core.world.site.storage.DatabaseSiteStorage;
import me.mykindos.betterpvp.core.world.site.storage.SiteStorage;

/**
 * Wiring for the site framework. This is where the single-server and networked implementations are chosen between,
 * and the only place that choice is made.
 */
public class SiteModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(Placement.class).toProvider(PlacementProvider.class);
        bind(SiteInstanceStore.class).to(DatabaseSiteInstanceStore.class);
        bind(ResidencyStore.class).to(DatabaseResidencyStore.class);
        bind(SiteStorage.class).to(DatabaseSiteStorage.class);
        bind(PlayerWhereabouts.class).to(LocalPlayerWhereabouts.class);
        bind(SlimeWorldLoader.class).to(PolarWorldLoader.class);
    }
}
