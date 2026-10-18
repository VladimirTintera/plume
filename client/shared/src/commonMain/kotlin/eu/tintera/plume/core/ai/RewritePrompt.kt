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

    /**
     * Doplnit emoji, text nechat. Malý model rád „vylepší" i slova, proto výsledek hlídá
     * [onlyEmojiAdded] — co neprojde, se zahodí.
     */
    Emoji,
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
            RewriteStyle.Emoji ->
                "Add one to three fitting emoji where they feel natural. Only insert emoji: " +
                    "do not change, fix, add or remove any word, letter or punctuation mark."
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

/**
 * Přidal model k [original] jen emoji ([result])? Pojistka stylu [RewriteStyle.Emoji]: po
 * odebrání emoji (a mezer — model je kolem emoji rád přidá nebo ubere) musí zbýt přesně
 * původní text. Malý model jinak „mimochodem" opraví překlep nebo přepíše slovo a uživatel by
 * to v záplavě emoji nepoznal.
 */
fun onlyEmojiAdded(original: String, result: String): Boolean =
    withoutEmoji(result).filterNot(Char::isWhitespace) == withoutEmoji(original).filterNot(Char::isWhitespace)

/** [text] bez emoji: obrázkové znaky, spojovače (ZWJ), voliče variant, odstíny pleti, keycap. */
internal fun withoutEmoji(text: String): String = buildString {
    var index = 0
    while (index < text.length) {
        val high = text[index]
        val pair = high.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()
        val codePoint = if (pair) (high.code - 0xD800) * 0x400 + (text[index + 1].code - 0xDC00) + 0x10000 else high.code
        val size = if (pair) 2 else 1
        if (!isEmojiCodePoint(codePoint)) append(text, index, index + size)
        index += size
    }
}

private fun isEmojiCodePoint(codePoint: Int): Boolean =
    codePoint >= 0x1F000 ||
        codePoint in 0x2600..0x27BF ||
        codePoint in 0x2B00..0x2BFF ||
        codePoint in 0x2300..0x23FF ||
        codePoint in 0xFE00..0xFE0F ||
        codePoint == 0x200D ||
        codePoint == 0x20E3 ||
        codePoint == 0x3030 || codePoint == 0x303D || codePoint == 0x3297 || codePoint == 0x3299
