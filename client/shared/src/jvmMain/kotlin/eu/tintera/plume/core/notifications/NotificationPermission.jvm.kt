package eu.tintera.plume.core.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import eu.tintera.plume.core.platform.DesktopOs
import eu.tintera.plume.core.platform.desktopDataDirectory
import eu.tintera.plume.core.platform.desktopOs
import eu.tintera.plume.core.telegram.NotificationChannelKind
import eu.tintera.plume.core.ui.openInSystem
import java.io.File
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * Oprávnění k notifikacím na desktopu. Na macOS je to systémové povolení pro `eu.tintera.plume`
 * (Nastavení systému → Oznámení). Windows se neptá, oznámení aplikace jde jen vypnout
 * (Nastavení → Systém → Oznámení); na Linuxu stačí, že systém má oznamovací oblast.
 */
actual class NotificationPermission internal constructor(private val notifier: DesktopNotifier) {

    actual fun isGranted(): Boolean = notifier.isGranted() == true

    actual fun openSystemSettings() {
        val settings = when (desktopOs) {
            DesktopOs.MacOs -> MAC_NOTIFICATION_SETTINGS
            DesktopOs.Windows -> WINDOWS_NOTIFICATION_SETTINGS
            else -> return
        }
        runCatching { openInSystem(URI(settings)) }
    }

    /** Kanály má jen Android. */
    actual val supportsChannelSettings: Boolean = false

    /** Oznámení skládá aplikace sama, takže zvuk podle času ovládne — jako na Androidu. */
    actual val supportsQuietHours: Boolean = true

    actual fun openChannelSettings(channel: NotificationChannelKind, soundId: Long) = Unit

    actual fun forgetTone(soundId: Long) = Unit

    actual fun openConversationSettings(chatId: Long, title: String, channel: NotificationChannelKind) = Unit

    internal fun undecided(): Boolean = notifier.available && notifier.isGranted() == null

    internal suspend fun request(): Boolean = withContext(Dispatchers.IO) { notifier.requestPermission() }

    /**
     * Bylo už vysvětlení ukázané? „Teď ne" nechá systémový stav nerozhodnutý, takže se to musí
     * pamatovat zvlášť — jinak by dialog vyskakoval při každém spuštění. Značka je soubor
     * v datech aplikace, stejně jako `NSUserDefaults` na iOS.
     */
    internal fun wasAsked(): Boolean = askedMarker.exists()

    internal fun markAsked() {
        runCatching { askedMarker.createNewFile() }
    }

    private val askedMarker get() = File(desktopDataDirectory, "notification_permission_asked")

    private companion object {
        const val MAC_NOTIFICATION_SETTINGS = "x-apple.systempreferences:com.apple.Notifications-Settings.extension?id=eu.tintera.plume"

        /** Seznam aplikací; na řádek jedné aplikace odkaz nevede. */
        const val WINDOWS_NOTIFICATION_SETTINGS = "ms-settings:notifications"
    }
}

/**
 * Stejný postup jako na Androidu a iOS: napřed vlastní vysvětlení, systémový dotaz až po
 * „Zapnout". macOS systémový dotaz ukáže jen jednou napořád, pak vede cesta přes Nastavení.
 */
@Composable
actual fun NotificationPermissionPrompt() {
    val permission = koinInject<NotificationPermission>()
    val scope = rememberCoroutineScope()
    var showRationale by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val undecided = withContext(Dispatchers.IO) { permission.undecided() }
        if (undecided && !permission.wasAsked()) showRationale = true
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
