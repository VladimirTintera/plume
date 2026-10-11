package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState

/** Kameru pro čtení QR kódu desktop zatím nemá. */
@Composable
actual fun rememberQrScanner(
    onScanned: (String) -> Unit,
    onFailure: (QrScanFailure) -> Unit,
): () -> Unit {
    val failure = rememberUpdatedState(onFailure)
    return { failure.value(QrScanFailure.Unavailable) }
}
