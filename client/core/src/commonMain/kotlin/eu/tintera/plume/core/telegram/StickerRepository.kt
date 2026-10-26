package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

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
     * (i z jiného zařízení) i po změně obsahu sady se přečte znovu. `null`, když se sadu
     * nepodařilo načíst.
     */
    fun stickerSetDetail(setId: Long): Flow<StickerSetDetail?>

    /**
     * Id sad, jejichž obsah nebo název se změnil (autor přidal samolepku, přejmenoval sadu).
     * Pro obsah sad načtený jednou ([stickerSet], [customEmojiSet]) — kdo si ho drží, načte ho znovu.
     */
    fun changedSets(): Flow<Long> = emptyFlow()

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

    /**
     * Vlastní emoji, která Telegram nabízí na profilovou fotku — účtu
     * (`getDefaultProfilePhotoCustomEmojiStickers`), nebo skupiny a kanálu ([forChat],
     * `getDefaultChatPhotoCustomEmojiStickers`). Jdou použít i bez Premium.
     */
    suspend fun defaultPhotoEmoji(forChat: Boolean): Result<List<CustomEmojiItem>> = Result.success(emptyList())

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

    /** Sady samolepek, které uživatel sám vytvořil — jen do nich jde přidávat. */
    suspend fun ownedSets(): Result<List<OwnedStickerSet>> = Result.failure(UnsupportedOperationException())

    /**
     * Založí novou sadu [title] s první samolepkou. [name] je jméno v odkazu
     * `t.me/addstickers/…`; prázdné = navrhne ho Telegram podle názvu.
     */
    suspend fun createSet(title: String, sticker: NewStickerFile, name: String = ""): Result<OwnedStickerSet> =
        Result.failure(UnsupportedOperationException())

    /** Volné a platné jméno sady pro název [title] (`getSuggestedStickerSetName`). */
    suspend fun suggestSetName(title: String): Result<String> = Result.failure(UnsupportedOperationException())

    /** Jde jméno [name] použít pro novou sadu? (`checkStickerSetName`) */
    suspend fun checkSetName(name: String): Result<StickerSetNameCheck> = Result.failure(UnsupportedOperationException())

    /**
     * Nová klíčová slova samolepky ve vlastní sadě — NAHRADÍ dosavadní (TDLib je číst neumí,
     * takže se předvyplnit nedají). Prázdný seznam je smaže.
     */
    suspend fun setKeywords(stickerFileId: Int, keywords: List<String>): Result<Unit> =
        Result.failure(UnsupportedOperationException())

    /** Přidá samolepku do vlastní sady (nejvýš [OwnedStickerSet.MAX_SIZE]). */
    suspend fun addToSet(set: OwnedStickerSet, sticker: NewStickerFile): Result<Unit> =
        Result.failure(UnsupportedOperationException())

    /** Přejmenuje vlastní sadu [name] (jméno v odkazu zůstává). */
    suspend fun renameSet(name: String, title: String): Result<Unit> = Result.failure(UnsupportedOperationException())

    /** Odebere samolepku (podle souboru [stickerFileId]) z vlastní sady. */
    suspend fun removeFromSet(stickerFileId: Int): Result<Unit> = Result.failure(UnsupportedOperationException())

    /** Přesune samolepku ve vlastní sadě na [position] (od nuly). */
    suspend fun moveInSet(stickerFileId: Int, position: Int): Result<Unit> = Result.failure(UnsupportedOperationException())

    /** Nová emoji samolepky ve vlastní sadě (1–20 emoji). */
    suspend fun setEmojis(stickerFileId: Int, emojis: String): Result<Unit> = Result.failure(UnsupportedOperationException())

    /**
     * Nová obálka vlastní sady [setId] / [name]: soubor [path] (WebP 100 × 100, viz
     * `StickerMaker.makeCover`), `null` = bez vlastní obálky, obálkou je zase první samolepka.
     */
    suspend fun setSetCover(setId: Long, name: String, path: String?): Result<Unit> = Result.failure(UnsupportedOperationException())

    /** Smaže vlastní sadu [setId] / [name] úplně — zmizí i ostatním, kdo ji mají. */
    suspend fun deleteSet(setId: Long, name: String): Result<Unit> = Result.failure(UnsupportedOperationException())

    /** Sady samolepek nalepených na fotku nebo video [fileId] (`getAttachedStickerSets`). */
    suspend fun attachedStickerSets(fileId: Int): Result<List<StickerSetSummary>> =
        Result.failure(UnsupportedOperationException())

    /**
     * Obrys samolepky jako SVG path (`getStickerOutlineSvgPath`, offline), v souřadnicích
     * samolepky; prázdný, když ho TDLib nezná. Kreslí se místo samolepky, dokud se nenačte.
     */
    suspend fun outline(stickerFileId: Int): String = ""

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

    /**
     * Samolepky na pozdrav do prázdného soukromého chatu (`getGreetingStickers` — TDLib tím hledá
     * emoji „👋⭐️", stejnou sadu bere i oficiální klient). Při chybě prázdný seznam.
     */
    suspend fun greetingStickers(): List<StickerItem> = emptyList()
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
    /** Sadu vytvořil uživatel — jde ji upravovat. */
    val isOwned: Boolean = false,
    /** Vlastní obálka sady; `null` = obálkou je první samolepka. */
    val customCover: MessageMedia.Sticker? = null,
)

