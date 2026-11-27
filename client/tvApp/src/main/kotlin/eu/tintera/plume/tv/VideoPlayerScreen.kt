package eu.tintera.plume.tv

import android.net.Uri
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.video.PartialFileDataSource
import eu.tintera.plume.core.video.StreamingSource
import eu.tintera.plume.core.video.streamOriginalVideo
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.connection_connecting
import eu.tintera.plume.resources.error_24dp
import eu.tintera.plume.resources.media_pause
import eu.tintera.plume.resources.media_play
import eu.tintera.plume.resources.media_video_playback_failed
import eu.tintera.plume.resources.pause_24dp
import eu.tintera.plume.resources.play_arrow_24dp
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private val logger = Logger.withTag("TvVideo")

/** Krok skoku šipkou vlevo / vpravo a tlačítky přetáčení na ovladači. */
private const val SEEK_STEP_MS = 10_000L

/** Jak dlouho zůstane ovládání vidět, když video hraje a nikdo nesahá na ovladač. */
private const val CONTROLS_TIMEOUT_MS = 4_000L

/**
 * Video přes celou obrazovku. Hraje už během stahování: nad rozestavěným souborem TDLib čte
 * [PartialFileDataSource] z `:client:core` (tentýž, který používá telefon) a když přehrávač
 * chce data mimo stažený úsek (`moov` na konci souboru, skok), přesune tam stahování.
 *
 * Ovládání ovladačem: OK / Přehrát-Pozastavit přepíná, šipky vlevo a vpravo skáčou o 10 s,
 * Zpět zavře. Ovládací prvky se po čtyřech vteřinách přehrávání schovají a vrátí se s každým
 * stiskem. `MediaSession` dává přehrávač systému — tlačítka médií na ovladači a karta
 * „Právě hraje" na Google TV fungují i bez fokusu v aplikaci.
 */
@Composable
fun VideoPlayerScreen(video: MessageMedia.Video, caption: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val files = koinInject<FileRepository>()
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onClose)

    val player = remember {
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(SEEK_STEP_MS)
            .setSeekForwardIncrementMs(SEEK_STEP_MS)
            .build()
            .apply {
                // Fokus zvuku řeší ExoPlayer: hudba z jiné aplikace se ztiší, hovor video zastaví.
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                    /* handleAudioFocus = */ true,
                )
                playWhenReady = true
            }
    }
    DisposableEffect(player) {
        val session = MediaSession.Builder(context, player).setId("plume-tv-video").build()
        onDispose {
            session.release()
            player.release()
        }
    }

    // Stav souboru čte vlákno přehrávače, proto `MutableStateFlow`, ne Compose stav.
    val fileState = remember { MutableStateFlow<FileState>(FileState.Pending) }
    LaunchedEffect(video.fileId) { files.file(video.fileId).collect { fileState.value = it } }
    var failed by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(video.fileId) {
        val canStream = streamOriginalVideo(video.supportsStreaming, video.mimeType)
        val first = fileState.first { state ->
            state is FileState.Ready || state is FileState.Failed ||
                (canStream && state is FileState.Downloading && state.path != null && state.totalSize > 0)
        }
        when (first) {
            is FileState.Ready -> player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(first.path))))
            is FileState.Downloading -> {
                val source = StreamingSource(
                    // Hotový soubor TDLib přesune z `temp/`; zdroj si cestu čte při každém otevření.
                    path = { fileState.value.currentPath() ?: first.path.orEmpty() },
                    totalSize = first.totalSize,
                    downloaded = {
                        when (val state = fileState.value) {
                            is FileState.Downloading -> state.downloaded
                            is FileState.Ready -> 0L until Long.MAX_VALUE
                            else -> LongRange.EMPTY
                        }
                    },
                    requestFrom = { offset -> scope.launch { files.downloadFrom(video.fileId, offset) } },
                )
                val factory = DataSource.Factory { PartialFileDataSource(source) }
                player.setMediaSource(
                    ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(Uri.parse("tdlib://${first.path}"))),
                )
            }
            else -> {
                failed = true
                return@LaunchedEffect
            }
        }
        player.prepare()
        loaded = true
    }

    // Stav přehrávače pro ovládání; pozice se čte tiknutím, ExoPlayer ji neohlašuje.
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(video.durationSeconds * 1000L) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
                player.duration.takeIf { it != C.TIME_UNSET && it > 0 }?.let { durationMs = it }
            }

            override fun onPlayerError(error: PlaybackException) {
                logger.w(error) { "Video ${video.fileId} na televizi nejde přehrát." }
                failed = true
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0)
            delay(250)
        }
    }

    // Ovládání: vidět při pauze, čekání a chvíli po každém stisku.
    var lastInteraction by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(playing, lastInteraction) {
        controlsVisible = true
        if (playing) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                lastInteraction = System.nanoTime()
                when (event.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, AndroidKeyEvent.KEYCODE_SPACE -> {
                        // První stisk při schovaném ovládání ho jen ukáže, jako u systémových přehrávačů.
                        if (controlsVisible || !playing) player.togglePlayback()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                        player.seekBack()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT, AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        player.seekForward()
                        true
                    }
                    // Šipky nahoru a dolů jen ukážou ovládání; jinam fokus nemá kam jít.
                    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN -> true
                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    keepScreenOn = true
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    this.player = player
                }
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )
        when {
            failed -> MessageScreen(
                stringResource(Res.string.media_video_playback_failed),
                Res.drawable.error_24dp,
                error = true,
            )
            !loaded || (buffering && !playing && positionMs == 0L) ->
                LoadingScreen(stringResource(Res.string.connection_connecting))
        }
        AnimatedVisibility(
            visible = controlsVisible && !failed,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerControls(caption, playing, positionMs, durationMs)
        }
    }
}

