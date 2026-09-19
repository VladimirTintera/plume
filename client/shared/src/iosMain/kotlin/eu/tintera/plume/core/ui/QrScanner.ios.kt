package eu.tintera.plume.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_close
import eu.tintera.plume.resources.devices_qr_hint
import kotlinx.cinterop.ExperimentalForeignApi
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectMake
import platform.QuartzCore.CATransaction
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue

/**
 * Čtečka nad AVFoundation: náhled kamery přes celou obrazovku a `AVCaptureMetadataOutput`
 * omezený na QR kódy. Oprávnění ke kameře se žádá až při spuštění.
 *
 * Simulátor kameru nemá, takže tam skončí jako [QrScanFailure.Unavailable].
 */
@Composable
actual fun rememberQrScanner(
    onScanned: (String) -> Unit,
    onFailure: (QrScanFailure) -> Unit,
): () -> Unit {
    var open by remember { mutableStateOf(false) }
    val currentOnScanned by rememberUpdatedState(onScanned)
    val currentOnFailure by rememberUpdatedState(onFailure)

    if (open) {
        QrScannerDialog(
            onScanned = { content ->
                open = false
                currentOnScanned(content)
            },
            onUnavailable = {
                open = false
                currentOnFailure(QrScanFailure.Unavailable)
            },
            onDismiss = { open = false },
        )
    }

    return remember {
        {
            when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
                AVAuthorizationStatusAuthorized -> open = true
                AVAuthorizationStatusNotDetermined ->
                    AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                        // Odpověď chodí na libovolném vlákně; stav kompozice se mění z hlavního.
                        dispatch_async(dispatch_get_main_queue()) {
                            if (granted) open = true else currentOnFailure(QrScanFailure.PermissionDenied)
                        }
                    }
                // Odmítnuté nebo omezené — dialog se znovu neukáže, změnit to jde jen v Nastavení.
                else -> currentOnFailure(QrScanFailure.PermissionDenied)
            }
        }
    }
}

@Composable
private fun QrScannerDialog(
    onScanned: (String) -> Unit,
    onUnavailable: () -> Unit,
    onDismiss: () -> Unit,
) {
    val currentOnScanned by rememberUpdatedState(onScanned)
    // Delegát i relace se drží silnou referencí po celou dobu dialogu — AVFoundation drží
    // delegáta jen slabě a bez toho by ho uklidil garbage collector (stejná past jako u výběrů).
    val scanner = remember {
        var delivered = false
        QrCaptureSession { content ->
            // Kód je v záběru desítky snímků po sobě; výsledek patří ven jen jednou.
            if (!delivered) {
                delivered = true
                currentOnScanned(content)
            }
        }
    }

    DisposableEffect(scanner) {
        if (scanner.session == null) onUnavailable() else scanner.start()
        onDispose { scanner.stop() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            scanner.session?.let { session ->
                UIKitView(
                    factory = { CameraPreviewView(session) },
                    modifier = Modifier.fillMaxSize(),
                    properties = UIKitInteropProperties(interactionMode = null),
                )
            }
            // Natvrdo bílá: podklad je obraz z kamery, role z `colorScheme` by na něm nebyla vidět
            // (stejná výjimka jako ovládání v prohlížeči médií).
            IconButton(
                onClick = onDismiss,
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(8.dp),
            ) {
                Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.common_close))
            }
            Text(
                stringResource(Res.string.devices_qr_hint),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
            )
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class QrCaptureSession(onCode: (String) -> Unit) {

    private val delegate = MetadataDelegate(onCode)

    /** `null` = kamera není (simulátor) nebo se nedala otevřít. */
    val session: AVCaptureSession? = run {
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return@run null
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, error = null) ?: return@run null
        val session = AVCaptureSession()
        if (!session.canAddInput(input)) return@run null
        session.addInput(input)
        val output = AVCaptureMetadataOutput()
        if (!session.canAddOutput(output)) return@run null
        session.addOutput(output)
        output.setMetadataObjectsDelegate(delegate, queue = dispatch_get_main_queue())
        // Druhy kódů jdou nastavit až po přidání výstupu do relace, dřív je výstup nezná.
        output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
        session
    }

    // `startRunning` blokuje, dokud se kamera nerozběhne — na hlavním vlákně by zamrzlo UI.
    fun start() {
        val session = session ?: return
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            session.startRunning()
        }
    }

    fun stop() {
        val session = session ?: return
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            session.stopRunning()
        }
    }
}

private class MetadataDelegate(
    private val onCode: (String) -> Unit,
) : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection,
    ) {
        didOutputMetadataObjects
            .firstNotNullOfOrNull { (it as? AVMetadataMachineReadableCodeObject)?.stringValue }
            ?.let(onCode)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class CameraPreviewView(session: AVCaptureSession) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {

    private val previewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }

    init {
        backgroundColor = UIColor.blackColor
        layer.addSublayer(previewLayer)
    }

    /** Vrstva velikost s view nemění sama; bez implicitní animace. */
    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        previewLayer.frame = bounds
        CATransaction.commit()
    }
}
