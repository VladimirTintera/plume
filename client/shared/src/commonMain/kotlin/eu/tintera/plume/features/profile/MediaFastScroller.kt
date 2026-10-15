package eu.tintera.plume.features.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.MediaPosition
import eu.tintera.plume.core.telegram.SharedMediaPositions
import eu.tintera.plume.core.ui.monthTitle
import eu.tintera.plume.core.ui.tick
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import kotlin.math.roundToInt
import kotlin.time.Instant

/**
 * Rychlé rolování v mřížce Médií: úchyt u pravého okraje, kterým jde dojet kamkoli do historie,
 * a během tažení bublina s měsícem pod prstem. Puštění skočí na nejbližší zprávu z řídkých
 * pozic ([onJump]) — mřížka pak načte okno kolem ní.
 *
 * - **Poloha je podíl v CELÉ historii** ([SharedMediaPositions.totalCount]), ne v načteném
 *   seznamu: načtená je jen jeho malá část a úchyt by s každou stránkou poskočil nahoru.
 * - **Dotyk bere jen úchyt**, ne celý pruh u okraje. Překrývající se sourozenci si dotyk nedělí,
 *   takže pruh přes celou výšku by u pravého okraje mřížku přestal rolovat i ťukat.
 * - **Ukáže se jen při rolování a tažení** a chvíli po něm zmizí — trvale viditelný úchyt by
 *   ležel přes dlaždice v pravém sloupci.
 * - **Při tažení tiká s každým novým měsícem** (`tick`, procházení po dílcích) — kolik jsem
 *   přejel, je cítit i bez pohledu na bublinu.
 * - Čtečka obrazovky úchyt nevidí: rolování mřížkou jí stačí a tažení po pixelech jí nic nedá.
 */
@Composable
internal fun MediaFastScroller(
    positions: SharedMediaPositions,
    gridState: LazyGridState,
    /** Zpráva nejvyšší viditelné dlaždice; čte se až při výpočtu polohy. */
    firstVisibleMessageId: () -> Long?,
    contentPadding: PaddingValues,
    onJump: (MediaPosition) -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = positions.totalCount.coerceAtLeast(1)
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val scrolledFraction by remember(positions) {
        derivedStateOf {
            val id = firstVisibleMessageId() ?: return@derivedStateOf 0f
            (positions.estimatePosition(id) ?: 0).toFloat() / total
        }
    }
    val fraction = dragFraction ?: scrolledFraction
    // Gesto žije déle než jedno složení; poloha a pozice se v něm čtou aktuální.
    val currentFraction by rememberUpdatedState(fraction)
    val currentPositions by rememberUpdatedState(positions)

    // Vidět jen při pohybu; po něm chvíli zůstane, ať jde chytit.
    val moving = gridState.isScrollInProgress || dragFraction != null
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(moving) {
        if (moving) {
            shown = true
        } else {
            delay(HIDE_DELAY_MILLIS)
            shown = false
        }
    }

    // Měsíc pod prstem a tik při jeho změně.
    val haptics = LocalHapticFeedback.current
    val dragged = dragFraction?.let { positions.nearest((it * (total - 1)).roundToInt()) }
    val month = dragged?.let { Instant.fromEpochSeconds(it.timestamp.toLong()).toLocalDateTime(TimeZone.currentSystemDefault()).date.yearMonth }
    var lastMonth by remember { mutableStateOf(month) }
    LaunchedEffect(month) {
        if (month != null && lastMonth != null && month != lastMonth) haptics.tick()
        lastMonth = month
    }

    val jump by rememberUpdatedState(onJump)
    BoxWithConstraints(modifier.fillMaxSize().clearAndSetSemantics {}) {
        val top = contentPadding.calculateTopPadding()
        val bottom = contentPadding.calculateBottomPadding()
        val track = (maxHeight - top - bottom - THUMB_HEIGHT).coerceAtLeast(1.dp)
        val trackPx = with(LocalDensity.current) { track.toPx() }
        val thumbY = top + track * fraction.coerceIn(0f, 1f)

        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.offset { IntOffset(0, thumbY.roundToPx()) },
            ) {
                if (month != null) {
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        Text(
                            monthTitle(month),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                // Dotyková plocha je širší než kreslený úchyt, ať se dá chytit prstem.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(width = THUMB_TOUCH_WIDTH, height = THUMB_HEIGHT)
                        .pointerInput(trackPx, total) {
                            detectVerticalDragGestures(
                                onDragStart = { dragFraction = currentFraction },
                                onDragEnd = {
                                    val target = dragFraction?.let { currentPositions.nearest((it * (total - 1)).roundToInt()) }
                                    dragFraction = null
                                    target?.let(jump)
                                },
                                onDragCancel = { dragFraction = null },
                                onVerticalDrag = { change, dy ->
                                    change.consume()
                                    dragFraction = ((dragFraction ?: currentFraction) + dy / trackPx).coerceIn(0f, 1f)
                                },
                            )
                        },
                ) {
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = if (dragFraction != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondary
                        },
                        modifier = Modifier.width(THUMB_WIDTH).height(THUMB_HEIGHT),
                    ) {}
                }
            }
        }
    }
}

private val THUMB_HEIGHT = 48.dp
private val THUMB_WIDTH = 6.dp
private val THUMB_TOUCH_WIDTH = 32.dp

/** Jak dlouho úchyt po zastavení zůstane vidět. */
private const val HIDE_DELAY_MILLIS = 1_500L
