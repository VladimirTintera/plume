package eu.tintera.plume.core.notifications

import android.app.ActivityManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import co.touchlab.kermit.Logger
import eu.tintera.plume.client.R
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.background_data_notification_text
import eu.tintera.plume.resources.background_data_notification_title
import kotlin.time.Duration.Companion.days

/**
 * Plume nemá data na pozadí: na mobilní síti mu systém v pozadí síť zavře, push přijde (přes
 * služby Google), ale TDLib se k serveru nedostane — hovor nezazvoní a zprávy přijdou až po
 * otevření aplikace. Na Foldu takhle propadly dva hovory (`docs/poznatky/notifikace.md`,
 * „Upozornění na zablokovaná data na pozadí").
 *
 * **Stav aplikace veřejné API neřekne** (`REJECT_METERED_BACKGROUND` je v `NetworkPolicyManager`,
 * skrytém). Prozradí ho až push: v pozadí na měřené síti hlásí `activeNetworkInfo` stav
 * `BLOCKED`. Dočasná výjimka z vysoké priority FCM ruší Doze a standby, ne uživatelské omezení —
 * takže `BLOCKED` při pushi znamená vypnutá data na pozadí, nebo spořič dat bez výjimky. Obojí
 * se povoluje na téže obrazovce nastavení aplikace.
 */
object BackgroundDataWarning {

    private val logger = Logger.withTag("Push")

    /**
     * Stav při pushi: `true` = síť je aplikaci zablokovaná, `false` = prošla, `null` = nejde
     * poznat (aplikace je v popředí — tam síť jde vždycky —, nebo síť není měřená či žádná).
     */
    fun check(context: Context): Boolean? = runCatching {
        if (inForeground()) return null
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        val info = connectivity.activeNetworkInfo ?: return null
        if (!connectivity.isActiveNetworkMetered) return null
        @Suppress("DEPRECATION")
        info.detailedState == NetworkInfo.DetailedState.BLOCKED
    }.getOrNull()

    /**
     * Zapíše zjištění a, je-li co hlásit, ukáže notifikaci — nejvýš jednou za [NOTIFY_INTERVAL],
     * ať každý push nevyskakuje znovu. Vrací `true`, když ji ukázal.
     */
    suspend fun report(context: Context, permission: NotificationPermission, blocked: Boolean): Boolean {
        permission.recordBackgroundData(blocked)
        // Data zase jdou: staré upozornění by v liště tvrdilo něco, co už neplatí.
        if (!blocked) runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
        if (!blocked || !permission.isGranted()) return false
        if (!permission.claimBackgroundDataNotification(NOTIFY_INTERVAL.inWholeMilliseconds)) return false
        val notification = NotificationCompat.Builder(context, CALLS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(readString(Res.string.background_data_notification_title))
            .setContentText(readString(Res.string.background_data_notification_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(readString(Res.string.background_data_notification_text)))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    REQUEST_CODE,
                    settingsIntent(context),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()
        return runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
            .onFailure { logger.w(it) { "Upozornění na data na pozadí nejde ukázat." } }
            .isSuccess
    }

    /** Nastavení dat aplikace: „Data na pozadí" a „Neomezené využití dat" (výjimka ze spořiče). */
    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun inForeground(): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        // Služba v popředí (hovor, poloha) má síť taky — nic by se z ní nepoznalo.
        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE
    }

    private val NOTIFY_INTERVAL = 7.days
    private const val NOTIFICATION_ID = 0x0BDA7A
    private const val REQUEST_CODE = 0x0BDA
}
