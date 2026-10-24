package eu.tintera.plume.core.ui

import eu.tintera.plume.core.telegram.ConnectionState
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.connection_connecting
import eu.tintera.plume.resources.connection_connecting_to_proxy
import eu.tintera.plume.resources.connection_updating
import eu.tintera.plume.resources.connection_waiting_for_network
import org.jetbrains.compose.resources.StringResource

/**
 * Popisek stavu spojení, nebo `null`, když je všechno v pořádku.
 *
 * `null` u [ConnectionState.Ready] je záměr: hlásit „připojeno" je šum, protože to je stav, ve
 * kterém aplikace stráví skoro celou dobu. Zajímavé je jen to, kdy připojená NENÍ — jinak si
 * uživatel „nic mi nechodí" vyloží jako prázdný chat.
 *
 * Sdílí ho seznam chatů i hlavička konverzace, aby tentýž stav nezněl na každém místě jinak.
 */
fun ConnectionState.label(): StringResource? = when (this) {
    ConnectionState.WaitingForNetwork -> Res.string.connection_waiting_for_network
    ConnectionState.Connecting -> Res.string.connection_connecting
    ConnectionState.ConnectingToProxy -> Res.string.connection_connecting_to_proxy
    ConnectionState.Updating -> Res.string.connection_updating
    ConnectionState.Ready -> null
}
