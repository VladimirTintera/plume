package eu.tintera.plume.wear

import android.content.Context
import androidx.core.content.edit
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.DEFAULT_CLEAR_CATEGORIES
import eu.tintera.plume.core.telegram.StorageRepository
import eu.tintera.plume.core.telegram.TelegramSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val logger = Logger.withTag("WearStorage")

/**
 * Úklid stažených médií na hodinkách. Fotky a hlasovky se stahují do mezipaměti TDLib a hodinky
 * mají místa málo; soubory zůstávají na serveru a stáhnou se znovu, až je někdo otevře.
 *
 * - **Pevné meze, ne volby** jako na telefonu (`StorageCleaner` ve `shared`): média starší
 *   [KEEP_SECONDS] a pak nejdéle nepoužité nad [MAX_BYTES]. Hodinky nastavení úložiště nemají
 *   a nikdo by ho tam nehledal.
 * - **`optimizeStorage` přes [StorageRepository.cleanUp], ne `use_storage_optimizer`** — ten bere
 *   meze z dalších voleb TDLib s výchozími hodnotami, které nikdo nevolil (zdůvodnění u telefonu).
 *   Samolepky, avatary a náhledy úklid nechá být, soubor mladší než hodina taky.
 * - **Nejvýš jednou za [INTERVAL_SECONDS]**, po přihlášení (start procesu). Proces na hodinkách
 *   startuje často — push, dlaždice — a procházení souborů by bylo zbytečné.
 * - Úklid a ruční vyčištění ([clearNow]) se nesmí potkat: souběžné `optimizeStorage` TDLib ruší.
 */
class WearStorageCleaner(
    private val context: Context,
    private val repository: StorageRepository,
    private val scope: CoroutineScope,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
) {

    private val mutex = Mutex()

    fun start(session: TelegramSession) {
        scope.launch {
            // StateFlow samo nevydá tutéž hodnotu dvakrát: jednou za přihlášení.
            session.authStage
                .filter { it == AuthStage.Ready }
                .collect { runIfDue() }
        }
    }

    private suspend fun runIfDue() = mutex.withLock {
        val last = prefs().getLong(KEY_LAST, 0L)
        if (now() - last < INTERVAL_SECONDS) return@withLock
        repository.cleanUp(olderThanSeconds = KEEP_SECONDS, maxBytes = MAX_BYTES).onSuccess { freed ->
            prefs().edit { putLong(KEY_LAST, now()) }
            logger.i { "Úklid úložiště hodinek uvolnil $freed B" }
        }
    }

    /**
     * Vyčistí stažená média hned (tlačítko v Nastavení) — stejný výběr jako výchozí v dialogu
     * telefonu: média ano, samolepky a avatary ne. Vrací uvolněné bajty.
     */
    suspend fun clearNow(): Result<Long> = mutex.withLock { repository.clear(DEFAULT_CLEAR_CATEGORIES) }

    private fun prefs() = context.getSharedPreferences("wear_storage", Context.MODE_PRIVATE)

    private companion object {
        const val KEY_LAST = "last_cleanup"
        const val KEEP_SECONDS = 3 * 24 * 3600
        const val MAX_BYTES = 100L * 1024 * 1024
        const val INTERVAL_SECONDS = 24 * 3600L
    }
}
