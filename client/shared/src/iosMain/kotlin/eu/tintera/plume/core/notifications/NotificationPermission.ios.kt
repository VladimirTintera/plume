package eu.tintera.plume.core.notifications

import androidx.compose.runtime.Composable
import eu.tintera.plume.core.telegram.NotificationChannelKind

/**
 * iOS zatím notifikace nezobrazuje (viz `NotificationPresenter.ios`), takže není o co žádat.
 * Až přibude APNs, přijde sem `UNUserNotificationCenter.requestAuthorization`.
 */
actual class NotificationPermission {
    actual fun isGranted(): Boolean = true
    actual fun openSystemSettings() = Unit
    actual val supportsChannelSettings: Boolean = false
    actual fun openChannelSettings(channel: NotificationChannelKind) = Unit
}

@Composable
actual fun NotificationPermissionPrompt() = Unit
