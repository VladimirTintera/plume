package eu.tintera.plume.features.lock

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Zámek přes celé okno, jako na iOS: dialog bez vlastního rozměru a bez zavření kliknutím vedle. */
@Composable
actual fun LockWindow(onBack: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            scrimColor = Color.Transparent,
        ),
        content = content,
    )
}
