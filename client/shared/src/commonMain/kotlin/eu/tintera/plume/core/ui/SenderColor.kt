package eu.tintera.plume.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.materialkolor.hct.Hct
import com.materialkolor.ktx.toColor
import com.materialkolor.ktx.toHct
import kotlin.math.absoluteValue

/**
 * Stálá barva odesílatele ve skupině — jméno nad zprávou a proužek citace.
 *
 * Dokud byli všichni `primary`, poznal se odesílatel v delší skupinové konverzaci jen čtením
 * jména. Barva podle id se naučí sama.
 *
 * **Barvy se odvozují z motivu, ne natvrdo** (konvence „barvy vždy z `colorScheme`"): odstíny
 * jsou rovnoměrně rozložené po kruhu od odstínu `primary`, se stejnou sytostí a se světlostí
 * podle režimu (HCT tón 40 ve světlém, 80 v tmavém). Tón zaručuje kontrast na plochách bublin
 * v obou režimech — přesně tak staví tónové palety samo M3 — a vazba na `primary` drží paletu
 * v souladu s Material You.
 */
@Composable
fun senderColor(senderId: Long): Color {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val palette = remember(scheme.primary, dark) {
        val base = scheme.primary.toHct().hue
        List(SENDER_COLOR_COUNT) { index ->
            Hct.from(senderHue(base, index), SENDER_CHROMA, if (dark) 80.0 else 40.0).toColor()
        }
    }
    return palette[senderColorIndex(senderId)]
}

/** Která z [SENDER_COLOR_COUNT] barev patří odesílateli. Stálá: stejné id = stejná barva. */
internal fun senderColorIndex(senderId: Long): Int =
    (senderId.hashCode().absoluteValue % SENDER_COLOR_COUNT)

/** Odstín [index]-té barvy: rovnoměrně po kruhu od [baseHue] (odstín `primary`). */
internal fun senderHue(baseHue: Double, index: Int): Double =
    (baseHue + index * 360.0 / SENDER_COLOR_COUNT) % 360.0

/** Kolik barev se střídá. Víc by se od sebe špatně rozlišovalo, míň by se opakovalo. */
internal const val SENDER_COLOR_COUNT = 8

private const val SENDER_CHROMA = 48.0
