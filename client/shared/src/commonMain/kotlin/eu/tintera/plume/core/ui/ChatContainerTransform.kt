package eu.tintera.plume.core.ui

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
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
 * Pár tvoří [rememberChatContainerSource] na řádku a [ChatContainer] kolem obrazovky chatu. Přechod
 * scén se pro tuhle dvojici vypíná (`NavTransitions`): seznam zůstává stát pod rostoucí plochou
 * a obrazovka chatu je celá v ní.
 */

/** Klíč páru řádek seznamu ↔ obrazovka chatu. */
fun chatContainerKey(chatId: Long): String = "chat-container-$chatId"

/**
 * Řádek seznamu chatů, ze kterého se chat otevírá: [container] patří na plochu řádku,
 * [content] na jeho obsah.
 */
@Stable
class ChatContainerSource internal constructor(val container: Modifier, val content: Modifier)

private val NoChatContainerSource = ChatContainerSource(Modifier, Modifier)

/**
 * **Plocha řádku roste PŘEMĚŘOVÁNÍM** (`RemeasureToBounds`), aby vyplnila celý kontejner.
 * Neprolíná se (`None`): leží POD obrazovkou chatu, která se na ní objeví, takže kontejner
 * není v žádné chvíli průhledný a seznam jím neprosvítá.
 *
 * **Obsah řádku drží svou velikost** (`skipToLookaheadSize`, jinak by se roztáhl do celé
 * obrazovky) **a na začátku letu rychle zmizí** — fade through jako v Material container
 * transform. Dokud zůstával, prosvítal náhled zprávy přes objevující se hlavičku konverzace.
 * Zpátky se objeví brzy, ještě pod konverzací, která ho kryje, dokud sama nezmizí.
 *
 * Mizení platí jen při spárovaném letu. Délky se drží v mezích přechodů scén
 * (`NavTransitions`): animace řádku je animací TÉHOŽ přechodu a delší by u návratu gestem
 * z jiné obrazovky (Nastavení) natáhla celý přechod a posunula tah.
 */
@Composable
fun rememberChatContainerSource(chatId: Long): ChatContainerSource {
    val shared = LocalSharedTransitionScope.current ?: return NoChatContainerSource
    if (LocalTwoPane.current) return NoChatContainerSource
    val scope = LocalNavAnimatedContentScope.current
    val state = with(shared) { rememberSharedContentState(key = chatContainerKey(chatId)) }
    val contentAlpha = scope.transition.animateFloat(
        transitionSpec = {
            if (targetState == EnterExitState.Visible) {
                tween(CONTENT_FADE_MS, delayMillis = SOURCE_FADE_IN_DELAY_MS, easing = LinearEasing)
            } else {
                tween(SOURCE_FADE_OUT_MS, easing = LinearEasing)
            }
        },
        label = "chat-row-content",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    return with(shared) {
        ChatContainerSource(
            container = Modifier.sharedBounds(
                sharedContentState = state,
                animatedVisibilityScope = scope,
                enter = EnterTransition.None,
                exit = ExitTransition.None,
                boundsTransform = { _, _ -> flightSpec },
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                zIndexInOverlay = SOURCE_Z,
            ),
            content = Modifier.skipToLookaheadSize().graphicsLayer {
                // Čte se až ve vrstvě: průběh se mění každý snímek a řádek se kvůli tomu nerekomponuje.
                alpha = if (isTransitionActive && state.isMatchFound) contentAlpha.value else 1f
            },
        )
    }
}

/**
 * Obrazovka chatu, která vyrůstá z řádku seznamu ([rememberChatContainerSource]).
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
    /*
     * Průhlednost obrazovky řídí ona sama, ne `enter`/`exit` sdílené plochy. Plocha bez páru
     * (řádek odscrollovaný, chat se mezitím přesunul, nebo chat v seznamu vůbec není) svoje
     * mizení nepoužila: konverzace při návratu stála a pak zmizela skokem. Bez páru proto mizí
     * a objevuje se jako fade through ostatních přechodů, s párem zpožděně podle letu.
     */
    val flightAlpha = scope.transition.animateFloat(
        transitionSpec = {
            if (targetState == EnterExitState.Visible) {
                // Objeví se brzy, ať je kontejner co nejdřív neprůhledný.
                tween(CONTENT_FADE_MS, delayMillis = ENTER_FADE_DELAY_MS, easing = LinearEasing)
            } else {
                // Zpět mizí až na konci, když už je zase skoro řádkem (zrcadlově k otevření).
                tween(CONTENT_FADE_MS, delayMillis = EXIT_FADE_DELAY_MS, easing = LinearEasing)
            }
        },
        label = "chat-container-alpha",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    val plainAlpha = scope.transition.animateFloat(
        transitionSpec = {
            if (targetState == EnterExitState.Visible) {
                tween(PLAIN_FADE_IN_MS, delayMillis = PLAIN_FADE_OUT_MS, easing = LinearEasing)
            } else {
                tween(PLAIN_FADE_OUT_MS, easing = LinearEasing)
            }
        },
        label = "chat-container-plain-alpha",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    val transition = scope.transition
    val state = with(shared) { rememberSharedContentState(key = chatContainerKey(chatId)) }
    val modifier = with(shared) {
        Modifier.fillMaxSize().sharedBounds(
            sharedContentState = state,
            animatedVisibilityScope = scope,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            boundsTransform = { _, _ -> flightSpec },
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(
                contentScale = ContentScale.FillWidth,
                alignment = Alignment.TopCenter,
            ),
            clipInOverlayDuringTransition = clip,
            zIndexInOverlay = TARGET_Z,
        ).graphicsLayer {
            // Čte se až ve vrstvě, let nic nerekomponuje.
            alpha = when {
                transition.currentState == transition.targetState -> 1f
                state.isMatchFound -> flightAlpha.value
                else -> plainAlpha.value
            }
        }
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
private const val EXIT_FADE_DELAY_MS = 200

/** Bez páru jako fade through ostatních přechodů (`NavTransitions`). */
private const val PLAIN_FADE_OUT_MS = 90
private const val PLAIN_FADE_IN_MS = 210

/** Obsah řádku zmizí rychleji, než se konverzace začne objevovat — dvojí expozice nenastane. */
private const val SOURCE_FADE_OUT_MS = 60
private const val SOURCE_FADE_IN_DELAY_MS = 90
private const val ENTER_FADE_DELAY_MS = SOURCE_FADE_OUT_MS
