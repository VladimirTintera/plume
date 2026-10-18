package eu.tintera.plume.core.ai

// Prompt je text pro MODEL, ne pro uživatele: česká slova v literálech sem patří, do
// composeResources ne. Nese to JMÉNO SOUBORU — `verifyNoHardcodedText` vypíná pravidlo
// o diakritice souborům `…Prompt.kt` v `core/ai`, a jen jim; slot v UI hlídá i tady.
// Nový prompt se proto musí jmenovat stejně. Proč to tak je: „Jazyky" v CLAUDE.md.

/** Kolik znaků nepřečtených zpráv dostane model; o odpovědi rozhoduje konec, ne román. */
const val MAX_TRIAGE_CHARS = 1_500

/**
 * Prompt pro triáž chatu: čeká protějšek na odpověď uživatele? [messages] jsou nepřečtené
 * příchozí zprávy od nejstarší, každá jeden prvek.
 *
 * - **Odpověď je jedno slovo** (`REPLY`, `INFO`, `UNSURE`), jako u [scamPrompt]: z věty by se
 *   verdikt dolovat musel.
 * - **`UNSURE` je výslovná možnost** a odznak se ukáže jen u jasného `REPLY`. Odznak u chatu,
 *   kde nikdo nic nechce, by učil odznak přehlížet — radši ho vynechat.
 * - **Pozdrav sám odpověď nečeká.** „Ahoj" bez dalšího je začátek, ne otázka; bez téhle věty
 *   by model chtěl odpověď na každou zprávu, protože na každou se odpovědět DÁ. **„Jak se máš?"
 *   ale ano** — s „greeting alone" bez příkladu ho Gemma 4 E2B brala jako pozdrav (ověřeno na
 *   emulátoru: „Ahoj, jak se máš. Jarda" → INFO).
 * - **Rozhoduje poslední zpráva**: otázka z první zprávy, kterou protějšek v dalších sám
 *   uzavřel („už nic, vyřešeno"), odpověď nečeká.
 * - Instrukce anglicky ze stejného důvodu jako u [summaryPrompt].
 * - [structured] = odpověď přijde jako [TriageAnswer] (`generateAnswer`); prompt pak o formátu
 *   mlčí a povolené hodnoty dá modelu schéma.
 * - **Ve skupině** ([group]) dostane model jen zprávy, které uživatele zmiňují nebo odpovídají
 *   na jeho zprávu, se jménem odesílatele („Jana: …"); ostatní provoz ve skupině ho nezajímá.
 */
fun triagePrompt(messages: List<String>, structured: Boolean = false, group: Boolean = false): String = buildString {
    appendLine("You help the user of a chat app decide which chats need their answer.")
    if (group) {
        appendLine("Below are unread messages in a group chat that mention the user or reply to the user's message, oldest first, each with its sender.")
    } else {
        appendLine("Below are the unread messages someone sent to the user in a private chat, oldest first.")
    }
    appendLine()
    appendLine("Messages:")
    appendLine("\"\"\"")
    // Konec je důležitější než začátek: když je toho moc, ořízne se od nejstarších.
    appendLine(messages.joinToString("\n").takeLast(MAX_TRIAGE_CHARS))
    appendLine("\"\"\"")
    appendLine()
    appendLine(if (structured) "Choose the verdict:" else "Answer with exactly one word:")
    appendLine("REPLY - the latest messages contain a question to the user, even small talk like \"how are you?\", or ask the user to do or decide something")
    appendLine("INFO - no question and no request: just information, news, thanks, a single \"hi\", or an automated notice")
    appendLine("UNSURE - anything you are not sure about")
    appendLine("Judge by the latest messages. A question to the user always needs an answer. When in doubt, answer UNSURE.")
    if (!structured) append("Answer:")
}.trimEnd()

/**
 * Čeká chat na odpověď? `true` jen při jednoznačném `REPLY`; `INFO`, `UNSURE`, prázdný výstup
 * i výstup, který jmenuje víc verdiktů naráz, je `false` — odznak se neukáže. Tolerantní
 * k obalu (tečka, uvozovky, malá písmena).
 */
fun parseTriageVerdict(output: String): Boolean {
    val words = TRIAGE_WORD.findAll(output.uppercase()).map { it.value }.toSet()
    return "REPLY" in words && "INFO" !in words && "UNSURE" !in words
}

private val TRIAGE_WORD = Regex("[A-Z]+")

/** Verdikt ze strukturované odpovědi — týmiž pravidly jako z textu ([parseTriageVerdict]). */
fun triageVerdictOf(answer: TriageAnswer): Boolean = parseTriageVerdict(answer.verdict.orEmpty())
