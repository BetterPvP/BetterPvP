package me.mykindos.betterpvp.champions.champions.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Optional;
import me.mykindos.betterpvp.champions.champions.skills.ChampionsSkillManager;
import me.mykindos.betterpvp.champions.champions.skills.Skill;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;

/**
 * Prints a skill's description at a level, level 1 by default. Skill names use {@code _} for spaces.
 */
@Singleton
public class SkillDescription extends BrigadierCommand {

    private final ChampionsSkillManager skillManager;

    @Inject
    public SkillDescription(ClientManager clientManager, ChampionsSkillManager skillManager) {
        super(clientManager);
        this.skillManager = skillManager;
    }

    @Override
    public String getName() {
        return "skilldescription";
    }

    @Override
    public String getDescription() {
        return "Prints the description of the skill to your chat";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("champions.command.skill-description.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("skill", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            skillManager.getObjects().keySet().stream()
                                    .map(name -> name.replace(" ", "_"))
                                    .filter(name -> name.toLowerCase().contains(builder.getRemainingLowerCase()))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            describe(context.getSource().getSender(), context.getArgument("skill", String.class), 1);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(BrigadierNodes.argument("level", IntegerArgumentType.integer(0))
                                .executes(context -> {
                                    describe(context.getSource().getSender(), context.getArgument("skill", String.class),
                                            context.getArgument("level", Integer.class));
                                    return Command.SINGLE_SUCCESS;
                                })));
    }

    private void describe(CommandSender sender, String skillName, int level) {
        final Optional<Skill> skillOptional = skillManager.getObject(skillName.replace("_", " "));
        if (skillOptional.isEmpty()) {
            UtilMessage.message(sender, "core.prefix.skills", "champions.command.skilldescription.invalid-skill",
                    Component.text(skillName, NamedTextColor.YELLOW));
            return;
        }
        final Skill skill = skillOptional.get();
        if (level > skill.getMaxLevel() + 1) {
            UtilMessage.message(sender, "core.prefix.skill", "champions.command.skilldescription.invalid-number",
                    Component.text(skillName, NamedTextColor.GREEN), Component.text("1", NamedTextColor.GREEN),
                    Component.text(String.valueOf(skill.getMaxLevel() + 1), NamedTextColor.GREEN));
            return;
        }
        Component component = Component.empty()
                .append(skill.getDisplayName().color(NamedTextColor.YELLOW))
                .append(Component.text(" ("))
                .append(Component.text(level, NamedTextColor.GREEN))
                .append(Component.text(")"));
        for (Component line : skill.getDescription(level)) {
            component = component.appendNewline().append(line.colorIfAbsent(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        UtilMessage.message(sender, "core.prefix.skill", component);
    }
}
