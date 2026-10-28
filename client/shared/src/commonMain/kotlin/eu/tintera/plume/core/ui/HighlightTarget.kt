package eu.tintera.plume.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * **Volba, na kterou se má obrazovka doscrollovat a krátce ji zvýraznit** — výsledek hledání
 * v Nastavení. Cíl je název řádku (tentýž prostředek, který řádek nese); řádek ho pozná sám
 * přes [highlightAnchor], takže se nic nemusí předávat sekcím ani podstránkám po cestě.
 *
 * Jednorázový: po zvýraznění ho řádek spotřebuje ([consume]), jinak by blikl při každém
 * dalším otevření sekce.
 */
@Stable
class HighlightTarget {
    var title: StringResource? by mutableStateOf(null)
        private set

    fun show(title: StringResource?) {
        this.title = title
    }

    fun consume() {
        title = null
    }
}

/** Cíl zvýraznění pro obrazovky Nastavení; mimo ně `null` a kotvy nedělají nic. */
val LocalHighlightTarget = staticCompositionLocalOf<HighlightTarget?> { null }

/**
 * Kotva řádku s názvem [title]: je-li to právě hledaná volba ([LocalHighlightTarget]), řádek se
 * po příjezdu obrazovky doscrolluje do pohledu a dvakrát se rozsvítí tónem `primary`.
 *
 * - **Tón se kreslí PŘES obsah, ne pod něj** (jako stavová vrstva M3): řádky mají vlastní
 *   podklad (`ListItem`, blok), který by tón pod sebou zakryl. Zaoblení bloku drží ořez řádku;
 *   samostatné kotvě (celý blok s nadpisem) ho dává [GROUP_CORNER].
 * - **Čeká se na dojezd obrazovky** ([ARRIVAL_DELAY_MS]): scroll a bliknutí během přechodu by
 *   nebylo vidět, a scroll by mohl dojet dřív, než se sloupec rozměří.
 * - Porovnává se text, ne prostředek: komponenty řádků (`SwitchListItem`, `ChoiceRow`) dostávají
 *   název už přeložený.
 */
@Composable
fun Modifier.highlightAnchor(title: String): Modifier {
    val target = LocalHighlightTarget.current ?: return this
    val wanted = target.title ?: return this
    if (stringResource(wanted) != title) return this

    val requester = remember { BringIntoViewRequester() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val margin = with(LocalDensity.current) { VIEW_MARGIN.toPx() }
    val glow = remember { Animatable(0f) }
    val color = MaterialTheme.colorScheme.primary
    val spec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
    LaunchedEffect(wanted) {
        delay(ARRIVAL_DELAY_MS)
        // S okrajem: holý `bringIntoView` řádek jen dotlačí k hraně, kde ho kryje navigační lišta.
        requester.bringIntoView(Rect(0f, -margin, size.width.toFloat(), size.height + margin))
        repeat(PULSES) {
            glow.animateTo(1f, spec)
            glow.animateTo(0f, spec)
        }
        target.consume()
    }
    return onSizeChanged { size = it }.bringIntoViewRequester(requester).drawWithContent {
        drawContent()
        if (glow.value > 0f) {
            drawRoundRect(
                color = color.copy(alpha = GLOW_ALPHA * glow.value),
                cornerRadius = CornerRadius(GROUP_CORNER.toPx()),
            )
        }
    }
}

private const val ARRIVAL_DELAY_MS = 450L

/** Kolik místa nad a pod řádkem nechat — ať nestojí na hraně pod lištou. */
private val VIEW_MARGIN = 96.dp
private const val PULSES = 2

/** Síla tónu na vrcholu — o stupeň výš než stavová vrstva stisku, ať je vidět i bez ťuknutí. */
private const val GLOW_ALPHA = 0.16f
