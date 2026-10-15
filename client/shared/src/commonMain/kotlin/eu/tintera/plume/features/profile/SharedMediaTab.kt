package eu.tintera.plume.features.profile

import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.SharedMediaKind
import eu.tintera.plume.core.telegram.SharedMediaPage

/**
 * Stav jedné záložky sdíleného obsahu: načtené zprávy, odkud číst dál a ke kterému dotazu patří.
 *
 * Čistá data a přechody, jako [MemberList] — na zařízení se chyba v nich pozná až cizím
 * výsledkem vmíchaným do jiného hledání, nebo pádem `LazyColumn` na dvakrát ukázané zprávě.
 */
data class SharedMediaTab(
    val messages: List<ChatMessage> = emptyList(),
    val loading: Boolean = false,
    val reachedEnd: Boolean = false,
    /** Dotaz, ke kterému [messages] patří — ne ten, který se právě píše. */
    val query: String = "",
    /** Odkud číst další stránku (viz [SharedMediaPage.nextFromMessageId]); `0` = od nejnovější. */
    val nextFromMessageId: Long = 0,
    /**
     * Seznam sahá k nejnovější zprávě. Po skoku rychlým rolováním ([withWindow]) ne — nad oknem
     * jsou novější, které se dotahují rolováním nahoru ([withNewerPage]).
     */
    val reachedNewest: Boolean = true,
    val loadingNewer: Boolean = false,
    /** Zpráva, na kterou má mřížka po skoku dorolovat; jednorázové, obrazovka ho spotřebuje. */
    val scrollTo: Long? = null,
) {
    /** Dotáhnout další stránku jde, jen když už nějaká přišla a server ještě nedošel na konec. */
    val canLoadMore: Boolean get() = !loading && !reachedEnd && nextFromMessageId != 0L

    /** Dotáhnout novější jde jen v okně po skoku, které k nejnovější nesahá. */
    val canLoadNewer: Boolean get() = !reachedNewest && !loadingNewer && messages.isNotEmpty()

    /**
     * Okno kolem zprávy [target] po skoku rychlým rolováním: nahradí celý seznam (skáče se přes
     * stovky nenačtených zpráv), k nejnovější nesahá a mřížka má dorolovat na [target].
     */
    fun withWindow(target: Long, page: SharedMediaPage): SharedMediaTab = SharedMediaTab(
        messages = page.messages.distinctBy { it.id },
        reachedEnd = page.reachedEnd,
        query = query,
        nextFromMessageId = page.nextFromMessageId,
        reachedNewest = false,
        scrollTo = target,
    )

    /**
     * Novější zprávy nad oknem. Stránka čtená od nejnovější načtené [fromMessageId] ji obsahuje
     * taky, a k tomu až pár novějších; co nového nepřinese, znamená, že okno k nejnovější došlo.
     * Patří jen k oknu, ze kterého se četla — po dalším skoku se zahodí.
     */
    fun withNewerPage(fromMessageId: Long, page: SharedMediaPage): SharedMediaTab {
        if (messages.firstOrNull()?.id != fromMessageId) return this
        val newer = page.messages.filter { it.id > fromMessageId }
        return copy(
            messages = (newer + messages).distinctBy { it.id },
            loadingNewer = false,
            reachedNewest = newer.isEmpty(),
        )
    }

    /** První stránka nahradí všechno, i rozdělané dotahování předchozího dotazu. */
    fun withFirstPage(query: String, page: SharedMediaPage): SharedMediaTab = SharedMediaTab(
        messages = page.messages.distinctBy { it.id },
        reachedEnd = page.reachedEnd,
        query = query,
        nextFromMessageId = page.nextFromMessageId,
    )

    /**
     * Další stránka se připojí, jen když pořád patří TOMUHLE seznamu — stejný dotaz, stejné místo.
     * Pomalá odpověď na předchozí dotaz by jinak přimíchala cizí výsledky.
     *
     * Tatáž zpráva na dvou stránkách zůstane jednou: přibude-li mezitím novější, posune se řada
     * o jedno místo a dva stejné klíče by `LazyColumn` shodily.
     */
    fun withNextPage(query: String, fromMessageId: Long, page: SharedMediaPage): SharedMediaTab {
        if (query != this.query || fromMessageId != nextFromMessageId) return this
        return copy(
            messages = (messages + page.messages).distinctBy { it.id },
            loading = false,
            reachedEnd = page.reachedEnd,
            nextFromMessageId = if (page.reachedEnd) nextFromMessageId else page.nextFromMessageId,
        )
    }

    /** Stránka se nepodařila: dotahování skončí, aby se nezkoušelo při každém scrollu dokola. */
    fun withFailedPage(query: String): SharedMediaTab =
        if (query != this.query) this else copy(loading = false, reachedEnd = true)
}

/**
 * Nabídnout nad záložkou hledání?
 *
 * **Jen u souborů, odkazů a hudby.** Ty mají jméno, titulek nebo text, podle kterého se hledá;
 * u fotek a videí by se hledalo jen v popiscích, které většina nemá, a prázdný výsledek nad
 * mřížkou plnou fotek by vypadal jako chyba. **A jen tam, kde se obsah nevejde na obrazovku**
 * ([SHARED_SEARCH_MIN_COUNT]) — nad třemi soubory by pole jen ubíralo místo. Rozepsané hledání
 * se ale neschová, ani kdyby počet klesl.
 */
fun offersSharedSearch(kind: SharedMediaKind, count: Int, query: String): Boolean =
    kind in SEARCHABLE_KINDS && (count > SHARED_SEARCH_MIN_COUNT || query.isNotEmpty())

/**
 * Nabízí se nad Médii kalendář po dnech? Od stejného počtu jako hledání v jiných záložkách —
 * u pár fotek je celá mřížka vidět naráz a kalendář by jen ubíral místo.
 */
fun offersMediaCalendar(count: Int): Boolean = count > SHARED_SEARCH_MIN_COUNT

/**
 * Rychlé rolování (úchyt s datem) u Médií? Až tam, kde je jich na několik obrazovek — u pár
 * desítek fotek se mřížka projede prstem a úchyt by jen překážel.
 */
fun offersFastScroll(count: Int): Boolean = count > FAST_SCROLL_MIN_COUNT

/** Od kolika médií se nabízí rychlé rolování; při třech sloupcích je to třicet řad. */
const val FAST_SCROLL_MIN_COUNT = 90

/** Druhy, ve kterých je podle čeho hledat — název souboru, skladby, text s odkazem. GIF a kolečko ne. */
private val SEARCHABLE_KINDS = setOf(SharedMediaKind.Files, SharedMediaKind.Music, SharedMediaKind.Links)

/** Od kolika položek se nad záložkou nabízí hledání — zhruba co se vejde na obrazovku telefonu. */
const val SHARED_SEARCH_MIN_COUNT = 8
