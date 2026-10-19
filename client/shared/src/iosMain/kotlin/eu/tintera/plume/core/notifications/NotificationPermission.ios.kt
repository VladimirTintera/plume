package eu.tintera.plume.core.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.NotificationChannelKind
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.compose.koinInject
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenNotificationSettingsURLString
import platform.UIKit.registerForRemoteNotifications
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatus
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.concurrent.Volatile
import kotlin.coroutines.resume

/**
 * Oprávnění k notifikacím na iOS.
 *
 * **Stav se zjišťuje jen asynchronně** (`getNotificationSettings`), kdežto [isGranted] je
 * synchronní: vrací poslední známý stav a zároveň ho obnoví. Nastavení ho čte při každém
 * návratu na obrazovku, takže změna v systému se tam projeví nanejvýš o jeden návrat později.
 */
actual class NotificationPermission {

    private val logger = Logger.withTag("NotificationPermission")

    @Volatile
    private var status: UNAuthorizationStatus = UNAuthorizationStatusNotDetermined

    init {
        refresh()
    }

    actual fun isGranted(): Boolean {
        refresh()
        return status.isGranted()
    }

    actual fun openSystemSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenNotificationSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }

    /** iOS kanály nemá; zvuk se nastavuje pro celou aplikaci v systému. */
    actual val hasSystemPermission: Boolean = true

    actual val supportsChannelSettings: Boolean = false

    /** Notifikace z APNs ukazuje systém a rozšíření notifikací zvuk neovládne podle času; obdobou je režim Soustředění. */
    actual val supportsQuietHours: Boolean = false
    actual fun openChannelSettings(channel: NotificationChannelKind, soundId: Long) = Unit

    actual fun forgetTone(soundId: Long) = Unit

    /** Výzvu hovoru tu systém nepovoluje zvlášť. */
    actual fun fullScreenCallsAllowed(): Boolean? = null

    actual fun openFullScreenCallSettings() = Unit
    actual fun openConversationSettings(chatId: Long, title: String, channel: NotificationChannelKind, soundId: Long) = Unit

    private fun refresh() {
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            if (settings != null) status = settings.authorizationStatus
        }
    }

    internal suspend fun currentStatus(): UNAuthorizationStatus = suspendCancellableCoroutine { continuation ->
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            val current = settings?.authorizationStatus ?: UNAuthorizationStatusNotDetermined
            status = current
            continuation.resume(current)
        }
    }

    /** Systémový dotaz. Po souhlasu se aplikace rovnou zaregistruje u APNs. */
    internal suspend fun request(): Boolean = suspendCancellableCoroutine { continuation ->
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { granted, error ->
            if (error != null) logger.w { "Žádost o notifikace selhala: ${error.localizedDescription}" }
            refresh()
            if (granted) registerForRemoteNotifications()
            continuation.resume(granted)
        }
    }

    /**
     * Bylo už vysvětlení ukázané? „Teď ne" nechá systémový stav nerozhodnutý, takže se to musí
     * pamatovat zvlášť — jinak by dialog vyskakoval při každém spuštění.
     */
    internal fun wasAsked(): Boolean = NSUserDefaults.standardUserDefaults.boolForKey(KEY_ASKED)

    internal fun markAsked() = NSUserDefaults.standardUserDefaults.setBool(true, forKey = KEY_ASKED)

    private companion object {
        const val KEY_ASKED = "notification_permission_asked"
    }
}

internal fun UNAuthorizationStatus.isGranted(): Boolean =
    this == UNAuthorizationStatusAuthorized || this == UNAuthorizationStatusProvisional || this == UNAuthorizationStatusEphemeral

/**
 * Registrace u APNs. Token přijde do `AppDelegate` (Swift) a odtud do [IosPush]. Musí běžet na
 * hlavním vlákně a opakovat se při každém startu — iOS token může kdykoli vyměnit.
 */
internal fun registerForRemoteNotifications() {
    dispatch_async(dispatch_get_main_queue()) {
        UIApplication.sharedApplication.registerForRemoteNotifications()
    }
}

/**
 * Stejný postup jako na Androidu: napřed vlastní vysvětlení, systémový dotaz až po „Zapnout".
 * Ptá se jen jednou — iOS systémový dotaz stejně ukáže jen jednou napořád, pak vede cesta přes
 * Nastavení.
 */
@Composable
actual fun NotificationPermissionPrompt() {
    val permission = koinInject<NotificationPermission>()
    val scope = rememberCoroutineScope()
    var showRationale by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (permission.currentStatus() == UNAuthorizationStatusNotDetermined && !permission.wasAsked()) showRationale = true
    }

    if (!showRationale) return

    NotificationRationaleDialog(
        onEnable = {
            permission.markAsked()
            showRationale = false
            scope.launch { permission.request() }
        },
        onDismiss = {
            permission.markAsked()
            showRationale = false
        },
    )
}
