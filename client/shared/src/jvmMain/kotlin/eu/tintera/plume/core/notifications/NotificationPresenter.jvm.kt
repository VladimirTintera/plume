package eu.tintera.plume.core.notifications

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.calls.CallRequests
import eu.tintera.plume.core.navigation.ChatOpenRequests
import eu.tintera.plume.core.platform.DesktopTaskbar
import eu.tintera.plume.core.platform.DesktopWindow
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.snooze.ChatSnoozes
import eu.tintera.plume.core.telegram.ChatNotification
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.IncomingCall
import eu.tintera.plume.core.telegram.NotificationRepository
import eu.tintera.plume.core.telegram.NotificationUpdate
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.call_answer
import eu.tintera.plume.resources.notification_call_decline
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
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
 * - **Tlačítka oznámení na macOS a toastu na Windows** (odpovědět, přečíst, ztlumit na hodinu)
 *   dělají totéž co akce notifikace na Androidu, přes sdílené [NotificationActions]. Běží na
 *   pozadí, okno se nevytahuje; nepovedená odpověď se ohlásí dalším oznámením.
 * - **Příchozí hovor, který tu jde přijmout** ([callsSupported]), má Přijmout a Odmítnout
 *   (macOS i Windows) a je tichý — zvoní `DesktopCallRinger` ve smyčce. Přijmout vytáhne okno
 *   a předá hovor obrazovce hovoru ([CallRequests]), ta se zeptá na mikrofon a přijme.
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
    /** Přijetí hovoru z oznámení; bez něj oznámení hovoru tlačítka nemá. */
    private val callRequests: CallRequests? = null,
    /** Hovory tu jde přijmout (ntgcalls je přibalená). */
    private val callsSupported: Boolean = false,
) {

    private val logger = Logger.withTag("Notifications")

    /**
     * Co už TDLib vydal, podle `groupId` — ať se nic neukáže dvakrát a ať je co rušit. Sahá se
     * sem jen z [dispatcher].
     */
    private val known = mutableMapOf<Int, MutableSet<Int>>()

    /** Skupiny notifikací příchozích hovorů; zruší je [NotificationUpdate.Removed] s týmž `groupId`. */
    private val calls = mutableSetOf<Int>()

    /** Ukázané příchozí hovory podle id hovoru — Odmítnout potřebuje vědět, jestli šlo o video. */
    private val incoming = mutableMapOf<Int, IncomingCall>()

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
        if (answerable || actions != null) {
            scope.launch(dispatcher) { prepareActions(if (actions != null) buttonLabels() else null) }
        }

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
        // Popisky se mohly změnit s jazykem; na macOS jsou v kategoriích, ne v oznámení.
        if (labels != null) prepareActions(labels)
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

    /**
     * Popisky tlačítek dopředu (viz [DesktopNotifier.prepareActions]): hovoru, když ho tu jde
     * přijmout, a zpráv podle [buttons]. Znovu před každým oznámením kvůli změně jazyka;
     * notifier přepisuje, jen když se popisky liší.
     */
    private suspend fun prepareActions(buttons: NotificationButtons.Labels?) {
        notifier.prepareActions(call = if (answerable) callLabels() else null, buttons = buttons)
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
            is ToastActivation.AnswerCall -> {
                logger.d { "Přijmout hovor ${activation.callId} z oznámení." }
                callRequests?.answer(activation.callId)
                DesktopWindow.activate()
            }
            is ToastActivation.DeclineCall -> {
                val isVideo = withContext(dispatcher) { incoming[activation.callId]?.isVideo } ?: false
                actions.declineCall(activation.callId, isVideo, READY_TIMEOUT)
                    .onSuccess { logger.d { "Hovor ${activation.callId} odmítnut z oznámení." } }
                    .onFailure { logger.w(it) { "Hovor ${activation.callId} nejde odmítnout." } }
            }
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
            incoming.values.removeAll { it.groupId == update.groupId }
            notifier.remove(listOf(callId(update.groupId)))
            return
        }
        known[update.groupId]?.removeAll(update.notificationIds.toSet())
        notifier.remove(update.notificationIds.map { id(update.groupId, it) })
    }

    /**
     * Příchozí hovor. Aktivní okno oznámení nedostane — obrazovka hovoru je v něm vidět a zvoní
     * se tak jako tak. Hovor, který tu přijmout nejde, zůstává jako dřív: jen „Volá ti" a klik
     * otevře chat.
     */
    private suspend fun showCall(call: IncomingCall) {
        if (DesktopWindow.focused.value) {
            logger.d { "Okno je aktivní, příchozí hovor ${call.callId} se neoznamuje." }
            return
        }
        logger.d { "Oznamuji příchozí hovor ${call.callId}, přijmout jde tady: $answerable." }
        calls += call.groupId
        val buttons = if (answerable) {
            incoming[call.callId] = call
            prepareActions(if (actions != null) buttonLabels() else null)
            callLabels().copy(callId = call.callId)
        } else {
            null
        }
        notifier.show(
            DesktopNotification(
                id = callId(call.groupId),
                thread = "chat-${call.chatId}",
                title = call.callerName.ifBlank { readString(Res.string.notification_call_unknown) },
                subtitle = null,
                body = readString(if (call.isVideo) Res.string.notification_call_video else Res.string.notification_call_voice),
                chatId = call.chatId,
                topicId = null,
                // Zvoní DesktopCallRinger; zvuk oznámení by ho zdvojil.
                silent = buttons != null,
                imagePath = null,
                avatarPath = call.avatarPath,
                call = buttons,
            ),
        )
    }

    private val answerable: Boolean get() = callsSupported && callRequests != null && actions != null

    private suspend fun callLabels() = CallButtons(
        callId = 0,
        answer = readString(Res.string.call_answer),
        decline = readString(Res.string.notification_call_decline),
    )

    private fun clear() {
        known.clear()
        calls.clear()
        incoming.clear()
        notifier.removeAll()
        DesktopTaskbar.setUnread(0)
    }

    private fun startBadge() {
        DesktopTaskbar.start(scope)
        scope.launch {
            // Počet od TDLib, ne součet nad načtenou částí seznamu (viz `ChatRepository.unreadMessages`).
            chats.unreadMessages
                .filterNotNull()
                .distinctUntilChanged()
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
        /** Na přihlášení TDLib po studeném startu z toastu. */
        val READY_TIMEOUT = 30.seconds

        /** Na potvrzení odpovědi od serveru. */
        val DELIVERY_TIMEOUT = 1.minutes
    }
}
