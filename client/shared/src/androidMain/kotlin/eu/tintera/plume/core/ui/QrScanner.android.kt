package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import co.touchlab.kermit.Logger
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Google code scanner: skenovací obrazovku kreslí Play služby a aplikaci vrátí jen přečtený
 * text. Oprávnění ke kameře proto aplikace nepotřebuje. Modul skeneru si Play služby při prvním
 * použití stáhnou samy.
 */
@Composable
actual fun rememberQrScanner(
    onScanned: (String) -> Unit,
    onFailure: (QrScanFailure) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val currentOnScanned by rememberUpdatedState(onScanned)
    val currentOnFailure by rememberUpdatedState(onFailure)
    return remember(context) {
        val scanner = GmsBarcodeScanning.getClient(
            context,
            GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
        return@remember {
            scanner.startScan()
                .addOnSuccessListener { barcode -> barcode.rawValue?.let { currentOnScanned(it) } }
                // Zrušení chodí jako `addOnCanceledListener`, ne jako chyba — to se nehlásí.
                .addOnFailureListener { failure ->
                    Logger.withTag("QrScanner").w(failure) { "Čtečka QR kódů se nespustila" }
                    currentOnFailure(QrScanFailure.Unavailable)
                }
        }
    }
}
