package eu.tintera.plume.core.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import co.touchlab.kermit.Logger

/**
 * Stav se čte z `DomainVerificationManager` a znovu při každém návratu do aplikace — uživatel
 * ho mění v systémovém nastavení, kam ho řádek pošle, a zpráva o změně nechodí.
 */
@Composable
actual fun rememberTelegramLinkHandling(): TelegramLinkHandling? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    var enabled by remember(context) { mutableStateOf(context.telegramLinksEnabled()) }
    LifecycleResumeEffect(context) {
        enabled = context.telegramLinksEnabled()
        onPauseOrDispose { }
    }
    return TelegramLinkHandling(enabled) { context.openLinkSettings() }
}

/**
 * Povolené = uživatel doménu vybral (`SELECTED`), nebo ji systém ověřil (`VERIFIED` — u `t.me`
 * se nestane, ale kdyby ano, odkazy Plume dostává taky).
 */
@RequiresApi(Build.VERSION_CODES.S)
private fun Context.telegramLinksEnabled(): Boolean {
    val manager = getSystemService(DomainVerificationManager::class.java) ?: return false
    val state = runCatching { manager.getDomainVerificationUserState(packageName) }
        .onFailure { Logger.withTag("TelegramLinks").w(it) { "Stav odkazů se nepodařilo přečíst." } }
        .getOrNull() ?: return false
    if (!state.isLinkHandlingAllowed) return false
    return when (state.hostToStateMap[TELEGRAM_HOST]) {
        DomainVerificationUserState.DOMAIN_STATE_SELECTED, DomainVerificationUserState.DOMAIN_STATE_VERIFIED -> true
        else -> false
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun Context.openLinkSettings() {
    val intent = Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, "package:$packageName".toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Upravené systémy obrazovku „Otevírat ve výchozím nastavení" nemusí mít; pak aspoň detail aplikace.
        Logger.withTag("TelegramLinks").w(e) { "Nastavení odkazů nejde otevřít." }
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/**
 * Rozhoduje `t.me`: na ní jsou kódy z Plume i z oficiálních klientů. `telegram.me`
 * a `telegram.dog` jsou staré podoby a systém je v nastavení nabízí každou zvlášť.
 */
private const val TELEGRAM_HOST = "t.me"
