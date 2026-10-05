package eu.tintera.plume.core.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.audio.RecordPermission
import eu.tintera.plume.core.telegram.OutgoingVideoNote
import eu.tintera.plume.core.ui.outgoingPath
import eu.tintera.plume.core.ui.videoThumbnail
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import platform.AVFoundation.AVAssetExportPresetHighestQuality
import platform.AVFoundation.AVAssetExportSession
import platform.AVFoundation.AVAssetExportSessionStatusCompleted
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureAudioChannel
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureFileOutput
import platform.AVFoundation.AVCaptureFileOutputRecordingDelegateProtocol
import platform.AVFoundation.AVCaptureMovieFileOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPreset640x480
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVFileTypeMPEG4
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaType
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMutableComposition
import platform.AVFoundation.AVMutableVideoComposition
import platform.AVFoundation.addMutableTrackWithMediaType
import platform.AVFoundation.insertTimeRange
import platform.AVFoundation.setPreferredTransform
import platform.AVFoundation.AVMutableVideoCompositionInstruction
import platform.AVFoundation.AVMutableVideoCompositionLayerInstruction
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.defaultDeviceWithDeviceType
import platform.AVFoundation.duration
import platform.AVFoundation.naturalSize
import platform.AVFoundation.preferredTransform
import platform.AVFoundation.requestAccessForMediaType
import platform.AVFoundation.setVideoComposition
import platform.AVFoundation.tracksWithMediaType
import platform.CoreGraphics.CGAffineTransformConcat
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGAffineTransformMakeTranslation
import platform.CoreGraphics.CGRectApplyAffineTransform
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreMedia.CMTimeAdd
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeRangeMake
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.QuartzCore.CATransaction
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create

@Composable
actual fun rememberVideoNoteCamera(): VideoNoteCamera? {
    val camera = remember { IosVideoNoteCamera.create() }
    DisposableEffect(camera) { onDispose { camera?.release() } }
    return camera
}

@Composable
actual fun rememberVideoNotePermission(): RecordPermission = remember {
    object : RecordPermission {
        override fun request(onResult: (granted: Boolean) -> Unit) {
            // Nejdřív kamera, pak mikrofon. Odpověď chodí mimo hlavní vlákno.
            requestAccess(AVMediaTypeVideo) { camera ->
                if (!camera) onResult(false) else requestAccess(AVMediaTypeAudio, onResult)
            }
        }
    }
}

private fun requestAccess(type: AVMediaType, onResult: (Boolean) -> Unit) {
    when (AVCaptureDevice.authorizationStatusForMediaType(type)) {
        AVAuthorizationStatusAuthorized -> onResult(true)
        AVAuthorizationStatusNotDetermined -> AVCaptureDevice.requestAccessForMediaType(type) { granted ->
            dispatch_async(dispatch_get_main_queue()) { onResult(granted) }
        }
        // Odmítnuté nebo omezené — systém se znovu nezeptá, změnit to jde jen v Nastavení.
        else -> onResult(false)
    }
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun prepareVideoNote(path: String): OutgoingVideoNote {
    val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(path), options = null)
    val track = asset.tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? AVAssetTrack
    val length = track?.let { orientedSize(it).let { (width, height) -> min(width, height).roundToInt() } } ?: 0
    val duration = CMTimeGetSeconds(asset.duration).takeIf { !it.isNaN() } ?: 0.0
    return OutgoingVideoNote(
        path = path,
        length = length,
        durationSeconds = duration.roundToInt().coerceAtLeast(1),
        thumbnail = videoThumbnail(asset, path.substringBeforeLast('/')),
    )
}

/** Rozměry stopy po `preferredTransform` — video z kamery je uložené naležato s otočením. */
@OptIn(ExperimentalForeignApi::class)
private fun orientedSize(track: AVAssetTrack): Pair<Double, Double> =
    track.naturalSize.useContents {
        CGRectApplyAffineTransform(CGRectMake(0.0, 0.0, width, height), track.preferredTransform).useContents {
            abs(size.width) to abs(size.height)
        }
    }

