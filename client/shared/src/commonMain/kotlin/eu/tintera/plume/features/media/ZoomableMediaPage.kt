package eu.tintera.plume.features.media

import androidx.compose.runtime.derivedStateOf
import eu.tintera.plume.core.ui.fileDragSupported
import eu.tintera.plume.core.ui.fileDragSource
import androidx.compose.ui.input.pointer.PointerType
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.ui.MEDIA_CARD_CORNER
import eu.tintera.plume.core.ui.MediaPageLayout
import eu.tintera.plume.core.ui.threshold
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlinx.coroutines.flow.Flow
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
 * ([MediaPageGestures.onEngage]); přiblížení v galerii nedává smysl, fotka by rostla přes lišty.
 *
 * Tah se nerozpoznává přes `detectTransformGestures`. Ten konzumuje pohyb v každém směru,
 * takže by fotka spolkla i vodorovný tah a pager nad ní by nešel posunout. Proto vlastní
 * rozhodnutí po překročení slopu: nepřiblížená fotka **vodorovný tah pustí pageru**, svislý
 * si nechá na zavření; přiblížená si nechá všechno (posun po fotce). Listovat se tedy dá jen
 * s fotkou v základní velikosti — posun po přiblížené fotce by se jinak pral s listováním.
 *
 * Posun i přiblížení se čtou **až v `graphicsLayer`**, takže se při gestu nic nerekomponuje;
 * překresluje se jen vrstva s obrázkem.
 *
 * **Myš a klávesnice** (desktop, Android s myší): štípnutí potřebuje dva prsty, takže myš by
 * uměla jen dvojklik na 2,5×. Proto:
 * - **Ctrl + kolečko přibližuje kolem kurzoru** (s ⌘ taky). Na Windows posílá štípnutí na
 *   touchpadu systém právě jako Ctrl + kolečko, takže tatáž obsluha pokryje i touchpad.
 *   Události štípnutí, které Compose zná (`ScaleChange`), desktopová vrstva neposílá vůbec.
 * - **Kolečko bez Ctrl posouvá přiblíženou fotku** (Shift do strany). Nepřiblížené nechá být —
 *   pager pak dostane Shift + kolečko a touchpad do strany jako dřív.
 * - **Klávesy +/−/0** posílá obrazovka jako [zoomSteps]; přibližuje se kolem středu stránky.
 */
