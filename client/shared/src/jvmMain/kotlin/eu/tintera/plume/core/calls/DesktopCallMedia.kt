package eu.tintera.plume.core.calls

import eu.tintera.plume.core.platform.desktopDataDirectory
import co.touchlab.kermit.Logger
import com.sun.jna.Memory
import com.sun.jna.Platform
import com.sun.jna.Pointer
import java.io.File
import com.sun.jna.ptr.ByteByReference
import com.sun.jna.ptr.LongByReference
import java.util.concurrent.Executors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * Přenos hovoru na desktopu: ntgcalls přes C API ([NtgNative]), stejné pořadí volání jako na
 * Androidu (`NtgCallMedia`) — hovor → zdroje → klíč z TDLib → spojení.
 *
 * - **Zvuk bere knihovna sama** (CoreAudio na macOS, Core Audio/WASAPI na Windows, Pulse/ALSA
 *   na Linuxu), nezávisle na tom, že desktop jinak zvuk přehrávat neumí. Mikrofon je výchozí
 *   zařízení systému; výstup jde na macOS za hovoru přepnout ([startAudio], [setOutput]).
 * - **Video**: kameru snímá knihovna sama (macOS AVFoundation, jinde WebRTC), vybírá se první
 *   ze seznamu `ntg_get_media_devices`. Snímky naší kamery (CAPTURE) i protistrany (PLAYBACK,
 *   slot `camera` jako EXTERNAL) chodí jako I420 do `ntg_on_frames_callback` a odtud do
 *   [VideoFrames], které kreslí `CallVideo`.
 * - **Náhled kamery do spojení dělá Plume sám** ([MacCamera], jen macOS): knihovna kameru
 *   otevře až se spojením. Před startem přenosu se náhled zastaví (kamera je volná) a jeho
 *   poslední snímek zůstane vidět, dokud nepřijde první snímek z knihovny — jako
 *   `SharedCamera` na Androidu. Windows a Linux náhled před spojením nemají.
 * - Všechno jde přes jedno vlákno, jako na Androidu; zpětná volání chodí z vláken knihovny.
 */
class DesktopCallMedia : CallMedia {
    /** Obraz hovoru pro `CallVideo`. */
    internal val video = VideoFrames()

