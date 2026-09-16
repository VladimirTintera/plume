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

    /**
     * Najde samolepky ve vlastních sadách i ve veřejných.
     *
     * Nikdy neselže celé: když nevyjde jeden zdroj (typicky veřejné hledání bez sítě), vrátí se
     * aspoň ten druhý. Prázdný výsledek znamená „nic nenalezeno".
     */
    suspend fun search(query: StickerQuery): StickerSearchResult
}

/** Co se hledá. Jestli jde o emoji, nebo o slovo, rozhoduje volající. */
sealed interface StickerQuery {

    /** Emoji oddělená mezerou, jak je napsal uživatel. */
    data class Emoji(val emojis: String) : StickerQuery

    /**
     * Slovo. Převádí se na emoji podle jazyka ([languageCodes], IETF tagy), protože samolepky
     * jsou k emoji přiřazené; ve vlastních sadách se hledá navíc i podle klíčových slov samolepek.
     */
    data class Text(val text: String, val languageCodes: List<String>) : StickerQuery
}

data class StickerSearchResult(
    /** Z nainstalovaných sad (a nedávných či oblíbených) — ty uživatel zná, jdou první. */
    val installed: List<StickerItem>,
    /** Z veřejných sad, bez těch, které už jsou v [installed]. */
    val public: List<StickerItem>,
)

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
