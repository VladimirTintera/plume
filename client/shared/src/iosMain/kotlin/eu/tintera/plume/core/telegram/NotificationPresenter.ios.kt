package eu.tintera.plume.core.telegram

import eu.tintera.plume.core.snooze.SNOOZE_REMINDER_PREFIX
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.notifications.pushChatId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNUserNotificationCenter

/**
 * Notifikace na iOS zobrazuje SYSTÉM z APNs pushe (viz `IosPush`), ne aplikace. TDLib vydává
 * `UpdateNotificationGroup` i tady, jenže jen dokud aplikace běží — a na pozadí ji iOS uspí,
 * takže by z toho byly nanejvýš notifikace v popředí, ke kterým už chodí push.
 *
 * Presenter proto dělá jen to, co push neumí:
 *
 * - **Uklidí notifikace přečtených chatů.** Přečtení jinde Telegram hlásí servisním pushem
 *   (`READ_HISTORY`), který se bez rozšíření s TDLib nezpracuje; notifikace by visely, dokud je
 *   uživatel nesmaže. Tady zmizí nejpozději s otevřením aplikace, kdy TDLib načte stav chatů.
 * - **Srovná odznak na ikoně.** Odznak nastavuje server v pushi (`aps.badge`) a po přečtení
 *   v aplikaci by zůstal starý. Počítá se jako počet nepřečtených zpráv v neztlumených chatech
 *   hlavního seznamu — to je i výchozí počítání Telegramu.
 */
actual class NotificationPresenter(private val chats: ChatRepository) {

    private val logger = Logger.withTag("Notifications")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    actual fun start(repository: NotificationRepository) {
        val ready = chats.chats(ChatFolder.Main).filterIsInstance<ChatListState.Ready>()

        scope.launch {
            ready.map { state -> state.chats.filter { it.unreadCount == 0 && !it.isMarkedAsUnread }.map { it.id }.toSet() }
                .distinctUntilChanged()
                // Seznam se při startu skládá po dávkách; stačí se podívat, až se usadí.
                .debounce(SETTLE_MS)
                .collect(::removeDelivered)
        }

        scope.launch {
            ready.map { state -> state.chats.filter { !it.isMuted }.sumOf { it.unreadCount } }
                .distinctUntilChanged()
                .debounce(SETTLE_MS)
                .collect { count ->
                    UNUserNotificationCenter.currentNotificationCenter().setBadgeCount(count.toLong()) { error ->
                        if (error != null) logger.w { "Odznak se nepodařilo nastavit: ${error.localizedDescription}" }
                    }
                }
        }
    }

    private fun removeDelivered(readChats: Set<Long>) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.getDeliveredNotificationsWithCompletionHandler { delivered ->
            val ids = delivered.orEmpty()
                .filterIsInstance<UNNotification>()
                // Připomínka odloženého chatu přichází k chatu, který bývá přečtený — ta zůstává.
                .filter { !it.request.identifier.startsWith(SNOOZE_REMINDER_PREFIX) }
                .filter { pushChatId(it.request.content.userInfo) in readChats }
                .map { it.request.identifier }
            if (ids.isNotEmpty()) {
                center.removeDeliveredNotificationsWithIdentifiers(ids)
                logger.d { "Uklizeno ${ids.size} notifikací přečtených chatů." }
            }
        }
    }

    private companion object {
        const val SETTLE_MS = 1_000L
    }
}
