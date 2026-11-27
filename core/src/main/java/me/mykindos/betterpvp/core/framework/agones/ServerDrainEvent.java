package me.mykindos.betterpvp.core.framework.agones;

import me.mykindos.betterpvp.core.framework.events.CustomEvent;

/**
 * Called on the main thread when the server starts draining, after it stops accepting arrivals and before players
 * are moved off. Save anything that must outlive the server here.
 */
public class ServerDrainEvent extends CustomEvent {
}
