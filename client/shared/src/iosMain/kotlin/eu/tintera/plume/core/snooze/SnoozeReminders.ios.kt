package eu.tintera.plume.core.snooze

import eu.tintera.plume.core.accounts.AccountSlot
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.notifications.NotificationCategories
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.snooze_reminder_text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Upozornění na vynoření odloženého chatu jako lokální notifikace — systém ji doručí sám, i když
 * aplikace neběží. `userInfo` nese chat stejně jako push ([NotificationCategories.KEY_CHAT_ID]),
 * takže ťuknutí otevře chat touž cestou.
 *
 * **Úklid notifikací přečtených chatů ji nesmí sebrat** (`NotificationPresenter.removeDelivered`):
 * připomínka přijde k chatu, který bývá přečtený. Pozná se podle [SNOOZE_REMINDER_PREFIX].
 */
class IosSnoozeReminders : SnoozeReminders {

    private val logger = Logger.withTag("SnoozeReminders")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun schedule(slot: AccountSlot, chatId: Long, title: String, at: Instant) {
        // Text se čte z prostředků suspend funkcí, proto to celé běží v korutině.
        scope.launch {
            val seconds = (at - Clock.System.now()).inWholeSeconds.coerceAtLeast(1).toDouble()
            val content = UNMutableNotificationContent().apply {
                setTitle(title)
                setBody(readString(Res.string.snooze_reminder_text))
                setSound(UNNotificationSound.defaultSound)
                setUserInfo(mapOf<Any?, Any?>(NotificationCategories.KEY_CHAT_ID to chatId))
            }
            val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false)
            val request = UNNotificationRequest.requestWithIdentifier(identifier(chatId), content, trigger)
            // Stejný identifikátor = nové odložení téhož chatu staré nahradí.
            UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request) { error ->
                if (error != null) logger.w { "Připomínku chatu $chatId se nepodařilo naplánovat: ${error.localizedDescription}" }
            }
        }
    }

    override fun cancel(slot: AccountSlot, chatId: Long) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        val ids = listOf(identifier(chatId))
        center.removePendingNotificationRequestsWithIdentifiers(ids)
        center.removeDeliveredNotificationsWithIdentifiers(ids)
    }

    private fun identifier(chatId: Long) = "$SNOOZE_REMINDER_PREFIX$chatId"
}

/** Začátek identifikátoru lokální notifikace s připomínkou odloženého chatu. */
internal const val SNOOZE_REMINDER_PREFIX = "snooze-reminder-"