/**
 * AVFoundation: `AVCaptureSession` s přední kamerou a mikrofonem, `AVCaptureMovieFileOutput`
 * do `.mov` a po skončení export do čtvercového MP4.
 *
 * - **Čtverec se ořezává až exportem** (`AVMutableVideoComposition` se středovým ořezem), ne
 *   při snímání — výstup do souboru ořez nemá. Náhled je `AspectFill` v kruhu, takže ukazuje
 *   stejný střed.
 * - **Přepínat kameru během nahrávání nejde** ([canSwitch] je `false`): `AVCaptureMovieFileOutput`
 *   při výměně vstupu nahrávání ukončí. Umělo by to jen vlastní zápis přes `AVAssetWriter`.
 * - **Relace běží na vlastní sériové frontě**: `startRunning` blokuje, a nahrávání se smí
 *   spustit až po něm, takže jdou po sobě na téže frontě.
 * - **Delegát se drží silnou referencí** — AVFoundation ho má jen slabě (stejná past jako
 *   u čtečky QR kódů).
 */
@OptIn(ExperimentalForeignApi::class)
private class IosVideoNoteCamera private constructor(
    private val camera: AVCaptureDevice,
    private val microphone: AVCaptureDevice,
) : VideoNoteCamera {

    private val session = AVCaptureSession().apply { sessionPreset = AVCaptureSessionPreset640x480 }
    private val movieOutput = AVCaptureMovieFileOutput()
    private var configured = false

    private val queue = dispatch_queue_create("eu.tintera.plume.videonote", null)
    private val logger = Logger.withTag("VideoNoteCamera")
    private var recordingPath: String? = null
    private var finished: CompletableDeferred<NSError?>? = null
    private val delegate = RecordingDelegate { error -> finished?.complete(error) }

    override val canSwitch: Boolean = false

    override fun switchCamera() = Unit

    override suspend fun join(previous: String, next: String): String {
        val target = joinVideoNotes(previous, next) ?: error("Kolečka nejde spojit.")
        val files = NSFileManager.defaultManager
        files.removeItemAtPath(previous, error = null)
        // Složka předchozího kolečka zůstala bez souborů (náhledový snímek maže volající).
        files.removeItemAtPath(previous.substringBeforeLast('/'), error = null)
        files.removeItemAtPath(next, error = null)
        return target
    }

    @Composable
    override fun Preview(modifier: Modifier) {
        UIKitView(
            factory = { CirclePreviewView(session) },
            modifier = modifier,
            properties = UIKitInteropProperties(interactionMode = null),
        )
    }

    /**
     * Vstupy se přidávají až při prvním nahrávání, ne s konverzací: `AVCaptureDeviceInput` bez
     * uděleného oprávnění selže (nebo si o něj systém řekne sám), a chat se otevírá mnohem
     * častěji, než se nahrává. Oprávnění vyřídí obrazovka dřív, než sem dojde.
     */
    private fun configure() {
        if (configured) return
        val inputs = listOf(camera, microphone).map { device ->
            AVCaptureDeviceInput.deviceInputWithDevice(device, error = null) ?: error("Kameru nebo mikrofon nejde otevřít.")
        }
        session.beginConfiguration()
        try {
            inputs.forEach { input -> check(session.canAddInput(input)) { "Vstup nejde přidat." }; session.addInput(input) }
            check(session.canAddOutput(movieOutput)) { "Výstup nejde přidat." }
            session.addOutput(movieOutput)
        } finally {
            session.commitConfiguration()
        }
        configured = true
    }

    override fun start() {
        check(recordingPath == null) { "Nahrávání už běží." }
        configure()
        val path = outgoingPath("video-note.mov")
        val done = CompletableDeferred<NSError?>()
        recordingPath = path
        finished = done
        dispatch_async(queue) {
            if (!session.running) session.startRunning()
            movieOutput.connectionWithMediaType(AVMediaTypeVideo)?.let { connection ->
                // Na výšku, a přední kamera zrcadlově — tak, jak ji uživatel viděl v náhledu.
                if (connection.isVideoRotationAngleSupported(PORTRAIT_ANGLE)) connection.videoRotationAngle = PORTRAIT_ANGLE
                if (connection.supportsVideoMirroring) {
                    connection.automaticallyAdjustsVideoMirroring = false
                    connection.videoMirrored = true
                }
            }
            movieOutput.startRecordingToOutputFileURL(NSURL.fileURLWithPath(path), recordingDelegate = delegate)
        }
    }

    override val level: Float
        get() {
            val channel = movieOutput.connectionWithMediaType(AVMediaTypeAudio)
                ?.audioChannels?.firstOrNull() as? AVCaptureAudioChannel ?: return 0f
            // Průměr v dB (−160…0) na 0..1, pod −50 dB ticho — stejně jako u hlasovky.
            return ((channel.averagePowerLevel + 50f) / 50f).coerceIn(0f, 1f)
        }

    override suspend fun stop(): String {
        val done = finished ?: error("Nic se nenahrává.")
        val raw = recordingPath ?: error("Nic se nenahrává.")
        dispatch_async(queue) { movieOutput.stopRecording() }
        val error = try {
            withTimeout(FINISH_TIMEOUT_MS) { done.await() }
        } finally {
            recordingPath = null
            finished = null
            dispatch_async(queue) { session.stopRunning() }
        }
        // Chyba s dopsaným souborem (došlo místo, přerušení hovorem) kolečko nezahazuje.
        if (error != null) logger.w { "Nahrávání kolečka skončilo chybou: ${error.localizedDescription}" }
        check(NSFileManager.defaultManager.fileExistsAtPath(raw)) { "Kolečko se nenahrálo." }
        val square = exportSquare(raw) ?: error("Kolečko nejde oříznout.")
        NSFileManager.defaultManager.removeItemAtPath(raw, error = null)
        return square
    }

    override fun cancel() {
        val raw = recordingPath ?: return
        finished?.invokeOnCompletion {
            NSFileManager.defaultManager.removeItemAtPath(raw.substringBeforeLast('/'), error = null)
        }
        recordingPath = null
        finished = null
        dispatch_async(queue) {
            movieOutput.stopRecording()
            session.stopRunning()
        }
    }

    override fun pause() {
        dispatch_async(queue) { if (movieOutput.recording) movieOutput.pauseRecording() }
    }

    override fun resume() {
        dispatch_async(queue) { if (movieOutput.recordingPaused) movieOutput.resumeRecording() }
    }

    fun release() {
        cancel()
        dispatch_async(queue) { session.stopRunning() }
    }

    companion object {
        /** `null` = kamera nebo mikrofon není (simulátor). Nic se neotevírá, jen se hledá. */
        fun create(): IosVideoNoteCamera? {
            val camera = AVCaptureDevice.defaultDeviceWithDeviceType(
                AVCaptureDeviceTypeBuiltInWideAngleCamera,
                mediaType = AVMediaTypeVideo,
                position = AVCaptureDevicePositionFront,
            ) ?: AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return null
            val microphone = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeAudio) ?: return null
            return IosVideoNoteCamera(camera, microphone)
        }
    }
}

