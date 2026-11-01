package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced

/**
 * Vrstva efektů nad CELOU obrazovkou konverzace: efekt zprávy, efekt animovaného emoji
 * a konfety výhry u kostky.
 *
 * Efekty kreslila zpráva sama a narážely na tři hranice: obsah zprávy je ořezaný obrysem
 * bubliny, seznam ořezává svou plochu (`clipToBounds`) a hlavička se vstupním polem leží ve
 * Scaffoldu nad seznamem — výbuch u nejnovější zprávy zajel pod pole. Zpráva sem proto předá
 * jen kresbu a místo, odkud vyletí ([PlayEffect]); vrstva leží nad Scaffoldem, takže efekt
 * letí přes hlavičku, pole i pod stavovou lištu.
 *
 * **Místo se čte při KRESLENÍ, každý snímek.** Zpráva se během efektu hýbe (scroll, tah na
 * odpověď, příjezd nové zprávy), a kotva spočítaná jednou by nechala výbuch viset ve vzduchu.
 * Kreslí se až po rozmístění celého snímku, takže poloha prvku je ta z téhož snímku; vrstva se
 * nechá překreslovat každý snímek, dokud v ní něco hraje. Při rozmístění by to nešlo: seznam
 * leží hlouběji než vrstva a přeskládává se až po ní, takže by kotva jela o snímek pozadu.
 */
@Stable
internal class EffectLayer {
    private var host: LayoutCoordinates? = null
    private val effects = mutableStateListOf<Effect>()

    private class Effect(val source: EffectSource, val drawing: @Composable () -> Unit)

    fun show(source: EffectSource, drawing: @Composable () -> Unit): Any =
        Effect(source, drawing).also { effects += it }

    fun hide(effect: Any) {
        effects.remove(effect)
    }

    /** Efekty v místě, odkud vyletěly. Patří do obalu obrazovky NAD Scaffold. */
    @Composable
    fun BoxScope.Content() {
        if (effects.isEmpty()) return
        var frame by remember { mutableLongStateOf(0L) }
        LaunchedEffect(Unit) {
            while (true) withFrameNanos { frame = it }
        }
        Box(Modifier.matchParentSize().onPlaced { host = it }) {
            effects.forEach { effect ->
                key(effect) {
                    Box(
                        Modifier.drawWithContent {
                            // Čtení snímku: kresba se opakuje, dokud efekt hraje (viz KDoc třídy).
                            frame
                            val at = effect.source.positionIn(host) ?: return@drawWithContent
                            translate(at.x, at.y) { this@drawWithContent.drawContent() }
                        },
                    ) { effect.drawing() }
                }
            }
        }
    }
}

/**
 * Místo, odkud efekt vyletí: střed prvku, ve kterém se [PlayEffect] zavolal. Když prvek
 * zrovna umístěný není, drží se poslední známá poloha. Zpráva, která odjede ze seznamu, efekt
 * ukončí (složí se a [PlayEffect] ho z vrstvy odebere).
 */
@Stable
internal class EffectSource {
    var coordinates: LayoutCoordinates? = null
    private var last: Offset? = null

    fun positionIn(host: LayoutCoordinates?): Offset? {
        val source = coordinates
        if (host != null && source != null && host.isAttached && source.isAttached) {
            last = host.localPositionOf(source, Offset.Zero)
        }
        return last
    }
}

/** Vrstva efektů obrazovky; `null` mimo konverzaci. */
internal val LocalEffectLayer = staticCompositionLocalOf<EffectLayer?> { null }

/**
 * Přehraje [drawing] ze středu tohoto prvku ve vrstvě efektů ([EffectLayer]), mimo všechny
 * ořezy. Kresba plochu nezabírá a kotví se v bodě, kam ji vrstva posadí (měří se na nulu).
 * Bez vrstvy se kreslí na místě, ve středu prvku.
 */
@Composable
internal fun BoxScope.PlayEffect(drawing: @Composable () -> Unit) {
    val layer = LocalEffectLayer.current
    val source = remember { EffectSource() }
    val current by rememberUpdatedState(drawing)
    // Bod bez velikosti ve středu prvku: jeho poloha je kotva efektu.
    Box(Modifier.align(Alignment.Center).onPlaced { source.coordinates = it }) {
        if (layer == null) drawing()
    }
    if (layer != null) {
        DisposableEffect(layer) {
            val effect = layer.show(source) { current() }
            onDispose { layer.hide(effect) }
        }
    }
}
