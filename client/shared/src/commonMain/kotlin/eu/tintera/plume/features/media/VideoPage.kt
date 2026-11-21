package eu.tintera.plume.features.media

import kotlinx.datetime.TimeZone
import eu.tintera.plume.core.files.savedFileName
import eu.tintera.plume.core.ui.rememberFileState
import eu.tintera.plume.core.ui.fileDragSupported
import eu.tintera.plume.core.ui.fileDragSource
import eu.tintera.plume.core.ui.DraggedFile
import androidx.compose.ui.input.pointer.PointerType
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.VideoStoryboard
import eu.tintera.plume.core.telegram.VideoQuality
import eu.tintera.plume.core.ui.LocalAutoDownload
import eu.tintera.plume.core.video.VideoQualityChoice
import eu.tintera.plume.core.video.pickVideoQuality
import eu.tintera.plume.core.video.videoDecoder
import eu.tintera.plume.core.video.videoQualities
import eu.tintera.plume.core.video.videoFragmentedStreamingNeedsHls
import eu.tintera.plume.core.video.videoFragmentedStreamingSupported
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toPath
import okio.FileSystem
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import eu.tintera.plume.core.ui.longPress
import eu.tintera.plume.resources.media_seek_back
import eu.tintera.plume.resources.media_seek_forward
import eu.tintera.plume.resources.media_speed_boost
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import eu.tintera.plume.core.video.VideoPositions
import eu.tintera.plume.resources.media_video_from_start
import eu.tintera.plume.resources.media_video_resumed
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.material3.IconToggleButton
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import eu.tintera.plume.core.adaptive.rememberTabletopHinge
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import eu.tintera.plume.core.ui.HideSystemBars
import eu.tintera.plume.core.ui.KeepScreenOn
import eu.tintera.plume.core.ui.statusBarArea
import eu.tintera.plume.core.ui.toggle
import eu.tintera.plume.resources.lock_24dp
import eu.tintera.plume.resources.lock_open_24dp
import eu.tintera.plume.resources.media_lock_controls
import eu.tintera.plume.resources.media_rotate_screen
import eu.tintera.plume.resources.media_unlock_controls
import eu.tintera.plume.resources.screen_rotation_24dp
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.foundation.layout.width
import eu.tintera.plume.core.ui.ScreenBrightness
import eu.tintera.plume.core.ui.rememberScreenBrightness
import eu.tintera.plume.resources.light_mode_24dp
import eu.tintera.plume.resources.media_brightness
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
import androidx.compose.material3.SliderState
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
import androidx.compose.ui.input.pointer.PointerEventPass
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
import eu.tintera.plume.features.conversation.items.SpoilerOverlay
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
import kotlinx.coroutines.flow.Flow
import eu.tintera.plume.resources.media_fullscreen_exit
import eu.tintera.plume.resources.media_fullscreen_enter
import eu.tintera.plume.resources.fullscreen_exit_24dp
import eu.tintera.plume.resources.fullscreen_24dp
import eu.tintera.plume.resources.media_pause
import eu.tintera.plume.resources.media_play
import eu.tintera.plume.resources.media_replay
import eu.tintera.plume.resources.media_video_download_failed
import eu.tintera.plume.resources.media_video_playback_failed
import androidx.compose.ui.text.style.TextAlign
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
 * - **Velký průběh uprostřed jen do rozběhnutí videa**, pak ho vystřídá ovládání a stažený
 *   úsek je vidět na dráze posuvníku ([VideoSeekTrack]).
 * - **Hraje jen vybraná stránka**; odlistování pozastaví, návrat pokračuje.
 * - **Ovládání se schová po [CONTROLS_HIDE_MS]** během přehrávání. Ťuknutí na celé obrazovce
 *   ho napřed vrátí, a teprve ťuknutí s ovládáním na očích vede zpátky do galerie. Jinak by
 *   se k posuvníku u hrajícího videa nedalo dostat.
 * - **Tah dolů/nahoru zavírá** jako u fotky; vodorovný tah nechává pageru. U videa na celé
 *   obrazovce mění svislý tah v levé půlce jas (viz „Jas tahem").
 * - **Karta jako u fotky** ([MediaPageLayout]): video se zaoblením, popisek pod ní. Ovládání
 *   přehrávače (posuvník, zámek) leží u okrajů stránky, ne na kartě.
 */
@Composable
internal fun VideoPage(
    video: MessageMedia.Video,
    messageId: Long,
    /** Čas v médiu: po načtení skočit sem (ms); nula = od začátku. */
    startMs: Long = 0L,
    caption: String,
    placement: MediaPagePlacement,
    dismissDrag: Animatable<Float, *>,
    gestures: MediaPageGestures,
    /**
     * Smí se přehrávat rozestavěný soubor. U mizejícího média ne: odpočet začíná až se staženým
     * souborem a video nesmí hrát dřív, než běží.
     */
    streaming: Boolean = true,
    /** Spoiler přes video; `null` = nic nezakrývá. Citlivý obsah doplní stránka z videa. */
    spoiler: MediaSpoiler? = null,
    modifier: Modifier = Modifier,
    /** Ztlumení; drží ho prohlížeč, aby platilo pro všechna videa, dokud je otevřený. `null` = bez tlačítka. */
    mute: VideoMute? = null,
    /** Obraz v obraze; `null` = nesmí (mizející médium — mělo by zůstat v aplikaci). */
    pictureInPicture: VideoPictureInPicture? = null,
    /** Ovládání celé obrazovky jako v přehrávači (lišty, zámek, otočení); `null` = bez něj. */
    fullscreen: VideoFullscreen? = null,
    commands: Flow<VideoCommand>? = null,
    /**
     * Pamatovat si, kde se přestalo sledovat ([VideoPositions]). Mizející médium ne (podruhé se
     * neotevře) a tajný chat ne — po tajném chatu nemá v zařízení zůstat stopa.
     */
    rememberPosition: Boolean = false,
    /** Volba kvality v liště; `null` = bez nabídky (hraje se, co vybere automatika). */
    quality: VideoQualitySelection? = null,
    /** Hraje-li vybraná stránka (viz druhé [VideoPage]); prohlížeč podle toho vypíná listování. */
    onPlayingChange: ((Boolean) -> Unit)? = null,
    /** Čas zprávy pro jméno videa taženého z okna ven; `null` = video se ven netáhne. */
    dragSentAt: Int? = null,
) {
    val scope = rememberCoroutineScope()
    val repository = koinInject<FileRepository>()
    // Ven jde původní soubor, a jen celý stažený — stav se sleduje, nic se kvůli tomu nestahuje.
    val original = rememberFileState(video.fileId.takeIf { dragSentAt != null && fileDragSupported }, download = false)
    val dragOut = if (dragSentAt != null && !spoiler.hidden) {
        {
            (original as? FileState.Ready)?.let { ready ->
                DraggedFile(ready.path, savedFileName(null, ready.path, dragSentAt, TimeZone.currentSystemDefault()))
            }
        }
    } else {
        null
    }
    val isCurrent = placement.isCurrent
    val qualityChoice = quality?.choice ?: VideoQualityChoice.Auto

    /*
     * Kvalita: původní soubor, nebo kvalita, kterou server přepočítal (`alternativeVideos`).
     * Mizející video jen původní — odpočet běží nad tím souborem a stream se u něj nepouští.
     */
    val qualities = remember(video, streaming) {
        if (!streaming) {
            listOf(video.original)
        } else {
            videoQualities(video.original, video.alternatives) { videoDecoder(it.codec.orEmpty(), it.width, it.height) }
        }
    }
    // Už stažený původní soubor vyhrává automatiku: nic nestojí. Jen sleduje, nic nestahuje.
    val originalReady by produceState(false, video.fileId, qualities.size) {
        if (qualities.size > 1) repository.observe(video.fileId).collect { value = it is FileState.Ready }
    }
    // Strop podle sítě a nastavení automatického stahování; mění se i za běhu (Wi-Fi → data).
    val autoMaxLines = LocalAutoDownload.current.maxVideoLines
    // Přepočítané kvality jsou fragmentované MP4; kde je platforma streamovat neumí (desktop), čekalo by se na celé.
    val convertedStreams = videoStreamingSupported && videoFragmentedStreamingSupported
    val autoPick = pickVideoQuality(qualities, VideoQualityChoice.Auto, autoMaxLines, originalReady, convertedStreams)
    val chosen = pickVideoQuality(qualities, qualityChoice, autoMaxLines, originalReady, convertedStreams)
    val chosenId = chosen.fileId
    // iOS streamuje přepočítanou kvalitu jen jako HLS — potřebuje její seznam.
    val playlistId = chosen.hlsFileId?.takeIf { !chosen.isOriginal && videoFragmentedStreamingNeedsHls }
    // Co se hraje a proč — jen u zobrazené stránky, prohlížeč skládá i sousedy.
    LaunchedEffect(chosenId, isCurrent) {
        if (qualities.size > 1 && isCurrent) {
            val offered = qualities.joinToString { it.logLabel() }
            val sent = video.alternatives.joinToString { it.logLabel() }
            logger.d { "Video ${video.fileId}: hraje ${chosen.logLabel()}, volba $qualityChoice, strop $autoMaxLines, nabídka $offered, od serveru $sent" }
        }
    }
    /*
     * Opuštěná kvalita se přestane stahovat — o to při volbě menší kvality jde. Jen rozstahovaná:
     * hotový soubor zůstává a o soubor, který se nestahoval, se TDLib žádat nemusí.
     */
    var previousId by remember { mutableStateOf(chosenId) }
    LaunchedEffect(chosenId) {
        val abandoned = previousId
        previousId = chosenId
        if (abandoned != chosenId && repository.observe(abandoned).first() is FileState.Downloading) {
            repository.cancelDownload(abandoned)
        }
    }
    val positions = koinInject<VideoPositions>()
    val positionKey = video.fileKey?.takeIf { rememberPosition && !video.isSecret }
    // Čte se jednou: během sledování se pozice zapisuje a začátek se tím měnit nemá.
    val resumeMs = remember(positionKey) { positionKey?.let(positions::of)?.positionMs }
    /*
     * Odkud začít: čas z odkazu ve zprávě (výslovná žádost) > kde uživatel sám přestal > odkud
     * video pustil odesílatel (přeposlání „od 1:23").
     */
    val resumed = resumeMs?.takeIf { startMs <= 0L }
    VideoPage(
        progress = VideoProgress(
            startMs = when {
                startMs > 0L -> startMs
                resumed != null -> resumed
                else -> video.startSeconds * 1000L
            },
            resumedFromMs = resumed,
            onProgress = positionKey?.let { key -> { position, duration -> positions.record(key, position, duration) } },
        ),
        storyboard = video.storyboard,
        fullscreen = fullscreen,
        commands = commands,
        mute = mute,
        pictureInPicture = pictureInPicture,
        aspectRatio = aspectRatioOf(video.width, video.height),
        durationSeconds = video.durationSeconds,
        caption = caption,
        placement = placement,
        dismissDrag = dismissDrag,
        gestures = gestures.copy(dragOut = dragOut),
        source = VideoFileSource(
            state = { started ->
                /*
                 * `key`, ne jen klíč `produceState`: ten restartuje producenta, ale stav si pamatuje
                 * BEZ klíče, takže po výběru jiné kvality vracel ještě pár snímků stav STARÉHO souboru.
                 * Stránka pak založila zdroj streamu s cestou a velikostí starého souboru a se
                 * stahováním nového — přehrávač četl cizí soubor (ověřeno: nová kvalita se žádala na
                 * offsetu za koncem svého souboru, kde končil `mdat` původního).
                 */
                key(chosenId) {
                    // HLS seznam se stahuje souběžně s videem: až bude stream čekat jen na něj, je už na disku.
                    if (started && playlistId != null) {
                        LaunchedEffect(playlistId) { repository.file(playlistId).first { it is FileState.Ready || it is FileState.Failed } }
                    }
                    produceState<FileState>(FileState.Pending, chosenId, started) {
                        if (started) repository.file(chosenId).collect { value = it }
                    }.value
                }
            },
            key = chosenId,
            /*
             * Streamování: přehrát rozestavěný soubor, místo aby se čekalo na celý. Původní soubor
             * jen s `supportsStreaming` od serveru. Přepočítané kvality vyrábí server pro streamování
             * (fragmentované MP4), takže vždycky — kde je platforma umí
             * ([videoFragmentedStreamingSupported]; iOS přes HLS).
             */
            streamable = streaming && videoStreamingSupported &&
                if (chosen.isOriginal) video.supportsStreaming else videoFragmentedStreamingSupported,
            // Přehrávač si řekne o `moov` atom na konci souboru; bez přesunu stahování by na něj
            // čekal marně. Lambda nese `fileId` té kvality, pro kterou zdroj vznikl.
            requestFrom = { offset -> scope.launch { repository.downloadFrom(chosenId, offset) } },
            hlsPlaylist = playlistId?.let { id -> suspend { repository.readText(id) } },
        ),
        onPlayingChange = onPlayingChange,
        quality = quality?.takeIf { qualities.size > 1 }?.let { selection ->
            VideoQualityMenu(
                options = qualities,
                choice = selection.choice,
                playing = chosen,
                autoPick = autoPick,
                onChoose = selection.onChoose,
            )
        },
        spoiler = spoiler?.copy(sensitive = video.isSensitive),
        modifier = modifier,
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
                            shape = RoundedCornerShape(MEDIA_CARD_CORNER * (1f - placement.immersion())),
                            besidePanes = true,
                        )
                        .spoilerBlur(spoiler),
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
    placement: MediaPagePlacement,
    dismissDrag: Animatable<Float, *>,
    gestures: MediaPageGestures,
    source: VideoFileSource,
    /** Náhled přes celou kartu, dokud se video nepřehrává. */
    thumbnail: @Composable () -> Unit,
    /** Spoiler přes video; `null` = nic nezakrývá. */
    spoiler: MediaSpoiler? = null,
    modifier: Modifier = Modifier,
    /** Ovládání na kartě navíc (✕ a volba režimu ve sdílení); v galerii, na celé obrazovce ne. */
    overlay: @Composable BoxScope.() -> Unit = {},
    progress: VideoProgress = VideoProgress(),
    /** Ztlumení za běhu; `null` = bez tlačítka a nahlas (náhledy při sdílení a nad polem). */
    mute: VideoMute? = null,
    /**
     * Obraz v obraze: tlačítko v liště a samočinný přechod při odchodu z aplikace, dokud video
     * hraje. Jen v prohlížeči médií (`null` = bez něj) — náhled při sdílení a nad polem je
     * chvilková kontrola.
     */
    pictureInPicture: VideoPictureInPicture? = null,
    /**
     * Celá obrazovka jako v přehrávači: systémové lišty se schovávají spolu s ovládáním, zámek
     * proti dotykům a ruční otočení. Jen v prohlížeči médií; `null` = bez toho (náhledy).
     */
    fullscreen: VideoFullscreen? = null,
    /** Povely z klávesnice ([VideoCommand]); vyřizuje je jen vybraná stránka na celé obrazovce. */
    commands: Flow<VideoCommand>? = null,
    /** Náhledy snímků nad posuvníkem; `null` = bez nich (krátké video, sdílení). */
    storyboard: VideoStoryboard? = null,
    /** Čip kvality v liště s nabídkou; `null` = bez něj (jediná kvalita, sdílení). */
    quality: VideoQualityMenu? = null,
    /**
     * Ozve se, když vybraná stránka začne nebo přestane hrát. Prohlížeč podle toho vypíná
     * listování — hrající video se nesmí odlistovat nechtěným tahem, pozastavené ano.
     */
    onPlayingChange: ((Boolean) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val voicePlayer = koinInject<VoicePlayer>()
    val settings = koinInject<AppSettings>()
    // Rychlost je volba přes celou aplikaci (jako u hlasovek), jen zvlášť pro video.
    val speed by settings.videoSpeed.collectAsState()
    val isCurrent = placement.isCurrent
    val immersive = placement.immersive
    val immersion = placement.immersion
    val sourceKey = source.key
    val startMs = progress.startMs
    val currentDismiss by rememberUpdatedState(gestures.onDismiss)
    // Tah videa ven myší ([MediaPageGestures.dragOut]); kde jde, myš stránku tahem nezavírá.
    val dragOut = gestures.dragOut?.takeIf { fileDragSupported }
    val currentDraggable by rememberUpdatedState(dragOut != null)

    // Hraje (a stahuje se) jen vybraná stránka na celé obrazovce.
    val active = isCurrent && immersive
    val currentTap by rememberUpdatedState(gestures.onTap)
    val currentImmersive by rememberUpdatedState(immersive)

    // Jednou spuštěné video stahuje dál, i když se odlistuje — rozstahované se nezahazuje.
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(active) { if (active) started = true }
    val file = source.state(started)
    val path = (file as? FileState.Ready)?.path

    val downloading = file as? FileState.Downloading
    /*
     * Souvisle stažený úsek a cesta se čtou z vlákna přehrávače, proto `MutableStateFlow`, ne
     * Compose stav. Každý zdroj má svoje: po výběru jiné kvality dohrává stará do výměny ze
     * svých hodnot a nová by jí je jinak přepsala cestou a úsekem úplně jiného souboru.
     */
    val downloadedRange = remember(sourceKey) { MutableStateFlow(LongRange.EMPTY) }
    val currentPath = remember(sourceKey) { MutableStateFlow("") }
    /*
     * Stream musí přežít DOKONČENÍ stahování. TDLib hotový soubor přesune z `temp/` na konečnou
     * cestu a `FileState.Downloading` vystřídá `Ready` — bez `downloaded` i bez dočasné cesty.
     * Přehrávač se kvůli tomu nepřenačítá (hrající video by zaškobrtlo), jen zdroj dostane
     * novou cestu a celý soubor jako stažený: další otevření (skok, přehrání od začátku) čte
     * odtud. Dřív zůstala dočasná cesta a opakované přehrání skončilo na ENOENT.
     */
    when (file) {
        is FileState.Downloading -> {
            downloadedRange.value = file.downloaded
            file.path?.let { currentPath.value = it }
        }
        // Hotový soubor je celý; kolik ho je, hlídá zdroj sám podle `totalSize`.
        is FileState.Ready -> {
            downloadedRange.value = 0L until Long.MAX_VALUE
            currentPath.value = file.path
        }
        else -> downloadedRange.value = LongRange.EMPTY
    }
    val streamPath = downloading?.path?.takeIf { source.streamable }
    val streamTotal = downloading?.totalSize ?: 0L

    val player = rememberVideoPlayer()
    var loaded by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(durationSeconds * 1000L) }
    var playing by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    // Přehrávač skončil chybou (zmizelý soubor, nečitelná data): nehraje, ať tvrdí UI cokoli.
    var failed by remember { mutableStateOf(false) }
    // Video se opravdu rozběhlo (ne jen načetlo): do té doby točí velký průběh uprostřed.
    var hasPlayed by remember { mutableStateOf(false) }
    // Během tažení posuvníku se ukazuje místo pod prstem, ne kde přehrávač zrovna je.
    var seekingFraction by remember { mutableStateOf<Float?>(null) }
    // Který zdroj ([VideoFileSource.key]) přehrávač právě má; jiný = čeká se na výměnu kvality.
    var loadedKey by remember { mutableStateOf<Any?>(null) }
    // Po výměně kvality hrající video čeká na data nového souboru: točí se průběh uprostřed.
    var swapping by remember { mutableStateOf(false) }

    // `load` hlasitost i rychlost přenastaví, takže se obojí zapisuje až PO načtení — i po
    // výměně kvality (klíč `loadedKey`).
    LaunchedEffect(loaded, loadedKey, speed) { if (loaded) player.setSpeed(speed.rate) }
    val muted = mute?.muted == true
    LaunchedEffect(loaded, loadedKey, muted) { if (loaded) player.setMuted(muted) }

    /*
     * Výměna kvality: dokud nový soubor nemá na disku nic, hraje dál ten starý (bez čekání na
     * černé obrazovce); pak přehrávač dostane nový zdroj, skočí na pozici, kde starý zrovna byl,
     * a pokud hrál, hraje dál. Přehrávač ani povrch se nezakládají znovu, takže zůstává obraz
     * v obraze, rychlost i ztlumení.
     */
    LaunchedEffect(sourceKey, path, streamPath) {
        if (loaded && loadedKey == sourceKey) return@LaunchedEffect
        val swap = loaded
        val wasPlaying = swap && player.isPlaying
        val resumeAt = if (swap) player.positionMs else startMs
        when {
            // Hotový soubor má přednost: jednodušší cesta a nic se na něj nečeká.
            path != null -> player.load(path)
            // Streamovat se dá, teprve až je na disku začátek souboru s indexem.
            streamPath != null && streamTotal > 0 -> {
                // Bez seznamu (nestáhl se) zbývá počkat na celý soubor: efekt se znovu spustí s `path`.
                val hlsPlaylist = source.hlsPlaylist
                val playlist = if (hlsPlaylist != null) hlsPlaylist() ?: return@LaunchedEffect else null
                player.loadStreaming(
                    StreamingSource(
                        path = { currentPath.value },
                        totalSize = streamTotal,
                        downloaded = { downloadedRange.value },
                        requestFrom = source.requestFrom,
                        hlsPlaylist = playlist,
                    ),
                )
            }
            else -> return@LaunchedEffect
        }
        if (resumeAt > 0) player.seekTo(resumeAt)
        if (wasPlaying) {
            player.play()
            swapping = true
        }
        loadedKey = sourceKey
        loaded = true
    }

    /*
     * Pozice se zapisuje jen u videa, které opravdu hrálo — jinak by otevření a zavření bez
     * puštění zapsalo nulu, a tím pamatovanou pozici smazalo. Čte se z posledního tiknutí
     * (`positionMs`), ne z přehrávače: při odchodu z kompozice už může být uvolněný.
     */
    val currentProgress by rememberUpdatedState(progress.onProgress)
    fun saveProgress(at: Long = positionMs) {
        if (hasPlayed) currentProgress?.invoke(at, durationMs)
    }

    LaunchedEffect(loaded, active) {
        if (!loaded) return@LaunchedEffect
        if (active) {
            // Hrající hlasovka by se s videem prala o reproduktor.
            voicePlayer.stop()
            player.play()
        } else {
            player.pause()
            saveProgress()
        }
    }
    // Průběžně, ať pozici nevezme ani zabitý proces; zápis do DataStore jednou za pár vteřin.
    LaunchedEffect(playing) {
        while (playing) {
            delay(PROGRESS_SAVE_MS)
            saveProgress()
        }
    }
    DisposableEffect(Unit) { onDispose { saveProgress() } }

    // „Pokračuje od…" po rozběhnutí na chvíli; pak už je jasné, co se děje.
    var resumeHint by remember { mutableStateOf(false) }
    LaunchedEffect(hasPlayed) {
        if (hasPlayed && progress.resumedFromMs != null) {
            resumeHint = true
            delay(RESUME_HINT_MS)
            resumeHint = false
        }
    }

    val currentPlayingChange by rememberUpdatedState(onPlayingChange)
    LaunchedEffect(playing, isCurrent) { if (isCurrent) currentPlayingChange?.invoke(playing) }

    // Přehrávač stav nehlásí; čte se po kouskách, jen dokud je stránka vybraná.
    LaunchedEffect(loaded, active) {
        if (!loaded || !active) return@LaunchedEffect
        while (true) {
            positionMs = player.positionMs
            player.durationMs.takeIf { it > 0 }?.let { durationMs = it }
            playing = player.isPlaying
            ended = player.isEnded
            failed = player.isFailed
            // Optimistické `playing = true` z tlačítka by jinak po chybě viselo do dalšího tiknutí.
            if (failed) playing = false
            if (playing) hasPlayed = true
            if (playing || failed) swapping = false
            delay(POSITION_TICK_MS)
        }
    }

    // Otevřená nabídka kvality drží ovládání na očích — se schovanou lištou by zmizela i ona.
    var qualityMenuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(controlsVisible, playing, qualityMenuOpen) {
        if (controlsVisible && playing && !qualityMenuOpen) {
            delay(CONTROLS_HIDE_MS)
            controlsVisible = false
        }
    }

    // Po dohrání se ovládání vrátí, ať je vidět tlačítko na opakování.
    LaunchedEffect(ended) {
        if (ended) {
            controlsVisible = true
            // Dokoukané video se zapomene (konec se nepamatuje, viz `resumePosition`).
            saveProgress(at = durationMs)
        }
    }
    // Po chybě taky: tlačítko přehrání přehrávač připraví znovu.
    LaunchedEffect(failed) { if (failed) controlsVisible = true }
    // Na celou obrazovku se přichází s ovládáním na očích; během přehrávání se samo schová.
    LaunchedEffect(immersive) { if (immersive) controlsVisible = true }

    /*
     * Obraz v obraze jen pro video, které právě hraje na celé obrazovce: parametry PiP platí pro
     * celou aktivitu, takže je smí nastavovat nejvýš jedna stránka. V okénku zůstane jen obraz —
     * ovládání v něm nejde ovládat prstem a zakrylo by půlku záběru.
     */
    val pip = if (pictureInPicture != null && active && loaded) {
        rememberPictureInPicture(
            player = player,
            aspectRatio = aspectRatio,
            playing = playing,
            onTogglePlay = {
                if (player.isPlaying) player.pause() else player.play()
                playing = !playing
            },
            onReturn = pictureInPicture.onReturn,
        )
    } else {
        null
    }
    val inPip = pip?.active == true
    // Desktop: video odešlo do vlastního okénka, prohlížeč se zavře a dá se psát dál.
    LaunchedEffect(inPip) { if (inPip && pip?.leavesViewer == true) currentDismiss?.invoke() }
    // Zavřené okénko i odchod na pozadí bez okénka video pozastaví (proces pod ním žije dál).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        player.pause()
        saveProgress()
    }

    // Hrající video nenechá displej zhasnout, ani v okénku obrazu v obraze.
    if (active && playing) KeepScreenOn()

    /*
     * Zámek proti dotykům: video hraje dál, ale tah nezavírá, ťuknutí nepřepíná a ovládání se
     * neukáže — ťuknutí jen vytáhne zamčený zámek, kterým se odemyká. Listování vypíná prohlížeč.
     */
    val locked = fullscreen?.locked == true && active
    val currentLocked by rememberUpdatedState(locked)
    var lockHint by remember { mutableStateOf(false) }
    // Po zamčení se zámek na chvíli ukáže — je vidět, co se stalo a čím se to vrací.
    LaunchedEffect(locked) { lockHint = locked }
    LaunchedEffect(lockHint) {
        if (lockHint) {
            delay(CONTROLS_HIDE_MS)
            lockHint = false
        }
    }
    /*
     * Zpět za zamčení nezavírá, jen ukáže zámek: zámek je proti nechtěným dotykům a tah od okraje
     * je ten nejčastější. `NavigationBackHandler`, ne `BackHandler` — ten by zabil predictive
     * back i odemčenému prohlížeči. Odemčený handler je vypnutý a gesto bere `NavDisplay`.
     */
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = locked,
        onBackCompleted = { lockHint = true },
    )

    /*
     * Systémové lišty jdou pryč spolu s ovládáním a vracejí se s ním (jako YouTube); zamčené
     * video je nechá schované. Tah od okraje je ukáže jen na chvíli.
     */
    if (fullscreen != null && active && loaded && !inPip) {
        HideSystemBars(hidden = locked || !controlsVisible)
    }

    /*
     * Gesta přehrávače: dvojí ťuknutí do levé / pravé půlky skočí o [SEEK_STEP_MS] zpět / vpřed
     * (víc ťuknutí rychle za sebou se sčítá), podržení pustí video dvojnásobnou rychlostí, dokud
     * prst leží. Zamčené video je nemá — zámek je právě proti náhodným dotykům.
     */
    val playerGestures = active && loaded && !locked && !inPip && !failed
    val pageHaptics = LocalHapticFeedback.current
    // Kolik se naskákalo a kterým směrem; ukazuje se u okraje, na který se ťuklo.
    var seekShown by remember { mutableLongStateOf(0L) }
    var seekRun by remember { mutableIntStateOf(0) }
    LaunchedEffect(seekRun) {
        if (seekRun > 0) {
            delay(SEEK_HINT_MS)
            seekShown = 0L
        }
    }
    fun seekBy(delta: Long) {
        // Opačný směr nebo pauza mezi dvojťuky začne počítat znovu.
        seekShown = if (seekShown != 0L && (seekShown > 0) == (delta > 0)) seekShown + delta else delta
        seekRun++
        val target = (player.positionMs + delta).coerceIn(0L, durationMs.coerceAtLeast(0L))
        player.seekTo(target)
        positionMs = target
        ended = false
        pageHaptics.tick()
    }
    LaunchedEffect(commands, active, loaded, locked) {
        if (commands == null || !active || !loaded || locked) return@LaunchedEffect
        commands.collect { command ->
            when (command) {
                VideoCommand.PlayPause -> {
                    if (playing) player.pause() else player.play()
                    playing = !playing
                    // Pozastavené ukáže ovládání, ať je vidět, kde video stojí.
                    if (!playing) controlsVisible = true
                }
                is VideoCommand.Seek -> if (!failed) seekBy(command.deltaMs)
            }
        }
    }

    /*
     * Dvojklik přepíná celou obrazovku okna (desktop, jako YouTube) místo skoku o 10 s — myší se
     * dvojklikem neposouvá, na to jsou šipky. Bere se aktuální stav, gesto se nezakládá znovu.
     */
    val toggleWindow: (() -> Unit)? = fullscreen?.onWindowChange?.let { change -> { change(!fullscreen.window) } }
    val currentToggleWindow by rememberUpdatedState(toggleWindow)

    var boosting by remember { mutableStateOf(false) }
    fun startBoost() {
        if (!playing) return
        boosting = true
        controlsVisible = false
        player.setSpeed(BOOST_RATE)
        pageHaptics.longPress()
    }
    fun stopBoost() {
        if (!boosting) return
        boosting = false
        player.setSpeed(speed.rate)
    }

    // Storyboard se stahuje, až video na celé obrazovce opravdu hraje — sousedé a galerie ne.
    val board = rememberStoryboard(storyboard, load = active && hasPlayed)

    /*
     * Jas tahem: svislý tah v levé půlce videa na celé obrazovce (jako MX Player nebo VLC; levá
     * i v RTL, je to zvyk přehrávačů, ne směr čtení). V pravé půlce svislý tah dál zavírá —
     * jinak by se z videa na celé obrazovce gestem nedalo odejít. Jas platí, jen dokud je stránka
     * na celé obrazovce: návrat do galerie, odlistování i zavření ho vrátí (jako YouTube).
     * V obrazu v obraze taky, jas okna by jinak svítil přes celý displej.
     */
    val brightness = if (fullscreen != null && active && loaded && !inPip) rememberScreenBrightness() else null
    val currentBrightness by rememberUpdatedState(brightness)
    val currentPlayerGestures by rememberUpdatedState(playerGestures)
    val currentBoosting by rememberUpdatedState(boosting)
    // Ukazatel jasu: během tahu a chvíli po něm.
    var brightnessShown by remember { mutableStateOf(false) }
    var brightnessRun by remember { mutableIntStateOf(0) }
    LaunchedEffect(brightnessRun) {
        if (brightnessRun > 0) {
            delay(SEEK_HINT_MS)
            brightnessShown = false
        }
    }

    /*
     * Skládačka na stole (napůl otevřená, pant vodorovně): video v půlce nad pantem, která stojí,
     * ovládání uprostřed ležící půlky pod ním — jako YouTube nebo Fotky Google na Foldu. Jen na celé
     * obrazovce; v galerii a v obrazu v obraze se nic nemění. Pant je v souřadnicích okna, stránka
     * si svou polohu v okně zapisuje při umístění (posun pageru do strany ji nemění).
     */
    val hinge = rememberTabletopHinge()?.takeIf { immersive && !inPip }
    var pageTop by remember { mutableFloatStateOf(0f) }
    var pageHeight by remember { mutableIntStateOf(0) }
    val tabletopTop = hinge?.let { (it.top - pageTop).roundToInt() }?.takeIf { it in 1 until pageHeight }
    val tabletopBottom = hinge?.let { (it.bottom - pageTop).roundToInt() }?.takeIf { tabletopTop != null }

    MediaPageLayout(
        aspectRatio = aspectRatio,
        caption = caption,
        reservedTop = placement.reservedTop,
        reservedBottom = placement.reservedBottom,
        immersion = immersion,
        captionAlpha = placement.captionAlpha,
        cardBias = placement.cardBias,
        tabletopSplit = { tabletopTop },
        modifier = modifier
            // Před posunem tahem (`offset` níž), ať se poloha stránky při zavírání tahem nemění.
            .onPlaced { coordinates ->
                if (hinge != null) {
                    pageTop = coordinates.positionInWindow().y
                    pageHeight = coordinates.size.height
                }
            }
            .pointerInput(Unit) {
                // Jen svislý tah (zavírání); vodorovný se nekonzumuje a patří pageru.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.type == PointerType.Mouse && currentDraggable) return@awaitEachGesture
                    var vertical: Boolean? = null
                    // Svislý tah v levé půlce videa na celé obrazovce: jas místo zavírání.
                    var dimming: ScreenBrightness? = null
                    var travel = Offset.Zero
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        val delta = change.position - change.previousPosition
                        if (vertical == null) {
                            travel += delta
                            if (travel.getDistance() > viewConfiguration.touchSlop) {
                                val upright = !currentLocked && abs(travel.y) > abs(travel.x) && event.changes.size == 1
                                dimming = currentBrightness?.takeIf {
                                    upright && currentPlayerGestures && !currentBoosting && down.position.x < size.width / 2
                                }
                                vertical = upright && (dimming != null || currentDismiss != null)
                            }
                        }
                        if (vertical == true) {
                            val target = dimming
                            if (target != null) {
                                // Celý rozsah jasu na [BRIGHTNESS_DRAG_SPAN] výšky; nahoru = jasněji.
                                target.set(target.level - delta.y / (size.height * BRIGHTNESS_DRAG_SPAN))
                                brightnessShown = true
                                brightnessRun++
                            } else {
                                scope.launch { dismissDrag.snapTo(dismissDrag.value + delta.y) }
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })

                    if (vertical == true && dimming == null) {
                        val dismiss = currentDismiss
                        if (dismiss != null && abs(dismissDrag.value) > size.height * DISMISS_THRESHOLD) {
                            dismiss()
                        } else {
                            scope.launch { dismissDrag.animateTo(0f) }
                        }
                    }
                }
            }
            /*
             * Tah přes práh není ťuknutí. `detectTapGestures` ruší ťuknutí jen u SPOTŘEBOVANÉHO
             * pohybu, ne podle vzdálenosti — dokud vodorovný tah bral pager, platilo to samo,
             * jenže video na celé obrazovce se nelistuje a švihnutí do strany pak přepnulo do
             * galerie. Spotřebovává se až ve fázi Final: pager (předek) i posuvník (potomek)
             * mají tah za sebou a ťuknutí pod tímhle modifikátorem zjistí, že bylo zrušené.
             */
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                    var dragged = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!dragged) dragged = (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                        if (dragged) event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(spoiler.hidden, playerGestures, toggleWindow != null) {
                val onTapPage: (Offset) -> Unit = {
                    if (spoiler.hidden) {
                        spoiler?.onReveal?.invoke()
                    } else if (currentLocked) {
                        lockHint = !lockHint
                    } else if (currentImmersive && !controlsVisible) {
                        controlsVisible = true
                    } else {
                        currentTap()
                    }
                }
                /*
                 * Dvojí ťuknutí a podržení jen u hrajícího videa na celé obrazovce. Detektor
                 * s dvojím ťuknutím čeká s jednoduchým, jestli nepřijde druhé — v galerii by tak
                 * přepnutí na celou obrazovku zbytečně zdrželo.
                 */
                if (!playerGestures && toggleWindow != null && !spoiler.hidden) {
                    detectTapGestures(
                        onTap = onTapPage,
                        onDoubleTap = { if (!currentLocked) currentToggleWindow?.invoke() },
                    )
                } else if (!playerGestures) {
                    detectTapGestures(onTap = onTapPage)
                } else {
                    detectTapGestures(
                        onTap = onTapPage,
                        onDoubleTap = { offset ->
                            val window = currentToggleWindow
                            if (window != null) window() else seekBy(if (offset.x < size.width / 2) -SEEK_STEP_MS else SEEK_STEP_MS)
                        },
                        onLongPress = { startBoost() },
                        onPress = {
                            tryAwaitRelease()
                            stopBoost()
                        },
                    )
                }
            }
            /*
             * Posun přes `offset`, ne `graphicsLayer`: nativní view s videem (UIKit na iOS)
             * transformace vrstvy nedostane, jen pozici z layoutu. Čte se ve fázi umístění, takže
             * nic nerekomponuje.
             */
            .offset { IntOffset(0, dismissDrag.value.roundToInt()) },
        /*
         * Ovládání přehrávače patří STRÁNCE, ne kartě: posuvník u spodního a zámek u horního okraje
         * obrazovky, jako VLC, YouTube nebo Fotky Google. Video na výšku nebo čtvercové leží na
         * celé obrazovce uprostřed s černými pruhy a ovládání v kartě viselo přes obraz uprostřed
         * displeje; v pruzích obraz nezakrývá. Na kartě zůstává jen to, co patří k obrazu
         * (tlačítko přehrání, průběh, hlášky uprostřed).
         */
        chrome = {
            Box(Modifier.fillMaxSize()) {
                // Podržení: dokud prst leží, hraje se rychleji — a je to vidět.
                AnimatedVisibility(
                    visible = boosting,
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(statusBarArea().union(WindowInsets.displayCutout).only(WindowInsetsSides.Top))
                        .padding(top = 24.dp),
                ) {
                    VideoHintPill(text = stringResource(Res.string.media_speed_boost))
                }

                // Jas tahem: kolik teď je, u horního okraje jako zrychlení podržením.
                AnimatedVisibility(
                    visible = brightnessShown && brightness != null,
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(statusBarArea().union(WindowInsets.displayCutout).only(WindowInsetsSides.Top))
                        .padding(top = 24.dp),
                ) {
                    // Úroveň drží i během mizení, kdy už jas může být vrácený.
                    var lastLevel by remember { mutableFloatStateOf(0f) }
                    brightness?.let { lastLevel = it.level }
                    BrightnessPill(level = { lastLevel })
                }

                // Posuvník leží u spodního okraje stránky s přechodem do tmava — na světlém záběru
                // by bílý posuvník jinak zmizel.
                AnimatedVisibility(
                    visible = immersive && loaded && controlsVisible && !inPip && !locked,
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    modifier = if (tabletopBottom != null) {
                        // Skládačka na stole: uprostřed ležící půlky pod pantem, obraz nezakrývá.
                        val below = with(LocalDensity.current) { (pageHeight - tabletopBottom).coerceAtLeast(0).toDp() }
                        Modifier
                            .align(Alignment.TopCenter)
                            .offset { IntOffset(0, tabletopBottom) }
                            .height(below)
                            .wrapContentHeight(Alignment.CenterVertically)
                    } else {
                        Modifier.align(Alignment.BottomCenter)
                    },
                ) {
                    BoxWithConstraints(
                        Modifier
                            .fillMaxWidth()
                            .then(
                                // Pod pantem leží ovládání na černé, ne přes obraz — přechod netřeba.
                                if (tabletopBottom != null) {
                                    Modifier
                                } else {
                                    Modifier.background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
                                        ),
                                    )
                                },
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
                                    .only(WindowInsetsSides.Bottom)
                                    // Na šířku leží navigační lišta nebo výřez po straně — lišta jde přes celou šířku stránky.
                                    .union(WindowInsets.navigationBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)),
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        val fraction = seekingFraction
                            ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                        // Kolik je staženo — dřív zvláštní kroužek v rohu karty, teď na dráze posuvníku.
                        val buffered = bufferedSpan(file)
                        val haptics = LocalHapticFeedback.current
                        // Tahem se tiká po dílcích, ne po pixelech — jako po sloupcích waveformu hlasovky.
                        var tickedStep by remember { mutableIntStateOf(-1) }
                        val seekBar: @Composable RowScope.() -> Unit = {
                            Text(
                                text = formatDuration(((fraction * durationMs) / 1000).toInt()),
                                style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                                color = Color.White,
                            )
                            // Vlastní dráhu bere jen varianta se stavem (od material3 1.13 už ne ta
                            // s `value`); stav se srovná s polohou videu stejně, jako to ta s `value` dělá uvnitř.
                            val sliderState = remember { SliderState(fraction) }
                            sliderState.value = fraction
                            Slider(
                                state = sliderState,
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
                                 * `docs/poznatky/prohlizec-medii.md`, „Video"). Přechod amplitudy animuje indikátor sám.
                                 */
                                track = { sliderState ->
                                    VideoSeekTrack(
                                        progress = { sliderState.value },
                                        buffered = buffered,
                                        wavy = playing && seekingFraction == null,
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = formatDuration((durationMs / 1000).toInt()),
                                style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                                color = Color.White,
                            )
                        }
                        val extras: @Composable RowScope.() -> Unit = {
                            quality?.let { menu ->
                                VideoQualityChip(
                                    menu = menu,
                                    expanded = qualityMenuOpen,
                                    onExpandedChange = { open ->
                                        qualityMenuOpen = open
                                        controlsVisible = true
                                    },
                                )
                            }
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
                            mute?.onToggle?.let { toggle ->
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
                        /*
                         * Na úzké stránce (telefon na výšku) se do jednoho řádku s časy a tlačítky
                         * posuvník nevejde — zbyl by mu palec. Pak má vlastní řádek přes celou šířku
                         * a tlačítka jdou pod něj doprava.
                         */
                        val spacing = Arrangement.spacedBy(12.dp)
                        val width = maxWidth
                        Column {
                            /*
                             * Při tahu po posuvníku snímek z místa pod prstem (storyboard od serveru).
                             * Jede nad posuvníkem s prstem a u okrajů se zastaví, aby nevyjel z karty.
                             */
                            val seeking = seekingFraction
                            if (board != null && seeking != null) {
                                val thumbWidth = STORYBOARD_THUMB_WIDTH.coerceAtMost(width)
                                StoryboardThumb(
                                    storyboard = board,
                                    positionMs = (seeking * durationMs).toLong(),
                                    modifier = Modifier
                                        .padding(bottom = 8.dp)
                                        .offset(x = (width - thumbWidth) * seeking)
                                        .size(thumbWidth, thumbWidth * board.tileHeight / board.tileWidth),
                                )
                            }
                            if (width >= VIDEO_CONTROLS_ONE_LINE_WIDTH) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = spacing) {
                                    seekBar()
                                    extras()
                                }
                            } else {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = spacing) { seekBar() }
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                                ) { extras() }
                            }
                        }
                    }
                }

                if (fullscreen != null) {
                    VideoTopControls(
                        visible = immersive && loaded && !inPip && if (locked) lockHint else controlsVisible,
                        locked = locked,
                        onLockChange = { on ->
                            fullscreen.onLockChange(on)
                            // Odemčení vrací ovládání hned; zamčení ho schová (podmínka `!locked`).
                            if (!on) controlsVisible = true
                        },
                        onRotate = fullscreen.onRotate,
                        window = fullscreen.window,
                        onWindowChange = fullscreen.onWindowChange,
                    )
                }
            }
        },
    ) {
        // Zaoblení se ořezává v layoutu, ne ve vrstvě: nativní view s videem (UIKit) ořez
        // vrstvy nedostane. Na celé obrazovce je nulové, takže přechod se kreslí jen nad náhledem.
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(MEDIA_CARD_CORNER * (1f - immersion())))
                // Tah myší po obrazu vytáhne video ven; ovládání (posuvník, tlačítka) je nad kartou.
                .then(if (dragOut != null) Modifier.fileDragSource(dragOut) else Modifier),
        ) {
            thumbnail()
            // V galerii jen náhled; povrch se složí až na celé obrazovce.
            if (loaded && immersive) {
                VideoSurface(player, Modifier.fillMaxSize())
            }

            /*
             * Velký průběh uprostřed jen DO ROZBĚHNUTÍ videa. U streamovaného videa je načtené
             * hned, jenže přehrávač ještě čeká na data — procento staženého souboru by tam lhalo
             * (hraje se dávno před 100 %), proto se po načtení točí neurčitě. Jakmile video hraje,
             * stažený úsek ukazuje dráha posuvníku ([VideoSeekTrack]) a uprostřed je ovládání.
             */
            if (immersive && started && (!hasPlayed || swapping) && !failed && file !is FileState.Failed) {
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
            // Chyba přehrávače (ne stahování): pod tlačítkem, které to zkusí znovu.
            if (immersive && failed && file !is FileState.Failed && !inPip) {
                Text(
                    text = stringResource(Res.string.media_video_playback_failed),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = IconButtonDefaults.largeContainerSize().height + 48.dp)
                        .padding(horizontal = 24.dp),
                )
            }

            // Skok dvojím ťuknutím: u okraje, na který se ťuklo, kolik se celkem přeskočilo.
            AnimatedVisibility(
                visible = seekShown != 0L && !inPip,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
                modifier = Modifier
                    .align(if (seekShown < 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 32.dp),
            ) {
                // Poslední směr drží i během mizení, kdy už je `seekShown` nula.
                var lastSeek by remember { mutableLongStateOf(0L) }
                if (seekShown != 0L) lastSeek = seekShown
                VideoHintPill(
                    text = stringResource(
                        if (lastSeek < 0) Res.string.media_seek_back else Res.string.media_seek_forward,
                        (abs(lastSeek) / 1000).toInt(),
                    ),
                )
            }
            // Pokračuje se z pamatované pozice: říct to a nabídnout začátek (`resumedFromMs`).
            AnimatedVisibility(
                visible = resumeHint && immersive && !inPip && !locked,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = IconButtonDefaults.largeContainerSize().height + 72.dp),
            ) {
                Row(
                    Modifier
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f), CircleCornerShape)
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.media_video_resumed, formatDuration(((progress.resumedFromMs ?: 0L) / 1000).toInt())),
                        style = MaterialTheme.typography.labelLarge.tabularNumbers(),
                        color = Color.White,
                    )
                    TextButton(
                        onClick = {
                            player.seekTo(0)
                            ended = false
                            resumeHint = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(Res.string.media_video_from_start))
                    }
                }
            }

            // Tlačítko přehrání v galerii: jen přepne na celou obrazovku, video se spustí tam.
            AnimatedVisibility(
                visible = !immersive && !spoiler.hidden,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
                modifier = Modifier.align(Alignment.Center),
            ) {
                FilledIconButton(
                    onClick = gestures.onEngage,
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

            if (spoiler != null) {
                SpoilerOverlay(
                    visible = !spoiler.revealed,
                    modifier = Modifier.fillMaxSize(),
                    sensitive = spoiler.sensitive,
                )
            }

            AnimatedVisibility(
                visible = immersive && loaded && (hasPlayed || failed) && !swapping && controlsVisible && !inPip && !locked,
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

            if (!inPip) overlay()
        }
    }
}

