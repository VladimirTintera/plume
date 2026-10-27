package eu.tintera.plume.features.conversation.items

import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import eu.tintera.plume.core.telegram.ChatMessage

/*
 * Zpráva pro čtečku obrazovky (TalkBack, VoiceOver).
 *
 * Bublina je jeden uzel už díky `contextClickable` (klikání slučuje potomky), takže text, popisek
 * i jméno se čtou vcelku. Chybělo ale to, co člověk vidí bez čtení a čtečka ne:
 *
 * - **kdo psal** u navazujících zpráv ve skupině — jméno nese jen první bublina bloku,
 * - **čas a stav doručení** — v bublině nejsou (čas je za tahem doleva, odznak přečtení jen
 *   u vybraných zpráv) a tahem se čtečka nikam nedostane,
 * - **Odpovědět a Reagovat** — gesta (tah doprava, dvojité ťuknutí) čtečka nezná; dosud vedla
 *   jen přes nabídku nad zprávou.
 *
 * Fotka, přehrávač, odkaz a další potomci s vlastním klikáním zůstávají samostatné uzly.
 */

/** Co bublina hlásí čtečce navíc k vlastnímu textu. Popisky už přeložené (viz [messageA11y]). */
internal class MessageA11y(
    /** Jméno odesílatele, které v bublině vidět není; `null` = neříkat. */
    val sender: String?,
    /** „14:32, Přečteno" — čte se jako stav uzlu, za textem zprávy. */
    val state: String,
    /** „Odpovědět"; `null` = do chatu psát nejde, akce se nenabízí. */
    val replyLabel: String?,
    /** „Reagovat ❤️"; `null` = akce se nenabízí (režim výběru). */
    val reactLabel: String?,
    val onReply: () -> Unit,
    val onReact: () -> Unit,
)

internal fun SemanticsPropertyReceiver.messageA11y(a11y: MessageA11y) {
    // `text` se slučuje SEZNAMEM a rodič jde první: jméno zazní před textem zprávy.
    a11y.sender?.let { text = AnnotatedString(it) }
    stateDescription = a11y.state
    customActions = listOfNotNull(
        a11y.replyLabel?.let { label -> CustomAccessibilityAction(label) { a11y.onReply(); true } },
        a11y.reactLabel?.let { label -> CustomAccessibilityAction(label) { a11y.onReact(); true } },
    )
}

/**
 * Jméno, které čtečka řekne před textem: jen u PŘÍCHOZÍ zprávy, která na předchozí od téhož
 * odesílatele navazuje — první bublina bloku jméno nese viditelně a čte se samo. Vlastní zprávu
 * ani soukromý chat (jméno `null`) to netýká.
 */
internal fun a11ySender(message: ChatMessage, continuesAbove: Boolean): String? =
    message.senderName?.takeIf { continuesAbove && !message.isOutgoing }
