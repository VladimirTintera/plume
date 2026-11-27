package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ConversationRepository

/** Zprávy jednoho úseku času ([messagesBetween]) a co z dotazu vyplynulo o okolí. */
internal class MessagesBetween(
    /** Zprávy úseku od nejstarší, bez servisních, nejvýš strop. */
    val messages: List<ChatMessage>,
    /** První zpráva PO úseku (od ní se četlo ke starším); `null`, když po úseku nic není. */
    val nextMessageId: Long?,
    /** Před koncem úseku nic není — chat začíná až po něm (u „Před rokem" se dál nepátrá). */
    val chatStartsAfter: Boolean,
    /** Načetlo se aspoň něco (kalendář se pak přepočítá — TDLib o stažených zprávách nedá vědět). */
    val readAnything: Boolean,
    /**
     * Úsek se přečetl celý (došlo se před jeho začátek, nebo na začátek chatu); `false`, když
     * čtení zastavil strop stránek nebo zpráv — počet je pak jen „aspoň tolik".
     */
    val complete: Boolean,
)

/**
 * Zprávy od [start] do [end] (sekundy epochy, konec bez): od první zprávy po úseku
 * (`firstMessageFrom`) historií ke starším, po stránkách [pageSize], nejvýš [pages] stránek
 * a [cap] zpráv (u dne nad stropem zůstane jeho KONEC). Čte se přes server, takže i den, který
 * zařízení nemá, se přečte celý. Sdílí ho shrnutí dne v kalendáři a „Před rokem".
 */
internal suspend fun ConversationRepository.messagesBetween(
    chatId: Long,
    start: Int,
    end: Int,
    cap: Int,
    pages: Int,
    pageSize: Int = 100,
): MessagesBetween {
    val next = firstMessageFrom(chatId, end).getOrNull()
    var from = next ?: 0L
    val seen = HashSet<Long>()
    val found = mutableListOf<ChatMessage>()
    var startsAfter = false
    var complete = false
    for (page in 0 until pages) {
        val fresh = history(chatId, from, limit = pageSize).getOrNull().orEmpty().filter { seen.add(it.id) }
        if (fresh.isEmpty()) {
            // Ani jedna zpráva před koncem úseku: chat začíná až po něm.
            startsAfter = seen.isEmpty() || seen.size == 1 && next != null
            complete = true
            break
        }
        found += fresh.filter { it.timestamp in start until end && !it.isService }
        val oldest = fresh.minBy { it.id }
        if (oldest.timestamp < start) {
            complete = true
            break
        }
        if (found.size >= cap) break
        from = oldest.id
    }
    return MessagesBetween(
        messages = found.sortedBy { it.id }.takeLast(cap),
        nextMessageId = next,
        chatStartsAfter = startsAfter,
        readAnything = seen.isNotEmpty(),
        complete = complete,
    )
}
