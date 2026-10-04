package eu.tintera.plume.core.ui

import android.os.Build
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView

actual val popupDismissesOnBack: Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

@Composable
actual fun PopupBackHandler(enabled: Boolean, onBack: () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val view = LocalView.current
    val currentOnBack by rememberUpdatedState(onBack)
    DisposableEffect(view, enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val dispatcher = view.findOnBackInvokedDispatcher() ?: return@DisposableEffect onDispose { }
        val callback = Api33.callback { currentOnBack() }
        // Výchozí priorita: klávesnice si svůj callback registruje až po nás, takže má přednost.
        dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
        onDispose { dispatcher.unregisterOnBackInvokedCallback(callback) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
actual fun PopupBackAfterKeyboard(onBack: () -> Unit) {
    val imeVisible by rememberUpdatedState(WindowInsets.isImeVisible)
    val keyboard = LocalSoftwareKeyboardController.current
    PopupBackHandler(enabled = true) {
        if (imeVisible) keyboard?.hide() else onBack()
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object Api33 {
    fun callback(onBack: () -> Unit): OnBackInvokedCallback = OnBackInvokedCallback { onBack() }
}
