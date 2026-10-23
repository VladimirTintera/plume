package eu.tintera.plume.core.webapp

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri

@Composable
actual fun rememberHomeScreenShortcuts(): HomeScreenShortcuts {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidHomeScreenShortcuts(context) }
}

/**
 * Připnutý zástupce otevře odkaz `t.me/…?startapp` přímo v Plume (`setPackage`) — stejnou
 * cestou jako odkaz zvenčí. Ikona je ikona aplikace: fotka bota by se musela napřed stáhnout.
 */
private class AndroidHomeScreenShortcuts(private val context: Context) : HomeScreenShortcuts {
    override val supported: Boolean get() = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    override fun status(id: String): String {
        if (!supported) return "unsupported"
        val pinned = ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
        return if (pinned.any { it.id == id }) "added" else "missing"
    }

    override fun add(id: String, label: String, url: String): Boolean {
        if (!supported) return false
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).setPackage(context.packageName)
        val shortcut = ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(label)
            .setIcon(IconCompat.createWithResource(context, context.applicationInfo.icon))
            .setIntent(intent)
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }
}
