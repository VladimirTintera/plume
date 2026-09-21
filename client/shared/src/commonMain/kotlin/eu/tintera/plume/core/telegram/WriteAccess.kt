package eu.tintera.plume.core.telegram

/**
 * Jestli uživatel smí do chatu psát, a když ne, proč — podle toho konverzace místo vstupního
 * pole ukáže, co se dělat dá.
 */
enum class WriteAccess {
    Allowed,

    /** Kanál, ve kterém uživatel nemá právo publikovat. Tady se jen čte (a ztlumuje). */
    ChannelReadOnly,

    /** Skupina, kde psaní zakázal správce — celé skupině, nebo jen tomuhle uživateli. */
    Restricted,

    /** Uživatel ve skupině nebo kanálu není (odešel, byl vyhozen, skupina se převedla). */
    NotMember,
}

/** Postavení uživatele ve skupině nebo kanálu, zúžené na to, co rozhoduje o psaní. */
internal sealed interface OwnMemberStatus {
    data class Creator(val isMember: Boolean) : OwnMemberStatus
    data class Admin(val canPostMessages: Boolean) : OwnMemberStatus
    data object Member : OwnMemberStatus
    data class Restricted(val isMember: Boolean, val canSendMessages: Boolean) : OwnMemberStatus
    data object Left : OwnMemberStatus
    data object Banned : OwnMemberStatus
}

/**
 * Pravidlo, kdo smí psát. Čistá funkce kvůli testům (`WriteAccessTest`) — kombinací je dost
 * a na zařízení se každá ověřuje jiným účtem.
 *
 * - [status] `null` = soukromý chat, tam se píše vždycky.
 * - V kanálu smí publikovat jen vlastník a správce s `canPostMessages`; členové a omezení čtou.
 * - Ve skupině rozhoduje [membersCanSend] (výchozí práva celé skupiny, `Chat.permissions`),
 *   u omezeného člena jeho vlastní práva — ta výchozí přebíjejí. Správce a vlastník píšou vždy.
 */
internal fun writeAccess(isChannel: Boolean, status: OwnMemberStatus?, membersCanSend: Boolean): WriteAccess =
    when (status) {
        null -> WriteAccess.Allowed
        is OwnMemberStatus.Creator -> if (status.isMember) WriteAccess.Allowed else WriteAccess.NotMember
        is OwnMemberStatus.Admin -> when {
            !isChannel || status.canPostMessages -> WriteAccess.Allowed
            else -> WriteAccess.ChannelReadOnly
        }
        OwnMemberStatus.Member -> when {
            isChannel -> WriteAccess.ChannelReadOnly
            membersCanSend -> WriteAccess.Allowed
            else -> WriteAccess.Restricted
        }
        is OwnMemberStatus.Restricted -> when {
            !status.isMember -> WriteAccess.NotMember
            isChannel -> WriteAccess.ChannelReadOnly
            status.canSendMessages -> WriteAccess.Allowed
            else -> WriteAccess.Restricted
        }
        OwnMemberStatus.Left, OwnMemberStatus.Banned -> WriteAccess.NotMember
    }
