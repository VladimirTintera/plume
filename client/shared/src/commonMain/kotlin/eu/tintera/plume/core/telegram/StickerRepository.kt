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

    /**
     * Nainstalované sady v pořadí, které si uživatel nastavil — běžné samolepky, nebo s
     * [StickerSetKind.Emoji] sady vlastních emoji (správa sad v Nastavení).
     */
    fun installedSets(kind: StickerSetKind = StickerSetKind.Stickers): Flow<List<StickerSetSummary>>

    /** Nedávno odeslané samolepky, od nejnovější. */
    fun recentStickers(): Flow<List<StickerItem>>

    fun favoriteStickers(): Flow<List<StickerItem>>

    /**
     * Odebere samolepku z nedávných — typicky tu poslanou omylem. [stickerFileId] jako u
     * [setFavorite]. Potvrzení dorazí přes [recentStickers].
     */
    suspend fun removeRecent(stickerFileId: Int): Result<Unit>

    /** Vymaže všechny nedávné. Seznam je v cloudu, zmizí na všech zařízeních účtu. */
    suspend fun clearRecent(): Result<Unit>

    /**
     * Přidá samolepku do oblíbených, nebo ji z nich odebere. [stickerFileId] je
     * `MessageMedia.Sticker.stickerFileId` / [StickerItem.sendFileId].
     *
     * Oblíbených je omezený počet (serverová volba, Premium ho zvedá); přidání nad limit vytlačí
     * nejstarší. Potvrzení dorazí přes [favoriteStickers].
     */
    suspend fun setFavorite(stickerFileId: Int, favorite: Boolean): Result<Unit>

    /**
     * Sada i s tím, jestli ji má uživatel nainstalovanou. Živý tok: po přidání nebo odebrání
     * (i z jiného zařízení) se přečte znovu. `null`, když se sadu nepodařilo načíst.
     */
    fun stickerSetDetail(setId: Long): Flow<StickerSetDetail?>

    /**
     * Populární sady, které Telegram právě doporučuje. Živý tok: přepočítá se, když se trendy
     * změní, i když si uživatel některou sadu přidá (kvůli [StickerSetSample.isInstalled]).
     */
    fun trendingSets(): Flow<List<StickerSetSample>>

    /** Označí trendy za zhlédnuté, aby je Telegram přestal hlásit jako nové. */
    suspend fun markTrendingViewed(setIds: List<Long>)

    /** Přidá sadu mezi nainstalované, nebo ji odebere. Potvrzení dorazí přes [stickerSetDetail]. */
    suspend fun setInstalled(setId: Long, installed: Boolean): Result<Unit>

    /**
     * Odloží nainstalovanou sadu do archivu (`false` ji z archivu vrátí mezi nainstalované).
     * Archivovaná sada ve výběru není, ale zůstává k dispozici — Telegram tam sady odkládá sám,
     * když jich je víc, než unese limit. Potvrzení dorazí přes [installedSets].
     */
    suspend fun setArchived(setId: Long, archived: Boolean): Result<Unit>

    /**
     * Nové pořadí nainstalovaných sad — to je i pořadí záložek ve výběru a platí na všech
     * zařízeních účtu. Potvrzení dorazí přes [installedSets].
     */
    suspend fun reorderSets(setIds: List<Long>, kind: StickerSetKind = StickerSetKind.Stickers): Result<Unit>

    /** Stránka archivovaných sad od sady za [afterSetId] (`0` = od začátku). */
    suspend fun archivedSets(afterSetId: Long, kind: StickerSetKind = StickerSetKind.Stickers): Result<ArchivedStickerSets>

    /**
     * Sada, kterou správci supergroupy [chatId] nastavili skupině (`SupergroupFullInfo
     * .stickerSetId`). Posílat z ní jde v té skupině, i když ji uživatel nainstalovanou nemá.
     * `null`, když chat supergroupa není (soukromý chat, kanál, malá skupina), sadu nemá, nebo
     * se to nepodařilo zjistit.
     */
    suspend fun groupStickerSet(chatId: Long): GroupStickerSet?

    /**
     * Smí uživatel v chatu [chatId] posílat vlastní emoji? S Premium všude, bez něj jen do
     * Uložených zpráv — ověřeno pokusem: zpráva s vlastním emoji tam entitu podrží i po
     * potvrzení serverem. Jinde by ji TDLib bez Premium zahodila.
     */
    suspend fun canUseCustomEmoji(chatId: Long): Boolean

    /** Nainstalované sady vlastních emoji, v pořadí uživatele. Živý tok jako [installedSets]. */
    fun customEmojiSets(): Flow<List<CustomEmojiSet>>

    /** Vlastní emoji jedné sady; načítá se až při otevření její záložky. */
    suspend fun customEmojiSet(setId: Long): Result<List<CustomEmojiItem>>

    /**
     * Vlastní emoji k hledanému textu: podle klíčových slov samolepek ([text]) i podle emoji, na
     * která se text převedl ([emojis] — tatáž jako výsledky hledání obyčejných emoji). Prochází
     * nainstalované sady; [chatId] proto, že dostupná vlastní emoji se mezi chaty liší.
     */
    suspend fun searchCustomEmoji(text: String, emojis: List<String>, chatId: Long): List<CustomEmojiItem>

    /**
     * Populární sady vlastních emoji k přidání — výběr je nabídne, když uživatel žádnou nemá.
     * Živý tok: po přidání se přepočítá ([CustomEmojiSetSample.isInstalled]).
     */
    fun trendingCustomEmojiSets(): Flow<List<CustomEmojiSetSample>>

    /** Obsah jedné sady. Načítá se až při otevření její záložky — sad bývá desítky. */
    suspend fun stickerSet(setId: Long): Result<List<StickerItem>>

    /** Sady samolepek nalepených na fotku nebo video [fileId] (`getAttachedStickerSets`). */
    suspend fun attachedStickerSets(fileId: Int): Result<List<StickerSetSummary>> =
        Result.failure(UnsupportedOperationException())

    /**
     * Najde samolepky ve vlastních sadách i ve veřejných, a u slova navíc sady podle názvu.
     *
     * Nikdy neselže celé: když nevyjde jeden zdroj (typicky veřejné hledání bez sítě), vrátí se
     * aspoň ten druhý. Prázdný výsledek znamená „nic nenalezeno".
     */
    suspend fun search(query: StickerQuery): StickerSearchResult

    /**
     * Kategorie pro hledání bez psaní (láska, pozdrav, smích…), jak je nabízí Telegram. Každá je
     * jen seznam emoji; samolepky k nim najde [search]. Při chybě prázdný seznam.
     */
    suspend fun categories(): List<StickerCategory>

    /**
     * Samolepky k jednomu [emoji] pro návrh nad vstupním polem: jen z toho, co uživatel zná
     * (vlastní sady, nedávné, oblíbené), ne z veřejných sad — nabízet cizí samolepky ke každému
     * napsanému emoji by bylo dotěrné. Při chybě prázdný seznam.
     */
    suspend fun stickersForEmoji(emoji: String): List<StickerItem>
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
    /**
     * Sady, jejichž název odpovídá slovu: nejdřív nainstalované, pak veřejné. U hledání podle
     * emoji prázdné — názvy sad emoji nemají.
     */
    val sets: List<StickerSetSample> = emptyList(),
)

