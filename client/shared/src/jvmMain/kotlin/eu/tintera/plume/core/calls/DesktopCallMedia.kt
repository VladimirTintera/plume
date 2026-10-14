package eu.tintera.plume.core.calls

import co.touchlab.kermit.Logger
import com.sun.jna.Memory
import com.sun.jna.Platform
import com.sun.jna.Pointer
import java.io.File
import com.sun.jna.ptr.ByteByReference
import java.util.concurrent.Executors

/**
 * Přenos hovoru na desktopu: ntgcalls přes C API ([NtgNative]), stejné pořadí volání jako na
 * Androidu (`NtgCallMedia`) — hovor → zdroje → klíč z TDLib → spojení.
 *
 * - **Zvuk bere knihovna sama** (CoreAudio na macOS, Core Audio/WASAPI na Windows, Pulse/ALSA
 *   na Linuxu), nezávisle na tom, že desktop jinak zvuk přehrávat neumí. Mikrofon i výstup jsou
 *   výchozí zařízení systému; přepínat výstup se nedá (jediný výstup „Reproduktor").
 * - **Video**: kameru snímá knihovna sama (macOS AVFoundation, jinde WebRTC), vybírá se první
 *   ze seznamu `ntg_get_media_devices`. Snímky naší kamery (CAPTURE) i protistrany (PLAYBACK,
 *   slot `camera` jako EXTERNAL) chodí jako I420 do `ntg_on_frames_callback` a odtud do
 *   [VideoFrames], které kreslí `CallVideo`. **Náhled kamery je až od spojení**: na Androidu
 *   ho při vytáčení drží sdílená kamera mimo knihovnu, tady by na to byla potřeba druhá
 *   instance knihovny.
 * - Všechno jde přes jedno vlákno, jako na Androidu; zpětná volání chodí z vláken knihovny.
 */
class DesktopCallMedia : CallMedia {
    /** Obraz hovoru pro `CallVideo`. */
    internal val video = VideoFrames()

