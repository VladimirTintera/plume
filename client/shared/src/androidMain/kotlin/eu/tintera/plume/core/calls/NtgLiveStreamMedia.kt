package eu.tintera.plume.core.calls

import android.content.Context
import android.media.AudioManager
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.LiveVideo
import eu.tintera.plume.core.telegram.LiveVideoQuality
import io.github.pytgcalls.ConnectionState
import io.github.pytgcalls.NTgCalls
import io.github.pytgcalls.devices.JavaVideoCapturerModule
import io.github.pytgcalls.media.AudioDescription
import io.github.pytgcalls.media.MediaDescription
import io.github.pytgcalls.media.MediaSource
import io.github.pytgcalls.media.StreamDevice
import io.github.pytgcalls.media.StreamMode
import io.github.pytgcalls.media.StreamStatus
import io.github.pytgcalls.media.VideoDescription
import io.github.pytgcalls.models.MediaSegmentPartStatus
import io.github.pytgcalls.models.MediaSegmentQuality
import io.github.pytgcalls.models.SsrcGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.ContextUtils
import org.webrtc.EglBase
import org.webrtc.VideoSink
import java.util.concurrent.Executors

/**
 * Živý příběh a videochat přes ntgcalls: skupinový hovor jen na příjem. Pořadí drží knihovna — hovor
 * (`createCall` vrátí přihlašovací payload) → zdroje přehrávání → přihlášení přes TDLib →
 * `connect` s odpovědí serveru → obraz vysílajícího (`addIncomingVideo`). Podle odpovědi
 * jede hovor přes WebRTC, nebo po segmentech streamu, o které si knihovna říká sama
 * (`onRequestBroadcastTimestamp` / `onRequestBroadcastPart`); viz `docs/poznatky/pribehy.md`.
 *
 * Vlastní instance knihovny a vlastní vlákno, nezávislé na hovoru ve dvou ([NtgCallMedia]).
 */
