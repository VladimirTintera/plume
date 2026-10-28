package eu.tintera.plume.features.calls

import androidx.compose.runtime.Immutable
import eu.tintera.plume.core.telegram.CallHistoryEntry
import eu.tintera.plume.core.ui.isSameDayAs

/**
 * Řádek historie hovorů: jeden hovor, nebo několik stejných po sobě (pět zmeškaných od jednoho
 * člověka během odpoledne). [entries] jsou od nejnovějšího, řádek ukazuje první.
 */
@Immutable
data class CallHistoryGroup(val entries: List<CallHistoryEntry>) {
    val latest: CallHistoryEntry get() = entries.first()
    val count: Int get() = entries.size
    val messageIds: List<Long> get() = entries.map { it.messageId }

    /** Klíč seznamu: nejnovější hovor řádku. */
    val key: String get() = "${latest.chat.id}-${latest.messageId}"
}

/**
 * Sloučí hovory, které jdou v historii **těsně po sobě** se stejným chatem, stejným výsledkem
 * (zmeškaný, odchozí…) a druhem (video, skupinový), a to **v jednom dni** — jako v telefonu
 * a v oficiálním Telegramu. Mezi dva zmeškané vložený přijatý hovor řadu přeruší: pořadí
 * událostí se slučováním nesmí ztratit.
 */
fun groupCallHistory(entries: List<CallHistoryEntry>): List<CallHistoryGroup> {
    val groups = mutableListOf<MutableList<CallHistoryEntry>>()
    entries.forEach { entry ->
        val last = groups.lastOrNull()?.last()
        if (last != null && last.sameRowAs(entry)) groups.last() += entry else groups += mutableListOf(entry)
    }
    return groups.map(::CallHistoryGroup)
}

private fun CallHistoryEntry.sameRowAs(other: CallHistoryEntry): Boolean =
    chat.id == other.chat.id &&
        outcome == other.outcome &&
        isVideo == other.isVideo &&
        isGroup == other.isGroup &&
        date.isSameDayAs(other.date)
