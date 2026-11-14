package me.mykindos.betterpvp.core.world.schematic.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;

/**
 * Builder tooling for structures, a build and its Mapper data-points saved and placed as one thing.
 *
 * @see me.mykindos.betterpvp.core.world.schematic.StructureFormat
 */
@Singleton
public class StructureCommand extends BrigadierCommand {

    static final String PREFIX = "core.prefix.structure";

    @Inject
    public StructureCommand(ClientManager clientManager) {
        super(clientManager);
        getAliases().add("struct");
    }

    /**
     * The structure name argument the subcommands share.
     */
    static RequiredArgumentBuilder<CommandSourceStack, String> nameArgument() {
        return BrigadierNodes.argument("name", StringArgumentType.word());
    }

    @Override
    public String getName() {
        return "structure";
    }

    @Override
    public String getDescription() {
        return "Save and paste builds together with their data-points";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.structure.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    UtilMessage.message(context.getSource().getSender(), PREFIX, "core.command.structure.usage");
                    return Command.SINGLE_SUCCESS;
                });
    }
}
