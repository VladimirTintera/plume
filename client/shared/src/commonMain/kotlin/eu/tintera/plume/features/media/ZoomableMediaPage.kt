package eu.tintera.plume.features.media

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import eu.tintera.plume.core.ui.MEDIA_CARD_CORNER
import eu.tintera.plume.core.ui.MediaPageLayout
import eu.tintera.plume.core.ui.threshold
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.launch

/**
 * Stránka se snímkem, který jde přiblížit — v prohlížeči médií i v potvrzení sdílení do chatu.
 *
 * Co se kreslí, dodá volající ([content]: fotka ze zprávy přes `RemoteImage`, sdílený soubor
 * přes Coil); stránka nese jen mechaniku. Dřív byla uvnitř prohlížeče napevno spojená s fotkou
 * ze zprávy, a sdílení proto snímek zvětšit neumělo vůbec — kopie gest by se s prohlížečem
 * rozešla na prvním doladění, stejně jako se to stalo s carouselem ([MediaCarousel]).
 *
 * Gesta jsou dvě a **každé má vlastní `pointerInput`**: tah a štípnutí, a ťuknutí (přepnutí
 * galerie a celé obrazovky, dvojité přiblížení). Obě sedí na celé stránce, ne na kartě —
 * štípnout jde i vedle úzké fotky. Štípnutí a dvojité ťuknutí přepnou na celou obrazovku
 * ([onEngage]); přiblížení v galerii nedává smysl, fotka by rostla přes lišty.
 *
 * Tah se nerozpoznává přes `detectTransformGestures`. Ten konzumuje pohyb v každém směru,
 * takže by fotka spolkla i vodorovný tah a pager nad ní by nešel posunout. Proto vlastní
 * rozhodnutí po překročení slopu: nepřiblížená fotka **vodorovný tah pustí pageru**, svislý
 * si nechá na zavření; přiblížená si nechá všechno (posun po fotce). Listovat se tedy dá jen
 * s fotkou v základní velikosti — posun po přiblížené fotce by se jinak pral s listováním.
 *
 * Posun i přiblížení se čtou **až v `graphicsLayer`**, takže se při gestu nic nerekomponuje;
 * překresluje se jen vrstva s obrázkem.
 */
