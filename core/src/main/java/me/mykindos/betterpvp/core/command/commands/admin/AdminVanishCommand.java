package me.mykindos.betterpvp.core.command.commands.admin;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.effects.EffectManager;
import me.mykindos.betterpvp.core.effects.EffectTypes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * Toggles the executor's command vanish, and broadcasts a fake leave or join so the toggle looks like a reconnect.
 */
@Singleton
public class AdminVanishCommand extends BrigadierCommand {

    private final Set<UUID> vanished = new HashSet<>();
    private final EffectManager effectManager;

    @Inject
    public AdminVanishCommand(ClientManager clientManager, EffectManager effectManager) {
        super(clientManager);
        this.effectManager = effectManager;
        getAliases().add("v");
    }

    @Override
    public String getName() {
        return "vanish";
    }

    @Override
    public String getDescription() {
        return "Become invisible and removes you from the tab list and auto-completions.";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.admin-vanish.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    if (vanished.remove(player.getUniqueId())) {
                        effectManager.removeEffect(player, EffectTypes.VANISH, "commandVanish");
                        UtilMessage.message(player, "core.prefix.vanish", Translations.component("core.command.vanish.disabled").color(NamedTextColor.RED));
                        UtilMessage.broadcast(fakeJoinMessage(player));
                    } else {
                        vanished.add(player.getUniqueId());
                        effectManager.addEffect(player, player, EffectTypes.VANISH, "commandVanish", 1, 100L, true, true, false, null);
                        UtilMessage.message(player, "core.prefix.vanish", Translations.component("core.command.vanish.enabled").color(NamedTextColor.GREEN));
                        UtilMessage.broadcast(fakeLeaveMessage(player));
                    }
                    return Command.SINGLE_SUCCESS;
                });
    }

    private static Component fakeLeaveMessage(Player player) {
        return Translations.component("core.command.vanish.fake_leave", Component.text(player.getName(), NamedTextColor.GRAY))
                .color(NamedTextColor.RED)
                .appendSpace()
                .append(Component.text("(").color(NamedTextColor.GRAY))
                .append(Translations.component("core.command.vanish.safe").color(NamedTextColor.GREEN))
                .append(Component.text(")").color(NamedTextColor.GRAY));
    }

    private static Component fakeJoinMessage(Player player) {
        return Translations.component("core.command.vanish.fake_join", Component.text(player.getName(), NamedTextColor.GRAY))
                .color(NamedTextColor.GREEN);
    }
}