/**
 * Čtvercový středový ořez do MP4, nejvýš [VIDEO_NOTE_MAX_LENGTH] px na stranu. `null`, když
 * export selže.
 *
 * Transformace jde po `preferredTransform` (otočí video na výšku, u přední kamery i zrcadlí),
 * pak posune střed čtverce do počátku a zmenší na výslednou stranu.
 */
@OptIn(ExperimentalForeignApi::class)
private suspend fun exportSquare(source: String): String? {
    val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(source), options = null)
    val track = asset.tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? AVAssetTrack ?: return null
    val (width, height) = orientedSize(track)
    val side = min(width, height)
    if (side <= 0.0) return null
    val output = min(side, VIDEO_NOTE_MAX_LENGTH.toDouble()).roundToInt().toDouble()
    val scale = output / side

    var transform = CGAffineTransformConcat(
        track.preferredTransform,
        CGAffineTransformMakeTranslation(-(width - side) / 2, -(height - side) / 2),
    )
    transform = CGAffineTransformConcat(transform, CGAffineTransformMakeScale(scale, scale))

    val layer = AVMutableVideoCompositionLayerInstruction.videoCompositionLayerInstructionWithAssetTrack(track)
    val start = CMTimeMake(value = 0, timescale = 1)
    layer.setTransform(transform, atTime = start)
    val instruction = AVMutableVideoCompositionInstruction()
    instruction.setTimeRange(CMTimeRangeMake(start, asset.duration))
    instruction.setLayerInstructions(listOf(layer))
    val composition = AVMutableVideoComposition()
    composition.setRenderSize(CGSizeMake(output, output))
    composition.setFrameDuration(CMTimeMake(value = 1, timescale = FRAME_RATE))
    composition.setInstructions(listOf(instruction))

    val target = source.substringBeforeLast('.') + ".mp4"
    val session = AVAssetExportSession(asset = asset, presetName = AVAssetExportPresetHighestQuality) ?: return null
    session.outputURL = NSURL.fileURLWithPath(target)
    session.outputFileType = AVFileTypeMPEG4
    session.setVideoComposition(composition)
    // Index na začátek: kolečko se u příjemce přehrává, ještě než se celé stáhne.
    session.shouldOptimizeForNetworkUse = true

    val completed = suspendCancellableCoroutine { continuation ->
        session.exportAsynchronouslyWithCompletionHandler {
            continuation.resume(session.status == AVAssetExportSessionStatusCompleted)
        }
        continuation.invokeOnCancellation { session.cancelExport() }
    }
    return target.takeIf { completed }
}

