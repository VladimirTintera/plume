package eu.tintera.plume.features.conversation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Požadavek „otevři v konverzaci hledání" zvenčí — z tlačítka Hledat v profilu chatu.
 *
 * Stav, ne událost, ze stejného důvodu jako `ChatOpenRequests`: profil se zavírá a konverzace
 * pod ním se teprve znovu skládá, takže v okamžiku ťuknutí nemusí být komu doručit. ViewModel
 * konverzace si požadavek pro svůj chat vyzvedne, jakmile ho odebírá.
 */
class ConversationSearchRequests {
    private val pending = MutableStateFlow<Long?>(null)

    val chatId: StateFlow<Long?> = pending.asStateFlow()

    fun request(chatId: Long) {
        pending.value = chatId
    }

    /** Vyřízeno — jen když čeká požadavek právě pro [chatId]. */
    fun consume(chatId: Long) {
        pending.compareAndSet(chatId, null)
    }
}
