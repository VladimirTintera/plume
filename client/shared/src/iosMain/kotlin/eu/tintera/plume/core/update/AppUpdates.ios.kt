package eu.tintera.plume.core.update

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * iOS API na aktualizace nemá. Až bude aplikace v App Store, dá se verze zjistit dotazem na
 * `itunes.apple.com/lookup?bundleId=…` a karta otevře stránku aplikace (viz docs/roadmap.md).
 */
actual class AppUpdates {

    actual val state: StateFlow<AppUpdateState> = MutableStateFlow(AppUpdateState.None).asStateFlow()

    actual fun check() = Unit

    actual val canCheck: Boolean = false

    actual suspend fun checkNow(): UpdateCheckResult = UpdateCheckResult.Unsupported

    actual fun complete() = Unit
}

@Composable
actual fun rememberStartUpdate(updates: AppUpdates): () -> Unit = {}
