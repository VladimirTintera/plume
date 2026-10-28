package eu.tintera.plume.features.conversation

import androidx.compose.runtime.Immutable
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Všechny zprávy po dnech — jen to, co je spočtené: od nejnovější zprávy po [coveredFrom].
 * Starší dny kalendář neukazuje jako prázdné, ale jako neznámé.
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
    private var mediaJob: Job? = null

    /**
     * Otevře kalendář a projde lokální databázi znovu: zprávy staré z minula tam jsou dál a k nim
     * nové i ty, které mezitím přinesl server — počítat od nuly je jednodušší než slévat.
     */
    fun open() {
        _state.value = ChatCalendarState()
        allJob?.cancel()
        allJob = scope.launch { countLocal() }
    }

    fun close() {
        allJob?.cancel()
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

    private fun AllMessagesCalendar.withCounts(counts: HistoryCounts): AllMessagesCalendar {
        val oldest = counts.oldestTimestamp ?: return this
        return copy(
            days = mergeDayCounts(days, counts.days),
            coveredFrom = Instant.fromEpochSeconds(oldest.toLong()).toLocalDateTime(zone).date,
            oldestMessageId = counts.oldestMessageId,
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

/** Kolik zpráv z lokální databáze jeden úsek; mezi úseky se kalendář překreslí. */
private const val LOCAL_CHUNK = 2_000

/**
 * Strop lokálního průchodu. Databáze rušné skupiny může mít statisíce zpráv; co je za stropem,
 * dočte „Načíst starší" (TDLib je vrátí z databáze, ne ze serveru).
 */
private const val LOCAL_CAP = 30_000

/** „Načíst starší" stáhne deset stránek historie — deset dotazů na server. */
private const val OLDER_CHUNK = 1_000
