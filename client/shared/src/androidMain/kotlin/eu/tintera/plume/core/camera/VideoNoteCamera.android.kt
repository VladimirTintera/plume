package eu.tintera.plume.core.camera

import androidx.media3.transformer.Effects
import eu.tintera.plume.core.video.exportVideo
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Composition
import androidx.media3.effect.Presentation
import androidx.media3.common.MimeTypes
import androidx.media3.common.MediaItem
import android.net.Uri
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Rational
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.ExperimentalPersistentRecording
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.audio.RecordPermission
import eu.tintera.plume.core.telegram.OutgoingVideoNote
import eu.tintera.plume.core.ui.OUTGOING_DIRECTORY
import eu.tintera.plume.core.ui.videoMetadata
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@Composable
actual fun rememberVideoNoteCamera(): VideoNoteCamera? {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    if (!hasCamera) return null
    val camera = remember(owner) { AndroidVideoNoteCamera(context.applicationContext, owner) }
    DisposableEffect(camera) { onDispose { camera.release() } }
    return camera
}

@Composable
actual fun rememberVideoNotePermission(): RecordPermission {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<(Boolean) -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        pending[0]?.invoke(result.values.all { it })
        pending[0] = null
    }
    return remember(launcher) {
        object : RecordPermission {
            override fun request(onResult: (granted: Boolean) -> Unit) {
                val missing = VIDEO_NOTE_PERMISSIONS.filter {
                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                }
                if (missing.isEmpty()) {
                    onResult(true)
                } else {
                    pending[0] = onResult
                    launcher.launch(missing.toTypedArray())
                }
            }
        }
    }
}

private val VIDEO_NOTE_PERMISSIONS = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

actual suspend fun prepareVideoNote(path: String): OutgoingVideoNote = withContext(Dispatchers.IO) {
    val metadata = videoMetadata(File(path))
    OutgoingVideoNote(
        path = path,
        /*
         * Čtverec, takže je to jedno — kdyby kamera přece jen vydala obdélník, kratší strana.
         * Zastropuje se: TDLib stranu od 640 px výš odmítne („Wrong video note length"), a kamera
         * bez SD (emulátor) natočí 720 × 720. Strana je jen údaj pro rozvržení; klienti video
         * vepisují do kruhu podle skutečných rozměrů.
         */
        length = minOf(metadata.width, metadata.height).coerceAtMost(VIDEO_NOTE_MAX_LENGTH),
        durationSeconds = metadata.durationSeconds.coerceAtLeast(1),
        thumbnail = metadata.thumbnail,
    )
}

/**
 * CameraX: náhled a `VideoCapture` s `Recorder` do MP4.
 *
 * - **Čtverec dělá `ViewPort` 1 : 1** nad oběma use case — ořez platí pro náhled i pro video, takže
 *   kolečko obsahuje přesně to, co bylo v kruhu vidět. Kvalita SD (640 × 480) → 480 × 480.
 * - **Přední kamera se zrcadlí i ve videu** (`MIRROR_MODE_ON_FRONT_ONLY`), jako náhled. Jinak by
 *   odeslané kolečko bylo stranově obrácené proti tomu, co uživatel viděl.
 * - **Nahrávání je „persistent"**, takže přežije přepnutí kamery (nové svázání s jiným selektorem).
 * - **Náhled jde přes `TextureView` (`COMPATIBLE`)**: `SurfaceView` je samostatné okno, které se
 *   ořezem do kruhu v Compose neořízne (stejná past jako u videa v prohlížeči médií).
 */
