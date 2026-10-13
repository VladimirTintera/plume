package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.navigation.ChatOpenRequests
import eu.tintera.plume.core.notifications.DesktopNotification
import eu.tintera.plume.core.notifications.DesktopNotifier
import eu.tintera.plume.core.notifications.NotificationActions
import eu.tintera.plume.core.notifications.NotificationButtons
import eu.tintera.plume.core.notifications.ReplyState
import eu.tintera.plume.core.notifications.ToastActivation
import eu.tintera.plume.core.platform.DesktopTaskbar
import eu.tintera.plume.core.platform.DesktopWindow
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.snooze.ChatSnoozes
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.notification_call_unknown
import eu.tintera.plume.resources.notification_call_video
import eu.tintera.plume.resources.notification_call_voice
import eu.tintera.plume.resources.notification_mark_read
import eu.tintera.plume.resources.notification_mute_hour
import eu.tintera.plume.resources.notification_reply
import eu.tintera.plume.resources.notification_reply_failed
import eu.tintera.plume.resources.notification_reply_send
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Notifikace na desktopu: TDLib je vydává, dokud aplikace běží (push tu není), a presenter je
 * předává systému přes [DesktopNotifier] — na macOS `UNUserNotificationCenter`, na Windows
 * toasty WinRT, na Linuxu AWT.
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
 * - **Odznak** je počet nepřečtených zpráv v neztlumených chatech hlavního seznamu, stejně jako
 *   na iOS: v Docku (macOS), na tlačítku okna (Windows). Viz [DesktopTaskbar].
 * - **Tlačítko okna na Windows blikne** u oznámení se zvukem, když okno není aktivní.
 * - **Tlačítka toastu na Windows** (odpovědět, přečíst, ztlumit na hodinu) dělají totéž co akce
 *   notifikace na Androidu, přes sdílené [NotificationActions]. Běží na pozadí, okno se
 *   nevytahuje; nepovedená odpověď se ohlásí dalším toastem.
 */