/** Kategorie pod polem hledání. */
data class StickerCategory(
    /** Název od Telegramu; slouží i jako stabilní klíč. */
    val name: String,
    /** Emoji, které kategorii zastupuje (vlastní emoji Telegramu). */
    val icon: MessageMedia.Sticker,
    /**
     * Ikona je jednobarevná a má se obarvit barvou textu (`needsRepainting`). Telegram ji kreslí
     * bíle, takže na světlém čipu by bez obarvení nebyla vidět.
     */
    val iconTinted: Boolean,
    /** Emoji oddělená mezerou, jak je bere [StickerQuery.Emoji]. */
    val emojis: String,
)

/** Sada otevřená ze samolepky v konverzaci. */
data class StickerSetDetail(
    val id: Long,
    val title: String,
    /** Krátké jméno sady z odkazu `t.me/addstickers/<name>` (viz [stickerSetLink]). */
    val name: String,
    val isInstalled: Boolean,
    /** Oficiální sady (od Telegramu) odebrat nejdou. */
    val isOfficial: Boolean,
    val stickers: List<StickerItem>,
    /** Sada vlastních emoji, ne samolepek — jinak se počítá („N emoji") i kreslí. */
    val isCustomEmoji: Boolean = false,
)

/**
 * Odkaz na sadu, jak ho dávají i oficiální klienti. Aplikace takový odkaz sama otevírá
 * (`InternalLink.StickerSet`), takže kruh se uzavře i u příjemce s Plume.
 */
fun stickerSetLink(name: String): String = "https://t.me/addstickers/$name"

/** Sada s ukázkou samolepek: v trendech a ve výsledcích hledání. */
data class StickerSetSample(
    val id: Long,
    val title: String,
    val isInstalled: Boolean,
    /** Pár samolepek na ukázku. Celou sadu ukáže až detail sady. */
    val covers: List<MessageMedia.Sticker>,
)

/** Sada skupiny a skupina sama — záložka ve výběru ji ukazuje fotkou skupiny, ne obálkou sady. */
data class GroupStickerSet(
    val set: StickerSetSummary,
    val chatId: Long,
    val chatTitle: String,
    val chatPhotoFileId: Int?,
)

/** Vlastní emoji ve výběru: id a obyčejné emoji, za které stojí (to jde do textu). */
data class CustomEmojiItem(val id: Long, val emoji: String)

/** Sada vlastních emoji v záložkách výběru emoji. */
data class CustomEmojiSet(val id: Long, val title: String, val cover: CustomEmojiItem?)

/** Populární sada vlastních emoji s ukázkou. */
data class CustomEmojiSetSample(
    val id: Long,
    val title: String,
    val isInstalled: Boolean,
    val covers: List<CustomEmojiItem>,
)

/** Sada v záložkách výběru. */
data class StickerSetSummary(
    val id: Long,
    val title: String,
    /** Samolepka, která sadu zastupuje v záložce. `null`, když sada žádnou nemá. */
    val cover: MessageMedia.Sticker?,
    /** Počet samolepek v sadě. */
    val size: Int = 0,
    /** Oficiální sady (od Telegramu) odebrat nejdou. */
    val isOfficial: Boolean = false,
    /**
     * Obálka sady vlastních emoji jako id — kreslí se přes `CustomEmojiImage`, které umí
     * jednobarevné emoji obarvit. Jako samolepka ([cover]) by bylo bílé na světlém pozadí.
     */
    val coverCustomEmojiId: Long? = null,
)

/** Druh sady: samolepky, nebo vlastní emoji. Telegram drží pořadí i archiv každého zvlášť. */
enum class StickerSetKind { Stickers, Emoji }

/** Stránka archivovaných sad. */
data class ArchivedStickerSets(
    /** Kolik archivovaných sad je celkem (přibližně, jak hlásí server). */
    val total: Int,
    val sets: List<StickerSetSummary>,
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
    /** Vlastní emoji (sada emoji) — kreslí se přes `CustomEmojiImage`, viz [StickerSetSummary.coverCustomEmojiId]. */
    val customEmojiId: Long? = null,
)
