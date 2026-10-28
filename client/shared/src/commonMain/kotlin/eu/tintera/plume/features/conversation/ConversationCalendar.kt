package eu.tintera.plume.features.conversation

import androidx.compose.runtime.Immutable
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.HistoryCounts
import eu.tintera.plume.core.telegram.SharedMediaKind
import eu.tintera.plume.core.telegram.mergeDayCounts
import eu.tintera.plume.features.profile.MediaCalendarState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import kotlin.time.Instant

/**
 * Všechny zprávy po dnech — jen to, co je spočtené: souvislý úsek od nejnovější zprávy po
 * [coveredFrom] a starší ostrůvky ([islands]). Ostatní dny kalendář neukazuje jako prázdné, ale
 * jako neznámé.
 */
@Immutable
data class AllMessagesCalendar(
    val days: Map<LocalDate, Int> = emptyMap(),
    /** Den nejstarší spočtené zprávy, spočtený jen zčásti; `null` = zatím nic. */
    val coveredFrom: LocalDate? = null,
    /** Odkud pokračovat ke starším. */
    val oldestMessageId: Long? = null,
    /** Prochází se lokální databáze. */
    val counting: Boolean = true,
    /** Stahují se starší zprávy ze serveru („Načíst starší"). */
    val loadingOlder: Boolean = false,
    /** Server starší zprávy nemá — spočtené je všechno až po začátek chatu. */
    val reachedStart: Boolean = false,
    val failed: Boolean = false,
    /**
     * Měsíc první zprávy chatu (ptá se server); kalendář jde rolovat až k němu, i když zprávy
     * mezi ním a [coveredFrom] v zařízení nejsou. `null` = nezjištěno, osa končí u [coveredFrom].
     */
    val firstMonth: YearMonth? = null,
    /** Úseky historie starší než [coveredFrom] — viz [Island]. */
    val islands: List<Island> = emptyList(),
)

/**
 * Úsek historie starší než souvislý úsek od nejnovější zprávy. Uživatel skočil na staré datum,
 * TDLib si stáhl okno zpráv kolem něj a uložil ho — mezi ním a souvislým úsekem ale zůstala
 * díra, přes kterou lokální průchod (`getChatHistory(onlyLocal = true)`) nevede: TDLib vydá
 * z databáze jen souvislé úseky. Do ostrůvku se proto vstupuje zprávou, na které konverzace
 * stála ([ConversationCalendar.open]), a od ní se počítá ke starším.
 *
 * Dny mezi [oldestDay] a [newestDay] jsou spočtené celé (i ty bez zpráv), krajní dny jen zčásti.
 */
@Immutable
data class Island(
    val days: Map<LocalDate, Int>,
    val oldestDay: LocalDate,
    val newestDay: LocalDate,
    val oldestMessageId: Long,
    val newestMessageId: Long,
)

/**
 * Kalendář konverzace: kolik zpráv přišlo který den ([filter] `null`), nebo kolik fotek, souborů,
 * odkazů… ([filter] druh).
 */
@Immutable
data class ChatCalendarState(
    /** `null` = všechny zprávy, jinak jen zprávy toho druhu. */
    val filter: SharedMediaKind? = null,
    val all: AllMessagesCalendar = AllMessagesCalendar(),
    /** Dny se zprávami druhu [filter], po stránkách od serveru; `null` u všech zpráv. */
    val media: MediaCalendarState? = null,
)

/**
 * Kalendář zpráv po dnech, otevřený z ⋮ konverzace. Patří k [ConversationViewModel], ale stav
 * má vlastní ([state]): `ConversationState` čte celá obrazovka a průběžné počítání by ji
 * skládalo znovu s každým úsekem.
 *
 * **Všechny zprávy se počítají z lokální databáze TDLib**, ne ze serveru. Server po dnech vydá
 * jen druhy obsahu (`getChatMessageCalendar` filtr „všechno" odmítne), odhad z čísel zpráv na
 * hranicích dnů stál na kanálu půl minuty na měsíc a tam, kde TDLib zná jen kus historie, vracel
 * nesmysly. V databázi je, co uživatel kdy načetl, a od toho dne dál je počet přesný; starší jde
 * dotáhnout ze serveru po kusech ([loadOlder]). Druhy počítá server celé ([setFilter]).
 */
