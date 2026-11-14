package eu.tintera.plume.core.notifications

import eu.tintera.plume.core.accounts.AccountSlot
import android.Manifest
import android.content.Context
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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

    actual val hasSystemPermission: Boolean = true

    /**
     * Od Androidu 14 `USE_FULL_SCREEN_INTENT` u aplikace z Play jen s deklarací v Play Console
     * (hovory, budíky). Build přes adb ho má vždycky — proto chybu testovací telefony neukázaly
     * a ukázal ji až Fold s Plume z Play (`docs/poznatky/notifikace.md`).
     */
    actual fun fullScreenCallsAllowed(): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }

    actual fun openFullScreenCallSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return openSystemSettings()
        val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Výrobce, který obrazovku nemá, dostane aspoň nastavení notifikací aplikace.
        runCatching { context.startActivity(intent) }.onFailure { openSystemSettings() }
    }

    actual val supportsChannelSettings: Boolean = true

    /** Notifikace kreslí aplikace sama (`NotificationPresenter`), takže je umí ztišit. */
    actual val supportsQuietHours: Boolean = true

    /** Kanál, kterým rozsah se zvukem [soundId] zní — kanál s tónem, když už vznikl, jinak výchozí. */
    actual fun openChannelSettings(slot: AccountSlot, channel: NotificationChannelKind, soundId: Long) {
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, existingSoundChannelId(context, slot, channel, soundId))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }

    actual fun forgetTone(slot: AccountSlot, soundId: Long) = deleteToneChannels(context, slot, soundId)

    /**
     * Konverzace visí pod kanálem, kterým chat opravdu zní: chat s tónem z Telegramu jde kanálem
     * toho tónu, a kanál konverzace pod výchozím kanálem by ho minul. Kanál tónu ale vzniká až
     * s první notifikací — dokud není, nezbývá než výchozí.
     */
    actual fun openConversationSettings(slot: AccountSlot, chatId: Long, title: String, channel: NotificationChannelKind, soundId: Long) {
        val parent = existingSoundChannelId(context, slot, channel, soundId)
        val conversationId = ensureConversationChannel(context, slot, parent, chatId, title)
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, parent)
            .apply { if (conversationId != null) putExtra(Settings.EXTRA_CONVERSATION_ID, conversationId) }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }

    /** Bylo už vysvětlení ukázané? Ptát se při každém spuštění by bylo otravné. */
    internal fun wasAsked(): Boolean = prefs().getBoolean(KEY_ASKED, false)

    internal fun markAsked() {
        prefs().edit().putBoolean(KEY_ASKED, true).apply()
    }

    actual fun backgroundDataBlocked(): Boolean = prefs().getBoolean(KEY_BACKGROUND_DATA_BLOCKED, false)

    actual fun openBackgroundDataSettings() {
        // Uživatel jde stav měnit; zda to pomohlo, ukáže až další push v pozadí.
        prefs().edit().putBoolean(KEY_BACKGROUND_DATA_BLOCKED, false).apply()
        runCatching { context.startActivity(BackgroundDataWarning.settingsIntent(context)) }
            .onFailure {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
    }

    /** Výsledek kontroly při pushi ([BackgroundDataWarning.check]). */
    internal fun recordBackgroundData(blocked: Boolean) {
        prefs().edit().putBoolean(KEY_BACKGROUND_DATA_BLOCKED, blocked).apply()
    }

    /** Smí se teď ukázat notifikace o zablokovaných datech? Když ano, zapíše si, že se ukázala. */
    internal fun claimBackgroundDataNotification(intervalMs: Long): Boolean {
        val now = System.currentTimeMillis()
        if (now - prefs().getLong(KEY_BACKGROUND_DATA_NOTIFIED_AT, 0) < intervalMs) return false
        prefs().edit().putLong(KEY_BACKGROUND_DATA_NOTIFIED_AT, now).apply()
        return true
    }

    /** Ptali jsme se už na celoobrazovkové hovory? Jednou stačí, dál vede cesta přes Nastavení. */
    internal fun wasAskedFullScreen(): Boolean = prefs().getBoolean(KEY_ASKED_FULL_SCREEN, false)

    internal fun markAskedFullScreen() {
        prefs().edit().putBoolean(KEY_ASKED_FULL_SCREEN, true).apply()
    }

    /**
     * SharedPreferences schválně, ne DataStore: jde o jediný boolean, který má smysl jen na
     * Androidu. Zavádět kvůli tomu perzistentní vrstvu přes všechny platformy by bylo nepoměrné.
     */
    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private companion object {
        const val PREFS = "notification_permission"
        const val KEY_ASKED = "asked"
        const val KEY_ASKED_FULL_SCREEN = "asked_full_screen"
        const val KEY_BACKGROUND_DATA_BLOCKED = "background_data_blocked"
        const val KEY_BACKGROUND_DATA_NOTIFIED_AT = "background_data_notified_at"
    }
}
