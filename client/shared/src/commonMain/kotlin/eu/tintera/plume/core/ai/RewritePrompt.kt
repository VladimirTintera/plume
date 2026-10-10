package eu.tintera.plume.core.ai

// Prompt je text pro MODEL, ne pro uživatele: česká slova v literálech sem patří, do
// composeResources ne. Nese to JMÉNO SOUBORU — `verifyNoHardcodedText` vypíná pravidlo
// o diakritice souborům `…Prompt.kt` v `core/ai`, a jen jim; slot v UI hlídá i tady.
// Nový prompt se proto musí jmenovat stejně. Proč to tak je: „Jazyky" v CLAUDE.md.

/** Delší koncept se nepřepisuje: výstup modelu má strop a delší text by useknul. */
const val MAX_REWRITE_CHARS = 2_000

/** Jak přepsat rozepsanou zprávu. */
enum class RewriteStyle {
    /** Kratší a k věci. */
    Shorter,

    /** Zdvořileji, ale ne o moc delší. */
    Polite,

    /** Jen pravopis, překlepy a chybějící diakritika. */
    Fix,
}

/**
 * Prompt pro přepis rozepsané zprávy, kterou uživatel ještě NEODESLAL. Výsledek vidí a rozhodne,
 * jestli ho použije — proto se tu sloh smí (na rozdíl od návrhů odpovědí, které se podepisují
 * naslepo).
 *
 * - **Jazyk zprávy, ne jazyk aplikace.** Kdo píše anglicky v české aplikaci, chce anglickou
 *   zprávu; „nepřekládej" musí stát výslovně, jinak malý model rád přejde do angličtiny.
 * - **Tykání zůstává tykáním.** „Zdvořileji" by jinak kamarádovi začalo vykat — stejná past jako
 *   u návrhů odpovědí (viz `formalityInstruction`).
 * - **Odpověď je jen přepsaný text** a [cleanRewrite] z ní stejně oloupe uvozovky a nadpis.
 * - Instrukce anglicky ze stejného důvodu jako u [summaryPrompt].
 */
fun rewritePrompt(text: String, style: RewriteStyle): String = buildString {
    appendLine("You edit a chat message the user is about to send.")
    appendLine(
        when (style) {
            RewriteStyle.Shorter -> "Make it shorter and more to the point. Drop filler words, keep everything important."
            RewriteStyle.Polite -> "Make it more polite and friendly, without making it much longer."
            RewriteStyle.Fix -> "Fix spelling, typos, grammar, punctuation and missing diacritics. Change nothing else."
        },
    )
    appendLine()
    appendLine("Rules:")
    appendLine("- Write in the same language as the message. Do not translate it.")
    appendLine("- Keep the meaning, names, numbers, links and emoji.")
    appendLine("- Keep the same form of address: in Czech, tykání stays tykání and vykání stays vykání.")
    appendLine("- Answer with the rewritten message only, without quotes, notes or explanations.")
    appendLine()
    appendLine("Message:")
    appendLine("\"\"\"")
    appendLine(text.take(MAX_REWRITE_CHARS))
    appendLine("\"\"\"")
    appendLine()
    append("Rewritten message:")
}

/**
 * Přepsaný text z výstupu modelu: bez obalu, který model rád přidá (nadpis „Rewritten message:",
 * blok kódu, uvozovky kolem celého textu). Uvozovky UVNITŘ textu zůstávají — jsou součástí
 * zprávy.
 */
fun cleanRewrite(output: String): String {
    var text = output.trim()
    text = text.removePrefix("```").removeSuffix("```").trim()
    text = text.removePrefix("\"\"\"").removeSuffix("\"\"\"").trim()
    LABEL.find(text)?.let { text = text.substring(it.range.last + 1).trim() }
    QUOTES.firstOrNull { (open, close) -> text.length >= 2 && text.startsWith(open) && text.endsWith(close) }
        ?.let { (open, close) ->
            val inner = text.substring(open.length, text.length - close.length)
            // Jen když uvozovky obalují CELÝ text, ne dvě citace na začátku a na konci.
            if (open !in inner && close !in inner) text = inner.trim()
        }
    return text
}

private val LABEL = Regex("^(rewritten message|rewritten|message)\\s*:", RegexOption.IGNORE_CASE)

private val QUOTES = listOf(
    "\"" to "\"",
    "„" to "“",
    "“" to "”",
    "«" to "»",
    "'" to "'",
)
