package eu.tintera.plume.core.calls

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import co.touchlab.kermit.Logger
import io.github.pytgcalls.ConnectionState
import io.github.pytgcalls.NTgCalls
import io.github.pytgcalls.media.AudioDescription
import io.github.pytgcalls.media.MediaDescription
import io.github.pytgcalls.media.MediaSource
import io.github.pytgcalls.media.StreamMode
import io.github.pytgcalls.p2p.RTCServer
import org.webrtc.ContextUtils
import java.util.concurrent.Executors

/**
 * Přenos hovoru přes ntgcalls (LGPL-3.0, `libntgcalls.so` s WebRTC uvnitř, viz
 * `docs/poznatky/hovory.md`). Pořadí volání drží knihovna: hovor → zdroje (mikrofon,
 * reproduktor) → klíč z TDLib (`skipExchange`, výměnu udělal TDLib) → spojení.
 *
 * Všechno jde přes jedno vlákno: knihovna není dělaná na souběžná volání a první dotyk
 * (`getProtocol`) nahrává ~20 MB knihovnu — na hlavním vlákně by to bylo znát.
 */
class NtgCallMedia(context: Context) : CallMedia {

    private val logger = Logger.withTag("NtgCallMedia")
    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "ntgcalls") }

    private var ntg: NTgCalls? = null
    private var callId = 0L
    private var previousAudioMode = AudioManager.MODE_NORMAL

    override val protocol: CallMediaProtocol by lazy {
        worker.submit<CallMediaProtocol> {
            ensureWebRtcContext()
            val p = NTgCalls.getProtocol()
            CallMediaProtocol(minLayer = p.min_layer, maxLayer = p.max_layer, versions = p.library_versions)
        }.get()
    }

    override fun start(session: CallMediaSession, events: CallMediaEvents) {
        worker.execute {
            runCatching {
                stopNow()
                ensureWebRtcContext()
                callId = session.callId.toLong()
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
                instance.createP2pCall(callId)
                instance.setStreamSources(callId, StreamMode.CAPTURE, MediaDescription(device(MICROPHONE), null, null, null))
                instance.setStreamSources(callId, StreamMode.PLAYBACK, MediaDescription(null, device(SPEAKER), null, null))
                instance.skipExchange(callId, session.encryptionKey, session.isOutgoing)
                instance.connectP2p(
                    callId,
                    session.servers.map { it.toRtcServer() },
                    session.versions,
                    session.allowP2p,
                    session.customParameters,
                )
                beginAudioSession()
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
            runCatching { ntg?.let { if (muted) it.mute(callId) else it.unmute(callId) } }
        }
    }

    override fun setSpeaker(on: Boolean) {
        worker.execute { runCatching { route(speaker = on) }.onFailure { logger.w(it) { "Přepnutí reproduktoru selhalo." } } }
    }

    /**
     * Sluchátko, nebo reproduktor. Od Androidu 12 přes `setCommunicationDevice` (starší
     * `isSpeakerphoneOn` tam pro hovory nefunguje spolehlivě), do té doby postaru.
     */
    private fun route(speaker: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val wanted = if (speaker) AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            audio.availableCommunicationDevices.firstOrNull { it.type == wanted }
                ?.let { audio.setCommunicationDevice(it) }
                ?: audio.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audio.isSpeakerphoneOn = speaker
        }
    }

    override fun stop() {
        worker.execute { stopNow() }
    }

    private fun stopNow() {
        val instance = ntg ?: return
        ntg = null
        runCatching { instance.stop(callId) }.onFailure { logger.w(it) { "Zastavení hovoru selhalo." } }
        endAudioSession()
    }

    /** Hovor jde do sluchátka, ne do reproduktoru, a systém ví, že běží hovor (ozvěna, hlasitost). */
    private fun beginAudioSession() {
        previousAudioMode = audio.mode
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        route(speaker = false)
    }

    private fun endAudioSession() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audio.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audio.isSpeakerphoneOn = false
            }
            audio.mode = previousAudioMode
        }
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

        fun CallMediaServer.toRtcServer() = RTCServer(id, ipv4, ipv6, port, username, password, turn, stun, tcp, peerTag)
    }
}