class ConversationCalendar(
    private val chatId: Long,
    private val repository: ConversationRepository,
    private val scope: CoroutineScope,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
) {
    private val _state = MutableStateFlow<ChatCalendarState?>(null)

    /** `null` = kalendář je zavřený. */
    val state: StateFlow<ChatCalendarState?> = _state.asStateFlow()

    private var allJob: Job? = null
    private var firstMonthJob: Job? = null
    private var mediaJob: Job? = null

    /**
     * Zprávy, na kterých konverzace stála, když se kalendář otevíral — vstupy do ostrůvků. Drží
     * se po celou dobu obrazovky: po dalším skoku jinam je okno v konverzaci jiné, ale to staré
     * v databázi zůstalo.
     */
    private val anchors = mutableSetOf<Long>()

    /**
     * Otevře kalendář a projde lokální databázi znovu: zprávy staré z minula tam jsou dál a k nim
     * nové i ty, které mezitím přinesl server — počítat od nuly je jednodušší než slévat.
     * [anchor] = nejnovější zpráva, kterou konverzace má načtenou; je-li starší než souvislý
     * úsek, kalendář od ní spočítá ostrůvek.
     */
    fun open(anchor: Long? = null) {
        anchor?.let { anchors += it }
        _state.value = ChatCalendarState()
        allJob?.cancel()
        allJob = scope.launch {
            countLocal()
            countIslands()
        }
        firstMonthJob?.cancel()
        firstMonthJob = scope.launch { findFirstMonth() }
    }

    fun close() {
        allJob?.cancel()
        firstMonthJob?.cancel()
        mediaJob?.cancel()
        _state.value = null
    }

    /** Přepne mezi všemi zprávami a jedním druhem; druh se načítá pokaždé od nejnovějšího dne. */
    fun setFilter(kind: SharedMediaKind?) {
        val current = _state.value ?: return
        if (current.filter == kind) return
        mediaJob?.cancel()
        _state.value = current.copy(filter = kind, media = kind?.let { MediaCalendarState(loading = true) })
        if (kind != null) loadMediaPage(kind, fromMessageId = 0)
    }

    /** Další stránka dnů druhu, když se k nejstaršímu načtenému měsíci dojede. */
    fun loadMoreMedia() {
        val current = _state.value ?: return
        val kind = current.filter ?: return
        val media = current.media ?: return
        if (media.loading || media.reachedEnd) return
        _state.value = current.copy(media = media.copy(loading = true))
        loadMediaPage(kind, media.nextFromMessageId)
    }

    /** „Načíst starší": další kus historie ze serveru, za nejstarší spočtenou zprávou. */
    fun loadOlder() {
        val all = _state.value?.all ?: return
        if (all.counting || all.loadingOlder || all.reachedStart) return
        val from = all.oldestMessageId ?: return
        updateAll { it.copy(loadingOlder = true, failed = false) }
        allJob = scope.launch {
            repository.messageCountsByDay(chatId, from, onlyLocal = false, maxMessages = OLDER_CHUNK, zone = zone)
                .onSuccess { counts -> updateAll { it.withCounts(counts).copy(loadingOlder = false, reachedStart = counts.reachedEnd) } }
                .onFailure { updateAll { it.copy(loadingOlder = false, failed = true) } }
        }
    }

    /** Lokální databáze po úsecích, ať se kalendář plní hned a ne až po posledním. */
    private suspend fun countLocal() {
        var from = 0L
        var counted = 0
        while (true) {
            val counts = repository.messageCountsByDay(chatId, from, onlyLocal = true, maxMessages = LOCAL_CHUNK, zone = zone)
                .getOrElse {
                    updateAll { it.copy(counting = false, failed = true) }
                    return
                }
            updateAll { it.withCounts(counts) }
            counted += counts.days.values.sum()
            val next = counts.oldestMessageId
            if (counts.reachedEnd || next == null || counted >= LOCAL_CAP) break
            from = next
        }
        updateAll { it.copy(counting = false) }
    }

    /**
     * Ostrůvky od kotev starších než souvislý úsek. Kotva uvnitř už spočteného ostrůvku se
     * přeskočí — dvě kotvy v témž okně by ho jinak sečetly dvakrát.
     */
    private suspend fun countIslands() {
        for (anchor in anchors.sortedDescending()) {
            val all = _state.value?.all ?: return
            val coveredOldest = all.oldestMessageId ?: return
            if (all.reachedStart || anchor >= coveredOldest) continue
            if (all.islands.any { anchor in it.oldestMessageId..it.newestMessageId }) continue
            val counts = repository.messageCountsByDay(
                chatId = chatId,
                fromMessageId = anchor,
                onlyLocal = true,
                maxMessages = ISLAND_CAP,
                zone = zone,
                includeFrom = true,
            ).getOrNull() ?: continue
            val island = islandOf(anchor, counts, zone) ?: continue
            updateAll { it.copy(islands = it.islands + island) }
        }
    }

    /**
     * Zprávy dne [date] pro „Shrnout den": od první zprávy dalšího dne (nebo od nejnovější, je-li
     * den poslední) se historie čte ke starším, dokud nesáhne před začátek dne. Od nejnovějších,
     * takže u dne nad stropem ([DAY_SUMMARY_CAP]) zůstane jeho konec. Servisní zprávy pryč —
     * o obsahu nic neříkají, jako u shrnutí konverzace.
     */
    suspend fun messagesOfDay(date: LocalDate): List<ChatMessage> {
        val start = date.atStartOfDayIn(zone).epochSeconds.toInt()
        val end = date.plus(DatePeriod(days = 1)).atStartOfDayIn(zone).epochSeconds.toInt()
        var from = repository.firstMessageFrom(chatId, end).getOrNull() ?: 0L
        val seen = HashSet<Long>()
        val day = mutableListOf<ChatMessage>()
        for (page in 0 until DAY_SUMMARY_PAGES) {
            val fresh = repository.history(chatId, from, limit = DAY_SUMMARY_PAGE).getOrNull().orEmpty()
                .filter { seen.add(it.id) }
            if (fresh.isEmpty()) break
            day += fresh.filter { it.timestamp in start until end && !it.isService }
            val oldest = fresh.minBy { it.id }
            if (oldest.timestamp < start || day.size >= DAY_SUMMARY_CAP) break
            from = oldest.id
        }
        return day.sortedBy { it.id }.takeLast(DAY_SUMMARY_CAP)
    }

    private suspend fun findFirstMonth() {
        // Před spuštěním Telegramu nic není — „první zpráva od té doby" je první zpráva chatu.
        val first = repository.firstMessageFrom(chatId, TELEGRAM_START).getOrNull() ?: return
        val timestamp = repository.message(chatId, first).getOrNull()?.timestamp ?: return
        val month = Instant.fromEpochSeconds(timestamp.toLong()).toLocalDateTime(zone).date.yearMonth
        updateAll { it.copy(firstMonth = month) }
    }

    private fun AllMessagesCalendar.withCounts(counts: HistoryCounts): AllMessagesCalendar {
        val oldest = counts.oldestTimestamp ?: return this
        val oldestId = counts.oldestMessageId ?: return this
        return copy(
            days = mergeDayCounts(days, counts.days),
            coveredFrom = Instant.fromEpochSeconds(oldest.toLong()).toLocalDateTime(zone).date,
            oldestMessageId = oldestId,
            // Ostrůvek, do kterého souvislý úsek dorostl, by se s ním sečetl dvakrát.
            islands = islands.filter { it.newestMessageId < oldestId },
        )
    }

    private fun updateAll(change: (AllMessagesCalendar) -> AllMessagesCalendar) {
        _state.update { it?.copy(all = change(it.all)) }
    }

    private fun loadMediaPage(kind: SharedMediaKind, fromMessageId: Long) {
        mediaJob = scope.launch {
            val result = repository.mediaCalendar(chatId, fromMessageId, kind)
            _state.update { state ->
                val media = state?.media?.takeIf { state.filter == kind } ?: return@update state
                state.copy(
                    media = result.fold(
                        onSuccess = { media.withPage(it) },
                        // Stránka, která nepřišla, ukončí dotahování — jinak by se zkoušela dokola,
                        // dokud se k ní dojíždí.
                        onFailure = { media.copy(loading = false, reachedEnd = true) },
                    ),
                )
            }
        }
    }
}

