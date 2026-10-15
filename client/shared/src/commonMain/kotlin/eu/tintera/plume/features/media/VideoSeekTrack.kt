package eu.tintera.plume.features.media

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.layout.fillMaxWidth
import eu.tintera.plume.core.telegram.FileState
import kotlin.math.max
import kotlin.math.min

/**
 * Dráha posuvníku videa: přehraná část (vlní se, dokud video hraje) a pod ní **stažený úsek**
 * světlejší barvou, jako v přehrávačích na webu. Dřív byl stav stahování zvlášť, kroužek v rohu
 * karty, který se ťuknutím rozvinul do čáry — dvě čáry přes video říkaly skoro totéž.
 *
 * - **Úsek se kreslí PŘES dráhu indikátoru**, ne pod ní: bílá s průhledností přes průhlednou
 *   bílou dá světlejší odstín a přes plnou bílou přehrané části nic nezmění.
 * - **Začíná až za mezerou u přehrané části** (geometrie z `LinearWavyProgressModifiers`
 *   v material3: dráha začíná `hlava + mezera + 2 × poloměr zakončení`), jinak by mezeru zalil.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun VideoSeekTrack(
    progress: () -> Float,
    buffered: ClosedFloatingPointRange<Float>?,
    wavy: Boolean,
    modifier: Modifier = Modifier,
) {
    val trackStroke = WavyProgressIndicatorDefaults.linearTrackStroke
    val gap = WavyProgressIndicatorDefaults.LinearIndicatorTrackGapSize
    LinearWavyProgressIndicator(
        progress = progress,
        color = Color.White,
        trackColor = TRACK_COLOR,
        amplitude = { if (wavy) 1f else 0f },
        trackStroke = trackStroke,
        gapSize = gap,
        modifier = modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                if (buffered == null) return@drawWithContent
                val cap = trackStroke.width / 2f
                val width = size.width
                val head = progress().coerceIn(0f, 1f) * width
                // Kde začíná dráha za přehranou částí — stejně jako v indikátoru.
                val trackStart = if (head < cap) cap else head.coerceIn(cap, width - cap) + min(head - cap, gap.toPx()) + cap * 2
                val start = max(buffered.start * width, trackStart).coerceAtLeast(cap)
                val end = min(buffered.endInclusive * width, width - cap)
                if (end > start) {
                    drawLine(
                        color = BUFFERED_COLOR,
                        start = Offset(start, size.height / 2f),
                        end = Offset(end, size.height / 2f),
                        strokeWidth = trackStroke.width,
                        cap = StrokeCap.Round,
                    )
                }
            },
    )
}

/**
 * Jaká část videa je stažená, jako podíl délky souboru; `null` = nic. Bajty nejsou čas —
 * u souboru s indexem na konci je posledních pár procent index, ne závěr videa — ale pro
 * odhad „kam až to pojede bez čekání" to stačí a jinak to přehrávače nedělají.
 *
 * Bere se SOUVISLÝ úsek, od kterého TDLib zrovna stahuje ([FileState.Downloading.downloaded]),
 * ne celkový podíl: po skoku dopředu je na disku i začátek, ale přehrávač ho teď nepotřebuje.
 */
internal fun bufferedSpan(file: FileState): ClosedFloatingPointRange<Float>? = when (file) {
    is FileState.Ready -> 0f..1f
    is FileState.Downloading -> {
        val range = file.downloaded
        if (file.totalSize <= 0 || range.isEmpty()) {
            null
        } else {
            val total = file.totalSize.toFloat()
            (range.first / total).coerceIn(0f, 1f)..((range.last + 1) / total).coerceIn(0f, 1f)
        }
    }
    else -> null
}

/** Nestažená část dráhy. */
private val TRACK_COLOR = Color.White.copy(alpha = 0.25f)

/** Stažený úsek; přes [TRACK_COLOR] dá zhruba polovinu bílé. */
private val BUFFERED_COLOR = Color.White.copy(alpha = 0.35f)
