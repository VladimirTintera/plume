package eu.tintera.plume.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import coil3.compose.AsyncImagePainter
import coil3.memory.MemoryCache
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.photo_crop_done
import eu.tintera.plume.resources.photo_crop_failed
import eu.tintera.plume.resources.photo_crop_hint
import eu.tintera.plume.resources.photo_crop_title
import eu.tintera.plume.resources.photo_crop_video_note
import eu.tintera.plume.resources.photo_crop_video_title
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.min

/**
 * Ořez profilové fotky: fotka pod kruhovým výřezem, posun a přiblížení prsty, „Hotovo" vrátí
 * výřez ([CropRegion]). Ořezává až platforma ([SquarePhoto]) — tady se jen vybírá.
 *
 * **Překryv přes celou obrazovku, ne `Dialog`.** Dialog je na Androidu vlastní okno a ve
 * společném kódu nejde nastavit, aby sahal pod stavovou lištu; nad černým ořezem by svítil
 * pruh ztmavené obrazovky. Stejná cesta jako náhled přílohy nad vstupním polem — obrazovka
 * překryv dává za svůj obsah.
 *
 * [path] `null` = zavřeno; při zavírání se dál kreslí poslední fotka, ať prolnutí nedobíhá
 * s prázdnem. [video]: v ořezu je snímek videa ([ProfileVideo.frame]) — výřez platí pro celé
 * video, pod ořezem je pás s úsekem a snímkem pro statickou fotku ([VideoTrimBar]) a ořez
 * ukazuje právě ten snímek, takže se výřez vybírá nad tím, co bude fotkou.
 */
@Composable
fun PhotoCropOverlay(
    path: String?,
    onCancel: () -> Unit,
    onConfirm: (CropRegion) -> Unit,
    video: VideoCropControl? = null,
) {
    var last by remember { mutableStateOf<String?>(null) }
    var lastVideo by remember { mutableStateOf<VideoCropControl?>(null) }
    LaunchedEffect(path, video) {
        if (path != null) {
            last = path
            lastVideo = video
        }
    }
    // `NavigationBackHandler`, ne `BackHandler` — ten by zabil predictive back.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = path != null,
        onBackCompleted = onCancel,
    )
    AnimatedVisibility(visible = path != null, enter = fadeIn(), exit = fadeOut()) {
        val shown = path ?: last
        if (shown != null) {
            PhotoCrop(
                shown,
                video = if (path != null) video else lastVideo,
                isOpen = path != null,
                onCancel = onCancel,
                onConfirm = onConfirm,
            )
        }
    }
}

