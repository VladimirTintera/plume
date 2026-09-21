package eu.tintera.plume

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.notifications.NotificationReplies
import eu.tintera.plume.core.notifications.PendingReply
import eu.tintera.plume.core.notifications.ReplyState
import eu.tintera.plume.core.telegram.NotificationPresenter
import org.koin.core.context.GlobalContext
import kotlin.time.Duration.Companion.seconds

/**
 * Dočká se potvrzení odpovědi z notifikace, která se nestihla potvrdit v okně příjemce akce
 * (pomalá síť, TDLib se po probuzení teprve připojuje).
 *
 * Dokud úloha běží, proces zmrazený není a TDLib zprávu odesílá. Po úspěchu se chat přečte
 * a TDLib notifikaci stáhne, po selhání notifikace ukáže „Neodesláno". Expedited jen od
 * Androidu 12 — stejný důvod jako u [NotificationDeliveryWorker].
 */
class ReplyDeliveryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val logger = Logger.withTag("NotificationReply")

    override suspend fun doWork(): Result {
        val koin = GlobalContext.getOrNull() ?: return Result.success()
        val reply = PendingReply(
            chatId = inputData.getLong(KEY_CHAT_ID, 0L),
            messageId = inputData.getLong(KEY_MESSAGE_ID, 0L),
            readUpTo = inputData.getLong(KEY_READ_UP_TO, 0L).takeIf { it != 0L },
        )
        val groupId = inputData.getInt(KEY_GROUP_ID, 0)
        val text = inputData.getString(KEY_TEXT).orEmpty()

        when (koin.get<NotificationReplies>().await(reply, TIMEOUT)) {
            ReplyState.Sent -> logger.d { "Odpověď z notifikace potvrzena v úloze." }
            ReplyState.Failed -> koin.get<NotificationPresenter>().showReplyFailed(groupId, text)
            // TDLib ji má dál v databázi a došle ji při příštím běhu procesu.
            ReplyState.Pending -> logger.w { "Odpověď z notifikace se nepotvrdila do $TIMEOUT." }
        }
        return Result.success()
    }

    companion object {

        private val TIMEOUT = 60.seconds

        private const val KEY_CHAT_ID = "chatId"
        private const val KEY_MESSAGE_ID = "messageId"
        private const val KEY_READ_UP_TO = "readUpTo"
        private const val KEY_GROUP_ID = "groupId"
        private const val KEY_TEXT = "text"

        fun enqueue(context: Context, reply: PendingReply, groupId: Int, text: String) {
            val request = OneTimeWorkRequestBuilder<ReplyDeliveryWorker>()
                .setInputData(
                    workDataOf(
                        KEY_CHAT_ID to reply.chatId,
                        KEY_MESSAGE_ID to reply.messageId,
                        KEY_READ_UP_TO to (reply.readUpTo ?: 0L),
                        KEY_GROUP_ID to groupId,
                        KEY_TEXT to text,
                    ),
                )
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    }
                }
                .build()
            // Úloha na každou odpověď zvlášť: dvě rychle po sobě se nesmí přepsat.
            WorkManager.getInstance(context)
                .enqueueUniqueWork("notification-reply-${reply.chatId}-${reply.messageId}", ExistingWorkPolicy.KEEP, request)
        }
    }
}
