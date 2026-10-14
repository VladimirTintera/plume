package eu.tintera.plume.core.calls

import android.content.Context
import android.media.AudioManager
import co.touchlab.kermit.Logger
import io.github.pytgcalls.ConnectionState
import io.github.pytgcalls.NTgCalls
import io.github.pytgcalls.VideoRotation
import io.github.pytgcalls.devices.JavaVideoCapturerModule
import io.github.pytgcalls.media.AudioDescription
import io.github.pytgcalls.media.MediaDescription
import io.github.pytgcalls.media.MediaSource
import io.github.pytgcalls.media.StreamDevice
import io.github.pytgcalls.media.StreamMode
import io.github.pytgcalls.media.StreamStatus
import io.github.pytgcalls.media.VideoDescription
import io.github.pytgcalls.models.Frame
import io.github.pytgcalls.p2p.RTCServer
import org.json.JSONObject
import org.webrtc.CapturerObserver
import org.webrtc.ContextUtils
import org.webrtc.EglBase
import org.webrtc.JavaI420Buffer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Přenos hovoru přes ntgcalls (LGPL-3.0, `libntgcalls.so` s WebRTC uvnitř, viz
 * `docs/poznatky/hovory.md`). Pořadí volání drží knihovna: hovor → zdroje (mikrofon,
 * reproduktor, kamera) → klíč z TDLib (`skipExchange`, výměnu udělal TDLib) → spojení.
 *
 * **Kameru bere knihovna sama** (zdroj DEVICE se jménem kamery, `JavaVideoCapturerModule`
 * nad Camera2) a snímky z ní vrací i nám pro náhled; obraz protistrany chodí jako EXTERNAL
 * přehrávání. Obojí jsou I420 snímky v `onFrames`, které se předají rendererům ([CallVideoSinks]).
 *
 * Všechno jde přes jedno vlákno: knihovna není dělaná na souběžná volání a první dotyk
 * (`getProtocol`) nahrává ~20 MB knihovnu — na hlavním vlákně by to bylo znát.
 */
