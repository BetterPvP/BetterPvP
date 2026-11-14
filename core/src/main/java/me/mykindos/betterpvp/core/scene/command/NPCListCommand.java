package me.mykindos.betterpvp.core.scene.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Objects;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.scene.SceneObjectRegistry;
import me.mykindos.betterpvp.core.scene.npc.NPC;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

/**
 * {@code /npc list} shows every active NPC with its factory, id and name.
 */
@Singleton
@BrigadierSubCommand(NPCCommand.class)
public class NPCListCommand extends BrigadierCommand {

    private final SceneObjectRegistry registry;

    @Inject
    public NPCListCommand(ClientManager clientManager, SceneObjectRegistry registry) {
        super(clientManager);
        this.registry = registry;
    }

    @Override
    public String getName() {
        return "list";
    }

    @Override
    public String getDescription() {
        return "Lists all active NPCs";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.n-p-c-list.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final CommandSender sender = context.getSource().getSender();
                    UtilMessage.message(sender, "core.prefix.command", "core.command.npc.list.header");
                    boolean found = false;
                    for (NPC npc : registry.getObjects(NPC.class)) {
                        final Component type = Translations.component("core.command.npc.list.entry.type_label")
                                .color(NamedTextColor.YELLOW)
                                .append(Component.text(npc.getFactory().getName()).color(NamedTextColor.DARK_GREEN));
                        final Component id = Translations.component("core.command.npc.list.entry.id_label")
                                .color(NamedTextColor.YELLOW)
                                .append(Component.text(npc.getId()).color(NamedTextColor.GOLD));
                        final Component name = Objects.requireNonNullElse(npc.getEntity().customName(), Component.text(npc.getEntity().getName()))
                                .applyFallbackStyle(NamedTextColor.GREEN);
                        UtilMessage.message(sender, "core.prefix.command", type.appendSpace().append(id).appendSpace().append(name));
                        found = true;
                    }
                    if (!found) {
                        UtilMessage.message(sender, "core.prefix.command", "core.command.npc.list.none");
                    }
                    return Command.SINGLE_SUCCESS;
                });
    }
}