/**
 * Dvě kolečka za sebou do jednoho MP4 vedle [next]. Obě už prošla [exportSquare], takže mají
 * stejnou čtvercovou stranu a otočení — stačí `AVMutableComposition` se stopou obrazu a zvuku
 * a export, žádná `AVMutableVideoComposition`. `null`, když export selže.
 */
@OptIn(ExperimentalForeignApi::class)
private suspend fun joinVideoNotes(previous: String, next: String): String? {
    val composition = AVMutableComposition()
    val video = composition.addMutableTrackWithMediaType(AVMediaTypeVideo, preferredTrackID = 0) ?: return null
    val audio = composition.addMutableTrackWithMediaType(AVMediaTypeAudio, preferredTrackID = 0)
    var cursor = CMTimeMake(value = 0, timescale = 1)
    for (path in listOf(previous, next)) {
        val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(path), options = null)
        val range = CMTimeRangeMake(CMTimeMake(value = 0, timescale = 1), asset.duration)
        val videoTrack = asset.tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? AVAssetTrack ?: return null
        if (!video.insertTimeRange(range, ofTrack = videoTrack, atTime = cursor, error = null)) return null
        if (path == previous) video.preferredTransform = videoTrack.preferredTransform
        (asset.tracksWithMediaType(AVMediaTypeAudio).firstOrNull() as? AVAssetTrack)?.let { audioTrack ->
            audio?.insertTimeRange(range, ofTrack = audioTrack, atTime = cursor, error = null)
        }
        cursor = CMTimeAdd(cursor, asset.duration)
    }

    val target = next.substringBeforeLast('/') + "/video-note-joined.mp4"
    val session = AVAssetExportSession(asset = composition, presetName = AVAssetExportPresetHighestQuality) ?: return null
    session.outputURL = NSURL.fileURLWithPath(target)
    session.outputFileType = AVFileTypeMPEG4
    session.shouldOptimizeForNetworkUse = true
    val completed = suspendCancellableCoroutine { continuation ->
        session.exportAsynchronouslyWithCompletionHandler {
            continuation.resume(session.status == AVAssetExportSessionStatusCompleted)
        }
        continuation.invokeOnCancellation { session.cancelExport() }
    }
    return target.takeIf { completed }
}

private class RecordingDelegate(
    private val onFinished: (NSError?) -> Unit,
) : NSObject(), AVCaptureFileOutputRecordingDelegateProtocol {
    override fun captureOutput(
        output: AVCaptureFileOutput,
        didFinishRecordingToOutputFileAtURL: NSURL,
        fromConnections: List<*>,
        error: NSError?,
    ) {
        onFinished(error)
    }
}

/** Náhled kamery ořezaný do kruhu. Compose interop view neořízne, proto si ho ořízne vrstva sama. */
@OptIn(ExperimentalForeignApi::class)
private class CirclePreviewView(session: AVCaptureSession) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {

    private val previewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }

    init {
        backgroundColor = UIColor.blackColor
        layer.masksToBounds = true
        layer.addSublayer(previewLayer)
    }

    /** Vrstva velikost s view nemění sama; bez implicitní animace. */
    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        previewLayer.frame = bounds
        layer.cornerRadius = bounds.useContents { min(size.width, size.height) / 2 }
        CATransaction.commit()
    }
}

private const val PORTRAIT_ANGLE = 90.0

private const val FRAME_RATE = 30

private const val FINISH_TIMEOUT_MS = 10_000L
