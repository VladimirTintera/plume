package eu.tintera.plume.core.ui

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/*
 * Otevření chatu ze seznamu jako container transform (Material motion): řádek se rozroste
 * v obrazovku chatu a zpět se do něj zase smrští. Dřív letěl jen avatar a název přes prolnutí
 * obou obrazovek — malá ikona a holý text bez ničeho kolem působily vytrženě. Teď letí UVNITŘ
 * plochy, která roste s nimi.
 *
 * Pár tvoří [chatContainerSource] na řádku a [ChatContainer] kolem obrazovky chatu. Přechod
 * scén se pro tuhle dvojici vypíná (`NavTransitions`): seznam zůstává stát pod rostoucí plochou
 * a obrazovka chatu je celá v ní.
 */

/** Klíč páru řádek seznamu ↔ obrazovka chatu. */
fun chatContainerKey(chatId: Long): String = "chat-container-$chatId"

/**
 * Řádek seznamu chatů, ze kterého se chat otevírá.
 *
 * **Řádek se roste PŘEMĚŘOVÁNÍM** (`RemeasureToBounds`), aby jeho plocha vyplnila celý kontejner
 * — obsah řádku si volající drží v jeho velikosti přes `skipToLookaheadSize`, jinak by se
 * do celé obrazovky roztáhl i on. Neprolíná se (`None`): leží POD obrazovkou chatu, která se
 * na něm objeví, takže kontejner není v žádné chvíli průhledný a seznam jím neprosvítá.
 */
@Composable
fun Modifier.chatContainerSource(chatId: Long): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    if (LocalTwoPane.current) return this
    return with(shared) {
        this@chatContainerSource.sharedBounds(
            sharedContentState = rememberSharedContentState(key = chatContainerKey(chatId)),
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            boundsTransform = { _, _ -> flightSpec },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            zIndexInOverlay = SOURCE_Z,
        )
    }
}

/**
 * Obsah řádku zůstane během růstu ve své velikosti (viz [chatContainerSource]) a drží se nahoře.
 */
@Composable
fun Modifier.chatContainerSourceContent(): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    if (LocalTwoPane.current) return this
    return with(shared) { this@chatContainerSourceContent.skipToLookaheadSize() }
}

/**
 * Obrazovka chatu, která vyrůstá z řádku seznamu ([chatContainerSource]).
 *
 * [fromList] = obrazovka leží v zásobníku přímo na seznamu chatů. Jinak se kontejner nezakládá:
 * téma skupiny nad seznamem témat má tentýž chat a kontejnery by se spárovaly navzájem.
 *
 * **Obsah se ŠKÁLUJE na šířku kontejneru, nepřeměřuje** (`ScaleToBounds`, jako „fit width"
 * v Material container transform). Konverzace se tak rozloží jednou v plné velikosti; přeměřovat
 * ji v každém snímku letu by bylo drahé. Co z ní přečnívá dolů, ořízne [ContainerClip].
 */
@Composable
fun ChatContainer(chatId: Long, fromList: Boolean, content: @Composable () -> Unit) {
    val shared = LocalSharedTransitionScope.current
    if (shared == null || !fromList || LocalTwoPane.current) {
        content()
        return
    }
    val scope = LocalNavAnimatedContentScope.current
    // 0 = obrazovka je ještě řádkem, 1 = je celá. Táž pružina jako let, aby rohy dosedly s ním.
    val open = scope.transition.animateFloat(
        transitionSpec = { clipSpec },
        label = "chat-container-open",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    val clip = remember(open) { ContainerClip(open) }
    val modifier = with(shared) {
        Modifier.fillMaxSize().sharedBounds(
            sharedContentState = rememberSharedContentState(key = chatContainerKey(chatId)),
            animatedVisibilityScope = scope,
            // Objeví se brzy, ať je kontejner co nejdřív neprůhledný; zpět mizí až na konci,
            // když už je zase skoro řádkem (zrcadlově k otevření).
            enter = fadeIn(tween(CONTENT_FADE_MS, delayMillis = ENTER_FADE_DELAY_MS, easing = LinearEasing)),
            exit = fadeOut(tween(CONTENT_FADE_MS, delayMillis = EXIT_FADE_DELAY_MS, easing = LinearEasing)),
            boundsTransform = { _, _ -> flightSpec },
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(
                contentScale = ContentScale.FillWidth,
                alignment = Alignment.TopCenter,
            ),
            clipInOverlayDuringTransition = clip,
            zIndexInOverlay = TARGET_Z,
        )
    }
    Box(modifier) { content() }
}

/**
 * Ořez kontejneru v překryvu: obdélník s rohy, které se s otevřením srovnají z [CONTAINER_CORNER]
 * na nulu. Škálovaný obsah je delší než kontejner, takže ořez je i to, co ho zkracuje.
 */
private class ContainerClip(private val open: State<Float>) : SharedTransitionScope.OverlayClip {
    // Tentýž objekt pro každý snímek, jak doporučuje dokumentace.
    private val path = Path()

    override fun getClipPath(
        sharedContentState: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path {
        val corner = with(density) { CONTAINER_CORNER.toPx() } * (1f - open.value.coerceIn(0f, 1f))
        path.reset()
        path.addRoundRect(RoundRect(bounds, CornerRadius(corner)))
        return path
    }
}

/** Rohy kontejneru na začátku letu — rohy krajních řádků skupiny. */
private val CONTAINER_CORNER = GROUP_CORNER

/**
 * Pořadí v překryvu: řádek pod obrazovkou chatu, obojí pod avatarem a názvem (0) a tečkou
 * dostupnosti (1), které letí samy.
 */
private const val SOURCE_Z = -2f
private const val TARGET_Z = -1f

private const val CONTENT_FADE_MS = 150
private const val ENTER_FADE_DELAY_MS = 50
private const val EXIT_FADE_DELAY_MS = 200
