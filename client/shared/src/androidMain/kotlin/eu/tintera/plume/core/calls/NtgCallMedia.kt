package eu.tintera.plume.core.calls

import android.content.Context
import android.media.AudioManager
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
class NtgCallMedia internal constructor(
    context: Context,
    /** Kdo vede zvuk: Telecom, nebo náhradně Plume sám (viz [CallAudioRoutes]). */
    private val routes: () -> CallAudioRoutes,
) : CallMedia {

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
                // V režimu PLAYBACK je slot `microphone` mikrofon PROTISTRANY: příjem zvuku
                // ntgcalls zapne jen pro něj (`StreamManager::optimize_sources`). Reproduktor
                // ve slotu `speaker` dal ticho — spojení naváže, ale příchozí kanál nevznikne.
                instance.setStreamSources(callId, StreamMode.PLAYBACK, MediaDescription(device(SPEAKER), null, null, null))
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

    override fun stop() {
        worker.execute { stopNow() }
    }

    private fun stopNow() {
        ntg?.let { instance ->
            runCatching { instance.stop(callId) }.onFailure { logger.w(it) { "Zastavení hovoru selhalo." } }
        }
        ntg = null
        held = false
        userMuted = false
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

        fun CallMediaServer.toRtcServer() = RTCServer(id, ipv4, ipv6, port, username, password, turn, stun, tcp, peerTag)
    }
}
