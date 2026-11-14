package me.mykindos.betterpvp.clans.clans.map.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.clans.clans.map.ClanMapService;
import me.mykindos.betterpvp.clans.clans.map.MapHandler;
import me.mykindos.betterpvp.clans.clans.map.terrain.MapTerrainService;
import me.mykindos.betterpvp.core.client.Rank;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.item.ItemFactory;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilInventory;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.model.SoundEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Gives the executor a clan map, unless they already have one. Admins can also save, reset or redraw the map.
 */
@Singleton
public class MapCommand extends BrigadierCommand {

    private final ItemFactory itemFactory;
    private final ClanMapService clanMapService;
    private final MapHandler mapHandler;
    private final MapTerrainService terrainService;

    @Inject
    public MapCommand(ClientManager clientManager, ItemFactory itemFactory, ClanMapService clanMapService, MapHandler mapHandler,
                      MapTerrainService terrainService) {
        super(clientManager);
        this.itemFactory = itemFactory;
        this.clanMapService = clanMapService;
        this.mapHandler = mapHandler;
        this.terrainService = terrainService;
    }

    @Override
    public String getName() {
        return "map";
    }

    @Override
    public String getDescription() {
        return "Get a map of the world";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("clans.command.map.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    new SoundEffect(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0f, 1f).play(player);
                    if (UtilInventory.contains(player, Material.FILLED_MAP, 1)) {
                        UtilMessage.message(player, "clans.prefix", Translations.component("clans.command.map.already-have").color(NamedTextColor.RED));
                        return Command.SINGLE_SUCCESS;
                    }
                    final ItemStack itemStack = clanMapService.createMapItem();
                    player.getInventory().addItem(itemFactory.convertItemStack(itemStack).orElse(itemStack));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierNodes.literal("save")
                        .requires(source -> senderHasRank(source, Rank.ADMIN))
                        .executes(context -> {
                            mapHandler.saveMapData();
                            UtilMessage.message(context.getSource().getSender(), "clans.prefix", "clans.command.map.save.success");
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(BrigadierNodes.literal("reset")
                        .requires(source -> senderHasRank(source, Rank.ADMIN))
                        .executes(context -> {
                            final Player player = getPlayerFromExecutor(context);
                            mapHandler.resetMapData(player.getWorld());
                            UtilMessage.message(player, "clans.prefix", "clans.command.map.reset.success");
                            // Rebuild the whole displayable area from the world's terrain, asynchronously
                            terrainService.regenerate(player.getWorld(), player);
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(BrigadierNodes.literal("redraw")
                        .requires(source -> senderHasRank(source, Rank.ADMIN))
                        .executes(context -> {
                            final Player player = getPlayerFromExecutor(context);
                            clanMapService.getOrCreateMapSettings(player).setForceRedraw(true);
                            UtilMessage.message(player, "clans.prefix", "clans.command.map.redraw.started");
                            return Command.SINGLE_SUCCESS;
                        }));
    }
}
