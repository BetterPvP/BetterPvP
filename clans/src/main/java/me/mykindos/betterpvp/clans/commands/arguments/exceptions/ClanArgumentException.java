package me.mykindos.betterpvp.clans.commands.arguments.exceptions;

import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.Dynamic3CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.mykindos.betterpvp.core.utilities.UtilTime;

import static me.mykindos.betterpvp.core.command.brigadier.arguments.CommandMessages.translatable;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ClanArgumentException {

    public static final DynamicCommandExceptionType MUST_NOT_BE_IN_A_CLAN_EXCEPTION = new DynamicCommandExceptionType(
            (player) -> translatable("clans.command.error.must_not_be_in_clan", player)
    );
    public static final Dynamic3CommandExceptionType INVALID_CLAN_NAME = new Dynamic3CommandExceptionType((name, min, max) ->
        translatable("clans.command.error.invalid_clan_name", name, min, max)
    );
    public static final DynamicCommandExceptionType NAME_ALREADY_EXISTS = new DynamicCommandExceptionType((name) ->
            translatable("clans.command.error.name_taken", name)
    );
    public static final DynamicCommandExceptionType NAME_IS_FILTERED = new DynamicCommandExceptionType((name) ->
            translatable("clans.command.error.name_filtered", name)
    );
    public static final SimpleCommandExceptionType MEMBER_CLAN_CANNOT_ACTION_SELF = new SimpleCommandExceptionType(
            translatable("clans.command.error.cannot_target_self")
    );
    public static final DynamicCommandExceptionType MEMBER_CANNOT_ACTION_MEMBER_RANK = new DynamicCommandExceptionType(
            (targetName) -> translatable("clans.command.error.rank_too_low_for_target", targetName)
    );
    public static final Dynamic2CommandExceptionType MEMBER_NOT_MEMBER_OF_CLAN = new Dynamic2CommandExceptionType(
            (clanName, targetName) -> translatable("clans.command.error.not_member_of_clan", clanName, targetName)
    );
    public static final DynamicCommandExceptionType TARGET_MEMBER_RANK_TOO_LOW = new DynamicCommandExceptionType(
            (targetName) -> translatable("clans.command.error.target_rank_too_low", targetName)
    );
    public static final DynamicCommandExceptionType TARGET_MEMBER_IN_ENEMY_TERRITORY = new DynamicCommandExceptionType(
            (targetName) -> translatable("clans.command.error.target_in_enemy_territory", targetName)
    );
    public static final DynamicCommandExceptionType UNKNOWN_CLAN_NAME_EXCEPTION = new DynamicCommandExceptionType(
            (name) -> translatable("clans.command.error.unknown_clan_name", name)
    );
    public static final DynamicCommandExceptionType NOT_IN_A_CLAN_EXCEPTION = new DynamicCommandExceptionType(
            (player) -> translatable("clans.command.error.not_in_clan", player)
    );
    public static final SimpleCommandExceptionType MUST_BE_IN_A_CLAN_EXCEPTION = new SimpleCommandExceptionType(
            translatable("clans.command.error.must_be_in_clan")
    );
    public static final Clan2CommandExceptionType CLAN_MUST_NOT_BE_SAME = new Clan2CommandExceptionType(
            (origin, target) -> translatable("clans.command.error.same_clan", origin.getName(), target.getName())
    );
    //Ally
    public static final Clan2CommandExceptionType CLAN_NOT_NEUTRAL_OF_CLAN = new Clan2CommandExceptionType(
            (origin, target) -> translatable("clans.command.error.not_neutral", origin.getName(), target.getName())
    );
    public static final Clan2CommandExceptionType CLAN_NOT_ENEMY_OF_CLAN = new Clan2CommandExceptionType(
            (origin, target) -> translatable("clans.command.error.not_enemy", origin.getName(), target.getName())
    );
    public static final Clan2CommandExceptionType CLAN_NOT_ALLY_OF_CLAN = new Clan2CommandExceptionType(
            (origin, target) -> translatable("clans.command.error.not_ally", origin.getName(), target.getName())
    );
    public static final Clan2CommandExceptionType CLAN_NOT_ALLY_OR_ENEMY_OF_CLAN = new Clan2CommandExceptionType(
            (origin, target) -> translatable("clans.command.error.not_ally_or_enemy", origin.getName(), target.getName()));
    public static final Dynamic2CommandExceptionType CLAN_AT_MAX_SQUAD_COUNT_ALLY = new Dynamic2CommandExceptionType(
            (origin, size) -> translatable("clans.command.error.max_squad_ally", origin, size)
    );
    public static final Dynamic2CommandExceptionType CLAN_OVER_MAX_SQUAD_COUNT_ALLY = new Dynamic2CommandExceptionType(
            (clanName, size) -> translatable("clans.command.error.over_max_squad_ally", clanName, size)
    );
    public static final Clan2CommandExceptionType CLAN_ALREADY_TRUSTS_CLAN = new Clan2CommandExceptionType(
            (origin, target) -> translatable("clans.command.error.already_trusted", origin.getName(), target.getName())
    );
    public static final Clan2CommandExceptionType CLAN_DOES_NOT_TRUST_CLAN = new Clan2CommandExceptionType(
            (origin, target) -> translatable("clans.command.error.not_trusted", origin.getName(), target.getName())
    );

    //enemy
    public static final Clan2CommandExceptionType CLAN_CANNOT_ACTION_CLAN_WHILE_PILLAGING = new Clan2CommandExceptionType(
            (origin, target) -> translatable("clans.command.error.pillaging_clan", origin.getName(), target.getName())
            );

    public static final ClanCommandExceptionType CLAN_CANNOT_ACTION_WHILE_BEING_PILLAGING = new ClanCommandExceptionType(
            (origin) -> translatable("clans.command.error.being_pillaged", origin.getName())
    );

    //Invite
    public static final Dynamic3CommandExceptionType CLAN_AT_MAX_SQUAD_COUNT_INVITE = new Dynamic3CommandExceptionType(
            (originName, targetName, size) -> translatable("clans.command.error.max_squad_invite", originName, targetName, size)
    );
    public static final Dynamic3CommandExceptionType ALLY_AT_MAX_SQUAD_COUNT_INVITE = new Dynamic3CommandExceptionType(
            (allyName, targetName, size) -> translatable("clans.command.error.ally_max_squad_invite", allyName, targetName, size)
    );

    //Join
    public static final Dynamic2CommandExceptionType CLAN_AT_MAX_SQUAD_COUNT_JOIN = new Dynamic2CommandExceptionType(
            (originName, size) -> translatable("clans.command.error.max_squad_join", originName, size)
    );
    public static final Dynamic3CommandExceptionType ALLY_AT_MAX_SQUAD_COUNT_JOIN = new Dynamic3CommandExceptionType(
            (originName, allyName, size) -> translatable("clans.command.error.ally_max_squad_join", originName, allyName, size)
    );

    //leave
    public static final SimpleCommandExceptionType LEADER_CANNOT_LEAVE = new SimpleCommandExceptionType(
            translatable("clans.command.error.leader_cannot_leave")
    );

    public static final SimpleCommandExceptionType CANNOT_LEAVE_IN_ENEMY_TERRITORY = new SimpleCommandExceptionType(
            translatable("clans.command.error.leave_in_enemy_territory")
    );

    //claim
    public static final SimpleCommandExceptionType CANNOT_CLAIM_MORE_TERRITORY = new SimpleCommandExceptionType(
            translatable("clans.command.error.claim_limit")
    );

    public static final SimpleCommandExceptionType CANNOT_CLAIM_WORLD = new SimpleCommandExceptionType(
            translatable("clans.command.error.claim_world")
    );

    public static final ClanCommandExceptionType CLAN_ALREADY_CLAIMS_TERRITORY = new ClanCommandExceptionType(
            clan -> translatable("clans.command.error.already_claimed", clan.getName())
    );

    public static final SimpleCommandExceptionType CANNOT_CLAIM_WITH_ENEMIES = new SimpleCommandExceptionType(
            translatable("clans.command.error.claim_with_enemies")
    );

    public static final SimpleCommandExceptionType CANNOT_CLAIM_ADJACENT_TO_OTHER_TERRITORY = new SimpleCommandExceptionType(
            translatable("clans.command.error.claim_next_to_other_clan")
    );

    public static final SimpleCommandExceptionType MUST_CLAIM_TERRITORY_ADJACENT_TO_OWN_TERRITORY = new SimpleCommandExceptionType(
            translatable("clans.command.error.claim_not_next_to_own")
    );

    public static final DynamicCommandExceptionType TERRITORY_ON_CLAIM_COOLDOWN = new DynamicCommandExceptionType(
            time -> translatable("clans.command.error.claim_cooldown", UtilTime.getTime((Double) time, 1))
    );

    //unclaim
    public static final SimpleCommandExceptionType NOT_ON_CLAIMED_TERRITORY = new SimpleCommandExceptionType(
            translatable("clans.command.error.not_on_claimed_territory")
    );

    public static final SimpleCommandExceptionType CANNOT_UNCLAIM_FROM_CLAN = new SimpleCommandExceptionType(
            translatable("clans.command.error.cannot_unclaim")
    );

    public static final ClanCommandExceptionType CLAN_ABLE_TO_RETAIN_TERRITORY = new ClanCommandExceptionType(
            clan -> translatable("clans.command.error.clan_retains_territory", clan.getName())
    );

    public static final ClanCommandExceptionType CLAN_UNCLAIM_SPLIT_TERRITORY = new ClanCommandExceptionType(
            clan -> translatable("clans.command.error.unclaim_splits_territory", clan.getName())
    );

    public static final SimpleCommandExceptionType NO_NEARBY_WILDERNESS = new SimpleCommandExceptionType(
            translatable("clans.command.error.no_nearby_wilderness")
    );

    //core
    public static final ClanCommandExceptionType NO_CORE_SET = new ClanCommandExceptionType(
            clan -> translatable("clans.command.error.no_core_set", clan.getName())
    );

    public static final DynamicCommandExceptionType CLAN_DOES_NOT_EXIST = new DynamicCommandExceptionType(
            target -> translatable("clans.command.error.clan_does_not_exist", target)
    );
}
