package eu.tintera.plume.features.conversation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Požadavek odpovědět na zprávu [messageId] v chatu [chatId]. */
data class ReplyRequest(val chatId: Long, val messageId: Long)

/**
 * Požadavek „připrav v konverzaci odpověď" zvenčí — z tlačítka Odpovědět v prohlížeči médií.
 *
 * Stav, ne událost, ze stejného důvodu jako [ConversationSearchRequests]: prohlížeč se zavírá
 * a konverzace pod ním se teprve znovu skládá, takže v okamžiku ťuknutí nemusí být komu doručit.
 * ViewModel konverzace si požadavek pro svůj chat vyzvedne, jakmile ho odebírá.
 */
class ConversationReplyRequests {
    private val pending = MutableStateFlow<ReplyRequest?>(null)

    val request: StateFlow<ReplyRequest?> = pending.asStateFlow()

    fun request(chatId: Long, messageId: Long) {
        pending.value = ReplyRequest(chatId, messageId)
    }

    /** Vyřízeno — jen když čeká požadavek právě pro [chatId]. */
    fun consume(chatId: Long) {
        pending.value?.takeIf { it.chatId == chatId }?.let { pending.compareAndSet(it, null) }
    }
}
