package eu.tintera.plume.core.telegram

import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.call_cancelled
import eu.tintera.plume.resources.call_declined
import eu.tintera.plume.resources.call_incoming
import eu.tintera.plume.resources.call_missed
import eu.tintera.plume.resources.call_outgoing
import eu.tintera.plume.resources.group_call_active
import eu.tintera.plume.resources.group_call_cancelled
import eu.tintera.plume.resources.group_call_incoming
import eu.tintera.plume.resources.group_call_missed
import eu.tintera.plume.resources.group_call_outgoing
import eu.tintera.plume.resources.group_video_call_active
import eu.tintera.plume.resources.group_video_call_cancelled
import eu.tintera.plume.resources.group_video_call_incoming
import eu.tintera.plume.resources.group_video_call_missed
import eu.tintera.plume.resources.group_video_call_outgoing
import eu.tintera.plume.resources.video_call_cancelled
import eu.tintera.plume.resources.video_call_declined
import eu.tintera.plume.resources.video_call_incoming
import eu.tintera.plume.resources.video_call_missed
import eu.tintera.plume.resources.video_call_outgoing
import org.jetbrains.compose.resources.StringResource

/**
 * Jak hovor skončil, jak to hlásí TDLib (`CallDiscardReason`). Doména si drží vlastní výčet,
 * aby pravidlo [callOutcome] šlo testovat bez TDLib.
 */
enum class CallEnd {
    /** Nikdo to nevzal. */
    Missed,

    /** Volaný hovor odmítl (i „obsazeno"). */
    Declined,

    /** Spojení spadlo. */
    Disconnected,

    /** Běžné zavěšení. */
    HungUp,

    /** Důvod neznámý. */
    Unknown,
}

/**
 * Co se uživateli o hovoru řekne — jediná informace, kvůli které se na zprávu o hovoru dívá.
 * Holé „Hovor" neříkalo ani směr, ani jestli se vůbec mluvilo.
 */
enum class CallOutcome {
    Outgoing,
    Incoming,

    /** Příchozí hovor, který uživatel nevzal. */
    Missed,

    /** Příchozí hovor, který uživatel odmítl. */
    Declined,

    /** Odchozí hovor, který protějšek nevzal nebo odmítl. */
    Cancelled,
}

/**
 * Pravidlo je stejné jako u oficiálních klientů: u ODCHOZÍHO hovoru je nevzatý i odmítnutý
 * „zrušený" — z pohledu volajícího se nedovolal, a rozlišovat, jestli protějšek zmáčkl
 * červené tlačítko, nebo nechal zvonit, by prozrazovalo víc, než Telegram jinde ukazuje.
 * U příchozího naopak záleží, jestli ho uživatel zmeškal, nebo odmítl sám.
 *
 * Spadlé spojení se počítá jako normální hovor: mluvilo se, jen konec nebyl čistý.
 */
fun callOutcome(isOutgoing: Boolean, end: CallEnd): CallOutcome = when (end) {
    CallEnd.Missed -> if (isOutgoing) CallOutcome.Cancelled else CallOutcome.Missed
    CallEnd.Declined -> if (isOutgoing) CallOutcome.Cancelled else CallOutcome.Declined
    CallEnd.Disconnected, CallEnd.HungUp, CallEnd.Unknown ->
        if (isOutgoing) CallOutcome.Outgoing else CallOutcome.Incoming
}

/** Hovor se nepodařil — kreslí se chybovou barvou. Zrušený odchozí ne: to je jen „nedovolal jsem se". */
val CallOutcome.isFailure: Boolean get() = this == CallOutcome.Missed || this == CallOutcome.Declined

/**
 * Název zprávy o hovoru: v bublině, v náhledu seznamu chatů, v citaci i v notifikaci.
 *
 * Skupinový hovor ([isGroup]) odmítnutý nezná — TDLib hlásí jen „zmeškaný" (`wasMissed`),
 * a dokud běží ([isActive]), na výsledku nezáleží.
 */
fun CallOutcome.title(isVideo: Boolean, isGroup: Boolean = false, isActive: Boolean = false): StringResource =
    if (isGroup) groupTitle(isVideo, isActive) else directTitle(isVideo)

private fun CallOutcome.groupTitle(isVideo: Boolean, isActive: Boolean): StringResource = when {
    isActive -> if (isVideo) Res.string.group_video_call_active else Res.string.group_call_active
    this == CallOutcome.Outgoing -> if (isVideo) Res.string.group_video_call_outgoing else Res.string.group_call_outgoing
    this == CallOutcome.Incoming -> if (isVideo) Res.string.group_video_call_incoming else Res.string.group_call_incoming
    this == CallOutcome.Cancelled -> if (isVideo) Res.string.group_video_call_cancelled else Res.string.group_call_cancelled
    else -> if (isVideo) Res.string.group_video_call_missed else Res.string.group_call_missed
}

private fun CallOutcome.directTitle(isVideo: Boolean): StringResource = when (this) {
    CallOutcome.Outgoing -> if (isVideo) Res.string.video_call_outgoing else Res.string.call_outgoing
    CallOutcome.Incoming -> if (isVideo) Res.string.video_call_incoming else Res.string.call_incoming
    CallOutcome.Missed -> if (isVideo) Res.string.video_call_missed else Res.string.call_missed
    CallOutcome.Declined -> if (isVideo) Res.string.video_call_declined else Res.string.call_declined
    CallOutcome.Cancelled -> if (isVideo) Res.string.video_call_cancelled else Res.string.call_cancelled
}
