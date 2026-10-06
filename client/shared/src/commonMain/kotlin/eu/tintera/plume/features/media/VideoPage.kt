package eu.tintera.plume.features.media

import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.mutableIntStateOf
import eu.tintera.plume.core.ui.tick
import eu.tintera.plume.resources.media_unmute
import eu.tintera.plume.resources.media_mute
import eu.tintera.plume.resources.volume_up_24dp
import eu.tintera.plume.resources.volume_off_24dp
import androidx.compose.material3.IconButton
import androidx.compose.runtime.collectAsState
import eu.tintera.plume.features.conversation.SpeedChip
import eu.tintera.plume.core.settings.AppSettings
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
import androidx.compose.foundation.layout.BoxScope
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
import eu.tintera.plume.core.ui.MEDIA_CARD_CORNER
import eu.tintera.plume.core.ui.MediaPageLayout
import eu.tintera.plume.core.ui.aspectRatioOf
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
import eu.tintera.plume.core.video.rememberPictureInPicture
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import eu.tintera.plume.resources.picture_in_picture_alt_24dp
import eu.tintera.plume.resources.media_picture_in_picture
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
 * - **Stahování začne, až je stránka vybraná na celé obrazovce** — soused složený dopředu
 *   stahovat nemá. Kde to jde, video se streamuje z rozestavěného souboru (viz níž).
 * - **Velký průběh uprostřed jen do rozběhnutí videa**, pak ho vystřídá ovládání a stav
 *   stahování se stáhne do kroužku v rohu ([VideoDownloadIndicator]); ťuknutím se roztáhne do
 *   čáry přes horní okraj, kde je vidět stažený úsek.
 * - **Hraje jen vybraná stránka**; odlistování pozastaví, návrat pokračuje.
 * - **Ovládání se schová po [CONTROLS_HIDE_MS]** během přehrávání. Ťuknutí na celé obrazovce
 *   ho napřed vrátí, a teprve ťuknutí s ovládáním na očích vede zpátky do galerie. Jinak by
 *   se k posuvníku u hrajícího videa nedalo dostat.
 * - **Tah dolů/nahoru zavírá** jako u fotky; vodorovný tah nechává pageru.
 * - **Karta jako u fotky** ([MediaPageLayout]): video se zaoblením, posuvník na jejím spodku,
 *   popisek pod ní.
 */
@Composable
internal fun VideoPage(
    video: MessageMedia.Video,
    messageId: Long,
    /** Čas v médiu: po načtení skočit sem (ms); nula = od začátku. */
    startMs: Long = 0L,
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
    /**
     * Smí se přehrávat rozestavěný soubor. U mizejícího média ne: odpočet začíná až se staženým
     * souborem a video nesmí hrát dřív, než běží.
     */
    streaming: Boolean = true,
    hasSpoiler: Boolean = false,
    revealed: Boolean = true,
    onReveal: () -> Unit = {},
    spoilerStyle: HazeBlurStyle = HazeBlurStyle,
    modifier: Modifier = Modifier,
    captionAlpha: () -> Float = { 1f },
    cardBias: () -> Float = { 0f },
    /** Ztlumení; drží ho prohlížeč, aby platilo pro všechna videa, dokud je otevřený. */
    muted: Boolean = false,
    /** Tlačítko ztlumení; `null` = bez něj. */
    onToggleMute: (() -> Unit)? = null,
    /** Smí video do obrazu v obraze (mizející médium ne — mělo by zůstat v aplikaci). */
    pictureInPicture: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val repository = koinInject<FileRepository>()
    VideoPage(
        startMs = startMs,
        muted = muted,
        onToggleMute = onToggleMute,
        pictureInPicture = pictureInPicture,
        aspectRatio = aspectRatioOf(video.width, video.height),
        durationSeconds = video.durationSeconds,
        caption = caption,
        isCurrent = isCurrent,
        immersive = immersive,
        immersion = immersion,
        reservedTop = reservedTop,
        reservedBottom = reservedBottom,
        dismissDrag = dismissDrag,
        onDismiss = onDismiss,
        onTap = onTap,
        onEngage = onEngage,
        source = { started ->
            produceState<FileState>(FileState.Pending, video.fileId, started) {
                if (started) repository.file(video.fileId).collect { value = it }
            }.value
        },
        /*
         * Streamování: přehrát rozestavěný soubor, místo aby se čekalo na celý. Jde to, jen když
         * má video index na začátku (`supportsStreaming` od serveru) a když to platforma umí —
         * na iOS zatím ne.
         */
        streamable = streaming && videoStreamingSupported && video.supportsStreaming,
        // Přehrávač si řekne o `moov` atom na konci souboru; bez přesunu stahování by na něj
        // čekal marně.
        requestFrom = { offset -> scope.launch { repository.downloadFrom(video.fileId, offset) } },
        hasSpoiler = hasSpoiler,
        sensitive = video.isSensitive,
        revealed = revealed,
        onReveal = onReveal,
        modifier = modifier,
        captionAlpha = captionAlpha,
        cardBias = cardBias,
        thumbnail = {
            // Náhled drží místo, dokud se video nestáhne; se sdíleným prvkem z bubliny.
            video.thumbnailFileId?.let { thumbnail ->
                RemoteImage(
                    fileId = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    background = Color.Transparent,
                    modifier = Modifier
                        .fillMaxSize()
                        // Týž tvar jako karta kolem (viz níž); ta se během letu neuplatní.
                        .sharedNavElement(
                            photoKey(messageId),
                            shape = RoundedCornerShape(MEDIA_CARD_CORNER * (1f - immersion())),
                            inTwoPane = true,
                        )
                        .spoilerBlur(hasSpoiler, revealed, spoilerStyle),
                )
            }
        },
    )
}

