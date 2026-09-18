package eu.tintera.plume.features.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.DarkSystemBars
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.asDayLabel
import eu.tintera.plume.core.ui.asTimeOfDay
import eu.tintera.plume.core.ui.photoKey
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.sharedNavElement
import eu.tintera.plume.features.conversation.MessageDeleteDialog
import eu.tintera.plume.features.conversation.ReactionPickerSheet
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.add_reaction_24dp
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_close
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.forward_24dp
import eu.tintera.plume.resources.media_photo
import eu.tintera.plume.resources.media_position
import eu.tintera.plume.resources.menu_delete
import eu.tintera.plume.resources.menu_forward
import eu.tintera.plume.resources.menu_more_reactions
import eu.tintera.plume.resources.sender_you
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Fotky a videa přes celou obrazovku, po vzoru Google Zpráv.
 *
 * - **Pozadí je rozostřený aktuální snímek** pod tmavým nádechem, ne holá černá. Prohlížeč tím
 *   drží náladu fotky a přitom je dost tmavý, aby bílé ovládání bylo čitelné na jakékoli.
 *   Kde rozostření není (Android 11 a starší), zůstane jen tmavý závoj.
 * - **Snímek je karta se zaoblením** a sousední stránky u okrajů vykukují — je vidět, že se dá
 *   listovat, ještě než se to uživatel zkusí.
 * - **Lišty jsou nad kartou, ne přes ni**: nahoře kdo a kdy fotku poslal a akce, dole reakce.
 *   Ťuknutí na snímek je schová i ukáže.
 */
@Composable
fun MediaViewerScreen(
    viewModel: MediaViewerViewModel,
    onBack: () -> Unit,
    onForward: (messageId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Podklad je tmavý v obou motivech, takže i ikony lišt musí být světlé.
    DarkSystemBars()

    /**
     * Svislé tažení zavírající prohlížeč.
     *
     * Drží se jako [Animatable], aby se dalo po puštění vrátit zpět. Čte se **jen ve fázi
     * kreslení** (`graphicsLayer`), takže tah nic nerekomponuje.
     */
    val dismissDrag = remember { Animatable(0f) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Hláška se ve stavu nuluje hned. Snackbar proto běží mimo efekt — efekt klíčovaný tou
    // hláškou by se vynulováním restartoval a snackbar zrušil (viz „Připnuté zprávy").
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        viewModel.dismissNotice()
        scope.launch { snackbarHostState.showSnackbar(getString(notice.resource, *notice.args.toTypedArray())) }
    }

    LaunchedEffect(state.closed) { if (state.closed) onBack() }

    Box(modifier.fillMaxSize()) {
        if (state.items.isNotEmpty()) {
            ViewerContent(
                state = state,
                dismissDrag = dismissDrag,
                onPageChanged = viewModel::onPageChanged,
                onReact = viewModel::toggleReaction,
                onDelete = viewModel::requestDelete,
                onForward = onForward,
                onDismiss = onBack,
            )
        } else {
            // Bez fotek (chyba, načítání) není co rozostřit; zůstává tmavá plocha a cesta ven.
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim)) {
                when {
                    state.error != null -> Text(
                        text = requireNotNull(state.error).resolve(),
                        color = ON_SCRIM,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )

                    state.loading -> LoadingIndicator(Modifier.align(Alignment.Center))
                }
                CloseButton(onBack, Modifier.align(Alignment.TopStart).statusBarsPadding())
            }
        }

        SnackbarHost(
            snackbarHostState,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = BOTTOM_BAR_HEIGHT),
        )
    }

    state.pendingDelete?.let { pending ->
        MessageDeleteDialog(
            pending = pending,
            partnerName = state.chatTitle,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }
}

/**
 * Pozadí, pager a lišty.
 *
 * **Starší vlevo, novější vpravo** — jako čas na ose, a stejně jako v konverzaci, kde novější
 * zprávy přibývají dál. Pager drží stránku podle klíče (id zprávy), takže když se vlevo dotáhnou
 * starší fotky, uživateli se pod prstem nic nepohne, jen se posune index.
 */
