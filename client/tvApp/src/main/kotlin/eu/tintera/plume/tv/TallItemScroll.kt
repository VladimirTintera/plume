package eu.tintera.plume.tv

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Zpráva vyšší než seznam (dlouhý text, komentář, který se do sloupce nevejde): šipka nahoru
 * a dolů ji napřed odroluje a teprve na jejím kraji pustí fokus na další zprávu. Bez toho
 * fokus skákal po celých zprávách a prostředek dlouhé zprávy se nedal přečíst — seznam ukáže
 * jen kraj zaostřené položky.
 *
 * Kraj zaostřené zprávy se měří proti viditelné části seznamu (bez [paddingTop] a [paddingBottom])
 * přes polohu v okně, **ne `boundsInWindow`**: ta je oříznutá rodiči, takže zpráva přečnívající
 * přes okraj by vypadala, že se vejde. Seznam se roluje o krok ([STEP_FRACTION] výšky), nejvýš
 * o to, co zbývá do kraje zprávy.
 *
 * [reverse]: seznam s `reverseLayout` (konverzace, nejnovější dole) roluje kladně směrem nahoru.
 */
@Stable
class TallItemScroll(
    private val listState: LazyListState,
    private val reverse: Boolean,
    private val scope: CoroutineScope,
) {
    /**
     * Odsazení seznamu se může změnit (tlačítko psaní se ukáže, až dorazí práva chatu) — proto
     * se jen přepíše, instance zůstává. Nová by nevěděla, která zpráva je zaostřená: změna
     * fokusu, která ji hlásí, už proběhla (ověřeno na emulátoru, šipka pak přeskočila zprávu).
     */
    var paddingTop = 0f
    var paddingBottom = 0f

    private var focusedKey: Any? = null
    private val itemEdges = HashMap<Any, Pair<Float, Float>>()
    private var listY = 0f
    private var listHeight = 0f
    private val listTop get() = listY + paddingTop
    private val listBottom get() = listY + listHeight - paddingBottom

    /** Na seznam: měří ho a bere šipky nahoru a dolů, dokud zaostřená zpráva přečnívá. */
    val listModifier: Modifier = Modifier
        .onGloballyPositioned { coordinates ->
            listY = coordinates.positionInWindow().y
            listHeight = coordinates.size.height.toFloat()
        }
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val (top, bottom) = focusedKey?.let(itemEdges::get) ?: return@onPreviewKeyEvent false
            val step = (listBottom - listTop) * STEP_FRACTION
            when (event.nativeKeyEvent.keyCode) {
                AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                    val hidden = bottom - listBottom
                    if (hidden <= EDGE_SLOP) return@onPreviewKeyEvent false
                    scroll(down = true, by = min(step, hidden))
                    true
                }
                AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                    val hidden = listTop - top
                    if (hidden <= EDGE_SLOP) return@onPreviewKeyEvent false
                    scroll(down = false, by = min(step, hidden))
                    true
                }
                else -> false
            }
        }

    /** Na každou zprávu seznamu s jejím klíčem: hlídá, která je zaostřená a kde leží. */
    fun item(key: Any): Modifier = Modifier
        .onFocusChanged { if (it.isFocused) focusedKey = key else if (focusedKey == key && !it.hasFocus) focusedKey = null }
        .onGloballyPositioned { coordinates ->
            val y = coordinates.positionInWindow().y
            itemEdges[key] = y to y + coordinates.size.height
        }

    private fun scroll(down: Boolean, by: Float) {
        // Obsah má jet nahoru (ukázat spodek zprávy): v obráceném seznamu záporně.
        val delta = if (down == reverse) -by else by
        scope.launch { listState.animateScrollBy(delta) }
    }

    private companion object {
        /** Kolik výšky seznamu jeden stisk odroluje: dost na čtení, s přesahem pro orientaci. */
        const val STEP_FRACTION = 0.6f
        /** Pár pixelů přesahu (zaokrouhlení, rámeček fokusu) za přečnívání nepovažovat. */
        const val EDGE_SLOP = 4f
    }
}

@Composable
fun rememberTallItemScroll(listState: LazyListState, reverse: Boolean, paddingTop: Dp, paddingBottom: Dp): TallItemScroll {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val top = with(density) { paddingTop.toPx() }
    val bottom = with(density) { paddingBottom.toPx() }
    return remember(listState, reverse) { TallItemScroll(listState, reverse, scope) }.apply {
        this.paddingTop = top
        this.paddingBottom = bottom
    }
}
