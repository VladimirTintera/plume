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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import eu.tintera.plume.core.ui.MorphShape
import eu.tintera.plume.core.ui.ShapeMorphs
import eu.tintera.plume.core.ui.animateMorph
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.blur.HazeBlurStyle
import eu.tintera.plume.core.audio.VoicePlayer
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.features.conversation.items.SpoilerOverlay
import eu.tintera.plume.features.conversation.items.spoilerBlur
import eu.tintera.plume.core.ui.photoKey
import eu.tintera.plume.core.ui.sharedNavElement
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.core.video.VideoSurface
import eu.tintera.plume.core.video.StreamingSource
import eu.tintera.plume.core.video.videoStreamingSupported
import kotlinx.coroutines.flow.MutableStateFlow
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
 * - **V galerii je video jen náhled s tlačítkem přehrání.** Hraje se až na celé obrazovce:
 *   ťuknutí (kamkoli na stránku, nebo na tlačítko) přepne na celou obrazovku a tam se teprve
 *   začne stahovat a přehrávat. Galerie je přehled k listování; video, které se rozehraje samo
 *   pod prstem, do ní nepatří. Návrat do galerie video pozastaví a ukáže zase náhled.
 * - **Video se stahuje celé, teprve pak hraje.** TDLib soubor stahuje po částech a přehrávač by
 *   z rozestavěného souboru (index často na konci) nepřečetl nic. Do té doby náhled a průběh.
 *   Stahování začne, až je stránka vybraná na celé obrazovce — soused složený dopředu
 *   stahovat nemá.
 * - **Hraje jen vybraná stránka**; odlistování pozastaví, návrat pokračuje.
 * - **Ovládání se schová po [CONTROLS_HIDE_MS]** během přehrávání. Ťuknutí na celé obrazovce
 *   ho napřed vrátí, a teprve ťuknutí s ovládáním na očích vede zpátky do galerie. Jinak by
 *   se k posuvníku u hrajícího videa nedalo dostat.
 * - **Tah dolů/nahoru zavírá** jako u fotky; vodorovný tah nechává pageru.
 * - **Karta jako u fotky** ([MediaPageLayout]): video se zaoblením, posuvník na jejím spodku,
 *   popisek pod ní.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun VideoPage(
    video: MessageMedia.Video,
    messageId: Long,
    caption: String,
    isCurrent: Boolean,
    immersive: Boolean,
    immersion: () -> Float,
    reservedTop: Dp,
    reservedBottom: Dp,
    dismissDrag: Animatable<Float, *>,
    onDismiss: () -> Unit,
    /** Přepne galerii a celou obrazovku. */
    onTap: () -> Unit,
    /** Přepne na celou obrazovku (tlačítko přehrání v galerii). */
    onEngage: () -> Unit,
    hasSpoiler: Boolean = false,
    revealed: Boolean = true,
    onReveal: () -> Unit = {},
    spoilerStyle: HazeBlurStyle = HazeBlurStyle,
    modifier: Modifier = Modifier,
    captionAlpha: () -> Float = { 1f },
    cardBias: () -> Float = { 0f },
) {
    val scope = rememberCoroutineScope()
    val repository = koinInject<FileRepository>()
    val voicePlayer = koinInject<VoicePlayer>()

    // Hraje (a stahuje se) jen vybraná stránka na celé obrazovce.
    val active = isCurrent && immersive
    val currentTap by rememberUpdatedState(onTap)
    val currentImmersive by rememberUpdatedState(immersive)

    // Jednou spuštěné video stahuje dál, i když se odlistuje — rozstahované se nezahazuje.
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(active) { if (active) started = true }
    val file by produceState<FileState>(FileState.Pending, video.fileId, started) {
        if (started) repository.file(video.fileId).collect { value = it }
    }
    val path = (file as? FileState.Ready)?.path

    /*
     * Streamování: přehrát rozestavěný soubor, místo aby se čekalo na celý.
     *
     * Jde to, jen když má video index na začátku (`supportsStreaming` od serveru) a když to
     * platforma umí — na iOS zatím ne. Souvisle stažených bajtů (`downloadedPrefixSize`) se
     * čte z vlákna přehrávače, proto `MutableStateFlow`, ne Compose stav.
     */
    val streamable = videoStreamingSupported && video.supportsStreaming
    val downloading = file as? FileState.Downloading
    // Stažený úsek se čte z vlákna přehrávače, proto `MutableStateFlow`, ne Compose stav.
    val downloadedRange = remember(video.fileId) { MutableStateFlow(LongRange.EMPTY) }
    downloadedRange.value = downloading?.downloaded ?: LongRange.EMPTY
    val streamPath = downloading?.path?.takeIf { streamable }
    val streamTotal = downloading?.totalSize ?: 0L

    val player = rememberVideoPlayer()
    var loaded by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(video.durationSeconds * 1000L) }
    var playing by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    // Během tažení posuvníku se ukazuje místo pod prstem, ne kde přehrávač zrovna je.
    var seekingFraction by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(path, streamPath) {
        if (loaded) return@LaunchedEffect
        when {
            // Hotový soubor má přednost: jednodušší cesta a nic se na něj nečeká.
            path != null -> {
                player.load(path)
                loaded = true
            }
            // Streamovat se dá, teprve až je na disku začátek souboru s indexem.
            streamPath != null && streamTotal > 0 -> {
                player.loadStreaming(
                    StreamingSource(
                        path = streamPath,
                        totalSize = streamTotal,
                        downloaded = { downloadedRange.value },
                        // Přehrávač si řekne o `moov` atom na konci souboru; bez přesunu
                        // stahování by na něj čekal marně.
                        requestFrom = { offset -> scope.launch { repository.downloadFrom(video.fileId, offset) } },
                    ),
                )
                loaded = true
            }
        }
    }

    LaunchedEffect(loaded, active) {
        if (!loaded) return@LaunchedEffect
        if (active) {
            // Hrající hlasovka by se s videem prala o reproduktor.
            voicePlayer.stop()
            player.play()
        } else {
            player.pause()
        }
    }

    // Přehrávač stav nehlásí; čte se po kouskách, jen dokud je stránka vybraná.
    LaunchedEffect(loaded, active) {
        if (!loaded || !active) return@LaunchedEffect
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
    // Na celou obrazovku se přichází s ovládáním na očích; během přehrávání se samo schová.
    LaunchedEffect(immersive) { if (immersive) controlsVisible = true }

    MediaPageLayout(
        aspectRatio = aspectRatioOf(video.width, video.height),
        caption = caption,
        reservedTop = reservedTop,
        reservedBottom = reservedBottom,
        immersion = immersion,
        captionAlpha = captionAlpha,
        cardBias = cardBias,
        modifier = modifier
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
            .pointerInput(hasSpoiler, revealed) {
                detectTapGestures(onTap = {
                    if (hasSpoiler && !revealed) {
                        onReveal()
                    } else if (currentImmersive && !controlsVisible) {
                        controlsVisible = true
                    } else {
                        currentTap()
                    }
                })
            }
            /*
             * Posun přes `offset`, ne `graphicsLayer`: nativní view s videem (UIKit na iOS)
             * transformace vrstvy nedostane, jen pozici z layoutu. Čte se ve fázi umístění, takže
             * nic nerekomponuje.
             */
            .offset { IntOffset(0, dismissDrag.value.roundToInt()) },
    ) {
        // Zaoblení se ořezává v layoutu, ne ve vrstvě: nativní view s videem (UIKit) ořez
        // vrstvy nedostane. Na celé obrazovce je nulové, takže přechod se kreslí jen nad náhledem.
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(CARD_CORNER * (1f - immersion())))) {
            // Náhled drží místo, dokud se video nestáhne; se sdíleným prvkem z bubliny.
            video.thumbnailFileId?.let { thumbnail ->
                RemoteImage(
                    fileId = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    background = Color.Transparent,
                    modifier = Modifier
                        .fillMaxSize()
                        .sharedNavElement(photoKey(messageId))
                        .spoilerBlur(hasSpoiler, revealed, spoilerStyle),
                )
            }
            // V galerii jen náhled; povrch se složí až na celé obrazovce.
            if (loaded && immersive) {
                VideoSurface(player, Modifier.fillMaxSize())
            }

            // Průběh i chyba patří celé obrazovce; v galerii je na jejich místě tlačítko přehrání.
            if (immersive) when (val state = file) {
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

            // Tlačítko přehrání v galerii: jen přepne na celou obrazovku, video se spustí tam.
            AnimatedVisibility(
                visible = !immersive && (!hasSpoiler || revealed),
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
                modifier = Modifier.align(Alignment.Center),
            ) {
                FilledIconButton(
                    onClick = onEngage,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                        contentColor = Color.White,
                    ),
                    // Klid / připraveno = sušenka, jako pozastavené video na celé obrazovce.
                    shape = MorphShape(ShapeMorphs.Rest, 1f, rotation = ShapeMorphs.REST_ROTATION),
                    modifier = Modifier.size(IconButtonDefaults.largeContainerSize()),
                ) {
                    Icon(
                        painterResource(Res.drawable.play_arrow_24dp),
                        contentDescription = stringResource(Res.string.media_play),
                        modifier = Modifier.size(IconButtonDefaults.largeIconSize),
                    )
                }
            }

            if (hasSpoiler) {
                SpoilerOverlay(
                    visible = !revealed,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            AnimatedVisibility(
                visible = immersive && loaded && controlsVisible,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
                modifier = Modifier.align(Alignment.Center),
            ) {
                // Jako u hlasovky: pozastaveno / dohráno = sušenka, hraje = kruh.
                val rest = animateMorph(!playing, "video-button-shape")
                FilledIconButton(
                    onClick = {
                        if (playing) player.pause() else player.play()
                        playing = !playing
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                        contentColor = Color.White,
                    ),
                    shape = MorphShape(ShapeMorphs.Rest, rest, rotation = ShapeMorphs.REST_ROTATION),
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

            // Posuvník leží na spodku karty s přechodem do tmava — na světlém záběru by bílý
            // posuvník jinak zmizel.
            AnimatedVisibility(
                visible = immersive && loaded && controlsVisible,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
                            ),
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val fraction = seekingFraction
                        ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                    Text(
                        text = formatDuration(((fraction * durationMs) / 1000).toInt()),
                        style = MaterialTheme.typography.labelMedium.tabularNumbers(),
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
                        style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                        color = Color.White,
                    )
                }
            }
        }
    }
}

private val CircleCornerShape = androidx.compose.foundation.shape.CircleShape

private const val POSITION_TICK_MS = 200L
private const val CONTROLS_HIDE_MS = 3000L
