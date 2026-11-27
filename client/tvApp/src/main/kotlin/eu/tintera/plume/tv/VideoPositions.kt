package eu.tintera.plume.tv

import android.content.Context
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.settings.sync.CloudRemoteGroup
import eu.tintera.plume.core.settings.sync.CloudStore
import eu.tintera.plume.core.settings.sync.CloudSyncBase
import eu.tintera.plume.core.settings.sync.CloudSyncGroup
import eu.tintera.plume.core.settings.sync.SyncEntries
import eu.tintera.plume.core.settings.sync.decodeBase
import eu.tintera.plume.core.settings.sync.encodeBase
import eu.tintera.plume.core.settings.sync.merge3
import eu.tintera.plume.core.settings.sync.positionEntries
import eu.tintera.plume.core.settings.sync.positionsWith
import eu.tintera.plume.core.settings.sync.readGroups
import eu.tintera.plume.core.settings.sync.resolvePosition
import eu.tintera.plume.core.settings.sync.writeGroup
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.video.VideoPosition
import eu.tintera.plume.core.video.VideoSource
import eu.tintera.plume.core.video.withPosition
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Kde se na televizi přestalo sledovat video — tentýž seznam a tatáž pravidla jako na telefonu
 * (`VideoPosition`, `withPosition` z `core`: jen videa od 2 min, ne začátek ani konec, nejvýš 200).
 * Klíč je soubor na serveru, takže pozice z telefonu ([TvPositionSync]) sedí na totéž video tady.
 *
 * **Uložené v `SharedPreferences`**, ne v DataStore: televize stojí jen na `core` a jeden seznam
 * do sto kB DataStore nepotřebuje. Zápis jde přes [update], ať se dva zápisy (přehrávač
 * a synchronizace) nepřepíšou.
 */
class TvVideoPositions(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    val all: StateFlow<List<VideoPosition>> = state.asStateFlow()

    fun of(key: String): VideoPosition? = state.value.firstOrNull { it.key == key }

    /** Zapíše pozici, nebo ji zapomene (dohráno, začátek, krátké video) — viz `resumePosition`. */
    fun record(key: String, positionMs: Long, durationMs: Long, source: VideoSource?) {
        val now = Clock.System.now().toEpochMilliseconds()
        update { it.withPosition(key, positionMs, durationMs, source = source, at = now) }
    }

    fun remove(key: String) = update { list -> list.filterNot { it.key == key } }

    @Synchronized
    fun update(transform: (List<VideoPosition>) -> List<VideoPosition>) {
        val updated = transform(state.value)
        if (updated == state.value) return
        state.value = updated
        prefs.edit().putString(KEY_POSITIONS, json.encodeToString(LIST, updated)).apply()
    }

    /** Základ trojcestného sloučení z poslední synchronizace (`CloudSyncBase` jako text). */
    var syncBase: String?
        get() = prefs.getString(KEY_BASE, null)
        set(value) {
            prefs.edit().putString(KEY_BASE, value).apply()
        }

    private fun read(): List<VideoPosition> = prefs.getString(KEY_POSITIONS, null)
        ?.let { runCatching { json.decodeFromString(LIST, it) }.getOrNull() }
        .orEmpty()

    private companion object {
        const val PREFS = "video_positions"
        const val KEY_POSITIONS = "positions"
        const val KEY_BASE = "sync_base"
        val LIST = ListSerializer(VideoPosition.serializer())
        val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * Rozkoukaná videa mezi televizí a telefonem (i desktopem) přes úložiště bota — tatáž skupina
 * `plume_positions`, týž formát a totéž trojcestné sloučení jako `CloudSync` ve `shared`
 * (`CloudGroups.kt` v `core`). Video rozkoukané na telefonu tak na televizi pokračuje, a naopak.
 *
 * Jen tahle skupina: ostatní (vzhled, záložky, barvy chatů) televize nemá a nečte ani nepřepisuje
 * — každá skupina má v úložišti vlastní klíče. Kdy se synchronizuje: po `Ready`, po návratu
 * do popředí (televize nechává aplikaci v pozadí celé dny), po místní změně s prodlevou a každých
 * 15 minut.
 */
@OptIn(FlowPreview::class)
class TvPositionSync(
    private val positions: TvVideoPositions,
    private val store: CloudStore,
    /** Id uživatele účtu (`ChatRepository.savedMessagesChatId`); `null`, dokud není znám. */
    private val accountId: StateFlow<Long?>,
    private val authStage: StateFlow<AuthStage>,
    /** `AppForeground.isForeground`; `null` = bez synchronizace po návratu do popředí. */
    private val foreground: Flow<Boolean>?,
    private val scope: CoroutineScope,
) {
    private val logger = Logger.withTag("TvPositionSync")
    private val mutex = Mutex()

    /** Položky po poslední synchronizaci; vlastní zápis sloučení další synchronizaci nespouští. */
    private var synced: SyncEntries? = null

    fun start() {
        scope.launch {
            authStage.collectLatest { stage -> if (stage == AuthStage.Ready) watch() }
        }
    }

    private suspend fun watch() = coroutineScope {
        val account = accountId.filterNotNull().first()
        sync(account)
        launch {
            while (true) {
                delay(PULL_PERIOD_MS)
                sync(account)
            }
        }
        foreground?.let { flow ->
            launch { flow.drop(1).filter { it }.collect { sync(account) } }
        }
        positions.all.map { positionEntries(it, account) }
            .distinctUntilChanged()
            .filter { it != synced }
            .debounce(CHANGE_DEBOUNCE_MS)
            .collect { sync(account) }
    }

    private suspend fun sync(account: Long) = mutex.withLock {
        runCatching { syncOnce(account) }.onFailure { logger.w(it) { "Synchronizace rozkoukaných videí selhala." } }
    }

    private suspend fun syncOnce(account: Long) {
        val group = CloudSyncGroup.Positions
        val remote = store.readGroups(listOf(group)).getOrElse { return }[group]
        if (remote is CloudRemoteGroup.Unsupported) return
        val present = remote as? CloudRemoteGroup.Present
        val theirs = present?.entries.orEmpty()
        val base = decodeBase(positions.syncBase)?.takeIf { it.accountId == account } ?: CloudSyncBase(account)
        val local = positionEntries(positions.all.value, account)
        val merged = merge3(base.groups[group].orEmpty(), local, theirs, ::resolvePosition)

        if (merged != local) positions.update { current -> positionsWith(merged, current, account, snapshot = local) }
        if (merged != theirs || (present == null && merged.isNotEmpty())) {
            store.writeGroup(group, merged, previousParts = present?.parts ?: 0)
        }
        positions.syncBase = encodeBase(CloudSyncBase(account, base.groups + (group to merged)))
        synced = merged
        logger.i { "Synchronizováno: ${merged.size} rozkoukaných videí." }
    }

    private companion object {
        const val CHANGE_DEBOUNCE_MS = 15_000L
        const val PULL_PERIOD_MS = 15 * 60_000L
    }
}
