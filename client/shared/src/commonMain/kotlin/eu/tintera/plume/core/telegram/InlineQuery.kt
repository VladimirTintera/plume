package eu.tintera.plume.core.telegram

/**
 * Rozepsaný dotaz na inline bota: `@gif kočka` v poli je bot „gif" a dotaz „kočka".
 *
 * Dotaz se na bota nepozná podle toho, KDO je `@jméno` — to se zjistí až po síti —, ale podle
 * tvaru textu: zavináč na úplném začátku, jméno a za ním mezera. Mezera je to, čím uživatel
 * říká „tohle je bot, teď píšu dotaz"; do té doby může psát zmínku (`@jana ahoj` se pozná až
 * dotazem, že Jana botem není). Tak to dělají i oficiální klienti.
 */
data class InlineQueryDraft(val username: String, val query: String)

/**
 * [InlineQueryDraft] z rozepsaného textu, nebo `null`, když text dotaz na bota není.
 *
 * Jméno je jen písmena, číslice a podtržítko a začíná písmenem (tak ho Telegram dovolí).
 * Dotaz je všechno za PRVNÍ mezerou, jak stojí — mezera navíc je součást dotazu, bot ji dostane.
 * Zalomení řádku hned za jménem dotaz není: to už uživatel píše zprávu, ne dotaz.
 */
fun inlineQueryOf(draft: String): InlineQueryDraft? {
    if (!draft.startsWith("@")) return null
    val space = draft.indexOf(' ')
    if (space < 0) return null
    val username = draft.substring(1, space)
    if (username.isEmpty() || username.length > MAX_USERNAME_LENGTH) return null
    if (!username.first().isAsciiLetter()) return null
    if (!username.all { it.isAsciiLetter() || it in '0'..'9' || it == '_' }) return null
    return InlineQueryDraft(username, draft.substring(space + 1))
}

/**
 * Text pole pro inline dotaz na [bot]: `@bot dotaz`. Mezera za jménem je vždycky — i s prázdným
 * dotazem, protože teprve s ní pozná [inlineQueryOf] dotaz a za ni se píše.
 */
fun inlineQueryText(bot: String, query: String): String = "@$bot $query"

private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

private const val MAX_USERNAME_LENGTH = 32

/**
 * Bot, který umí inline dotazy.
 *
 * [placeholder] je nápověda od bota („Search GIFs…"); prázdná, když ji bot nemá. [needLocation]
 * = bot chce k dotazu polohu (hledání míst v okolí) — bez ní často nevrátí nic.
 */
data class InlineBotInfo(
    val userId: Long,
    val username: String,
    val placeholder: String,
    val needLocation: Boolean,
)

/**
 * Jedna stránka výsledků inline dotazu.
 *
 * [queryId] určuje, KTERÝ dotaz se posílá: výsledek se odesílá dvojicí `queryId` + [InlineResult.id],
 * ne obsahem — obsah zprávy si bot připravil sám a může se lišit od toho, co ukazuje náhled.
 * [nextOffset] prázdný = další stránka není.
 */
data class InlineResultsPage(
    val queryId: Long,
    val results: List<InlineResult>,
    val nextOffset: String,
    val button: InlineResultsButton?,
)

/** Tlačítko nad výsledky: bot chce, aby se u něj uživatel napřed něco nastavil. */
sealed interface InlineResultsButton {
    val text: String

    /** Otevře chat s botem a pošle mu `/start` s [parameter]. */
    data class StartBot(override val text: String, val parameter: String) : InlineResultsButton

    /** Mini aplikace — Plume ji neumí, tlačítko jen vysvětlí proč. */
    data class WebApp(override val text: String) : InlineResultsButton
}

/**
 * Jeden výsledek inline dotazu, jak ho kreslí panel nad polem.
 *
 * Obrázkové výsledky ([Gif], [Picture], [Sticker]) jdou do mřížky, ostatní ([Row]) jsou řádky
 * s náhledem, titulkem a popisem přes celou šířku. Rozlišuje se to podle druhu, ne podle bota —
 * jeden bot může vrátit obojí.
 */
sealed interface InlineResult {
    val id: String

    data class Gif(override val id: String, val animation: MessageMedia.Animation) : InlineResult

    /** Fotka, nebo obal videa ([isVideo]). */
    data class Picture(
        override val id: String,
        val fileId: Int,
        val minithumbnail: Minithumbnail?,
        val isVideo: Boolean,
    ) : InlineResult

    data class Sticker(override val id: String, val sticker: MessageMedia.Sticker) : InlineResult

    data class Row(
        override val id: String,
        val kind: InlineRowKind,
        val title: String,
        val description: String,
        val thumbnailFileId: Int?,
    ) : InlineResult
}

/** Druh řádku — podle něj ikona, když bot náhled nedal. */
enum class InlineRowKind { Article, File, Audio, Voice, Contact, Location, Game }

/**
 * Výsledky inline dotazu v panelu nad polem.
 *
 * Výsledky patří DVOJICI bot + dotaz ([botUsername], [resultsQuery]), ne tomu, co je zrovna v poli:
 * během psaní dalšího znaku zůstávají předchozí výsledky vidět (panel by jinak problikl
 * načítáním) a stránka pro starý dotaz se zahodí ([withPage]).
 */
data class InlineQueryState(
    val bot: InlineBotInfo,
    /** Dotaz, na který se právě čeká nebo čekalo — z pole. */
    val query: String,
    /** Dotaz, ke kterému patří [results]; `null` = ještě žádná odpověď. */
    val resultsQuery: String? = null,
    val queryId: Long = 0,
    val results: List<InlineResult> = emptyList(),
    val nextOffset: String = "",
    val button: InlineResultsButton? = null,
    val loading: Boolean = true,
    val failed: Boolean = false,
) {
    val botUsername: String get() = bot.username

    /** Odpověď pro aktuální dotaz dorazila a nic v ní není. */
    val nothingFound: Boolean
        get() = !loading && !failed && resultsQuery == query && results.isEmpty() && button == null

    val canLoadMore: Boolean get() = !loading && nextOffset.isNotEmpty() && resultsQuery == query

    /** Nový dotaz se stejným botem: výsledky zůstanou, dokud nepřijdou nové. */
    fun withQuery(query: String): InlineQueryState = copy(query = query, loading = true, failed = false)

    /**
     * Přidá stránku pro [forQuery]. [append] = další stránka téhož dotazu, jinak první (nahradí
     * předchozí výsledky). Stránka pro jiný dotaz, než na který se teď čeká, se zahodí.
     */
    fun withPage(forQuery: String, page: InlineResultsPage, append: Boolean): InlineQueryState {
        if (forQuery != query) return this
        // Bot občas vrátí tentýž výsledek na dvou stránkách; dva stejné klíče by mřížku shodily.
        val merged = if (append) (results + page.results).distinctBy { it.id } else page.results.distinctBy { it.id }
        return copy(
            resultsQuery = forQuery,
            queryId = page.queryId,
            results = merged,
            nextOffset = page.nextOffset,
            // Tlačítko posílá jen první stránka; další ho nemají a zmizet nemá.
            button = if (append) button else page.button,
            loading = false,
            failed = false,
        )
    }

    fun withFailure(forQuery: String): InlineQueryState =
        if (forQuery != query) this else copy(loading = false, failed = true)
}
