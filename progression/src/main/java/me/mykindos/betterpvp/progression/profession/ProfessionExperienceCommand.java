package me.mykindos.betterpvp.progression.profession;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.arguments.BPvPArgumentTypes;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.progression.profile.ProfessionData;
import me.mykindos.betterpvp.progression.profile.ProfessionProfileManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

/**
 * {@code /<profession>exp set <player> <experience>} sets a player's experience in one profession. Each profession
 * has its own subclass, so each keeps its own command name, rank and messages.
 */
public abstract class ProfessionExperienceCommand extends BrigadierCommand {

    private final ProfessionHandler handler;
    private final ProfessionProfileManager professionProfileManager;
    private final String prefix;
    private final String setKey;

    /**
     * @param prefix the message prefix key, such as {@code core.prefix.fishing}
     * @param setKey the success message key, given the player, the new experience and the old experience
     */
    protected ProfessionExperienceCommand(ClientManager clientManager, ProfessionHandler handler,
                                          ProfessionProfileManager professionProfileManager, String prefix, String setKey) {
        super(clientManager);
        this.handler = handler;
        this.professionProfileManager = professionProfileManager;
        this.prefix = prefix;
        this.setKey = setKey;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.literal("set")
                        .then(BrigadierNodes.argument("player", BPvPArgumentTypes.playerName())
                                .then(BrigadierNodes.argument("experience", DoubleArgumentType.doubleArg(0))
                                        .executes(context -> {
                                            setExperience(context.getSource().getSender(), context.getArgument("player", String.class),
                                                    context.getArgument("experience", Double.class));
                                            return Command.SINGLE_SUCCESS;
                                        }))));
    }

    private void setExperience(CommandSender sender, String targetName, double experience) {
        clientManager.search().offline(targetName).thenAcceptAsync(targetOptional -> {
            if (targetOptional.isEmpty()) {
                UtilMessage.message(sender, prefix, "progression.command.exp.player-not-found", Component.text(targetName, NamedTextColor.YELLOW));
                return;
            }
            final Client target = targetOptional.get();
            final ProfessionData professionData = handler.getProfessionData(target.getUniqueId());
            final double oldExperience = professionData.getExperience();
            professionData.setExperience(experience);
            professionProfileManager.getRepository().saveExperience(target.getUniqueId(), professionData.getProfession(), professionData.getExperience());
            UtilMessage.message(sender, prefix, setKey,
                    Component.text(target.getName(), NamedTextColor.YELLOW),
                    Component.text(experience, NamedTextColor.GREEN),
                    Component.text(oldExperience, NamedTextColor.WHITE));
        });
    }
}
