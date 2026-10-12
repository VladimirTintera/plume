package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable

/** Proč se čtečka nespustila. Zrušení uživatelem chybou není — to se neohlašuje vůbec. */
enum class QrScanFailure { Unavailable, PermissionDenied }

/**
 * Čtečka QR kódů přes celou obrazovku. Vrací funkci, která ji spustí; přečtený obsah přijde
 * do [onScanned], zrušení nic nevolá.
 *
 * Na platformě, protože kamera i dekodér jsou systémové:
 * - **Android: Google code scanner** z Play služeb. Skenovací obrazovku dodá systém, takže
 *   aplikace nepotřebuje oprávnění ke kameře ani vlastní CameraX s ML Kitem. Bez Play služeb
 *   se nespustí ([QrScanFailure.Unavailable]).
 * - **iOS: AVFoundation** (`AVCaptureMetadataOutput`) ve vlastní obrazovce. VisionKit
 *   `DataScannerViewController` je jen pro Swift, z Kotlinu na něj nejde dosáhnout. Oprávnění
 *   ke kameře se žádá při prvním spuštění (`NSCameraUsageDescription`).
 */
@Composable
expect fun rememberQrScanner(
    onScanned: (String) -> Unit,
    onFailure: (QrScanFailure) -> Unit,
): () -> Unit

/** Má platforma čtečku? Desktop kameru nemá — tlačítko, které jen ohlásí chybu, se nenabízí. */
expect val qrScannerSupported: Boolean
