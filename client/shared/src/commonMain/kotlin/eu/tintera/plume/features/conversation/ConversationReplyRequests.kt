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

    private val sentReplies = MutableStateFlow<ReplyRequest?>(null)

    /**
     * Odpověď na [ReplyRequest.messageId] odešla z JINÉ obrazovky (formulář ankety) a konverzace
     * ji má zahodit. Formulář to hlásí sám po úspěšném odeslání: konverzace by to z příchozí
     * zprávy nepoznala u naplánované ankety, ta do historie nepřijde.
     */
    val sent: StateFlow<ReplyRequest?> = sentReplies.asStateFlow()

    fun replySent(chatId: Long, messageId: Long) {
        sentReplies.value = ReplyRequest(chatId, messageId)
    }

    fun consumeSent(chatId: Long) {
        sentReplies.value?.takeIf { it.chatId == chatId }?.let { sentReplies.compareAndSet(it, null) }
    }
}
