package me.mykindos.betterpvp.progression.profession.woodcutting.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.progression.profession.ProfessionExperienceCommand;
import me.mykindos.betterpvp.progression.profession.woodcutting.WoodcuttingHandler;
import me.mykindos.betterpvp.progression.profile.ProfessionProfileManager;
import net.kyori.adventure.text.Component;

/**
 * {@code /woodcuttingexp set <player> <experience>}.
 */
@Singleton
public class WoodcuttingExperienceCommand extends ProfessionExperienceCommand {

    @Inject
    public WoodcuttingExperienceCommand(ClientManager clientManager, WoodcuttingHandler woodcuttingHandler, ProfessionProfileManager professionProfileManager) {
        super(clientManager, woodcuttingHandler, professionProfileManager, "core.prefix.woodcutting", "progression.command.woodcuttingexp.set");
    }

    @Override
    public String getName() {
        return "woodcuttingexp";
    }

    @Override
    public String getDescription() {
        return "Base woodcutting experience command";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("progression.command.woodcutting-experience.description");
    }
}
