package eu.tintera.plume.core.telegram

/**
 * Instant View: webová stránka převedená serverem Telegramu na bloky (`getWebPageInstantView`) —
 * článek se přečte v aplikaci, bez prohlížeče. Bloky jsou tytéž jako u článku ve zprávě
 * (`MessageRichMessage`), takže se převádějí stejně ([MessageBody] s médii, `toRichBody`).
 */
interface InstantViewRepository {
    /** Stránka [url]; `null`, když Instant View nemá (404). */
    suspend fun page(url: String): Result<InstantViewPage?>
}

/**
 * @property title název článku (`PageBlockTitle`), z textu vyjmutý; `null`, když stránka žádný nemá.
 * @property body text s entitami a médii (`Article.media`), jako článek ve zprávě.
 * @property viewCount kolikrát stránku lidé v Telegramu otevřeli; 0 = neznámé.
 * @property isPartial TDLib poslal jen začátek; zbytek ukáže web.
 */
data class InstantViewPage(
    val url: String,
    val title: String?,
    val body: MessageBody,
    val viewCount: Int,
    val isRtl: Boolean,
    val isPartial: Boolean,
)
