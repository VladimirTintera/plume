package eu.tintera.plume.features.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.audio.VoicePlayer
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.photoKey
import eu.tintera.plume.core.ui.sharedNavElement
import eu.tintera.plume.core.video.VideoSurface
import eu.tintera.plume.core.video.rememberVideoPlayer
import eu.tintera.plume.features.conversation.formatDuration
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.media_pause
import eu.tintera.plume.resources.media_play
import eu.tintera.plume.resources.media_replay
import eu.tintera.plume.resources.media_video_download_failed
import eu.tintera.plume.resources.pause_24dp
import eu.tintera.plume.resources.play_arrow_24dp
import eu.tintera.plume.resources.replay_24dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Stránka prohlížeče s videem.
 *
 * - **Video se stahuje celé, teprve pak hraje.** TDLib soubor stahuje po částech a přehrávač by
 *   z rozestavěného souboru (index často na konci) nepřečetl nic. Do té doby náhled a průběh.
 *   Stahování začne, až je stránka vybraná — soused složený dopředu stahovat nemá.
 * - **Hraje jen vybraná stránka**; odlistování pozastaví, návrat pokračuje.
 * - **Ovládání se schová po [CONTROLS_HIDE_MS]** během přehrávání, ťuknutí ho vrátí.
 * - **Tah dolů/nahoru zavírá** jako u fotky; vodorovný tah nechává pageru.
 * - Popisek se kreslí tady pod ovládáním, ne v pageru — jinak by se s ním překrývaly.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun VideoPage(
    video: MessageMedia.Video,
    messageId: Long,
    caption: String,
    isCurrent: Boolean,
    dismissDrag: Animatable<Float, *>,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val repository = koinInject<FileRepository>()
    val voicePlayer = koinInject<VoicePlayer>()

    // Jednou vybraná stránka stahuje dál, i když se odlistuje — rozstahované video se nezahazuje.
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(isCurrent) { if (isCurrent) started = true }
    val file by produceState<FileState>(FileState.Pending, video.fileId, started) {
        if (started) repository.file(video.fileId).collect { value = it }
    }
    val path = (file as? FileState.Ready)?.path

    val player = rememberVideoPlayer()
    var loaded by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(video.durationSeconds * 1000L) }
    var playing by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    // Během tažení posuvníku se ukazuje místo pod prstem, ne kde přehrávač zrovna je.
    var seekingFraction by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(path) {
        if (path != null && !loaded) {
            player.load(path)
            loaded = true
        }
    }

    LaunchedEffect(loaded, isCurrent) {
        if (!loaded) return@LaunchedEffect
        if (isCurrent) {
            // Hrající hlasovka by se s videem prala o reproduktor.
            voicePlayer.stop()
            player.play()
        } else {
            player.pause()
        }
    }

    // Přehrávač stav nehlásí; čte se po kouskách, jen dokud je stránka vybraná.
    LaunchedEffect(loaded, isCurrent) {
        if (!loaded || !isCurrent) return@LaunchedEffect
        while (true) {
            positionMs = player.positionMs
            player.durationMs.takeIf { it > 0 }?.let { durationMs = it }
            playing = player.isPlaying
            ended = player.isEnded
            delay(POSITION_TICK_MS)
        }
    }

    LaunchedEffect(controlsVisible, playing) {
        if (controlsVisible && playing) {
            delay(CONTROLS_HIDE_MS)
            controlsVisible = false
        }
    }

    // Po dohrání se ovládání vrátí, ať je vidět tlačítko na opakování.
    LaunchedEffect(ended) { if (ended) controlsVisible = true }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Jen svislý tah (zavírání); vodorovný se nekonzumuje a patří pageru.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var vertical: Boolean? = null
                    var travel = Offset.Zero
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        val delta = change.position - change.previousPosition
                        if (vertical == null) {
                            travel += delta
                            if (travel.getDistance() > viewConfiguration.touchSlop) {
                                vertical = abs(travel.y) > abs(travel.x) && event.changes.size == 1
                            }
                        }
                        if (vertical == true) {
                            scope.launch { dismissDrag.snapTo(dismissDrag.value + delta.y) }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })

                    if (vertical == true) {
                        if (abs(dismissDrag.value) > size.height * DISMISS_THRESHOLD) {
                            onDismiss()
                        } else {
                            scope.launch { dismissDrag.animateTo(0f) }
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { controlsVisible = !controlsVisible })
            },
    ) {
        /*
         * Posun přes `offset`, ne `graphicsLayer`: nativní view s videem (UIKit na iOS) transformace
         * vrstvy nedostane, jen pozici z layoutu. Čte se ve fázi umístění, takže nic nerekomponuje.
         */
        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(0, dismissDrag.value.roundToInt()) },
        ) {
            // Náhled drží místo, dokud se video nestáhne; se sdíleným prvkem z bubliny.
            video.thumbnailFileId?.let { thumbnail ->
                RemoteImage(
                    fileId = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    background = Color.Transparent,
                    modifier = Modifier.fillMaxSize().sharedNavElement(photoKey(messageId)),
                )
            }
            if (loaded) {
                VideoSurface(player, Modifier.fillMaxSize())
            }
        }

        when (val state = file) {
            is FileState.Downloading, FileState.Pending -> if (started) {
                val progress = (state as? FileState.Downloading)?.progress
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f), CircleCornerShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (progress == null || progress <= 0f) {
                        CircularWavyProgressIndicator(color = Color.White, modifier = Modifier.size(48.dp))
                    } else {
                        CircularWavyProgressIndicator(progress = { progress }, color = Color.White, modifier = Modifier.size(48.dp))
                    }
                }
            }

            is FileState.Failed -> Text(
                text = stringResource(Res.string.media_video_download_failed),
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )

            is FileState.Ready -> Unit
        }

        AnimatedVisibility(
            visible = loaded && controlsVisible,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
            modifier = Modifier.align(Alignment.Center),
        ) {
            FilledIconButton(
                onClick = {
                    if (playing) player.pause() else player.play()
                    playing = !playing
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                    contentColor = Color.White,
                ),
                modifier = Modifier.size(IconButtonDefaults.largeContainerSize()),
            ) {
                Icon(
                    painterResource(
                        when {
                            ended -> Res.drawable.replay_24dp
                            playing -> Res.drawable.pause_24dp
                            else -> Res.drawable.play_arrow_24dp
                        },
                    ),
                    contentDescription = when {
                        ended -> stringResource(Res.string.media_replay)
                        playing -> stringResource(Res.string.media_pause)
                        else -> stringResource(Res.string.media_play)
                    },
                    modifier = Modifier.size(IconButtonDefaults.largeIconSize),
                )
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = if (controlsVisible || caption.isNotBlank()) 0.55f else 0f))
                .navigationBarsPadding(),
        ) {
            AnimatedVisibility(visible = loaded && controlsVisible) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val fraction = seekingFraction
                        ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                    Text(
                        text = formatDuration(((fraction * durationMs) / 1000).toInt()),
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = Color.White,
                    )
                    Slider(
                        value = fraction,
                        onValueChange = {
                            seekingFraction = it
                            controlsVisible = true
                        },
                        onValueChangeFinished = {
                            seekingFraction?.let { player.seekTo((it * durationMs).toLong()) }
                            seekingFraction = null
                            ended = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.35f),
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatDuration((durationMs / 1000).toInt()),
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = Color.White,
                    )
                }
            }
            if (caption.isNotBlank()) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
        }
    }
}

private val CircleCornerShape = androidx.compose.foundation.shape.CircleShape

private const val POSITION_TICK_MS = 200L
private const val CONTROLS_HIDE_MS = 3000L

/** Jako u fotky: jak daleko musí tah zajít, aby se prohlížeč zavřel. */
private const val DISMISS_THRESHOLD = 0.18f
