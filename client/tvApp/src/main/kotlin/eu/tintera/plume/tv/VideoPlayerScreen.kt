package eu.tintera.plume.tv

import android.net.Uri
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.video.PartialFileDataSource
import eu.tintera.plume.core.video.StreamingSource
import eu.tintera.plume.core.video.VideoQualityChoice
import eu.tintera.plume.core.video.VideoSource
import eu.tintera.plume.core.video.streamOriginalVideo
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.connection_connecting
import eu.tintera.plume.resources.error_24dp
import eu.tintera.plume.resources.keyboard_arrow_up_24dp
import eu.tintera.plume.resources.media_pause
import eu.tintera.plume.resources.media_play
import eu.tintera.plume.resources.media_video_from_start
import eu.tintera.plume.resources.media_video_playback_failed
import eu.tintera.plume.resources.media_video_resumed
import eu.tintera.plume.resources.pause_24dp
import eu.tintera.plume.resources.play_arrow_24dp
import eu.tintera.plume.resources.replay_24dp
import eu.tintera.plume.resources.tv_up_next
import eu.tintera.plume.resources.tv_up_next_cancel
import eu.tintera.plume.resources.tv_up_next_play
import java.io.File
import eu.tintera.plume.core.di.APP_SCOPE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import org.koin.core.qualifier.named
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

/** Odpočet do dalšího videa z fronty (kanál jako pořad). */
private const val UP_NEXT_MS = 6_000L
private val UP_NEXT_WIDTH = 320.dp

/** Jak dlouho je po rozběhnutí z pamatované pozice vidět „Pokračuje od…" (jako na telefonu). */
private const val RESUME_HINT_MS = 6_000L