actual class NotificationPresenter internal constructor(
    private val chats: ChatRepository,
    private val settings: AppSettings,
    private val chatOpenRequests: ChatOpenRequests,
    private val notifier: DesktopNotifier,
    private val scope: CoroutineScope,
    /** Odložené chaty: dokud spí, přijdou jejich oznámení potichu. */
    private val snoozes: ChatSnoozes? = null,
    /** Tlačítka toastu; bez nich se tlačítka nenabízejí. */
    private val actions: NotificationActions? = null,
) {

    private val logger = Logger.withTag("Notifications")

    /**
     * Co už TDLib vydal, podle `groupId` — ať se nic neukáže dvakrát a ať je co rušit. Sahá se
     * sem jen z [dispatcher].
     */
    private val known = mutableMapOf<Int, MutableSet<Int>>()

    /** Skupiny notifikací příchozích hovorů; zruší je [NotificationUpdate.Removed] s týmž `groupId`. */
    private val calls = mutableSetOf<Int>()

    /** Názvy chatů z ukázaných oznámení — pro toast o neodeslané odpovědi. Jen z [dispatcher]. */
    private val titles = mutableMapOf<Long, String>()

    private val dispatcher = Dispatchers.Default.limitedParallelism(1)

    actual fun start(repository: NotificationRepository) {
        notifier.onActivated = { activation ->
            when (activation) {
                is ToastActivation.Open -> {
                    logger.d { "Klik na oznámení: chat ${activation.chatId}, téma ${activation.topicId}." }
                    chatOpenRequests.request(activation.chatId, activation.topicId)
                    DesktopWindow.activate()
                }
                else -> scope.launch { act(activation) }
            }
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

    private suspend fun add(notifications: List<ChatNotification>) {
        val labels = if (actions != null) buttonLabels() else null
        var attention = false
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
            if (!newest.isSilent && !quiet) attention = true
            titles[newest.chatId] = newest.chatTitle
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
                    imagePath = newest.imagePath,
                    avatarPath = newest.chatAvatarPath,
                    timestamp = newest.timestamp.toLong(),
                    buttons = labels?.let {
                        NotificationButtons(
                            messageId = newest.messageId,
                            // Ve skupině odpověď cituje, komu odpovídá; v soukromém chatu je to jasné.
                            quoteMessageId = newest.messageId.takeIf { newest.senderName != null },
                            canReply = newest.canReply,
                            canMute = newest.canMute,
                            labels = it,
                        )
                    },
                ),
            )
        }
        // Jednou za dávku, ne za každý chat.
        if (attention) DesktopTaskbar.requestAttention()
    }

    private suspend fun buttonLabels() = NotificationButtons.Labels(
        reply = readString(Res.string.notification_reply),
        send = readString(Res.string.notification_reply_send),
        markRead = readString(Res.string.notification_mark_read),
        mute = readString(Res.string.notification_mute_hour),
    )

    /**
     * Tlačítko toastu. Aplikace běží (jinak by aktivátor nebyl), ale po studeném startu z Centra
     * oznámení se TDLib teprve přihlašuje — proto štědré čekání. Po odeslání se čeká na
     * potvrzení a chat se přečte ([NotificationActions.await]); TDLib pak toast stáhne sám.
     */
    private suspend fun act(activation: ToastActivation) {
        val actions = actions ?: return
        when (activation) {
            is ToastActivation.Reply -> {
                logger.d { "Odpověď z toastu do chatu ${activation.chatId}." }
                val reply = actions.send(
                    chatId = activation.chatId,
                    text = activation.text,
                    replyToMessageId = activation.quoteMessageId,
                    timeout = READY_TIMEOUT,
                    topicId = activation.topicId,
                ).getOrElse {
                    replyFailed(activation)
                    return
                }
                when (actions.await(reply, DELIVERY_TIMEOUT)) {
                    ReplyState.Sent -> logger.d { "Odpověď z toastu odešla." }
                    ReplyState.Failed -> replyFailed(activation)
                    // Proces běží dál a TDLib zprávu došle; nepřečtený chat je menší zlo.
                    ReplyState.Pending -> logger.i { "Odpověď z toastu se zatím nepotvrdila, došle ji TDLib." }
                }
            }
            is ToastActivation.MarkRead -> actions.markRead(activation.chatId, activation.messageId, READY_TIMEOUT)
                .onSuccess { logger.d { "Chat ${activation.chatId} přečten z toastu." } }
            is ToastActivation.Mute -> actions.mute(activation.chatId, READY_TIMEOUT)
                .onSuccess { logger.d { "Chat ${activation.chatId} ztlumen z toastu." } }
            is ToastActivation.Open -> Unit
        }
    }

    private suspend fun replyFailed(reply: ToastActivation.Reply) {
        val title = withContext(dispatcher) { titles[reply.chatId] } ?: return
        notifier.show(
            DesktopNotification(
                id = "reply-failed-${reply.chatId}-${Clock.System.now().toEpochMilliseconds()}",
                thread = "chat-${reply.chatId}",
                title = title,
                subtitle = null,
                body = readString(Res.string.notification_reply_failed, reply.text),
                chatId = reply.chatId,
                topicId = reply.topicId,
                silent = false,
                imagePath = null,
                avatarPath = null,
            ),
        )
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
                imagePath = null,
                avatarPath = call.avatarPath,
            ),
        )
    }

    private fun clear() {
        known.clear()
        calls.clear()
        notifier.removeAll()
        DesktopTaskbar.setUnread(0)
    }

    @OptIn(FlowPreview::class)
    private fun startBadge() {
        DesktopTaskbar.start(scope)
        scope.launch {
            chats.chats(ChatFolder.Main)
                .filterIsInstance<ChatListState.Ready>()
                .map { state -> state.chats.filter { !it.isMuted }.sumOf { it.unreadCount } }
                .distinctUntilChanged()
                // Seznam se při startu skládá po dávkách; stačí se podívat, až se usadí.
                .debounce(BADGE_SETTLE_MS)
                .collect(DesktopTaskbar::setUnread)
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

        /** Na přihlášení TDLib po studeném startu z toastu. */
        val READY_TIMEOUT = 30.seconds

        /** Na potvrzení odpovědi od serveru. */
        val DELIVERY_TIMEOUT = 1.minutes
    }
}
