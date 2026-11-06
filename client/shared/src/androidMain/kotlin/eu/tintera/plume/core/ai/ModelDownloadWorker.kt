package eu.tintera.plume.core.ai

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import co.touchlab.kermit.Logger
import eu.tintera.plume.client.R
import eu.tintera.plume.core.notifications.MODEL_DOWNLOAD_CHANNEL_ID
import eu.tintera.plume.core.notifications.createModelDownloadChannel
import eu.tintera.plume.core.ui.fileSizeString
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.model_download_notification_done
import eu.tintera.plume.resources.model_download_notification_done_text
import eu.tintera.plume.resources.model_download_notification_failed
import eu.tintera.plume.resources.model_download_notification_failed_text
import eu.tintera.plume.resources.model_download_notification_stop
import eu.tintera.plume.resources.model_download_notification_title
import eu.tintera.plume.resources.model_download_notification_waiting
import eu.tintera.plume.resources.settings_on_device_ai_download_progress
import eu.tintera.plume.resources.settings_on_device_ai_not_enough_space
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * Stáhne model Gemma jako úloha WorkManageru se službou v popředí a notifikací průběhu.
 *
 * Stahuje se gigabajty, tedy minuty. Dokud to dělala korutina v procesu aplikace, stahování
 * skončilo s procesem — a ten systém v pozadí zabíjí běžně — a navázalo až dalším ťuknutím
 * v Nastavení. Úlohu WorkManager spustí znovu i po zabití procesu a stahování naváže na `.part`
 * (`Range`, viz [LiteRtOnDeviceAi]).
 *
 * - **Stahuje pořád [LiteRtOnDeviceAi]**, i s navazováním po výpadku sítě; úloha jen drží proces
 *   naživu a ukazuje průběh. Stav pro obrazovku zapisuje tatáž instance, takže Nastavení vidí
 *   průběh, ať stahování spustilo ťuknutí, nebo WorkManager po restartu procesu.
 * - **Podmínka sítě.** Bez připojení se úloha nespustí a když síť zmizí, WorkManager ji zastaví
 *   a po návratu spustí znovu — služba v popředí tak nevisí hodiny bez sítě. Krátký výpadek,
 *   o kterém systém ještě neví, dál přečká smyčka v [LiteRtOnDeviceAi].
 * - **Služba v popředí typu `dataSync`.** Bez ní by běžná úloha skončila po deseti minutách.
 *   Od Androidu 12 se ale služba v popředí z pozadí spustit nesmí (úloha naplánovaná po zabití
 *   procesu běží právě tam) — pak se stahuje jako běžná úloha: po limitu ji systém zastaví,
 *   WorkManager ji spustí znovu a stahování naváže. Pomalejší, ale dojde se.
 * - **Zastavit jde z notifikace i z Nastavení.** Bez toho by stahování, které přežije zabití
 *   aplikace, nešlo zastavit vůbec. Zrušení úlohy pozná [onStopped] podle důvodu
 *   ([WorkInfo.STOP_REASON_CANCELLED_BY_APP]) od zastavení systémem.
 */
