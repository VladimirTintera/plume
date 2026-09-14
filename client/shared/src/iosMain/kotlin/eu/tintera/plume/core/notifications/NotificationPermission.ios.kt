package eu.tintera.plume.core.notifications

import androidx.compose.runtime.Composable

/**
 * iOS zatím notifikace nezobrazuje (viz `NotificationPresenter.ios`), takže není o co žádat.
 * Až přibude APNs, přijde sem `UNUserNotificationCenter.requestAuthorization`.
 */
actual class NotificationPermission {
    actual fun isGranted(): Boolean = true
    actual fun openSystemSettings() = Unit
}

@Composable
actual fun NotificationPermissionPrompt() = Unit
