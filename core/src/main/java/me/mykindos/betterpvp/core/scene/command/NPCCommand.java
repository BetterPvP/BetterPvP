package me.mykindos.betterpvp.core.scene.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

/**
 * NPC tooling: {@code spawn}, {@code remove} and {@code list}.
 */
@Singleton
public class NPCCommand extends BrigadierCommand {

    @Inject
    public NPCCommand(ClientManager clientManager) {
        super(clientManager);
    }

    @Override
    public String getName() {
        return "npc";
    }

    @Override
    public String getDescription() {
        return "NPC Commands";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.n-p-c.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final CommandSender sender = context.getSource().getSender();
                    UtilMessage.message(sender, "core.prefix.command", "core.command.npc.help.title");
                    UtilMessage.message(sender, "core.prefix.command", "core.command.npc.help.spawn");
                    UtilMessage.message(sender, "core.prefix.command", "core.command.npc.help.remove");
                    UtilMessage.message(sender, "core.prefix.command", "core.command.npc.help.list");
                    return Command.SINGLE_SUCCESS;
                });
    }
}
