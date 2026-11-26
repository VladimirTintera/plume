package eu.tintera.plume.core.files

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import co.touchlab.kermit.Logger
import eu.tintera.plume.client.R
import eu.tintera.plume.core.notifications.UPLOADS_CHANNEL_ID
import eu.tintera.plume.core.notifications.createUploadsChannel
import eu.tintera.plume.core.telegram.UploadActivity
import eu.tintera.plume.core.ui.fileSizeString
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.upload_notification_progress
import eu.tintera.plume.resources.upload_notification_title
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.koin.core.context.GlobalContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Drží proces naživu, dokud se nahrávají soubory ([ActiveUploads]) — úloha WorkManageru se
 * službou v popředí typu `dataSync`. Nahrává dál TDLib; úloha jen brání systému, aby proces
 * na pozadí zmrazil nebo zabil (`docs/poznatky/konverzace-psani.md`, „Nahrávání na pozadí").
 *
 * **Jméno třídy se nesmí měnit** (ani balíček): WorkManager si ho pamatuje v naplánované úloze.
 */
class UploadKeepAliveWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val logger = Logger.withTag("Uploads")

    /** Úlohu se podařilo povýšit na službu v popředí; bez ní notifikace nemá smysl. */
    private var foreground = true

    override suspend fun doWork(): Result {
        val activity = GlobalContext.getOrNull()?.getOrNull<ActiveUploads>()?.activity ?: return Result.success()
        createUploadsChannel(applicationContext)

        // Do Androidu 11 jde služba v popředí spustit i z pozadí, takže se s ní dá chvíli počkat:
        // fotka se nahraje za vteřinu a notifikace by jen problikla. Od 12 se musí spustit hned,
        // dokud je aplikace vidět — notifikaci tam systém sám odloží o deset vteřin.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            withTimeoutOrNull(LEGACY_FOREGROUND_DELAY) { activity.first { it.isIdle } } != null
        ) {
            return Result.success()
        }

        var current = activity.value
        // Po zabití procesu ji WorkManager spustí znovu, a TDLib rozeslané zprávy obnoví až po
        // startu klienta; na první nahrávání se proto čeká déle než na další.
        var grace = START_GRACE
        while (true) {
            if (current.isIdle) {
                // Soubory alba a zprávy odeslané rychle za sebou nenavazují: mezi koncem jednoho
                // a začátkem dalšího bývá chvíle bez nahrávání.
                current = withTimeoutOrNull(grace) { activity.first { !it.isIdle } } ?: return Result.success()
                continue
            }
            grace = IDLE_GRACE
            show(current)
            // Notifikace se nemění častěji než jednou za vteřinu, ať ji systém nezačne zahazovat.
            delay(NOTIFICATION_INTERVAL)
            val shown = current
            current = withTimeoutOrNull(STALL_TIMEOUT) { activity.first { it != shown } } ?: run {
                // Bez sítě nahrávání stojí a služba by jen pálila baterii. TDLib ho dokončí,
                // až bude aplikace zase v popředí.
                logger.i { "Nahrávání se ${STALL_TIMEOUT.inWholeMinutes} min nehnulo, proces už se nedrží." }
                return Result.success()
            }
        }
    }

    private suspend fun show(activity: UploadActivity) {
        if (!foreground) return
        val info = foregroundInfo(activity)
        try {
            setForeground(info)
        } catch (e: CancellationException) {
            // Je to taky `IllegalStateException`, viz AiModelDownloadWorker.
            throw e
        } catch (e: IllegalStateException) {
            // `ForegroundServiceStartNotAllowedException` (Android 12+, úloha běží z pozadí —
            // typicky po zabití procesu). Úloha proces drží i tak, jen kratší dobu.
            logger.i { "Nahrávání poběží bez služby v popředí: ${e.message}" }
            foreground = false
        }
    }

    private suspend fun foregroundInfo(activity: UploadActivity): ForegroundInfo {
        val fraction = activity.fraction
        val notification = NotificationCompat.Builder(applicationContext, UPLOADS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getPluralString(Res.plurals.upload_notification_title, activity.files, activity.files))
            .setContentText(
                if (fraction != null) {
                    getString(
                        Res.string.upload_notification_progress,
                        fileSizeString(activity.uploadedBytes),
                        fileSizeString(activity.totalBytes),
                    )
                } else {
                    null
                },
            )
            .setProgress(PROGRESS_MAX, ((fraction ?: 0f) * PROGRESS_MAX).toInt(), fraction == null)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            // Krátké nahrávání (fotka) skončí dřív, než by se notifikace vůbec ukázala.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .setContentIntent(openApp())
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    /** Otevře aplikaci tam, kde byla. Cíl přes launcher intent, `MainActivity` je ve vstupním bodu. */
    private fun openApp(): PendingIntent? {
        val component = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
            ?.component
            ?: return null
        val intent = Intent(Intent.ACTION_MAIN)
            .setComponent(component)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(applicationContext, NOTIFICATION_ID, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    internal companion object {
        const val WORK_NAME = "upload-keep-alive"
        const val NOTIFICATION_ID = 7304
        const val PROGRESS_MAX = 1000
        val LEGACY_FOREGROUND_DELAY: Duration = 3.seconds
        val START_GRACE: Duration = 15.seconds
        val IDLE_GRACE: Duration = 5.seconds
        val NOTIFICATION_INTERVAL: Duration = 1.seconds
        val STALL_TIMEOUT: Duration = 5.minutes
    }
}

/**
 * Naplánuje [UploadKeepAliveWorker], kdykoli začne nahrávání v kterémkoli účtu. Nahrávání
 * začíná ťuknutím na Odeslat, tedy s aplikací v popředí — jen tehdy jde službu v popředí
 * spustit (Android 12+).
 */
class UploadKeepAlive(
    private val context: Context,
    private val activity: StateFlow<UploadActivity>,
    private val scope: CoroutineScope,
) {
    private val logger = Logger.withTag("Uploads")

    fun start() {
        scope.launch {
            activity
                .filter { !it.isIdle }
                // Nový soubor, ne každý posun průběhu: plánování je zápis do databáze WorkManageru.
                .map { it.started }
                .distinctUntilChanged()
                .collect { enqueue() }
        }
    }

    private fun enqueue() {
        val request = OneTimeWorkRequestBuilder<UploadKeepAliveWorker>()
            .apply {
                // Expedited do Androidu 11 znamená službu v popředí OD STARTU úlohy (WorkManager
                // si vezme `getForegroundInfo`) — notifikace by problikla i u každé fotky.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                }
            }
            .build()
        runCatching {
            // KEEP: běžící úloha si nový soubor všimne sama, druhá by jen čekala ve frontě.
            WorkManager.getInstance(context).enqueueUniqueWork(UploadKeepAliveWorker.WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }.onFailure { logger.w(it) { "Úlohu nahrávání nejde naplánovat." } }
    }
}
