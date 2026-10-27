package eu.tintera.plume.features.profile

import eu.tintera.plume.core.telegram.SharedMediaKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Požadavek „otevři profil chatu na záložce sdíleného obsahu" zvenčí — z prohlížeče médií
 * („Všechna média").
 *
 * Stav, ne událost, ze stejného důvodu jako `ConversationJumpRequests`: prohlížeč se zavírá
 * a profil se teprve skládá, a záložky v něm přibývají, jak dorážejí počty. Profil si požadavek
 * pro svůj chat vyzvedne, až záložku má. Routa profilu záložku nenese schválně: `ChatProfileRoute`
 * se porovnává celá (otevřený profil, profil vedle konverzace) a pole navíc by ty rovnosti
 * rozbilo.
 */
class ProfileTabRequests {
    private val pending = MutableStateFlow<Pair<Long, SharedMediaKind>?>(null)

    /** (chatId, záložka) čekajícího požadavku. */
    val target: StateFlow<Pair<Long, SharedMediaKind>?> = pending.asStateFlow()

    fun request(chatId: Long, kind: SharedMediaKind) {
        pending.value = chatId to kind
    }

    /** Vyřízeno — jen když čeká požadavek právě pro [chatId]. */
    fun consume(chatId: Long) {
        pending.value?.takeIf { it.first == chatId }?.let { pending.compareAndSet(it, null) }
    }
}
