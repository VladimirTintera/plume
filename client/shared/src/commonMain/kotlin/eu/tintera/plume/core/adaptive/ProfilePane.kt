package eu.tintera.plume.core.adaptive

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
 * Šířka KARTY profilu tak, aby kartě konverzace vedle zbylo aspoň [MIN_CONVERSATION_WIDTH].
 * [total] je celá plocha, [listWidth] šířka seznamu vlevo (ve třech panelech), nebo `null`, když
 * seznam vidět není. Nejužší je profil na výchozí šířce panelu — užší by se do něj nevešly ani
 * dlaždice akcí. Panel profilu je o okraje karty širší ([PROFILE_CARD_MARGINS]).
 */
fun profileWidthBeside(wanted: Dp, total: Dp, listWidth: Dp?, defaultWidth: Dp): Dp {
    // Před kartou konverzace je mezera od seznamu, nebo okraj od kraje okna — v obou případech jedna.
    val room = total - MIN_CONVERSATION_WIDTH - PANE_SPACING - PROFILE_CARD_MARGINS - (listWidth ?: 0.dp)
    return wanted.coerceAtMost(room).coerceAtLeast(defaultWidth.coerceAtMost(room))
}

/**
 * O kolik je panel profilu širší než jeho karta: mezera od konverzace a okraj od kraje okna
 * (`PaneCard`). Šířky kotev platí pro kartu, ať se dlaždice akcí vejdou i s okraji.
 */
val PROFILE_CARD_MARGINS: Dp = PANE_SPACING * 2

private val PROFILE_MEDIUM_WIDTH = 480.dp
private val PROFILE_WIDE_WIDTH = 600.dp
