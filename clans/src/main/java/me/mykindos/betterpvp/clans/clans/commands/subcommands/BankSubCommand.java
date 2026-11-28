package me.mykindos.betterpvp.clans.clans.commands.subcommands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.CustomLog;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.ClanManager;
import me.mykindos.betterpvp.clans.clans.commands.ClanCommand;
import me.mykindos.betterpvp.clans.clans.commands.ClanSubCommand;
import me.mykindos.betterpvp.clans.clans.writer.ClanBank;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.gamer.Gamer;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.SubCommand;
import me.mykindos.betterpvp.core.components.clans.data.ClanMember;
import me.mykindos.betterpvp.core.framework.economy.ledger.LedgerParty;
import me.mykindos.betterpvp.core.utilities.UtilFormat;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.ledger.LedgerStatus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@CustomLog
@Singleton
@SubCommand(ClanCommand.class)
public class BankSubCommand extends ClanSubCommand {

    @Inject
    public BankSubCommand(ClanManager clanManager, ClientManager clientManager) {
        super(clanManager, clientManager);
    }

    @Override
    public String getName() {
        return "bank";
    }

    @Override
    public String getDescription() {
        return "clans.command.bank.description";
    }

    @Override
    public String getUsage() {
        return super.getUsage() + " [deposit|withdraw]";
    }

    @Override
    public void execute(Player player, Client client, String... args) {

        Clan clan = clanManager.getClanByPlayer(player).orElseThrow();

        if (!clan.getMember(player.getUniqueId()).hasRank(ClanMember.MemberRank.MEMBER)) {
            UtilMessage.message(player, CLANS_PREFIX, "clans.command.clan.bank.recruits-no-access");
            return;
        }

       UtilMessage.message(player, CLANS_PREFIX, "clans.command.clan.bank.balance", Component.text("$" + UtilFormat.formatNumber(clan.getBalance()), NamedTextColor.GREEN));

    }

    @Override
    public ClanMember.MemberRank getRequiredMemberRank() {
        return ClanMember.MemberRank.ADMIN;
    }

    @Override
    public String getArgumentType(int arg) {
        return arg == 1 ? ArgumentType.SUBCOMMAND.name() : ArgumentType.NONE.name();
    }

    @Singleton
    @SubCommand(BankSubCommand.class)
    private static class BankWithdrawSubCommand extends ClanSubCommand {

        private final ClanBank bank;

        @Inject
        public BankWithdrawSubCommand(ClanManager clanManager, ClientManager clientManager, ClanBank bank) {
            super(clanManager, clientManager);
            this.bank = bank;
        }

        @Override
        public String getName() {
            return "withdraw";
        }

        @Override
        public String getDescription() {
        return "clans.command.bank-withdraw.description";
    }

        @Override
        public String getUsage() {
            return super.getUsage() + " <amount>";
        }

        @Override
        public void execute(Player player, Client client, String... args) {

            if(args.length != 2) {
                UtilMessage.message(player, CLANS_PREFIX, "clans.command.clan.bank.withdraw.usage");
                return;
            }

            Clan clan = clanManager.getClanByPlayer(player).orElseThrow();

            if (!clan.getMember(player.getUniqueId()).hasRank(ClanMember.MemberRank.ADMIN)) {
                UtilMessage.message(player, CLANS_PREFIX, "clans.command.clan.bank.withdraw.no-rank");
                return;
            }

            Gamer gamer = client.getGamer();
            try {
                int amountToWithdraw = Integer.parseInt(args[1]);
                if(clan.getBalance() < amountToWithdraw) {
                    UtilMessage.message(player, CLANS_PREFIX, "clans.command.clan.bank.withdraw.insufficient-funds");
                    return;
                }

                if(amountToWithdraw <= 0) {
                    UtilMessage.message(player, CLANS_PREFIX, "clans.command.clan.bank.withdraw.invalid-amount");
                    return;
                }


                bank.move(bank.account(clan), LedgerParty.of(gamer), amountToWithdraw, "clan.bank.withdraw")
                        .thenAccept(receipt -> {
                            if (!receipt.isPosted()) {
                                UtilMessage.message(player, CLANS_PREFIX, receipt.getStatus() == LedgerStatus.INSUFFICIENT_FUNDS
                                        ? "clans.command.clan.bank.withdraw.insufficient-funds"
                                        : "core.ledger.unavailable");
                                return;
                            }
                            clan.getMembers().forEach(member -> {
                                Player clanPlayer = Bukkit.getPlayer(member.getUuid());
                                if (clanPlayer != null) {
                                    UtilMessage.message(clanPlayer, CLANS_PREFIX, "clans.command.clan.bank.withdraw.success",
                                            Component.text(player.getName(), NamedTextColor.YELLOW),
                                            Component.text("$" + amountToWithdraw, NamedTextColor.GREEN));
                                }
                            });
                            log.info("{} withdrew ${} from clan {}", player.getName(), amountToWithdraw, clan.getId()).submit();
                        });
            }catch(NumberFormatException ex) {
                UtilMessage.message(player, CLANS_PREFIX, "clans.command.clan.bank.withdraw.not-number");
            }

        }

