package me.mykindos.betterpvp.core.command.commands.general;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.effects.Effect;
import me.mykindos.betterpvp.core.effects.EffectManager;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilTime;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * Lists the executor's active effects with their time left.
 */
@Singleton
public class ShowEffectsCommand extends BrigadierCommand {

    private final EffectManager effectManager;

    @Inject
    public ShowEffectsCommand(ClientManager clientManager, EffectManager effectManager) {
        super(clientManager);
        this.effectManager = effectManager;
    }

    @Override
    public String getName() {
        return "showeffects";
    }

    @Override
    public String getDescription() {
        return "List all effects the player currently has";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.show-effects.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    final Optional<ConcurrentHashMap<String, List<Effect>>> effects = effectManager.getObject(player.getUniqueId().toString());
                    if (effects.isEmpty()) {
                        UtilMessage.message(player, "core.prefix.effects", "core.command.showeffects.none");
                        return Command.SINGLE_SUCCESS;
                    }
                    effects.get().values().forEach(effectList -> effectList.forEach(effect ->
                            UtilMessage.message(player, "core.prefix.effects", "core.command.showeffects.entry",
                                    Component.text(effect.getEffectType().getName() + " " + effect.getAmplifier(), NamedTextColor.WHITE),
                                    Component.text(UtilTime.getTime(effect.getRemainingDuration(), 1), NamedTextColor.GREEN))));
                    return Command.SINGLE_SUCCESS;
                });
    }
}
