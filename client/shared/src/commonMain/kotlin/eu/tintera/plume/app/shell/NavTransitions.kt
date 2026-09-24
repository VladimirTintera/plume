package eu.tintera.plume.app.shell

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.SceneDecoratorStrategy
import androidx.navigation3.scene.SceneDecoratorStrategyScope
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation3.scene.Scene

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
     * Zpět gestem, podle specifikace Googlu pro celou obrazovku (návrhové zásady Androidu,
     * „Predictive back" → „Full screen surfaces"): **fade through se zmenšením**. Odcházející
     * obrazovka jde ze 100 na 90 % a zmizí do 35 % průběhu, přicházející jde ze 110 na 100 %
     * a objevuje se od 35 % — v tom bodě není vidět ani jedna. Křivka je (0,1; 0,1; 0; 1),
     * podle Googlu shodná se systémovými animacemi. Zaoblené rohy přidává [PredictiveBackCorners]
     * — přechod obsahu tvar neumí.
     *
     * **Dřív se pod zmenšovanou obrazovkou odkrývala ta předchozí** (jako systémový návrat
     * mezi aktivitami) a mizela až na konci. Po švihnutí tak obrazovka ještě chvíli stála a pak
     * pomalu vybledla; s fade through je odchod hotový v první třetině tahu a po puštění prstu
     * zbývá jen objevení nové obrazovky.
     *
     * **Průběh gesta je lineární** (vzdálenost prstu od hrany) a Google výslovně říká, že se
     * tak přímo použít nemá — proto křivka na zmenšení, ne `LinearEasing`. Průběh k jedničce
     * skoro nedojde, takže s lineárním zmenšením nebylo v půlce tahu vidět nic.
     *
     * **Délka [PREDICTIVE_MS] tah vůbec neovlivňuje, určuje jen dojezd po puštění prstu.**
     * Během tahu jde přechod po zlomcích (`seekTo(progress)`), bez ohledu na milisekundy. Po
     * puštění ho `NavDisplay` dojede sám: zbytek přechodu v jeho vlastním čase, rychlost prstu
     * nebere v úvahu a jiný dojezd nastavit nejde. Přechod pro tlačítko se při dojezdu gesta
     * nevolá (ověřeno logem).
     */
    fun <T : Any> predictivePop(): AnimatedContentTransitionScope<Scene<T>>.(Int) -> ContentTransform = { _ ->
        val enter = scaleIn(tween(PREDICTIVE_MS, easing = PredictiveBackEasing), initialScale = PREDICTIVE_ENTER_SCALE) +
            fadeIn(
                tween(
                    durationMillis = PREDICTIVE_MS - PREDICTIVE_FADE_MS,
                    delayMillis = PREDICTIVE_FADE_MS,
                    easing = LinearEasing,
                ),
            )
        val exit = scaleOut(tween(PREDICTIVE_MS, easing = PredictiveBackEasing), targetScale = PREDICTIVE_EXIT_SCALE) +
            fadeOut(tween(PREDICTIVE_FADE_MS, easing = LinearEasing))
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

/**
 * Zaoblené rohy odcházející scény, jako u systémového predictive back. Bez nich byla zmenšená
 * obrazovka ostře useknutý obdélník a na stejně barevném pozadí seznamu pod ní se ztrácela.
 *
 * `ContentTransform` tvar neumí (má jen průhlednost, posun, měřítko a ořez na rozměr), takže
 * rohy nese **dekorátor scény**: obalí obsah a ořízne ho podle stavu přechodu, ve kterém scéna
 * je. Poloměr je animace TÉHOŽ přechodu (`transition.animateDp`), takže ho gesto posouvá spolu
 * se zmenšením a po zrušeném tahu se vrátí na nulu.
 *
 * Rohy dostane i obrazovka odcházející tlačítkem (dopředu i zpět) — přechod neprozradí, jestli
 * běží gestem. Tam ale obrazovka během nich mizí (za 90 ms), takže to skoro vidět není.
 *
 * **Při fade through jsou rohy jemný detail.** Zaoblená obrazovka je vidět jen do 35 % tahu
 * a pod ní je plocha stejné barvy; u návratu mezi aktivitami, kde je rohy vidět víc, se pod
 * zmenšenou obrazovkou odkrývá ta předchozí.
 *
 * Překryvné scény (dialogy) Nav3 dekorátorům nepodává vůbec.
 */
internal class PredictiveBackCorners<T : Any> : SceneDecoratorStrategy<T> {
    override fun SceneDecoratorStrategyScope<T>.decorateScene(scene: Scene<T>): Scene<T> =
        RoundedExitScene(scene)
}

/**
 * Obal scény, který jen ořízne její obsah. Všechno ostatní patří obalené scéně — i klíč:
 * `NavDisplay` rozlišuje scény podle třídy a klíče a třída je tu u všech stejná, takže klíč nese
 * i třídu obalené scény, aby se jednopanelová scéna nepotkala s list-detail scénou o stejném klíči.
 */
private class RoundedExitScene<T : Any>(private val scene: Scene<T>) : Scene<T> {
    override val key: Any = scene::class to scene.key
    override val entries: List<NavEntry<T>> get() = scene.entries
    override val previousEntries: List<NavEntry<T>> get() = scene.previousEntries
    override val metadata: Map<String, Any> get() = scene.metadata
    override val content: @Composable () -> Unit = {
        Box(Modifier.roundedWhileExiting()) { scene.content() }
    }

    override fun equals(other: Any?): Boolean = other is RoundedExitScene<*> && other.scene == scene
    override fun hashCode(): Int = scene.hashCode()
    override fun toString(): String = "RoundedExitScene($scene)"
}

@Composable
private fun Modifier.roundedWhileExiting(): Modifier {
    val radius = LocalNavAnimatedContentScope.current.transition.animateDp(
        // Plný poloměr do chvíle, kdy obrazovka zmizí (35 % gesta); potom by nebyl vidět.
        transitionSpec = { tween(PREDICTIVE_FADE_MS, easing = LinearEasing) },
        label = "exit-corners",
    ) { if (it == EnterExitState.PostExit) PREDICTIVE_CORNER else 0.dp }
    // Čte se až ve vrstvě: tah posouvá poloměr každý snímek a scéna se kvůli tomu nerekomponuje.
    return graphicsLayer {
        val r = radius.value
        clip = r > 0.dp
        if (clip) shape = RoundedCornerShape(r)
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

/**
 * Celková délka přechodu gestem = nejdelší dojezd po puštění prstu (viz
 * [NavTransitions.predictivePop]). Rozdělení je v podílech, protože tah přechodem posouvá po
 * zlomcích: odchod do 35 %, příchod od 35 %, jak to uvádí specifikace Googlu.
 */
private const val PREDICTIVE_MS = 240
private const val PREDICTIVE_FADE_MS = PREDICTIVE_MS * 35 / 100
private const val PREDICTIVE_EXIT_SCALE = 0.9f
private const val PREDICTIVE_ENTER_SCALE = 1.1f

/** Křivka ze specifikace Googlu pro predictive back, „stejná jako u systémových animací". */
private val PredictiveBackEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

/** Rohy zmenšené obrazovky — `extraLarge` z tvarové škály M3, blízko rohům displeje telefonů. */
private val PREDICTIVE_CORNER = 28.dp
