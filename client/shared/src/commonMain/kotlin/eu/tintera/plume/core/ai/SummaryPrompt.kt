package eu.tintera.plume.core.ai

/** Jedna zpráva pro shrnutí: kdo ji napsal a co v ní je. */
data class TranscriptLine(val author: String, val text: String)

/**
 * Nejvýš kolik znaků přepisu jde do modelu.
 *
 * Gemini Nano bere na vstupu zhruba 4 000 tokenů. Čeština má na token méně znaků než angličtina,
 * takže 6 000 znaků je s rezervou na instrukce i odpověď. Když se přepis nevejde, zahazují se
 * NEJSTARŠÍ zprávy — shrnutí má říct, co se dělo naposledy.
 */
const val MAX_TRANSCRIPT_CHARS = 6_000

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
fun summaryPrompt(chatTitle: String, lines: List<TranscriptLine>, languageCode: String): String {
    val transcript = fitTranscript(lines)
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
