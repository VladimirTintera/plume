package eu.tintera.plume.core.wifi

/** Jak dopadlo [WifiJoiner.join]. */
enum class WifiJoin {
    /** Systém ukázal dialog „Uložit síť?" — rozhodne uživatel. */
    Dialog,

    /** Otevřelo se nastavení Wi-Fi; síť si uživatel vybere sám a heslo vloží ze schránky. */
    Settings,

    /** Nic z toho nejde. */
    Failed,
}

/**
 * Nabídne připojení k síti s heslem ze zprávy. **Nic se nepřipojí samo**: rozhoduje systémový
 * dialog, nebo uživatel v nastavení.
 *
 * Android 11+: `Settings.ACTION_WIFI_ADD_NETWORKS` (dialog „Uložit síť?"), starší Android:
 * nastavení Wi-Fi. iOS: připojení z aplikace chce oprávnění Hotspot Configuration
 * (entitlement v profilu aplikace), které Plume nemá — [canJoin] je `false` a zbude kopírování
 * hesla a QR kód.
 */
expect class WifiJoiner {
    val canJoin: Boolean

    fun join(ssid: String, password: String): WifiJoin
}
