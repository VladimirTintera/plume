package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ui.foldDiacritics

/** Jazyk, do kterého jde překládat: kód pro server a název v jazyce aplikace. */
data class TranslationLanguage(val code: String, val name: String)

/**
 * Nabídka jazyků překladu: napřed jazyk aplikace (výchozí cíl), pak ostatní abecedně podle
 * přeloženého názvu. [query] filtruje podle názvu bez ohledu na diakritiku a velikost písmen,
 * nebo podle začátku kódu („de").
 *
 * Názvy dává systém ([name] je `localizedLanguageName`); co systém nezná, ukáže se kódem.
 */
internal fun translationLanguageOptions(
    appLanguage: String,
    query: String,
    name: (String) -> String?,
): List<TranslationLanguage> {
    val app = normalizedLanguageCode(appLanguage)
    val others = TRANSLATION_LANGUAGES
        .filter { it != app }
        .map { TranslationLanguage(it, name(it) ?: it) }
        .sortedBy { foldDiacritics(it.name.lowercase()) }
    val all = listOf(TranslationLanguage(app, name(app) ?: app)) + others
    val folded = foldDiacritics(query.trim().lowercase())
    if (folded.isEmpty()) return all
    return all.filter { foldDiacritics(it.name.lowercase()).contains(folded) || it.code.lowercase().startsWith(folded) }
}

/**
 * Kód jazyka z platformy tak, jak ho vede seznam [TRANSLATION_LANGUAGES]. Java u hebrejštiny,
 * indonéštiny a jidiš vrací zastaralé kódy (`iw`, `in`, `ji`) — server je sice bere taky, jenže
 * v nabídce by pak tentýž jazyk stál dvakrát.
 */
internal fun normalizedLanguageCode(code: String): String = when (code) {
    "iw" -> "he"
    "in" -> "id"
    "ji" -> "yi"
    else -> code
}

/**
 * Jazyky, do kterých server překládá (dokumentace `translateText`). TDLib je lokálně nekontroluje,
 * rozhoduje server. Vynechané jsou jen aliasy téhož jazyka (`iw`, `in`, `ji`, `zh`, `zh-Hans`,
 * `zh-Hant`): čínština je dvakrát, zjednodušená a tradiční, jako u oficiálních klientů.
 *
 * **Čínština je podle regionu, ne podle písma.** Systém pojmenuje `zh-Hant` „Čínština (han
 * (tradiční))"; `zh-TW` je „Čínština (Tchaj-wan)", čemu člověk rozumí, a server bere obojí.
 */
internal val TRANSLATION_LANGUAGES = listOf(
    "af", "sq", "am", "ar", "hy", "az", "eu", "be", "bn", "bs", "bg", "ca", "ceb", "zh-CN",
    "zh-TW", "co", "hr", "cs", "da", "nl", "en", "eo", "et", "fi", "fr", "fy", "gl", "ka", "de",
    "el", "gu", "ht", "ha", "haw", "he", "hi", "hmn", "hu", "is", "ig", "id", "ga", "it", "ja",
    "jv", "kn", "kk", "km", "rw", "ko", "ku", "ky", "lo", "la", "lv", "lt", "lb", "mk", "mg", "ms",
    "ml", "mt", "mi", "mr", "mn", "my", "ne", "no", "ny", "or", "ps", "fa", "pl", "pt", "pa", "ro",
    "ru", "sm", "gd", "sr", "st", "sn", "sd", "si", "sk", "sl", "so", "es", "su", "sw", "sv", "tl",
    "tg", "ta", "tt", "te", "th", "tr", "tk", "uk", "ur", "ug", "uz", "vi", "cy", "xh", "yi", "yo",
    "zu",
)

/**
 * Text, ze kterého má smysl jazyk rozpoznávat, nebo `null`. Pár písmen („ok", „jo", „👍 díky")
 * rozpoznání plete a krátkou zprávu by mohlo vydávat za češtinu — pak by zmizel překlad, který
 * uživatel chtěl. Krátká zpráva proto „Přeložit" nabízí vždycky.
 */
internal fun languageDetectionText(text: String): String? =
    text.trim().takeIf { trimmed -> trimmed.count { it.isLetter() } >= MIN_DETECTION_LETTERS }

/**
 * Je zpráva už v jazyce překladu ([target], kód z [TRANSLATION_LANGUAGES])? Pak se „Přeložit"
 * nenabízí — překlad by vrátil totéž.
 *
 * Porovnává se základ jazyka (`en-US` je angličtina). **Čínština se nikdy neschovává**: cíl je
 * zjednodušená nebo tradiční (`zh-CN` / `zh-TW`) a rozpoznání písmo nerozliší, takže překlad
 * mezi nimi smysl dává.
 */
internal fun alreadyInLanguage(detected: String?, target: String): Boolean {
    if (detected == null || '-' in target) return false
    val base = normalizedLanguageCode(detected.substringBefore('-').substringBefore('_').lowercase())
    return base == normalizedLanguageCode(target.lowercase())
}

private const val MIN_DETECTION_LETTERS = 10
