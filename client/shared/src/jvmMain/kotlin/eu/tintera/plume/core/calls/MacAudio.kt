package eu.tintera.plume.core.calls

import eu.tintera.plume.core.platform.MacNative

/**
 * JNI most k CoreAudio (`jvmMain/objc/PlumeAudio.m`): typ připojení zvukového zařízení,
 * výchozí výstup systému a sledování změn zařízení. Seznam zařízení dává ntgcalls
 * (`ntg_get_media_devices`).
 */
internal object MacAudio {

    val available: Boolean by lazy { MacNative.loaded && runCatching { nativeInit() }.getOrDefault(false) }

    @Volatile
    private var onChange: (() -> Unit)? = null

    /**
     * Hlásí změny zařízení (připojení, odpojení, jiný výchozí výstup) z vlákna CoreAudio,
     * dokud se nezavolá [unwatch]. Jedna změna často přijde jako několik hlášení po sobě.
     */
    fun watch(listener: () -> Unit) {
        if (!available) return
        onChange = listener
        nativeWatch(true)
    }

    fun unwatch() {
        if (!available) return
        nativeWatch(false)
        onChange = null
    }

    @JvmStatic
    fun onDevicesChanged() {
        onChange?.invoke()
    }

    /** Typ připojení zařízení s [uid] (`kAudioDeviceTransportType…`), 0 = nevíme. */
    fun transportType(uid: String): Int = if (available) runCatching { nativeTransportType(uid) }.getOrDefault(0) else 0

    /** UID výchozího výstupu systému. */
    fun defaultOutputUid(): String? = if (available) runCatching { nativeDefaultOutputUid() }.getOrNull() else null

    @JvmStatic
    private external fun nativeInit(): Boolean

    @JvmStatic
    private external fun nativeWatch(on: Boolean)

    @JvmStatic
    private external fun nativeTransportType(uid: String): Int

    @JvmStatic
    private external fun nativeDefaultOutputUid(): String?
}

/**
 * Druh výstupu podle typu připojení CoreAudio; `null` = do výběru nepatří. Virtuální
 * zařízení (záznam obrazovky, ovladače konferenčních aplikací) by v nabídce jen mátla
 * a zvuk by z nich nikdo neslyšel.
 */
internal fun macOutputKind(transport: Int): CallAudioOutput.Kind? = when (transport) {
    fourCc("virt") -> null
    fourCc("blue"), fourCc("blea") -> CallAudioOutput.Kind.Bluetooth
    fourCc("usb ") -> CallAudioOutput.Kind.Headset
    // Vestavěné (reproduktory i sluchátka v konektoru — macOS je hlásí jako dvě zařízení),
    // HDMI / DisplayPort (monitor), AirPlay, Thunderbolt a neznámé: jméno řekne víc než ikona.
    else -> CallAudioOutput.Kind.Speaker
}

/** Čtyřznakový kód CoreAudio jako číslo (`'bltn'`). */
internal fun fourCc(code: String): Int = code.fold(0) { acc, char -> (acc shl 8) or (char.code and 0xFF) }
