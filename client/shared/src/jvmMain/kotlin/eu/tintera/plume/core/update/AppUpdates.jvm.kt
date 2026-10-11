package eu.tintera.plume.core.update

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Desktop se zatím z obchodu neinstaluje, aktualizaci nemá kdo nabídnout. */
actual class AppUpdates {

    actual val state: StateFlow<AppUpdateState> = MutableStateFlow(AppUpdateState.None).asStateFlow()

    actual fun check() = Unit

    actual fun complete() = Unit
}

@Composable
actual fun rememberStartUpdate(updates: AppUpdates): () -> Unit = {}
