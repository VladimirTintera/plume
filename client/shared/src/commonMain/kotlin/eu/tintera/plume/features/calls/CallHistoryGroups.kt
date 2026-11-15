package eu.tintera.plume.features.calls

import androidx.compose.runtime.Immutable
import eu.tintera.plume.core.telegram.CallHistoryEntry
import eu.tintera.plume.core.ui.isSameDayAs

/**
 * Řádek historie hovorů: jeden hovor, nebo několik stejných po sobě (pět zmeškaných od jednoho
 * člověka během odpoledne). [entries] jsou od nejnovějšího, řádek ukazuje první. [source] je
 * účet ve společné historii víc účtů (pořadí zdroje), jinak nula.
 */
@Immutable
data class CallHistoryGroup(val entries: List<CallHistoryEntry>, val source: Int = 0) {
    val latest: CallHistoryEntry get() = entries.first()
    val count: Int get() = entries.size
    val messageIds: List<Long> get() = entries.map { it.messageId }

    /** Klíč seznamu: nejnovější hovor řádku; totéž id chatu i zprávy může mít víc účtů. */
    val key: String get() = "$source-${latest.chat.id}-${latest.messageId}"
}

/** Hovor ve společné historii víc účtů: [source] je pořadí účtu ve zdrojích. */
data class SourcedCall(val source: Int, val entry: CallHistoryEntry)

/**
 * Sloučí hovory, které jdou v historii **těsně po sobě** se stejným chatem, stejným výsledkem
 * (zmeškaný, odchozí…) a druhem (video, skupinový), a to **v jednom dni** — jako v telefonu
 * a v oficiálním Telegramu. Mezi dva zmeškané vložený přijatý hovor řadu přeruší: pořadí
 * událostí se slučováním nesmí ztratit.
 */
fun groupCallHistory(entries: List<CallHistoryEntry>): List<CallHistoryGroup> =
    groupSourcedCallHistory(entries.map { SourcedCall(0, it) })

/** [groupCallHistory] pro společnou historii: hovory různých účtů se nikdy neslučují. */
fun groupSourcedCallHistory(calls: List<SourcedCall>): List<CallHistoryGroup> {
    val groups = mutableListOf<MutableList<SourcedCall>>()
    calls.forEach { call ->
        val last = groups.lastOrNull()?.last()
        if (last != null && last.source == call.source && last.entry.sameRowAs(call.entry)) groups.last() += call else groups += mutableListOf(call)
    }
    return groups.map { group -> CallHistoryGroup(group.map { it.entry }, group.first().source) }
}

private fun CallHistoryEntry.sameRowAs(other: CallHistoryEntry): Boolean =
    chat.id == other.chat.id &&
        outcome == other.outcome &&
        isVideo == other.isVideo &&
        isGroup == other.isGroup &&
        date.isSameDayAs(other.date)