class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val logger = Logger.withTag("OnDeviceAi")

    /** Úlohu se podařilo povýšit na službu v popředí; jinak se průběh jen zobrazuje. */
    private var foreground = true

    /** Naposledy ukázaný průběh (procenta, čekání na síť) — notifikace se mění jen při změně. */
    private var shown: Pair<Int, Boolean>? = null

    override suspend fun doWork(): Result {
        val ai = GlobalContext.getOrNull()?.getOrNull<LiteRtOnDeviceAi>() ?: return Result.failure()
        val model = inputData.getString(KEY_MODEL)?.let { name -> LiteRtModel.entries.find { it.name == name } }
            ?: return Result.failure()
        createModelDownloadChannel(applicationContext)

        return try {
            coroutineScope {
                val progress = launch {
                    ai.downloadProgress(model).conflate().collect { showProgress(model, it) }
                }
                try {
                    ai.runDownload(model)
                } finally {
                    progress.cancel()
                }
            }
            notifyResult(model, error = null)
            Result.success()
        } catch (e: CancellationException) {
            ai.onWorkerStopped(
                model,
                byUser = stopReason == WorkInfo.STOP_REASON_CANCELLED_BY_APP,
                waitingForNetwork = stopReason == WorkInfo.STOP_REASON_CONSTRAINT_CONNECTIVITY,
            )
            hideProgress(model)
            throw e
        } catch (e: Exception) {
            notifyResult(model, error = e)
            Result.failure()
        }
    }

    private suspend fun showProgress(model: LiteRtModel, progress: ModelDownload) {
        val percent = progress.fraction?.let { (it * 100).toInt() } ?: -1
        val key = percent to progress.waitingForNetwork
        if (key == shown) return
        shown = key

        val info = foregroundInfo(model, progress, percent)
        if (foreground) {
            try {
                setForeground(info)
                return
            } catch (e: CancellationException) {
                // Je to taky `IllegalStateException` — bez tohohle by zrušená úloha vypadala
                // jako odepřená služba v popředí a notifikaci průběhu by ukázala znovu.
                throw e
            } catch (e: IllegalStateException) {
                // `ForegroundServiceStartNotAllowedException` (Android 12+, úloha běží z pozadí).
                // Očekávaný stav po zabití procesu, ne chyba — proto bez stack trace.
                logger.i { "Stahování modelu poběží bez služby v popředí (${model.displayName}): ${e.message}" }
                foreground = false
            }
        }
        notify(info.notificationId, info.notification)
    }

    private suspend fun foregroundInfo(model: LiteRtModel, progress: ModelDownload, percent: Int): ForegroundInfo {
        val total = progress.totalBytes
        val text = when {
            progress.waitingForNetwork -> readString(Res.string.model_download_notification_waiting)
            total != null -> readString(
                Res.string.settings_on_device_ai_download_progress,
                fileSizeString(progress.downloadedBytes),
                fileSizeString(total),
            )
            else -> null
        }
        val notification = NotificationCompat.Builder(applicationContext, MODEL_DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(readString(Res.string.model_download_notification_title, model.displayName))
            .setContentText(text)
            .setProgress(100, percent.coerceAtLeast(0), percent < 0 || progress.waitingForNetwork)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            // Bez toho by Android 12+ notifikaci služby ukázal až po deseti vteřinách.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp())
            .addAction(
                0,
                readString(Res.string.model_download_notification_stop),
                WorkManager.getInstance(applicationContext).createCancelPendingIntent(id),
            )
            .build()
        val notificationId = PROGRESS_NOTIFICATION_ID + model.ordinal
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    /**
     * Notifikace průběhu po zastavení. Službu v popředí uklidí WorkManager sám; tohle je pro
     * případ, kdy úloha běžela bez ní a notifikaci ukazovala sama.
     */
    private fun hideProgress(model: LiteRtModel) {
        if (!foreground) NotificationManagerCompat.from(applicationContext).cancel(PROGRESS_NOTIFICATION_ID + model.ordinal)
    }

    /**
     * Výsledek, i když je aplikace zavřená: kdo stahování spustil a odešel, se jinak nedozví,
     * že model je připravený — nebo že se stahování zastavilo a čeká na něj.
     */
    private suspend fun notifyResult(model: LiteRtModel, error: Exception?) {
        hideProgress(model)
        val (title, text) = when (error) {
            null -> readString(Res.string.model_download_notification_done, model.displayName) to
                readString(Res.string.model_download_notification_done_text)
            is NotEnoughSpaceException -> readString(Res.string.model_download_notification_failed, model.displayName) to
                readString(Res.string.settings_on_device_ai_not_enough_space)
            else -> readString(Res.string.model_download_notification_failed, model.displayName) to
                readString(Res.string.model_download_notification_failed_text)
        }
        val notification = NotificationCompat.Builder(applicationContext, MODEL_DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(if (error == null) NotificationCompat.CATEGORY_STATUS else NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        notify(RESULT_NOTIFICATION_ID + model.ordinal, notification)
    }

    private fun notify(id: Int, notification: Notification) {
        // Od Androidu 13 bez oprávnění `notify` tiše nic neudělá; ptá se Nastavení → Notifikace.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(applicationContext).notify(id, notification)
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
        return PendingIntent.getActivity(applicationContext, OPEN_APP_REQUEST_CODE, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {

        private const val KEY_MODEL = "model"

        /** Oddělené od notifikací zpráv (ty mají id skupin TDLib, malá kladná čísla). */
        private const val PROGRESS_NOTIFICATION_ID = 0x6A10_0000
        private const val RESULT_NOTIFICATION_ID = 0x6A20_0000
        private const val OPEN_APP_REQUEST_CODE = 0x6A30_0000

        private fun workName(model: LiteRtModel) = "ai-model-download-${model.name}"

        /**
         * `KEEP`: běžící i naplánovaná úloha (třeba čekající na síť) zůstává, druhá by stahovala
         * do téhož `.part` souběžně. Dokončená nebo zrušená úloha `KEEP` neblokuje.
         */
        fun enqueue(context: Context, model: LiteRtModel) {
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setInputData(workDataOf(KEY_MODEL to model.name))
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(workName(model), ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context, model: LiteRtModel) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(model))
        }

        /** Je úloha naplánovaná nebo běží? Po zabití procesu ji WorkManager spustí sám. */
        suspend fun isPending(context: Context, model: LiteRtModel): Boolean =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(workName(model)).first()
                .any { !it.state.isFinished }
    }
}
