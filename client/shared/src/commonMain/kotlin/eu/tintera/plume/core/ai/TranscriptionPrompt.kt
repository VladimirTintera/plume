package eu.tintera.plume.core.ai

// Prompt je text pro MODEL, ne pro uživatele: česká slova v literálech sem patří, do
// composeResources ne. Nese to JMÉNO SOUBORU — `verifyNoHardcodedText` vypíná pravidlo
// o diakritice souborům `…Prompt.kt` v `core/ai`, a jen jim; slot v UI hlídá i tady.
// Nový prompt se proto musí jmenovat stejně. Proč to tak je: „Jazyky" v CLAUDE.md.

/**
 * Pokyn k přepisu řeči ze zvuku (hlasovka, kolečko). Zvuk jde modelu zvlášť, tohle je text
 * vedle něj.
 *
 * - **Doslova a nic navíc**: bez úvodu („Here is the transcript"), bez shrnutí a bez překladu.
 * - **Jazyk aplikace je jen nápověda** ([languageCode]): kdo dostane anglickou hlasovku, chce ji
 *   přepsanou anglicky, ne přeloženou.
 */
fun transcriptionPrompt(languageCode: String): String =
    "Transcribe this voice message word for word, in the language that is spoken. " +
        "The speaker most likely speaks the language with code \"$languageCode\". " +
        "Do not translate or summarize it and do not add anything else; write only the transcript."
