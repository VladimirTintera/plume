package eu.tintera.plume.core.storage

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.connectivity.AppForeground
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.settings.CacheLimit
import eu.tintera.plume.core.settings.KeepMedia
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.StorageRepository
import eu.tintera.plume.core.telegram.TelegramSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * Úklid stažených médií podle voleb „Uchovávat média" ([AppSettings.keepMedia]) a „Strop
 * mezipaměti" ([AppSettings.cacheLimit]) — obojí jedním voláním `optimizeStorage`.
 *
 * TDLib by uměl uklízet sám (volba `use_storage_optimizer`), jenže meze bere z dalších voleb
 * (`storage_max_files_size`…) s výchozími hodnotami, které nikdo nevolil (100 MB) — úklid podle
 * stáří by pak mazal i podle velikosti. Proto se `optimizeStorage` volá odsud, s mezemi
 * vypsanými výslovně ([StorageRepository.cleanUp]).
 *
 * Běží při návratu do popředí s přihlášeným účtem, když je čas ([cleanupDue]). Startuje se ze
 * `startKoin` jako `ConnectionController` — aplikace může běžet dny bez nové obrazovky.
 */
class StorageCleaner(
    private val repository: StorageRepository,
    private val settings: AppSettings,
    private val session: TelegramSession,
    private val foreground: AppForeground,
    private val scope: CoroutineScope,
    private val now: () -> Long = { Clock.System.now().epochSeconds },
) {

    private val logger = Logger.withTag("Storage")

    /** Úklid a změna volby se nesmí potkat — souběžné `optimizeStorage` TDLib ruší (`StorageManager`). */
    private val mutex = Mutex()

    fun start() {
        scope.launch {
            combine(session.authStage, foreground.isForeground) { stage, inForeground ->
                stage == AuthStage.Ready && inForeground
            }
                .distinctUntilChanged()
                .filter { it }
                .collect { runIfDue() }
        }
    }

    /**
     * Uklidí, jestli je čas. Selhání se jen zaloguje (to dělá repository) a čas úklidu se
     * nezapíše, takže se to příště zkusí znovu.
     */
    suspend fun runIfDue() {
        mutex.withLock {
            val keep = settings.keepMedia.value
            val limit = settings.cacheLimit.value
            if (cleanupDue(keep, limit, settings.lastStorageCleanup.value, now())) clean(keep, limit)
        }
    }

    /**
     * Uklidí hned, bez ohledu na čas posledního úklidu — po změně volby, aby se projevila,
     * dokud se na ni uživatel dívá. Hodnoty se předávají, protože uložení do nastavení je
     * asynchronní. Vrací uvolněné bajty; bez obou mezí se nemaže nic.
     */
    suspend fun runNow(keep: KeepMedia, limit: CacheLimit): Result<Long> = mutex.withLock { clean(keep, limit) }

    private suspend fun clean(keep: KeepMedia, limit: CacheLimit): Result<Long> {
        if (keep.seconds == null && limit.bytes == null) return Result.success(0)
        return repository.cleanUp(olderThanSeconds = keep.seconds, maxBytes = limit.bytes).onSuccess { freed ->
            settings.setLastStorageCleanup(now())
            logger.i { "Úklid podle voleb $keep / $limit uvolnil $freed B" }
        }
    }
}

/** Den mezi dvěma automatickými úklidy podle stáří. Víc nemá smysl: doby uchování jsou ve dnech. */
const val CLEANUP_INTERVAL_SECONDS: Long = 24 * 60 * 60

/**
 * Se stropem se uklízí častěji — za den se ho dá přerůst (videa) a čekat do zítřka by z něj
 * udělalo doporučení. Ne při každém návratu do popředí: úklid prochází všechny soubory
 * mezipaměti (na emulátoru vteřiny) a souběžné čtení přehledu Úložiště ho přeruší.
 */
const val LIMIT_CLEANUP_INTERVAL_SECONDS: Long = 6 * 60 * 60

/**
 * Je čas na automatický úklid? Jen s nějakou mezí ([KeepMedia.Forever] a [CacheLimit.None]
 * neuklízí nic), podle stáří nejvýš jednou za [CLEANUP_INTERVAL_SECONDS], se stropem jednou za
 * [LIMIT_CLEANUP_INTERVAL_SECONDS]. Čas v budoucnosti (přenastavené hodiny) se bere jako
 * „dávno" — jinak by se po posunu hodin zpátky neuklízelo dny.
 */
fun cleanupDue(keep: KeepMedia, limit: CacheLimit = CacheLimit.None, lastRunAt: Long?, now: Long): Boolean {
    val interval = when {
        limit.bytes != null -> LIMIT_CLEANUP_INTERVAL_SECONDS
        keep.seconds != null -> CLEANUP_INTERVAL_SECONDS
        else -> return false
    }
    if (lastRunAt == null || lastRunAt > now) return true
    return now - lastRunAt >= interval
}