/**
 * Ovládání celé obrazovky, které drží prohlížeč, ne stránka: zámek musí vypnout i listování
 * a vynucená orientace má platit i pro další video.
 */
internal class VideoFullscreen(
    val locked: Boolean,
    val onLockChange: (Boolean) -> Unit,
    /** Otočí obrazovku do druhé orientace; `null`, kde ji platforma vynutit neumí. */
    val onRotate: (() -> Unit)?,
    /** Okno je přes celou obrazovku na žádost prohlížeče (desktop). */
    val window: Boolean = false,
    /** Přepne okno přes celou obrazovku; `null`, kde to platforma nemá (telefon). */
    val onWindowChange: ((Boolean) -> Unit)? = null,
)

/**
 * Odkud se video přehrává: stav souboru a jak se z rozestavěného streamuje. U zprávy se
 * stahuje z TDLib (i zvolená kvalita), u sdílení je soubor celý na disku.
 *
 * `@Immutable`: po vzniku se nic nemění, jen [key] je typu `Any?` a kompilátor by třídu vedl
 * jako nestabilní — porovnávala by se identitou a stránka by se nepřeskočila nikdy.
 */
@Immutable
internal data class VideoFileSource(
    /** Stav souboru s videem; `started` říká, že se má začít stahovat. */
    val state: @Composable (started: Boolean) -> FileState,
    /** Smí se přehrávat rozestavěný soubor ([FileState.Downloading]). */
    val streamable: Boolean,
    /** Stahovat od daného bajtu — přehrávač chce index na konci souboru. */
    val requestFrom: (offset: Long) -> Unit,
    /**
     * Stáhne a přečte HLS seznam, bez kterého se rozestavěný soubor streamovat nedá
     * ([videoFragmentedStreamingNeedsHls]); `null` = seznam není potřeba. Vrátí-li `null`, čeká
     * se na celý soubor.
     */
    val hlsPlaylist: (suspend () -> String?)? = null,
    /**
     * Který soubor [state] právě dodává (u zprávy `fileId` zvolené kvality). Když se změní
     * u načteného videa, přehrávač vymění zdroj **na téže pozici** a hraje dál — viz „Výměna
     * kvality" ve [VideoPage].
     */
    val key: Any? = null,
)

