package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable

/**
 * Hlasitost médií v systému (gesto v pravé půlce videa na celé obrazovce, jako MX Player nebo
 * VLC). Mění se táž hlasitost jako tlačítky na boku telefonu, ne jen hlasitost přehrávače —
 * tlačítka a gesto se tak nepřetahují o dvě různá čísla.
 *
 * - **[level] je 0 až 1** a během tahu se mění plynule; do systému jde zaokrouhlený na jeho
 *   kroky (na Androidu obvykle 15 nebo 25).
 * - **[sync] přečte hlasitost znovu** — mezi dvěma tahy ji mohla změnit tlačítka.
 */
interface MediaVolume {
    val level: Float

    fun set(level: Float)

    fun sync()
}

/**
 * `null`, kde to nejde: iOS hlasitost systému z aplikace nastavit nedovolí (jen přes
 * `MPVolumeView`) a desktop gesto prstem nemá. Na Androidu taky u zařízení s pevnou hlasitostí.
 */
@Composable
expect fun rememberMediaVolume(): MediaVolume?
