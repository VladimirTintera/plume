package eu.tintera.plume.core.notifications

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import eu.tintera.plume.core.telegram.NotificationChannelKind

actual class NotificationPermission(private val context: Context) {

    actual fun isGranted(): Boolean {
        // Před Androidem 13 se o notifikace nežádalo — byly povolené rovnou.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true

        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    actual fun openSystemSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            // Spouštíme mimo activity kontext (z Koinu drženého Application), takže je NEW_TASK nutný.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }

    actual val supportsChannelSettings: Boolean = true

    actual fun openChannelSettings(channel: NotificationChannelKind) {
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channel.channelId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }

    actual fun openConversationSettings(chatId: Long, title: String, channel: NotificationChannelKind) {
        val conversationId = ensureConversationChannel(context, channel, chatId, title)
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channel.channelId)
            .apply { if (conversationId != null) putExtra(Settings.EXTRA_CONVERSATION_ID, conversationId) }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }

    /** Bylo už vysvětlení ukázané? Ptát se při každém spuštění by bylo otravné. */
    internal fun wasAsked(): Boolean = prefs().getBoolean(KEY_ASKED, false)

    internal fun markAsked() {
        prefs().edit().putBoolean(KEY_ASKED, true).apply()
    }

    /**
     * SharedPreferences schválně, ne DataStore: jde o jediný boolean, který má smysl jen na
     * Androidu. Zavádět kvůli tomu perzistentní vrstvu přes všechny platformy by bylo nepoměrné.
     */
    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private companion object {
        const val PREFS = "notification_permission"
        const val KEY_ASKED = "asked"
    }
}
