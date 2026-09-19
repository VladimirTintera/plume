package eu.tintera.plume.core.share

import androidx.compose.runtime.Composable

/**
 * Vrátí uživatele do aplikace, ze které sdílel — zavření výběru chatu při sdílení.
 *
 * Android: úloha Plume jde do pozadí (`moveTaskToBack`) a pod ní je aplikace, ze které se
 * sdílelo. Plume díky `singleTask` běží ve vlastní úloze, takže je to přesně ona. iOS nic:
 * tam se chat vybírá v rozšíření a výběr v aplikaci se neukazuje.
 */
@Composable
expect fun rememberReturnToCaller(): () -> Unit
