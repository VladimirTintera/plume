package eu.tintera.plume.features.media

import eu.tintera.plume.features.report.ReportHost
import androidx.compose.foundation.focusable
import eu.tintera.plume.core.ui.ConfirmDialog
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import eu.tintera.plume.core.video.VideoQualityChoice
import eu.tintera.plume.resources.text_select_24dp
import eu.tintera.plume.resources.media_text_hide
import eu.tintera.plume.resources.auto_awesome_24dp
import eu.tintera.plume.resources.media_describe
import eu.tintera.plume.resources.media_describe_hide
import eu.tintera.plume.resources.media_text
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.core.ui.tick
import eu.tintera.plume.core.navigation.NO_START
import eu.tintera.plume.core.telegram.customEmojiIdOf
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.core.ui.CustomEmojiIcon
import eu.tintera.plume.core.ui.strayTapGuard
import eu.tintera.plume.core.ui.MEDIA_PAGE_VERTICAL_GAP
import eu.tintera.plume.core.ui.MediaCarousel
import eu.tintera.plume.core.ui.aspectRatioOf
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.ExpressiveMenu
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import org.koin.compose.koinInject
import eu.tintera.plume.core.telegram.RevealedSpoilers
import androidx.compose.runtime.snapshotFlow
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import eu.tintera.plume.core.files.autosavesToGallery
import eu.tintera.plume.core.files.fileShareSupported
import eu.tintera.plume.core.files.rememberSaveAs
import eu.tintera.plume.core.files.rememberSavePermission
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.SelfDestructType
import eu.tintera.plume.core.ui.DarkSystemBars
import eu.tintera.plume.core.ui.LockScreenOrientation
import eu.tintera.plume.core.ui.ScreenOrientation
import eu.tintera.plume.core.ui.screenOrientationLockSupported
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.NavigationEventInfo
import eu.tintera.plume.core.ui.rememberWindowFullscreen
import androidx.compose.ui.platform.LocalWindowInfo
import eu.tintera.plume.core.ui.SecureScreen
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.zoomLoadsFullResolution
import eu.tintera.plume.features.conversation.items.SpoilerOverlay
import eu.tintera.plume.features.conversation.items.rememberSpoilerBlurStyle
import eu.tintera.plume.features.conversation.items.spoilerBlur
import eu.tintera.plume.core.ui.asDayLabel
import eu.tintera.plume.core.ui.asTimeOfDay
import eu.tintera.plume.core.ui.photoKey
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.sharedNavElement
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.features.conversation.MessageDeleteDialog
import eu.tintera.plume.features.conversation.ReactionPickerSheet
import eu.tintera.plume.resources.menu_reply
import eu.tintera.plume.resources.reply_24dp
import eu.tintera.plume.resources.Res
import eu.tintera.plume.features.conversation.StickerSetSheet
import eu.tintera.plume.resources.sticker_24dp
import eu.tintera.plume.resources.media_attached_stickers
import eu.tintera.plume.resources.media_make_sticker
import eu.tintera.plume.resources.flag_24dp
import eu.tintera.plume.resources.media_profile_photo_report
import eu.tintera.plume.resources.add_reaction_24dp
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_close
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.description_24dp
import eu.tintera.plume.resources.download_24dp
import eu.tintera.plume.resources.forward_24dp
import eu.tintera.plume.resources.media_photo
import eu.tintera.plume.resources.media_position
import eu.tintera.plume.resources.media_self_destruct_seconds
import eu.tintera.plume.resources.media_view_once
import eu.tintera.plume.resources.menu_delete
import eu.tintera.plume.resources.media_more
import eu.tintera.plume.resources.sponsored_open
import eu.tintera.plume.resources.sponsored_recommended
import eu.tintera.plume.resources.sponsored_sponsored
import eu.tintera.plume.resources.media_save
import eu.tintera.plume.resources.media_save_as
import eu.tintera.plume.resources.media_save_downloads
import eu.tintera.plume.resources.media_copy_photo
import eu.tintera.plume.resources.media_copy_video
import eu.tintera.plume.resources.content_copy_24dp
import eu.tintera.plume.resources.media_profile_photo_delete_text
import eu.tintera.plume.resources.media_profile_photo_delete_title
import eu.tintera.plume.resources.media_profile_photo_delete
import eu.tintera.plume.resources.media_profile_photo_set_main
import eu.tintera.plume.resources.person_24dp
import eu.tintera.plume.resources.media_share
import eu.tintera.plume.resources.more_vert_24dp
import eu.tintera.plume.resources.schedule_24dp
import eu.tintera.plume.resources.share_24dp
import eu.tintera.plume.resources.menu_forward
import eu.tintera.plume.resources.menu_more_reactions
import eu.tintera.plume.resources.sender_you
import kotlin.math.abs
import kotlinx.coroutines.launch
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
 *
 * Tohle je **galerie**. Ťuknutí, dvojité ťuknutí nebo štípnutí na snímku přepne prohlížeč
 * na **celou obrazovku** (viz [ViewerContent]); další ťuknutí vrátí galerii.
 */
