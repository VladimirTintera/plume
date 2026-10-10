package eu.tintera.plume.core.snooze

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.tintera.plume.client.R
import eu.tintera.plume.core.notifications.NotificationIntents
import eu.tintera.plume.core.notifications.SNOOZE_CHANNEL_ID
import eu.tintera.plume.core.notifications.createSnoozeChannel
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.snooze_reminder_text
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Upozornění na vynoření odloženého chatu přes WorkManager: úloha přežije zabití procesu
 * i restart telefonu a spustí aplikaci, když neběží.
 *
 * **Úloha je jedna na chat** (`enqueueUniqueWork` s REPLACE): nové odložení téhož chatu staré
 * upozornění nahradí, zrušení odložení ho zruší i s notifikací v liště.
 *
 * Nepřesnost: WorkManager čas nedodrží na minutu, v úsporném režimu (Doze) může upozornění
 * přijít později. Přesný alarm (`SCHEDULE_EXACT_ALARM`) by chtěl další oprávnění a odložení
 * chatu na minutu přesné být nemusí — chat se mezitím vynoří v seznamu přesně.
 */
class AndroidSnoozeReminders(private val context: Context) : SnoozeReminders {

    override fun schedule(chatId: Long, title: String, at: Instant) {
        val delay = (at - Clock.System.now()).inWholeMilliseconds.coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<SnoozeReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_CHAT_ID to chatId, KEY_TITLE to title))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(chatId), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(chatId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(chatId))
        NotificationManagerCompat.from(context).cancel(notificationTag(chatId), NOTIFICATION_ID)
    }
}

/** Ukáže upozornění, že se odložený chat vrací; ťuknutí ho otevře. */
class SnoozeReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val chatId = inputData.getLong(KEY_CHAT_ID, 0L).takeIf { it != 0L } ?: return Result.success()
        val title = inputData.getString(KEY_TITLE).orEmpty()
        // Od Androidu 13 bez oprávnění `notify` tiše nic neudělá; chat se vynoří aspoň v seznamu.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        // Kanál se zakládá se startem aplikace; úloha ale může proces spustit sama.
        createSnoozeChannel(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, SNOOZE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(readString(Res.string.snooze_reminder_text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openChat(chatId))
            .build()
        NotificationManagerCompat.from(applicationContext).notify(notificationTag(chatId), NOTIFICATION_ID, notification)
        return Result.success()
    }

    /**
     * Intent na otevření chatu. **Data nesou chatId**, aby byl intent každého chatu jiný podle
     * `filterEquals` — se společným request code by jinak všechna upozornění otevřela chat, který
     * se zaregistroval první (extras do rovnosti nevstupují, viz `NotificationIntents.openChat`).
     */
    private fun openChat(chatId: Long): PendingIntent? {
        val intent = NotificationIntents.chatIntent(applicationContext, chatId)
            ?.setData(Uri.parse("plume-snooze://chat/$chatId"))
            ?: return null
        return PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

private fun workName(chatId: Long) = "snooze-reminder-$chatId"

/** Notifikace se odlišují tagem, ne id — id je Int a Long chatu by se do něj nevešlo bez kolizí. */
private fun notificationTag(chatId: Long) = "snooze-reminder:$chatId"

private const val NOTIFICATION_ID = 1
private const val KEY_CHAT_ID = "chatId"
private const val KEY_TITLE = "title"