@Composable
private fun ViewerContent(
    state: MediaViewerState,
    dismissDrag: Animatable<Float, *>,
    onPageChanged: (Int) -> Unit,
    onReact: (messageId: Long, emoji: String) -> Unit,
    onDelete: (messageId: Long) -> Unit,
    onForward: (messageId: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val items = state.items
    val initialIndex = remember { items.indexOfFirst { it.messageId == state.initialMessageId }.coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = initialIndex) { items.size }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect(onPageChanged)
    }

    val current = items.getOrNull(pagerState.currentPage)
    var chromeVisible by remember { mutableStateOf(true) }

    // Obsah pod prohlížečem prosvítá, jak se fotka odtahuje pryč — je tím vidět, že gesto něco
    // dělá, ještě než se pustí. Pozadí i lišty mizí spolu.
    val fadeWithDismiss = Modifier.graphicsLayer {
        alpha = 1f - (abs(dismissDrag.value) / size.height * DISMISS_FADE).coerceIn(0f, 1f)
    }

    val density = LocalDensity.current
    val topInset = with(density) { WindowInsets.statusBars.getTop(this).toDp() } + TOP_BAR_HEIGHT
    val bottomInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() } + BOTTOM_BAR_HEIGHT

    BoxWithConstraints(Modifier.fillMaxSize()) {
        AmbientBackground(items, pagerState, Modifier.fillMaxSize().then(fadeWithDismiss))

        // Na širokém displeji má stránka strop, jinak by sousedé leželi daleko za okrajem.
        val pageInset = max(PAGE_INSET.value, ((maxWidth - MAX_PAGE_WIDTH) / 2).value).dp

        HorizontalPager(
            state = pagerState,
            key = { items[it].messageId },
            // Sousední stránky vykukují u okrajů.
            contentPadding = PaddingValues(horizontal = pageInset),
            pageSpacing = PAGE_GAP,
            // Soused se skládá dopředu, ať při listování naskočí hned i s už staženým náhledem.
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items[page]
            val isCurrent = page == pagerState.currentPage
            // Karta leží mezi lištami; stránka sama jde přes celou výšku, aby přiblížená fotka
            // měla kam růst.
            val captionAlpha = { 1f - abs(pagerState.getOffsetDistanceInPages(page)).coerceIn(0f, 1f) }
            /*
             * Karta užší než stránka (fotka na výšku, široký displej) by vycentrovaná ležela daleko
             * od okraje a soused by nebyl vidět vůbec. Sousední karta se proto přisouvá k okraji
             * blíž aktuální stránce — plynule s tahem, takže uprostřed obrazovky stojí vždy na středu.
             */
            val cardBias = { (-pagerState.getOffsetDistanceInPages(page)).coerceIn(-1f, 1f) }
            val pageModifier = Modifier.fillMaxSize().padding(top = topInset + PAGE_VERTICAL_GAP, bottom = bottomInset + PAGE_VERTICAL_GAP)
            when (val media = item.media) {
                is MessageMedia.Video -> VideoPage(
                    video = media,
                    messageId = item.messageId,
                    caption = item.caption,
                    isCurrent = isCurrent,
                    dismissDrag = dismissDrag,
                    onDismiss = onDismiss,
                    onTap = { chromeVisible = !chromeVisible },
                    modifier = pageModifier,
                    captionAlpha = captionAlpha,
                    cardBias = cardBias,
                )

                is MessageMedia.Photo -> ZoomableImage(
                    photo = media,
                    messageId = item.messageId,
                    caption = item.caption,
                    isCurrent = isCurrent,
                    dismissDrag = dismissDrag,
                    onDismiss = onDismiss,
                    onTap = { chromeVisible = !chromeVisible },
                    modifier = pageModifier,
                    captionAlpha = captionAlpha,
                    cardBias = cardBias,
                )

                else -> Unit
            }
        }

        val chromeEnter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec())
        val chromeExit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec())

        AnimatedVisibility(
            visible = chromeVisible && current != null,
            enter = chromeEnter,
            exit = chromeExit,
            modifier = Modifier.align(Alignment.TopStart).then(fadeWithDismiss),
        ) {
            if (current != null) {
                TopBar(
                    item = current,
                    state = state,
                    pagerState = pagerState,
                    onClose = onDismiss,
                    onDelete = { onDelete(current.messageId) },
                    onForward = { onForward(current.messageId) },
                )
            }
        }

        val emoji = current?.let { state.availableReactions[it.messageId] }.orEmpty()
        AnimatedVisibility(
            visible = chromeVisible && current != null && emoji.isNotEmpty(),
            enter = chromeEnter,
            exit = chromeExit,
            modifier = Modifier.align(Alignment.BottomCenter).then(fadeWithDismiss),
        ) {
            if (current != null) {
                ReactionBar(
                    item = current,
                    emoji = emoji,
                    onReact = { onReact(current.messageId, it) },
                )
            }
        }
    }
}

