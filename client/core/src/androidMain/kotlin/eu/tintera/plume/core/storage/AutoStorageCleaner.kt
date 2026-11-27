package eu.tintera.plume.core.storage

import android.content.Context
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.DEFAULT_CLEAR_CATEGORIES
import eu.tintera.plume.core.telegram.StorageRepository
import eu.tintera.plume.core.telegram.TelegramSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Automatický úklid stažených médií pro zařízení bez nastavení úložiště — hodinky a televizi.
 * Fotky, videa a hlasovky se stahují do mezipaměti TDLib; soubory zůstávají na serveru a stáhnou
 * se znovu, až je někdo otevře.
 *
 * - **Pevné meze, ne volby** jako na telefonu (`StorageCleaner` ve `shared`): média, na která se
 *   nesáhlo [keepSeconds], a pak nejdéle nepoužitá nad [maxBytes]. Každá aplikace si je dává
 *   sama (hodinky mají místa nejmíň, televize stahuje videa).
 * - **`optimizeStorage` přes [StorageRepository.cleanUp], ne `use_storage_optimizer`** — ten bere
 *   meze z dalších voleb TDLib s výchozími hodnotami, které nikdo nevolil (zdůvodnění u telefonu).
 *   Samolepky, avatary a náhledy úklid nechá být, soubor mladší než hodina taky — tedy i video,
 *   které se právě přehrává.
 * - **Nejvýš jednou za [intervalSeconds]**, kontroluje se po přihlášení a pak každou hodinu,
 *   dokud proces běží: televize nechává aplikaci v paměti celé dny a jen po startu by úklid
 *   neproběhl nikdy. Kontrola je jen čtení času z `SharedPreferences`.
 * - Úklid a ruční vyčištění ([clearNow]) se nesmí potkat: souběžné `optimizeStorage` TDLib ruší.
 *   Proto má být čistič v procesu jeden.
 */
class AutoStorageCleaner(
    private val context: Context,
    private val repository: StorageRepository,
    private val scope: CoroutineScope,
    /** Soubor `SharedPreferences` s časem posledního úklidu (hodinky `wear_storage`). */
    private val prefsName: String,
    private val keepSeconds: Int,
    private val maxBytes: Long,
    private val intervalSeconds: Long = DAY_SECONDS,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val logger = Logger.withTag("AutoStorage")
    private val mutex = Mutex()

    fun start(session: TelegramSession) {
        scope.launch {
            session.authStage.collectLatest { stage ->
                if (stage != AuthStage.Ready) return@collectLatest
                while (true) {
                    runIfDue()
                    delay(CHECK_PERIOD_MS)
                }
            }
        }
    }

    private suspend fun runIfDue() = mutex.withLock {
        val last = prefs().getLong(KEY_LAST, 0L)
        if (now() - last < intervalSeconds) return@withLock
        repository.cleanUp(olderThanSeconds = keepSeconds, maxBytes = maxBytes).onSuccess { freed ->
            prefs().edit().putLong(KEY_LAST, now()).apply()
            logger.i { "Úklid úložiště ($prefsName) uvolnil $freed B" }
        }
    }

    /**
     * Vyčistí stažená média hned (tlačítko v Nastavení hodinek) — stejný výběr jako výchozí
     * v dialogu telefonu: média ano, samolepky a avatary ne. Vrací uvolněné bajty.
     */
    suspend fun clearNow(): Result<Long> = mutex.withLock { repository.clear(DEFAULT_CLEAR_CATEGORIES) }

    private fun prefs() = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private companion object {
        const val KEY_LAST = "last_cleanup"
        const val DAY_SECONDS = 24 * 3600L
        const val CHECK_PERIOD_MS = 3600_000L
    }
}
