@file:Suppress("FunctionName", "PropertyName", "ClassName", "unused")

package eu.tintera.plume.core.calls

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.ByteByReference

/**
 * Vazba na C API ntgcalls (`ntgcalls.h` z vydání 3.0.3) přes JNA. Desktop jede na JDK 17,
 * takže Java FFM (finální až v JDK 22) nepřipadá v úvahu, a vlastní JNI by chtělo překládat
 * nativní kód pro každou platformu zvlášť.
 *
 * - **API je synchronní** (žádné futures); zpětná volání chodí z vláken knihovny.
 * - **C `bool` se předává jako `Byte` 1/0**, ve struktuře i v argumentu funkce. JNA `Boolean`
 *   převádí na `int` a ten do C `bool` nedošel správně: `is_outgoing = true` knihovna četla
 *   jako nepravdu, desktop jako volající se pak choval jako volaný, `InitialSetup` neposlal
 *   nikdo a hovor skončil TIMEOUT.
 * - **`size_t` je `Long`**: všechny podporované platformy jsou 64bitové (Windows x64 má `long`
 *   32bitový, `size_t` ale 64bitový, proto ne `NativeLong`).
 * - **Zpětná volání musí zůstat silně odkazovaná**, dokud je knihovna může zavolat — jinak je
 *   GC uklidí a knihovna skočí do uvolněné paměti.
 */
internal interface NtgNative : Library {
    fun ntg_get_version(): String
    fun ntg_last_error(): String?
    fun ntg_set_log_callback(callback: LogCallback?, userData: Pointer?)

    fun ntg_instance_create(): Pointer
    fun ntg_instance_destroy(handle: Pointer)

    fun ntg_get_protocol(out: NtgProtocol): Int
    fun ntg_protocol_free(value: NtgProtocol)
    fun ntg_get_media_devices(out: NtgMediaDevices): Int
    fun ntg_media_devices_free(value: NtgMediaDevices)

    fun ntg_create_p2p_call(handle: Pointer, userId: Long): Int
    fun ntg_skip_exchange(handle: Pointer, userId: Long, key: ByteArray, keyLength: Long, isOutgoing: Byte): Int
    fun ntg_connect_p2p(
        handle: Pointer,
        userId: Long,
        servers: Array<NtgRtcServer>,
        serversLength: Long,
        versions: Array<String>,
        versionsLength: Long,
        p2pAllowed: Byte,
        customParameters: String,
    ): Int
    fun ntg_set_stream_sources(handle: Pointer, chatId: Long, mode: Int, media: NtgMediaDescription.ByValue): Int
    fun ntg_send_signaling_data(handle: Pointer, chatId: Long, data: ByteArray, length: Long): Int
    fun ntg_mute(handle: Pointer, chatId: Long, out: ByteByReference): Int
    fun ntg_unmute(handle: Pointer, chatId: Long, out: ByteByReference): Int
    fun ntg_pause(handle: Pointer, chatId: Long, out: ByteByReference): Int
    fun ntg_resume(handle: Pointer, chatId: Long, out: ByteByReference): Int
    fun ntg_stop(handle: Pointer, chatId: Long): Int

    fun ntg_on_signaling_data_callback(handle: Pointer, callback: SignalingCallback?, userData: Pointer?): Int
    fun ntg_on_connection_change_callback(handle: Pointer, callback: ConnectionCallback?, userData: Pointer?): Int
    fun ntg_on_remote_source_change_callback(handle: Pointer, callback: RemoteSourceCallback?, userData: Pointer?): Int
    fun ntg_on_frames_callback(handle: Pointer, callback: FramesCallback?, userData: Pointer?): Int

    fun interface LogCallback : Callback {
        fun invoke(message: NtgLogMessage.ByValue, userData: Pointer?)
    }

    fun interface SignalingCallback : Callback {
        fun invoke(handle: Pointer?, chatId: Long, data: Pointer?, length: Long, userData: Pointer?)
    }

    fun interface ConnectionCallback : Callback {
        fun invoke(handle: Pointer?, chatId: Long, info: NtgConnectionInfo.ByValue, userData: Pointer?)
    }