/**
 * Rozostřené snímky přes celou obrazovku, které **jezdí s pagerem**.
 *
 * Kreslí se pozadí aktuální stránky i obou sousedů vedle sebe a posouvají se o tentýž zlomek
 * stránky jako karty (`currentPageOffsetFraction`, čtený až při kreslení). Pozadí se tak mění
 * spolu s tahem, ne skokem až po přeskočení na další stránku. Šev mezi dvěma pozadími změkčí
 * samo rozostření — Haze rozmazává složený obraz, ne každý snímek zvlášť.
 *
 * Bere se **malý náhled**, ne ostrá verze: pod rozostřením detail nic nepřidá a náhled je na
 * disku skoro vždycky, protože ho kreslila konverzace. Rozostřuje Haze jako závoj pod nabídkou
 * nad zprávou — ten má i náhradu pro Android 11 a starší, kde se kreslí jen tmavý závoj.
 */
@Composable
private fun AmbientBackground(items: List<ViewerItem>, pagerState: PagerState, modifier: Modifier = Modifier) {
    val hazeState = rememberHazeState()
    val scrim = MaterialTheme.colorScheme.scrim
    val style = remember(scrim) {
        HazeBlurStyle {
            colorEffects(listOf(HazeColorEffect.tint(scrim.copy(alpha = AMBIENT_TINT_ALPHA))))
            blurRadius(AMBIENT_BLUR_RADIUS)
            noiseFactor(0f)
            fallbackColorEffect(HazeColorEffect.tint(scrim.copy(alpha = AMBIENT_FALLBACK_ALPHA)))
        }
    }
    val current = pagerState.currentPage

    Box(modifier.background(scrim)) {
        Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
            for (page in (current - 1)..(current + 1)) {
                val item = items.getOrNull(page) ?: continue
                val fileId = when (val media = item.media) {
                    is MessageMedia.Photo -> media.thumbnailFileId ?: media.fileId
                    is MessageMedia.Video -> media.thumbnailFileId
                    else -> null
                } ?: continue
                key(item.messageId) {
                    RemoteImage(
                        fileId = fileId,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        background = Color.Transparent,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = (page - pagerState.currentPage - pagerState.currentPageOffsetFraction) * size.width
                            },
                    )
                }
            }
        }
        Box(Modifier.fillMaxSize().hazeBlur(input = HazeInput.Sources(hazeState), style = style))
    }
}

/**
 * Kdo a kdy snímek poslal, a akce nad ním.
 *
 * Akce se ukážou, až server řekne, že se smějí (`messageActions` se načítá pro stránku, na které
 * uživatel stojí) — koš, po kterém přijde chyba, je horší než žádný.
 */
@Composable
private fun TopBar(
    item: ViewerItem,
    state: MediaViewerState,
    pagerState: PagerState,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onForward: () -> Unit,
) {
    val actions = state.actions[item.messageId]

    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(TOP_BAR_HEIGHT)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CloseButton(onClose)

        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                text = when {
                    item.isOutgoing -> stringResource(Res.string.sender_you)
                    else -> item.senderName ?: state.chatTitle.orEmpty()
                },
                style = MaterialTheme.typography.titleMedium,
                color = ON_SCRIM,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Počítadlo se odvozuje od pozice otevřené fotky posunem indexu: výřez je souvislý.
            val position = state.initialPosition
            val initial = state.items.indexOfFirst { it.messageId == state.initialMessageId }
            val number = position?.let { it.number + (pagerState.currentPage - initial) }
            val counter = if (position != null && initial >= 0 && number != null && number in 1..position.total) {
                stringResource(Res.string.media_position, number, position.total)
            } else {
                null
            }
            Text(
                text = listOfNotNull("${item.timestamp.asDayLabel()} ${item.timestamp.asTimeOfDay()}", counter)
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = ON_SCRIM.copy(alpha = SECONDARY_ALPHA),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (actions?.canForward == true) {
            ChromeIcon(Res.drawable.forward_24dp, stringResource(Res.string.menu_forward), onForward)
        }
        if (actions != null && (actions.canDeleteForSelf || actions.canDeleteForAll)) {
            ChromeIcon(Res.drawable.delete_24dp, stringResource(Res.string.menu_delete), onDelete)
        }
    }
}

