package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.Flow

/**
 * GIFy pro výběr: uložené (Telegram si je pamatuje u účtu, sdílí je všechna zařízení) a hledání.
 *
 * **Hledání GIFů Telegram nemá jako funkci API.** Oficiální klienti se ptají inline bota, jehož
 * jméno posílá server (volba `animation_search_bot_username`, dnes `@gif`); prázdný dotaz vrací
 * populární. Tady se to dělá stejně, jen se výsledky neposílají přes bota: GIF z výsledku je
 * soubor, který na serveru už je, a odchází jako každá jiná animace (bez „přes @gif").
 */
interface GifRepository {

    /** Uložené GIFy, od naposledy použitého. Živý tok — mění se i odesláním z jiného zařízení. */
    fun savedGifs(): Flow<List<GifItem>>

    /**
     * Jedna stránka hledání. Prázdný [query] = populární. [offset] je [GifPage.nextOffset]
     * předchozí stránky, na začátku prázdný. [chatId] chce inline bot vědět, kam se hledá.
     */
    suspend fun search(chatId: Long, query: String, offset: String): Result<GifPage>

    /** Odebere GIF z uložených. Potvrzení dorazí přes [savedGifs]. */
    suspend fun removeSaved(gif: GifItem): Result<Unit>

    /**
     * Uloží GIF (třeba nalezený mezi populárními) mezi uložené, na začátek. Uložených je omezený
     * počet a nejstarší přebytečný server vyřadí. Potvrzení dorazí přes [savedGifs].
     */
    suspend fun addSaved(gif: GifItem): Result<Unit>
}

/** GIF ve výběru. Posílá se soubor ([MessageMedia.Animation.fileId]), který na serveru už je. */
data class GifItem(val animation: MessageMedia.Animation) {
    /** Klíč v mřížce. Tentýž soubor se v jedné stránce neopakuje (viz [GifPage]). */
    val id: Int get() = animation.fileId
}

data class GifPage(
    val gifs: List<GifItem>,
    /** Prázdný = další stránka není. */
    val nextOffset: String,
)