    fun interface RemoteSourceCallback : Callback {
        fun invoke(handle: Pointer?, chatId: Long, source: NtgRemoteSource.ByValue, userData: Pointer?)
    }

    /** Snímky: [frames] ukazuje na pole `ntg_frame` o [framesLength] prvcích, platné jen během volání. */
    fun interface FramesCallback : Callback {
        fun invoke(handle: Pointer?, chatId: Long, mode: Int, device: Int, frames: Pointer?, framesLength: Long, userData: Pointer?)
    }

    companion object {
        const val OK = 0

        const val MEDIA_SOURCE_DEVICE = 4
        const val MEDIA_SOURCE_EXTERNAL = 6

        const val STREAM_MODE_CAPTURE = 0
        const val STREAM_MODE_PLAYBACK = 1

        const val STREAM_DEVICE_CAMERA = 2
        const val STREAM_DEVICE_SCREEN = 3

        const val STREAM_STATUS_ACTIVE = 0

        const val CONNECTION_CONNECTING = 0
        const val CONNECTION_CONNECTED = 1
        const val CONNECTION_FAILED = 2
        const val CONNECTION_TIMEOUT = 3
        const val CONNECTION_CLOSED = 4

        /** Jméno knihovny pro JNA: `libntgcalls.dylib`, `ntgcalls.dll`, `libntgcalls.so`. */
        const val LIBRARY = "ntgcalls"

        fun load(): NtgNative = Native.load(LIBRARY, NtgNative::class.java)
    }
}

@Structure.FieldOrder("level", "source", "file", "line", "message")
internal open class NtgLogMessage : Structure() {
    @JvmField var level: Int = 0
    @JvmField var source: Int = 0
    @JvmField var file: String? = null
    @JvmField var line: Int = 0
    @JvmField var message: String? = null

    class ByValue : NtgLogMessage(), Structure.ByValue
}

@Structure.FieldOrder("state", "kind")
internal open class NtgConnectionInfo : Structure() {
    @JvmField var state: Int = 0
    @JvmField var kind: Int = 0

    class ByValue : NtgConnectionInfo(), Structure.ByValue
}

@Structure.FieldOrder("ssrc", "state", "device")
internal open class NtgRemoteSource : Structure() {
    @JvmField var ssrc: Int = 0
    @JvmField var state: Int = 0
    @JvmField var device: Int = 0

    class ByValue : NtgRemoteSource(), Structure.ByValue
}

@Structure.FieldOrder("media_source", "sample_rate", "channel_count", "input", "keep_open")
internal open class NtgAudioDescription : Structure() {
    @JvmField var media_source: Int = 0
    @JvmField var sample_rate: Int = 0
    @JvmField var channel_count: Byte = 0
    @JvmField var input: String? = null
    @JvmField var keep_open: Byte = 0

    class ByReference : NtgAudioDescription(), Structure.ByReference
}

@Structure.FieldOrder("microphone", "speaker", "camera", "screen")
internal open class NtgMediaDescription : Structure() {
    @JvmField var microphone: NtgAudioDescription.ByReference? = null
    @JvmField var speaker: NtgAudioDescription.ByReference? = null

    @JvmField var camera: NtgVideoDescription.ByReference? = null

    /** Obrazovka: desktop ji jen přijímá (PLAYBACK), sám zatím nesdílí. */
    @JvmField var screen: NtgVideoDescription.ByReference? = null

    class ByValue : NtgMediaDescription(), Structure.ByValue
}

@Structure.FieldOrder("media_source", "width", "height", "fps", "input", "keep_open")
internal open class NtgVideoDescription : Structure() {
    @JvmField var media_source: Int = 0
    @JvmField var width: Short = 0
    @JvmField var height: Short = 0
    @JvmField var fps: Byte = 0
    @JvmField var input: String? = null
    @JvmField var keep_open: Byte = 0

    class ByReference : NtgVideoDescription(), Structure.ByReference
}

