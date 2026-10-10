package eu.tintera.plume.features.lock

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun LockWindow(onBack: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            // Insety si zámek bere sám (`safeDrawing`), jinak by je dostal dvakrát.
            usePlatformInsets = false,
            useSoftwareKeyboardInset = false,
            scrimColor = Color.Transparent,
            animateTransition = false,
        ),
        content = content,
    )
}