/**
 * Přehrávání videa na stránce carouselu — co přehrát, dodá volající: video ze zprávy (stahuje
 * se z TDLib, i streamováním) i sdílený soubor, který už na disku je ([file]). Dřív bylo
 * přehrávání spojené se zprávou a sdílení video pustit neumělo.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun VideoPage(
    aspectRatio: Float,
    durationSeconds: Int,
    caption: String,
    isCurrent: Boolean,
    immersive: Boolean,
    immersion: () -> Float,
    reservedTop: Dp,
    reservedBottom: Dp,
    dismissDrag: Animatable<Float, *>,
    /** Svislý tah přes práh; `null` = svislý tah nic nedělá (viz [ZoomableMediaPage]). */
    onDismiss: (() -> Unit)?,
    onTap: () -> Unit,
    onEngage: () -> Unit,
    /** Stav souboru s videem; `started` říká, že se má začít stahovat. */
    source: @Composable (started: Boolean) -> FileState,
    /** Smí se přehrávat rozestavěný soubor ([FileState.Downloading]). */
    streamable: Boolean,
    /** Stahovat od daného bajtu — přehrávač chce index na konci souboru. */
    requestFrom: (offset: Long) -> Unit,
    /** Náhled přes celou kartu, dokud se video nepřehrává. */
    thumbnail: @Composable () -> Unit,
    hasSpoiler: Boolean = false,
    /** Skryté kvůli citlivému obsahu — mění jen štítek (viz `SpoilerOverlay`). */
    sensitive: Boolean = false,
    revealed: Boolean = true,
    onReveal: () -> Unit = {},
    modifier: Modifier = Modifier,
    captionAlpha: () -> Float = { 1f },
    cardBias: () -> Float = { 0f },
    /** Ovládání na kartě navíc (✕ a volba režimu ve sdílení); v galerii, na celé obrazovce ne. */
    overlay: @Composable BoxScope.() -> Unit = {},
    /** Po načtení skočit sem (ms) — čas v médiu; nula = od začátku. */
    startMs: Long = 0L,
    /**
     * Ztlumení za běhu. Drží ho volající (prohlížeč), ne stránka: ztlumené video by jinak po
     * odlistování na další zase hrálo nahlas.
     */
    muted: Boolean = false,
    /** Tlačítko ztlumení v liště; `null` = bez něj (náhledy při sdílení a nad polem). */
    onToggleMute: (() -> Unit)? = null,
    /**
     * Obraz v obraze: tlačítko v liště a samočinný přechod při odchodu z aplikace, dokud video
     * hraje. Jen v prohlížeči médií — náhled při sdílení a nad polem je chvilková kontrola.
     */
    pictureInPicture: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val voicePlayer = koinInject<VoicePlayer>()
    val settings = koinInject<AppSettings>()
    // Rychlost je volba přes celou aplikaci (jako u hlasovek), jen zvlášť pro video.
    val speed by settings.videoSpeed.collectAsState()
    val currentDismiss by rememberUpdatedState(onDismiss)

    // Hraje (a stahuje se) jen vybraná stránka na celé obrazovce.
    val active = isCurrent && immersive
    val currentTap by rememberUpdatedState(onTap)
    val currentImmersive by rememberUpdatedState(immersive)

    // Jednou spuštěné video stahuje dál, i když se odlistuje — rozstahované se nezahazuje.
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(active) { if (active) started = true }
    val file = source(started)
    val path = (file as? FileState.Ready)?.path

    val downloading = file as? FileState.Downloading
    // Souvisle stažený úsek se čte z vlákna přehrávače, proto `MutableStateFlow`, ne Compose stav.
    val downloadedRange = remember { MutableStateFlow(LongRange.EMPTY) }
    downloadedRange.value = downloading?.downloaded ?: LongRange.EMPTY
    val streamPath = downloading?.path?.takeIf { streamable }
    val streamTotal = downloading?.totalSize ?: 0L

    val player = rememberVideoPlayer()
    var loaded by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(durationSeconds * 1000L) }
    var playing by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    // Video se opravdu rozběhlo (ne jen načetlo): do té doby točí velký průběh uprostřed.
    var hasPlayed by remember { mutableStateOf(false) }
    // Stav stahování rozvinutý do čáry přes spodní hranu (jinak kroužek v rohu).
    var downloadExpanded by remember { mutableStateOf(false) }
    // Během tažení posuvníku se ukazuje místo pod prstem, ne kde přehrávač zrovna je.
    var seekingFraction by remember { mutableStateOf<Float?>(null) }

    // `load` hlasitost i rychlost přenastaví, takže se obojí zapisuje až PO načtení (klíč `loaded`).
    LaunchedEffect(loaded, speed) { if (loaded) player.setSpeed(speed.rate) }
    LaunchedEffect(loaded, muted) { if (loaded) player.setMuted(muted) }

    LaunchedEffect(path, streamPath) {
        if (loaded) return@LaunchedEffect
        when {
            // Hotový soubor má přednost: jednodušší cesta a nic se na něj nečeká.
            path != null -> {
                player.load(path)
                if (startMs > 0) player.seekTo(startMs)
                loaded = true
            }
            // Streamovat se dá, teprve až je na disku začátek souboru s indexem.
            streamPath != null && streamTotal > 0 -> {
                player.loadStreaming(
                    StreamingSource(
                        path = streamPath,
                        totalSize = streamTotal,
                        downloaded = { downloadedRange.value },
                        requestFrom = requestFrom,
                    ),
                )
                if (startMs > 0) player.seekTo(startMs)
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
            if (playing) hasPlayed = true
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

    /*
     * Obraz v obraze jen pro video, které právě hraje na celé obrazovce: parametry PiP platí pro
     * celou aktivitu, takže je smí nastavovat nejvýš jedna stránka. V okénku zůstane jen obraz —
     * ovládání v něm nejde ovládat prstem a zakrylo by půlku záběru.
     */
    val pip = if (pictureInPicture && active && loaded) {
        rememberPictureInPicture(
            player = player,
            aspectRatio = aspectRatio,
            playing = playing,
            onTogglePlay = {
                if (player.isPlaying) player.pause() else player.play()
                playing = !playing
            },
        )
    } else {
        null
    }
    val inPip = pip?.active == true
    // Zavřené okénko i odchod na pozadí bez okénka video pozastaví (proces pod ním žije dál).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }

    // Stahování, které dobíhá pod už hrajícím videem.
    val downloadVisible = immersive && hasPlayed && file is FileState.Downloading && !inPip

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
                                vertical = currentDismiss != null && abs(travel.y) > abs(travel.x) && event.changes.size == 1
                            }
                        }
                        if (vertical == true) {
                            scope.launch { dismissDrag.snapTo(dismissDrag.value + delta.y) }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })

                    if (vertical == true) {
                        val dismiss = currentDismiss
                        if (dismiss != null && abs(dismissDrag.value) > size.height * DISMISS_THRESHOLD) {
                            dismiss()
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
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(MEDIA_CARD_CORNER * (1f - immersion())))) {
            thumbnail()
            // V galerii jen náhled; povrch se složí až na celé obrazovce.
            if (loaded && immersive) {
                VideoSurface(player, Modifier.fillMaxSize())
            }

            /*
             * Velký průběh uprostřed jen DO ROZBĚHNUTÍ videa. U streamovaného videa je načtené
             * hned, jenže přehrávač ještě čeká na data — procento staženého souboru by tam lhalo
             * (hraje se dávno před 100 %), proto se po načtení točí neurčitě. Jakmile video hraje,
             * stav stahování převezme nerušivý [VideoDownloadIndicator] a uprostřed je ovládání.
             */
            if (immersive && started && !hasPlayed && file !is FileState.Failed) {
                val progress = (file as? FileState.Downloading)?.progress?.takeIf { !loaded && it > 0f }
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f), CircleCornerShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (progress == null) {
                        CircularWavyProgressIndicator(color = Color.White, modifier = Modifier.size(48.dp))
                    } else {
                        CircularWavyProgressIndicator(progress = { progress }, color = Color.White, modifier = Modifier.size(48.dp))
                    }
                }
            }
            if (immersive && file is FileState.Failed) {
                Text(
                    text = stringResource(Res.string.media_video_download_failed),
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
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
                    sensitive = sensitive,
                )
            }

            AnimatedVisibility(
                visible = immersive && loaded && hasPlayed && controlsVisible && !inPip,
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
                visible = immersive && loaded && controlsVisible && !inPip,
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
                        /*
                         * Ovládání se ukazuje jen na celé obrazovce, kde karta sahá až ke spodní
                         * hraně displeje. Bez odsazení ležel posuvník v POVINNÉ zóně systémových
                         * gest (na emulátoru Pixel 9 posledních 84 px) a vodorovný tah po něm
                         * přepnul aplikace, místo aby posunul video. Přechod do tmava jde dál
                         * až ke hraně, odsadí se jen obsah.
                         */
                        .windowInsetsPadding(
                            WindowInsets.navigationBars.union(WindowInsets.mandatorySystemGestures)
                                .only(WindowInsetsSides.Bottom),
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
                    val haptics = LocalHapticFeedback.current
                    // Tahem se tiká po dílcích, ne po pixelech — jako po sloupcích waveformu hlasovky.
                    var tickedStep by remember { mutableIntStateOf(-1) }
                    Slider(
                        value = fraction,
                        onValueChange = {
                            seekingFraction = it
                            controlsVisible = true
                            val step = (it * VIDEO_SEEK_STEPS).toInt().coerceIn(0, VIDEO_SEEK_STEPS - 1)
                            if (step != tickedStep) {
                                if (tickedStep >= 0) haptics.tick()
                                tickedStep = step
                            }
                        },
                        onValueChangeFinished = {
                            seekingFraction?.let { player.seekTo((it * durationMs).toLong()) }
                            seekingFraction = null
                            ended = false
                            tickedStep = -1
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.35f),
                        ),
                        /*
                         * Dráha se vlní, dokud video hraje, a po pozastavení i během tahu se
                         * narovná — tentýž význam jako vlnka hrající hlasovky („teď zní", viz
                         * `CLAUDE.md`, „Video"). Přechod amplitudy animuje indikátor sám.
                         */
                        track = { sliderState ->
                            val wavy = playing && seekingFraction == null
                            LinearWavyProgressIndicator(
                                progress = { sliderState.value },
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.35f),
                                amplitude = { if (wavy) 1f else 0f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatDuration((durationMs / 1000).toInt()),
                        style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                        color = Color.White,
                    )
                    // Rychlost a zvuk za časem, jako u hlasovky; bílé na tmavém přechodu karty.
                    SpeedChip(
                        speed = speed,
                        accent = Color.White,
                        onAccent = Color.Black,
                        onClick = {
                            settings.setVideoSpeed(speed.next())
                            controlsVisible = true
                        },
                    )
                    if (pip?.supported == true) {
                        IconButton(
                            onClick = pip::enter,
                            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                painterResource(Res.drawable.picture_in_picture_alt_24dp),
                                contentDescription = stringResource(Res.string.media_picture_in_picture),
                            )
                        }
                    }
                    onToggleMute?.let { toggle ->
                        IconButton(
                            onClick = {
                                toggle()
                                controlsVisible = true
                            },
                            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                painterResource(if (muted) Res.drawable.volume_off_24dp else Res.drawable.volume_up_24dp),
                                contentDescription = stringResource(if (muted) Res.string.media_unmute else Res.string.media_mute),
                            )
                        }
                    }
                }
            }

            VideoDownloadIndicator(
                progress = (file as? FileState.Downloading)?.progress ?: 1f, // dostaženo: plný, než zmizí
                visible = downloadVisible,
                expanded = downloadExpanded,
                onToggle = { downloadExpanded = !downloadExpanded },
            )

            if (!inPip) overlay()
        }
    }
}

private val CircleCornerShape = androidx.compose.foundation.shape.CircleShape

private const val POSITION_TICK_MS = 200L
private const val CONTROLS_HIDE_MS = 3000L

/** Na kolik dílků se posuvník videa dělí, když tiká haptika (po pětinách procenta by bzučel). */
private const val VIDEO_SEEK_STEPS = 20
