package eu.tintera.plume.features.conversation

import androidx.compose.runtime.Immutable
import eu.tintera.plume.core.telegram.CalendarDayMedia
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.HistoryCounts
import eu.tintera.plume.core.telegram.SenderInfo
import eu.tintera.plume.core.telegram.SharedMediaKind
import eu.tintera.plume.core.telegram.mergeDayCounts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

/** Co kalendář počítá. */
@Immutable
sealed interface CalendarFilter {
    /** Všechny zprávy — z lokální databáze ([AllMessagesCalendar]). */
    data object All : CalendarFilter

    /** Fotky, soubory, odkazy… — po dnech ze serveru (`getChatMessageCalendar`). */
    data class Kind(val kind: SharedMediaKind) : CalendarFilter

    /**
     * Výsledky hledání v chatu: výraz, zprávy jednoho člena skupiny, nebo obojí naráz
     * (`searchChatMessages` umí výraz i odesílatele). Ze serveru, takže přesně za celou historii.
     */
    data class Search(val query: String = "", val sender: SenderInfo? = null) : CalendarFilter {
        /** Bez výrazu i člena není co hledat — kalendář jen vyzve k psaní. */
        val isEmpty: Boolean get() = query.isBlank() && sender == null
    }
}

/** Den spočtený serverem: kolik zpráv a první z nich (kam vede ťuknutí, náhled fotky). */
@Immutable
data class ServerDay(val count: Int, val firstMessageId: Long, val media: CalendarDayMedia? = null)

/** Dny ze serveru po stránkách, od nejnovějšího; starší se dotahují při dojetí k nim. */
@Immutable
data class ServerDays(
    val days: Map<LocalDate, ServerDay> = emptyMap(),
    val loading: Boolean = true,
    val reachedEnd: Boolean = false,
    val next: Long = 0,
)

/**
 * Den, který přišel na dvou stránkách (poslední den stránky bývá neúplný), se sečte; první
 * zprávou zůstane ta starší.
 */
internal fun mergeServerDays(loaded: Map<LocalDate, ServerDay>, page: List<Pair<LocalDate, ServerDay>>): Map<LocalDate, ServerDay> {
    val merged = LinkedHashMap(loaded)
    for ((date, day) in page) {
        val known = merged[date]
        merged[date] = if (known == null) {
            day
        } else {
            val older = if (day.firstMessageId < known.firstMessageId) day else known
            older.copy(count = known.count + day.count)
        }
    }
    return merged
}

/**
 * Kalendář konverzace: kolik zpráv přišlo který den, nebo kolik fotek, souborů, odkazů,
 * nalezených zpráv… podle [filter].
 */