    private val logger = Logger.withTag("DesktopCallMedia")
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "ntgcalls").apply { isDaemon = true } }

    /** Knihovna se nahrává až při prvním hovoru (35 MB), ne při startu aplikace. */
    private val native: NtgNative by lazy {
        NtgNative.load().also { lib ->
            lib.ntg_set_log_callback(logCallback, null)
            logger.i { "ntgcalls ${lib.ntg_get_version()}" }
        }
    }

    private var handle: Pointer? = null
    private var callId = 0L
    private var userMuted = false
    private var held = false
    private var cameraOn = false
    private var devices: Devices? = null
    private var pixels = ByteArray(0)

    // Zpětná volání drží tahle instance — JNA by je jinak nechala uklidit, zatímco je knihovna volá.
    private var signalingCallback: NtgNative.SignalingCallback? = null
    private var connectionCallback: NtgNative.ConnectionCallback? = null
    private var remoteSourceCallback: NtgNative.RemoteSourceCallback? = null
    private var framesCallback: NtgNative.FramesCallback? = null

    private val logCallback = NtgNative.LogCallback { message, _ ->
        // Jen chyby: WebRTC na úrovni informací píše desítky řádků za vteřinu.
        if (message.level >= LOG_ERROR) logger.w { "ntgcalls: ${message.message}" }
    }

    /** Přijímat obraz jde vždycky; když kamera chybí, zapnutí kamery jen nic neukáže. */
    override val supportsVideo: Boolean = true

    /** Kamera je jedna (vybírá se první), přepínat není mezi čím. */
    override val canSwitchCamera: Boolean = false

    /** Kameru otevírá knihovna až se spojením (viz třída). */
    override val previewsBeforeConnect: Boolean = false

    override val protocol: CallMediaProtocol by lazy {
        worker.submit<CallMediaProtocol> {
            val out = NtgProtocol()
            check(native.ntg_get_protocol(out) == NtgNative.OK) { "ntg_get_protocol: ${native.ntg_last_error()}" }
            CallMediaProtocol(minLayer = out.min_layer, maxLayer = out.max_layer, versions = out.versions())
                .also { native.ntg_protocol_free(out) }
        }.get()
    }

    override fun startAudio(onOutputs: (List<CallAudioOutput>, CallAudioOutput) -> Unit) {
        // Výstup vybírá systém; jediná možnost je ta, kterou má počítač nastavenou.
        onOutputs(listOf(CallAudioOutput.Speaker), CallAudioOutput.Speaker)
    }

    override fun start(session: CallMediaSession, events: CallMediaEvents) {
        worker.execute {
            runCatching {
                stopNow()
                val lib = native
                callId = session.callId.toLong()
                val instance = lib.ntg_instance_create()
                handle = instance
                signalingCallback = NtgNative.SignalingCallback { _, _, data, length, _ ->
                    data?.getByteArray(0, length.toInt())?.let(events::onSignaling)
                }.also { lib.ntg_on_signaling_data_callback(instance, it, null) }
                connectionCallback = NtgNative.ConnectionCallback { _, _, info, _ ->
                    logger.i { "Spojení: ${info.state}" }
                    events.onConnection(
                        when (info.state) {
                            NtgNative.CONNECTION_CONNECTED -> CallConnection.Connected
                            NtgNative.CONNECTION_CONNECTING -> CallConnection.Connecting
                            NtgNative.CONNECTION_CLOSED -> CallConnection.Closed
                            else -> CallConnection.Failed
                        },
                    )
                }.also { lib.ntg_on_connection_change_callback(instance, it, null) }
                remoteSourceCallback = NtgNative.RemoteSourceCallback { _, _, source, _ ->
                    if (source.device == NtgNative.STREAM_DEVICE_CAMERA) {
                        val active = source.state == NtgNative.STREAM_STATUS_ACTIVE
                        if (!active) video.clear(CallVideoSource.Remote)
                        events.onRemoteVideo(active)
                    }
                }.also { lib.ntg_on_remote_source_change_callback(instance, it, null) }
                val seen = mutableSetOf<Pair<Int, Int>>()
                framesCallback = NtgNative.FramesCallback { _, _, mode, device, frames, count, _ ->
                    if (synchronized(seen) { seen.add(mode to device) }) logger.d { "První snímky: režim $mode, zařízení $device." }
                    if (device == NtgNative.STREAM_DEVICE_CAMERA && frames != null && count > 0) {
                        val source = if (mode == NtgNative.STREAM_MODE_CAPTURE) CallVideoSource.Local else CallVideoSource.Remote
                        // Z dávky stačí poslední snímek, starší by se stejně hned překreslil.
                        render(source, NtgFrame(frames.share((count - 1) * FRAME_SIZE)))
                    }
                }.also { lib.ntg_on_frames_callback(instance, it, null) }

                lib.check("create_p2p_call") { ntg_create_p2p_call(instance, callId) }
                val found = devices()
                devices = found
                lib.check("set_stream_sources(capture)") {
                    ntg_set_stream_sources(instance, callId, NtgNative.STREAM_MODE_CAPTURE, capture(found))
                }
                // V režimu PLAYBACK je slot `microphone` mikrofon PROTISTRANY (viz `NtgCallMedia`):
                // příjem zvuku ntgcalls zapne jen pro něj. Slot `camera` jako EXTERNAL = obraz
                // protistrany jde nám (`ntg_on_frames_callback`), bez něj by ho knihovna nepřijala.
                lib.check("set_stream_sources(playback)") {
                    ntg_set_stream_sources(
                        instance,
                        callId,
                        NtgNative.STREAM_MODE_PLAYBACK,
                        media(microphone = found.speaker, camera = REMOTE_CAMERA),
                    )
                }
                lib.check("skip_exchange") {
                    ntg_skip_exchange(instance, callId, session.encryptionKey, session.encryptionKey.size.toLong(), session.isOutgoing.toByte())
                }
                val servers = rtcServers(session.servers)
                lib.check("connect_p2p") {
                    ntg_connect_p2p(
                        instance,
                        callId,
                        servers,
                        session.servers.size.toLong(),
                        session.versions.toTypedArray(),
                        session.versions.size.toLong(),
                        session.allowP2p.toByte(),
                        session.customParameters,
                    )
                }
            }.onFailure { failure ->
                logger.e(failure) { "Přenos hovoru se nepodařilo spustit." }
                events.onConnection(CallConnection.Failed)
            }
        }
    }

    override fun receiveSignaling(data: ByteArray) {
        worker.execute {
            val instance = handle ?: return@execute
            runCatching { native.ntg_send_signaling_data(instance, callId, data, data.size.toLong()) }
                .onFailure { logger.w(it) { "Signalizaci se nepodařilo předat." } }
        }
    }

    override fun setMuted(muted: Boolean) {
        worker.execute {
            userMuted = muted
            if (!held) toggle(if (muted) NtgNative::ntg_mute else NtgNative::ntg_unmute)
        }
    }

    override fun setHeld(held: Boolean) {
        worker.execute {
            this.held = held
            if (held) {
                toggle(NtgNative::ntg_mute)
                toggle(NtgNative::ntg_pause)
            } else {
                toggle(NtgNative::ntg_resume)
                if (!userMuted) toggle(NtgNative::ntg_unmute)
            }
        }
    }

    /** Výstup vybírá systém (viz třída). */
    override fun setOutput(output: CallAudioOutput) = Unit

    /**
     * Zapne nebo vypne kameru. Před spojením si jen zapamatuje, co chtít ([start] ji pak
     * zahrne do zdrojů); za hovoru přenastaví zdroje CAPTURE. Přední / zadní desktop nezná.
     */
    override fun setCamera(on: Boolean, front: Boolean) {
        worker.execute {
            if (cameraOn == on) return@execute
            cameraOn = on
            if (!on) video.clear(CallVideoSource.Local)
            val instance = handle ?: return@execute
            val found = devices ?: return@execute
            runCatching {
                native.check("set_stream_sources(camera $on)") {
                    ntg_set_stream_sources(instance, callId, NtgNative.STREAM_MODE_CAPTURE, capture(found))
                }
            }.onFailure { logger.w(it) { "Kameru nejde ${if (on) "zapnout" else "vypnout"}." } }
        }
    }

    override fun stop() {
        worker.execute { stopNow() }
    }

    private fun stopNow() {
        val instance = handle ?: return
        runCatching {
            native.ntg_stop(instance, callId)
            native.ntg_instance_destroy(instance)
        }.onFailure { logger.w(it) { "Zastavení hovoru selhalo." } }
        handle = null
        held = false
        userMuted = false
        cameraOn = false
        devices = null
        signalingCallback = null
        connectionCallback = null
        remoteSourceCallback = null
        framesCallback = null
        video.clearAll()
    }

    /** Snímek z knihovny: data platí jen během zpětného volání, proto se hned zkopírují. */
    private fun render(source: CallVideoSource, frame: NtgFrame) {
        val info = frame.frame_data
        val width = info.widthPx
        val height = info.heightPx
        val data = frame.data ?: return
        if (width <= 0 || height <= 0) return
        val size = width * height + 2 * ((width + 1) / 2) * ((height + 1) / 2)
        if (frame.data_len < size) return
        runCatching {
            synchronized(this) {
                if (pixels.size < size) pixels = ByteArray(size)
                data.read(0, pixels, 0, size)
                video.push(source, pixels, width, height, info.rotationDegrees)
            }
        }.onFailure { logger.w(it) { "Snímek se nepodařilo vykreslit." } }
    }

    private fun capture(found: Devices): NtgMediaDescription.ByValue {
        val camera = found.camera?.takeIf { cameraOn }
        if (cameraOn && camera == null) logger.w { "Kamera není, hovor jde bez obrazu." }
        return media(
            microphone = found.microphone,
            camera = camera?.let {
                NtgVideoDescription.ByReference().apply {
                    media_source = NtgNative.MEDIA_SOURCE_DEVICE
                    width = CAMERA_WIDTH
                    height = CAMERA_HEIGHT
                    fps = CAMERA_FPS
                    input = it
                    keep_open = 1
                }
            },
        )
    }

    private fun toggle(action: NtgNative.(Pointer, Long, ByteByReference) -> Int) {
        val instance = handle ?: return
        runCatching { native.action(instance, callId, ByteByReference()) }
            .onFailure { logger.w(it) { "Přepnutí zvuku hovoru selhalo." } }
    }

    private inline fun NtgNative.check(what: String, call: NtgNative.() -> Int) {
        val result = call()
        check(result == NtgNative.OK) { "ntg_$what: $result ${ntg_last_error()}" }
    }

    /** Metadata zařízení pro `ntg_set_stream_sources` (JSON, jak ho chce knihovna). */
    private data class Devices(val microphone: String, val speaker: String, val camera: String?)

    /**
     * Mikrofon a výstup: výchozí zařízení systému. Metadata zná každý zvukový modul knihovny
     * po svém — macOS prázdné `uid` (= výchozí), Windows `index` 0 (= výchozí), Pulse a ALSA
     * id zařízení, a tam se bere první, které knihovna vypíše. Kamera je všude první ze seznamu
     * (`{"id": …}`); na macOS ji seznam dává přes AVFoundation.
     */
    private fun devices(): Devices {
        val os = System.getProperty("os.name").lowercase()
        val out = NtgMediaDevices()
        check(native.ntg_get_media_devices(out) == NtgNative.OK) { "ntg_get_media_devices: ${native.ntg_last_error()}" }
        try {
            val camera = out.cameras().firstOrNull()
            logger.i { "Kamera: ${camera?.name ?: "žádná"}." }
            return when {
                os.contains("mac") -> Devices(MAC_MICROPHONE, MAC_SPEAKER, camera?.metadata)
                os.contains("win") -> Devices(WINDOWS_MICROPHONE, WINDOWS_SPEAKER, camera?.metadata)
                else -> Devices(
                    microphone = out.microphones().firstOrNull()?.metadata ?: error("Žádný mikrofon."),
                    speaker = out.speakers().firstOrNull()?.metadata ?: error("Žádný výstup zvuku."),
                    camera = camera?.metadata,
                )
            }
        } finally {
            native.ntg_media_devices_free(out)
        }
    }

    private fun media(microphone: String, camera: NtgVideoDescription.ByReference? = null): NtgMediaDescription.ByValue =
        NtgMediaDescription.ByValue().apply {
            this.microphone = NtgAudioDescription.ByReference().apply {
                media_source = NtgNative.MEDIA_SOURCE_DEVICE
                sample_rate = SAMPLE_RATE
                channel_count = CHANNELS
                input = microphone
                keep_open = 1
            }
            this.camera = camera
        }

    /**
     * Souvislé pole struktur (`toArray`), jak ho C čeká; `peer_tag` drží [Memory] až do volání.
     * JNA prázdné pole struktur neumí, proto aspoň jedna (počet se předává zvlášť).
     */
    private fun rtcServers(servers: List<CallMediaServer>): Array<NtgRtcServer> {
        @Suppress("UNCHECKED_CAST")
        val array = NtgRtcServer().toArray(servers.size.coerceAtLeast(1)) as Array<NtgRtcServer>
        servers.forEachIndexed { index, server ->
            array[index].apply {
                id = server.id
                ipv4 = server.ipv4
                ipv6 = server.ipv6
                port = server.port.toShort()
                username = server.username
                password = server.password
                turn = server.turn.toByte()
                stun = server.stun.toByte()
                tcp = server.tcp.toByte()
                server.peerTag?.let { tag ->
                    peer_tag = Memory(tag.size.toLong()).apply { write(0, tag, 0, tag.size) }
                    peer_tag_len = tag.size.toLong()
                }
            }
        }
        return array
    }

    private fun Boolean.toByte(): Byte = if (this) 1 else 0

    companion object {
        /**
         * Knihovna je k dispozici: přibalená v resources pod prefixem JNA (`downloadNtgcalls`
         * v Gradlu), nebo ve složce z `jna.library.path`. Jen se hledá, nenahrává — 35 MB se
         * nahraje až s prvním hovorem.
         */
        fun available(): Boolean {
            val file = System.mapLibraryName(NtgNative.LIBRARY)
            val bundled = DesktopCallMedia::class.java.classLoader.getResource("${Platform.RESOURCE_PREFIX}/$file") != null
            val onPath = System.getProperty("jna.library.path")?.split(File.pathSeparator)?.any { File(it, file).isFile } == true
            return bundled || onPath
        }

        /** Velikost `ntg_frame` v poli snímků (podle zarovnání platformy, jak ho počítá JNA). */
        private val FRAME_SIZE: Long by lazy { NtgFrame().size().toLong() }

        /** Obraz protistrany: EXTERNAL = snímky jdou nám; 0 = rozměr podle snímku. */
        private val REMOTE_CAMERA: NtgVideoDescription.ByReference
            get() = NtgVideoDescription.ByReference().apply {
                media_source = NtgNative.MEDIA_SOURCE_EXTERNAL
                input = ""
            }

        // Jako na Androidu; knihovna vybere nejbližší formát kamery a přeškáluje na tenhle.
        private const val CAMERA_WIDTH: Short = 1280
        private const val CAMERA_HEIGHT: Short = 720
        private const val CAMERA_FPS: Byte = 30

        private const val SAMPLE_RATE = 48_000
        private const val CHANNELS: Byte = 2
        private const val LOG_ERROR = 8

        // Metadata zařízení jsou u ntgcalls JSON; čte se směr a identifikace zařízení.
        private const val MAC_MICROPHONE = """{"is_microphone":true,"uid":""}"""
        private const val MAC_SPEAKER = """{"is_microphone":false,"uid":""}"""
        private const val WINDOWS_MICROPHONE = """{"is_microphone":true,"index":0,"uid":"","automatic_restart":true}"""
        private const val WINDOWS_SPEAKER = """{"is_microphone":false,"index":0,"uid":"","automatic_restart":true}"""
    }
}
