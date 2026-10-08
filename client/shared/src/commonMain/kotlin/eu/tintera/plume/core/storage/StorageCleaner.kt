package eu.tintera.plume.core.storage

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.connectivity.AppForeground
import eu.tintera.plume.core.settings.AppSettings
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
 * Úklid starých médií podle volby „Uchovávat média" ([AppSettings.keepMedia]).
 *
 * TDLib by uměl uklízet sám (volba `use_storage_optimizer`), jenže meze bere z dalších voleb
 * (`storage_max_files_size`…), jejichž výchozí hodnoty nikde popsané nejsou — a úklid podle
 * stáří by pak mohl mazat i podle velikosti. Proto se `optimizeStorage` volá odsud, s mezemi
 * vypsanými výslovně ([StorageRepository.clearOlderThan]).
 *
 * Běží nejvýš jednou denně ([cleanupDue]): při návratu do popředí s přihlášeným účtem. Startuje
 * se ze `startKoin` jako `ConnectionController` — aplikace může běžet dny bez nové obrazovky.
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

    /** Úklid a změna volby se nesmí potkat — dva souběžné `optimizeStorage` nedávají smysl. */
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
            if (cleanupDue(keep, settings.lastStorageCleanup.value, now())) clean(keep)
        }
    }

    /**
     * Uklidí hned, bez ohledu na čas posledního úklidu — po změně volby, aby se projevila,
     * dokud se na ni uživatel dívá. Vrací uvolněné bajty; u [KeepMedia.Forever] se nemaže nic.
     */
    suspend fun runNow(keep: KeepMedia): Result<Long> = mutex.withLock { clean(keep) }

    private suspend fun clean(keep: KeepMedia): Result<Long> {
        val seconds = keep.seconds ?: return Result.success(0)
        return repository.clearOlderThan(seconds).onSuccess { freed ->
            settings.setLastStorageCleanup(now())
            logger.i { "Úklid podle volby $keep uvolnil $freed B" }
        }
    }
}

/** Den mezi dvěma automatickými úklidy. Víc nemá smysl: doby uchování jsou ve dnech. */
const val CLEANUP_INTERVAL_SECONDS: Long = 24 * 60 * 60

/**
 * Je čas na automatický úklid? Jen s jinou volbou než [KeepMedia.Forever] a nejvýš jednou za
 * [CLEANUP_INTERVAL_SECONDS]. Čas v budoucnosti (přenastavené hodiny) se bere jako „dávno" —
 * jinak by se po posunu hodin zpátky neuklízelo dny.
 */
fun cleanupDue(keep: KeepMedia, lastRunAt: Long?, now: Long): Boolean {
    if (keep.seconds == null) return false
    if (lastRunAt == null || lastRunAt > now) return true
    return now - lastRunAt >= CLEANUP_INTERVAL_SECONDS
}
