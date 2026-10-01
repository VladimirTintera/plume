package eu.tintera.plume.features.conversation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Požadavek dát do pole chatu [chatId] inline dotaz `@bot query`. */
data class InlineQueryRequest(val chatId: Long, val bot: String, val query: String)

/**
 * Inline dotaz z tlačítka bota do chatu, který si uživatel vybral (`switchInline`).
 *
 * Stav, ne událost, jako [ConversationReplyRequests]: vybraná konverzace se teprve skládá, a i když
 * už otevřená je, dotaz musí počkat na obnovení konceptu — jinak by ho koncept přepsal.
 */
class InlineQueryRequests {
    private val pending = MutableStateFlow<InlineQueryRequest?>(null)

    val request: StateFlow<InlineQueryRequest?> = pending.asStateFlow()

    fun request(chatId: Long, bot: String, query: String) {
        pending.value = InlineQueryRequest(chatId, bot, query)
    }

    /** Vyřízeno — jen když čeká požadavek právě pro [chatId]. */
    fun consume(chatId: Long) {
        pending.value?.takeIf { it.chatId == chatId }?.let { pending.compareAndSet(it, null) }
    }
}
