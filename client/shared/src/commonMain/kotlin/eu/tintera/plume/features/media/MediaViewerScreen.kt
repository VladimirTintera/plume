package eu.tintera.plume.features.media

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.photoKey
import eu.tintera.plume.core.ui.sharedNavElement
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.close_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs
import kotlin.math.max

/**
 * Fotka přes celou obrazovku.
 *
 * Pozadí je **tmavé v obou režimech** (`scrim`). Fotka potřebuje neutrální podklad, na kterém
 * nezáleží na tom, jestli je aplikace ve světlém, nebo tmavém tématu — světlá plocha kolem
 * snímku mění jeho vnímanou expozici.
 */
@Composable
fun MediaViewerScreen(
    viewModel: MediaViewerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    /**
     * Svislé tažení zavírající prohlížeč.
     *
     * Drží se jako [Animatable], aby se dalo po puštění vrátit zpět. Čte se **jen ve fázi
     * kreslení** (`drawBehind`, `graphicsLayer`), takže tah nic nerekomponuje.
     */
    val dismissDrag = remember { Animatable(0f) }
    val scrim = MaterialTheme.colorScheme.scrim

    Box(
        modifier
            .fillMaxSize()
            // Pozadí prosvítá, jak se fotka odtahuje pryč — je tím vidět, že gesto něco dělá,
            // ještě než se pustí.
            .drawBehind {
                val progress = (abs(dismissDrag.value) / size.height * DISMISS_FADE).coerceIn(0f, 1f)
                drawRect(scrim.copy(alpha = 1f - progress))
            },
    ) {
        when {
            state.error != null -> Text(
                text = state.error.orEmpty(),
                color = ON_SCRIM,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )

            state.items.isNotEmpty() -> MediaPager(
                state = state,
                dismissDrag = dismissDrag,
                onPageChanged = viewModel::onPageChanged,
                onDismiss = onBack,
            )

            state.loading -> LoadingIndicator(Modifier.align(Alignment.Center))
        }

        // Bez fotek (chyba, načítání) zůstává aspoň cesta ven.
        if (state.items.isEmpty()) CloseButton(onBack, Modifier.align(Alignment.TopStart))
    }
}

/**
 * Listování fotkami chatu.
 *
 * **Starší vlevo, novější vpravo** — jako čas na ose, a stejně jako v konverzaci, kde novější
 * zprávy přibývají dál. Pager drží stránku podle klíče (id zprávy), takže když se vlevo dotáhnou
 * starší fotky, uživateli se pod prstem nic nepohne, jen se posune index.
 */
@Composable
private fun BoxScope.MediaPager(
    state: MediaViewerState,
    dismissDrag: Animatable<Float, *>,
    onPageChanged: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val photos = state.items
    val initialIndex = remember { photos.indexOfFirst { it.messageId == state.initialMessageId }.coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = initialIndex) { photos.size }

    val latestPhotos by rememberUpdatedState(photos)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect(onPageChanged)
    }

    HorizontalPager(
        state = pagerState,
        key = { photos[it].messageId },
        // Soused se skládá dopředu, ať při listování naskočí hned i s už staženým náhledem.
        beyondViewportPageCount = 1,
        pageSpacing = 16.dp,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        val item = photos[page]
        when (val media = item.media) {
            is MessageMedia.Video -> VideoPage(
                video = media,
                messageId = item.messageId,
                caption = item.caption,
                isCurrent = page == pagerState.currentPage,
                dismissDrag = dismissDrag,
                onDismiss = onDismiss,
            )

            is MessageMedia.Photo -> ZoomableImage(
                photo = media,
                messageId = item.messageId,
                isCurrent = page == pagerState.currentPage,
                dismissDrag = dismissDrag,
                onDismiss = onDismiss,
            )

            else -> Unit
        }
    }

    val current = latestPhotos.getOrNull(pagerState.currentPage)

    val scrim = MaterialTheme.colorScheme.scrim
    Row(
        Modifier
            .align(Alignment.TopStart)
            .fillMaxWidth()
            // Přechod, ne pruh: bílé ovládání by se na světlé fotce (screenshot) ztratilo,
            // plný pruh by ale fotku nahoře ořízl. Přechod zabere jen tolik, kolik je potřeba.
            .background(Brush.verticalGradient(listOf(scrim.copy(alpha = TOP_SCRIM_ALPHA), Color.Transparent)))
            .statusBarsPadding()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CloseButton(onDismiss)

        // Počítadlo se odvozuje od pozice otevřené fotky posunem indexu: výřez je souvislý.
        val position = state.initialPosition
        if (position != null && current != null) {
            val initial = photos.indexOfFirst { it.messageId == state.initialMessageId }
            val number = position.number + (pagerState.currentPage - initial)
            if (initial >= 0 && number in 1..position.total) {
                Text(
                    text = "$number z ${position.total}",
                    style = MaterialTheme.typography.titleMedium,
                    color = ON_SCRIM,
                )
            }
        }
    }

    // Video si popisek kreslí samo, pod ovládáním.
    if (current != null && current.media is MessageMedia.Photo && current.caption.isNotBlank()) {
        Text(
            text = current.caption,
            style = MaterialTheme.typography.bodyMedium,
            color = ON_SCRIM,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                // Vlastní závoj pod textem: popisek leží na fotce, která pod ním může být
                // světlá i tmavá, a bez podkladu by se v ní ztratil.
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = CAPTION_SCRIM_ALPHA))
                .padding(horizontal = 20.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier.padding(4.dp)) {
        Icon(
            painterResource(Res.drawable.close_24dp),
            contentDescription = "Zavřít",
            tint = ON_SCRIM,
        )
    }
}

