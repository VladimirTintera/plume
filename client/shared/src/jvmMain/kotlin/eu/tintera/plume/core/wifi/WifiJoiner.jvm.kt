package eu.tintera.plume.core.wifi

/** Připojit se k Wi-Fi z JVM nejde; heslo zůstává ke zkopírování. */
actual class WifiJoiner {
    actual val canJoin: Boolean = false

    actual fun join(ssid: String, password: String): WifiJoin = WifiJoin.Failed
}