@Immutable
data class ChatCalendarState(
    val filter: CalendarFilter = CalendarFilter.All,
    val all: AllMessagesCalendar = AllMessagesCalendar(),
    /** Dny druhu nebo hledání ze serveru; `null` u všech zpráv a u prázdného hledání. */
    val server: ServerDays? = null,
    /** Výběr člena pro hledání podle odesílatele; `null` = zavřený. */
    val memberPicker: MemberPicker? = null,
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
    private var serverJob: Job? = null
    private var memberJob: Job? = null

    /**
     * Zprávy, na kterých konverzace stála, když se kalendář otevíral — vstupy do ostrůvků. Drží
     * se po celou dobu obrazovky: po dalším skoku jinam je okno v konverzaci jiné, ale to staré
     * v databázi zůstalo.
     */
    private val anchors = mutableSetOf<Long>()

    /** První zpráva chatu (ze serveru, kvůli ose); úsek, který na ni dosáhne, je u začátku. */
    private var firstMessageId: Long? = null

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
            // Při otevření se kalendář plní průběžně, po úsecích.
            val publish = { all: AllMessagesCalendar ->
                updateAll { all.copy(firstMonth = it.firstMonth, reachedStart = all.reachedStart || it.reachedStart) }
            }
            val local = countLocal(AllMessagesCalendar(), publish)
            if (local == null) {
                updateAll { it.copy(counting = false, failed = true) }
                return@launch
            }
            publish(local)
            countIslands(local, publish)
        }
        firstMonthJob?.cancel()
        firstMonthJob = scope.launch { findFirstMonth() }
    }

    /**
     * Přepočítá všechny zprávy znovu, protože konverzace vedle načetla další okno historie
     * ([LoadedHistory]); [anchor] je jeho nejnovější zpráva — vstup do ostrůvku, který s ním
     * mohl vzniknout. Počítá se BOKEM a výsledek se vymění naráz: kalendář se nevrátí do stavu
     * „počítám" a neproblikne, jen přibydou dny. Běží-li zrovna první počítání nebo „Načíst
     * starší", nic se nedělá — kotva zůstane zapsaná a další okno ji započítá.
     */
    fun refresh(anchor: Long?) {
        val current = _state.value?.all ?: return
        anchor?.let { anchors += it }
        if (current.counting || current.loadingOlder) return
        allJob?.cancel()
        allJob = scope.launch {
            val local = countLocal(AllMessagesCalendar(), publish = {}) ?: return@launch
            val all = countIslands(local, publish = {})
            // Začátek chatu zůstává začátkem: databáze o zprávy nepřichází, úsek může jen růst.
            updateAll { all.copy(counting = false, firstMonth = it.firstMonth, reachedStart = all.reachedStart || it.reachedStart) }
        }
    }

    fun close() {
        allJob?.cancel()
        firstMonthJob?.cancel()
        serverJob?.cancel()
        memberJob?.cancel()
        _state.value = null
    }

    /**
     * Přepne, co kalendář počítá. Druh a hledání se načítají pokaždé od nejnovějšího dne;
     * přepnutí na hledání si nechá výraz i člena, které už byly zadané.
     */
    fun setFilter(filter: CalendarFilter) {
        val current = _state.value ?: return
        // Hledání zůstává hledáním: znovu ťuknutý čip nesmaže výraz ani člena.
        if (current.filter == filter || (filter is CalendarFilter.Search && current.filter is CalendarFilter.Search)) return
        loadServer(current.copy(filter = filter))
    }

    /** Hledaný výraz; hledá se po krátké pauze v psaní, jako hledání v konverzaci. */
    fun setSearchQuery(text: String) {
        val current = _state.value ?: return
        val search = current.filter as? CalendarFilter.Search ?: return
        if (search.query == text) return
        _state.value = current.copy(filter = search.copy(query = text))
        serverJob?.cancel()
        serverJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _state.value?.let { loadServer(it) }
        }
    }

    /** Člen skupiny pro hledání podle odesílatele; `null` = filtr pryč. Hledá se hned. */
    fun setSearchSender(sender: SenderInfo?) {
        val current = _state.value ?: return
        val search = current.filter as? CalendarFilter.Search ?: CalendarFilter.Search()
        memberJob?.cancel()
        loadServer(current.copy(filter = search.copy(sender = sender), memberPicker = null))
    }

    fun openMemberPicker() {
        _state.update { it?.copy(memberPicker = MemberPicker()) }
        onMemberQuery("")
    }

    fun closeMemberPicker() {
        memberJob?.cancel()
        _state.update { it?.copy(memberPicker = null) }
    }

    /** Člen podle jména, s pauzou v psaní; prázdné = první členové skupiny. */
    fun onMemberQuery(text: String) {
        _state.update { current -> current?.memberPicker?.let { current.copy(memberPicker = it.copy(query = text, loading = true)) } ?: current }
        memberJob?.cancel()
        memberJob = scope.launch {
            if (text.isNotEmpty()) delay(SEARCH_DEBOUNCE_MS)
            val members = repository.searchMembers(chatId, text.trim()).getOrDefault(emptyList())
            _state.update { current -> current?.memberPicker?.let { current.copy(memberPicker = it.copy(members = members, loading = false)) } ?: current }
        }
    }

    /** Další stránka dnů druhu nebo hledání, když se k nejstaršímu načtenému měsíci dojede. */
    fun loadMoreServer() {
        val current = _state.value ?: return
        val server = current.server ?: return
        if (server.loading || server.reachedEnd) return
        _state.value = current.copy(server = server.copy(loading = true))
        loadServerPage(current.filter, server.next)
    }

    /** Stav s [state] a první stránkou jeho filtru (u všech zpráv a prázdného hledání žádnou). */
    private fun loadServer(state: ChatCalendarState) {
        serverJob?.cancel()
        val filter = state.filter
        val needsServer = filter is CalendarFilter.Kind || (filter is CalendarFilter.Search && !filter.isEmpty)
        _state.value = state.copy(server = if (needsServer) ServerDays() else null)
        if (needsServer) loadServerPage(filter, from = 0)
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

    /**
     * Lokální databáze po úsecích od nejnovější zprávy; [publish] dostává mezivýsledky (při
     * otevření, ať se kalendář plní hned; při přepočtu nic). `null` = databáze neodpověděla.
     */
    private suspend fun countLocal(start: AllMessagesCalendar, publish: (AllMessagesCalendar) -> Unit): AllMessagesCalendar? {
        var all = start
        var from = 0L
        var counted = 0
        while (true) {
            val counts = repository.messageCountsByDay(chatId, from, onlyLocal = true, maxMessages = LOCAL_CHUNK, zone = zone)
                .getOrElse { return null }
            all = all.withCounts(counts)
            publish(all)
            counted += counts.days.values.sum()
            val next = counts.oldestMessageId
            if (counts.reachedEnd || next == null || counted >= LOCAL_CAP) break
            from = next
        }
        return all.copy(counting = false)
    }

    /**
     * Ostrůvky od kotev starších než souvislý úsek. Kotva uvnitř už spočteného ostrůvku se
     * přeskočí — dvě kotvy v témž okně by ho jinak sečetly dvakrát.
     */
    private suspend fun countIslands(start: AllMessagesCalendar, publish: (AllMessagesCalendar) -> Unit): AllMessagesCalendar {
        var all = start
        for (anchor in anchors.sortedDescending()) {
            val coveredOldest = all.oldestMessageId ?: return all
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
            all = all.copy(islands = all.islands + island)
            publish(all)
        }
        return all
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
        firstMessageId = first
        val timestamp = repository.message(chatId, first).getOrNull()?.timestamp ?: return
        val month = Instant.fromEpochSeconds(timestamp.toLong()).toLocalDateTime(zone).date.yearMonth
        updateAll { it.copy(firstMonth = month, reachedStart = it.reachedStart || it.oldestMessageId == first) }
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
            // Úsek dosáhl první zprávy chatu: dál do minulosti nic není, i když to řekla lokální
            // databáze, ne server — nejstarší den je celý a „Načíst starší" nemá co nabízet.
            reachedStart = reachedStart || oldestId == firstMessageId,
        )
    }

    private fun updateAll(change: (AllMessagesCalendar) -> AllMessagesCalendar) {
        _state.update { it?.copy(all = change(it.all)) }
    }

    private fun loadServerPage(filter: CalendarFilter, from: Long) {
        serverJob = scope.launch {
            val page: Result<Triple<List<Pair<LocalDate, ServerDay>>, Long, Boolean>> = when (filter) {
                is CalendarFilter.Kind -> repository.mediaCalendar(chatId, from, filter.kind).map { page ->
                    val days = page.days.map { day ->
                        val date = localDate(day.message.timestamp)
                        date to ServerDay(day.count, day.message.id, CalendarDayMedia(date, day.count, day.message))
                    }
                    Triple(days, page.nextFromMessageId, page.reachedEnd || page.nextFromMessageId == from)
                }
                is CalendarFilter.Search -> repository.searchInChat(chatId, filter.query.trim(), from, filter.sender?.id).map { page ->
                    // Výsledek je zpráva, ne den: po dnech se sečtou tady. První zprávou dne je
                    // nejstarší výsledek, ať ťuknutí vede na začátek dne, jako u druhů.
                    val days = page.hits.groupBy { localDate(it.date) }.map { (date, hits) ->
                        date to ServerDay(hits.size, hits.minOf { it.messageId })
                    }
                    Triple(days, page.nextFromMessageId ?: 0, page.nextFromMessageId == null)
                }
                CalendarFilter.All -> return@launch
            }
            _state.update { state ->
                val server = state?.server?.takeIf { state.filter == filter } ?: return@update state
                state.copy(
                    server = page.fold(
                        onSuccess = { (days, next, end) ->
                            server.copy(days = mergeServerDays(server.days, days), loading = false, reachedEnd = end, next = next)
                        },
                        // Stránka, která nepřišla, ukončí dotahování — jinak by se zkoušela dokola,
                        // dokud se k ní dojíždí.
                        onFailure = { server.copy(loading = false, reachedEnd = true) },
                    ),
                )
            }
        }
    }

    private fun localDate(timestamp: Int): LocalDate = Instant.fromEpochSeconds(timestamp.toLong()).toLocalDateTime(zone).date
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
