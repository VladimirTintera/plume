package eu.tintera.plume.features.conversation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Požadavek „skoč v konverzaci na zprávu" zvenčí — ze záložky v profilu chatu.
 *
 * Stav, ne událost, ze stejného důvodu jako [ConversationSearchRequests]: profil se zavírá
 * a konverzace pod ním se teprve znovu skládá. ViewModel konverzace si požadavek pro svůj chat
 * vyzvedne, jakmile ho odebírá.
 */
class ConversationJumpRequests {
    private val pending = MutableStateFlow<Pair<Long, Long>?>(null)

    /** (chatId, messageId) čekajícího skoku. */
    val target: StateFlow<Pair<Long, Long>?> = pending.asStateFlow()

    fun request(chatId: Long, messageId: Long) {
        pending.value = chatId to messageId
    }

    /** Vyřízeno — jen když čeká požadavek právě pro [chatId]. */
    fun consume(chatId: Long) {
        pending.value?.takeIf { it.first == chatId }?.let { pending.compareAndSet(it, null) }
    }
}