@Composable
private fun PhotoCrop(
    path: String,
    video: VideoCropControl?,
    isOpen: Boolean,
    onCancel: () -> Unit,
    onConfirm: (CropRegion) -> Unit,
) {
    val isVideo = video != null
    // Podklad je černý v obou motivech, takže i ikony systémových lišt musí být světlé.
    if (isOpen) DarkSystemBars()
    val context = LocalPlatformContext.current

    // U videa se ukazuje snímek, který bude statickou fotkou. Nový se vytáhne až po krátké
    // pauze v tahu — na každý pohyb prstu by se dekódovalo zbytečně.
    var shown by remember(path) { mutableStateOf(path) }
    val coverMs = video?.trim?.coverMs
    LaunchedEffect(video, coverMs) {
        if (video == null || coverMs == null) return@LaunchedEffect
        delay(COVER_FRAME_DELAY)
        runCatching { video.frame(coverMs, PROFILE_VIDEO_FRAME_EDGE) }.onSuccess { shown = it }
    }
    // Do načtení nového snímku zůstává vidět ten předchozí (z paměťové keše Coilu), jinak by
    // ořez při každé změně snímku problikl načítáním.
    var lastKey by remember(path) { mutableStateOf<MemoryCache.Key?>(null) }
    // Nejvýš 1600 px: na výběr výřezu stačí a fotka z foťáku by jinak zabrala desítky MB.
    // Na poměru stran se zmenšením nic nemění, a výřez je v podílech.
    val request = remember(shown) {
        ImageRequest.Builder(context)
            .data("file://$shown")
            .size(PREVIEW_EDGE)
            .placeholderMemoryCacheKey(lastKey)
            .build()
    }
    val painter = rememberAsyncImagePainter(request)
    val state by painter.state.collectAsState()
    // Rozměry drží i přes výměnu snímku: snímky téhož videa jsou stejně velké a výřez se nemá
    // na okamžik zavřít do načítání.
    var knownSize by remember(path) { mutableStateOf<Size?>(null) }
    LaunchedEffect(state) {
        val success = state as? AsyncImagePainter.State.Success ?: return@LaunchedEffect
        success.painter.intrinsicSize.takeIf { it.width > 0f && it.height > 0f }?.let { knownSize = it }
        lastKey = success.result.memoryCacheKey
    }
    val imageSize = (state as? AsyncImagePainter.State.Success)?.painter?.intrinsicSize
        ?.takeIf { it.width > 0f && it.height > 0f } ?: knownSize
    var geometry by remember(path) { mutableStateOf<PhotoCropGeometry?>(null) }
    var placement by remember(path) { mutableStateOf(CropPlacement()) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    painterResource(Res.drawable.close_24dp),
                    contentDescription = stringResource(Res.string.common_cancel),
                    tint = Color.White,
                )
            }
            Text(
                text = stringResource(if (isVideo) Res.string.photo_crop_video_title else Res.string.photo_crop_title),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
        }

        // Ořez na plochu: posunutá nebo přiblížená fotka by jinak přetekla přes nadpis
        // i stavovou lištu (`graphicsLayer` na obrázku hranice rodiče neřeší).
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds(), contentAlignment = Alignment.Center) {
            val density = LocalDensity.current
            val circle = with(density) { min(maxWidth.toPx(), maxHeight.toPx()) - 2 * CIRCLE_MARGIN.toPx() }
            when {
                imageSize != null && circle > 0f -> {
                    val current = remember(imageSize, circle) { PhotoCropGeometry(imageSize, circle) }
                    LaunchedEffect(current) {
                        geometry = current
                        placement = current.clamped(placement)
                    }
                    val base = current.displayedSize(1f)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(current) {
                                detectTransformGestures { centroid, pan, zoom, _ ->
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    placement = current.transformed(placement, centroid - center, pan, zoom)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painter,
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .requiredSize(with(density) { base.width.toDp() }, with(density) { base.height.toDp() })
                                .graphicsLayer {
                                    scaleX = placement.scale
                                    scaleY = placement.scale
                                    translationX = placement.offset.x
                                    translationY = placement.offset.y
                                },
                        )
                        CropMask(circle)
                    }
                }
                state is AsyncImagePainter.State.Error -> Text(
                    text = stringResource(Res.string.photo_crop_failed),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                )
                else -> LoadingIndicator()
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (video != null) VideoTrimBar(video, Modifier.fillMaxWidth())
            Text(
                // U videa i to, že se zkrátí a přijde o zvuk, a jak se vybírá úsek a fotka.
                text = stringResource(if (isVideo) Res.string.photo_crop_video_note else Res.string.photo_crop_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
            Button(
                onClick = { geometry?.let { onConfirm(it.region(placement)) } },
                enabled = geometry != null && isOpen,
            ) {
                Text(stringResource(Res.string.photo_crop_done))
            }
        }
    }
}

/** Ztmavení všeho kromě kruhu a jeho obrys — co zůstane ve světle, bude profilovka. */
@Composable
private fun CropMask(circle: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val hole = Rect(center, circle / 2)
        val veil = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, Size(size.width, size.height)))
            addOval(hole)
        }
        drawPath(veil, Color.Black.copy(alpha = VEIL_ALPHA))
        drawCircle(Color.White.copy(alpha = 0.8f), radius = circle / 2, center = center, style = Stroke(1.5.dp.toPx()))
    }
}

private const val PREVIEW_EDGE = 1600
private const val COVER_FRAME_DELAY = 150L
private const val VEIL_ALPHA = 0.6f
private val CIRCLE_MARGIN = 24.dp