/** Pozice se za hraní zapisuje každých 10 s (40 tiknutí po 250 ms), jako na telefonu. */
private const val SAVE_EVERY_TICKS = 40

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
fun VideoPlayerScreen(
    request: VideoRequest,
    /** Ruční volba kvality; drží ji navigace, ať platí i pro další videa z fronty. */
    qualityChoice: VideoQualityChoice,
    onQualityChoice: (VideoQualityChoice) -> Unit,
    onNext: (VideoRequest) -> Unit,
    onClose: () -> Unit,
) {
    val video = request.video
    val caption = request.caption
    val context = LocalContext.current
    val files = koinInject<FileRepository>()
    val positions = koinInject<TvVideoPositions>()
    val accountId by koinInject<ChatRepository>().savedMessagesChatId.collectAsState()
    // Odkud začít: pamatovaná pozice (i z telefonu, viz TvPositionSync), jinak čas, od kterého
    // video poslal odesílatel. Čte se jednou — během sledování se pozice přepisuje.
    val remembered = remember { video.fileKey?.let(positions::of)?.positionMs }
    val startMs = remember { remembered ?: (video.startSeconds * 1000L) }
    val scope = rememberCoroutineScope()
    val appScope = koinInject<CoroutineScope>(named(APP_SCOPE))
    BackHandler(onBack = onClose)
    // Kterou kvalitu hrát (automatika podle sítě, nebo ruční volba); `null`, dokud se neví.
    val quality = rememberPlayedQuality(video, qualityChoice)
    val chosen = quality?.playing
    val chosenId = chosen?.fileId
    var choosingQuality by remember { mutableStateOf(false) }
    // Zavřené video (i opuštěná kvalita) se přestane stahovat: odběr `files.file` stahování
    // spustil a sám ho nezruší. Televize mívají kolem 8 GB, a náhled pár vteřin dlouhého videa
    // z kanálu by jinak stáhl celé. V aplikačním scope: scope kompozice je při zavření už zrušený.
    DisposableEffect(chosenId) {
        onDispose {
            val id = chosenId ?: return@onDispose
            appScope.launch {
                if (files.observe(id).first() is FileState.Downloading) files.cancelDownload(id)
            }
        }
    }

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

    // Stav souboru čte vlákno přehrávače, proto `MutableStateFlow`, ne Compose stav. Pro každou
    // kvalitu nový: se sdíleným by zdroj nové kvality na okamžik viděl stav STARÉHO souboru
    // (jeho cestu a velikost) — past, na kterou narazil telefon (`produceState` bez klíče).
    val fileState = remember(chosenId) { MutableStateFlow<FileState>(FileState.Pending) }
    LaunchedEffect(chosenId) {
        val id = chosenId ?: return@LaunchedEffect
        files.file(id).collect { fileState.value = it }
    }
    var failed by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    // Výměna kvality za běhu: od přepnutí do rozběhnutí nové se točí čekání.
    var switching by remember { mutableStateOf(false) }
    LaunchedEffect(chosenId) {
        val playedQuality = chosen ?: return@LaunchedEffect
        val fileId = playedQuality.fileId
        logger.i {
            "Video ${video.fileId}: hraje ${playedQuality.lines}p ${playedQuality.codec ?: "původní"} (soubor $fileId, " +
                "kvalit ${quality?.options?.size}, volba ${quality?.choice})"
        }
        // První kvalita začíná od pamatované pozice, další (ruční volba, změna sítě) tam, kde
        // předchozí zrovna byla; hrálo-li video, hraje dál (`playWhenReady` se nemění).
        val resumeAt = if (loaded) player.currentPosition else startMs
        if (loaded) switching = true
        // Přepočítaná kvalita je fragmentované MP4, to ExoPlayer streamuje vždycky.
        val canStream = !playedQuality.isOriginal || streamOriginalVideo(video.supportsStreaming, video.mimeType)
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
                    requestFrom = { offset -> scope.launch { files.downloadFrom(fileId, offset) } },
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
        if (resumeAt > 0) player.seekTo(resumeAt)
        player.prepare()
        loaded = true
    }

    // Stav přehrávače pro ovládání; pozice se čte tiknutím, ExoPlayer ji neohlašuje.
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(video.durationSeconds * 1000L) }
    // Další video z fronty, na které běží odpočet po dohrání; `null` = žádný odpočet.
    var upNext by remember { mutableStateOf<VideoRequest?>(null) }
    LaunchedEffect(upNext) {
        val next = upNext ?: return@LaunchedEffect
        delay(UP_NEXT_MS)
        onNext(next)
    }
    // Zpět během odpočtu ho jen zruší (video zůstane na konci); složeno po Zpět přehrávače, má přednost.
    BackHandler(enabled = upNext != null) { upNext = null }
    // Pozice se zapisuje jen u videa, které opravdu hrálo: otevření a zavření bez puštění by
    // zapsalo začátek, a tím pamatovanou pozici (třeba z telefonu) smazalo.
    var hasPlayed by remember { mutableStateOf(false) }
    val save = { at: Long ->
        val key = video.fileKey
        if (hasPlayed && key != null) {
            val source = accountId?.let { VideoSource(it, request.chatId, request.messageId) }
            positions.record(key, at, durationMs, source)
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                if (isPlaying) hasPlayed = true
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
                if (state == Player.STATE_READY) switching = false
                player.duration.takeIf { it != C.TIME_UNSET && it > 0 }?.let { durationMs = it }
                // Dohrané se zapomene (příště od začátku), jako na telefonu.
                if (state == Player.STATE_ENDED) {
                    save(durationMs)
                    // Kanál jako pořad: po dohrání odpočet do dalšího videa z fronty.
                    upNext = request.next()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                logger.w(error) { "Video ${video.fileId} (soubor $chosenId) na televizi nejde přehrát." }
                failed = true
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        var ticks = 0
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0)
            // Každých 10 s hraní: proces může zabít systém, nebo televize usne.
            if (playing && ++ticks % SAVE_EVERY_TICKS == 0) save(positionMs)
            delay(250)
        }
    }
    // Zavření přehrávače a odchod aplikace do pozadí (tlačítko Domů). Pozice z posledního
    // tiknutí: při odchodu z kompozice může být přehrávač už uvolněný.
    val latestPosition by rememberUpdatedState(positionMs)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) save(latestPosition) }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            save(latestPosition)
        }
    }

    // „Pokračuje od 12:34" a Od začátku na OK, dokud je nápis vidět (jako „Přeskočit úvod"
    // u streamovacích služeb). Odpočet běží od rozběhnutí, ne od otevření.
    var resumeHint by remember { mutableStateOf(remembered != null) }
    LaunchedEffect(hasPlayed) {
        if (hasPlayed && resumeHint) {
            delay(RESUME_HINT_MS)
            resumeHint = false
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
                val key = event.nativeKeyEvent.keyCode
                // Další video: OK během odpočtu, nebo kdykoli tlačítko „další" na ovladači.
                val queued = upNext ?: request.next()
                if (key == AndroidKeyEvent.KEYCODE_MEDIA_NEXT && queued != null) {
                    onNext(queued)
                    return@onPreviewKeyEvent true
                }
                if (upNext != null) {
                    if (key == AndroidKeyEvent.KEYCODE_DPAD_CENTER || key == AndroidKeyEvent.KEYCODE_ENTER) {
                        onNext(queued!!)
                        return@onPreviewKeyEvent true
                    }
                    // Cokoli jiného (skok zpět, pauza) odpočet zruší a udělá, co dělá vždycky.
                    upNext = null
                }
                if (resumeHint && (key == AndroidKeyEvent.KEYCODE_DPAD_CENTER || key == AndroidKeyEvent.KEYCODE_ENTER)) {
                    player.seekTo(0)
                    resumeHint = false
                    return@onPreviewKeyEvent true
                }
                when (key) {
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_SPACE -> {
                        // První stisk při schovaném ovládání ho jen ukáže, jako u systémových přehrávačů.
                        if (controlsVisible || !playing) player.togglePlayback()
                        true
                    }
                    // Tlačítka médií přepínají vždycky: klávesa se tu spotřebuje a `MediaSession`
                    // ji už nedostane, takže pauza z ovladače by jinak napoprvé jen ukázala ovládání.
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        player.togglePlayback()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY -> {
                        player.play()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        player.pause()
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
                    // Šipka nahoru při vidět ovládání otevře výběr kvality (nápověda je v liště),
                    // jinak šipky nahoru a dolů jen ukážou ovládání; jinam fokus nemá kam jít.
                    AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                        if (controlsVisible && (quality?.options?.size ?: 0) > 1) choosingQuality = true
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> true
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
            !loaded || switching || (buffering && !playing && !hasPlayed) ->
                LoadingScreen(stringResource(Res.string.connection_connecting))
        }
        AnimatedVisibility(
            // Během odpočtu do dalšího videa ovládání ustoupí kartě (jinak by ji překrývalo).
            visible = (controlsVisible || choosingQuality) && !failed && upNext == null,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerControls(
                caption = caption,
                playing = playing,
                positionMs = positionMs,
                durationMs = durationMs,
                quality = chosen?.takeIf { (quality?.options?.size ?: 0) > 1 }?.let { qualityLabel(it) },
            )
        }
        AnimatedVisibility(
            visible = resumeHint && hasPlayed && !failed,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = SAFE_VERTICAL),
        ) {
            ResumeHint(remembered ?: 0L)
        }
        upNext?.let { next ->
            UpNextCard(next, Modifier.align(Alignment.BottomEnd).padding(end = SAFE_HORIZONTAL, bottom = SAFE_VERTICAL))
        }
    }
    if (choosingQuality && quality != null) {
        val close = {
            choosingQuality = false
            lastInteraction = System.nanoTime()
            runCatching { focus.requestFocus() }
            Unit
        }
        VideoQualityDialog(
            quality = quality,
            onChoose = { choice ->
                onQualityChoice(choice)
                close()
            },
            onDismiss = close,
        )
    }
}

