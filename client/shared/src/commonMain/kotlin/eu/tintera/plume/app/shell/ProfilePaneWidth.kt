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
import eu.tintera.plume.core.adaptive.PaneEdge
import eu.tintera.plume.core.adaptive.PaneResize
import eu.tintera.plume.core.adaptive.nearestProfileAnchor
import eu.tintera.plume.core.adaptive.profileWidthBeside
import eu.tintera.plume.core.adaptive.width
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.profile_pane_width
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Šířka profilu vedle konverzace a táhlo, které ji mění. [resizing] = za tahu nebo dojezdu,
 * kdy shell panelům vypíná animaci rozměrů (viz [DraggedWidth]).
 */
internal class ProfilePaneWidth(val width: Dp, val resize: PaneResize, val resizing: Boolean)

/**
 * Šířka profilu ze zvolené kotvy (`AppSettings.profilePaneAnchor`), za tahu podle prstu.
 *
 * - **Za tahu se šířka rovnou omezuje** ([profileWidthBeside]), ne až při kreslení: jinak by
 *   tah za hranici „nabíral" a cesta zpátky by chvíli nic nedělala.
 * - **Po puštění dojede pružinou na nejbližší kotvu** ([DraggedWidth]) a ta se uloží. Do stavu
 *   se zapíše hned, ne až z toku nastavení — ten dorazí o snímek později a profil by skočil zpátky.
 * - **Výchozí kotva se neukládá**, jako u seznamu.
 */
@Composable
internal fun rememberProfilePaneResize(defaultWidth: Dp, total: Dp, listWidth: Dp?): ProfilePaneWidth {
    val settings = koinInject<AppSettings>()
    val saved by settings.profilePaneAnchor.collectAsStateWithLifecycle()
    var chosen by remember { mutableStateOf<ProfilePaneAnchor?>(null) }
    val dragged = rememberDraggedWidth()
    val anchor = chosen ?: saved ?: ProfilePaneAnchor.Default
    val clamp = { wanted: Dp -> profileWidthBeside(wanted, total, listWidth, defaultWidth) }
    val width = clamp(dragged.value ?: anchor.width(defaultWidth))

    val currentWidth by rememberUpdatedState(width)
    val currentAnchor by rememberUpdatedState(anchor)
    val currentClamp by rememberUpdatedState(clamp)
    val currentDefault by rememberUpdatedState(defaultWidth)
    val label = stringResource(Res.string.profile_pane_width)
    val resize = remember(label, settings, dragged) {
        val settle = { next: ProfilePaneAnchor ->
            dragged.settle(from = currentWidth, target = currentClamp(next.width(currentDefault))) {
                chosen = next
                settings.setProfilePaneAnchor(next)
            }
        }
        PaneResize(
            edge = PaneEdge.Start,
            // Profil je vpravo: tah doleva (záporný posun) ho rozšiřuje.
            onDrag = { delta -> dragged.drag(from = currentWidth, delta = -delta, clamp = currentClamp) },
            onDragStopped = { settle(nearestProfileAnchor(currentWidth, currentDefault)) },
            onCycle = {
                val entries = ProfilePaneAnchor.entries
                settle(entries[(currentAnchor.ordinal + 1) % entries.size])
            },
            label = label,
        )
    }
    return ProfilePaneWidth(width, resize, resizing = dragged.active)
}
