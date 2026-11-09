package eu.tintera.plume.core.notifications

import eu.tintera.plume.core.accounts.AccountSlot
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.navigation.ChatOpenRequests
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.notification_mark_read
import eu.tintera.plume.resources.notification_mute_hour
import eu.tintera.plume.resources.notification_reply
import eu.tintera.plume.resources.notification_reply_failed
import eu.tintera.plume.resources.notification_reply_send
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.Koin
import platform.Foundation.NSUUID
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationActionIcon
import platform.UserNotifications.UNNotificationActionOptionNone
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationCategoryOptionNone
import platform.UserNotifications.UNNotificationDefaultActionIdentifier
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNTextInputNotificationAction
import platform.UserNotifications.UNTextInputNotificationResponse
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Akce z notifikace na iOS: kategorie s akcemi a obsluha toho, na co uživatel v notifikaci ťukl.
 *
 * Notifikace přicházejí z APNs (viz [IosPush]); kategorii a klíče z [NotificationCategories]
 * doplní rozšíření notifikací (`NotificationService.swift`). Když neběží, akce nejsou, ale
 * ťuknutí chat otevře i tak — [pushInfo] čte i holý push od Telegramu. Ověřit to jde bez APNs:
 * `xcrun simctl push` doručí na simulátor notifikaci (postup v docs/poznatky/notifikace.md, „Akce z notifikace").
 *
 * **Delegát se nastavuje při startu, v `initKoin`**, ne až se složí UI. Ťuknutí na akci umí
 * spustit ukončenou aplikaci na pozadí a iOS odpověď doručí jen delegátovi, který existuje
 * dřív, než aplikace doběhne `didFinishLaunching` — `App.init` v SwiftUI je ještě před tím.
 */
object IosNotificationCenter {

    private val logger = Logger.withTag("NotificationAction")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** `UNUserNotificationCenter` si delegáta drží jen slabě — bez téhle reference by zmizel. */
    private var delegate: Delegate? = null

    fun install(koin: Koin) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        delegate = Delegate(koin).also { center.delegate = it }
        // Popisky se čtou z prostředků (`suspend`), kategorie se tedy registrují až v korutině.
        // Na delegátovi to nezávisí: odpověď na akci se doručí i bez nich.
        scope.launch { center.setNotificationCategories(categories()) }
    }

    private suspend fun categories(): Set<UNNotificationCategory> {
        /*
         * Popisky se načtou DŘÍV, než začne volání ObjC. `getString` je `suspend`; uvnitř
         * argumentů ObjC metody Kotlin/Native převede už vyhodnocené argumenty na NSString,
         * korutina se uspí, pokračuje na jiném vlákně a autorelease pool je mezitím uvolní —
         * aplikace při startu padala na `objc_retain` v `actionWithIdentifier` (ověřeno pádem
         * na simulátoru; test bez suspendu v argumentech prošel).
         */
        val replyTitle = readString(Res.string.notification_reply)
        val sendTitle = readString(Res.string.notification_reply_send)
        val markReadTitle = readString(Res.string.notification_mark_read)
        val muteTitle = readString(Res.string.notification_mute_hour)

        val reply = UNTextInputNotificationAction.actionWithIdentifier(
            identifier = NotificationCategories.ACTION_REPLY,
            title = replyTitle,
            options = UNNotificationActionOptionNone,
            icon = UNNotificationActionIcon.iconWithSystemImageName("arrowshape.turn.up.left"),
            textInputButtonTitle = sendTitle,
            textInputPlaceholder = "",
        )
        val markRead = UNNotificationAction.actionWithIdentifier(
            identifier = NotificationCategories.ACTION_MARK_READ,
            title = markReadTitle,
            options = UNNotificationActionOptionNone,
            icon = UNNotificationActionIcon.iconWithSystemImageName("checkmark.message"),
        )
        val mute = UNNotificationAction.actionWithIdentifier(
            identifier = NotificationCategories.ACTION_MUTE,
            title = muteTitle,
            options = UNNotificationActionOptionNone,
            icon = UNNotificationActionIcon.iconWithSystemImageName("bell.slash"),
        )
        val byId = mapOf(
            NotificationCategories.ACTION_REPLY to reply,
            NotificationCategories.ACTION_MARK_READ to markRead,
            NotificationCategories.ACTION_MUTE to mute,
        )
        return NotificationCategories.all.map { (id, actions) ->
            UNNotificationCategory.categoryWithIdentifier(
                identifier = id,
                actions = actions.map { byId.getValue(it) },
                intentIdentifiers = emptyList<Any>(),
                options = UNNotificationCategoryOptionNone,
            )
        }.toSet()
    }

    private class Delegate(private val koin: Koin) : NSObject(), UNUserNotificationCenterDelegateProtocol {

        @OptIn(BetaInteropApi::class)
        @ObjCSignatureOverride
        override fun userNotificationCenter(
            center: UNUserNotificationCenter,
            didReceiveNotificationResponse: UNNotificationResponse,
            withCompletionHandler: () -> Unit,
        ) {
            val response = didReceiveNotificationResponse
            val content = response.notification.request.content
            val info = pushInfo(content.userInfo)
            if (info == null) {
                withCompletionHandler()
                return
            }
            val chatId = info.chatId
            val messageId = info.messageId
            val isGroup = info.isGroup

            // Ťuknutí na samotnou notifikaci otevře chat; aplikace jde do popředí sama.
            if (response.actionIdentifier == UNNotificationDefaultActionIdentifier) {
                koin.get<ChatOpenRequests>().request(koin.get<AccountSlot>(), chatId)
                withCompletionHandler()
                return
            }

            // Akce bez otevření aplikace: iOS ji na pozadí nechá běžet jen chvíli. Úloha na
            // pozadí ten čas prodlouží (zhruba na půl minuty) — obdoba `goAsync` na Androidu.
            val application = UIApplication.sharedApplication
            var task = UIBackgroundTaskInvalid
            task = application.beginBackgroundTaskWithExpirationHandler {
                application.endBackgroundTask(task)
                task = UIBackgroundTaskInvalid
            }
            val text = (response as? UNTextInputNotificationResponse)?.userText?.trim()

            scope.launch {
                try {
                    val actions = koin.get<NotificationActions>()
                    when (response.actionIdentifier) {
                        NotificationCategories.ACTION_REPLY -> if (!text.isNullOrEmpty()) {
                            reply(actions, chatId, text, replyTo = messageId.takeIf { isGroup }, content = response.notification)
                        }
                        NotificationCategories.ACTION_MARK_READ -> actions.markRead(chatId, messageId, READY_TIMEOUT)
                            .onSuccess { logger.d { "Chat $chatId z notifikace přečten." } }
                        NotificationCategories.ACTION_MUTE -> actions.mute(chatId, READY_TIMEOUT)
                            .onSuccess { logger.d { "Chat $chatId z notifikace ztlumen na ${NotificationActions.MUTE_DURATION}." } }
                    }
                    // Přečtení a ztlumení TDLib potvrdí lokálně hned, na server je posílá až potom.
                    // Po completion handleru iOS proces uspí a zabitý uspaný proces se k tomu už
                    // nedostane — chat zůstal nepřečtený (ověřeno na simulátoru: studený start,
                    // akce, ukončení). Odpověď na server čeká sama (`await`), tahle dvě ne.
                    if (response.actionIdentifier != NotificationCategories.ACTION_REPLY) delay(SYNC_GRACE)
                } finally {
                    withCompletionHandler()
                    if (task != UIBackgroundTaskInvalid) application.endBackgroundTask(task)
                }
            }
        }

        /**
         * Notifikace v popředí: ukázat ji. Bez tohohle ji iOS u aplikace v popředí zahodí,
         * a Android je taky ukazuje — co zobrazit, rozhoduje TDLib, ne to, jestli je aplikace
         * otevřená.
         */
        @ObjCSignatureOverride
        override fun userNotificationCenter(
            center: UNUserNotificationCenter,
            willPresentNotification: UNNotification,
            withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
        ) {
            // Přečtení a smazání jinde, obnova odznaku — nic k ukázání.
            val locKey = telegramPush(willPresentNotification.request.content.userInfo)?.locKey
            if (locKey in TelegramPush.SERVICE_KEYS) {
                withCompletionHandler(0uL)
                return
            }
            withCompletionHandler(
                UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionList or UNNotificationPresentationOptionSound,
            )
        }

        private suspend fun reply(actions: NotificationActions, chatId: Long, text: String, replyTo: Long?, content: UNNotification) {
            val started = TimeSource.Monotonic.markNow()
            val reply = actions.send(chatId, text, replyTo, READY_TIMEOUT).getOrElse {
                showReplyFailed(content, text)
                return
            }
            when (actions.await(reply, BUDGET - started.elapsedNow())) {
                ReplyState.Sent -> logger.d { "Odpověď z notifikace odešla za ${started.elapsedNow().inWholeMilliseconds} ms." }
                ReplyState.Failed -> showReplyFailed(content, text)
                // Úlohu, která by na potvrzení počkala déle, iOS nemá; zprávu drží TDLib a došle
                // ji při příštím běhu aplikace.
                ReplyState.Pending -> logger.i { "Odpověď z notifikace se nestihla potvrdit, dojde s příštím během." }
            }
        }

        /**
         * „Neodesláno: …" jako nová notifikace ve vlákně téhož chatu. Původní notifikaci iOS po
         * akci zavře a text odpovědi by se jinak ztratil.
         */
        private suspend fun showReplyFailed(original: UNNotification, text: String) {
            val source = original.request.content
            val content = UNMutableNotificationContent().apply {
                setTitle(source.title)
                setBody(readString(Res.string.notification_reply_failed, text))
                setThreadIdentifier(source.threadIdentifier)
                setCategoryIdentifier(source.categoryIdentifier)
                setUserInfo(source.userInfo)
            }
            val request = UNNotificationRequest.requestWithIdentifier(NSUUID().UUIDString, content, null)
            UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request, null)
        }
    }

    /** Na rozběhnutí TDLib, když akce spustila ukončenou aplikaci. */
    private val READY_TIMEOUT = 5.seconds

    /**
     * Kolik času dostane TDLib na odeslání přečtení / ztlumení na server. Stav spojení se pro
     * rozhodování použít nesmí (smí jen jako popisek), proto pevná doba — pár vteřin z půlminuty,
     * kterou dává úloha na pozadí.
     */
    private val SYNC_GRACE = 3.seconds

    /** Úloha na pozadí dostane asi 30 s; rezerva na zavolání completion handleru. */
    private val BUDGET = 25.seconds
}
