package eu.tintera.plume.core.ai

// Prompt je text pro MODEL, ne pro uživatele: česká slova v literálech sem patří, do
// composeResources ne. Nese to JMÉNO SOUBORU — `verifyNoHardcodedText` vypíná pravidlo
// o diakritice souborům `…Prompt.kt` v `core/ai`, a jen jim; slot v UI hlídá i tady.
// Nový prompt se proto musí jmenovat stejně. Proč to tak je: „Jazyky" v CLAUDE.md.

/**
 * Od kolika vteřin se „Shrnout hlasovku" nabízí. Půlminutový přepis je pár řádků a přečte se
 * rychleji, než model odpoví; od minuty je přepis souvislý text, ve kterém se hledá, co
 * mluvčí vlastně chtěl.
 */
const val VOICE_SUMMARY_MIN_SECONDS = 60

/** Delší přepis se modelu dává jen do téhle délky — stejný strop jako přepis pro shrnutí. */
const val MAX_VOICE_SUMMARY_CHARS = MAX_TRANSCRIPT_CHARS

/**
 * Prompt pro shrnutí PŘEPISU hlasovky nebo kolečka do pár odrážek. Přepis dělá rozpoznávání
 * řeči (Gemma, systém, server), shrnutí jazykový model — u Nano + Gemmy tedy dva modely za sebou.
 *
 * - **Přepis je mluvená řeč s chybami rozpoznávání**: model ji nemá opravovat ani doplňovat,
 *   jen vytáhnout, co mluvčí říká a co chce. Nejistý úsek raději vynechat než si domýšlet.
 * - **Co se po posluchači chce** má vlastní větu, jako u vysvětlení ([explainPrompt]) — kvůli
 *   tomu se dlouhá hlasovka shrnuje nejčastěji.
 * - **Odpověď v jazyce aplikace** ([languageCode]), ne nahrávky; instrukce anglicky ze stejného
 *   důvodu jako u [summaryPrompt].
 */
fun voiceSummaryPrompt(transcript: String, speaker: String, languageCode: String): String = buildString {
    appendLine("You help the user of a chat app with a long voice message they received.")
    appendLine("Below is an automatic transcript of what $speaker said. It is spoken language and may contain recognition errors.")
    appendLine()
    appendLine("Summarize the voice message:")
    appendLine("- 2 to 4 short bullet points, each starting with \"- \"")
    appendLine("- if $speaker asks the listener to do something, say what and by when")
    appendLine("- keep every name, day, date, time, amount and place the same as in the transcript: translate them, never change them")
    appendLine("- only what the transcript says; skip parts that make no sense instead of guessing")
    appendLine("- ${voiceSummaryLanguage(languageCode)}")
    appendLine("- start directly with the first bullet point, no introduction")
    appendLine()
    appendLine("Transcript:")
    appendLine("\"\"\"")
    appendLine(transcript.take(MAX_VOICE_SUMMARY_CHARS))
    appendLine("\"\"\"")
    appendLine()
    append("Summary:")
}

private fun voiceSummaryLanguage(languageCode: String): String = when (languageCode) {
    "cs" -> "write in Czech, even if the voice message is in another language"
    "en" -> "write in English, even if the voice message is in another language"
    else -> "write in the language of the voice message"
}
