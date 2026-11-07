package eu.tintera.plume.core.ai

// Prompt je text pro MODEL, ne pro uživatele: česká slova v literálech sem patří, do
// composeResources ne. Nese to JMÉNO SOUBORU — `verifyNoHardcodedText` vypíná pravidlo
// o diakritice souborům `…Prompt.kt` v `core/ai`, a jen jim; slot v UI hlídá i tady.
// Nový prompt se proto musí jmenovat stejně. Proč to tak je: „Jazyky" v CLAUDE.md.

/** Jedno kolo volného rozhovoru: co napsal uživatel a co mu model odpověděl. */
data class ChatExchange(val question: String, val answer: String)

private const val USER = "User:"
private const val MODEL = "Assistant:"

/**
 * Prompt pro volný rozhovor (Vyzkoušet model). Celá historie jde pokaždé znovu v jednom
 * promptu — `OnDeviceAi.generate` mají všichni poskytovatelé, stavovou session ne (Gemini Nano
 * přes ML Kit), a porovnání modelů má jet stejnou cestou.
 *
 * - **Jazyk podle poslední zprávy uživatele**, ne podle aplikace: v rozhovoru se jazyk střídá
 *   a test češtiny je půlka účelu.
 * - **„Nikdy nepiš za uživatele"** — přepis „User: / Assistant:" malý model svádí dopsat i další
 *   repliku uživatele. Věta to omezí, [chatReply] usekne, co přesto proklouzne.
 * - Že model nemá internet ani chaty, říká prompt kvůli otázkám typu „co mi psala Jana" —
 *   jinak by si odpověď vymyslel.
 */
fun chatPrompt(history: List<ChatExchange>, message: String): String = buildString {
    appendLine(
        "You are a friendly assistant inside a chat app. You run entirely on the user's phone: " +
            "you have no internet access and you cannot see the user's chats, contacts or files.",
    )
    appendLine("- reply in the language of the user's last message")
    appendLine("- keep replies short and conversational unless the user asks for detail")
    appendLine("- if you do not know something, say so instead of guessing")
    appendLine("- write only your own reply, never the user's next message")
    appendLine()
    if (history.isNotEmpty()) {
        appendLine("Conversation so far:")
        appendLine()
        history.forEach { exchange ->
            appendLine("$USER ${exchange.question}")
            appendLine("$MODEL ${exchange.answer}")
            appendLine()
        }
    }
    appendLine("$USER $message")
    append(MODEL)
}

/** Začal model psát za uživatele? Pak je jeho odpověď u konce a generování se může zastavit. */
fun chatReplyEnded(text: String): Boolean = "\n$USER" in text

/** Odpověď modelu bez repliky, kterou dopsal za uživatele, a bez zopakované značky na začátku. */
fun chatReply(text: String): String =
    text.substringBefore("\n$USER").trim().removePrefix(MODEL).trim()