@OptIn(ExperimentalPersistentRecording::class)
private class AndroidVideoNoteCamera(
    private val context: Context,
    private val owner: LifecycleOwner,
) : VideoNoteCamera {

    // Inicializace CameraX kameru neotevírá (indikátor soukromí nesvítí), a do ťuknutí je hotová.
    private val providerFuture = ProcessCameraProvider.getInstance(context)
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val logger = Logger.withTag("VideoNoteCamera")

    private val preview = Preview.Builder().build()
    private val videoCapture = VideoCapture.Builder(
        Recorder.Builder()
            // SD (480p) mají skoro všechny telefony; bez něj radši nižší než vyšší — TDLib bere
            // stranu kolečka jen pod 640 px (viz [prepareVideoNote]).
            .setQualitySelector(QualitySelector.from(Quality.SD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
            .setTargetVideoEncodingBitRate(VIDEO_BITRATE)
            .build(),
    )
        .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
        .build()

    private var selector = CameraSelector.DEFAULT_FRONT_CAMERA
    private var recording: Recording? = null
    private var folder: File? = null
    private var output: File? = null
    private var finalized: CompletableDeferred<VideoRecordEvent.Finalize>? = null

    @Volatile
    private var amplitude = 0f

    private fun provider(): ProcessCameraProvider = providerFuture.get(PROVIDER_TIMEOUT_SECONDS, TimeUnit.SECONDS)

    override val canSwitch: Boolean
        get() = providerFuture.isDone && runCatching {
            val provider = provider()
            provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) && provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
        }.getOrDefault(false)

    @Composable
    override fun Preview(modifier: Modifier) {
        AndroidView(
            factory = { viewContext ->
                PreviewView(viewContext).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    preview.surfaceProvider = surfaceProvider
                }
            },
            onRelease = { preview.surfaceProvider = null },
            modifier = modifier,
        )
    }

    // Oprávnění k mikrofonu i kameře vyřídí obrazovka dřív, než sem dojde.
    @SuppressLint("MissingPermission")
    override fun start() {
        check(recording == null) { "Nahrávání už běží." }
        val provider = provider()
        if (!provider.hasCamera(selector)) selector = CameraSelector.DEFAULT_BACK_CAMERA
        bind(provider)

        val directory = File(context.cacheDir, "$OUTGOING_DIRECTORY/${UUID.randomUUID()}").apply { mkdirs() }
        val target = File(directory, "video-note.mp4")
        val done = CompletableDeferred<VideoRecordEvent.Finalize>()
        folder = directory
        output = target
        finalized = done
        amplitude = 0f
        recording = try {
            videoCapture.output
                .prepareRecording(context, FileOutputOptions.Builder(target).build())
                .withAudioEnabled()
                .asPersistentRecording()
                .start(mainExecutor) { event ->
                    when (event) {
                        is VideoRecordEvent.Status -> amplitude = event.recordingStats.audioStats.audioAmplitude.toFloat()
                        is VideoRecordEvent.Finalize -> done.complete(event)
                        else -> Unit
                    }
                }
        } catch (failure: Throwable) {
            provider.unbindAll()
            directory.deleteRecursively()
            folder = null
            output = null
            finalized = null
            throw failure
        }
    }

    private fun bind(provider: ProcessCameraProvider) {
        val group = UseCaseGroup.Builder()
            .setViewPort(ViewPort.Builder(Rational(1, 1), Surface.ROTATION_0).build())
            .addUseCase(preview)
            .addUseCase(videoCapture)
            .build()
        provider.unbindAll()
        provider.bindToLifecycle(owner, selector, group)
    }

    /** Odmocnina zvedne tiché úrovně, ať je řeč vidět — stejně jako u hlasovky. */
    override val level: Float get() = sqrt(amplitude.coerceIn(0f, 1f))

    override suspend fun stop(): String {
        val active = recording ?: error("Nic se nenahrává.")
        val done = finalized ?: error("Nic se nenahrává.")
        val target = output ?: error("Nic se nenahrává.")
        active.stop()
        recording = null
        val event = try {
            withTimeout(FINALIZE_TIMEOUT_MS) { done.await() }
        } finally {
            unbind()
        }
        // Některé chyby (došlo místo, kamera se odpojila) soubor přesto dopíšou; rozhoduje, jestli je.
        if (event.hasError()) logger.w(event.cause) { "Nahrávání kolečka skončilo chybou ${event.error}." }
        check(target.length() > 0) { "Kolečko se nenahrálo." }
        folder = null
        output = null
        finalized = null
        return shrinkIfNeeded(target).absolutePath
    }

    /**
     * Kolečko s větší stranou než [VIDEO_NOTE_MAX_LENGTH] přepočítá na přesně tolik.
     *
     * **Telegram větší kolečko převede na obyčejné video** — zpráva pak přijde jako obdélník
     * s tlačítkem přehrání, i když odešla jako kolečko (ověřeno Bot API: 720 × 720 přišlo jako
     * `video`). Kamera bez SD (emulátor, část telefonů) přitom jinak než 720p nenatočí a CameraX
     * výstup zmenšit neumí, jen oříznout. Zmenšuje se tedy až hotový soubor, a jen když je
     * potřeba — s SD je soubor rovnou 480 × 480 a nic se nepřepočítává.
     */
    private suspend fun shrinkIfNeeded(source: File): File {
        val size = videoMetadata(source).let { maxOf(it.width, it.height) }
        if (size <= VIDEO_NOTE_MAX_LENGTH) return source
        val target = File(source.parentFile, "video-note-$VIDEO_NOTE_MAX_LENGTH.mp4")
        export(Composition.Builder(EditedMediaItemSequence.Builder(squareItem(source)).build()).build(), target)
        source.delete()
        return target
    }

    /**
     * Dvě kolečka za sebou do jednoho souboru — Transformer s jednou sekvencí dvou položek.
     * Obě jdou přes tentýž [Presentation] na [VIDEO_NOTE_MAX_LENGTH] px: sekvence s různými
     * rozměry by výsledek vzala podle první položky a druhou roztáhla.
     *
     * **Zvuk jde do AAC.** Sekvenci Transformer zvukem vždycky překódovává (úseky se musí
     * navázat na vzorek), a bez výslovného formátu volí kodér podle vstupu. Emulátor nahrává
     * AMR-NB a s kodérem AMR se export zasekl — hlídač ho po 25 s ukončil („no output sample
     * written"). AAC je navíc to, co kolečka ostatních klientů nesou.
     */
    override suspend fun join(previous: String, next: String): String {
        val first = File(previous)
        val second = File(next)
        val target = File(second.parentFile, "video-note-joined.mp4")
        val sequence = EditedMediaItemSequence.Builder(squareItem(first), squareItem(second)).build()
        try {
            export(Composition.Builder(sequence).build(), target, audioMimeType = MimeTypes.AUDIO_AAC)
        } catch (failure: Throwable) {
            target.delete()
            throw failure
        }
        first.delete()
        // Složka předchozího kolečka zůstala prázdná (náhledový snímek maže volající); prázdnou smaže.
        first.parentFile?.delete()
        second.delete()
        return target.absolutePath
    }

    private fun squareItem(source: File): EditedMediaItem =
        EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(source)))
            .setEffects(
                Effects(
                    emptyList(),
                    listOf(
                        Presentation.createForWidthAndHeight(
                            VIDEO_NOTE_MAX_LENGTH,
                            VIDEO_NOTE_MAX_LENGTH,
                            Presentation.LAYOUT_SCALE_TO_FIT,
                        ),
                    ),
                ),
            )
            .build()

    private suspend fun export(composition: Composition, target: File, audioMimeType: String? = null) =
        exportVideo(context, composition, target, audioMimeType)

    override fun cancel() {
        val active = recording ?: return
        val directory = folder
        // Soubor se dopíše až po zastavení, takže se maže až po něm.
        finalized?.invokeOnCompletion { directory?.deleteRecursively() }
        active.stop()
        recording = null
        folder = null
        output = null
        finalized = null
        unbind()
    }

    override fun pause() {
        recording?.pause()
    }

    override fun resume() {
        recording?.resume()
    }

    override fun switchCamera() {
        selector = if (selector == CameraSelector.DEFAULT_FRONT_CAMERA) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }
        // Mimo nahrávání není co přepínat; volba platí pro příští start.
        if (recording == null) return
        runCatching { bind(provider()) }
            .onFailure { failure -> logger.w(failure) { "Kameru nejde přepnout." } }
    }

    private fun unbind() {
        if (providerFuture.isDone) runCatching { provider().unbindAll() }
    }

    fun release() {
        cancel()
        unbind()
    }
}

/** Kolem 1 Mbit/s jako oficiální klienti: minuta kolečka je pak ~8 MB. */
private const val VIDEO_BITRATE = 1_000_000

private const val PROVIDER_TIMEOUT_SECONDS = 3L

private const val FINALIZE_TIMEOUT_MS = 10_000L
