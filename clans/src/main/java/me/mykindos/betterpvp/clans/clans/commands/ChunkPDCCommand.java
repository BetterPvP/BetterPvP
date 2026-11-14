package me.mykindos.betterpvp.clans.clans.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.concurrent.atomic.AtomicInteger;
import me.mykindos.betterpvp.clans.clans.ClanManager;
import me.mykindos.betterpvp.clans.utilities.ClansNamespacedKeys;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.framework.CoreNamespaceKeys;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Writes each claimed chunk's clan id into the chunk's persistent data, and clears its block tags.
 */
@Singleton
public class ChunkPDCCommand extends BrigadierCommand {

    private final ClanManager clanManager;

    @Inject
    public ChunkPDCCommand(ClientManager clientManager, ClanManager clanManager) {
        super(clientManager);
        this.clanManager = clanManager;
    }

    @Override
    public String getName() {
        return "chunkpdc";
    }

    @Override
    public String getDescription() {
        return "Assigns PDC to all clan chunks";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("clans.command.chunk-p-d-c.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final AtomicInteger count = new AtomicInteger();
                    clanManager.getObjects().values().forEach(clan -> clan.getTerritory().forEach(territory -> {
                        final PersistentDataContainer data = territory.getWorldChunk().getPersistentDataContainer();
                        data.set(ClansNamespacedKeys.CLAN, PersistentDataType.LONG, clan.getId());
                        data.remove(CoreNamespaceKeys.BLOCK_TAG_CONTAINER_KEY);
                        count.getAndIncrement();
                    }));
                    UtilMessage.message(context.getSource().getSender(), "clans.prefix", "clans.command.chunk-pdc.success",
                            Component.text(count.get(), NamedTextColor.YELLOW));
                    return Command.SINGLE_SUCCESS;
                });
    }
}
