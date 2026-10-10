package eu.tintera.plume.core.ai

// Prompt je text pro MODEL, ne pro uživatele: česká slova v literálech sem patří, do
// composeResources ne. Nese to JMÉNO SOUBORU — `verifyNoHardcodedText` vypíná pravidlo
// o diakritice souborům `…Prompt.kt` v `core/ai`, a jen jim; slot v UI hlídá i tady.
// Nový prompt se proto musí jmenovat stejně. Proč to tak je: „Jazyky" v CLAUDE.md.

/**
 * Od kolika znaků se „Vysvětlit" nabízí. Krátkou zprávu člověk přečte rychleji, než model
 * odpoví; 280 znaků je zhruba pět řádků v bublině na telefonu.
 */
const val EXPLAIN_MIN_CHARS = 280

/** Delší zpráva se modelu dává jen do téhle délky — stejný strop jako přepis pro shrnutí. */
const val MAX_EXPLAIN_CHARS = MAX_TRANSCRIPT_CHARS

/**
 * Prompt pro vysvětlení JEDNÉ dlouhé zprávy („o co jde"). Totéž co shrnutí konverzace, jen
 * nad jednou zprávou: vejde se do vstupu celá a čte to jen uživatel, takže slabší sloh vadí míň.
 *
 * - **Odpověď v jazyce aplikace** ([languageCode]), ne zprávy: vysvětlení cizojazyčné zprávy
 *   je půlka užitku. Bez výslovné věty model odpovídá jazykem instrukcí.
 * - **Co se po čtenáři chce** (termín, částka, odkaz k vyplnění) má vlastní větu — kvůli tomu
 *   se dlouhá zpráva vysvětluje nejčastěji.
 * - Instrukce anglicky ze stejného důvodu jako u [summaryPrompt].
 */
fun explainPrompt(text: String, languageCode: String): String = buildString {
    appendLine("You help the user of a chat app understand a long message they received.")
    appendLine()
    appendLine("Explain what the message below is about:")
    appendLine("- 2 to 5 short bullet points, each starting with \"- \"")
    appendLine("- if the message asks the reader to do something, say what and by when")
    // Bez téhle věty Gemma 4 E2B přepsala „by Wednesday evening" na „do čtvrtka večer" — den
    // z vedlejší věty. Se zněním „exactly as the message says them" zase opsala anglické
    // úseky v uvozovkách („Thursday, October 15"); proto „přelož, ale neměň" (ověřeno na emulátoru).
    appendLine("- keep every name, day, date, time, amount and place the same as in the message: translate them, never change them")
    appendLine("- only facts from the message, do not invent anything")
    appendLine("- ${explainLanguage(languageCode)}")
    appendLine("- start directly with the first bullet point, no introduction")
    appendLine()
    appendLine("Message:")
    appendLine("\"\"\"")
    appendLine(text.take(MAX_EXPLAIN_CHARS))
    appendLine("\"\"\"")
    appendLine()
    append("Explanation:")
}

private fun explainLanguage(languageCode: String): String = when (languageCode) {
    "cs" -> "write in Czech, even if the message is in another language"
    "en" -> "write in English, even if the message is in another language"
    else -> "write in the language of the message"
}
