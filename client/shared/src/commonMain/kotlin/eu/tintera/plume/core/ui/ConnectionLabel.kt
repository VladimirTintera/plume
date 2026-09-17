package eu.tintera.plume.core.ui

import eu.tintera.plume.core.telegram.ConnectionState

/**
 * Popisek stavu spojení, nebo `null`, když je všechno v pořádku.
 *
 * `null` u [ConnectionState.Ready] je záměr: hlásit „připojeno" je šum, protože to je stav, ve
 * kterém aplikace stráví skoro celou dobu. Zajímavé je jen to, kdy připojená NENÍ — jinak si
 * uživatel „nic mi nechodí" vyloží jako prázdný chat.
 *
 * Sdílí ho seznam chatů i hlavička konverzace, aby tentýž stav nezněl na každém místě jinak.
 */
fun ConnectionState.label(): String? = when (this) {
    ConnectionState.WaitingForNetwork -> "Čekání na síť…"
    ConnectionState.Connecting -> "Připojování…"
    ConnectionState.ConnectingToProxy -> "Připojování k proxy…"
    ConnectionState.Updating -> "Aktualizace…"
    ConnectionState.Ready -> null
}
