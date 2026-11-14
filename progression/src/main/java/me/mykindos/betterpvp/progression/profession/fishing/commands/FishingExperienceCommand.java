package me.mykindos.betterpvp.progression.profession.fishing.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.progression.profession.ProfessionExperienceCommand;
import me.mykindos.betterpvp.progression.profession.fishing.FishingHandler;
import me.mykindos.betterpvp.progression.profile.ProfessionProfileManager;
import net.kyori.adventure.text.Component;

/**
 * {@code /fishingexp set <player> <experience>}.
 */
@Singleton
public class FishingExperienceCommand extends ProfessionExperienceCommand {

    @Inject
    public FishingExperienceCommand(ClientManager clientManager, FishingHandler fishingHandler, ProfessionProfileManager professionProfileManager) {
        super(clientManager, fishingHandler, professionProfileManager, "core.prefix.fishing", "progression.command.fishingexp.set");
    }

    @Override
    public String getName() {
        return "fishingexp";
    }

    @Override
    public String getDescription() {
        return "Base fishing experience command";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("progression.command.fishing-experience.description");
    }
}
