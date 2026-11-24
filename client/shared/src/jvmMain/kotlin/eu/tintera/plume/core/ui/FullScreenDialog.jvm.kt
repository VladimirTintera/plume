package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Dialog bez vlastního rozměru a i přes pruh titulku okna (`DesktopTitleBar` ho hlásí jako horní
 * inset); obsah si insety bere sám, jako na iOS.
 */
@Composable
actual fun FullScreenDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, usePlatformInsets = false),
        content = content,
    )
}
