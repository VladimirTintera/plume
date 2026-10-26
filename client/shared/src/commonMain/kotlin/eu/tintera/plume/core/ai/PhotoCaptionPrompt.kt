package eu.tintera.plume.core.ai

// Prompt je text pro MODEL, ne pro uživatele: česká slova v literálech sem patří, do
// composeResources ne. Nese to JMÉNO SOUBORU — `verifyNoHardcodedText` vypíná pravidlo
// o diakritice souborům `…Prompt.kt` v `core/ai`, a jen jim; slot v UI hlídá i tady.
// Nový prompt se proto musí jmenovat stejně. Proč to tak je: „Jazyky" v CLAUDE.md.

/** Delší návrh popisku se zkrátí — popisek je věta, ne odstavec. */
const val MAX_CAPTION_CHARS = 200

/**
 * Pokyn k návrhu POPISKU fotky, kterou uživatel posílá (`OnDeviceAi.describeImage`
 * s vlastním promptem). Jiný úkol než popis pro nevidomé ([imageDescriptionPrompt]): jedna
 * krátká věta, kterou by pod fotku napsal odesílatel, ne výčet toho, co je na ní.
 *
 * - **Píše za uživatele**, takže věcně a bez přehánění: model jinak sklouzává k reklamnímu
 *   slohu („Úchvatný výhled!"), a ten pod fotku od kamaráda nepatří.
 * - **Bez hashtagů, emoji a uvozovek** — Telegram není Instagram a uvozovky model rád přidá
 *   kolem celé věty ([cleanCaption] je stejně odstraní).
 * - **Nehádat, kdo je na fotce** ze stejného důvodu jako u popisu.
 */
fun photoCaptionPrompt(languageCode: String): String = buildString {
    appendLine("Suggest a caption the sender could write under this photo in a chat message.")
    appendLine("- one short, plain sentence, at most 12 words")
    appendLine("- say what the photo shows in a natural, friendly way; no exaggeration, no advertising tone")
    appendLine("- no hashtags, no emoji, no quotation marks")
    appendLine("- do not guess who the people are")
    appendLine("- ${captionLanguage(languageCode)}")
    append("- answer with the caption only")
}

/**
 * Návrh popisku tak, jak jde do pole: první neprázdný řádek bez odrážky a uvozovek kolem,
 * zkrácený na [MAX_CAPTION_CHARS]. `null`, když model nenapsal nic použitelného.
 */
fun cleanCaption(text: String): String? {
    val line = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
    val unquoted = line.removePrefix("- ").removePrefix("* ").trim()
        .trim('"', '„', '“', '”', '\'', '«', '»').trim()
    return unquoted.take(MAX_CAPTION_CHARS).trim().ifEmpty { null }
}

private fun captionLanguage(languageCode: String): String = when (languageCode) {
    "cs" -> "write in Czech"
    "en" -> "write in English"
    else -> "write in the language with code \"$languageCode\""
}
