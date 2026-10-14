package eu.tintera.plume.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.adaptive.ProfilePaneAnchor
import eu.tintera.plume.core.adaptive.ProfilePaneResize
import eu.tintera.plume.core.adaptive.nearestProfileAnchor
import eu.tintera.plume.core.adaptive.profileWidthBeside
import eu.tintera.plume.core.adaptive.width
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.profile_pane_width
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Šířka profilu vedle konverzace a táhlo, které ji mění. */
internal class ProfilePaneWidth(val width: Dp, val resize: ProfilePaneResize)

/**
 * Šířka profilu ze zvolené kotvy (`AppSettings.profilePaneAnchor`), za tahu podle prstu.
 *
 * - **Za tahu se šířka rovnou omezuje** ([profileWidthBeside]), ne až při kreslení: jinak by
 *   tah za hranici „nabíral" a cesta zpátky by chvíli nic nedělala.
 * - **Po puštění se dorovná na nejbližší kotvu** a ta se uloží. Do stavu se zapíše hned, ne až
 *   z toku nastavení — ten dorazí o snímek později a profil by na okamžik skočil zpátky.
 * - **Výchozí kotva se neukládá**, jako u seznamu.
 */
@Composable
internal fun rememberProfilePaneResize(defaultWidth: Dp, total: Dp, listWidth: Dp?): ProfilePaneWidth {
    val settings = koinInject<AppSettings>()
    val saved by settings.profilePaneAnchor.collectAsStateWithLifecycle()
    var chosen by remember { mutableStateOf<ProfilePaneAnchor?>(null) }
    var dragging by remember { mutableStateOf<Dp?>(null) }
    val anchor = chosen ?: saved ?: ProfilePaneAnchor.Default
    val clamp = { wanted: Dp -> profileWidthBeside(wanted, total, listWidth, defaultWidth) }
    val width = clamp(dragging ?: anchor.width(defaultWidth))

    val currentWidth by rememberUpdatedState(width)
    val currentAnchor by rememberUpdatedState(anchor)
    val currentClamp by rememberUpdatedState(clamp)
    val currentDefault by rememberUpdatedState(defaultWidth)
    val label = stringResource(Res.string.profile_pane_width)
    val resize = remember(label, settings) {
        val settle = { next: ProfilePaneAnchor ->
            chosen = next
            dragging = null
            settings.setProfilePaneAnchor(next)
        }
        ProfilePaneResize(
            // Profil je vpravo: tah doleva (záporný posun) ho rozšiřuje.
            onDrag = { delta -> dragging = currentClamp((dragging ?: currentWidth) - delta) },
            onDragStopped = { settle(nearestProfileAnchor(currentWidth, currentDefault)) },
            onCycle = {
                val entries = ProfilePaneAnchor.entries
                settle(entries[(currentAnchor.ordinal + 1) % entries.size])
            },
            label = label,
        )
    }
    return ProfilePaneWidth(width, resize)
}