@Composable
internal fun ZoomableMediaPage(
    aspectRatio: Float,
    caption: String,
    placement: MediaPagePlacement,
    dismissDrag: Animatable<Float, *>,
    gestures: MediaPageGestures,
    /** Přiblížit jde jen odkrytý snímek; zakrytý spoilerem dvojité ťuknutí jen ťukne. */
    zoomable: Boolean = true,
    /** Přiblížení z klávesnice (+/−/0); bere si ho jen stránka, na které pager stojí. */
    zoomSteps: Flow<ZoomStep>? = null,
    modifier: Modifier = Modifier,
    /** Modifikátor karty se snímkem, pod přiblížením (sdílený prvek z bubliny). */
    cardModifier: Modifier = Modifier,
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
    val isCurrent = placement.isCurrent
    val immersive = placement.immersive
    val immersion = placement.immersion
    val currentEngage by rememberUpdatedState(gestures.onEngage)
    val currentTap by rememberUpdatedState(gestures.onTap)
    val currentDismiss by rememberUpdatedState(gestures.onDismiss)
    val currentZoomable by rememberUpdatedState(zoomable)
    // Tah ven ([MediaPageGestures.dragOut]) jen u nepřiblížené fotky — přiblíženou tah myší posouvá.
    val dragOut = gestures.dragOut
    val draggable = dragOut != null && fileDragSupported
    val currentDraggable by rememberUpdatedState(draggable)

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    // Tah ven jen u nepřiblížené fotky; přes `derivedStateOf`, ať přiblížení nerekomponuje každý snímek.
    val unzoomed by remember { derivedStateOf { scale == 1f } }

    // Sousední stránka zůstává složená; kdyby si přiblížení nechala, vrátil by se uživatel
    // k fotce v půlce výřezu a nešlo by z ní listovat dál.
    LaunchedEffect(isCurrent) {
        if (!isCurrent) {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
        }
    }

    /** Přiblížení na [target] s animací; bod [focus] zůstane na místě (kromě návratu na 1×). */
    suspend fun animateZoom(target: Float, focus: Offset) {
        val destination = if (target == 1f) Offset.Zero else card.zoomedOffset(focus, Offset(offsetX, offsetY), scale, target)
        val fromScale = scale
        val fromX = offsetX
        val fromY = offsetY
        // Jedna animace řídí všechny tři hodnoty, aby se posun nerozešel s přiblížením a fotka
        // po cestě neuhýbala.
        animate(0f, 1f) { progress, _ ->
            scale = fromScale + (target - fromScale) * progress
            offsetX = fromX + (destination.x - fromX) * progress
            offsetY = fromY + (destination.y - fromY) * progress
        }
    }

    // Klávesy +/−/0 z obrazovky. Kroky se zpracují postupně, rychlé mačkání se nesloučí.
    LaunchedEffect(isCurrent, zoomSteps) {
        if (!isCurrent || zoomSteps == null) return@LaunchedEffect
        zoomSteps.collect { step ->
            if (!currentZoomable) return@collect
            val target = when (step) {
                ZoomStep.In -> (scale * KEY_ZOOM_STEP).coerceAtMost(MAX_SCALE)
                ZoomStep.Out -> (scale / KEY_ZOOM_STEP).coerceAtLeast(1f)
                ZoomStep.Reset -> 1f
            }
            if (target == scale) return@collect
            if (target > 1f) currentEngage()
            animateZoom(target, Offset(card.page.width / 2f, card.page.height / 2f))
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
        reservedTop = placement.reservedTop,
        reservedBottom = placement.reservedBottom,
        immersion = immersion,
        captionAlpha = placement.captionAlpha,
        cardBias = placement.cardBias,
        modifier = modifier
            .onSizeChanged { card.page = it }
            // Kolečko myši: s Ctrl (⌘) přibližuje kolem kurzoru, bez něj posouvá přiblíženou fotku.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Scroll) continue
                        val change = event.changes.firstOrNull() ?: continue
                        if (change.isConsumed) continue
                        val delta = event.changes.fold(Offset.Zero) { sum, it -> sum + it.scrollDelta }
                        val zoomKey = event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed
                        when {
                            zoomKey && currentZoomable -> {
                                // `scrollDelta` je v zubech kolečka (touchpad posílá zlomky),
                                // takže zub = jeden násobek kroku a štípnutí je plynulé.
                                val target = (scale * WHEEL_ZOOM_STEP.pow(-delta.y)).coerceIn(1f, MAX_SCALE)
                                if (target != scale) {
                                    if (target > 1f) currentEngage()
                                    val destination = if (target == 1f) {
                                        Offset.Zero
                                    } else {
                                        card.zoomedOffset(change.position, Offset(offsetX, offsetY), scale, target)
                                    }
                                    scale = target
                                    offsetX = destination.x
                                    offsetY = destination.y
                                }
                                // I na krajích (1×, 5×): okno ani pager Ctrl + kolečko dostat nemají.
                                change.consume()
                            }
                            scale > 1f -> {
                                val step = WHEEL_PAN_STEP.toPx()
                                val moved = card.clamp(offsetX - delta.x * step, offsetY - delta.y * step, scale)
                                offsetX = moved.x
                                offsetY = moved.y
                                change.consume()
                            }
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Myš nad nepřiblíženou fotkou táhne snímek ven; zavírá se Esc a ✕.
                    if (down.type == PointerType.Mouse && currentDraggable && scale == 1f) return@awaitEachGesture
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

                                    val clamped = card.clamp(
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
                        scope.launch { animateZoom(target, tap) }
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
                    .then(if (draggable && unzoomed && dragOut != null) Modifier.fileDragSource(dragOut) else Modifier)
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
    var page: IntSize = IntSize.Zero

    /** Meze posunu: přiblížená fotka nesmí odjet tak, aby kraj stránky zůstal prázdný. */
    fun clamp(x: Float, y: Float, forScale: Float): Offset {
        val maxX = max(0f, (bounds.width * forScale - page.width) / 2f)
        val maxY = max(0f, (bounds.height * forScale - page.height) / 2f)
        return Offset(x.coerceIn(-maxX, maxX), y.coerceIn(-maxY, maxY))
    }

    /**
     * Posun po přiblížení z [fromScale] na [toScale] tak, aby bod [focus] (souřadnice stránky)
     * zůstal na místě — přibližuje se kolem kurzoru nebo ťuknutí, ne kolem středu.
     */
    fun zoomedOffset(focus: Offset, from: Offset, fromScale: Float, toScale: Float): Offset {
        val relativeX = focus.x - bounds.center.x
        val relativeY = focus.y - bounds.center.y
        val factor = toScale / fromScale
        return clamp(relativeX - (relativeX - from.x) * factor, relativeY - (relativeY - from.y) * factor, toScale)
    }
}

/** Krok přiblížení z klávesnice. */
internal enum class ZoomStep { In, Out, Reset }

/** Co právě dělá prst na fotce. Rozhoduje se jednou za gesto, po překročení slopu. */
private enum class Gesture { Undecided, Transform, Dismiss, Page }

/** Strop přiblížení. Nad tím jsou z fotky jen pixely. */
private const val MAX_SCALE = 5f

/** Kam skočí dvojité ťuknutí. */
private const val DOUBLE_TAP_SCALE = 2.5f

/** Přiblížení na jeden zub kolečka s Ctrl. */
private const val WHEEL_ZOOM_STEP = 1.25f

/** Přiblížení na jedno stisknutí +/−. */
private const val KEY_ZOOM_STEP = 1.5f

/** Posun přiblížené fotky na jeden zub kolečka. */
private val WHEEL_PAN_STEP = 64.dp