    private val logger = Logger.withTag("DesktopCallMedia")
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "ntgcalls").apply { isDaemon = true } }

    /**
     * Knihovna se nahrává až při prvním hovoru (35 MB), ne při startu aplikace — a z dat
     * aplikace, kam se vybalí jednou ([cachedNativeLibrary]), ne z dočasné kopie při každém startu.
     */
    private val native: NtgNative by lazy {
        NtgNative.load(cachedNativeLibrary(NtgNative.LIBRARY, desktopDataDirectory, logger)).also { lib ->
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
        // Do logu jen chyby: WebRTC na úrovni informací píše desítky řádků za vteřinu.
        if (message.level >= LOG_ERROR) logger.w { "ntgcalls: ${message.message}" }
        // Do výpisu k hovoru všechno — opakování prořeže `CallDetailLog`.
        synchronized(detailLock) { detailLog?.let { log -> writeDetail(log, message) } }
    }

    /** Podrobný výpis běžícího hovoru (Nastavení → Pomoc, `docs/poznatky/hovory.md`, „Deník hovorů"). */
    private var detailLog: CallDetailLog? = null
    private val detailLock = Any()

    private fun writeDetail(log: CallDetailLog, message: NtgLogMessage) {
        val text = message.message?.trimEnd() ?: return
        val file = message.file?.substringAfterLast('/')?.substringAfterLast('\\').orEmpty()
        val tag = if (message.source == LOG_SOURCE_SELF) "ntgcalls/$file" else file
        val level = when {
            message.level >= LOG_ERROR -> 'E'
            message.level >= LOG_WARNING -> 'W'
            message.level >= LOG_INFO -> 'I'
            else -> 'D'
        }
        log.write(tag, text, "${DETAIL_STAMP.format(java.util.Date())} $level $tag:${message.line}: $text")
    }

    /** Přijímat obraz jde vždycky; když kamera chybí, zapnutí kamery jen nic neukáže. */
    override val supportsVideo: Boolean = true

    /** Kamera je jedna (vybírá se první), přepínat není mezi čím. */
    override val canSwitchCamera: Boolean = false

    /** Náhled do spojení jen přes [MacCamera] (viz třída). */
    override val previewsBeforeConnect: Boolean get() = MacCamera.available

    private var previewing = false

    /** Protistrana posílá kameru / obrazovku; obraz protistrany je, když aspoň jedno. */
    private var remoteCamera = false
    private var remoteScreen = false

    /** Výstup zvolený za hovoru (metadata zařízení); `null` = výchozí výstup systému. */
    private var selectedOutput: String? = null

    /** Výstup, který má hrát (zvolený, jinak výchozí systému); `null` = nevíme (Windows, Linux). */
    private var currentOutput: String? = null

    /** Na čem přehrávání protistrany v knihovně opravdu běží. */
    private var playback: String? = null

    /** Komu hlásit výstupy (repository), od [startAudio] do [stop]. */
    private var outputsListener: ((List<CallAudioOutput>, CallAudioOutput) -> Unit)? = null

    override val protocol: CallMediaProtocol by lazy {
        worker.submit<CallMediaProtocol> {
            val out = NtgProtocol()
            check(native.ntg_get_protocol(out) == NtgNative.OK) { "ntg_get_protocol: ${native.ntg_last_error()}" }
            CallMediaProtocol(minLayer = out.min_layer, maxLayer = out.max_layer, versions = out.versions())
                .also { native.ntg_protocol_free(out) }
        }.get()
    }

    /**
     * Výstupy zvuku: na macOS výstupní zařízení ze seznamu knihovny (jméno, druh podle
     * typu připojení z CoreAudio) a vybraný je výchozí výstup systému. Seznam se čte
     * s vytočením / přijetím; sluchátka připojená až za hovoru v něm nebudou. Windows
     * a Linux zatím jen výchozí výstup systému (neověřené platformy).
     */
    override fun startAudio(onOutputs: (List<CallAudioOutput>, CallAudioOutput) -> Unit) {
        worker.execute {
            selectedOutput = null
            currentOutput = null
            outputsListener = onOutputs
            reportOutputs()
            // Zařízení připojená / odpojená za hovoru: seznam znovu a případně jiný výstup.
            MacAudio.watch { worker.execute { reportOutputs() } }
        }
    }

    /**
     * Přečte výstupy, nahlásí je a zajistí, že zvuk jde do vybraného:
     * - **zvolené zařízení zmizelo** (odpojená sluchátka) → zpátky na výchozí výstup systému,
     * - **nic zvoleného** → výchozí výstup systému, i když se změní (po připojení sluchátek na
     *   ně macOS sám přepne, a hovor má jít s ním — jako na Androidu).
     */
    private fun reportOutputs() {
        val listener = outputsListener ?: return
        val outputs = runCatching { macOutputs() }
            .onFailure { logger.w(it) { "Výstupy zvuku nejde zjistit." } }
            .getOrDefault(emptyList())
        if (outputs.isEmpty()) {
            listener(listOf(CallAudioOutput.Speaker), CallAudioOutput.Speaker)
            return
        }
        if (selectedOutput != null && outputs.none { it.id == selectedOutput }) {
            logger.i { "Zvolený výstup zvuku zmizel, zpátky na výchozí." }
            selectedOutput = null
        }
        val default = MacAudio.defaultOutputUid()
        val current = outputs.firstOrNull { it.id == selectedOutput }
            ?: outputs.firstOrNull { uid(it.id) == default }
            ?: outputs.first()
        logger.d { "Výstupy zvuku: ${outputs.joinToString { "${it.name} (${it.kind})" }}, hraje ${current.name}." }
        listener(outputs, current)
        currentOutput = current.id
        current.id?.let(::applyPlayback)
    }

    private fun macOutputs(): List<CallAudioOutput> {
        if (!System.getProperty("os.name").lowercase().contains("mac") || !MacAudio.available) return emptyList()
        val out = NtgMediaDevices()
        check(native.ntg_get_media_devices(out) == NtgNative.OK) { "ntg_get_media_devices: ${native.ntg_last_error()}" }
        try {
            return out.speakers().mapNotNull { device ->
                val metadata = device.metadata ?: return@mapNotNull null
                val uid = uid(metadata) ?: return@mapNotNull null
                val kind = macOutputKind(MacAudio.transportType(uid)) ?: return@mapNotNull null
                CallAudioOutput(kind = kind, name = device.name, id = metadata)
            }
        } finally {
            native.ntg_media_devices_free(out)
        }
    }

    /** `uid` z metadat zařízení (`{"is_microphone":false,"uid":"…"}`). */
    private fun uid(metadata: String?): String? = metadata?.let {
        runCatching { Json.parseToJsonElement(it).jsonObject["uid"]?.jsonPrimitive?.content }.getOrNull()
    }

    override fun start(session: CallMediaSession, events: CallMediaEvents) {
        worker.execute {
            runCatching {
                stopNow()
                val lib = native
                callId = session.callId.toLong()
                synchronized(detailLock) {
                    detailLog?.close()
                    detailLog = CallDetailLog.open(File(desktopDataDirectory, CALL_JOURNAL_DIRECTORY).path, callId)
                }
                val instance = lib.ntg_instance_create()
                handle = instance
                signalingCallback = NtgNative.SignalingCallback { _, _, data, length, _ ->
                    data?.getByteArray(0, length.toInt())?.let(events::onSignaling)
                }.also { lib.ntg_on_signaling_data_callback(instance, it, null) }
                connectionCallback = NtgNative.ConnectionCallback { _, _, info, _ ->
                    logger.i { "Spojení: ${NtgNative.connectionName(info.state)}" }
                    events.onConnection(
                        when (info.state) {
                            NtgNative.CONNECTION_CONNECTED -> CallConnection.Connected
                            NtgNative.CONNECTION_CONNECTING -> CallConnection.Connecting
                            NtgNative.CONNECTION_CLOSED -> CallConnection.Closed
                            else -> CallConnection.Failed
                        },
                    )
                }.also { lib.ntg_on_connection_change_callback(instance, it, null) }
                remoteCamera = false
                remoteScreen = false
                remoteSourceCallback = NtgNative.RemoteSourceCallback { _, _, source, _ ->
                    // Obraz protistrany je kamera, nebo sdílená obrazovka (v hovoru ve dvou jedno z nich).
                    val active = source.state == NtgNative.STREAM_STATUS_ACTIVE
                    when (source.device) {
                        NtgNative.STREAM_DEVICE_CAMERA -> remoteCamera = active
                        NtgNative.STREAM_DEVICE_SCREEN -> {
                            remoteScreen = active
                            events.onRemoteScreen(active)
                        }
                        else -> return@RemoteSourceCallback
                    }
                    if (!remoteCamera && !remoteScreen) video.clear(CallVideoSource.Remote)
                    events.onRemoteVideo(remoteCamera || remoteScreen)
                }.also { lib.ntg_on_remote_source_change_callback(instance, it, null) }
                val seen = mutableSetOf<Pair<Int, Int>>()
                framesCallback = NtgNative.FramesCallback { _, _, mode, device, frames, count, _ ->
                    if (synchronized(seen) { seen.add(mode to device) }) logger.d { "První snímky: režim $mode, zařízení $device." }
                    val remoteScreenFrame = device == NtgNative.STREAM_DEVICE_SCREEN && mode == NtgNative.STREAM_MODE_PLAYBACK
                    if ((device == NtgNative.STREAM_DEVICE_CAMERA || remoteScreenFrame) && frames != null && count > 0) {
                        val source = if (mode == NtgNative.STREAM_MODE_CAPTURE) CallVideoSource.Local else CallVideoSource.Remote
                        // Z dávky stačí poslední snímek, starší by se stejně hned překreslil.
                        render(source, NtgFrame(frames.share((count - 1) * FRAME_SIZE)))
                    }
                }.also { lib.ntg_on_frames_callback(instance, it, null) }

                lib.check("create_p2p_call") { ntg_create_p2p_call(instance, callId) }
                // Náhled pryč DŘÍV, než kameru otevře knihovna; jeho poslední snímek zůstává.
                stopPreview()
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
                        media(microphone = (currentOutput ?: found.speaker).also { playback = it }, camera = REMOTE_CAMERA, screen = REMOTE_CAMERA),
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

    /**
     * Přepne výstup: za hovoru přenastaví zdroje PLAYBACK (knihovna znovu otevře AudioQueue
     * s jiným `uid`, krátká mezera ve zvuku). Před spojením si jen zapamatuje volbu.
     */
    override fun setOutput(output: CallAudioOutput) {
        worker.execute {
            val metadata = output.id ?: return@execute
            selectedOutput = metadata
            currentOutput = metadata
            applyPlayback(metadata)
        }
    }

    /** Přehrávání protistrany na zařízení [metadata]; bez běžícího přenosu jen čeká na [start]. */
    private fun applyPlayback(metadata: String) {
        val instance = handle ?: return
        if (playback == metadata) return
        runCatching {
            native.check("set_stream_sources(output)") {
                ntg_set_stream_sources(instance, callId, NtgNative.STREAM_MODE_PLAYBACK, media(microphone = metadata, camera = REMOTE_CAMERA, screen = REMOTE_CAMERA))
            }
            playback = metadata
        }.onFailure { logger.w(it) { "Výstup zvuku nejde přepnout." } }
    }

    /**
     * Zapne nebo vypne kameru. Před spojením si jen zapamatuje, co chtít ([start] ji pak
     * zahrne do zdrojů); za hovoru přenastaví zdroje CAPTURE. Přední / zadní desktop nezná.
     */
    override fun setCamera(on: Boolean, front: Boolean) {
        worker.execute {
            if (cameraOn == on) return@execute
            cameraOn = on
            if (!on) video.clear(CallVideoSource.Local)
            val instance = handle ?: run {
                // Ještě se nespojilo (vytáčení, zvonění): náhled dělá Plume sám.
                if (on) startPreview() else stopPreview()
                return@execute
            }
            val found = devices ?: return@execute
            runCatching {
                native.check("set_stream_sources(camera $on)") {
                    ntg_set_stream_sources(instance, callId, NtgNative.STREAM_MODE_CAPTURE, capture(found))
                }
            }.onFailure { logger.w(it) { "Kameru nejde ${if (on) "zapnout" else "vypnout"}." } }
        }
    }

    /**
     * Čas přehrávání a zachytávání pro deník hovorů. Počítadlo přenesených bajtů za proces desktop
     * nemá (na Androidu `TrafficStats`) — zaseknutí pozná deník podle stojícího přehrávání.
     */
    override fun stats(): CallMediaStats? = runCatching {
        worker.submit<CallMediaStats?> {
            val instance = handle ?: return@submit null
            fun time(mode: Int): Long? {
                val out = LongByReference()
                return if (native.ntg_time(instance, callId, mode, out) == NtgNative.OK) out.value else null
            }
            CallMediaStats(
                rxBytes = null,
                txBytes = null,
                playedSeconds = time(NtgNative.STREAM_MODE_PLAYBACK),
                capturedSeconds = time(NtgNative.STREAM_MODE_CAPTURE),
            )
        }.get(STATS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    }.getOrNull()

    override fun stop() {
        worker.execute {
            MacAudio.unwatch()
            outputsListener = null
            selectedOutput = null
            currentOutput = null
            // Hovor, který se nespojil (zrušené vytáčení): přenos neběžel, náhled ano.
            stopPreview()
            cameraOn = false
            video.clearAll()
            stopNow()
        }
    }

    /**
     * Synchronně zastaví náhled i knihovnu (ukončení aplikace, `DesktopShutdown`). Jde
     * frontou pracovního vlákna, takže počká i na `stop`, který zavěšení zařadilo před ním.
     */
    internal fun shutdown(timeout: Duration) {
        runCatching {
            worker.submit {
                stopPreview()
                stopNow()
            }.get(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        }.onFailure { logger.w(it) { "Knihovna hovoru se při ukončení nezastavila včas." } }
    }

    private fun startPreview() {
        if (previewing || !MacCamera.available) return
        var first = true
        previewing = MacCamera.start { pixels, width, height, stride ->
            if (first) logger.d { "První snímek náhledu: ${width}×$height." }
            first = false
            runCatching { video.pushBgra(CallVideoSource.Local, pixels, width, height, stride) }
                .onFailure { logger.w(it) { "Snímek náhledu se nepodařilo vykreslit." } }
        }
        if (!previewing) logger.w { "Náhled kamery nejde spustit." }
    }

    private fun stopPreview() {
        if (!previewing) return
        previewing = false
        MacCamera.stop()
    }

    private fun stopNow() {
        synchronized(detailLock) {
            detailLog?.close()
            detailLog = null
        }
        val instance = handle ?: return
        runCatching {
            native.ntg_stop(instance, callId)
            native.ntg_instance_destroy(instance)
        }.onFailure { logger.w(it) { "Zastavení hovoru selhalo." } }
        handle = null
        playback = null
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

    private fun media(
        microphone: String,
        camera: NtgVideoDescription.ByReference? = null,
        screen: NtgVideoDescription.ByReference? = null,
    ): NtgMediaDescription.ByValue =
        NtgMediaDescription.ByValue().apply {
            this.microphone = NtgAudioDescription.ByReference().apply {
                media_source = NtgNative.MEDIA_SOURCE_DEVICE
                sample_rate = SAMPLE_RATE
                channel_count = CHANNELS
                input = microphone
                keep_open = 1
            }
            this.camera = camera
            this.screen = screen
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
        private const val LOG_WARNING = 4
        private const val LOG_INFO = 2
        private const val LOG_SOURCE_SELF = 2

        /** Jak dlouho čekat na vlákno knihovny kvůli vzorku do deníku. */
        private const val STATS_TIMEOUT_MS = 300L

        private val DETAIL_STAMP = java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", java.util.Locale.ROOT)

        // Metadata zařízení jsou u ntgcalls JSON; čte se směr a identifikace zařízení.
        private const val MAC_MICROPHONE = """{"is_microphone":true,"uid":""}"""
        private const val MAC_SPEAKER = """{"is_microphone":false,"uid":""}"""
        private const val WINDOWS_MICROPHONE = """{"is_microphone":true,"index":0,"uid":"","automatic_restart":true}"""
        private const val WINDOWS_SPEAKER = """{"is_microphone":false,"index":0,"uid":"","automatic_restart":true}"""
    }
}
