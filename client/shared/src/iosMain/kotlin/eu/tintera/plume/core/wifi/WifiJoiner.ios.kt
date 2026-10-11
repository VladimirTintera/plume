package eu.tintera.plume.core.wifi

/** iOS: bez oprávnění Hotspot Configuration se k síti z aplikace připojit nejde (viz [WifiJoiner]). */
actual class WifiJoiner {
    actual val canJoin: Boolean = false

    actual fun join(ssid: String, password: String): WifiJoin = WifiJoin.Failed
}
