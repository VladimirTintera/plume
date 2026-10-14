package eu.tintera.plume.features.conversation

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ReplyQuote
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import org.jetbrains.compose.resources.StringResource

/*
 * Co si části [ConversationViewModel] volají navzájem. Každá část dostává v konstruktoru jen to,
 * co opravdu potřebuje; volání do jiné části je funkce. Kde volající pojmenovává argumenty, je
 * to rozhraní — funkční typ pojmenované argumenty neunese. `fun interface` zase nedovolí
 * výchozí hodnoty, takže [MediaSender] a [MessageJumper] jsou obyčejná rozhraní.
 */

/**
 * Odeslání média s citací a hlášením chyby (viz `ConversationViewModel.sendMedia`): spotřebuje
 * rozepsanou citaci, nahlásí chat jako použitý a naplánované ohlásí místo vložení do konverzace.
 */
internal interface MediaSender {
    fun sendMedia(
        failureMessage: StringResource,
        scheduled: Boolean = false,
        send: suspend (replyTo: Long?, quote: ReplyQuote?) -> Result<ChatMessage>,
    )
}

/** Skok na zprávu (viz `ConversationViewModel.jumpToMessage`). */
internal interface MessageJumper {
    fun jumpToMessage(
        messageId: Long,
        fromSearch: Boolean = false,
        anchor: ScrollAnchor = ScrollAnchor.Center,
    )
}

/** Chyba pro uživatele z výjimky; surová hláška jde do logu (viz „Chybové hlášky pro uživatele" v CLAUDE.md). */
internal fun Throwable.reported(logger: Logger, message: StringResource): UiText {
    logger.w(this) { message.key }
    return userMessage(message)
}