/**
 * Odkaz na sadu, jak ho dávají i oficiální klienti. Aplikace takový odkaz sama otevírá
 * (`InternalLink.StickerSet`), takže kruh se uzavře i u příjemce s Plume.
 */
fun stickerSetLink(name: String): String = "https://t.me/addstickers/$name"

/** Vlastní sada samolepek (`getOwnedStickerSets`): sem jde přidávat. */
data class OwnedStickerSet(
    val id: Long,
    /** Jméno v odkazu `t.me/addstickers/…`; podle něj se do sady přidává. */
    val name: String,
    val title: String,
    val size: Int,
    val cover: MessageMedia.Sticker?,
) {
    /** Sada je plná — další samolepku Telegram nepřijme. */
    val isFull: Boolean get() = size >= MAX_SIZE

    companion object {
        /** Kolik běžných samolepek sada unese (`createNewStickerSet`: 1–120). */
        const val MAX_SIZE = 120
    }
}

/**
 * Samolepka k nahrání: PNG nebo WebP, delší strana 512 px (WebP z PNG si udělá server),
 * 1–20 emoji, ke kterým se nabízí, a nepovinná klíčová slova pro hledání ([stickerKeywords]).
 */
data class NewStickerFile(val path: String, val emojis: String, val keywords: List<String> = emptyList())

/** Výsledek kontroly jména nové sady. */
enum class StickerSetNameCheck {
    Ok,

    /**
     * Server jméno odmítl — mimo povolené znaky, nebo pravidla, která pole nehlídá: na začátku
     * musí být písmeno (`1test` neprošlo, ověřeno na emulátoru).
     */
    Invalid,

    /** Jméno už má jiná sada. */
    Occupied,
}

/**
 * Jméno sady, jak ho uživatel píše: jen znaky, které Telegram v odkazu sady přijme (anglická
 * písmena, číslice, podtržítko), nejvýš [STICKER_SET_NAME_MAX]. Mezera se stane podtržítkem —
 * kdo píše „moje sada", myslí `moje_sada`. Ostatní znaky (diakritika) vypadnou rovnou v poli,
 * ať se nečeká na odmítnutí od serveru.
 */
fun stickerSetNameInput(text: String): String =
    text.replace(' ', '_').filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' }
        .take(STICKER_SET_NAME_MAX)

const val STICKER_SET_NAME_MAX = 64

/**
 * Klíčová slova samolepky z pole: oddělená čárkou, bez okrajových mezer, prázdná a opakovaná
 * (bez ohledu na velikost písmen) vynechaná. Jestli se vejdou do limitů, říká
 * [stickerKeywordsFit] — oříznout je potichu by schovalo, co se neuloží.
 */
fun stickerKeywords(text: String): List<String> =
    text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }

/** Nejvýš [STICKER_KEYWORDS_MAX] slov s celkovou délkou nejvýš [STICKER_KEYWORDS_LENGTH] (TDLib `inputSticker`). */
fun stickerKeywordsFit(keywords: List<String>): Boolean =
    keywords.size <= STICKER_KEYWORDS_MAX && keywords.sumOf { it.length } <= STICKER_KEYWORDS_LENGTH

const val STICKER_KEYWORDS_MAX = 20
const val STICKER_KEYWORDS_LENGTH = 64

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
    /** Sadu vytvořil uživatel — jde ji upravovat. */
    val isOwned: Boolean = false,
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
