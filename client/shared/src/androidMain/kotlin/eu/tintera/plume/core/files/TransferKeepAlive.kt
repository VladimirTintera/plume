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
import eu.tintera.plume.core.telegram.TransferActivity
import eu.tintera.plume.core.ui.fileSizeString
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.download_notification_title
import eu.tintera.plume.resources.download_notification_title_after_upload
import eu.tintera.plume.resources.transfer_notification_both
import eu.tintera.plume.resources.transfer_notification_cancel_uploads
import eu.tintera.plume.resources.transfer_notification_pause_downloads
import eu.tintera.plume.resources.transfer_notification_progress
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
 * Drží proces naživu, dokud se nahrávají nebo stahují soubory ([ActiveTransfers]) — úloha
 * WorkManageru se službou v popředí typu `dataSync`. Přenáší dál TDLib; úloha jen brání systému,
 * aby proces na pozadí zmrazil nebo zabil (`docs/poznatky/konverzace-psani.md`, „Nahrávání
 * a stahování na pozadí").
 *
 * **Jméno třídy se nesmí měnit** (ani balíček): WorkManager si ho pamatuje v naplánované úloze.
 * Proto „Upload", i když hlídá i stahování — vyšla tak v `android-0.4.12`.
 */
class UploadKeepAliveWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val logger = Logger.withTag("Transfers")

    /** Úlohu se podařilo povýšit na službu v popředí; bez ní notifikace nemá smysl. */
    private var foreground = true

    override suspend fun doWork(): Result {
        val activity = GlobalContext.getOrNull()?.getOrNull<ActiveTransfers>()?.activity ?: return Result.success()
        createUploadsChannel(applicationContext)

        // Do Androidu 11 jde služba v popředí spustit i z pozadí, takže se s ní dá chvíli počkat:
        // fotka se přenese za vteřinu a notifikace by jen problikla. Od 12 se musí spustit hned,
        // dokud je aplikace vidět — notifikaci tam systém sám odloží o deset vteřin.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            withTimeoutOrNull(LEGACY_FOREGROUND_DELAY) { activity.first { it.isIdle } } != null
        ) {
            return Result.success()
        }

        var current = activity.value
        // Po zabití procesu ji WorkManager spustí znovu, a TDLib rozeslané zprávy i rozestahované
        // soubory obnoví až po startu klienta; na první přenos se proto čeká déle než na další.
        var grace = START_GRACE
        while (true) {
            if (current.isIdle) {
                // Soubory alba a zprávy odeslané rychle za sebou nenavazují: mezi koncem jednoho
                // a začátkem dalšího bývá chvíle bez přenosu.
                current = withTimeoutOrNull(grace) { activity.first { !it.isIdle } } ?: return Result.success()
                continue
            }
            grace = IDLE_GRACE
            show(current)
            // Notifikace se nemění častěji než jednou za vteřinu, ať ji systém nezačne zahazovat.
            delay(NOTIFICATION_INTERVAL)
            val shown = current
            current = withTimeoutOrNull(STALL_TIMEOUT) { activity.first { it != shown } } ?: run {
                // Bez sítě přenos stojí a služba by jen pálila baterii. TDLib ho dokončí,
                // až bude aplikace zase v popředí.
                logger.i { "Přenos se ${STALL_TIMEOUT.inWholeMinutes} min nehnul, proces už se nedrží." }
                return Result.success()
            }
        }
    }

    private suspend fun show(activity: Transfers) {
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
            logger.i { "Přenos poběží bez služby v popředí: ${e.message}" }
            foreground = false
        }
    }

    private suspend fun foregroundInfo(transfers: Transfers): ForegroundInfo {
        // Průběh jednou lištou přes oba směry: notifikace je jedna a dvě lišty neumí.
        val activity = transfers.uploads + transfers.downloads
        val fraction = activity.fraction
        val notification = NotificationCompat.Builder(applicationContext, UPLOADS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title(transfers))
            .setContentText(
                if (fraction != null) {
                    getString(
                        Res.string.transfer_notification_progress,
                        fileSizeString(activity.transferredBytes),
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
            .apply {
                // Nahrávání pozastavit nejde (TDLib ho umí jen zrušit smazáním zprávy), stahování
                // ano — jako ve Stahování. Zrušit jde jen nahrávání, jehož zprávu známe.
                if (transfers.uploads.cancellable > 0) {
                    addAction(0, getString(Res.string.transfer_notification_cancel_uploads), TransferActionsReceiver.cancelUploads(applicationContext))
                }
                if (!transfers.downloads.isIdle) {
                    addAction(0, getString(Res.string.transfer_notification_pause_downloads), TransferActionsReceiver.pauseDownloads(applicationContext))
                }
            }
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun title(transfers: Transfers): String {
        val uploads = transfers.uploads.files
        val downloads = transfers.downloads.files
        return when {
            downloads == 0 -> getPluralString(Res.plurals.upload_notification_title, uploads, uploads)
            uploads == 0 -> getPluralString(Res.plurals.download_notification_title, downloads, downloads)
            else -> getString(
                Res.string.transfer_notification_both,
                getPluralString(Res.plurals.upload_notification_title, uploads, uploads),
                getPluralString(Res.plurals.download_notification_title_after_upload, downloads, downloads),
            )
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
 * Naplánuje [UploadKeepAliveWorker], kdykoli začne nahrávání nebo stahování v kterémkoli účtu.
 * Přenos začíná ťuknutím (Odeslat, stáhnout soubor, Pokračovat ve Stahování), tedy s aplikací
 * v popředí — jen tehdy jde službu v popředí spustit (Android 12+).
 */
class TransferKeepAlive(
    private val context: Context,
    private val activity: StateFlow<Transfers>,
    private val scope: CoroutineScope,
) {
    private val logger = Logger.withTag("Transfers")

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
        }.onFailure { logger.w(it) { "Úlohu přenosu nejde naplánovat." } }
    }
}
