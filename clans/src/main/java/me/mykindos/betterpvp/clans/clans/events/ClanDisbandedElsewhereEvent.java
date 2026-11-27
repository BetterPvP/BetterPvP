package me.mykindos.betterpvp.clans.clans.events;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.core.framework.events.CustomEvent;
import org.jetbrains.annotations.NotNull;

/**
 * A clan this server held was disbanded on another server, which already ran its {@link ClanDisbandEvent}. Fired just
 * before this server drops the clan, so anything it keeps for the clan here can let go of it.
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public class ClanDisbandedElsewhereEvent extends CustomEvent {

    private final @NotNull Clan clan;

    public ClanDisbandedElsewhereEvent(@NotNull Clan clan) {
        this.clan = clan;
    }
}
