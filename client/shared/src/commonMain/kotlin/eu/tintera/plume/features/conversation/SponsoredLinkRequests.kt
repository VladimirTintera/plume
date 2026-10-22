package eu.tintera.plume.features.conversation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Požadavek otevřít odkaz reklamy [url] v konverzaci chatu [chatId]. */
data class SponsoredLinkRequest(val chatId: Long, val url: String)

/**
 * Odkaz reklamy z tlačítka v prohlížeči médií (video reklamy na celé obrazovce).
 *
 * Odkaz otevírá konverzace, ne prohlížeč: odkaz do Telegramu jde do aplikace a cizí web až po
 * potvrzení (`ConversationLinks.openLink`) — to všechno žije v konverzaci. Prohlížeč kliknutí
 * nahlásí sám, zavře se a odkaz předá sem.
 *
 * Stav, ne událost, ze stejného důvodu jako [ConversationReplyRequests]: konverzace pod
 * zavíraným prohlížečem se teprve znovu skládá, takže v okamžiku ťuknutí nemusí být komu doručit.
 */
class SponsoredLinkRequests {
    private val pending = MutableStateFlow<SponsoredLinkRequest?>(null)

    val request: StateFlow<SponsoredLinkRequest?> = pending.asStateFlow()

    fun request(chatId: Long, url: String) {
        pending.value = SponsoredLinkRequest(chatId, url)
    }

    /** Vyřízeno — jen když čeká požadavek právě pro [chatId]. */
    fun consume(chatId: Long) {
        pending.value?.takeIf { it.chatId == chatId }?.let { pending.compareAndSet(it, null) }
    }
}