@Composable
internal fun ZoomableMediaPage(
    aspectRatio: Float,
    caption: String,
    /** Stránka, na které pager stojí. Odlistovaná fotka se vrací do základní velikosti. */
    isCurrent: Boolean,
    /** Celá obrazovka; návrat do galerie vrací přiblížení na základní velikost. */
    immersive: Boolean,
    immersion: () -> Float,
    reservedTop: Dp,
    reservedBottom: Dp,
    dismissDrag: Animatable<Float, *>,
    /**
     * Svislý tah přes práh. Prohlížeč se jím zavírá, sdílení jím opouští celou obrazovku;
     * `null` = svislý tah nic nedělá (sdílení v galerii — není kam odejít).
     */
    onDismiss: (() -> Unit)?,
    /** Ťuknutí: přepne galerii a celou obrazovku, nebo odkryje spoiler — rozhodne volající. */
    onTap: () -> Unit,
    /** Přiblížení gestem — přepne na celou obrazovku. */
    onEngage: () -> Unit,
    /** Přiblížit jde jen odkrytý snímek; zakrytý spoilerem dvojité ťuknutí jen ťukne. */
    zoomable: Boolean = true,
    modifier: Modifier = Modifier,
    /** Modifikátor karty se snímkem, pod přiblížením (sdílený prvek z bubliny). */
    cardModifier: Modifier = Modifier,
    captionAlpha: () -> Float = { 1f },
    cardBias: () -> Float = { 0f },
    /** Ovládání na kartě, které se s fotkou nepřibližuje (✕ a volba režimu ve sdílení). */
    overlay: @Composable BoxScope.() -> Unit = {},
    /** Snímek; vyplní kartu a přibližuje se. */
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    // Práh zavíracího tahu se ohlásí jednou za gesto. Pole, ne stav: čte se v obsluze gesta.
    var dismissArmed by remember { mutableStateOf(false) }
    val card = remember { CardGeometry() }
    val currentEngage by rememberUpdatedState(onEngage)
    val currentTap by rememberUpdatedState(onTap)
    val currentDismiss by rememberUpdatedState(onDismiss)
    val currentZoomable by rememberUpdatedState(zoomable)

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Sousední stránka zůstává složená; kdyby si přiblížení nechala, vrátil by se uživatel
    // k fotce v půlce výřezu a nešlo by z ní listovat dál.
    LaunchedEffect(isCurrent) {
        if (!isCurrent) {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
        }
    }

    // Galerie přiblížení nezná: fotka dosedne zpátky spolu s tím, jak se karta zmenšuje.
    LaunchedEffect(immersive) {
        if (!immersive && scale != 1f) {
            val fromScale = scale
            val fromX = offsetX
            val fromY = offsetY
            animate(0f, 1f) { progress, _ ->
                scale = fromScale + (1f - fromScale) * progress
                offsetX = fromX * (1f - progress)
                offsetY = fromY * (1f - progress)
            }
        }
    }

    MediaPageLayout(
        aspectRatio = aspectRatio,
        caption = caption,
        reservedTop = reservedTop,
        reservedBottom = reservedBottom,
        immersion = immersion,
        captionAlpha = captionAlpha,
        cardBias = cardBias,
        modifier = modifier
            .pointerInput(Unit) {
                /** Meze posunu: přiblížená fotka nesmí odjet tak, aby kraj stránky zůstal prázdný. */
                fun clampOffset(x: Float, y: Float, forScale: Float): Offset {
                    val maxX = max(0f, (card.bounds.width * forScale - size.width) / 2f)
                    val maxY = max(0f, (card.bounds.height * forScale - size.height) / 2f)
                    return Offset(x.coerceIn(-maxX, maxX), y.coerceIn(-maxY, maxY))
                }

                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var gesture = Gesture.Undecided
                    var travel = Offset.Zero

                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        val pan = event.calculatePan()

                        if (gesture == Gesture.Undecided) {
                            travel += pan
                            val pastSlop = travel.getDistance() > viewConfiguration.touchSlop
                            gesture = when {
                                !currentZoomable && fingers > 1 -> Gesture.Page
                                // Dva prsty jsou štípnutí vždycky, i bez slopu.
                                fingers > 1 -> Gesture.Transform
                                !pastSlop -> Gesture.Undecided
                                scale > 1f -> Gesture.Transform
                                abs(travel.y) > abs(travel.x) && currentDismiss != null -> Gesture.Dismiss
                                // Vodorovně u nepřiblížené fotky: listování, patří pageru.
                                else -> Gesture.Page
                            }
                        }

                        when (gesture) {
                            Gesture.Transform -> {
                                // Štípnutí v galerii přepne na celou obrazovku; posun přiblížené
                                // fotky se tam dá dělat jen na ní, takže se nic nemění.
                                if (fingers > 1) currentEngage()
                                val zoom = event.calculateZoom()
                                val centroid = event.calculateCentroid(useCurrent = false)
                                val next = (scale * zoom).coerceIn(1f, MAX_SCALE)

                                if (centroid != Offset.Unspecified && !(next == 1f && scale == 1f)) {
                                    /*
                                     * Přiblížení kolem prstů, ne kolem středu.
                                     *
                                     * Bod pod prsty musí zůstat na místě, jinak fotka při
                                     * štípnutí ujíždí a uživatel ji pak dohání posunem.
                                     */
                                    val relativeX = centroid.x - card.bounds.center.x
                                    val relativeY = centroid.y - card.bounds.center.y
                                    val factor = next / scale

                                    val clamped = clampOffset(
                                        x = relativeX - (relativeX - offsetX) * factor + pan.x,
                                        y = relativeY - (relativeY - offsetY) * factor + pan.y,
                                        forScale = next,
                                    )

                                    scale = next
                                    offsetX = clamped.x
                                    offsetY = clamped.y
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }

                            Gesture.Dismiss -> {
                                scope.launch { dismissDrag.snapTo(dismissDrag.value + pan.y) }
                                /*
                                 * Práh se hlásí PŘI PŘEKROČENÍ, ne až po puštění: uživatel má
                                 * vědět, že stačí pustit. `armed` drží, aby se ozval jednou —
                                 * tatáž mechanika jako u archivace tahem v seznamu chatů.
                                 */
                                val past = abs(dismissDrag.value) > size.height * DISMISS_THRESHOLD
                                if (past && !dismissArmed) haptics.threshold()
                                dismissArmed = past
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }

                            Gesture.Undecided, Gesture.Page -> Unit
                        }
                    } while (event.changes.any { it.pressed })

                    if (gesture == Gesture.Dismiss) {
                        dismissArmed = false
                        val dismiss = currentDismiss
                        if (dismiss != null && abs(dismissDrag.value) > size.height * DISMISS_THRESHOLD) {
                            dismiss()
                        } else {
                            scope.launch { dismissDrag.animateTo(0f) }
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentTap() },
                    onDoubleTap = { tap ->
                        if (!currentZoomable) {
                            currentTap()
                            return@detectTapGestures
                        }
                        currentEngage()
                        val target = if (scale > 1f) 1f else DOUBLE_TAP_SCALE
                        val relativeX = tap.x - card.bounds.center.x
                        val relativeY = tap.y - card.bounds.center.y
                        val factor = target / scale

                        val destination = if (target == 1f) {
                            Offset.Zero
                        } else {
                            val maxX = max(0f, (card.bounds.width * target - size.width) / 2f)
                            val maxY = max(0f, (card.bounds.height * target - size.height) / 2f)
                            Offset(
                                (relativeX - (relativeX - offsetX) * factor).coerceIn(-maxX, maxX),
                                (relativeY - (relativeY - offsetY) * factor).coerceIn(-maxY, maxY),
                            )
                        }

                        val fromScale = scale
                        val fromX = offsetX
                        val fromY = offsetY

                        scope.launch {
                            // Jedna animace řídí všechny tři hodnoty, aby se posun
                            // nerozešel s přiblížením a fotka po cestě neuhýbala.
                            animate(0f, 1f) { progress, _ ->
                                scale = fromScale + (target - fromScale) * progress
                                offsetX = fromX + (destination.x - fromX) * progress
                                offsetY = fromY + (destination.y - fromY) * progress
                            }
                        }
                    },
                )
            }
            // Zavírací tah posouvá kartu i s popiskem. Za gesty, aby souřadnice dotyku zůstaly
            // v neposunuté stránce.
            .graphicsLayer {
                translationY = dismissDrag.value
                // Při zavíracím tahu se stránka zmenšuje, takže je vidět, že odchází pryč.
                val shrink = 1f - (abs(dismissDrag.value) / size.height).coerceIn(0f, 1f) * 0.2f
                scaleX = shrink
                scaleY = shrink
            },
    ) {
        Box(Modifier.fillMaxSize().onPlaced { card.bounds = it.boundsInParent() }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(cardModifier)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                        // Zaoblení nese vrstva, takže se s přiblížením zvětšuje spolu s fotkou.
                        // Na celé obrazovce mizí: fotka jde od kraje ke kraji.
                        clip = true
                        this.shape = RoundedCornerShape(MEDIA_CARD_CORNER * (1f - immersion()))
                    },
                content = content,
            )
            overlay()
        }
    }
}

/** Kde leží karta se snímkem ve stránce. Mění se při layoutu, čte se jen v gestech — proto ne stav. */
private class CardGeometry {
    var bounds: Rect = Rect.Zero
}

/** Co právě dělá prst na fotce. Rozhoduje se jednou za gesto, po překročení slopu. */
private enum class Gesture { Undecided, Transform, Dismiss, Page }

/** Strop přiblížení. Nad tím jsou z fotky jen pixely. */
private const val MAX_SCALE = 5f

/** Kam skočí dvojité ťuknutí. */
private const val DOUBLE_TAP_SCALE = 2.5f