@Composable
fun MediaViewerScreen(
    viewModel: MediaViewerViewModel,
    onBack: () -> Unit,
    onForward: (messageId: Long) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Odpovědět na zobrazenou zprávu: prohlížeč se zavře a odpověď se připraví v konverzaci pod
     * ním. `null`, když pod prohlížečem konverzace toho chatu není (profil, anketa) — pak se
     * tlačítko nekreslí, nebylo by kam se vrátit.
     */
    onReply: ((messageId: Long) -> Unit)? = null,
    /**
     * Čas v médiu: otevřít rovnou na celé obrazovce a hrát od téhle vteřiny; `NO_START` = ne.
     * Platí jen pro zprávu, se kterou se prohlížeč otevřel (`initialMessageId`).
     */
    startSeconds: Int = NO_START,
    /**
     * Tlačítko reklamy (video reklamy na celé obrazovce): prohlížeč kliknutí nahlásí, zavře se
     * a odkaz předá sem — otevírá ho konverzace pod ním.
     */
    onOpenSponsoredLink: (url: String) -> Unit = {},
    /**
     * Otevřít prohlížeč znovu na zprávě v čase (s) — návrat z okénka obrazu v obraze na
     * desktopu, kde se prohlížeč při přechodu do okénka zavřel. `null` = návrat nejde.
     */
    onReopenAt: ((messageId: Long, seconds: Int) -> Unit)? = null,
    /** Tvorba samolepky ze stažené fotky; `null` = tady se nenabízí. */
    onMakeSticker: ((path: String, width: Int, height: Int) -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stickerSource = state.stickerSource
    LaunchedEffect(stickerSource) {
        val source = stickerSource ?: return@LaunchedEffect
        viewModel.stickerSourceHandled()
        onMakeSticker?.invoke(source.path, source.width, source.height)
    }
    val videoAd by viewModel.videoAd.collectAsStateWithLifecycle()
    // Podklad je tmavý v obou motivech, takže i ikony lišt musí být světlé.
    DarkSystemBars()
    // Mizející médium se nesmí dát vyfotit — jinak by odpočet nic neznamenal.
    SecureScreen(enabled = state.isSelfDestruct)

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
        scope.launch { snackbarHostState.showSnackbar(notice.resolveNow()) }
    }

    LaunchedEffect(state.closed) {
        if (state.closed) {
            viewModel.onDismiss()
            onBack()
        }
    }

    /*
     * Zpět (gesto i tlačítko) obsluhuje `NavDisplay`, ne vlastní handler: jen tak dostane průběh
     * gesta a prohlížeč se při tahu zpět zmenšuje a fotka letí do bubliny (predictive back).
     * Handler s `onBackCompleted` si gesto bral sám a animace se nespustila vůbec. Spotřebování
     * mizejícího média při odchodu hlídá `onDispose` níž, ať se odchází jakkoli.
     */
    DisposableEffect(Unit) {
        onDispose {
            viewModel.onDismiss()
        }
    }

    val savePermission = rememberSavePermission()
    val saveAs = rememberSaveAs()

    Box(modifier.fillMaxSize()) {
        if (state.items.isNotEmpty()) {
            ViewerContent(
                state = state,
                dismissDrag = dismissDrag,
                onPageChanged = viewModel::onPageChanged,
                onReact = viewModel::toggleReaction,
                onDelete = viewModel::requestDelete,
                onForward = onForward,
                onReply = onReply?.let { reply ->
                    { messageId: Long ->
                        viewModel.onDismiss()
                        reply(messageId)
                    }
                },
                onSave = { messageId ->
                    savePermission.request { granted ->
                        if (granted) viewModel.save(messageId) else viewModel.onSaveDenied()
                    }
                },
                onShare = viewModel::share,
                onCopy = viewModel::copy,
                canCopy = viewModel::canCopy,
                // Výběr umístění je aktivita systému, takže ho spouští obrazovka; ViewModel
                // jen dodá stažený soubor. Na iOS „Uložit jako…" není — umí to sdílení.
                onSaveAs = saveAs?.let { launcher ->
                    { messageId: Long ->
                        scope.launch {
                            val file = viewModel.prepareSaveAs(messageId) ?: return@launch
                            launcher.launch(file.path, file.fileName) { saved -> viewModel.onSavedAs(messageId, saved) }
                        }
                    }
                },
                onSetMainProfilePhoto = viewModel::setMainProfilePhoto,
                onDeleteProfilePhoto = viewModel::requestDeleteProfilePhoto,
                onReportProfilePhoto = viewModel::requestReportProfilePhoto,
                onAttachedStickers = viewModel::showAttachedStickers,
                hasAttachedStickers = viewModel::hasAttachedStickers,
                onRecognizeText = if (viewModel.canRecognizeText) viewModel::recognizeText else null,
                onMakeSticker = if (viewModel.canMakeSticker && onMakeSticker != null) viewModel::makeSticker else null,
                onToggleTextLine = viewModel::toggleTextLine,
                onClearTextSelection = viewModel::clearTextSelection,
                onCopyText = viewModel::copyRecognizedText,
                onCloseText = viewModel::closeRecognizedText,
                onDescribePhoto = if (state.canDescribePhoto) viewModel::describePhoto else null,
                onCopyDescription = viewModel::copyPhotoDescription,
                onCloseDescription = viewModel::closePhotoDescription,
                onFocusShown = viewModel::focusShown,
                onDismiss = {
                    viewModel.onDismiss()
                    onBack()
                },
                onSponsoredButton = {
                    viewModel.openSponsoredLink()?.let { url ->
                        viewModel.onDismiss()
                        onOpenSponsoredLink(url)
                    }
                },
                videoAd = videoAd,
                videoAdActions = remember(viewModel) {
                    VideoAdActions(
                        onPlaying = viewModel::onVideoPlaying,
                        // Odkaz inzerenta otevře konverzace pod prohlížečem (jako tlačítko reklamy).
                        onOpen = {
                            viewModel.openVideoAd()?.let { url ->
                                viewModel.onDismiss()
                                onOpenSponsoredLink(url)
                            }
                        },
                        onClose = viewModel::closeVideoAd,
                        onReport = viewModel::reportVideoAd,
                        onHeld = viewModel::holdVideoAd,
                    )
                },
                startSeconds = startSeconds,
                onReopenAt = onReopenAt,
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
                CloseButton(
                    onClick = {
                        viewModel.onDismiss()
                        onBack()
                    },
                    modifier = Modifier.align(Alignment.TopStart).statusBarsPadding(),
                )
            }
        }

        SnackbarHost(
            snackbarHostState,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = BOTTOM_BAR_HEIGHT),
        )
    }

    ReportHost(viewModel.videoAdReportFlow, snackbarHostState)

    state.attachedStickerSets?.let { sets ->
        AttachedStickerSetsSheet(sets = sets, onOpen = viewModel::openStickerSet, onDismiss = viewModel::dismissAttachedStickers)
    }
    state.openStickerSetId?.let { setId ->
        StickerSetSheet(setId = setId, onPick = null, onDismiss = viewModel::dismissAttachedStickers)
    }

    if (state.reportingPhoto != null) {
        ReportPhotoSheet(onReport = viewModel::reportProfilePhoto, onDismiss = viewModel::dismissReportProfilePhoto)
    }

    if (state.pendingProfilePhotoDelete != null) {
        ConfirmDialog(
            title = stringResource(Res.string.media_profile_photo_delete_title),
            text = stringResource(Res.string.media_profile_photo_delete_text),
            confirmLabel = stringResource(Res.string.menu_delete),
            onConfirm = viewModel::confirmDeleteProfilePhoto,
            onDismiss = viewModel::dismissDeleteProfilePhoto,
            icon = Res.drawable.delete_24dp,
            destructive = true,
        )
    }

    state.pendingDelete?.let { pending ->
        MessageDeleteDialog(
            pending = pending,
            partnerName = state.chatTitle,
            // Prohlížeč moderování autora nedočítá (`PendingDelete.moderation` je `null`).
            onConfirm = { revoke, _ -> viewModel.confirmDelete(revoke) },
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
    onReply: ((messageId: Long) -> Unit)?,
    onSave: (messageId: Long) -> Unit,
    onShare: (messageId: Long) -> Unit,
    /** Do schránky; nabízí se jen u média, které jde zkopírovat ([canCopy]). */
    onCopy: (messageId: Long) -> Unit,
    canCopy: (ViewerItem) -> Boolean,
    onSaveAs: ((messageId: Long) -> Unit)?,
    onSetMainProfilePhoto: (photoId: Long) -> Unit,
    onDeleteProfilePhoto: (photoId: Long) -> Unit,
    onReportProfilePhoto: (photoId: Long) -> Unit,
    /** „Samolepky na fotce" u stránky, která je má ([hasAttachedStickers]). */
    onAttachedStickers: (messageId: Long) -> Unit,
    hasAttachedStickers: (ViewerItem) -> Boolean,
    /** Text z fotky; `null` = platforma ho neumí. */
    onRecognizeText: ((messageId: Long) -> Unit)?,
    /** Samolepka z fotky; `null` = platforma ji neumí, nebo není kam ji otevřít. */
    onMakeSticker: ((messageId: Long) -> Unit)?,
    onToggleTextLine: (index: Int) -> Unit,
    onClearTextSelection: () -> Unit,
    onCopyText: () -> Unit,
    onCloseText: () -> Unit,
    /** Popis fotky modelem v telefonu; `null` = AI v zařízení vypnutá nebo model obraz nebere. */
    onDescribePhoto: ((messageId: Long) -> Unit)?,
    onCopyDescription: () -> Unit,
    onCloseDescription: () -> Unit,
    onFocusShown: () -> Unit,
    onDismiss: () -> Unit,
    /** Tlačítko reklamy pod snímkem; kreslí se jen u reklamy ([MediaViewerState.sponsored]). */
    onSponsoredButton: () -> Unit,
    /** Reklama nad hrajícím videem z kanálu; `null` = žádná. */
    videoAd: ViewerVideoAd?,
    videoAdActions: VideoAdActions,
    startSeconds: Int,
    onReopenAt: ((messageId: Long, seconds: Int) -> Unit)?,
) {
    val items = state.items
    /*
     * Počet stránek i klíč stránky MUSÍ číst tentýž stav, ne seznam zachycený v lambdě. Počet
     * se do `PagerState` zapíše už při rekompozici téhle funkce, jenže pager leží
     * v `BoxWithConstraints` a jeho `key` se obnoví až v subkompozici při layoutu. Po dotažení
     * dalších fotek tak pager mezitím přepočítal klíče s novým počtem nad starým seznamem
     * a spadl na `IndexOutOfBoundsException`.
     */
    val latestItems by rememberUpdatedState(items)
    val initialIndex = remember { items.indexOfFirst { it.messageId == state.initialMessageId }.coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = initialIndex) { latestItems.size }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect(onPageChanged)
    }

    /*
     * Dosednutí na jinou fotku po tahu prstem tikne (procházení po dílcích). Jen po TAHU:
     * programový posun (smazaná fotka, skok na konkrétní fotku) gestem uživatele není.
     */
    val haptics = LocalHapticFeedback.current
    val pagerDragged by pagerState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(pagerState) {
        var swiped = false
        var settled = pagerState.settledPage
        snapshotFlow { Triple(pagerDragged, pagerState.isScrollInProgress, pagerState.settledPage) }
            .collect { (dragged, scrolling, page) ->
                if (dragged) swiped = true
                if (scrolling || dragged) return@collect
                // Dosedlo: na jinou stránku po tahu tikne; tah zpátky na tutéž se zapomene.
                if (page != settled && swiped) haptics.tick()
                settled = page
                swiped = false
            }
    }

    // Vynuluje se až po posunu: vynulování mění klíč efektu a zrušilo by rozjetý posun.
    LaunchedEffect(state.focusMessageId) {
        val target = state.focusMessageId ?: return@LaunchedEffect
        val index = items.indexOfFirst { it.messageId == target }
        if (index >= 0) pagerState.animateScrollToPage(index)
        onFocusShown()
    }

    val current = items.getOrNull(pagerState.currentPage)

    /*
     * Galerie, nebo celá obrazovka. Na celé obrazovce je pozadí černé, lišty a popisek zmizí
     * a každá stránka má celou šířku, bez vykukujících sousedů. Přepíná se gestem nad snímkem:
     * ťuknutí oběma směry, dvojité ťuknutí a štípnutí jen dovnitř — přiblížená fotka i fotka
     * vrácená na základní velikost zůstávají na celé obrazovce.
     *
     * Režim patří celému prohlížeči, ne stránce: listuje se v něm dál.
     */
    // Čas v médiu otevírá video rovnou přehrávané — v galerii by ho uživatel musel pustit sám.
    // Ztlumení platí pro všechna videa, dokud je prohlížeč otevřený; po odlistování by jinak
    // další video zase hrálo nahlas. Neukládá se: příště se začíná se zvukem.
    var videoMuted by rememberSaveable { mutableStateOf(false) }
    // Kvalita videa stejně: platí pro další videa, dokud je prohlížeč otevřený; příště automaticky.
    var videoQuality by rememberSaveable(stateSaver = VideoQualityChoiceSaver) {
        mutableStateOf<VideoQualityChoice>(VideoQualityChoice.Auto)
    }
    var immersive by rememberSaveable(state.isSelfDestruct) {
        mutableStateOf(state.isSelfDestruct || startSeconds != NO_START)
    }
    LaunchedEffect(state.isSelfDestruct) {
        if (state.isSelfDestruct) immersive = true
    }
    val immersionState = animateFloatAsState(
        targetValue = if (immersive) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "media-immersion",
    )
    // Pružina přestřelí; záporné odsazení nebo zaoblení by layout odmítl.
    val immersion = { immersionState.value.coerceIn(0f, 1f) }
    /*
     * Zámek proti dotykům a ručně vynucená orientace patří prohlížeči, ne stránce: zámek vypíná
     * i listování a otočení na šířku má platit i pro další video. Obojí platí jen na celé
     * obrazovce — návrat do galerie orientaci pustí (jako YouTube). Zamčené video do galerie
     * nepustí, takže se tam zámek ruší jen pro jistotu (smazaná zpráva, mizející médium).
     */
    var controlsLocked by rememberSaveable { mutableStateOf(false) }
    // Které video právě hraje (id zprávy); hlásí ho vybraná stránka.
    var playingVideo by remember { mutableStateOf<Long?>(null) }
    // Čas reklam nad videem běží jen při přehrávání na celé obrazovce.
    LaunchedEffect(playingVideo, immersive) { videoAdActions.onPlaying(playingVideo?.takeIf { immersive }) }
    var forcedOrientation by rememberSaveable { mutableStateOf<ScreenOrientation?>(null) }
    /*
     * Celá obrazovka okna (desktop): tlačítko u videa, F a dvojklik. Patří prohlížeči jako zámek
     * — platí i pro další video. Návrat do galerie, zavření prohlížeče i Esc vrátí okno, jak bylo
     * (Esc ji ukončí dřív, než zavře prohlížeč). Ukončení tlačítkem okna (zelené na macOS) se
     * pozná podle stavu okna; okno, které přes celou obrazovku dal uživatel sám, prohlížeč nevrací.
     */
    val windowFullscreen = rememberWindowFullscreen()
    var windowFullscreenOn by remember { mutableStateOf(false) }
    LaunchedEffect(windowFullscreen) {
        snapshotFlow { windowFullscreen?.active == true }.collect { if (!it) windowFullscreenOn = false }
    }
    val setWindowFullscreen: ((Boolean) -> Unit)? = windowFullscreen?.let { window ->
        { on: Boolean ->
            windowFullscreenOn = on
            if (on) immersive = true
            window.set(on)
        }
    }
    val leaveFullscreenOnClose by rememberUpdatedState(windowFullscreenOn)
    DisposableEffect(windowFullscreen) { onDispose { if (leaveFullscreenOnClose) windowFullscreen?.set(false) } }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = windowFullscreenOn,
        onBackCompleted = { setWindowFullscreen?.invoke(false) },
    )
    LaunchedEffect(immersive) {
        if (!immersive) {
            controlsLocked = false
            forcedOrientation = null
            if (windowFullscreenOn) setWindowFullscreen?.invoke(false)
        }
    }
    LockScreenOrientation(forcedOrientation.takeIf { immersive })
    val windowSize = LocalWindowInfo.current.containerSize
    // Otočit = do té druhé, než v jaké okno právě je (i když ji zvolil senzor, ne tlačítko).
    val rotate: (() -> Unit)? = if (screenOrientationLockSupported()) {
        {
            forcedOrientation = if (windowSize.width > windowSize.height) ScreenOrientation.Portrait else ScreenOrientation.Landscape
        }
    } else {
        null
    }
    val videoFullscreen = VideoFullscreen(
        locked = controlsLocked,
        onLockChange = { controlsLocked = it },
        onRotate = rotate,
        window = windowFullscreenOn,
        onWindowChange = setWindowFullscreen,
    )
    val enterImmersive = { immersive = true }
    val toggleImmersive = { if (!state.isSelfDestruct) immersive = !immersive }

    val spoilerStyle = rememberSpoilerBlurStyle()
    val revealedSpoilers = koinInject<RevealedSpoilers>()
    val revealedIds by revealedSpoilers.revealed.collectAsState()
    LaunchedEffect(state.initialMessageId) {
        revealedSpoilers.reveal(state.initialMessageId)
    }

    // Obsah pod prohlížečem prosvítá, jak se fotka odtahuje pryč — je tím vidět, že gesto něco
    // dělá, ještě než se pustí. Pozadí i lišty mizí spolu.
    val fadeWithDismiss = Modifier.graphicsLayer {
        alpha = 1f - (abs(dismissDrag.value) / size.height * DISMISS_FADE).coerceIn(0f, 1f)
    }

    val density = LocalDensity.current
    val topInset = with(density) { WindowInsets.statusBars.getTop(this).toDp() } + TOP_BAR_HEIGHT
    val bottomInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() } + BOTTOM_BAR_HEIGHT

    /*
     * Myš a klávesnice (desktop, Android s klávesnicí): ←/→ a šipky po stranách listují, +/−/0
     * přibližují (viz `ViewerControls.kt`). Listuje se jen tam, kam by šel i tah. Fokus si
     * prohlížeč bere hned, jinak by klávesa nedošla nikam: v Compose ji dostane jen zaostřený
     * prvek a jeho předci.
     *
     * HRAJÍCÍ video na celé obrazovce se nelistuje: vodorovný tah při sledování je skoro vždycky
     * nechtěný — sjetý svislý tah jasu, palec vedle posuvníku — a odlistování by video uprostřed
     * zastavilo. Pozastavené a dohrané ano, zamčené nikdy. Klíčem je id zprávy, ne příznak:
     * hlášení od stránky, která už vybraná není, tak nic nezablokuje.
     */
    val pagerScroll = !(
        immersive && current?.media is MessageMedia.Video &&
            (controlsLocked || current.messageId == playingVideo)
        )
    val scope = rememberCoroutineScope()
    val goToPage = { target: Int ->
        if (pagerScroll && target in 0 until pagerState.pageCount) scope.launch { pagerState.animateScrollToPage(target) }
    }
    val zoomSteps = remember { MutableSharedFlow<ZoomStep>(extraBufferCapacity = 4) }
    // Klávesy pro vybrané video (mezerník, šipky); vyřizuje je stránka, která má přehrávač.
    val videoCommands = remember { MutableSharedFlow<VideoCommand>(extraBufferCapacity = 4) }
    val currentIsVideo = current?.media is MessageMedia.Video
    val mouse = remember { MouseActivity() }
    val keyFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { keyFocus.requestFocus() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val action = viewerKey(
                    key = event.key,
                    char = event.utf16CodePoint.takeIf { it > 0 }?.toChar(),
                    ctrl = event.isCtrlPressed,
                    alt = event.isAltPressed,
                    shift = event.isShiftPressed,
                    meta = event.isMetaPressed,
                    rtl = rtl,
                    video = currentIsVideo,
                    // Šipky posouvají tam, kde by se ani tahem nelistovalo.
                    seekWithArrows = currentIsVideo && (windowFullscreenOn || !pagerScroll),
                ) ?: return@onPreviewKeyEvent false
                when (action) {
                    ViewerKey.Previous -> goToPage(pagerState.currentPage - 1)
                    ViewerKey.Next -> goToPage(pagerState.currentPage + 1)
                    ViewerKey.ZoomIn -> zoomSteps.tryEmit(ZoomStep.In)
                    ViewerKey.ZoomOut -> zoomSteps.tryEmit(ZoomStep.Out)
                    ViewerKey.ZoomReset -> zoomSteps.tryEmit(ZoomStep.Reset)
                    // V galerii video nehraje; mezerník ho pustí jako ťuknutí (na celou obrazovku).
                    ViewerKey.PlayPause -> if (immersive) videoCommands.tryEmit(VideoCommand.PlayPause) else enterImmersive()
                    ViewerKey.SeekBack -> videoCommands.tryEmit(VideoCommand.Seek(-KEY_SEEK_STEP_MS))
                    ViewerKey.SeekForward -> videoCommands.tryEmit(VideoCommand.Seek(KEY_SEEK_STEP_MS))
                    ViewerKey.ToggleMute -> videoMuted = !videoMuted
                    ViewerKey.ToggleFullscreen -> {
                        val set = setWindowFullscreen ?: return@onPreviewKeyEvent false
                        set(!windowFullscreenOn)
                    }
                    ViewerKey.Copy -> {
                        // Nad textem z fotky a popisem patří Ctrl+C jejich výběru, ne fotce.
                        val item = current?.takeIf {
                            state.actions[it.messageId]?.canSave == true && canCopy(it) &&
                                state.recognizedText?.messageId != it.messageId &&
                                state.photoDescription?.messageId != it.messageId
                        } ?: return@onPreviewKeyEvent false
                        onCopy(item.messageId)
                    }
                }
                true
            }
            /*
             * Fokus se vrací: kliknuté tlačítko (přehrát uprostřed videa) si ho vezme, a když se
             * pak s ovládáním schová, fokus nezůstane nikde a klávesy by do prohlížeče nedošly.
             */
            .onFocusChanged { focus -> if (!focus.hasFocus) runCatching { keyFocus.requestFocus() } }
            .focusRequester(keyFocus)
            .focusable()
            .trackMouse(mouse),
    ) {
        MediaCarousel(
            pagerState = pagerState,
            pageKey = { latestItems[it].messageId },
            immersion = immersion,
            backdropModifier = fadeWithDismiss,
            modifier = Modifier.fillMaxSize(),
            // Otevřená fotka je vidět hned, sousedé se dotahují až pod ní — nesmí naskočit.
            animateArrivals = true,
            // Video na celé obrazovce se neodlistuje (viz `pagerScroll`).
            userScrollEnabled = pagerScroll,
            // Pozadí bere malý náhled, ne ostrou verzi: pod rozostřením detail nic nepřidá
            // a náhled je na disku skoro vždycky, protože ho kreslila konverzace.
            backdrop = { index ->
                val fileId = when (val media = latestItems.getOrNull(index)?.media) {
                    is MessageMedia.Photo -> media.thumbnailFileId ?: media.fileId
                    is MessageMedia.Video -> media.thumbnailFileId
                    else -> null
                }
                if (fileId != null) {
                    RemoteImage(
                        fileId = fileId,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        background = Color.Transparent,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            },
        ) { carouselPage ->
            val page = carouselPage.index
            val item = latestItems[page]
            val isCurrent = carouselPage.isCurrent
            val hasSpoiler = when (val m = item.media) {
                is MessageMedia.Photo -> m.hasSpoiler
                is MessageMedia.Video -> m.hasSpoiler
                else -> false
            }
            val revealed = !hasSpoiler || item.messageId in revealedIds
            val onReveal = { revealedSpoilers.reveal(item.messageId) }
            // Karta leží mezi lištami; stránka sama jde přes celou výšku, aby přiblížená fotka
            // měla kam růst.
            val captionAlpha = carouselPage.captionAlpha
            val cardBias = carouselPage.cardBias
            val pageModifier = Modifier.fillMaxSize()
            val reservedTop = topInset + MEDIA_PAGE_VERTICAL_GAP
            val reservedBottom = bottomInset + MEDIA_PAGE_VERTICAL_GAP
            when (val media = item.media) {
                is MessageMedia.Video -> VideoPage(
                    video = media,
                    messageId = item.messageId,
                    startMs = if (item.messageId == state.initialMessageId && startSeconds != NO_START) startSeconds * 1000L else 0L,
                    streaming = !state.isSelfDestruct,
                    // Mizející video v okénku nad jinými aplikacemi by obešlo FLAG_SECURE i odpočet.
                    pictureInPicture = !state.isSelfDestruct,
                    onReturnFromPip = onReopenAt?.let { reopen -> { ms: Long -> reopen(item.messageId, (ms / 1000).toInt()) } },
                    caption = item.caption,
                    isCurrent = isCurrent,
                    immersive = immersive,
                    immersion = immersion,
                    reservedTop = reservedTop,
                    reservedBottom = reservedBottom,
                    dismissDrag = dismissDrag,
                    onDismiss = onDismiss,
                    onTap = toggleImmersive,
                    onEngage = enterImmersive,
                    hasSpoiler = hasSpoiler,
                    revealed = revealed,
                    onReveal = onReveal,
                    spoilerStyle = spoilerStyle,
                    modifier = pageModifier,
                    captionAlpha = captionAlpha,
                    cardBias = cardBias,
                    muted = videoMuted,
                    onToggleMute = { videoMuted = !videoMuted },
                    qualityChoice = videoQuality,
                    onQualityChoice = { videoQuality = it },
                    fullscreen = videoFullscreen,
                    commands = videoCommands,
                    onPlayingChange = { playing ->
                        if (playing) {
                            playingVideo = item.messageId
                        } else if (playingVideo == item.messageId) {
                            playingVideo = null
                        }
                    },
                    rememberPosition = !state.isSelfDestruct,
                )

                is MessageMedia.Photo -> ZoomableImage(
                    photo = media,
                    messageId = item.messageId,
                    photoText = state.recognizedText?.takeIf { it.messageId == item.messageId },
                    onToggleTextLine = onToggleTextLine,
                    caption = item.caption,
                    isCurrent = isCurrent,
                    immersive = immersive,
                    immersion = immersion,
                    reservedTop = reservedTop,
                    reservedBottom = reservedBottom,
                    dismissDrag = dismissDrag,
                    onDismiss = onDismiss,
                    onTap = toggleImmersive,
                    onEngage = enterImmersive,
                    hasSpoiler = hasSpoiler,
                    revealed = revealed,
                    onReveal = onReveal,
                    spoilerStyle = spoilerStyle,
                    modifier = pageModifier,
                    captionAlpha = captionAlpha,
                    cardBias = cardBias,
                    zoomSteps = zoomSteps,
                )

                else -> Unit
            }
        }

        SystemBarShades(immersion, fadeWithDismiss)

        PageArrows(
            activity = mouse,
            hasPrevious = pagerScroll && pagerState.currentPage > 0,
            hasNext = pagerScroll && pagerState.currentPage < pagerState.pageCount - 1,
            onPrevious = { goToPage(pagerState.currentPage - 1) },
            onNext = { goToPage(pagerState.currentPage + 1) },
            modifier = fadeWithDismiss,
        )

        val chromeEnter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec())
        val chromeExit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec())

        /*
         * Reklama nad videem: jen na celé obrazovce, kde video hraje, nahoře pod ovládáním
         * přehrávače. Drží se i se schovaným ovládáním — po dobu zobrazení ji schovat nejde.
         */
        AnimatedVisibility(
            visible = immersive && videoAd != null,
            enter = chromeEnter,
            exit = chromeExit,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .then(fadeWithDismiss)
                .padding(top = topInset, start = 16.dp, end = 16.dp),
        ) {
            // Poslední zobrazená zůstane vidět, dokud rámeček neodjede. Obyčejný držák, ne stav:
            // zápis stavu během kompozice by ji spustil znovu.
            val last = remember { arrayOfNulls<ViewerVideoAd>(1) }
            if (videoAd != null) last[0] = videoAd
            last[0]?.let { shown ->
                VideoAdBox(
                    shown = shown,
                    onOpen = videoAdActions.onOpen,
                    onClose = videoAdActions.onClose,
                    onReport = videoAdActions.onReport,
                    onHeld = videoAdActions.onHeld,
                )
            }
        }

        AnimatedVisibility(
            visible = !immersive && current != null && !state.isSelfDestruct,
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
                    onSave = { onSave(current.messageId) },
                    onShare = { onShare(current.messageId) },
                    onCopy = { onCopy(current.messageId) }.takeIf { canCopy(current) },
                    onSaveAs = onSaveAs?.let { saveAs -> { saveAs(current.messageId) } },
                    onSetMainProfilePhoto = { onSetMainProfilePhoto(current.messageId) },
                    onDeleteProfilePhoto = { onDeleteProfilePhoto(current.messageId) },
                    onReportProfilePhoto = { onReportProfilePhoto(current.messageId) },
                    onAttachedStickers = { onAttachedStickers(current.messageId) }.takeIf { hasAttachedStickers(current) },
                    // Jen fotka, a jen když jde uložit: u chatu se zakázaným ukládáním by text
                    // z fotky obešel tutéž ochranu jako snímek obrazovky.
                    onRecognizeText = onRecognizeText
                        ?.takeIf { current.media is MessageMedia.Photo }
                        ?.let { recognize -> { recognize(current.messageId) } },
                    // Totéž omezení: samolepka by fotku z chatu se zákazem ukládání vynesla ven.
                    onMakeSticker = onMakeSticker
                        ?.takeIf { current.media is MessageMedia.Photo }
                        ?.let { make -> { make(current.messageId) } },
                    // Totéž omezení jako text z fotky: popis by obešel zákaz ukládání.
                    onDescribePhoto = onDescribePhoto
                        ?.takeIf { current.media is MessageMedia.Photo }
                        ?.let { describe -> { describe(current.messageId) } },
                )
            }
        }

        AnimatedVisibility(
            visible = state.isSelfDestruct,
            enter = chromeEnter,
            exit = chromeExit,
            modifier = Modifier.align(Alignment.TopStart).then(fadeWithDismiss),
        ) {
            SelfDestructTopBar(
                remainingSeconds = state.remainingSeconds,
                selfDestructType = state.selfDestructType,
                onClose = onDismiss,
            )
        }

        /*
         * Pruh reakcí vyjede zdola, až prohlížeč dosedne (přechod na obrazovku i let fotky
         * z bubliny doběhly) a reakce jsou načtené. Během letu by vyskočil na místě, kam fotka
         * teprve míří — a právě tam dopadá druhé ťuknutí dvojitého ťuknutí na fotku. Vyjíždí jen
         * poprvé; po návratu z celé obrazovky a mezi stránkami se jen prolne jako horní lišta.
         */
        val navTransition = LocalNavAnimatedContentScope.current.transition
        var settled by remember { mutableStateOf(false) }
        LaunchedEffect(navTransition) {
            snapshotFlow {
                navTransition.currentState == EnterExitState.Visible &&
                    navTransition.targetState == EnterExitState.Visible
            }.first { it }
            settled = true
        }
        val emoji = current?.let { state.availableReactions[it.messageId] }.orEmpty()
        // Odpovědět jde, jen když je kam (konverzace pod prohlížečem) a server to u zprávy dovolí.
        val canReply = onReply != null && current != null && state.actions[current.messageId]?.canReply == true
        val photoText = state.recognizedText?.takeIf { it.messageId == current?.messageId }
        val photoDescription = state.photoDescription?.takeIf { it.messageId == current?.messageId }
        val reactionsVisible = settled && !immersive && current != null && (emoji.isNotEmpty() || canReply) &&
            !state.isSelfDestruct && photoText == null && photoDescription == null
        var reactionsArrived by remember { mutableStateOf(false) }
        LaunchedEffect(reactionsVisible) { if (reactionsVisible) reactionsArrived = true }
        val reactionsEnter = if (reactionsArrived) {
            chromeEnter
        } else {
            slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } + chromeEnter
        }
        AnimatedVisibility(
            visible = reactionsVisible,
            enter = reactionsEnter,
            exit = chromeExit,
            modifier = Modifier.align(Alignment.BottomCenter).then(fadeWithDismiss),
        ) {
            if (current != null) {
                ReactionBar(
                    item = current,
                    emoji = emoji,
                    onReact = { onReact(current.messageId, it) },
                    onReply = if (canReply && onReply != null) {
                        { onReply(current.messageId) }
                    } else {
                        null
                    },
                )
            }
        }

        // Tlačítko reklamy na místě pruhu reakcí — reklama reakce ani odpověď nemá.
        val sponsored = state.sponsored
        val sponsoredVisible = settled && !immersive && current != null && sponsored != null
        var sponsoredArrived by remember { mutableStateOf(false) }
        LaunchedEffect(sponsoredVisible) { if (sponsoredVisible) sponsoredArrived = true }
        AnimatedVisibility(
            visible = sponsoredVisible,
            enter = if (sponsoredArrived) chromeEnter else slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } + chromeEnter,
            exit = chromeExit,
            modifier = Modifier.align(Alignment.BottomCenter).then(fadeWithDismiss),
        ) {
            if (current != null && sponsored != null) SponsoredButtonBar(current, sponsored, onSponsoredButton)
        }

        // Text z fotky na místě pruhu reakcí; poslední text se drží i během odjezdu panelu.
        var lastPhotoText by remember { mutableStateOf(photoText) }
        if (photoText != null) lastPhotoText = photoText
        AnimatedVisibility(
            visible = photoText != null && !immersive,
            enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } + chromeEnter,
            exit = slideOutVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } + chromeExit,
            modifier = Modifier.align(Alignment.BottomCenter).then(fadeWithDismiss),
        ) {
            lastPhotoText?.let { shown ->
                PhotoTextPanel(text = shown, onCopy = onCopyText, onClearSelection = onClearTextSelection, onClose = onCloseText)
            }
        }

        // Popis fotky na témže místě; oba panely naráz nejsou (ViewModel jeden zavře druhým).
        var lastDescription by remember { mutableStateOf(photoDescription) }
        if (photoDescription != null) lastDescription = photoDescription
        AnimatedVisibility(
            visible = photoDescription != null && !immersive,
            enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } + chromeEnter,
            exit = slideOutVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } + chromeExit,
            modifier = Modifier.align(Alignment.BottomCenter).then(fadeWithDismiss),
        ) {
            lastDescription?.let { shown ->
                PhotoDescriptionPanel(description = shown, onCopy = onCopyDescription, onClose = onCloseDescription)
            }
        }
    }
}

