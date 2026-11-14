package me.mykindos.betterpvp.clans.clans.commands.subcommands.brigadier.general;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.clans.clans.ClanManager;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("/clan help")
class BrigadierHelpSubCommandTest {

    private static ParseResults<CommandSourceStack> parse(String input) {
        final CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(new BrigadierHelpSubCommand(mock(ClientManager.class), mock(ClanManager.class)).define());
        return dispatcher.parse(input, mock(CommandSourceStack.class));
    }

    @Test
    @DisplayName("CLANS-42 F2 page 0 is not a page, so it cannot reach the paging code")
    void pageZeroIsRejected() {
        assertTrue(parse("help 1").getExceptions().isEmpty());
        assertFalse(parse("help 0").getContext().getArguments().containsKey("page"));
    }
}
