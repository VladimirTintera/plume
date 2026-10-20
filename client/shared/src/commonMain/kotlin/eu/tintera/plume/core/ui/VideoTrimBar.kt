package eu.tintera.plume.core.ui

import eu.tintera.plume.resources.video_trim_story_cover_earlier
import eu.tintera.plume.resources.video_trim_story_cover_later
import eu.tintera.plume.resources.video_trim_summary_cover
import org.jetbrains.compose.resources.StringResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.video_trim_cover_earlier
import eu.tintera.plume.resources.video_trim_cover_later
import eu.tintera.plume.resources.video_trim_move_earlier
import eu.tintera.plume.resources.video_trim_move_later
import eu.tintera.plume.resources.video_trim_strip
import eu.tintera.plume.resources.video_trim_summary
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/**
 * Pás snímků videa pod ořezem: bílý rámeček je úsek, který se stane animovanou profilovkou
 * (posouvá se tahem), svislá čárka snímek pro statickou fotku (tahem za ni, nebo ťuknutím
 * kamkoli do pásu). Co je mimo úsek, je ztmavené.
 *
 * **Tah rozhoduje podle toho, kde začal**: u čárky (nebo když úsek posouvat nejde, protože je
 * video krátké) se vybírá snímek, jinde se posouvá úsek. Dvě úchyty vedle sebe by se na
 * telefonu trefovaly špatně.
 */