/**
 * Fotka s přiblížením.
 *
 * Gesta jsou dvě a **každé má vlastní `pointerInput`**: tah a štípnutí, a ťuknutí (dvojité
 * přiblížení).
 *
 * Tah se nerozpoznává přes `detectTransformGestures`. Ten konzumuje pohyb v každém směru,
 * takže by fotka spolkla i vodorovný tah a pager nad ní by nešel posunout. Proto vlastní
 * rozhodnutí po překročení slopu: nepřiblížená fotka **vodorovný tah pustí pageru**, svislý
 * si nechá na zavření; přiblížená si nechá všechno (posun po fotce). Listovat se tedy dá jen
 * s fotkou v základní velikosti — posun po přiblížené fotce by se jinak pral s listováním.
 *
 * Posun i přiblížení se čtou **až v `graphicsLayer`**, takže se při gestu nic nerekomponuje;
 * překresluje se jen vrstva s obrázkem.
 */
@Composable
private fun ZoomableImage(
    photo: MessageMedia.Photo,
    messageId: Long,
    /** Stránka, na které pager stojí. Odlistovaná fotka se vrací do základní velikosti. */
    isCurrent: Boolean,
    dismissDrag: Animatable<Float, *>,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Sousední stránka zůstává složená; kdyby si přiblížení nechala, vrátil by se uživatel
    // k fotce v půlce výřezu a nešlo by z ní listovat dál.
    LaunchedEffect(isCurrent) {
        if (!isCurrent) {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportWidth = constraints.maxWidth.toFloat()
        val viewportHeight = constraints.maxHeight.toFloat()

        /*
         * Skutečná vykreslená velikost fotky při základním přiblížení.
         *
         * Počítá se z poměru stran v metadatech zprávy, ne z načteného souboru — jinak by se
         * meze posunu měnily ve chvíli, kdy dorazí ostrá verze. `ContentScale.Fit` dělá totéž
         * co tenhle výpočet, takže sedí.
         */
        val ratio = if (photo.width > 0 && photo.height > 0) {
            photo.width.toFloat() / photo.height.toFloat()
        } else {
            1f
        }
        val fittedWidth: Float
        val fittedHeight: Float
        if (viewportWidth / viewportHeight > ratio) {
            fittedHeight = viewportHeight
            fittedWidth = viewportHeight * ratio
        } else {
            fittedWidth = viewportWidth
            fittedHeight = viewportWidth / ratio
        }

        /** Meze posunu: přes okraj přiblížené fotky se ven dostat nedá. */
        fun clampOffset(x: Float, y: Float, forScale: Float): Offset {
            val maxX = max(0f, (fittedWidth * forScale - viewportWidth) / 2f)
            val maxY = max(0f, (fittedHeight * forScale - viewportHeight) / 2f)
            return Offset(x.coerceIn(-maxX, maxX), y.coerceIn(-maxY, maxY))
        }

        RemoteImage(
            // Ostrá verze; zmenšenina z bubliny slouží jako okamžitý náhled, než se dotáhne.
            // Ta je skoro vždycky už na disku, protože ji kreslila konverzace.
            fileId = photo.fullFileId,
            thumbnailFileId = photo.fileId,
            contentDescription = "Fotka",
            contentScale = ContentScale.Fit,
            background = Color.Transparent,
            modifier = Modifier
                .fillMaxSize()
                .sharedNavElement(photoKey(messageId))
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY + dismissDrag.value
                    // Při zavíracím tahu se fotka zmenšuje, takže je vidět, že odchází pryč.
                    val shrink = 1f - (abs(dismissDrag.value) / size.height).coerceIn(0f, 1f) * 0.2f
                    if (scale == 1f) {
                        scaleX = shrink
                        scaleY = shrink
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var gesture = Gesture.Undecided
                        var travel = Offset.Zero

                        do {
                            val event = awaitPointerEvent()
                            val fingers = event.changes.count { it.pressed }
                            val pan = event.calculatePan()

                            if (gesture == Gesture.Undecided) {
                                travel += pan
                                val pastSlop = travel.getDistance() > viewConfiguration.touchSlop
                                gesture = when {
                                    // Dva prsty jsou štípnutí vždycky, i bez slopu.
                                    fingers > 1 -> Gesture.Transform
                                    !pastSlop -> Gesture.Undecided
                                    scale > 1f -> Gesture.Transform
                                    abs(travel.y) > abs(travel.x) -> Gesture.Dismiss
                                    // Vodorovně u nepřiblížené fotky: listování, patří pageru.
                                    else -> Gesture.Page
                                }
                            }

                            when (gesture) {
                                Gesture.Transform -> {
                                    val zoom = event.calculateZoom()
                                    val centroid = event.calculateCentroid(useCurrent = false)
                                    val next = (scale * zoom).coerceIn(1f, MAX_SCALE)

                                    if (centroid != Offset.Unspecified && !(next == 1f && scale == 1f)) {
                                        /*
                                         * Přiblížení kolem prstů, ne kolem středu.
                                         *
                                         * Bod pod prsty musí zůstat na místě, jinak fotka při
                                         * štípnutí ujíždí a uživatel ji pak dohání posunem.
                                         */
                                        val relativeX = centroid.x - viewportWidth / 2f
                                        val relativeY = centroid.y - viewportHeight / 2f
                                        val factor = next / scale

                                        val clamped = clampOffset(
                                            x = relativeX - (relativeX - offsetX) * factor + pan.x,
                                            y = relativeY - (relativeY - offsetY) * factor + pan.y,
                                            forScale = next,
                                        )

                                        scale = next
                                        offsetX = clamped.x
                                        offsetY = clamped.y
                                    }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }

                                Gesture.Dismiss -> {
                                    scope.launch { dismissDrag.snapTo(dismissDrag.value + pan.y) }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }

                                Gesture.Undecided, Gesture.Page -> Unit
                            }
                        } while (event.changes.any { it.pressed })

                        if (gesture == Gesture.Dismiss) {
                            if (abs(dismissDrag.value) > size.height * DISMISS_THRESHOLD) {
                                onDismiss()
                            } else {
                                scope.launch { dismissDrag.animateTo(0f) }
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            val target = if (scale > 1f) 1f else DOUBLE_TAP_SCALE
                            val relativeX = tap.x - viewportWidth / 2f
                            val relativeY = tap.y - viewportHeight / 2f
                            val factor = target / scale

                            val destination = if (target == 1f) {
                                Offset.Zero
                            } else {
                                clampOffset(
                                    x = relativeX - (relativeX - offsetX) * factor,
                                    y = relativeY - (relativeY - offsetY) * factor,
                                    forScale = target,
                                )
                            }

                            val fromScale = scale
                            val fromX = offsetX
                            val fromY = offsetY

                            scope.launch {
                                // Jedna animace řídí všechny tři hodnoty, aby se posun
                                // nerozešel s přiblížením a fotka po cestě neuhýbala.
                                animate(0f, 1f) { progress, _ ->
                                    scale = fromScale + (target - fromScale) * progress
                                    offsetX = fromX + (destination.x - fromX) * progress
                                    offsetY = fromY + (destination.y - fromY) * progress
                                }
                            }
                        },
                    )
                }
        )
    }
}

/** Co právě dělá prst na fotce. Rozhoduje se jednou za gesto, po překročení slopu. */
private enum class Gesture { Undecided, Transform, Dismiss, Page }

/**
 * Barva ovládání nad fotkou.
 *
 * Schválně natvrdo: podklad je vždycky tmavý závoj, takže role z `colorScheme` by ve světlém
 * tématu dala tmavý text na tmavé ploše.
 */
private val ON_SCRIM = Color.White

/** Jak daleko od horní hrany dolů (v podílu výšky) musí tah zajít, aby se prohlížeč zavřel. */
private const val DISMISS_THRESHOLD = 0.18f

/** Jak rychle při zavíracím tahu prosvítá obsah pod prohlížečem. */
private const val DISMISS_FADE = 2.5f

/** Strop přiblížení. Nad tím jsou z fotky jen pixely. */
private const val MAX_SCALE = 5f

/** Kam skočí dvojité ťuknutí. */
private const val DOUBLE_TAP_SCALE = 2.5f

/** Průsvit přechodu pod zavíracím tlačítkem a počítadlem, u horní hrany. */
private const val TOP_SCRIM_ALPHA = 0.6f

/** Průsvit pruhu pod popiskem. */
private const val CAPTION_SCRIM_ALPHA = 0.55f
