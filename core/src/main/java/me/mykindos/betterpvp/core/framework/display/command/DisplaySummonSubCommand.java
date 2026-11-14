package me.mykindos.betterpvp.core.framework.display.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Arrays;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.framework.display.DisplayEditorManager;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;

/**
 * {@code /display summon block <material>} spawns a block display where the executor stands and selects it.
 */
@Singleton
@BrigadierSubCommand(DisplayCommand.class)
public class DisplaySummonSubCommand extends BrigadierCommand {

    private final DisplayEditorManager displayEditorManager;

    @Inject
    public DisplaySummonSubCommand(ClientManager clientManager, DisplayEditorManager displayEditorManager) {
        super(clientManager);
        this.displayEditorManager = displayEditorManager;
    }

    @Override
    public String getName() {
        return "summon";
    }

    @Override
    public String getDescription() {
        return "Summon a display entity";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.display-summon.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    UtilMessage.message(context.getSource().getSender(), "core.prefix.display", "core.display.summon.usage");
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierNodes.literal("block")
                        .executes(context -> {
                            UtilMessage.message(context.getSource().getSender(), "core.prefix.display", "core.display.summon.block_usage");
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(BrigadierNodes.argument("block", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    Arrays.stream(Material.values())
                                            .filter(Material::isBlock)
                                            .map(material -> material.getKey().getKey())
                                            .filter(key -> key.startsWith(builder.getRemainingLowerCase()))
                                            .forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    final Player player = getPlayerFromExecutor(context);
                                    final String blockName = context.getArgument("block", String.class);
                                    final Material material = Material.matchMaterial(blockName);
                                    if (material == null) {
                                        UtilMessage.message(player, "core.prefix.display", "core.display.summon.invalid_block", Component.text(blockName));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    final Location location = player.getLocation();
                                    location.setYaw(0);
                                    location.setPitch(0);
                                    final BlockDisplay display = player.getWorld().spawn(location, BlockDisplay.class);
                                    display.setBlock(Bukkit.createBlockData(material));
                                    displayEditorManager.selectDisplay(player, display);
                                    UtilMessage.message(player, "core.prefix.display", "core.display.summon.success");
                                    return Command.SINGLE_SUCCESS;
                                })));
    }
}