/** Odkud video začne a kam hlásí, kde se přestalo sledovat. */
internal data class VideoProgress(
    /** Po načtení skočit sem (ms) — čas v médiu; nula = od začátku. */
    val startMs: Long = 0L,
    /**
     * [startMs] je pamatovaná pozice, ne žádost: po rozběhnutí se na chvíli ukáže „Pokračuje od…"
     * s tlačítkem Od začátku. Bez něj by video hrálo uprostřed a nebylo by jasné proč.
     */
    val resumedFromMs: Long? = null,
    /**
     * Kde se sledování zastavilo (pozice, délka v ms) — při pozastavení stránkou, odchodu,
     * dohrání a průběžně během hraní. `null` = nepamatuje se.
     */
    val onProgress: ((positionMs: Long, durationMs: Long) -> Unit)? = null,
)

/**
 * Ztlumení za běhu a tlačítko v liště. Drží ho volající (prohlížeč), ne stránka: ztlumené video
 * by jinak po odlistování na další zase hrálo nahlas.
 */
internal data class VideoMute(
    val muted: Boolean,
    val onToggle: () -> Unit,
)

/** Smí video do obrazu v obraze (tlačítko v liště, samočinný přechod při odchodu z aplikace). */
internal data class VideoPictureInPicture(
    /** Okénko obrazu v obraze se vrací do prohlížeče na téže zprávě v čase (ms); desktop. */
    val onReturn: ((positionMs: Long) -> Unit)? = null,
)

