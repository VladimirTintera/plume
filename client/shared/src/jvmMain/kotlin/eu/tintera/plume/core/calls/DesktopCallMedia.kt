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
 * Androidu (`NtgCallMedia`) — hovor → zdroje → klíč z TDLib → spojení. Jen zvuk.
 *
 * - **Zvuk bere knihovna sama** (CoreAudio na macOS, Core Audio/WASAPI na Windows, Pulse/ALSA
 *   na Linuxu), nezávisle na tom, že desktop jinak zvuk přehrávat neumí. Mikrofon i výstup jsou
 *   výchozí zařízení systému; přepínat výstup se nedá (jediný výstup „Reproduktor").
 * - **Video zatím ne** ([supportsVideo] je `false`): kamera ani obraz protistrany.
 * - Všechno jde přes jedno vlákno, jako na Androidu; zpětná volání chodí z vláken knihovny.
 */
class DesktopCallMedia : CallMedia {
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

    // Zpětná volání drží tahle instance — JNA by je jinak nechala uklidit, zatímco je knihovna volá.
    private var signalingCallback: NtgNative.SignalingCallback? = null
    private var connectionCallback: NtgNative.ConnectionCallback? = null
    private var remoteSourceCallback: NtgNative.RemoteSourceCallback? = null

    private val logCallback = NtgNative.LogCallback { message, _ ->
        // Jen chyby: WebRTC na úrovni informací píše desítky řádků za vteřinu.
        if (message.level >= LOG_ERROR) logger.w { "ntgcalls: ${message.message}" }
    }

    override val supportsVideo: Boolean = false

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
                        logger.i { "Protistrana posílá obraz (${source.state}), desktop ho zatím neukáže." }
                    }
                }.also { lib.ntg_on_remote_source_change_callback(instance, it, null) }

                lib.check("create_p2p_call") { ntg_create_p2p_call(instance, callId) }
                val devices = audioDevices()
                lib.check("set_stream_sources(capture)") {
                    ntg_set_stream_sources(instance, callId, NtgNative.STREAM_MODE_CAPTURE, media(microphone = devices.first))
                }
                // V režimu PLAYBACK je slot `microphone` mikrofon PROTISTRANY (viz `NtgCallMedia`):
                // příjem zvuku ntgcalls zapne jen pro něj.
                lib.check("set_stream_sources(playback)") {
                    ntg_set_stream_sources(instance, callId, NtgNative.STREAM_MODE_PLAYBACK, media(microphone = devices.second))
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

    /** Video desktop zatím neumí ([supportsVideo]). */
    override fun setCamera(on: Boolean, front: Boolean) = Unit

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
        signalingCallback = null
        connectionCallback = null
        remoteSourceCallback = null
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

    /**
     * Mikrofon a výstup: výchozí zařízení systému. Metadata zná každý zvukový modul knihovny
     * po svém — macOS prázdné `uid` (= výchozí), Windows `index` 0 (= výchozí), Pulse a ALSA
     * id zařízení, a tam se bere první, které knihovna vypíše.
     */
    private fun audioDevices(): Pair<String, String> {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("mac") -> MAC_MICROPHONE to MAC_SPEAKER
            os.contains("win") -> WINDOWS_MICROPHONE to WINDOWS_SPEAKER
            else -> {
                val out = NtgMediaDevices()
                check(native.ntg_get_media_devices(out) == NtgNative.OK) { "ntg_get_media_devices: ${native.ntg_last_error()}" }
                try {
                    val microphone = out.microphones().firstOrNull()?.metadata ?: error("Žádný mikrofon.")
                    val speaker = out.speakers().firstOrNull()?.metadata ?: error("Žádný výstup zvuku.")
                    microphone to speaker
                } finally {
                    native.ntg_media_devices_free(out)
                }
            }
        }
    }

    private fun media(microphone: String): NtgMediaDescription.ByValue = NtgMediaDescription.ByValue().apply {
        this.microphone = NtgAudioDescription.ByReference().apply {
            media_source = NtgNative.MEDIA_SOURCE_DEVICE
            sample_rate = SAMPLE_RATE
            channel_count = CHANNELS
            input = microphone
            keep_open = 1
        }
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