/**
 * Karta dalšího videa vpravo dole: náhled, popisek, odpočet a „Přehrát hned" (OK) / „Zrušit"
 * (Zpět). Jako u streamovacích služeb: tlačítka nejsou samostatný fokus, klávesy bere přehrávač.
 */
@Composable
private fun UpNextCard(next: VideoRequest, modifier: Modifier) {
    var elapsed by remember(next) { mutableLongStateOf(0L) }
    LaunchedEffect(next) {
        while (elapsed < UP_NEXT_MS) {
            delay(100)
            elapsed += 100
        }
    }
    val seconds = ((UP_NEXT_MS - elapsed + 999) / 1000).toInt().coerceAtLeast(0)
    val thumbnail = rememberImage(next.video.thumbnailFileId, with(LocalDensity.current) { UP_NEXT_WIDTH.roundToPx() })
    Column(
        modifier
            .width(UP_NEXT_WIDTH)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.8f)),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.DarkGray)) {
            thumbnail?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            Text(
                next.video.durationSeconds.asDuration(),
                style = MaterialTheme.typography.labelMedium.tabularNums(),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        // Odpočet jako proužek přes celou šířku karty.
        Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.25f))) {
            Box(Modifier.fillMaxWidth(elapsed.toFloat() / UP_NEXT_MS).height(3.dp).background(Color.White))
        }
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(Res.string.tv_up_next, seconds),
                style = MaterialTheme.typography.labelLarge.tabularNums(),
                color = Color.White.copy(alpha = 0.75f),
            )
            val title = next.caption.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
            if (title.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.clip(CircleShape).background(Color.White).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(painterResource(Res.drawable.play_arrow_24dp), contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                    Text(stringResource(Res.string.tv_up_next_play), style = MaterialTheme.typography.labelLarge, color = Color.Black)
                }
                Text(
                    stringResource(Res.string.tv_up_next_cancel),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.15f)).padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Pilulka „Pokračuje od 12:34" s tlačítkem Od začátku, které bere OK, dokud je vidět. */
@Composable
private fun ResumeHint(fromMs: Long) {
    Row(
        Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.7f)).padding(start = 24.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(Res.string.media_video_resumed, fromMs.asClock()),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
        Row(
            Modifier.clip(CircleShape).background(Color.White).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(painterResource(Res.drawable.replay_24dp), contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
            Text(stringResource(Res.string.media_video_from_start), style = MaterialTheme.typography.labelLarge, color = Color.Black)
        }
    }
}

/**
 * Spodní pruh: popisek, tlačítko stavu, průběh a časy, vpravo hrající kvalita s šipkou nahoru
 * ([quality], `null` = video jinou kvalitu nemá). Přes video tmavý přechod kvůli čitelnosti.
 */
@Composable
private fun PlayerControls(caption: String, playing: Boolean, positionMs: Long, durationMs: Long, quality: String?) {
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
            if (quality != null) {
                // Výběr kvality je šipkou nahoru; pilulka to říká ikonou, text je hrající kvalita.
                Row(
                    Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.15f)).padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(painterResource(Res.drawable.keyboard_arrow_up_24dp), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Text(quality, style = MaterialTheme.typography.labelLarge.tabularNums(), color = Color.White)
                }
            }
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
