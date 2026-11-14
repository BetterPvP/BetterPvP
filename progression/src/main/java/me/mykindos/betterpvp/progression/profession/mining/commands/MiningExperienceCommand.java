package me.mykindos.betterpvp.progression.profession.mining.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.progression.profession.ProfessionExperienceCommand;
import me.mykindos.betterpvp.progression.profession.mining.MiningHandler;
import me.mykindos.betterpvp.progression.profile.ProfessionProfileManager;
import net.kyori.adventure.text.Component;

/**
 * {@code /miningexp set <player> <experience>}.
 */
@Singleton
public class MiningExperienceCommand extends ProfessionExperienceCommand {

    @Inject
    public MiningExperienceCommand(ClientManager clientManager, MiningHandler miningHandler, ProfessionProfileManager professionProfileManager) {
        super(clientManager, miningHandler, professionProfileManager, "core.prefix.mining", "progression.command.miningexp.set");
    }

    @Override
    public String getName() {
        return "miningexp";
    }

    @Override
    public String getDescription() {
        return "Base mining experience command";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("progression.command.mining-experience.description");
    }
}