/** Ostrůvek spočtený od kotvy [anchor]; `null`, když v databázi není ani ona. */
internal fun islandOf(anchor: Long, counts: HistoryCounts, zone: TimeZone): Island? {
    fun day(timestamp: Int?): LocalDate? = timestamp?.let { Instant.fromEpochSeconds(it.toLong()).toLocalDateTime(zone).date }
    val oldestDay = day(counts.oldestTimestamp) ?: return null
    val newestDay = day(counts.newestTimestamp) ?: return null
    return Island(
        days = counts.days,
        oldestDay = oldestDay,
        newestDay = newestDay,
        oldestMessageId = counts.oldestMessageId ?: anchor,
        newestMessageId = anchor,
    )
}

/** Kolik zpráv dne nejvýš jde do shrnutí; model je stejně skládá po částech. */
private const val DAY_SUMMARY_CAP = 500

/** Stránka a nejvýš stránek při čtení dne — rušný den se nesmí číst donekonečna. */
private const val DAY_SUMMARY_PAGE = 100
private const val DAY_SUMMARY_PAGES = 30

/** 1. 8. 2013, kdy Telegram začal — dřív žádná zpráva není. */
private const val TELEGRAM_START = 1_375_315_200

/** Strop jednoho ostrůvku; okno po skoku má desítky zpráv, víc jen po dlouhém čtení. */
private const val ISLAND_CAP = 10_000

/** Kolik zpráv z lokální databáze jeden úsek; mezi úseky se kalendář překreslí. */
private const val LOCAL_CHUNK = 2_000

/**
 * Strop lokálního průchodu. Databáze rušné skupiny může mít statisíce zpráv; co je za stropem,
 * dočte „Načíst starší" (TDLib je vrátí z databáze, ne ze serveru).
 */
private const val LOCAL_CAP = 30_000

/** „Načíst starší" stáhne deset stránek historie — deset dotazů na server. */
private const val OLDER_CHUNK = 1_000