@Composable
private fun SelfDestructTopBar(
    remainingSeconds: Int?,
    selfDestructType: SelfDestructType?,
    onClose: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(TOP_BAR_HEIGHT)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CloseButton(onClose)

        Spacer(Modifier.weight(1f))

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f),
            modifier = Modifier.padding(end = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.schedule_24dp),
                    contentDescription = null,
                    tint = ON_SCRIM,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                val timerText = when {
                    remainingSeconds != null -> stringResource(Res.string.media_self_destruct_seconds, remainingSeconds)
                    selfDestructType is SelfDestructType.Timer -> stringResource(Res.string.media_self_destruct_seconds, selfDestructType.seconds)
                    else -> stringResource(Res.string.media_view_once)
                }
                Text(
                    text = timerText,
                    style = MaterialTheme.typography.labelLarge,
                    color = ON_SCRIM,
                )
            }
        }
    }
}

/**
 * Ztmavení pod systémovými lištami na celé obrazovce.
 *
 * Lišty zůstávají (čas, notifikace), jen přes přiblížený nebo vysoký snímek by světlé ikony
 * na světlém záběru nebyly vidět. Přechod do průhledna, ne pruh — plná plocha by ukrojila
 * kus fotky.
 */
@Composable
internal fun androidx.compose.foundation.layout.BoxScope.SystemBarShades(immersion: () -> Float, fade: Modifier) {
    val density = LocalDensity.current
    val top = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val bottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val shade = Color.Black.copy(alpha = BAR_SHADE_ALPHA)
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(top + BAR_SHADE_EXTRA)
            .then(fade)
            .graphicsLayer { alpha = immersion() }
            .background(Brush.verticalGradient(listOf(shade, Color.Transparent))),
    )
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(bottom + BAR_SHADE_EXTRA)
            .then(fade)
            .graphicsLayer { alpha = immersion() }
            .background(Brush.verticalGradient(listOf(Color.Transparent, shade))),
    )
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
    onSave: () -> Unit,
    onShare: () -> Unit,
    /** `null` = médium do schránky nejde ([MediaViewerViewModel.canCopy]). */
    onCopy: (() -> Unit)?,
    onSaveAs: (() -> Unit)?,
    onSetMainProfilePhoto: () -> Unit,
    onDeleteProfilePhoto: () -> Unit,
    onReportProfilePhoto: () -> Unit,
    onRecognizeText: (() -> Unit)?,
    onMakeSticker: (() -> Unit)?,
    onDescribePhoto: (() -> Unit)?,
    /** `null` = na médiu samolepky nejsou. */
    onAttachedStickers: (() -> Unit)? = null,
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
            // Reklama čas nemá; místo něj štítek, ať se nesplete s příspěvkem kanálu.
            val sponsored = state.sponsored
            val subtitle = if (sponsored != null) {
                stringResource(if (sponsored.isRecommended) Res.string.sponsored_recommended else Res.string.sponsored_sponsored)
            } else {
                "${item.timestamp.asDayLabel()} ${item.timestamp.asTimeOfDay()}"
            }
            Text(
                text = listOfNotNull(subtitle, counter).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                color = ON_SCRIM.copy(alpha = SECONDARY_ALPHA),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        /*
         * Venku jsou jen dvě nejčastější akce, zbytek je v nabídce ⋮. Se Sdílením by byly ikony
         * čtyři a na užším telefonu by se nevešel titulek. Smazat do nabídky patří tak jako tak:
         * nevratná akce nemá stát hned vedle častých. Přeposlat je sdílení do Telegramu, takže
         * vedle Sdílet by byly dvě podobné ikony.
         *
         * Co se právě dělá, točí na místě svého tlačítka, aby lišta neposkočila — stahování
         * originálu může trvat. U „Uložit jako…" z nabídky točí ⋮.
         *
         * Desktop systémový dialog sdílení nemá ([fileShareSupported]); místo Sdílet je venku
         * Kopírovat, které tam médium dostane do jiné aplikace nebo do složky. Na telefonu je
         * kopírování fotky v nabídce — schránka tam vedle sdílení tolik neznamená. Galerii
         * desktop taky nemá a ukládá do Stažených; tlačítko to musí říct.
         */
        val busy = state.busy[item.messageId]
        val canSave = actions?.canSave == true
        val copyInBar = !fileShareSupported
        val copy = onCopy?.takeIf { canSave }
        val copyLabel = stringResource(if (item.media is MessageMedia.Video) Res.string.media_copy_video else Res.string.media_copy_photo)
        if (canSave) {
            if (fileShareSupported) {
                BusyOr(busy == MediaTask.Share) {
                    ChromeIcon(Res.drawable.share_24dp, stringResource(Res.string.media_share), onShare)
                }
            } else if (copy != null) {
                BusyOr(busy == MediaTask.Copy) {
                    ChromeIcon(Res.drawable.content_copy_24dp, copyLabel, copy)
                }
            }
            BusyOr(busy == MediaTask.Gallery) {
                ChromeIcon(
                    Res.drawable.download_24dp,
                    stringResource(if (autosavesToGallery) Res.string.media_save else Res.string.media_save_downloads),
                    onSave,
                )
            }
        }
        val copyInMenu = copy?.takeIf { !copyInBar }

        val canForward = actions?.canForward == true
        val canDelete = actions != null && (actions.canDeleteForSelf || actions.canDeleteForAll)
        val saveAs = onSaveAs?.takeIf { canSave }
        val recognizeText = onRecognizeText?.takeIf { canSave }
        val textShown = state.recognizedText?.messageId == item.messageId
        val describePhoto = onDescribePhoto?.takeIf { canSave }
        val makeSticker = onMakeSticker?.takeIf { canSave }
        val descriptionShown = state.photoDescription?.messageId == item.messageId
        // Vlastní profilovky: starší jde nastavit zase jako hlavní (ta nejnovější hlavní je)
        // a každou smazat. Cizí profilovky a fotky skupin tohle nemají — TDLib to neumí.
        val own = state.ownProfilePhotos
        val canSetMain = own && item.messageId != state.items.lastOrNull()?.messageId
        // Cizí profilovku jde nahlásit moderátorům, kde to TDLib dovolí.
        val reportable = item.messageId in state.reportablePhotos
        if (canForward || canDelete || saveAs != null || copyInMenu != null || own || reportable || recognizeText != null || describePhoto != null || makeSticker != null || onAttachedStickers != null) {
            var menuOpen by remember { mutableStateOf(false) }
            BusyOr(busy == MediaTask.SaveAs || (busy == MediaTask.Copy && !copyInBar) || busy == MediaTask.Text || busy == MediaTask.Describe || busy == MediaTask.Sticker) {
                Box {
                    ChromeIcon(Res.drawable.more_vert_24dp, stringResource(Res.string.media_more)) { menuOpen = true }
                    ExpressiveMenu(
                        expanded = menuOpen,
                        onDismiss = { menuOpen = false },
                        groups = listOf(
                            listOfNotNull(
                                recognizeText?.let {
                                    MenuAction(
                                        stringResource(if (textShown) Res.string.media_text_hide else Res.string.media_text),
                                        Res.drawable.text_select_24dp,
                                        onClick = it,
                                    )
                                },
                                describePhoto?.let {
                                    MenuAction(
                                        stringResource(if (descriptionShown) Res.string.media_describe_hide else Res.string.media_describe),
                                        Res.drawable.auto_awesome_24dp,
                                        onClick = it,
                                    )
                                },
                                onAttachedStickers?.let {
                                    MenuAction(stringResource(Res.string.media_attached_stickers), Res.drawable.sticker_24dp, onClick = it)
                                },
                                makeSticker?.let {
                                    MenuAction(stringResource(Res.string.media_make_sticker), Res.drawable.sticker_24dp, onClick = it)
                                },
                                MenuAction(stringResource(Res.string.menu_forward), Res.drawable.forward_24dp, onClick = onForward).takeIf { canForward },
                                copyInMenu?.let { MenuAction(copyLabel, Res.drawable.content_copy_24dp, onClick = it) },
                                saveAs?.let { MenuAction(stringResource(Res.string.media_save_as), Res.drawable.description_24dp, onClick = it) },
                                MenuAction(
                                    stringResource(Res.string.media_profile_photo_set_main),
                                    Res.drawable.person_24dp,
                                    onClick = onSetMainProfilePhoto,
                                ).takeIf { canSetMain },
                            ),
                            listOfNotNull(
                                MenuAction(
                                    stringResource(Res.string.media_profile_photo_report),
                                    Res.drawable.flag_24dp,
                                    onClick = onReportProfilePhoto,
                                ).takeIf { reportable },
                            ),
                            // Smazání zvlášť: nevratné, nemá stát mezi častými akcemi.
                            listOfNotNull(
                                MenuAction(stringResource(Res.string.menu_delete), Res.drawable.delete_24dp, destructive = true, onClick = onDelete).takeIf { canDelete },
                                MenuAction(
                                    stringResource(Res.string.media_profile_photo_delete),
                                    Res.drawable.delete_24dp,
                                    destructive = true,
                                    onClick = onDeleteProfilePhoto,
                                ).takeIf { own },
                            ),
                        ),
                    )
                }
            }
        }
    }
}

