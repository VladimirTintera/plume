package eu.tintera.plume.wear

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.FailureConfirmationDialog
import androidx.wear.compose.material3.SuccessConfirmationDialog
import androidx.wear.compose.material3.confirmationDialogCurvedText

/**
 * Potvrzení po akci (M3 Expressive pro hodinky): velká ikona a krátký text po obvodu displeje,
 * po pár vteřinách zmizí samo ([onDismiss]). Text musí být pár slov — po obvodu se delší věta
 * nevejde; vysvětlení patří do obrazovky, ne sem.
 */
@Composable
fun SuccessConfirmation(visible: Boolean, text: String, onDismiss: () -> Unit) {
    // Styl se čte v kompozici, slot zakřiveného textu už composable není.
    val style = ConfirmationDialogDefaults.curvedTextStyle
    SuccessConfirmationDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        curvedText = { confirmationDialogCurvedText(text, style) },
    )
}

/** Totéž pro nezdar: přeškrtnutá ikona v chybové barvě. */
@Composable
fun FailureConfirmation(visible: Boolean, text: String, onDismiss: () -> Unit) {
    val style = ConfirmationDialogDefaults.curvedTextStyle
    FailureConfirmationDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        curvedText = { confirmationDialogCurvedText(text, style) },
    )
}