        @Override
        public ClanMember.MemberRank getRequiredMemberRank() {
            return ClanMember.MemberRank.ADMIN;
        }

        @Override
        public String getArgumentType(int arg) {
            return ArgumentType.NONE.name();
        }

    }

    @Singleton
    @SubCommand(BankSubCommand.class)
    private static class BankDepositSubCommand extends ClanSubCommand {

        private final ClanBank bank;

        @Inject
        public BankDepositSubCommand(ClanManager clanManager, ClientManager clientManager, ClanBank bank) {
            super(clanManager, clientManager);
            this.bank = bank;
        }

        @Override
        public String getName() {
            return "deposit";
        }

        @Override
        public String getDescription() {
        return "clans.command.bank-deposit.description";
    }

        @Override
        public String getUsage() {
            return super.getUsage() + " <amount>";
        }

        @Override
        public void execute(Player player, Client client, String... args) {

            if(args.length != 2) {
                UtilMessage.message(player, CLANS_PREFIX, "clans.command.clan.bank.deposit.usage");
                return;
            }

            Clan clan = clanManager.getClanByPlayer(player).orElseThrow();
            Gamer gamer = client.getGamer();
            try {
                int amountToWithdraw = Integer.parseInt(args[1]);
                if(gamer.getBalance() < amountToWithdraw) {
                    UtilMessage.message(player, CLANS_PREFIX, "clans.command.clan.bank.deposit.insufficient-funds");
                    return;
                }

                if(amountToWithdraw <= 0) {
                    UtilMessage.message(player, CLANS_PREFIX, "clans.command.clan.bank.deposit.invalid-amount");
                    return;
                }

                bank.move(LedgerParty.of(gamer), bank.account(clan), amountToWithdraw, "clan.bank.deposit")
                        .thenAccept(receipt -> {
                            if (!receipt.isPosted()) {
                                UtilMessage.message(player, CLANS_PREFIX, receipt.getStatus() == LedgerStatus.INSUFFICIENT_FUNDS
                                        ? "clans.command.clan.bank.deposit.insufficient-funds"
                                        : "core.ledger.unavailable");
                                return;
                            }
                            clan.getMembers().forEach(member -> {
                                Player clanPlayer = Bukkit.getPlayer(member.getUuid());
                                if (clanPlayer != null) {
                                    UtilMessage.message(clanPlayer, CLANS_PREFIX, "clans.command.clan.bank.deposit.success",
                                            Component.text(player.getName(), NamedTextColor.YELLOW),
                                            Component.text("$" + amountToWithdraw, NamedTextColor.GREEN));
                                }
                            });
                            log.info("{} deposited ${} into clan {}", player.getName(), amountToWithdraw, clan.getId()).submit();
                        });
            }catch(NumberFormatException ex) {
                UtilMessage.message(player, CLANS_PREFIX, "clans.command.clan.bank.deposit.not-number");
            }

        }

        @Override
        public String getArgumentType(int arg) {
            return ArgumentType.NONE.name();
        }

    }

}
