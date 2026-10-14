package eu.tintera.plume.core.adaptive

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Kotvy šířky profilu vedle konverzace. Ukládá se jménem (`AppSettings.profilePaneAnchor`).
 * Sbalený profil smysl nemá — kdo ho nechce, zavře ho.
 */
enum class ProfilePaneAnchor {
    /** Výchozí šířka panelu (360 dp, na XL 412 dp). */
    Default,

    /** Střední: delší jména členů a popisy se nezalamují. */
    Medium,

    /** Široká: sdílená média ve větších náhledech. */
    Wide,
}

/** Šířka profilu na kotvě; [defaultWidth] je výchozí šířka panelu z direktivy. */
fun ProfilePaneAnchor.width(defaultWidth: Dp): Dp = when (this) {
    ProfilePaneAnchor.Default -> defaultWidth
    ProfilePaneAnchor.Medium -> PROFILE_MEDIUM_WIDTH
    ProfilePaneAnchor.Wide -> PROFILE_WIDE_WIDTH
}

/** Kotva nejblíž šířce [width] — kam se profil dorovná po puštění táhla. */
fun nearestProfileAnchor(width: Dp, defaultWidth: Dp): ProfilePaneAnchor =
    ProfilePaneAnchor.entries.minBy { abs((it.width(defaultWidth) - width).value) }

/**
 * Šířka profilu tak, aby konverzaci vedle zbylo aspoň [MIN_CONVERSATION_WIDTH]. [total] je celá
 * plocha, [listWidth] šířka seznamu vlevo (ve třech panelech), nebo `null`, když seznam vidět
 * není. Nejužší je profil na výchozí šířce panelu — užší by se do něj nevešly ani dlaždice akcí.
 */
fun profileWidthBeside(wanted: Dp, total: Dp, listWidth: Dp?, defaultWidth: Dp): Dp {
    val room = total - MIN_CONVERSATION_WIDTH - (listWidth?.let { it + PANE_SPACING } ?: 0.dp)
    return wanted.coerceAtMost(room).coerceAtLeast(defaultWidth.coerceAtMost(room))
}

/**
 * Táhlo profilu, poskytuje shell jen tam, kde profil stojí vedle konverzace (Chaty, dva i tři
 * panely). Táhlo knihovny (`paneExpansionDragHandle`) to neumí: ve třech panelech se nekreslí
 * vůbec a ve dvou počítá šířku PRVNÍHO panelu, ne profilu. Šířka profilu proto jde metadaty
 * (`Panes.width`) a táhlo je vlastní, v kartě profilu u levého okraje.
 */
@Stable
class ProfilePaneResize(
    /** Tah o [delta] doleva (záporné) profil rozšiřuje. */
    val onDrag: (delta: Dp) -> Unit,
    /** Puštění: dorovnat na kotvu a uložit. */
    val onDragStopped: () -> Unit,
    /** Čtečka obrazovky: další kotva dokola. */
    val onCycle: () -> Unit,
    /** Popis pro čtečku („Šířka profilu"). */
    val label: String,
)

val LocalProfilePaneResize = compositionLocalOf<ProfilePaneResize?> { null }

/** Táhlo M3 u levého okraje profilu. Tahem šířka; čtečka obrazovky přepíná kotvy akcí táhla. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePaneHandle(resize: ProfilePaneResize, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val dragState = rememberDraggableState { deltaPx -> resize.onDrag(with(density) { deltaPx.toDp() }) }
    VerticalDragHandle(
        modifier = modifier
            .padding(start = 4.dp)
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                interactionSource = interactionSource,
                onDragStopped = { resize.onDragStopped() },
            )
            .semantics {
                contentDescription = resize.label
                onClick { resize.onCycle(); true }
            },
        interactionSource = interactionSource,
    )
}

private val PROFILE_MEDIUM_WIDTH = 480.dp
private val PROFILE_WIDE_WIDTH = 600.dp
