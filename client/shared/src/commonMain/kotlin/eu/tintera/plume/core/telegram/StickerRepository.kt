package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.Flow

/**
 * Samolepky uživatele pro výběr: nainstalované sady, nedávno použité a oblíbené.
 *
 * Všechno jsou **živé toky**, ne jednorázová čtení. Sady, oblíbené i nedávné se mění i z jiného
 * zařízení a TDLib o tom posílá updaty — výběr otevřený v jednom okně tak nezůstane pozadu.
 *
 * Prémiové samolepky se uživateli bez Premium **nenabízejí**. Odeslat je nemůže a samolepka,
 * po jejímž ťuknutí přijde chyba, je horší než samolepka, která v nabídce není.
 */
interface StickerRepository {

    /** Nainstalované běžné sady v pořadí, které si uživatel nastavil. */
    fun installedSets(): Flow<List<StickerSetSummary>>

    /** Nedávno odeslané samolepky, od nejnovější. */
    fun recentStickers(): Flow<List<StickerItem>>

    fun favoriteStickers(): Flow<List<StickerItem>>

    /** Obsah jedné sady. Načítá se až při otevření její záložky — sad bývá desítky. */
    suspend fun stickerSet(setId: Long): Result<List<StickerItem>>
}

/** Sada v záložkách výběru. */
data class StickerSetSummary(
    val id: Long,
    val title: String,
    /** Samolepka, která sadu zastupuje v záložce. `null`, když sada žádnou nemá. */
    val cover: MessageMedia.Sticker?,
)

/**
 * Samolepka ve výběru.
 *
 * [sendFileId] je soubor samolepky v jakémkoli formátu — posílá se ten, ne náhled. [preview] je
 * tatáž samolepka tak, jak ji umí vykreslit konverzace (statická, animovaná, náhled).
 */
data class StickerItem(
    /** Id samolepky od TDLib; stabilní klíč v mřížce. */
    val id: Long,
    val sendFileId: Int,
    val preview: MessageMedia.Sticker,
)
