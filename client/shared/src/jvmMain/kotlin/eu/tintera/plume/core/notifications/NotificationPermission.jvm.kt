package eu.tintera.plume.core.notifications

import androidx.compose.runtime.Composable
import eu.tintera.plume.core.telegram.NotificationChannelKind

/** Notifikace desktop zatím nemá ([eu.tintera.plume.core.telegram.NotificationPresenter]). */
actual class NotificationPermission {

    actual fun isGranted(): Boolean = false

    actual fun openSystemSettings() = Unit

    actual val supportsChannelSettings: Boolean = false

    actual val supportsQuietHours: Boolean = false

    actual fun openChannelSettings(channel: NotificationChannelKind) = Unit

    actual fun openConversationSettings(chatId: Long, title: String, channel: NotificationChannelKind) = Unit
}

@Composable
actual fun NotificationPermissionPrompt() = Unit
