package eu.tintera.plume.core.ai

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.features.media.BackgroundPhotoDescriber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

/**
 * Popisy fotek na pozadí ([BackgroundPhotoDescriber]) jako periodická úloha WorkManageru:
 * jen na nabíječce a s baterií, která není slabá. Gemma běží v procesu aplikace (LiteRT-LM),
 * takže jí pozadí nevadí; AICore (Gemini Nano) by inferenci na pozadí nepustil, a tu stejně
 * popisovač nepoužije.
 */
class PhotoDescriptionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val describer = GlobalContext.getOrNull()?.getOrNull<BackgroundPhotoDescriber>() ?: return Result.success()
        describer.describeBatch()
        return Result.success()
    }
}

/**
 * Zařadí úlohu, když je volba zapnutá, a zruší ji, když se vypne. Sleduje volbu od startu
 * procesu (`createdAtStart`), takže úloha přežije i restart a nikdo nemusí otevřít Nastavení.
 */
class PhotoDescriptionSchedule(
    private val context: Context,
    settings: AppSettings,
    scope: CoroutineScope,
) {
    init {
        scope.launch {
            // Výchozí `false` přijde dřív, než se nastavení načte; rušit se proto smí jen po
            // skutečném vypnutí, jinak by každý start procesu úlohu zrušil a plán posunul.
            var wasEnabled = false
            settings.backgroundPhotoDescriptions.collect { enabled ->
                val work = WorkManager.getInstance(context)
                val turnedOff = wasEnabled && !enabled
                wasEnabled = enabled
                if (enabled) {
                    val request = PeriodicWorkRequestBuilder<PhotoDescriptionWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiresCharging(true)
                                .setRequiresBatteryNotLow(true)
                                .build(),
                        )
                        .build()
                    // KEEP: zapnutí po restartu nemá posouvat čas, kdy úloha přijde na řadu.
                    work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
                } else if (turnedOff) {
                    work.cancelUniqueWork(WORK_NAME)
                }
            }
        }
    }

    private companion object {
        const val WORK_NAME = "photo-descriptions"

        /** Jak často se dávka zkusí; na nabíječce stačí párkrát denně. */
        const val INTERVAL_HOURS = 6L
    }
}
