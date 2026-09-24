package eu.tintera.plume.app.shell

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEvent
import kotlin.math.roundToInt

/**
 * Přechody mezi scénami `NavDisplay`: obrazovky na telefonu, a na široké ploše přechod mezi
 * Chaty a Nastavením (uvnitř list-detail scény animuje panely scaffold sám).
 *
 * **Výchozí přechod Nav3 na Androidu je 700ms prolnutí obou obrazovek NARÁZ** (`fadeIn` spolu
 * s `fadeOut`). Obě jsou neprůhledné, takže v půlce jsou obě napůl průhledné přes sebe — nadpis
 * „Nastavení" přes pilulku hledání, řádky přes řádky. Vypadalo to jako chyba vykreslení,
 * a protože se návrat do seznamu chatů skládá znovu celý, první snímek trval dlouho a prolnutí
 * skočilo rovnou doprostřed.
 *
 * Proto **sdílená osa X** z Material motion: odcházející obrazovka zmizí během prvních 90 ms,
 * přicházející se objeví až potom — dvojí expozice nenastane nikdy. Obě se přitom posunou
 * o [SHIFT] ve směru navigace (dopředu doleva, zpět doprava), takže je poznat, kterým směrem
 * se šlo. Průhlednost jde po tweenu kvůli zpoždění (pružina ho nemá), posun po spatial specu
 * z [MaterialTheme.motionScheme].
 */
internal class NavTransitions(
    private val shiftPx: Int,
    private val spatial: FiniteAnimationSpec<IntOffset>,
) {
    fun <T : Any> forward(): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
        sharedAxis(this, SlideDirection.Start)
    }

    fun <T : Any> pop(): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
        sharedAxis(this, SlideDirection.End)
    }

    /**
     * Zpět gestem. Průběh gesta posouvá přechod (`seekTo`), takže všechno jede lineárně
     * a zlomek přechodu odpovídá tahu prstu.
     *
     * Odcházející obrazovka se zmenší na 90 % a popojede od hrany, ze které se táhne — jako
     * systémový predictive back. Výchozí z Nav3 ji zmenšoval na 70 %, tedy o třetinu plochy,
     * a přitom neprůhlednou až do konce. Zmenšení dojede už v 60 % přechodu: průběh gesta
     * k jedničce skoro nedojde, a s lineárním zmenšením přes celou délku bylo v půlce tahu
     * sotva vidět (ověřeno na emulátoru). Zmizí až v poslední čtvrtině, tedy po puštění prstu;
     * přicházející se objeví hned na začátku tahu, aby kolem zmenšené obrazovky nebyla prázdná
     * plocha.
     */
    fun <T : Any> predictivePop(): AnimatedContentTransitionScope<Scene<T>>.(Int) -> ContentTransform =
        { edge ->
            // Tah od levé hrany odsouvá obrazovku doprava a naopak.
            val direction = if (edge == NavigationEvent.EDGE_RIGHT) -1 else 1
            val enter = fadeIn(tween(PREDICTIVE_ENTER_MS, easing = LinearEasing))
            val exit = scaleOut(tween(PREDICTIVE_SHRINK_MS, easing = LinearEasing), targetScale = PREDICTIVE_SCALE) +
                slideOut(tween(PREDICTIVE_SHRINK_MS, easing = LinearEasing)) { size ->
                    IntOffset((size.width * PREDICTIVE_DRIFT * direction).roundToInt(), 0)
                } +
                fadeOut(
                    tween(
                        durationMillis = PREDICTIVE_MS - PREDICTIVE_FADE_DELAY_MS,
                        delayMillis = PREDICTIVE_FADE_DELAY_MS,
                        easing = LinearEasing,
                    ),
                )
            enter togetherWith exit
        }

    private fun <T : Any> sharedAxis(
        scope: AnimatedContentTransitionScope<Scene<T>>,
        towards: SlideDirection,
    ): ContentTransform = with(scope) {
        // Posun ve směru navigace, znaménko podle směru (a tím i podle RTL) dodá kontejner.
        val shift: (Int) -> Int = { full -> if (full >= 0) shiftPx else -shiftPx }
        val enter = slideIntoContainer(towards, spatial, initialOffset = shift) +
            fadeIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS, easing = LinearOutSlowInEasing))
        val exit = slideOutOfContainer(towards, spatial, targetOffset = shift) +
            fadeOut(tween(FADE_OUT_MS, easing = FastOutLinearInEasing))
        enter togetherWith exit
    }
}

@Composable
internal fun rememberNavTransitions(): NavTransitions {
    val shiftPx = with(LocalDensity.current) { SHIFT.roundToPx() }
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    return remember(shiftPx, spatial) { NavTransitions(shiftPx, spatial) }
}

/** Posun obrazovky při sdílené ose — Material motion uvádí 30 dp. */
private val SHIFT = 30.dp

/** Odcházející obrazovka zmizí dřív, než se přicházející začne objevovat (fade through). */
private const val FADE_OUT_MS = 90
private const val FADE_IN_MS = 210

private const val PREDICTIVE_MS = 400
private const val PREDICTIVE_SHRINK_MS = 240
private const val PREDICTIVE_ENTER_MS = 80
private const val PREDICTIVE_FADE_DELAY_MS = 300
private const val PREDICTIVE_SCALE = 0.9f
private const val PREDICTIVE_DRIFT = 0.06f