/** `uint16_t` šířka a výška: v Kotlinu `Short`, čte se přes [widthPx] / [heightPx]. */
@Structure.FieldOrder("absolute_capture_timestamp_ms", "rotation", "width", "height")
internal open class NtgFrameData : Structure() {
    @JvmField var absolute_capture_timestamp_ms: Long = 0
    @JvmField var rotation: Int = 0
    @JvmField var width: Short = 0
    @JvmField var height: Short = 0

    val widthPx: Int get() = width.toInt() and 0xFFFF
    val heightPx: Int get() = height.toInt() and 0xFFFF

    /** Ve stupních po směru hodinových ručiček (`ntg_video_rotation`: 0, 90, 180, 270). */
    val rotationDegrees: Int get() = rotation.coerceIn(0, 3) * 90
}

@Structure.FieldOrder("ssrc", "data", "data_len", "frame_data")
internal open class NtgFrame : Structure {
    constructor() : super()
    constructor(pointer: Pointer) : super(pointer) {
        read()
    }

    @JvmField var ssrc: Long = 0
    @JvmField var data: Pointer? = null
    @JvmField var data_len: Long = 0
    @JvmField var frame_data: NtgFrameData = NtgFrameData()
}

@Structure.FieldOrder("min_layer", "max_layer", "udp_p2p", "udp_reflector", "library_versions", "library_versions_len")
internal open class NtgProtocol : Structure() {
    @JvmField var min_layer: Int = 0
    @JvmField var max_layer: Int = 0
    @JvmField var udp_p2p: Byte = 0
    @JvmField var udp_reflector: Byte = 0
    @JvmField var library_versions: Pointer? = null
    @JvmField var library_versions_len: Long = 0

    fun versions(): List<String> =
        library_versions?.getPointerArray(0, library_versions_len.toInt())?.map { it.getString(0) }.orEmpty()
}

@Structure.FieldOrder("name", "metadata")
internal open class NtgDeviceInfo : Structure {
    constructor() : super()
    constructor(pointer: Pointer) : super(pointer) {
        read()
    }

    @JvmField var name: String? = null
    @JvmField var metadata: String? = null
}

@Structure.FieldOrder(
    "microphone", "microphone_len",
    "speaker", "speaker_len",
    "camera", "camera_len",
    "screen", "screen_len",
)
internal open class NtgMediaDevices : Structure() {
    @JvmField var microphone: Pointer? = null
    @JvmField var microphone_len: Long = 0
    @JvmField var speaker: Pointer? = null
    @JvmField var speaker_len: Long = 0
    @JvmField var camera: Pointer? = null
    @JvmField var camera_len: Long = 0
    @JvmField var screen: Pointer? = null
    @JvmField var screen_len: Long = 0

    fun microphones(): List<NtgDeviceInfo> = devices(microphone, microphone_len)

    fun speakers(): List<NtgDeviceInfo> = devices(speaker, speaker_len)

    fun cameras(): List<NtgDeviceInfo> = devices(camera, camera_len)

    private fun devices(first: Pointer?, count: Long): List<NtgDeviceInfo> {
        if (first == null || count <= 0) return emptyList()
        val head = NtgDeviceInfo(first)
        @Suppress("UNCHECKED_CAST")
        return (head.toArray(count.toInt()) as Array<NtgDeviceInfo>).onEach { it.read() }.toList()
    }
}

@Structure.FieldOrder(
    "id", "ipv4", "ipv6", "port", "username", "password", "turn", "stun", "tcp", "peer_tag", "peer_tag_len",
)
internal open class NtgRtcServer : Structure() {
    @JvmField var id: Long = 0
    @JvmField var ipv4: String? = null
    @JvmField var ipv6: String? = null
    @JvmField var port: Short = 0
    @JvmField var username: String? = null
    @JvmField var password: String? = null
    @JvmField var turn: Byte = 0
    @JvmField var stun: Byte = 0
    @JvmField var tcp: Byte = 0
    @JvmField var peer_tag: Pointer? = null
    @JvmField var peer_tag_len: Long = 0
}