/** Zvolená kvalita a její změna; drží je prohlížeč, aby platily i pro další videa (jako ztlumení). */
@Immutable
internal data class VideoQualitySelection(
    val choice: VideoQualityChoice,
    val onChoose: (VideoQualityChoice) -> Unit,
)

/** Povel z klávesnice pro vybrané video; prohlížeč ho posílá stránce, která má přehrávač. */
internal sealed interface VideoCommand {
    data object PlayPause : VideoCommand

    data class Seek(val deltaMs: Long) : VideoCommand
}

/**
 * Zámek a otočení v levém horním rohu stránky.
 *
 * - **Dole místo není**: čas, posuvník, rychlost, obraz v obraze a zvuk. S dalšími dvěma
 *   tlačítky by posuvníku na telefonu na výšku zbyl palec.
 * - **Zamčené video ukazuje jen zámek**, vyplněný: ťuknutí kamkoli ho vytáhne, ťuknutí na něj
 *   odemkne. Stav nese výplň, ne jen tvar ikony.
 * - **Roh ustupuje stavové liště a výřezu, jen kam stránka opravdu sahá** (insety samy nevědí,
 *   kde prvek leží, takže se porovnávají s polohou v okně).
 *   Bere se místo lišty i schované ([statusBarArea]): vrací se spolu s ovládáním a tlačítka by
 *   jinak během jejího vyjetí poskočila.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BoxScope.VideoTopControls(
    visible: Boolean,
    locked: Boolean,
    onLockChange: (Boolean) -> Unit,
    onRotate: (() -> Unit)?,
    window: Boolean,
    onWindowChange: ((Boolean) -> Unit)?,
) {
    val density = LocalDensity.current
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val safe = statusBarArea()
        .union(WindowInsets.displayCutout)
        .union(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
    var insetTop by remember { mutableFloatStateOf(0f) }
    var insetStart by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
        modifier = Modifier
            .align(Alignment.TopStart)
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                val windowWidth = coordinates.findRootCoordinates().size.width
                insetTop = (safe.getTop(density) - bounds.top).coerceAtLeast(0f)
                insetStart = if (ltr) {
                    safe.getLeft(density, LayoutDirection.Ltr) - bounds.left
                } else {
                    safe.getRight(density, LayoutDirection.Rtl) - (windowWidth - bounds.right)
                }.coerceAtLeast(0f)
            },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f), Color.Transparent),
                    ),
                )
                .padding(
                    start = with(density) { insetStart.toDp() },
                    top = with(density) { insetTop.toDp() },
                )
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconToggleButton(
                checked = locked,
                onCheckedChange = { on ->
                    haptics.toggle(on)
                    onLockChange(on)
                },
                colors = IconButtonDefaults.iconToggleButtonColors(
                    contentColor = Color.White,
                    checkedContainerColor = Color.White,
                    checkedContentColor = Color.Black,
                ),
                shapes = IconButtonDefaults.toggleableShapes(),
            ) {
                Icon(
                    painterResource(if (locked) Res.drawable.lock_24dp else Res.drawable.lock_open_24dp),
                    contentDescription = stringResource(if (locked) Res.string.media_unlock_controls else Res.string.media_lock_controls),
                )
            }
            if (onRotate != null && !locked) {
                IconButton(
                    onClick = onRotate,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        painterResource(Res.drawable.screen_rotation_24dp),
                        contentDescription = stringResource(Res.string.media_rotate_screen),
                    )
                }
            }
            if (onWindowChange != null && !locked) {
                IconButton(
                    onClick = { onWindowChange(!window) },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        painterResource(if (window) Res.drawable.fullscreen_exit_24dp else Res.drawable.fullscreen_24dp),
                        contentDescription = stringResource(if (window) Res.string.media_fullscreen_exit else Res.string.media_fullscreen_enter),
                    )
                }
            }
        }
    }
}

/**
 * Jas při tahu: ikona slunce a úroveň, bílé na tmavé pilulce jako [VideoHintPill]. Úroveň je
 * tentýž indikátor jako posuvník videa ([VideoSeekTrack]: tloušťka, mezera, zakončení), jen
 * rovný — vlna tam znamená „hraje", jas se nehýbe. Dřív tu byl tenký hladký pruh z jiné rodiny.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BrightnessPill(level: () -> Float) {
    Row(
        Modifier
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f), CircleCornerShape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painterResource(Res.drawable.light_mode_24dp),
            contentDescription = stringResource(Res.string.media_brightness),
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
        LinearWavyProgressIndicator(
            progress = level,
            color = Color.White,
            trackColor = TRACK_COLOR,
            amplitude = { 0f },
            modifier = Modifier.width(BRIGHTNESS_BAR_WIDTH),
        )
    }
}

/** Krátká hláška přes video (skok, zrychlení): bílý text na tmavé pilulce. */
@Composable
private fun VideoHintPill(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.tabularNumbers(),
        color = Color.White,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f), CircleCornerShape)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private val CircleCornerShape = androidx.compose.foundation.shape.CircleShape

