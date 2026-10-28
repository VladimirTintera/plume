package eu.tintera.plume.features.conversation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Kam konverzace skočí: na zprávu, nebo na první zprávu dne (začátek dne v sekundách epochy). */
sealed interface JumpTarget {
    data class Message(val messageId: Long) : JumpTarget
    data class Day(val epochSeconds: Int) : JumpTarget
}

/**
 * Požadavek „skoč v konverzaci" zvenčí — ze záložky v profilu chatu (zpráva) a z kalendáře
 * zpráv v panelu vedle konverzace (zpráva nebo den).
 *
 * Stav, ne událost, ze stejného důvodu jako [ConversationSearchRequests]: profil se zavírá
 * a konverzace pod ním se teprve znovu skládá. ViewModel konverzace si požadavek pro svůj chat
 * vyzvedne, jakmile ho odebírá. Vyzvednutý se vynuluje, takže totéž ťuknutí podruhé je nový
 * požadavek, ne stejná hodnota, kterou `StateFlow` spolkne.
 */
class ConversationJumpRequests {
    private val pending = MutableStateFlow<Pair<Long, JumpTarget>?>(null)

    /** (chatId, cíl) čekajícího skoku. */
    val target: StateFlow<Pair<Long, JumpTarget>?> = pending.asStateFlow()

    fun request(chatId: Long, messageId: Long) {
        pending.value = chatId to JumpTarget.Message(messageId)
    }

    fun requestDay(chatId: Long, epochSeconds: Int) {
        pending.value = chatId to JumpTarget.Day(epochSeconds)
    }

    /** Vyřízeno — jen když čeká požadavek právě pro [chatId]. */
    fun consume(chatId: Long) {
        pending.value?.takeIf { it.first == chatId }?.let { pending.compareAndSet(it, null) }
    }
}