/** Indikátor na místě tlačítka, dokud jeho akce běží. */
@Composable
private fun BusyOr(busy: Boolean, content: @Composable () -> Unit) {
    if (busy) LoadingIndicator(color = ON_SCRIM, modifier = Modifier.size(48.dp)) else content()
}

/**
 * Rychlé reakce pod snímkem a „+" pro celý výběr.
 *
 * Použitá reakce je podsvícená a ťuknutí ji sundá; počet se ukáže od dvou, jako u čipů
 * v konverzaci („1" je šum — že tam někdo je, říká už podsvícení).
 */
@Composable
private fun ReactionBar(
    item: ViewerItem,
    emoji: List<String>,
    onReact: (String) -> Unit,
    /** Odpovědět — na začátku lišty, jako v Google Zprávách; `null` = nekreslí se. */
    onReply: (() -> Unit)?,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val quick = emoji.take(QUICK_REACTIONS)
    val chosen = item.reactions.filter { it.isChosen }.map { it.emoji }.toSet()

    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(BOTTOM_BAR_HEIGHT)
            // Pruh je dole, kudy vede palec při listování a zavírání — nedbalý tah ani ťuknutí
            // hned po otevření fotky reakci dát nesmí.
            .strayTapGuard(item.messageId)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onReply != null) {
            ChromeIcon(Res.drawable.reply_24dp, stringResource(Res.string.menu_reply), onClick = onReply)
        }
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
                        val customId = customEmojiIdOf(reaction)
                        if (customId != null) {
                            CustomEmojiIcon(customId, size = 28.dp)
                        } else {
                            // Mimo typografickou škálu schválně, jako v pruhu reakcí v konverzaci.
                            Text(text = reaction, fontSize = 24.sp)
                        }
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

