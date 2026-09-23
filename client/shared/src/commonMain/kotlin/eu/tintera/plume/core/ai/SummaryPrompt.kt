package eu.tintera.plume.core.ai

// Prompt je text pro MODEL, ne pro uživatele: česká slova v literálech sem patří, do
// composeResources ne. Nese to JMÉNO SOUBORU — `verifyNoHardcodedText` vypíná pravidlo
// o diakritice souborům `…Prompt.kt` v `core/ai`, a jen jim; slot v UI hlídá i tady.
// Nový prompt se proto musí jmenovat stejně. Proč to tak je: „Jazyky" v CLAUDE.md.

/** Jedna zpráva pro shrnutí: kdo ji napsal a co v ní je. */
data class TranscriptLine(val author: String, val text: String)

/**
 * Kolik znaků přepisu jde do modelu, když jeho limit neznáme.
 *
 * Gemini Nano bere na vstupu zhruba 4 000 tokenů. Čeština má na token méně znaků než angličtina,
 * takže 6 000 znaků je s rezervou na instrukce i odpověď. Když se přepis nevejde, zahazují se
 * NEJSTARŠÍ zprávy — shrnutí má říct, co se dělo naposledy.
 */
const val MAX_TRANSCRIPT_CHARS = 6_000

/** Horní mez přepisu i u modelu s velkým limitem — delší přepis jen prodlužuje čekání. */
const val MAX_TRANSCRIPT_CHARS_CAP = 24_000

/**
 * Odhad znaků přepisu pro limit [tokenLimit]: zhruba tři znaky na token (čeština je na tokeny
 * dražší než angličtina) a pětina limitu na instrukce. Odhad se pak ověřuje počítáním tokenů.
 */
fun transcriptCharsFor(tokenLimit: Int?): Int =
    tokenLimit?.takeIf { it > 0 }
        ?.let { (it * 4 / 5 * 3).coerceIn(1_000, MAX_TRANSCRIPT_CHARS_CAP) }
        ?: MAX_TRANSCRIPT_CHARS

/** Jedna zpráva se ořízne na tolik znaků; dlouhý příspěvek by jinak vytlačil všechny ostatní. */
const val MAX_LINE_CHARS = 600

/**
 * Prompt pro shrnutí konverzace.
 *
 * Instrukce jsou anglicky — Gemini Nano je na angličtinu laděný a pokyny v ní plní spolehlivěji.
 * Jazyk ODPOVĚDI se volí výslovně podle jazyka aplikace ([languageCode]); bez toho model odpovídá
 * jazykem instrukcí, ne konverzace.
 *
 * [lines] jsou chronologicky, nejstarší první.
 */
fun summaryPrompt(
    chatTitle: String,
    lines: List<TranscriptLine>,
    languageCode: String,
    maxChars: Int = MAX_TRANSCRIPT_CHARS,
): String {
    val transcript = fitTranscript(lines, maxChars)
    return buildString {
        appendLine("You summarize a chat conversation from the messaging app Telegram.")
        appendLine("Chat name: $chatTitle")
        appendLine()
        appendLine("Write a short summary of the conversation below:")
        appendLine("- 3 to 6 bullet points, each starting with \"- \"")
        appendLine("- mention who said or decided what, using the names from the transcript")
        appendLine("- only facts from the transcript, do not invent anything")
        appendLine("- ${languageInstruction(languageCode)}")
        appendLine()
        appendLine("Transcript:")
        appendLine(transcript)
        appendLine()
        append("Summary:")
    }
}

/** Kolik zpráv z [lines] se vejde do přepisu dlouhého [maxChars]. */
fun keptLineCount(lines: List<TranscriptLine>, maxChars: Int): Int =
    fitTranscript(lines, maxChars).let { if (it.isEmpty()) 0 else it.count { c -> c == '\n' } + 1 }

/** Přepis ve tvaru „Jméno: text", oříznutý zezačátku na [MAX_TRANSCRIPT_CHARS]. */
internal fun fitTranscript(lines: List<TranscriptLine>, maxChars: Int = MAX_TRANSCRIPT_CHARS): String {
    val rendered = lines
        .filter { it.text.isNotBlank() }
        .map { line ->
            val text = line.text.replace('\n', ' ').trim()
            val clipped = if (text.length > MAX_LINE_CHARS) text.take(MAX_LINE_CHARS) + "…" else text
            "${line.author}: $clipped"
        }
    // Od nejnovější dozadu, dokud se vejde; pak zase chronologicky.
    val kept = ArrayDeque<String>()
    var length = 0
    for (line in rendered.asReversed()) {
        val added = line.length + 1
        if (length + added > maxChars && kept.isNotEmpty()) break
        kept.addFirst(line)
        length += added
    }
    return kept.joinToString("\n")
}

private fun languageInstruction(languageCode: String): String = when (languageCode) {
    "cs" -> "write the summary in Czech"
    "en" -> "write the summary in English"
    else -> "write the summary in the language of the conversation"
}

/**
 * Menší délka přepisu, když se prompt o [tokens] tokenech nevešel do [tokenLimit].
 *
 * Zmenšuje úměrně přesahu a ještě o desetinu navíc: tokeny na znak nejsou v přepisu rovnoměrné
 * (emoji, odkazy), takže přesně úměrné zmenšení by se často minulo o kousek.
 */
fun shrunkTranscriptChars(currentChars: Int, tokens: Int, tokenLimit: Int): Int =
    (currentChars.toLong() * tokenLimit / tokens * 9 / 10).toInt()
        .coerceAtMost(currentChars - 1)
        .coerceAtLeast(1)
