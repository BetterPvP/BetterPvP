package me.mykindos.betterpvp.clans.clans.listeners;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.mykindos.betterpvp.clans.clans.ClanManager;
import me.mykindos.betterpvp.clans.clans.events.ClanPropertyUpdateEvent;
import me.mykindos.betterpvp.clans.clans.writer.ClanWriter;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.framework.updater.UpdateEvent;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import org.bukkit.event.EventHandler;

@BPvPListener
@Singleton
public class ClanPropertyListener extends ClanListener{

    private final ClanWriter writer;

    @Inject
    public ClanPropertyListener(ClanManager clanManager, ClientManager clientManager, ClanWriter writer) {
        super(clanManager, clientManager);
        this.writer = writer;
    }

    @EventHandler
    public void onSettingsUpdated(ClanPropertyUpdateEvent event) {
        writer.saveProperty(event.getContainer(), event.getProperty(), event.getNewValue());
    }

    @UpdateEvent(delay = 120_000)
    public void processStatUpdates(){
        writer.flushProperties(true);
    }

}
