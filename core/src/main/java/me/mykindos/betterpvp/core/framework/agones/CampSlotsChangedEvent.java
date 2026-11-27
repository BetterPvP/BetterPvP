package me.mykindos.betterpvp.core.framework.agones;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import me.mykindos.betterpvp.core.framework.events.CustomEvent;

/**
 * Called by whatever hosts camps when the number it holds, or could hold, changes. A camp server publishes it to
 * Agones as the {@code camp-slots} counter.
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public class CampSlotsChangedEvent extends CustomEvent {

    private final int hosted;
    private final int capacity;

    public CampSlotsChangedEvent(int hosted, int capacity) {
        this.hosted = hosted;
        this.capacity = capacity;
    }

}
