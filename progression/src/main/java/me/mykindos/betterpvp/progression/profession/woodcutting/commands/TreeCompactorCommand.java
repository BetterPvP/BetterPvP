package me.mykindos.betterpvp.progression.profession.woodcutting.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import java.util.Objects;
import lombok.CustomLog;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.cooldowns.CooldownManager;
import me.mykindos.betterpvp.core.item.BaseItem;
import me.mykindos.betterpvp.core.item.ItemFactory;
import me.mykindos.betterpvp.core.item.ItemRegistry;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilInventory;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.progression.profession.skill.woodcutting.treecompactor.TreeCompactor;
import me.mykindos.betterpvp.progression.profile.ProfessionProfileManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Turns each stack of 64 logs of one type, or of every type, into a compacted log, once the executor has the Tree
 * Compactor skill. In creative it hands out a stack of compacted logs instead.
 */
@CustomLog
@Singleton
public class TreeCompactorCommand extends BrigadierCommand {

    private static final String TREE_COMPACTOR = "Tree Compactor";
    private static final List<String> LOG_TYPES = List.of(
            "Oak", "Birch", "Dark_Oak", "Jungle", "Mangrove",
            "Acacia", "Spruce", "Cherry", "Pale_Oak", "All"
    );

    private final ProfessionProfileManager professionProfileManager;
    private final TreeCompactor treeCompactor;
    private final ItemRegistry itemRegistry;
    private final ItemFactory itemFactory;
    private final CooldownManager cooldownManager;

    @Inject
    public TreeCompactorCommand(ClientManager clientManager, ProfessionProfileManager professionProfileManager, TreeCompactor treeCompactor,
                                ItemRegistry itemRegistry, ItemFactory itemFactory, CooldownManager cooldownManager) {
        super(clientManager);
        this.professionProfileManager = professionProfileManager;
        this.treeCompactor = treeCompactor;
        this.itemRegistry = itemRegistry;
        this.itemFactory = itemFactory;
        this.cooldownManager = cooldownManager;
    }

    @Override
    public @NotNull String getName() {
        return "treecompactor";
    }

    @Override
    public @NotNull String getDescription() {
        return "Compact your logs";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("progression.command.tree-compactor.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    compact(getPlayerFromExecutor(context), null);
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierNodes.argument("logType", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            LOG_TYPES.stream()
                                    .filter(type -> type.toLowerCase().startsWith(builder.getRemainingLowerCase()))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            compact(getPlayerFromExecutor(context), context.getArgument("logType", String.class));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    /**
     * @param logType the log type to compact, or {@code null} when none was given
     */
    private void compact(Player player, @Nullable String logType) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            final ItemStack result = itemFactory.create(getCompactedLog()).createItemStack();
            result.setAmount(64);
            player.getInventory().addItem(result);
            UtilMessage.message(player, "core.prefix.treecompactor", "progression.command.treecompactor.creative-logs");
            return;
        }

        professionProfileManager.getObject(player.getUniqueId().toString()).ifPresent(profile -> {
            if (treeCompactor.getSkillLevel(profile) <= 0) {
                UtilMessage.message(player, "core.prefix.treecompactor", "progression.command.treecompactor.not-unlocked",
                        Component.text("/woodcutting", NamedTextColor.GREEN));
                return;
            }
            if (logType == null) {
                UtilMessage.message(player, "core.prefix.command", Translations.component("progression.command.treecompactor.usage",
                        Component.text("treecompactor <logType>", NamedTextColor.GREEN)).color(NamedTextColor.YELLOW));
                return;
            }

            final @Nullable Material logMaterial = logTypeToMaterial(logType, false);
            if (logMaterial == null) {
                UtilMessage.message(player, "core.prefix.treecompactor", "progression.command.treecompactor.unknown-log-type",
                        Component.text(logType, NamedTextColor.WHITE));
                return;
            }

            // The cooldown manager tells the player when they are still on cooldown
            if (!cooldownManager.use(player, TREE_COMPACTOR, treeCompactor.getCooldown(), true, false)) {
                return;
            }

            final List<Material> logTypesToCompact = logMaterial == Material.AIR
                    ? LOG_TYPES.stream().map(type -> logTypeToMaterial(type, true)).filter(Objects::nonNull).toList()
                    : List.of(logMaterial);

            int compacted = 0;
            for (Material material : logTypesToCompact) {
                while (UtilInventory.contains(player, material, 64)) {
                    if (UtilInventory.remove(player, material, 64)) {
                        player.getInventory().addItem(itemFactory.create(getCompactedLog()).createItemStack());
                        compacted++;
                    }
                }
            }

            UtilMessage.message(player, "core.prefix.treecompactor", "progression.command.treecompactor.compacted",
                    Component.text(compacted + "x", NamedTextColor.GREEN));
            if (compacted == 0) {
                cooldownManager.removeCooldown(player, TREE_COMPACTOR, true);
            }
            log.info("{} compacted {}x logs.", player.getName(), compacted)
                    .addClientContext(player).addLocationContext(player.getLocation()).submit();
        });
    }

    /**
     * Turns a log type into its {@link Material}.
     *
     * @param logType       the log type to convert
     * @param ignoreAllType when false, the {@code All} log type maps to {@link Material#AIR}, and when true to null
     * @return the log's material, or null for an unknown type
     */
    private static @Nullable Material logTypeToMaterial(@NotNull String logType, boolean ignoreAllType) {
        final boolean isAll = logType.equalsIgnoreCase("All");
        if (ignoreAllType && isAll) {
            return null;
        }
        return isAll ? Material.AIR : Material.getMaterial(logType.toUpperCase() + "_LOG");
    }

    private @NotNull BaseItem getCompactedLog() {
        return Objects.requireNonNull(itemRegistry.getItem(new NamespacedKey("progression", "compacted_log")));
    }
}