/** Spodní pruh: popisek, tlačítko stavu, průběh a časy. Přes video tmavý přechod kvůli čitelnosti. */
@Composable
private fun PlayerControls(caption: String, playing: Boolean, positionMs: Long, durationMs: Long) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
            .padding(horizontal = SAFE_HORIZONTAL, vertical = SAFE_VERTICAL)
            .padding(top = 48.dp),
    ) {
        if (caption.isNotBlank()) {
            Text(
                caption,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (playing) Res.drawable.pause_24dp else Res.drawable.play_arrow_24dp),
                    contentDescription = stringResource(if (playing) Res.string.media_pause else Res.string.media_play),
                    tint = Color.Black,
                    modifier = Modifier.size(32.dp),
                )
            }
            Text(positionMs.asClock(), style = MaterialTheme.typography.labelLarge.tabularNums(), color = Color.White)
            ProgressBar(
                progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
                modifier = Modifier.weight(1f),
            )
            Text(durationMs.asClock(), style = MaterialTheme.typography.labelLarge.tabularNums(), color = Color.White)
        }
    }
}

@Composable
private fun ProgressBar(progress: Float, modifier: Modifier) {
    Box(modifier.height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.3f))) {
        Box(Modifier.fillMaxWidth(progress).height(6.dp).background(MaterialTheme.colorScheme.primary))
    }
}

private fun Player.togglePlayback() {
    if (playbackState == Player.STATE_ENDED) seekTo(0)
    if (isPlaying) pause() else play()
}

private fun FileState.currentPath(): String? = when (this) {
    is FileState.Ready -> path
    is FileState.Downloading -> path
    else -> null
}

/** Čas přehrávání: `m:ss`, u hodinových videí `h:mm:ss`. */
private fun Long.asClock(): String {
    val total = (this / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun androidx.compose.ui.text.TextStyle.tabularNums() = copy(fontFeatureSettings = "tnum")

/** Bezpečné okraje obrazovky televize (Google: 5 % — 58 dp po stranách, 28 dp nahoře a dole). */
val SAFE_HORIZONTAL = 58.dp
val SAFE_VERTICAL = 28.dp
