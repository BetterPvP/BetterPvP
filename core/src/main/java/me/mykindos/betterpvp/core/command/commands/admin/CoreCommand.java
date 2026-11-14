package me.mykindos.betterpvp.core.command.commands.admin;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.client.Rank;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCoreCommandLoader;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.loader.CoreCommandLoader;
import me.mykindos.betterpvp.core.content.manifest.ManifestPublisher;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.resourcepack.ResourcePackHandler;
import me.mykindos.betterpvp.core.client.punishments.rules.RuleManager;
import me.mykindos.betterpvp.core.tips.TipManager;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.model.Reloadable;
import me.mykindos.betterpvp.core.world.schematic.SchematicService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /core reload} reloads Core's config, reloadables and commands. {@code /core publishmanifest} re-publishes the
 * game content manifest to the database.
 */
@Singleton
public class CoreCommand extends BrigadierCommand {

    private final Core core;
    private final BrigadierCoreCommandLoader brigadierCoreCommandLoader;
    private final CoreCommandLoader commandLoader;
    private final TipManager tipManager;
    private final ResourcePackHandler resourcePackHandler;
    private final RuleManager ruleManager;
    private final SchematicService schematicService;
    private final ManifestPublisher manifestPublisher;

    @Inject
    public CoreCommand(ClientManager clientManager, Core core, BrigadierCoreCommandLoader brigadierCoreCommandLoader,
                       CoreCommandLoader commandLoader, TipManager tipManager, ResourcePackHandler resourcePackHandler,
                       RuleManager ruleManager, SchematicService schematicService, ManifestPublisher manifestPublisher) {
        super(clientManager);
        this.core = core;
        this.brigadierCoreCommandLoader = brigadierCoreCommandLoader;
        this.commandLoader = commandLoader;
        this.tipManager = tipManager;
        this.resourcePackHandler = resourcePackHandler;
        this.ruleManager = ruleManager;
        this.schematicService = schematicService;
        this.manifestPublisher = manifestPublisher;
    }

    @Override
    public String getName() {
        return "core";
    }

    @Override
    public String getDescription() {
        return "Base core command";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.core.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.literal("reload")
                        .executes(context -> {
                            reload(context.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(BrigadierNodes.literal("publishmanifest")
                        .executes(context -> {
                            clientManager.sendMessageToRank("core.prefix.core", Translations.component("core.command.core.publishmanifest.started",
                                    Component.text(context.getSource().getSender().getName(), NamedTextColor.YELLOW)), Rank.ADMIN);
                            manifestPublisher.publish();
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private void reload(CommandSender sender) {
        core.reload();
        core.getReloadables().forEach(Reloadable::reload);

        brigadierCoreCommandLoader.reload();
        Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
        commandLoader.reload(core.getClass().getPackageName());
        tipManager.reloadTips(core);
        resourcePackHandler.reload();
        ruleManager.reload(core);
        schematicService.clearCache();

        UtilMessage.message(sender, "core.prefix.core", "core.command.core.reload.success");
    }
}