/**
 * Tlačítko reklamy pod médiem na celé obrazovce. Specifikace ho tu chce: odkaz reklamy se
 * u videa otevírá až odsud, ťuknutí na video v konverzaci jen přehrává.
 */
@Composable
private fun SponsoredButtonBar(item: ViewerItem, sponsored: ViewerSponsored, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(BOTTOM_BAR_HEIGHT)
            .strayTapGuard(item.messageId)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Button(onClick = onClick, shapes = ButtonDefaults.shapes()) {
            // Popisek dodává Telegram — vlastní text by byl zásah do reklamy.
            Text(sponsored.buttonText.ifBlank { stringResource(Res.string.sponsored_open) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ChromeIcon(icon: org.jetbrains.compose.resources.DrawableResource, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
        Icon(painterResource(icon), contentDescription = description, tint = ON_SCRIM)
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier.padding(4.dp), shapes = IconButtonDefaults.shapes()) {
        Icon(
            painterResource(Res.drawable.close_24dp),
            contentDescription = stringResource(Res.string.common_close),
            tint = ON_SCRIM,
        )
    }
}

/**
 * Fotka ze zprávy s přiblížením: mechaniku nese [ZoomableMediaPage] (sdílená se sdílením do
 * chatu), tady je jen to, co patří zprávě — ostrá verze s náhledem, sdílený prvek z bubliny
 * a spoiler, který odkryje první ťuknutí.
 */
@Composable
private fun ZoomableImage(
    photo: MessageMedia.Photo,
    messageId: Long,
    /** Rozpoznaný text na téhle fotce — zvýrazní se na ní a jde na ní vybírat; `null` = žádný. */
    photoText: PhotoText?,
    onToggleTextLine: (index: Int) -> Unit,
    caption: String,
    isCurrent: Boolean,
    immersive: Boolean,
    immersion: () -> Float,
    reservedTop: Dp,
    reservedBottom: Dp,
    dismissDrag: Animatable<Float, *>,
    onDismiss: () -> Unit,
    onTap: () -> Unit,
    onEngage: () -> Unit,
    hasSpoiler: Boolean = false,
    revealed: Boolean = true,
    onReveal: () -> Unit = {},
    spoilerStyle: HazeBlurStyle = HazeBlurStyle,
    modifier: Modifier = Modifier,
    captionAlpha: () -> Float = { 1f },
    cardBias: () -> Float = { 0f },
    /** Přiblížení z klávesnice (+/−/0), viz [ZoomableMediaPage]. */
    zoomSteps: Flow<ZoomStep>? = null,
) {
    val hidden = hasSpoiler && !revealed
    ZoomableMediaPage(
        aspectRatio = aspectRatioOf(photo.width, photo.height),
        caption = caption,
        isCurrent = isCurrent,
        immersive = immersive,
        immersion = immersion,
        reservedTop = reservedTop,
        reservedBottom = reservedBottom,
        dismissDrag = dismissDrag,
        onDismiss = onDismiss,
        onTap = { if (hidden) onReveal() else onTap() },
        onEngage = onEngage,
        zoomable = !hidden,
        zoomSteps = zoomSteps,
        modifier = modifier,
        cardModifier = Modifier.sharedNavElement(photoKey(messageId), besidePanes = true),
        captionAlpha = captionAlpha,
        cardBias = cardBias,
    ) {
        RemoteImage(
            // Ostrá verze; zmenšenina z bubliny slouží jako okamžitý náhled, než se dotáhne.
            // Ta je skoro vždycky už na disku, protože ji kreslila konverzace.
            fileId = photo.fullFileId,
            thumbnailFileId = photo.fileId,
            contentDescription = stringResource(Res.string.media_photo),
            // Karta má poměr stran fotky, takže `Crop` nic neukrojí — jen zakryje zaokrouhlení.
            // Soubor bez náhledu rozměry nemá a karta je čtverec; tam by `Crop` ukrojil kraje.
            contentScale = if (photo.width > 0 && photo.height > 0) ContentScale.Crop else ContentScale.Fit,
            background = Color.Transparent,
            // Přiblížení jde do 5×; z obrázku velkého jako karta by se jen roztahovalo.
            fullResolution = zoomLoadsFullResolution,
            modifier = Modifier
                .fillMaxSize()
                .spoilerBlur(hasSpoiler, revealed, spoilerStyle),
        )

        if (hasSpoiler) {
            SpoilerOverlay(
                visible = !revealed,
                modifier = Modifier.fillMaxSize(),
                sensitive = photo.isSensitive,
            )
        }

        AnimatedVisibility(
            visible = photoText != null,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Poslední text se drží i během mizení, ať zvýraznění nezmizí skokem dřív než závoj.
            val shown = remember { mutableStateOf(photoText) }
            if (photoText != null) shown.value = photoText
            shown.value?.let { PhotoTextHighlights(it.text.lines, it.selected, onToggleTextLine) }
        }
    }
}

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
internal const val DISMISS_FADE = 2.5f

/** Ztmavení pod systémovými lištami na celé obrazovce; přechod sahá kus pod lištu. */
private const val BAR_SHADE_ALPHA = 0.45f

/** Posun videa šipkou; jako YouTube (dvojí ťuknutí skáče o 10 s, šipka jemněji). */
private const val KEY_SEEK_STEP_MS = 5_000L
private val BAR_SHADE_EXTRA = 24.dp

private val TOP_BAR_HEIGHT = 64.dp
private val BOTTOM_BAR_HEIGHT: Dp = 72.dp

private val REACTION_SIZE = 44.dp
private const val QUICK_REACTIONS = 6
private val PICKER_GRID_HEIGHT = 320.dp

/** Podsvícení použité reakce na tmavém pozadí. */
private const val CHOSEN_ALPHA = 0.22f

/** Datum pod jménem je druhotné. */
private const val SECONDARY_ALPHA = 0.75f

/** Co jde s reklamou nad videem (viz [ViewerVideoAds]). */
internal class VideoAdActions(
    val onPlaying: (messageId: Long?) -> Unit,
    val onOpen: () -> Unit,
    val onClose: () -> Unit,
    val onReport: () -> Unit,
    val onHeld: (Boolean) -> Unit,
)
