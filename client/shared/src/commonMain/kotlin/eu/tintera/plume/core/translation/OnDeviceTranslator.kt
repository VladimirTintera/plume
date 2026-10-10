package eu.tintera.plume.core.translation

/**
 * Překlad v telefonu — text zprávy neodchází na server. Na Androidu ML Kit Translation, iOS ho
 * zatím nemá (Translation framework je jen pro SwiftUI) a v DI tam žádný není.
 *
 * `core/translation` je hranice jako `core/ocr`: knihovna na překlad smí být jen tady. Jazyky
 * jsou kódy z [deviceTranslationCode], ne kódy serverového překladu.
 *
 * **Jazykové balíčky se stahují jen na výslovný pokyn** ([download]): každý má desítky MB a na
 * mobilních datech by je nikdo nečekal.
 */
interface OnDeviceTranslator {
    /** Umí překládat z / do jazyka [language]? */
    fun supports(language: String): Boolean

    /** Jsou v telefonu balíčky pro pár [source] → [target]? */
    suspend fun hasModels(source: String, target: String): Boolean

    /** Stáhne balíčky pro pár [source] → [target]. */
    suspend fun download(source: String, target: String): Result<Unit>

    /** Přeloží [text]; balíčky už musí být v telefonu ([hasModels]). */
    suspend fun translate(text: String, source: String, target: String): Result<String>

    /** Stažené jazykové balíčky — Nastavení → Úložiště. Vestavěná angličtina tu není, smazat nejde. */
    suspend fun downloadedPacks(): List<TranslationPack>

    /** Smaže balíček jazyka [language]; další překlad z / do něj si ho řekne stáhnout znovu. */
    suspend fun deletePack(language: String): Result<Unit>
}

/** Stažený jazykový balíček. [bytes] = místo na disku; `null`, když ho nejde zjistit. */
data class TranslationPack(val language: String, val bytes: Long?)

/**
 * Kód jazyka pro překlad v telefonu z kódu serverového překladu nebo rozpoznávání jazyka:
 * jen jazyk bez regionu („pt-BR" → „pt"). `null`, kde by překlad v telefonu dal něco jiného,
 * než uživatel chce: tradiční čínštinu (`zh-TW`) ML Kit neumí, jeho „zh" je zjednodušená.
 */
fun deviceTranslationCode(code: String): String? {
    val parts = code.replace('_', '-').split('-')
    val language = parts.first().lowercase()
    if (language == "zh") {
        val region = parts.getOrNull(1)?.uppercase()
        return if (region == null || region == "CN" || region == "HANS") "zh" else null
    }
    return when (language) {
        // Zastaralé kódy, které Java vrací (stejně jako `normalizedLanguageCode`).
        "iw" -> "he"
        "in" -> "id"
        "ji" -> "yi"
        else -> language
    }.takeIf { it.isNotBlank() }
}