class NtgCallMedia internal constructor(
    context: Context,
    /** Kdo vede zvuk: Telecom, nebo náhradně Plume sám (viz [CallAudioRoutes]). */
    private val routes: () -> CallAudioRoutes,
) : CallMedia, CallVideoSinks {

    constructor(context: Context) : this(context, { CallAudioRouter(context.getSystemService(AudioManager::class.java)) })

    private val logger = Logger.withTag("NtgCallMedia")
    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "ntgcalls") }

    private var ntg: NTgCalls? = null
    private var callId = 0L
    private var router: CallAudioRoutes? = null

    /** Uživatel si ztlumil mikrofon — po skončení podržení (GSM hovor) se mu nesmí zapnout. */
    private var userMuted = false
    private var held = false

    /** Kamera: zapnutá a která. Platí i před spuštěním přenosu (vytáčený videohovor). */
    private var cameraOn = false
    private var cameraFront = true

    /** Posíláme obrazovku místo kamery ([setScreenSharing]). */
    private var screenOn = false

    /** Kolikáté sdílení běží — pozdní konec projekce z dřívějšího se tím pozná a zahodí. */
    private var screenGeneration = 0

    /** Komu hlásit události běžícího přenosu (od [start]). */
    private var events: CallMediaEvents? = null

    /** Protistrana posílá kameru / obrazovku; obraz protistrany je, když aspoň jedno. */
    private var remoteCamera = false
    private var remoteScreen = false

    /** Kamery telefonu (metadata pro ntgcalls), načtené při prvním zapnutí. */
    private val cameras: List<Camera> by lazy { readCameras() }

    private val sinks = ConcurrentHashMap<CallVideoSource, VideoSink>()

    /**
     * Přímé buffery na snímky, po zdroji. Snímek 720p má 1,4 MB a nový buffer pro každý
     * (30 za vteřinu) by uvolnil až GC; buffer se vrací, až ho renderer pustí.
     */
    private val framePools = ConcurrentHashMap<CallVideoSource, ConcurrentLinkedQueue<ByteBuffer>>()

    override val eglContext: EglBase.Context get() = JavaVideoCapturerModule.getSharedEGLContext()

    override fun attach(source: CallVideoSource, sink: VideoSink) {
        sinks[source] = sink
    }

    override fun detach(source: CallVideoSource, sink: VideoSink) {
        sinks.remove(source, sink)
    }

    override val protocol: CallMediaProtocol by lazy {
        worker.submit<CallMediaProtocol> {
            ensureWebRtcContext()
            val p = NTgCalls.getProtocol()
            CallMediaProtocol(minLayer = p.min_layer, maxLayer = p.max_layer, versions = p.library_versions)
        }.get()
    }

    override fun startAudio(onOutputs: (List<CallAudioOutput>, CallAudioOutput) -> Unit) {
        worker.execute {
            // Systém ví, že běží hovor (ozvěna, hlasitost hovoru), a zvuk jde tam, kam patří.
            router?.stop()
            router = routes().also { it.start(onOutputs) }
        }
    }

    override fun start(session: CallMediaSession, events: CallMediaEvents) {
        worker.execute {
            runCatching {
                // Předchozí přenos pryč, zvuková relace (spuštěná při vytáčení / přijetí) zůstává.
                ntg?.let { old -> runCatching { old.stop(callId) } }
                ntg = null
                ensureWebRtcContext()
                callId = session.callId.toLong()
                this.events = events
                val instance = NTgCalls()
                ntg = instance
                instance.onSignalingData { _, data -> events.onSignaling(data) }
                instance.onConnectionChange { _, info ->
                    logger.i { "Spojení: ${info.state}" }
                    events.onConnection(
                        when (info.state) {
                            ConnectionState.CONNECTED -> CallConnection.Connected
                            ConnectionState.CONNECTING -> CallConnection.Connecting
                            ConnectionState.CLOSED -> CallConnection.Closed
                            else -> CallConnection.Failed
                        },
                    )
                }
                remoteCamera = false
                remoteScreen = false
                instance.onRemoteSourceChange { _, source ->
                    // Obraz protistrany je kamera, nebo sdílená obrazovka (v hovoru ve dvou jedno z nich).
                    when (source.device) {
                        StreamDevice.CAMERA -> remoteCamera = source.state == StreamStatus.ACTIVE
                        StreamDevice.SCREEN -> remoteScreen = source.state == StreamStatus.ACTIVE
                        else -> return@onRemoteSourceChange
                    }
                    events.onRemoteVideo(remoteCamera || remoteScreen)
                }
                instance.onFrames { _, mode, device, frames ->
                    // Naše sdílená obrazovka se v náhledu nekreslí (obraz v obraze sebe sama).
                    if (device != StreamDevice.CAMERA && !(device == StreamDevice.SCREEN && mode == StreamMode.PLAYBACK)) return@onFrames
                    val source = if (mode == StreamMode.CAPTURE) CallVideoSource.Local else CallVideoSource.Remote
                    val sink = sinks[source] ?: return@onFrames
                    frames.lastOrNull()?.let { render(source, sink, it) }
                }
                instance.createP2pCall(callId)
                // Náhled pryč DŘÍV, než vznikne zdroj kamery knihovny: převzetí běžící kamery
                // zablokuje přenos (`SharedCamera`). Do spojení náhled stojí, pak běží zdroj.
                stopPreview()
                instance.setStreamSources(callId, StreamMode.CAPTURE, capture())
                // V režimu PLAYBACK je slot `microphone` mikrofon PROTISTRANY: příjem zvuku
                // ntgcalls zapne jen pro něj (`StreamManager::optimize_sources`). Reproduktor
                // ve slotu `speaker` dal ticho — spojení naváže, ale příchozí kanál nevznikne.
                // Kamera protistrany se přijímá VŽDY, i v hlasovém hovoru: zapnout ji může
                // kdykoli a bez příjmu by se obraz nedohodl.
                // Obrazovka protistrany stejně: slot `screen` jako EXTERNAL.
                instance.setStreamSources(callId, StreamMode.PLAYBACK, MediaDescription(device(SPEAKER), null, REMOTE_CAMERA, REMOTE_CAMERA))
                instance.skipExchange(callId, session.encryptionKey, session.isOutgoing)
                instance.connectP2p(
                    callId,
                    session.servers.map { it.toRtcServer() },
                    session.versions,
                    session.allowP2p,
                    session.customParameters,
                )

            }.onFailure { failure ->
                logger.e(failure) { "Přenos hovoru se nepodařilo spustit." }
                events.onConnection(CallConnection.Failed)
            }
        }
    }

    override fun receiveSignaling(data: ByteArray) {
        worker.execute {
            runCatching { ntg?.sendSignalingData(callId, data) }
                .onFailure { logger.w(it) { "Signalizaci se nepodařilo předat." } }
        }
    }

    override fun setMuted(muted: Boolean) {
        worker.execute {
            userMuted = muted
            if (!held) runCatching { ntg?.let { if (muted) it.mute(callId) else it.unmute(callId) } }
        }
    }

    override fun setHeld(held: Boolean) {
        worker.execute {
            this.held = held
            runCatching {
                ntg?.let { instance ->
                    if (held) {
                        instance.mute(callId)
                        instance.pause(callId)
                    } else {
                        instance.resume(callId)
                        if (!userMuted) instance.unmute(callId)
                    }
                }
            }.onFailure { logger.w(it) { "Podržení hovoru selhalo." } }
        }
    }

    override fun setOutput(output: CallAudioOutput) {
        worker.execute { router?.select(output) }
    }

    override val supportsScreenSharing: Boolean = true

    /**
     * Obrazovka místo kamery. Souhlas ([ScreenCapture.permission]) už je, službu s typem
     * `mediaProjection` přidává `CallService` podle stavu hovoru — tady se na ni počká, až pak
     * se zdroje přenastaví (`getMediaProjection` volá knihovna s otevřením zdroje).
     */
    override fun setScreenSharing(on: Boolean) {
        worker.execute {
            if (screenOn == on) return@execute
            val instance = ntg
            if (on) {
                val intent = ScreenCapture.permission ?: run {
                    logger.w { "Sdílení obrazovky bez souhlasu uživatele." }
                    return@execute
                }
                val ready = runBlocking {
                    withTimeoutOrNull(SCREEN_SERVICE_TIMEOUT_MS) { ScreenCapture.serviceReady.first { it } }
                }
                if (ready == null) {
                    logger.w { "Služba hovoru neběží s typem mediaProjection, obrazovku nejde sdílet." }
                    return@execute
                }
                JavaVideoCapturerModule.setMediaProjectionPermissionResult(intent)
                // Souhlas je na jedno použití; další sdílení si řekne o nový.
                ScreenCapture.permission = null
            }
            screenOn = on
            val generation = ++screenGeneration
            if (instance == null) return@execute
            runCatching { instance.setStreamSources(callId, StreamMode.CAPTURE, capture()) }
                .onFailure { logger.w(it) { "Sdílení obrazovky nejde ${if (on) "zapnout" else "vypnout"}." } }
            if (on) {
                ScreenCapture.watchStop(stillWanted = { screenOn && screenGeneration == generation }) {
                    worker.execute { screenStoppedBySystem(generation) }
                }
            }
        }
    }

    /** Snímání ukončil systém: obrazovka ze zdrojů pryč a hovor se to dozví. */
    private fun screenStoppedBySystem(generation: Int) {
        if (!screenOn || screenGeneration != generation) return
        logger.i { "Sdílení obrazovky ukončil systém." }
        screenOn = false
        runCatching { ntg?.setStreamSources(callId, StreamMode.CAPTURE, capture()) }
            .onFailure { logger.w(it) { "Po konci sdílení nejde vrátit zdroje." } }
        events?.onScreenSharingStopped()
    }

    override fun setCamera(on: Boolean, front: Boolean) {
        worker.execute {
            if (cameraOn == on && cameraFront == front) return@execute
            val switching = cameraOn && on
            cameraOn = on
            cameraFront = front
            if (!on) framePools.remove(CallVideoSource.Local)
            // Bez běžícího přenosu běží jen náhled; `start` dá kameru do zdrojů a ten ji převezme.
            val instance = ntg ?: run {
                if (on) startPreview() else stopPreview()
                return@execute
            }
            if (switching && switchRunningCamera()) return@execute
            if (switching) {
                // Výměna zdroje kameru zhasne (viz `switchRunningCamera`): napřed pryč, pak nová.
                // Protistrana na okamžik dostane „kamera vypnutá".
                runCatching { instance.setStreamSources(callId, StreamMode.CAPTURE, MediaDescription(device(MICROPHONE), null, null, null)) }
                SharedCamera.releaseCurrent()
            }
            runCatching { instance.setStreamSources(callId, StreamMode.CAPTURE, capture()) }
                .onFailure { logger.w(it) { "Kameru se nepodařilo přepnout." } }
            // Vypnutá kamera napevno pryč: knihovna ji uvolní až za 200 ms a zapnutí v té lhůtě
            // by převzalo běžící kameru (zablokování, viz `SharedCamera`).
            if (!on) SharedCamera.releaseCurrent()
        }
    }

    /** Co posíláme: mikrofon a s kamerou, nebo se sdílením obrazovky i obraz. */
    private fun capture(): MediaDescription {
        if (screenOn) {
            // Obrazovka místo kamery — knihovna je v hovoru ve dvou smíchat nedovolí.
            val (width, height) = ScreenCapture.size(appContext)
            return MediaDescription(device(MICROPHONE), null, null, VideoDescription(MediaSource.DESKTOP, width, height, SCREEN_FPS, SCREEN_INPUT, true))
        }
        val camera = if (cameraOn) camera(cameraFront) else null
        if (cameraOn && camera == null) logger.w { "Telefon nemá kameru." }
        return MediaDescription(
            device(MICROPHONE),
            null,
            camera?.let { VideoDescription(MediaSource.DEVICE, CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FPS, it.metadata, true) },
            null,
        )
    }

    /**
     * Přední ↔ zadní na BĚŽÍCÍ kameře, bez výměny zdroje v ntgcalls — ta by kameru zhasla
     * ([SharedCamera]). `false`, když sdílenou kameru použít nejde; pak se mění zdroj.
     */
    private fun switchRunningCamera(): Boolean {
        val id = camera(cameraFront)?.id ?: return false
        return (SharedCamera.use(id, CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FPS) != null)
            .also { if (!it) logger.w { "Kameru nejde přepnout na místě, měním zdroj." } }
    }

    /**
     * Náhled před spojením: přenos (a s ním zdroj kamery knihovny) vzniká až po `Ready`, takže
     * vytáčený videohovor by do té doby ukazoval černo. Kamera se proto otevře hned a snímky
     * (`VideoFrame` s texturou ve sdíleném kontextu EGL) jdou rovnou do rendereru náhledu.
     * Před spuštěním přenosu se napevno uvolní (`start`); převzít ji zdroj knihovny nesmí.
     */
    private fun startPreview() {
        val id = camera(cameraFront)?.id ?: return
        // I u běžícího náhledu: `use` s jiným id kameru přepne (přední ↔ zadní při vytáčení).
        val source = SharedCamera.use(id, CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FPS) ?: return
        if (preview == null && !SharedCamera.preview(source, previewObserver)) {
            logger.w { "Náhled kamery před spojením nejde, ukáže se až po spojení." }
            SharedCamera.releaseNow(source)
            return
        }
        preview = source
    }

    /** Kamera náhledu napevno pryč: vypnutá, hovor zrušený, nebo začíná přenos (viz `start`). */
    private fun stopPreview() {
        preview?.let(SharedCamera::releaseNow)
        preview = null
    }

    /** Sdílená kamera v náhledu, dokud si ji nepřevezme přenos. */
    private var preview: Any? = null

    private val previewObserver = object : CapturerObserver {
        override fun onCapturerStarted(success: Boolean) = Unit

        override fun onCapturerStopped() = Unit

        override fun onFrameCaptured(frame: VideoFrame) {
            sinks[CallVideoSource.Local]?.let { sink -> runCatching { sink.onFrame(frame) } }
        }
    }

    private fun camera(front: Boolean): Camera? = cameras.firstOrNull { it.front == front } ?: cameras.firstOrNull()

    private fun readCameras(): List<Camera> = runCatching {
        ensureWebRtcContext()
        NTgCalls.getMediaDevices().camera.map { info ->
            val json = runCatching { JSONObject(info.metadata) }.getOrNull()
            Camera(metadata = info.metadata, id = json?.optString("id") ?: info.name, front = json?.optBoolean("is_front") ?: false)
        }
    }.onFailure { logger.w(it) { "Kamery se nepodařilo zjistit." } }.getOrDefault(emptyList())

    /**
     * Snímek I420 (těsně za sebou Y, U, V) do rendereru. Volá se z vlákna knihovny; renderer
     * si snímek kreslí na vlastním vlákně EGL a buffer vrátí, až s ním skončí.
     */
    private fun render(source: CallVideoSource, sink: VideoSink, frame: Frame) {
        val info = frame.frame_data ?: return
        val width = info.width
        val height = info.height
        if (width <= 0 || height <= 0) return
        val ySize = width * height
        val chromaWidth = (width + 1) / 2
        val chromaSize = chromaWidth * ((height + 1) / 2)
        val size = ySize + 2 * chromaSize
        if (frame.data.size < size) return
        val pool = framePools.getOrPut(source) { ConcurrentLinkedQueue() }
        val buffer = pool.poll()?.takeIf { it.capacity() >= size }?.apply { clear() } ?: ByteBuffer.allocateDirect(size)
        buffer.put(frame.data, 0, size)
        fun plane(offset: Int, length: Int) = buffer.duplicate().apply { position(offset); limit(offset + length) }.slice()
        val i420 = JavaI420Buffer.wrap(
            width, height,
            plane(0, ySize), width,
            plane(ySize, chromaSize), chromaWidth,
            plane(ySize + chromaSize, chromaSize), chromaWidth,
        ) { if (pool.size < FRAME_POOL_DEPTH) pool.offer(buffer) }
        val video = VideoFrame(i420, info.rotation.degrees, info.absolute_capture_timestamp_ms * 1_000_000L)
        runCatching { sink.onFrame(video) }
        video.release()
    }

    override fun stop() {
        worker.execute { stopNow() }
    }

    private fun stopNow() {
        ntg?.let { instance ->
            runCatching { instance.stop(callId) }.onFailure { logger.w(it) { "Zastavení hovoru selhalo." } }
        }
        ntg = null
        stopPreview()
        held = false
        userMuted = false
        cameraOn = false
        screenOn = false
        events = null
        cameraFront = true
        framePools.clear()
        // I u hovoru, který se nespojil (zrušený při vytáčení): zvuková relace běží od začátku.
        router?.stop()
        router = null
    }

    /** WebRTC v knihovně čte kontext přes `ContextUtils`; bez něj výčet zařízení spadne. */
    private fun ensureWebRtcContext() {
        if (ContextUtils.getApplicationContext() == null) ContextUtils.initialize(appContext)
    }

    private companion object {
        // Metadata zařízení jsou u ntgcalls na Androidu syntetický JSON; čte se jen směr.
        const val MICROPHONE = "{\"is_microphone\":true}"
        const val SPEAKER = "{\"is_microphone\":false}"

        fun device(metadata: String) = AudioDescription(MediaSource.DEVICE, 48_000, 2, metadata, true)

        /**
         * Kamera bere snímky na šířku (rotaci nese snímek zvlášť) a knihovna je škáluje přesně
         * na tenhle rozměr — musí tedy mít poměr stran kamery, jinak by obraz zdeformovala.
         */
        const val CAMERA_WIDTH = 1280
        const val CAMERA_HEIGHT = 720
        const val CAMERA_FPS = 30

        /** Obrazovka se mění pomaleji než obraz z kamery; víc snímků by jen zatížilo kodér. */
        const val SCREEN_FPS = 15

        /** Zařízení obrazovky, jak ho hlásí knihovna (`MediaDevice::get_screen_devices`). */
        const val SCREEN_INPUT = """{"id":"screen"}"""

        /** Jak dlouho čekat, než služba hovoru poběží s typem `mediaProjection`. */
        const val SCREEN_SERVICE_TIMEOUT_MS = 3_000L

        /** Obraz protistrany: EXTERNAL = snímky jdou nám (`onFrames`); 0 = rozměr podle snímku. */
        val REMOTE_CAMERA = VideoDescription(MediaSource.EXTERNAL, 0, 0, 0, "", false)

        /** Renderer drží nejvýš snímek, dva; víc bufferů je rezerva. */
        const val FRAME_POOL_DEPTH = 4

        val VideoRotation.degrees: Int
            get() = when (this) {
                VideoRotation.VIDEO_ROTATION_0 -> 0
                VideoRotation.VIDEO_ROTATION_90 -> 90
                VideoRotation.VIDEO_ROTATION_180 -> 180
                VideoRotation.VIDEO_ROTATION_270 -> 270
            }

        fun CallMediaServer.toRtcServer() = RTCServer(id, ipv4, ipv6, port, username, password, turn, stun, tcp, peerTag)
    }
}

/** Kamera telefonu, jak ji zná ntgcalls: metadata `{"id": …, "is_front": …}` jdou do zdroje beze změny. */
private class Camera(val metadata: String, val id: String, val front: Boolean)