@Composable
internal fun VideoTrimBar(control: VideoCropControl, modifier: Modifier = Modifier, labels: VideoTrimLabels = VideoTrimLabels.ProfilePhoto) {
    val trim = control.trim
    val haptics = LocalHapticFeedback.current
    val summary = stringResource(
        labels.summary,
        clock(trim.startMs),
        clock(trim.endMs),
        clock(trim.coverMs),
    )
    val stripLabel = stringResource(Res.string.video_trim_strip)
    val moveLater = stringResource(Res.string.video_trim_move_later)
    val moveEarlier = stringResource(Res.string.video_trim_move_earlier)
    val coverLater = stringResource(labels.coverLater)
    val coverEarlier = stringResource(labels.coverEarlier)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(STRIP_HEIGHT)
                // Čtečka tahat neumí — úsek i snímek posune po sekundách akcemi.
                .clearAndSetSemantics {
                    contentDescription = stripLabel
                    stateDescription = summary
                    customActions = buildList {
                        if (control.trim.canMove) {
                            add(CustomAccessibilityAction(moveLater) { control.moveTo(control.trim.startMs + STEP_MS); true })
                            add(CustomAccessibilityAction(moveEarlier) { control.moveTo(control.trim.startMs - STEP_MS); true })
                        }
                        add(CustomAccessibilityAction(coverLater) { control.chooseCover(control.trim.coverMs + STEP_MS); true })
                        add(CustomAccessibilityAction(coverEarlier) { control.chooseCover(control.trim.coverMs - STEP_MS); true })
                    }
                },
        ) {
            val width = constraints.maxWidth.toFloat()
            val duration = trim.durationMs.coerceAtLeast(1)
            fun xOf(ms: Long) = ms * width / duration
            fun msOf(x: Float) = (x / width * duration).toLong()

            Row(Modifier.fillMaxSize().clip(StripShape).background(Color.White.copy(alpha = 0.12f))) {
                repeat(STRIP_FRAMES) { index ->
                    // Střed dlaždice: první a poslední snímek videa bývají černé nebo rozmazané.
                    StripFrame(control, atMs = duration * (2 * index + 1) / (2 * STRIP_FRAMES), Modifier.weight(1f))
                }
            }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(control, width) {
                        detectTapGestures { position ->
                            val before = control.trim.coverMs / 1000
                            control.chooseCover(msOf(position.x))
                            if (control.trim.coverMs / 1000 != before) haptics.tick()
                        }
                    }
                    .pointerInput(control, width) {
                        val grab = COVER_GRAB.toPx()
                        var choosingCover = false
                        var anchor = 0f
                        var anchorMs = 0L
                        detectHorizontalDragGestures(
                            onDragStart = { position ->
                                val trimNow = control.trim
                                choosingCover = !trimNow.canMove || abs(position.x - xOf(trimNow.coverMs)) < grab
                                anchor = position.x
                                anchorMs = trimNow.startMs
                            },
                        ) { change, _ ->
                            change.consume()
                            // Tiká po celých sekundách, jako posuvník videa — po pixelech by
                            // to bylo souvislé bzučení.
                            if (choosingCover) {
                                val before = control.trim.coverMs / 1000
                                control.chooseCover(msOf(change.position.x))
                                if (control.trim.coverMs / 1000 != before) haptics.tick()
                            } else {
                                val before = control.trim.startMs / 1000
                                control.moveTo(anchorMs + msOf(change.position.x - anchor))
                                if (control.trim.startMs / 1000 != before) haptics.tick()
                            }
                        }
                    },
            ) {
                val left = xOf(trim.startMs)
                val right = xOf(trim.endMs)
                val veil = Color.Black.copy(alpha = OUTSIDE_ALPHA)
                if (left > 0f) drawRect(veil, size = Size(left, size.height))
                if (right < size.width) drawRect(veil, topLeft = Offset(right, 0f), size = Size(size.width - right, size.height))

                val frame = FRAME_STROKE.toPx()
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(left + frame / 2, frame / 2),
                    size = Size((right - left - frame).coerceAtLeast(0f), size.height - frame),
                    cornerRadius = CornerRadius(STRIP_CORNER.toPx()),
                    style = Stroke(frame),
                )

                // Čárka snímku: bílá s tmavým lemem, ať je vidět i nad světlým záběrem.
                val x = xOf(trim.coverMs).coerceIn(frame, size.width - frame)
                val marker = COVER_WIDTH.toPx()
                val outline = 1.dp.toPx()
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.5f),
                    topLeft = Offset(x - marker / 2 - outline, frame),
                    size = Size(marker + 2 * outline, size.height - 2 * frame),
                    cornerRadius = CornerRadius(marker),
                )
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(x - marker / 2, frame + outline),
                    size = Size(marker, size.height - 2 * frame - 2 * outline),
                    cornerRadius = CornerRadius(marker / 2),
                )
            }
        }
        Text(
            text = summary,
            style = MaterialTheme.typography.labelMedium.tabularNumbers(),
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

@Composable
private fun StripFrame(control: VideoCropControl, atMs: Long, modifier: Modifier) {
    val path by produceState<String?>(null, control, atMs) {
        value = runCatching { control.frame(atMs, PROFILE_VIDEO_STRIP_EDGE) }.getOrNull()
    }
    Box(modifier.fillMaxHeight()) {
        path?.let {
            AsyncImage(
                model = localImage(it),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Jak se jmenuje vybraný snímek: u profilovky je to statická FOTKA, u příběhu OBÁLKA. Pás je
 * jinak tentýž.
 */
enum class VideoTrimLabels(val summary: StringResource, val coverLater: StringResource, val coverEarlier: StringResource) {
    ProfilePhoto(Res.string.video_trim_summary, Res.string.video_trim_cover_later, Res.string.video_trim_cover_earlier),
    StoryCover(Res.string.video_trim_summary_cover, Res.string.video_trim_story_cover_later, Res.string.video_trim_story_cover_earlier),
}

/** „0:12" — úsek má nejvýš pár minut a víc než sekundy z pásu nikdo netrefí. */
private fun clock(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

private const val STRIP_FRAMES = 8
private const val STEP_MS = 1_000L
private const val OUTSIDE_ALPHA = 0.6f
private val STRIP_HEIGHT = 56.dp
private val STRIP_CORNER = 8.dp
private val StripShape = RoundedCornerShape(STRIP_CORNER)
private val FRAME_STROKE = 3.dp
private val COVER_WIDTH = 4.dp
private val COVER_GRAB = 24.dp
