package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.navigation.ChatOpenRequests
import eu.tintera.plume.core.notifications.DesktopNotification
import eu.tintera.plume.core.notifications.DesktopNotifier
import eu.tintera.plume.core.platform.DesktopWindow
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.snooze.ChatSnoozes
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.notification_call_unknown
import eu.tintera.plume.resources.notification_call_video
import eu.tintera.plume.resources.notification_call_voice
import java.awt.Taskbar
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Notifikace na desktopu: TDLib je vydává, dokud aplikace běží (push tu není), a presenter je
 * předává systému přes [DesktopNotifier] — na macOS `UNUserNotificationCenter`, jinde AWT.
 *
 * - **Jedna zpráva = jedno oznámení**, seskupené po chatech (`thread`). Tak to na macOS dělají
 *   Zprávy i Telegram; skládat zprávy do jednoho oznámení jako `MessagingStyle` na Androidu
 *   tu nejde. Id oznámení je `groupId` + `notificationId`, takže se dá zrušit přesně to, co
 *   TDLib stáhne (přečtení jinde).
 * - **Z dávky se ukáže jen nejnovější zpráva chatu.** Po startu nebo po výpadku spojení vydá
 *   TDLib naráz všechno, co se nahromadilo — deset bannerů za sebou by byl šum. Ostatní se
 *   jen zapamatují, aby je pozdější update neukázal znovu.
 * - **Aktivní okno oznámení nedostane** ([DesktopWindow.focused]): uživatel se do aplikace
 *   právě dívá a nepřečtené vidí v seznamu. Tak to dělá i Telegram Desktop.
 * - **Tiché hodiny a odložené chaty** jako na Androidu: oznámení přijde, jen bez zvuku.
 * - **Odznak v Docku** (macOS) je počet nepřečtených zpráv v neztlumených chatech hlavního
 *   seznamu, stejně jako odznak na iOS.
 */
actual class NotificationPresenter internal constructor(
    private val chats: ChatRepository,
    private val settings: AppSettings,
    private val chatOpenRequests: ChatOpenRequests,
    private val notifier: DesktopNotifier,
    private val scope: CoroutineScope,
    /** Odložené chaty: dokud spí, přijdou jejich oznámení potichu. */
    private val snoozes: ChatSnoozes? = null,
) {

    private val logger = Logger.withTag("Notifications")

    /**
     * Co už TDLib vydal, podle `groupId` — ať se nic neukáže dvakrát a ať je co rušit. Sahá se
     * sem jen z [dispatcher].
     */
    private val known = mutableMapOf<Int, MutableSet<Int>>()

    /** Skupiny notifikací příchozích hovorů; zruší je [NotificationUpdate.Removed] s týmž `groupId`. */
    private val calls = mutableSetOf<Int>()

    private val dispatcher = Dispatchers.Default.limitedParallelism(1)

    actual fun start(repository: NotificationRepository) {
        notifier.onActivated = { chatId, topicId ->
            logger.d { "Klik na oznámení: chat $chatId, téma $topicId." }
            chatOpenRequests.request(chatId, topicId)
            DesktopWindow.activate()
        }
        if (!notifier.available) logger.i { "Systémová oznámení tu nejsou k dispozici (ladicí běh na macOS?)." }

        scope.launch(dispatcher) {
            repository.updates.collect { update ->
                runCatching {
                    when (update) {
                        is NotificationUpdate.Added -> add(update.notifications)
                        is NotificationUpdate.Removed -> remove(update)
                        is NotificationUpdate.Call -> showCall(update.call)
                        NotificationUpdate.Cleared -> clear()
                    }
                }.onFailure { logger.w(it) { "Oznámení se nepodařilo zpracovat." } }
            }
        }
        startBadge()
    }

    private fun add(notifications: List<ChatNotification>) {
        notifications.groupBy { it.groupId }.forEach { (groupId, batch) ->
            val ids = known.getOrPut(groupId) { mutableSetOf() }
            val fresh = batch.filter { ids.add(it.notificationId) }
            val newest = fresh.maxByOrNull { it.timestamp } ?: return@forEach
            if (DesktopWindow.focused.value) {
                logger.d { "Okno je aktivní, oznámení skupiny $groupId se neukazuje." }
                return@forEach
            }
            val quiet = settings.quietHours.value.silences(newest.channel, minuteOfDay()) ||
                snoozes?.isSleeping(newest.chatId) == true
            logger.d { "Oznámení skupiny $groupId (${fresh.size} nových), potichu=${newest.isSilent || quiet}." }
            notifier.show(
                DesktopNotification(
                    id = id(groupId, newest.notificationId),
                    thread = "chat-${newest.chatId}",
                    title = newest.chatTitle,
                    subtitle = newest.senderName,
                    body = newest.text,
                    chatId = newest.chatId,
                    topicId = newest.topicId,
                    silent = newest.isSilent || quiet,
                    imagePath = newest.imagePath ?: newest.chatAvatarPath,
                ),
            )
        }
    }

    private fun remove(update: NotificationUpdate.Removed) {
        if (calls.remove(update.groupId)) {
            notifier.remove(listOf(callId(update.groupId)))
            return
        }
        known[update.groupId]?.removeAll(update.notificationIds.toSet())
        notifier.remove(update.notificationIds.map { id(update.groupId, it) })
    }

    private suspend fun showCall(call: IncomingCall) {
        if (DesktopWindow.focused.value) return
        calls += call.groupId
        notifier.show(
            DesktopNotification(
                id = callId(call.groupId),
                thread = "chat-${call.chatId}",
                title = call.callerName.ifBlank { readString(Res.string.notification_call_unknown) },
                subtitle = null,
                body = readString(if (call.isVideo) Res.string.notification_call_video else Res.string.notification_call_voice),
                chatId = call.chatId,
                topicId = null,
                silent = false,
                imagePath = call.avatarPath,
            ),
        )
    }

    private fun clear() {
        known.clear()
        calls.clear()
        notifier.removeAll()
        setBadge(0)
    }

    @OptIn(FlowPreview::class)
    private fun startBadge() {
        val taskbar = runCatching { Taskbar.getTaskbar() }.getOrNull()
            ?.takeIf { it.isSupported(Taskbar.Feature.ICON_BADGE_TEXT) }
            ?: return
        scope.launch {
            chats.chats(ChatFolder.Main)
                .filterIsInstance<ChatListState.Ready>()
                .map { state -> state.chats.filter { !it.isMuted }.sumOf { it.unreadCount } }
                .distinctUntilChanged()
                // Seznam se při startu skládá po dávkách; stačí se podívat, až se usadí.
                .debounce(BADGE_SETTLE_MS)
                .collect { count -> runCatching { taskbar.setIconBadge(count.takeIf { it > 0 }?.toString()) } }
        }
    }

    private fun setBadge(count: Int) {
        runCatching {
            val taskbar = Taskbar.getTaskbar()
            if (taskbar.isSupported(Taskbar.Feature.ICON_BADGE_TEXT)) taskbar.setIconBadge(count.takeIf { it > 0 }?.toString())
        }
    }

    private fun minuteOfDay(): Int {
        val time = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return time.hour * 60 + time.minute
    }

    private fun id(groupId: Int, notificationId: Int) = "g$groupId-n$notificationId"

    private fun callId(groupId: Int) = "call-$groupId"

    private companion object {
        const val BADGE_SETTLE_MS = 1_000L
    }
}