class NtgLiveStreamMedia(
    context: Context,
    /** Kde běží dotazy na TDLib, o které si knihovna řekne (segmenty, čas streamu). */
    private val scope: CoroutineScope,
) : LiveStreamMedia, CallVideoSinks {

    private val logger = Logger.withTag("LiveStream")
    private val appContext = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "ntgcalls-live") }

    private var ntg: NTgCalls? = null
    private var callId = 0L
    private var host: LiveStreamHost? = null

    /** Zvuk: WebRTC v knihovně přehrává jako hovor, takže výstup vede táž cesta jako u hovoru. */
    private val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var router: CallAudioRoutes? = null

    @Volatile private var sink: VideoSink? = null
    private val frames = I420Frames()

    /** Kdy přišel poslední snímek kamery — obrazovka se kreslí, jen když kamera mlčí. */
    @Volatile private var lastCameraFrameMs = 0L
    @Volatile private var videoShown = false

    override val eglContext: EglBase.Context get() = JavaVideoCapturerModule.getSharedEGLContext()

    override fun attach(source: CallVideoSource, sink: VideoSink) {
        if (source == CallVideoSource.Live) this.sink = sink
    }

    override fun detach(source: CallVideoSource, sink: VideoSink) {
        if (source == CallVideoSource.Live && this.sink === sink) this.sink = null
    }

    override fun start(callId: Long, host: LiveStreamHost) {
        worker.execute {
            stopNow()
            runCatching {
                if (ContextUtils.getApplicationContext() == null) ContextUtils.initialize(appContext)
                this.callId = callId
                this.host = host
                val instance = NTgCalls()
                ntg = instance
                instance.onConnectionChange { id, info ->
                    if (id != callId) return@onConnectionChange
                    logger.i { "Spojení: ${info.state}" }
                    host.onConnection(
                        when (info.state) {
                            ConnectionState.CONNECTED -> CallConnection.Connected
                            ConnectionState.CONNECTING -> CallConnection.Connecting
                            ConnectionState.CLOSED -> CallConnection.Closed
                            else -> CallConnection.Failed
                        },
                    )
                }
                instance.onRemoteSourceChange { id, source ->
                    if (id != callId || source.device != StreamDevice.CAMERA) return@onRemoteSourceChange
                    if (source.state != StreamStatus.ACTIVE && videoShown) {
                        videoShown = false
                        host.onVideo(false)
                    }
                }
                instance.onFrames { id, mode, device, received ->
                    if (id != callId || mode != StreamMode.PLAYBACK) return@onFrames
                    val now = System.currentTimeMillis()
                    when (device) {
                        StreamDevice.CAMERA -> lastCameraFrameMs = now
                        StreamDevice.SCREEN -> if (now - lastCameraFrameMs < CAMERA_PRIORITY_MS) return@onFrames
                        else -> return@onFrames
                    }
                    if (!videoShown) {
                        videoShown = true
                        host.onVideo(true)
                    }
                    val target = sink ?: return@onFrames
                    received.lastOrNull()?.let { frames.render(CallVideoSource.Live, target, it) }
                }
                instance.onRequestBroadcastTimestamp { id ->
                    if (id != callId) return@onRequestBroadcastTimestamp
                    scope.launch {
                        val time = runCatching { host.timestamp() }.getOrDefault(0L)
                        onWorker(instance) { it.sendBroadcastTimestamp(id, time) }
                    }
                }
                instance.onRequestBroadcastPart { id, request ->
                    if (id != callId) return@onRequestBroadcastPart
                    scope.launch {
                        val data = runCatching {
                            host.segment(request.timestamp, request.channel_id, request.quality.toLiveQuality())
                        }.getOrNull()
                        onWorker(instance) {
                            it.sendBroadcastPart(
                                id,
                                request.segment_id,
                                request.part_id,
                                if (data != null) MediaSegmentPartStatus.SUCCESS else MediaSegmentPartStatus.NOT_READY,
                                request.quality_update,
                                data,
                            )
                        }
                    }
                }
                // Bez soupravy a sluchátek z reproduktoru, ne ze sluchátka u ucha.
                router = CallAudioRouter(audio, fallback = CallAudioOutput.Speaker).also { it.start { _, _ -> } }
                val payload = instance.createCall(callId)
                // Jen příjem: v režimu PLAYBACK je slot `microphone` zvuk OSTATNÍCH (viz
                // NtgCallMedia), kamera a obrazovka vysílajícího jdou nám jako EXTERNAL.
                instance.setStreamSources(callId, StreamMode.PLAYBACK, MediaDescription(SPEAKER, null, REMOTE_VIDEO, REMOTE_VIDEO))
                val audioSourceId = runCatching { JSONObject(payload).optLong("ssrc").toInt() }.getOrDefault(0)
                scope.launch {
                    val response = runCatching { host.join(payload, audioSourceId) }.getOrNull()
                    if (response == null) {
                        host.onConnection(CallConnection.Failed)
                        return@launch
                    }
                    onWorker(instance) {
                        runCatching {
                            it.connect(callId, response, false)
                            logger.i { "Připojeno, režim ${it.getConnectionMode(callId)}" }
                            host.onJoined()
                        }.onFailure { failure ->
                            logger.e(failure) { "Připojení k živému příběhu selhalo." }
                            host.onConnection(CallConnection.Failed)
                        }
                    }
                }
            }.onFailure { failure ->
                logger.e(failure) { "Příjem živého příběhu se nepodařilo spustit." }
                host.onConnection(CallConnection.Failed)
            }
        }
    }

    override fun addVideo(callId: Long, participantId: Long, video: LiveVideo) {
        worker.execute {
            val instance = ntg?.takeIf { this.callId == callId } ?: return@execute
            runCatching {
                instance.addIncomingVideo(
                    callId,
                    participantId,
                    video.endpoint,
                    video.sourceGroups.map { SsrcGroup(it.semantics, it.sourceIds) },
                )
            }.onFailure { logger.w(it) { "Obraz vysílajícího nejde přijmout." } }
        }
    }

    override fun removeVideo(callId: Long, endpoint: String) {
        worker.execute {
            val instance = ntg?.takeIf { this.callId == callId } ?: return@execute
            runCatching { instance.removeIncomingVideo(callId, endpoint) }
        }
    }

    override fun stop(callId: Long) {
        worker.execute { if (this.callId == callId) stopNow() }
    }

    /** Na vlákně knihovny, a jen dokud běží TATÁŽ instance (odpověď na starý příběh se zahodí). */
    private fun onWorker(instance: NTgCalls, block: (NTgCalls) -> Unit) {
        worker.execute {
            if (ntg !== instance) return@execute
            runCatching { block(instance) }.onFailure { logger.w(it) { "Odpověď knihovně selhala." } }
        }
    }

    private fun stopNow() {
        ntg?.let { instance -> runCatching { instance.stop(callId) }.onFailure { logger.w(it) { "Zastavení selhalo." } } }
        ntg = null
        host = null
        router?.stop()
        router = null
        videoShown = false
        lastCameraFrameMs = 0L
        frames.clear()
    }

    private companion object {
        /** Zařízení přehrávání, jak ho chce ntgcalls na Androidu (syntetický JSON, viz NtgCallMedia). */
        val SPEAKER = AudioDescription(MediaSource.DEVICE, 48_000, 2, "{\"is_microphone\":false}", true)

        /** EXTERNAL = snímky jdou nám (`onFrames`); 0 = rozměr podle snímku. */
        val REMOTE_VIDEO = VideoDescription(MediaSource.EXTERNAL, 0, 0, 0, "", false)

        /** Jak dlouho po posledním snímku kamery ještě nekreslit sdílenou obrazovku. */
        const val CAMERA_PRIORITY_MS = 1_000L

        fun MediaSegmentQuality?.toLiveQuality(): LiveVideoQuality? = when (this) {
            MediaSegmentQuality.THUMBNAIL -> LiveVideoQuality.Thumbnail
            MediaSegmentQuality.MEDIUM -> LiveVideoQuality.Medium
            MediaSegmentQuality.FULL -> LiveVideoQuality.Full
            MediaSegmentQuality.NONE, null -> null
        }
    }
}
