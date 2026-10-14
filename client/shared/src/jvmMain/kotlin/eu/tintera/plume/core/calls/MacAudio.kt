package eu.tintera.plume.core.calls

import eu.tintera.plume.core.platform.MacNative

/**
 * JNI most k CoreAudio (`jvmMain/objc/PlumeAudio.m`): typ připojení zvukového zařízení
 * a výchozí výstup systému. Seznam zařízení dává ntgcalls (`ntg_get_media_devices`).
 */
internal object MacAudio {

    val available: Boolean get() = MacNative.loaded

    /** Typ připojení zařízení s [uid] (`kAudioDeviceTransportType…`), 0 = nevíme. */
    fun transportType(uid: String): Int = if (available) runCatching { nativeTransportType(uid) }.getOrDefault(0) else 0

    /** UID výchozího výstupu systému. */
    fun defaultOutputUid(): String? = if (available) runCatching { nativeDefaultOutputUid() }.getOrNull() else null

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