private const val POSITION_TICK_MS = 200L
private const val PROGRESS_SAVE_MS = 10_000L
private const val RESUME_HINT_MS = 6_000L
private const val SEEK_STEP_MS = 10_000L
private const val SEEK_HINT_MS = 800L
private const val BOOST_RATE = 2f

/** Jakou část výšky stránky projede prst od tmy po plný jas. */
private const val BRIGHTNESS_DRAG_SPAN = 0.6f
private val BRIGHTNESS_BAR_WIDTH = 96.dp

/** Šířka náhledu snímku nad posuvníkem; výška podle dlaždice. */
private val STORYBOARD_THUMB_WIDTH = 160.dp
private const val CONTROLS_HIDE_MS = 3000L

/** Na kolik dílků se posuvník videa dělí, když tiká haptika (po pětinách procenta by bzučel). */
private const val VIDEO_SEEK_STEPS = 20

/**
 * Od jaké šířky stránky se posuvník vejde do jednoho řádku s časy a tlačítky. Časy, rychlost,
 * obraz v obraze a zvuk zaberou kolem 320 dp; posuvníku má zbýt aspoň půlka telefonu.
 */
private val VIDEO_CONTROLS_ONE_LINE_WIDTH = 480.dp

private val logger = Logger.withTag("VideoQuality")

/** Kvalita do logu: `fileId=šířka×výška/kodek/bajty`. */
private fun VideoQuality.logLabel(): String = "$fileId=${width}x$height/${codec ?: "orig"}/$sizeBytes"

/**
 * Stáhne malý textový soubor (HLS seznam) a přečte ho; `null`, když se stáhnout nebo přečíst
 * nepodařil.
 */
private suspend fun FileRepository.readText(fileId: Int): String? {
    val ready = file(fileId).first { it is FileState.Ready || it is FileState.Failed } as? FileState.Ready
        ?: return null
    return withContext(Dispatchers.Default) {
        runCatching { FileSystem.SYSTEM.read(ready.path.toPath()) { readUtf8() } }
            .onFailure { logger.w(it) { "Seznam HLS $fileId nejde přečíst." } }
            .getOrNull()
    }
}