/**
 * Rychlé reakce pod snímkem a „+" pro celý výběr.
 *
 * Použitá reakce je podsvícená a ťuknutí ji sundá; počet se ukáže od dvou, jako u čipů
 * v konverzaci („1" je šum — že tam někdo je, říká už podsvícení).
 */
@Composable
private fun ReactionBar(item: ViewerItem, emoji: List<String>, onReact: (String) -> Unit) {
    var pickerOpen by remember { mutableStateOf(false) }
    val quick = emoji.take(QUICK_REACTIONS)
    val chosen = item.reactions.filter { it.isChosen }.map { it.emoji }.toSet()

    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(BOTTOM_BAR_HEIGHT)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        quick.forEach { reaction ->
            val count = item.reactions.firstOrNull { it.emoji == reaction }?.count ?: 0
            Box {
                Surface(
                    onClick = { onReact(reaction) },
                    shape = CircleShape,
                    color = if (reaction in chosen) ON_SCRIM.copy(alpha = CHOSEN_ALPHA) else Color.Transparent,
                    modifier = Modifier.size(REACTION_SIZE),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // Mimo typografickou škálu schválně, jako v pruhu reakcí v konverzaci.
                        Text(text = reaction, fontSize = 24.sp)
                    }
                }
                if (count > 1) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .padding(horizontal = 5.dp),
                    )
                }
            }
        }
        if (emoji.size > quick.size) {
            ChromeIcon(Res.drawable.add_reaction_24dp, stringResource(Res.string.menu_more_reactions)) { pickerOpen = true }
        }
    }

    if (pickerOpen) {
        ReactionPickerSheet(
            emoji = emoji,
            chosen = chosen,
            gridHeight = PICKER_GRID_HEIGHT,
            onPick = { reaction ->
                pickerOpen = false
                onReact(reaction)
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

@Composable
private fun ChromeIcon(icon: org.jetbrains.compose.resources.DrawableResource, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painterResource(icon), contentDescription = description, tint = ON_SCRIM)
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier.padding(4.dp)) {
        Icon(
            painterResource(Res.drawable.close_24dp),
            contentDescription = stringResource(Res.string.common_close),
            tint = ON_SCRIM,
        )
    }
}

/** Kde leží karta se snímkem ve stránce. Mění se při layoutu, čte se jen v gestech — proto ne stav. */
private class CardGeometry {
    var bounds: Rect = Rect.Zero
}

/**
 * Fotka s přiblížením.
 *
 * Gesta jsou dvě a **každé má vlastní `pointerInput`**: tah a štípnutí, a ťuknutí (skrytí lišt,
 * dvojité přiblížení). Obě sedí na celé stránce, ne na kartě — štípnout jde i vedle úzké fotky.
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
    caption: String,
    /** Stránka, na které pager stojí. Odlistovaná fotka se vrací do základní velikosti. */
    isCurrent: Boolean,
    dismissDrag: Animatable<Float, *>,
    onDismiss: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    captionAlpha: () -> Float = { 1f },
    cardBias: () -> Float = { 0f },
) {
    val scope = rememberCoroutineScope()
    val shape = MaterialTheme.shapes.large
    val card = remember { CardGeometry() }

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

    MediaPageLayout(
        aspectRatio = aspectRatioOf(photo.width, photo.height),
        caption = caption,
        captionAlpha = captionAlpha,
        cardBias = cardBias,
        modifier = modifier
            .pointerInput(Unit) {
                /** Meze posunu: přiblížená fotka nesmí odjet tak, aby kraj stránky zůstal prázdný. */
                fun clampOffset(x: Float, y: Float, forScale: Float): Offset {
                    val maxX = max(0f, (card.bounds.width * forScale - size.width) / 2f)
                    val maxY = max(0f, (card.bounds.height * forScale - size.height) / 2f)
                    return Offset(x.coerceIn(-maxX, maxX), y.coerceIn(-maxY, maxY))
                }

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
                                    val relativeX = centroid.x - card.bounds.center.x
                                    val relativeY = centroid.y - card.bounds.center.y
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
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        val target = if (scale > 1f) 1f else DOUBLE_TAP_SCALE
                        val relativeX = tap.x - card.bounds.center.x
                        val relativeY = tap.y - card.bounds.center.y
                        val factor = target / scale

                        val destination = if (target == 1f) {
                            Offset.Zero
                        } else {
                            val maxX = max(0f, (card.bounds.width * target - size.width) / 2f)
                            val maxY = max(0f, (card.bounds.height * target - size.height) / 2f)
                            Offset(
                                (relativeX - (relativeX - offsetX) * factor).coerceIn(-maxX, maxX),
                                (relativeY - (relativeY - offsetY) * factor).coerceIn(-maxY, maxY),
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
            // Zavírací tah posouvá kartu i s popiskem. Za gesty, aby souřadnice dotyku zůstaly
            // v neposunuté stránce.
            .graphicsLayer {
                translationY = dismissDrag.value
                // Při zavíracím tahu se stránka zmenšuje, takže je vidět, že odchází pryč.
                val shrink = 1f - (abs(dismissDrag.value) / size.height).coerceIn(0f, 1f) * 0.2f
                scaleX = shrink
                scaleY = shrink
            },
    ) {
        RemoteImage(
            // Ostrá verze; zmenšenina z bubliny slouží jako okamžitý náhled, než se dotáhne.
            // Ta je skoro vždycky už na disku, protože ji kreslila konverzace.
            fileId = photo.fullFileId,
            thumbnailFileId = photo.fileId,
            contentDescription = stringResource(Res.string.media_photo),
            // Karta má poměr stran fotky, takže `Crop` nic neukrojí — jen zakryje zaokrouhlení.
            contentScale = ContentScale.Crop,
            background = Color.Transparent,
            modifier = Modifier
                .fillMaxSize()
                .onPlaced { card.bounds = it.boundsInParent() }
                .sharedNavElement(photoKey(messageId))
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                    // Zaoblení nese vrstva, takže se s přiblížením zvětšuje spolu s fotkou.
                    clip = true
                    this.shape = shape
                },
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
internal val ON_SCRIM = Color.White

/** Jak daleko od horní hrany dolů (v podílu výšky) musí tah zajít, aby se prohlížeč zavřel. */
internal const val DISMISS_THRESHOLD = 0.18f

/** Jak rychle při zavíracím tahu prosvítá obsah pod prohlížečem. */
private const val DISMISS_FADE = 2.5f

/** Strop přiblížení. Nad tím jsou z fotky jen pixely. */
private const val MAX_SCALE = 5f

/** Kam skočí dvojité ťuknutí. */
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * Od kraje obrazovky ke kartě; sousední stránka vykukuje o [PAGE_INSET] − [PAGE_GAP] (24 dp).
 * Dost na to, aby bylo vidět, že vedle jsou další fotky; mezera mezi kartami jen oddělí.
 */
private val PAGE_INSET = 32.dp
private val PAGE_GAP = 8.dp

/** Nejširší stránka. Širší displej (rozložený Fold, tablet) dostane po stranách víc souseda. */
private val MAX_PAGE_WIDTH = 720.dp

/** Vzduch mezi kartou a lištami. */
private val PAGE_VERTICAL_GAP = 8.dp

private val TOP_BAR_HEIGHT = 64.dp
private val BOTTOM_BAR_HEIGHT: Dp = 72.dp

private val REACTION_SIZE = 44.dp
private const val QUICK_REACTIONS = 6
private val PICKER_GRID_HEIGHT = 320.dp

/** Podsvícení použité reakce na tmavém pozadí. */
private const val CHOSEN_ALPHA = 0.22f

/** Datum pod jménem je druhotné. */
private const val SECONDARY_ALPHA = 0.75f

/**
 * Rozostření pozadí. Velký poloměr: z fotky má zbýt jen barevná nálada, ne tvary, které by
 * soupeřily s kartou nad ní.
 */
private val AMBIENT_BLUR_RADIUS = 48.dp
private const val AMBIENT_TINT_ALPHA = 0.45f

/** Bez rozostření (Android 11 a starší) musí závoj fotku skoro schovat, jinak by rušila. */
private const val AMBIENT_FALLBACK_ALPHA = 0.85f
