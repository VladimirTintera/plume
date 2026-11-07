package eu.tintera.plume.core.ai

import android.content.Context
import androidx.work.WorkerParameters
import eu.tintera.ai.core.AiModelDownloadWorker
import eu.tintera.ai.core.LiteRtModel
import eu.tintera.ai.core.LiteRtOnDeviceAi
import eu.tintera.ai.core.ModelDownload
import eu.tintera.ai.core.ModelDownloadNotifications
import eu.tintera.ai.core.NotEnoughSpaceException
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
import org.koin.core.context.GlobalContext

/**
 * Úloha stahování modelu v zařízení ([AiModelDownloadWorker] z ai-engine :ai-core). Stahuje
 * knihovna; tady je jen to, odkud si vezme runtime a notifikace — z Koinu, i když ji WorkManager
 * pustí po zabití procesu bez obrazovky.
 *
 * **Jméno třídy se nesmí měnit** (ani balíček): WorkManager si ho pamatuje v naplánované úloze
 * a úloha z předchozí verze aplikace by po přejmenování skončila chybou. Proto třída zůstala
 * tady pod původním jménem, když se stahování přesunulo do knihovny.
 */
class ModelDownloadWorker(context: Context, params: WorkerParameters) : AiModelDownloadWorker(context, params) {

    override fun dependencies(): Dependencies? {
        val koin = GlobalContext.getOrNull() ?: return null
        val ai = koin.getOrNull<LiteRtOnDeviceAi>() ?: return null
        val notifications = koin.getOrNull<ModelDownloadNotifications>() ?: return null
        return Dependencies(ai, notifications)
    }
}

/** Notifikace stahování modelu v barvách a textech Plume (kanál „Stahování modelu"). */
class PlumeModelDownloadNotifications : ModelDownloadNotifications {

    override val smallIcon: Int = R.drawable.ic_notification

    override suspend fun channel(context: Context): String {
        createModelDownloadChannel(context)
        return MODEL_DOWNLOAD_CHANNEL_ID
    }

    override suspend fun progressTitle(model: LiteRtModel): String =
        readString(Res.string.model_download_notification_title, model.displayName)

    override suspend fun progressText(progress: ModelDownload): String? {
        val total = progress.totalBytes
        return when {
            progress.waitingForNetwork -> readString(Res.string.model_download_notification_waiting)
            total != null -> readString(
                Res.string.settings_on_device_ai_download_progress,
                fileSizeString(progress.downloadedBytes),
                fileSizeString(total),
            )
            else -> null
        }
    }

    override suspend fun stopAction(): String = readString(Res.string.model_download_notification_stop)

    override suspend fun result(model: LiteRtModel, error: Exception?): Pair<String, String> = when (error) {
        null -> readString(Res.string.model_download_notification_done, model.displayName) to
            readString(Res.string.model_download_notification_done_text)
        is NotEnoughSpaceException -> readString(Res.string.model_download_notification_failed, model.displayName) to
            readString(Res.string.settings_on_device_ai_not_enough_space)
        else -> readString(Res.string.model_download_notification_failed, model.displayName) to
            readString(Res.string.model_download_notification_failed_text)
    }
}
