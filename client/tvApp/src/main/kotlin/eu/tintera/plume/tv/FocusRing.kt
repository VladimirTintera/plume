package eu.tintera.plume.tv

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme

/**
 * Fokus zprávy: světlý prstenec KOLEM ní, s mezerou od jejího okraje.
 *
 * Dřív měla zaostřená bublina 3dp rámeček v barvě `border` UVNITŘ svého tvaru. Ze tří metrů
 * byl nevýrazný: středně šedá na šedé příchozí bublině skoro splývala, rámeček ukrajoval okraj
 * obsahu (fotky, citace) a návod Google chce, aby byla odezva na ovladač „okamžitá a zřetelná“.
 * Prstenec je v `onSurface` (na Google TV je fokus bílý) a stojí na ploše stránky, ne na
 * bublině, takže kontrast nezávisí na barvě bubliny (příchozí, odchozí, fotka). Mezera ho
 * odliší od okraje bubliny i u fotky se světlým okrajem.
 *
 * Zvětšení (jako u karet tv-material) zprávy nemají: široká bublina by přetekla do okraje
 * a text ve zvětšené vrstvě by se rozmazal.
 *
 * [ringShape] je tvar prstence: u zaoblené zprávy [focusRingShape] (soustředné rohy), u kulaté
 * a pilulky tentýž tvar. Modifikátor patří PŘED `clip` zprávy, jinak ho ořízne.
 * Prstenec se kreslí mimo hranice prvku, takže seznam musí mít aspoň [FOCUS_RING_OUTSET]
 * mezery mezi položkami i na krajích (`contentPadding`).
 */
@Composable
internal fun Modifier.focusRing(focused: Boolean, ringShape: Shape): Modifier {
    val color = MaterialTheme.colorScheme.onSurface
    // Bez překmitu: průhlednost nad 1 by se ořízla a vypadala jako bliknutí.
    val alpha by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "focus-ring",
    )
    return drawWithContent {
        drawContent()
        if (alpha > 0f) {
            val stroke = FOCUS_RING_WIDTH.toPx()
            // Střed tahu leží v mezeře + polovině šířky od okraje zprávy.
            val inset = (FOCUS_RING_GAP + FOCUS_RING_WIDTH / 2).toPx()
            val ring = Size(size.width + inset * 2, size.height + inset * 2)
            val outline = ringShape.createOutline(ring, layoutDirection, this)
            translate(-inset, -inset) {
                drawOutline(outline, color.copy(alpha = alpha), style = Stroke(stroke))
            }
        }
    }
}

/** Tvar prstence kolem zprávy se zaoblením [cornerRadius]: rohy soustředné s jejími. */
internal fun focusRingShape(cornerRadius: Dp) = RoundedCornerShape(cornerRadius + FOCUS_RING_GAP + FOCUS_RING_WIDTH / 2)

/** Mezera mezi okrajem zprávy a prstencem. */
private val FOCUS_RING_GAP = 3.dp

/** Šířka prstence. */
private val FOCUS_RING_WIDTH = 3.dp

/** Jak daleko za okraj zprávy prstenec sahá (mezera + šířka). */
internal val FOCUS_RING_OUTSET = FOCUS_RING_GAP + FOCUS_RING_WIDTH
