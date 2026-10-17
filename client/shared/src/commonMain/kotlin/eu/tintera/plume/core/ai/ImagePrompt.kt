package eu.tintera.plume.core.ai

// Prompt je text pro MODEL, ne pro uživatele: česká slova v literálech sem patří, do
// composeResources ne. Nese to JMÉNO SOUBORU — `verifyNoHardcodedText` vypíná pravidlo
// o diakritice souborům `…Prompt.kt` v `core/ai`, a jen jim; slot v UI hlídá i tady.
// Nový prompt se proto musí jmenovat stejně. Proč to tak je: „Jazyky" v CLAUDE.md.

/**
 * Pokyn k popisu fotky pro toho, kdo ji nevidí (`OnDeviceAi.describeImage`). Obrázek jde
 * modelu zvlášť, tohle je text vedle něj.
 *
 * - **Krátce a věcně**: dvě až čtyři věty, co je na fotce a co je na ní podstatné. Popis se čte
 *   (i čtečkou obrazovky), dlouhý výčet každého detailu by nikdo neposlouchal.
 * - **U textu na fotce jen jeho druh** (účtenka, cedule, obrazovka aplikace), ne výčet slov —
 *   od toho je „Text z fotky".
 * - **Nehádat, kdo je na fotce**: jméno by model vymyslel; stačí popsat, co člověk dělá.
 * - **Odpověď v jazyce aplikace** ([languageCode]); bez výslovné věty model odpovídá jazykem
 *   instrukcí.
 */
fun imageDescriptionPrompt(languageCode: String): String = buildString {
    appendLine("Describe this photo for someone who cannot see it.")
    appendLine("- 2 to 4 short sentences: what the photo shows and what matters in it")
    // Se zněním „say what it is about, do not copy all of it" vyjmenovala Gemma 4 E2B u snímku
    // obrazovky všechny popisky v uvozovkách (ověřeno na emulátoru); proto druh textu a zákaz citovat.
    appendLine("- if there is text in the photo, say in a few words what kind of text it is (for example a receipt, a sign, an app screen); do not quote or list the words")
    appendLine("- do not guess who the people are, describe what they look like and do")
    appendLine("- only what is visible, do not invent anything")
    appendLine("- ${imageLanguage(languageCode)}")
    append("- start directly with the description, no introduction")
}

private fun imageLanguage(languageCode: String): String = when (languageCode) {
    "cs" -> "write in Czech"
    "en" -> "write in English"
    else -> "write in the language with code \"$languageCode\""
}
