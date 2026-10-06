package eu.tintera.plume.features.share

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.features.media.SystemBarShades
import eu.tintera.plume.features.media.VideoPage
import eu.tintera.plume.features.media.ZoomableMediaPage
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import eu.tintera.plume.core.telegram.AttachmentMode
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.ui.DarkSystemBars
import eu.tintera.plume.core.ui.EmbeddedMediaPicker
import eu.tintera.plume.core.ui.embeddedMediaPickerAvailable
import eu.tintera.plume.core.ui.rememberAttachmentPicker
import eu.tintera.plume.core.ui.rememberEmbeddedMediaSelection
import eu.tintera.plume.core.ui.MEDIA_PAGE_VERTICAL_GAP
import eu.tintera.plume.core.ui.MediaCarousel
import eu.tintera.plume.core.ui.MediaPageLayout
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.features.conversation.AttachmentModeSheet
import eu.tintera.plume.features.conversation.getAttachmentModeIcon
import eu.tintera.plume.features.conversation.items.SpoilerOverlay
import eu.tintera.plume.features.conversation.items.rememberSpoilerBlurStyle
import eu.tintera.plume.features.conversation.items.spoilerBlur
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.attachment_mode_title
import eu.tintera.plume.resources.attachment_remove_photo
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_close
import eu.tintera.plume.resources.conversation_send
import eu.tintera.plume.resources.media_position
import eu.tintera.plume.resources.send_24dp
import eu.tintera.plume.resources.share_access_denied_gallery
import eu.tintera.plume.resources.share_access_denied_pick
import eu.tintera.plume.resources.share_access_denied_picker
import eu.tintera.plume.resources.share_access_denied_title
import eu.tintera.plume.resources.share_preparing
import eu.tintera.plume.resources.perm_media_24dp
import eu.tintera.plume.resources.share_to_add_media
import eu.tintera.plume.resources.share_to_caption
import eu.tintera.plume.resources.share_to_collapse_gallery
import eu.tintera.plume.resources.share_to_expand_gallery
import eu.tintera.plume.resources.keyboard_arrow_down_24dp
import eu.tintera.plume.resources.saved_messages
import eu.tintera.plume.resources.share_to_recipient
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Potvrzení sdílení do chatu vybraného v systémové nabídce (Direct Share).
 *
 * Hlavička říká KOMU to jde, uprostřed je snímek velký (a jde z něj sundat spoiler či mizení),
 * dole popisek a odeslání. Bez téhle obrazovky se sdílený snímek objevil jako miniatura nad
 * vstupním polem konverzace — cíl byl tedy jasný, ale to, co uživatel posílá, bylo 120 dp velké.
 *
 * **Snímky kreslí TENTÝŽ [MediaCarousel] jako prohlížeč médií** — sousedé u okrajů, jezdící
 * rozostřené pozadí, karta vepsaná mezi lišty — a nad ním leží vlastní hlavička, pole na popisek
 * a galerie. Dřív tu byl vlastní pager, karta i pozadí: sdílelo se jen rozostření, a jakmile se
 * doladil prohlížeč, vypadalo sdílení jako jiná aplikace. Bílé ovládání ([DarkSystemBars]) je
 * tatáž výjimka z rolí motivu jako v prohlížeči.
 */
@Composable
fun ShareToChatScreen(
    viewModel: ShareToChatViewModel,
    /** ✕ i gesto zpět: sdílení se zahodí a uživatel se vrátí do aplikace, ze které sdílel. */
    onClose: () -> Unit,
    /** Odesláno — otevře se konverzace, takže je vidět, že zpráva dorazila. */
    onSent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val close by rememberUpdatedState(onClose)
    val sent by rememberUpdatedState(onSent)
    LaunchedEffect(state.gone) { if (state.gone) close() }
    LaunchedEffect(state.sent) { if (state.sent) sent() }

    /*
     * Odchod BEZ odeslání sdílení zahodí — ať už ✕, systémovým gestem zpět (to jde přes
     * `NavDisplay`, ne přes obrazovku), nebo přepnutím sekce. Stejné pravidlo jako u výběru chatu:
     * bez toho by obsah zůstal viset a po obnově aktivity by obrazovka naskočila znovu.
     * Zahazuje se jen sdílení, které obrazovka ukazuje (`ShareToChatViewModel.abandon`).
     */
    DisposableEffect(Unit) { onDispose { viewModel.abandon() } }

    /*
     * Nové sdílení do téhož chatu obrazovku přenastaví (shell ji nechává, je to tatáž routa).
     * Carousel, galerie i odcházející snímky patřily předchozímu sdílení, takže se skládají
     * znovu — jinak by staré snímky z carouselu „odcházely" animací a galerie by držela
     * zaškrtnutí, které už neplatí.
     */
    key(state.contentId) {
        ShareToChatContent(viewModel, onClose, modifier)
    }
}

@Composable
private fun ShareToChatContent(
    viewModel: ShareToChatViewModel,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val attachments = state.attachments

    // Pozadí je tmavé v obou motivech, takže ikony lišt musí být světlé — jako v prohlížeči.
    DarkSystemBars()

    val snackbar = remember { SnackbarHostState() }
    val notice = state.notice?.resolve()
    LaunchedEffect(notice) {
        if (notice != null) {
            snackbar.showSnackbar(notice)
            viewModel.consumeNotice()
        }
    }

    /*
     * Počet stránek i klíč stránky čtou TÝŽ stav — pager leží v `BoxWithConstraints` a klíče se
     * obnovují až v subkompozici; se seznamem zachyceným v lambdě by po přidání fotky z galerie
     * přepočítal klíče s novým počtem nad starým seznamem (tatáž past jako v prohlížeči médií).
     */
    val shown = rememberShownAttachments(attachments.orEmpty())
    val latestAttachments by rememberUpdatedState(shown.pages)
    val pagerState = rememberPagerState { latestAttachments.size }
    val scope = rememberCoroutineScope()

    /*
     * Odebraný snímek dohrál odchod. Když je to zrovna zobrazená stránka, carousel napřed
     * dojede na souseda (další, u posledního předchozí) a teprve pak neviditelná stránka zmizí —
     * soused tak přijede tahem, místo aby na jejím místě naskočil. Pager drží pozici podle
     * klíče, takže odebrání stránky, ze které se už odjelo, s ním nepohne.
     */
    val onLeft: (String) -> Unit = { path ->
        scope.launch {
            val pages = latestAttachments
            val index = pages.indexOfFirst { it.path == path }
            if (index >= 0 && index == pagerState.currentPage && pages.size > 1) {
                pagerState.animateScrollToPage(if (index < pages.lastIndex) index + 1 else index - 1)
            }
            shown.remove(path)
        }
    }

    /*
     * Dobraná fotka se hned ukáže: pager přejede na konec dávky, kam se přidává. Bez toho by
     * výběr v galerii nebyl nahoře nijak vidět, jen by přibylo „N z M".
     */
    var shownCount by remember { mutableStateOf(-1) }
    LaunchedEffect(attachments?.size) {
        val count = attachments?.size ?: return@LaunchedEffect
        if (shownCount in 0 until count) pagerState.animateScrollToPage(count - 1)
        shownCount = count
    }

    /*
     * Galerie pod popiskem: vestavěný výběr fotek, přes který jde k dávce dobrat další snímky.
     *
     * Je tu pořád, ve dvou polohách (dole a nahoře pod hlavičkou) — zavřít nejde. Jedinou
     * výjimkou je klávesnice při psaní popisku: zabírá totéž místo, a obě naráz by snímek
     * smrskly na proužek. Jakmile klávesnice zmizí, galerie je zpátky dole sama. Kde vestavěný
     * výběr není (starší Android), obrazovka zůstává bez galerie.
     */
    /*
     * Celá obrazovka, jako v prohlížeči médií: ťuknutí na snímek schová hlavičku, pole i galerii,
     * pozadí zčerná a snímek jde od kraje ke kraji — fotku jde přiblížit, video přehrát. Zpátky
     * vede ťuknutí, tah dolů nebo gesto zpět. Dřív šlo sdílený snímek vidět jen jako kartu mezi
     * lištami a zkontrolovat, co vlastně odchází, nešlo.
     */
    var immersive by rememberSaveable { mutableStateOf(false) }
    val immersionState = animateFloatAsState(
        targetValue = if (immersive) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "share-immersion",
    )
    // Pružina přestřelí; záporné odsazení nebo zaoblení by layout odmítl.
    val immersion = { immersionState.value.coerceIn(0f, 1f) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val enterImmersive = {
        // Klávesnice by na celé obrazovce zůstala viset přes snímek.
        keyboard?.hide()
        focusManager.clearFocus()
        immersive = true
    }
    // Zavírací tah na celé obrazovce; po návratu do galerie se karta vrátí na místo.
    val dismissDrag = remember { Animatable(0f) }
    LaunchedEffect(immersive) { if (!immersive) dismissDrag.animateTo(0f) }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = immersive,
        onBackCompleted = { immersive = false },
    )

    val galleryAvailable = embeddedMediaPickerAvailable()
    // `WindowInsets.isImeVisible` je jen na Androidu; výška klávesnice funguje i na iOS.
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    // Na celé obrazovce galerie ustoupí: je to cizí okno nad vším a snímek by zakryla.
    val galleryShown = galleryAvailable && !imeVisible && attachments != null && !immersive
    /*
     * Bez vestavěné galerie (Android pod 14, nebo 14 bez SDK Extensions 15) jde dávku doplnit
     * systémovým výběrem z ikony v poli — jinak by šlo poslat jen to, co přišlo sdílením.
     * Tamější fotky picker nevydá pod stejným identifikátorem jako sdílení, takže tu nejde
     * poznat, že uživatel vybral tutéž fotku podruhé.
     */
    val systemPicker = rememberAttachmentPicker(
        onPicked = viewModel::addAttachments,
        onError = viewModel::reportError,
    )
    val mediaSelection = rememberEmbeddedMediaSelection()

    var galleryExpanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(galleryShown) { if (!galleryShown) galleryExpanded = false }

    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Obsah galerie jede pod navigační lištu, jako v sheetu v konverzaci.
    val collapsedHeight = navigationBar + minOf(windowHeight * GALLERY_COLLAPSED_FRACTION, GALLERY_COLLAPSED_MAX)
    var headerHeight by remember { mutableStateOf(0.dp) }
    // Kde začíná řádek s polem; pod ním už karta nesmí být.
    var captionTop by remember { mutableStateOf(windowHeight) }

    // Zpět rozbalenou galerii nejdřív sbalí, teprve další zpět odchází — jako šipka v hlavičce.
    // `NavigationBackHandler`, ne `BackHandler`, ten by zabil predictive back.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = galleryShown && galleryExpanded,
        onBackCompleted = { galleryExpanded = false },
    )

    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        // Rozbalená galerie sahá až pod hlavičku; ta zůstává vidět, ať je jasné, komu se vybírá.
        val expandedHeight = maxHeight - headerHeight

        /*
         * Snímky jsou TENTÝŽ carousel jako v prohlížeči médií: sousedé vykukují u okrajů,
         * rozostřené pozadí jede s tahem. Karta se vepisuje mezi hlavičku a pole na popisek
         * (`MediaPageLayout`) — carousel sám jde přes celou obrazovku, lišty leží nad ním.
         */
        MediaCarousel(
            pagerState = pagerState,
            pageKey = { latestAttachments.getOrNull(it)?.path ?: it },
            immersion = immersion,
            modifier = Modifier.fillMaxSize(),
            backdrop = { index ->
                latestAttachments.getOrNull(index)?.let { attachment ->
                    AsyncImage(
                        // Coil potřebuje schéma, holá cesta by se vyhodnotila jako relativní URI.
                        model = "file://${attachment.previewPath}",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            },
        ) { page ->
            latestAttachments.getOrNull(page.index)?.let { attachment ->
                // Stránky nejsou 1:1 s přílohami (odcházející snímek v nich ještě chvíli je),
                // takže se index do ViewModelu hledá podle cesty.
                val index = { attachments.orEmpty().indexOfFirst { it.path == attachment.path } }
                SharedPhoto(
                    attachment = attachment,
                    canSelfDestruct = state.canSelfDestruct,
                    leaving = shown.isLeaving(attachment.path),
                    onLeft = { onLeft(attachment.path) },
                    onRemove = { index().takeIf { it >= 0 }?.let(viewModel::removeAttachment) },
                    onSetMode = { mode -> index().takeIf { it >= 0 }?.let { viewModel.setMode(it, mode) } },
                    isCurrent = page.isCurrent,
                    immersive = immersive,
                    immersion = immersion,
                    dismissDrag = dismissDrag,
                    onToggleImmersive = { if (immersive) immersive = false else enterImmersive() },
                    onEngage = enterImmersive,
                    onLeaveImmersive = { immersive = false },
                    reservedTop = headerHeight + MEDIA_PAGE_VERTICAL_GAP,
                    // Horní odsazení řádku s polem dělá mezeru samo.
                    reservedBottom = (maxHeight - captionTop).coerceAtLeast(0.dp),
                    cardBias = page.cardBias,
                )
            }
        }

        // Ovládání leží na rozostřené fotce, takže bílá — role z `colorScheme` by ve světlém
        // motivu dala tmavý text na tmavé ploše. Tatáž výjimka jako v prohlížeči médií.
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = !immersive,
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
                ) {
                Recipient(
                    title = state.chatTitle,
                    avatarFileId = state.avatarFileId,
                    chatId = state.chatId,
                    isSavedMessages = state.isSavedMessages,
                    kind = state.kind,
                    position = latestAttachments
                        .takeIf { it.size > 1 }
                        ?.let { stringResource(Res.string.media_position, pagerState.currentPage + 1, it.size) },
                    onClose = onClose,
                    onCollapseGallery = if (galleryShown && galleryExpanded) {
                        { galleryExpanded = false }
                    } else {
                        null
                    },
                    modifier = Modifier.onSizeChanged { headerHeight = with(density) { it.height.toDp() } },
                )
                }

                // Místo pro kartu; snímky samotné kreslí carousel pod touhle vrstvou.
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (attachments == null) {
                        // Soubory se ještě kopírují (velké video chvíli trvá) — obrazovka je
                        // vidět hned, ať je jasné, kam obsah míří.
                        Preparing()
                    } else if (attachments.isEmpty() && state.accessDenied) {
                        // Sdílený snímek nedorazil (Android k němu Plume nepustil) — radu, kde
                        // ho najít, místo prázdné plochy. Zmizí s prvním vybraným snímkem.
                        AccessDenied(
                            inGallery = galleryShown,
                            onPick = systemPicker::pickMedia,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }

                    // Nad polem, ne u spodku obrazovky: galerii kreslí cizí okno (`SurfaceView`),
                    // které leží nad vším v naší vrstvě, a hláška pod ním by nebyla vidět.
                    SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter)) {
                        Snackbar(it)
                    }
                }

                /*
                 * Pole na celé obrazovce zmizí (jen průhledností, takže do posledního snímku drží
                 * místo). Kde začínalo, si obrazovka pamatuje dál — karta se při návratu vepíše
                 * tam, odkud odjela.
                 */
                AnimatedVisibility(
                    visible = !immersive,
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    modifier = Modifier.onGloballyPositioned {
                        if (!immersive) captionTop = with(density) { it.positionInParent().y.toDp() }
                    },
                ) {
                CaptionBar(
                    caption = viewModel.caption,
                    onCaptionChange = viewModel::onCaptionChange,
                    // Dokud se soubory připravují (i ty dobrané z galerie), není co poslat —
                    // odeslání by dávku poslalo bez nich.
                    enabled = !attachments.isNullOrEmpty() && !state.sending && !mediaSelection.preparing,
                    sending = state.sending,
                    onSend = viewModel::send,
                    onAddMedia = if (!galleryAvailable && attachments != null) systemPicker::pickMedia else null,
                    // S galerií pod sebou spodní lišta pole nic neodsouvá — sahá k ní galerie.
                    modifier = if (galleryShown) Modifier else Modifier.navigationBarsPadding().imePadding(),
                )
                }

                /*
                 * Sbalená galerie má v rozvržení vlastní místo — snímek nad ní i pole jsou vidět
                 * celé. Rozbalená už místo nezabírá, jen vyjede PŘES snímek a pole až pod hlavičku
                 * (tak to dělají Google Zprávy); zmenšovat snímek na proužek by nic nepřineslo.
                 */
                if (galleryShown) Spacer(Modifier.height(collapsedHeight))
            }

            // Na celé obrazovce ztmavené systémové lišty, jako v prohlížeči.
            SystemBarShades(immersion, Modifier)

            if (galleryShown) {
                GalleryPanel(
                    expanded = galleryExpanded,
                    onExpandedChange = { galleryExpanded = it },
                    collapsedHeight = collapsedHeight,
                    expandedHeight = expandedHeight,
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) { pickerModifier ->
                    // Po odebrání snímku ✕ se galerie otevře znovu: zaškrtnutí si obnovuje jen
                    // při otevření a odebraná fotka by v ní jinak zůstala zaškrtnutá.
                    key(state.removals) {
                        EmbeddedMediaPicker(
                            selection = mediaSelection,
                            pickedPaths = attachments.orEmpty().map { it.path },
                            // Sdílená fotka je v galerii taky — ťuknutí na ni ji nepřidá podruhé.
                            sources = state.sources,
                            allowPhotos = true,
                            allowVideos = true,
                            expanded = galleryExpanded,
                            onPicked = viewModel::addAttachments,
                            onUnpicked = viewModel::removeAttachments,
                            onError = viewModel::reportError,
                            // „Hotovo" galerii nezavírá, jen ji vrátí dolů — panel má dvě polohy
                            // a zmizet nemá (vyjma klávesnice, viz výš).
                            onDone = { galleryExpanded = false },
                            modifier = pickerModifier,
                        )
                    }
                }
            }
        }
    }
}

/** Komu to jde: ✕, tvář a jméno chatu, u víc snímků i pozice v dávce. */
@Composable
private fun Recipient(
    title: String,
    avatarFileId: Int?,
    chatId: Long,
    isSavedMessages: Boolean,
    kind: ChatKind,
    position: String?,
    onClose: () -> Unit,
    /** Rozbalená galerie: místo ✕ šipka dolů, která ji sbalí (jako v Google Zprávách). */
    onCollapseGallery: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().height(HEADER_HEIGHT).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (onCollapseGallery != null) {
            IconButton(onClick = onCollapseGallery) {
                Icon(
                    painterResource(Res.drawable.keyboard_arrow_down_24dp),
                    contentDescription = stringResource(Res.string.share_to_collapse_gallery),
                )
            }
        } else {
            IconButton(onClick = onClose) {
                Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.common_close))
            }
        }
        if (title.isNotEmpty()) {
            Avatar(
                chatId = chatId,
                title = title,
                avatarFileId = avatarFileId,
                size = AVATAR_SIZE,
                isSavedMessages = isSavedMessages,
                shape = kind.avatarShape(),
                // Avatar leží na rozostřené fotce, ne na ploše motivu; barvu popředí si podle ní
                // dopočítává záložka Uložených zpráv.
                backdrop = Color.Black,
            )
            Text(
                // Uložené zprávy nesou v TDLib jméno vlastního účtu — „Komu: Jan Novák" u nich mátlo.
                text = stringResource(
                    Res.string.share_to_recipient,
                    if (isSavedMessages) stringResource(Res.string.saved_messages) else title,
                ),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
        } else {
            Box(Modifier.weight(1f))
        }
        position?.let {
            Text(it, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp))
        }
    }
}

/**
 * Jeden sdílený snímek: karta v carouselu a na ní ✕ a volba způsobu odeslání.
 *
 * **Stránky jsou TYTÉŽ jako v prohlížeči médií** ([ZoomableMediaPage], [VideoPage]), jen nakrmené
 * souborem na disku místo zprávy. Ťuknutí proto i tady přepne na celou obrazovku, kde jde fotku
 * přiblížit a video přehrát — dřív byla karta jen obrázek a uživatel si nemohl prohlédnout, co
 * posílá. **Karta má rozměry SNÍMKU, ne stránky** — to zařídí `MediaPageLayout` z poměru stran.
 *
 * Spoiler je ve sdílení volba odesílatele, ne zakrytý cizí obsah: v galerii je snímek zakrytý
 * (tak ho uvidí příjemce), na celé obrazovce odkrytý — prohlížet si vlastní fotku přes rozmazání
 * nemá smysl.
 */
@Composable
private fun SharedPhoto(
    attachment: OutgoingAttachment,
    canSelfDestruct: Boolean,
    /** Snímek z dávky odebraný — karta se zmenší a zprůhlední, pak [onLeft]. */
    leaving: Boolean,
    onLeft: () -> Unit,
    onRemove: () -> Unit,
    onSetMode: (AttachmentMode) -> Unit,
    isCurrent: Boolean,
    immersive: Boolean,
    immersion: () -> Float,
    dismissDrag: Animatable<Float, *>,
    onToggleImmersive: () -> Unit,
    onEngage: () -> Unit,
    onLeaveImmersive: () -> Unit,
    reservedTop: Dp,
    reservedBottom: Dp,
    cardBias: () -> Float,
) {
    val spoilerStyle = rememberSpoilerBlurStyle()
    var modeSheet by remember { mutableStateOf(false) }
    val isVideo = attachment is OutgoingAttachment.Video
    val hidden = attachment.hasSpoiler && !immersive

    if (modeSheet) {
        AttachmentModeSheet(
            currentMode = attachment.mode,
            isVideo = isVideo,
            selfDestructAllowed = canSelfDestruct,
            onSelectMode = onSetMode,
            onDismiss = { modeSheet = false },
        )
    }

    /*
     * Odebraný snímek nezmizí skokem: karta se stáhne a zprůhlední, a teprve pak ji carousel
     * odebere. Effects spec, ne spatial — pružina by průhlednost přestřelila do bliknutí.
     * Znovu zaškrtnutý snímek se během odchodu vrátí toutéž animací zpátky.
     */
    val left by rememberUpdatedState(onLeft)
    val presence by animateFloatAsState(
        targetValue = if (leaving) 0f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        finishedListener = { if (it == 0f) left() },
    )
    val pageModifier = Modifier.fillMaxSize().graphicsLayer {
        alpha = presence
        val scale = LEAVING_SCALE + (1f - LEAVING_SCALE) * presence
        scaleX = scale
        scaleY = scale
    }
    // Na celé obrazovce vede svislý tah zpátky do galerie; v galerii nemá kam.
    val onDismiss = onLeaveImmersive.takeIf { immersive }

    // ✕ a volba režimu patří galerii; na celé obrazovce by překážely snímku.
    val badges: @Composable BoxScope.() -> Unit = {
        AnimatedVisibility(
            visible = !immersive,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                FilledTonalIconButton(
                    onClick = onRemove,
                    modifier = Modifier.align(Alignment.TopEnd).padding(BADGE_INSET).size(BADGE_BUTTON),
                ) {
                    Icon(
                        painterResource(Res.drawable.close_24dp),
                        contentDescription = stringResource(Res.string.attachment_remove_photo),
                        modifier = Modifier.size(20.dp),
                    )
                }

                // Spoiler, mizení i „jako soubor" nabízí tentýž sheet jako nad vstupním polem —
                // jedno místo, kde se o způsobu odeslání rozhoduje.
                val custom = attachment.mode !is AttachmentMode.Standard
                FilledTonalIconButton(
                    onClick = { modeSheet = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(BADGE_INSET).size(BADGE_BUTTON),
                    colors = if (custom) {
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        IconButtonDefaults.filledTonalIconButtonColors()
                    },
                ) {
                    Icon(
                        painterResource(getAttachmentModeIcon(attachment.mode, isVideo)),
                        contentDescription = stringResource(Res.string.attachment_mode_title),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }

    when (attachment) {
        is OutgoingAttachment.Video -> VideoPage(
            aspectRatio = attachment.previewRatio ?: 1f,
            durationSeconds = attachment.durationSeconds,
            // Popisek se tu teprve píše, a to do pole pod kartou.
            caption = "",
            isCurrent = isCurrent,
            immersive = immersive,
            immersion = immersion,
            reservedTop = reservedTop,
            reservedBottom = reservedBottom,
            dismissDrag = dismissDrag,
            onDismiss = onDismiss,
            onTap = onToggleImmersive,
            onEngage = onEngage,
            // Soubor je celý na disku, nic se nestahuje.
            source = { FileState.Ready(attachment.path) },
            streamable = false,
            requestFrom = {},
            hasSpoiler = attachment.hasSpoiler,
            // Zakrytý spoiler odkryje (a na celou obrazovku přepne) první ťuknutí.
            revealed = immersive,
            onReveal = onEngage,
            modifier = pageModifier,
            cardBias = cardBias,
            overlay = badges,
            thumbnail = {
                AsyncImage(
                    model = "file://${attachment.previewPath}",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().spoilerBlur(
                        hasSpoiler = attachment.hasSpoiler,
                        revealed = !hidden,
                        style = spoilerStyle,
                    ),
                )
            },
        )

        else -> ZoomableMediaPage(
            aspectRatio = attachment.previewRatio ?: 1f,
            caption = "",
            isCurrent = isCurrent,
            immersive = immersive,
            immersion = immersion,
            reservedTop = reservedTop,
            reservedBottom = reservedBottom,
            dismissDrag = dismissDrag,
            onDismiss = onDismiss,
            onTap = onToggleImmersive,
            onEngage = onEngage,
            modifier = pageModifier,
            cardBias = cardBias,
            overlay = badges,
        ) {
            AsyncImage(
                model = "file://${attachment.previewPath}",
                contentDescription = null,
                // Karta už poměr stran drží, takže `Crop` jen dorovná zaokrouhlení a nic
                // neukrojí — sdílený snímek má být vidět CELÝ, je to to jediné, co odejde.
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().spoilerBlur(
                    hasSpoiler = attachment.hasSpoiler,
                    revealed = !hidden,
                    style = spoilerStyle,
                ),
            )
            if (attachment.hasSpoiler) SpoilerOverlay(visible = hidden)
        }
    }
}

/** Soubory se kopírují do cache aplikace; velké video to chvíli trvá. */
@Composable
private fun Preparing() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LoadingIndicator(color = LocalContentColor.current)
        Text(stringResource(Res.string.share_preparing), style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Sdílený snímek nejde přečíst: aplikace, ze které se sdílelo, k němu Plume nepustila.
 *
 * Tak dopadne screenshot sdílený z náhledu po vyfocení obrazovky rovnou na kontakt — intent
 * posílá Android System Intelligence bez oprávnění ke snímku (viz `ShareRequests.prepared`).
 * Snímek je ale v galerii nejnovější, takže stačí ťuknout na první fotku dole; kde vestavěná
 * galerie není, otevře se systémový výběr.
 */
@Composable
private fun AccessDenied(inGallery: Boolean, onPick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(Res.string.share_access_denied_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(
                if (inGallery) Res.string.share_access_denied_gallery else Res.string.share_access_denied_picker,
            ),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (!inGallery) {
            FilledTonalButton(onClick = onPick) { Text(stringResource(Res.string.share_access_denied_pick)) }
        }
    }
}

/**
 * Popisek a odeslání.
 *
 * Karta s polem a kulaté tlačítko vedle ní — tentýž tvar jako vstupní pole konverzace, aby
 * odeslání odsud vypadalo jako odeslání odtamtud. Pole je `BasicTextField` s dekorací ze stejného
 * důvodu: `TextField` má odsazení 16 dp napevno a v nižší kartě by text seděl mimo střed.
 */
@Composable
private fun CaptionBar(
    caption: androidx.compose.ui.text.input.TextFieldValue,
    onCaptionChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    enabled: Boolean,
    sending: Boolean,
    onSend: () -> Unit,
    /** Systémový výběr fotek; jen kde není galerie pod polem, jinak `null`. */
    onAddMedia: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(FIELD_CORNER),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                if (onAddMedia != null) {
                    // 4 dp = (56 − 48) / 2: ikona sedí na středu jednořádkového pole a u víc řádků
                    // zůstane u posledního, jako sponka ve vstupním poli konverzace.
                    IconButton(onClick = onAddMedia, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)) {
                        Icon(
                            painterResource(Res.drawable.perm_media_24dp),
                            contentDescription = stringResource(Res.string.share_to_add_media),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                BasicTextField(
                    value = caption,
                    onValueChange = onCaptionChange,
                    textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 4,
                    interactionSource = interaction,
                    keyboardOptions = KeyboardOptions(
                        // Popisek jsou věty, ne jedno slovo — klávesnice má nabídnout velké písmeno sama.
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default,
                    ),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        TextFieldDefaults.DecorationBox(
                            value = caption.text,
                            innerTextField = inner,
                            enabled = true,
                            singleLine = false,
                            visualTransformation = VisualTransformation.None,
                            interactionSource = interaction,
                            placeholder = { Text(stringResource(Res.string.share_to_caption)) },
                            colors = TextFieldDefaults.colors(
                                // Kontejner i podtržení nese karta kolem; pole samo nekreslí nic.
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            container = {},
                        )
                    },
                )
            }
        }

        FilledIconButton(
            onClick = onSend,
            enabled = enabled,
            // 56 dp je minimální výška jednořádkového pole, takže tlačítko sedí na jeho střed
            // a u víceřádkového popisku zůstane u posledního řádku — jako v konverzaci.
            modifier = Modifier.size(SEND_BUTTON),
            shape = CircleShape,
        ) {
            if (sending) {
                CircularProgressIndicator(
                    color = LocalContentColor.current,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Icon(
                    painterResource(Res.drawable.send_24dp),
                    contentDescription = stringResource(Res.string.conversation_send),
                )
            }
        }
    }
}

/** Poměr stran připraveného snímku; `null`, když ho platforma nezjistila. */
private val OutgoingAttachment.previewRatio: Float?
    get() {
        val (width, height) = when (this) {
            is OutgoingAttachment.Photo -> width to height
            is OutgoingAttachment.Video -> width to height
            is OutgoingAttachment.Document -> return null
        }
        return if (width > 0 && height > 0) width.toFloat() / height else null
    }

/** Co se dá vykreslit: u videa jeho náhled, jinak soubor sám. */
private val OutgoingAttachment.previewPath: String
    get() = when (this) {
        is OutgoingAttachment.Video -> thumbnail?.path ?: path
        else -> path
    }

private val HEADER_HEIGHT = 64.dp
private val AVATAR_SIZE = 32.dp
private val BADGE_BUTTON = 40.dp
private val BADGE_INSET = 8.dp
/**
 * Stránky carouselu: přílohy dávky a k nim snímky, které z ní právě odcházejí.
 *
 * Pager odebrání stránky animovat neumí — chybějící stránka prostě zmizí a soused naskočí na
 * její místo. Odebraný snímek proto v carouselu chvíli zůstane (na svém dřívějším místě),
 * dohraje odchod a teprve pak ho obrazovka odebere ([remove]). Znovu zaškrtnutý snímek
 * z odcházejících vypadne a vrátí se.
 */
private class ShownAttachments {
    private val leaving = LinkedHashMap<String, Pair<Int, OutgoingAttachment>>()
    private var previous: List<OutgoingAttachment>? = null
    private var version by mutableIntStateOf(0)
    /** Stav, ne pole: po dohraném odchodu ([remove]) se musí obrazovka i pager překreslit. */
    var pages: List<OutgoingAttachment> by mutableStateOf(emptyList())
        private set

    /** Volá se při každé změně dávky, v kompozici — odchod se musí zjistit dřív, než se vykreslí. */
    fun update(current: List<OutgoingAttachment>) {
        val now = current.mapTo(HashSet()) { it.path }
        previous?.forEachIndexed { index, attachment ->
            if (attachment.path !in now && attachment.path !in leaving) leaving[attachment.path] = index to attachment
        }
        now.forEach { leaving.remove(it) }
        previous = current
        rebuild(current)
    }

    fun isLeaving(path: String): Boolean {
        version // čtení kvůli rekompozici
        return path in leaving
    }

    fun remove(path: String) {
        if (leaving.remove(path) != null) {
            rebuild(previous.orEmpty())
            version++
        }
    }

    private fun rebuild(current: List<OutgoingAttachment>) {
        pages = current.toMutableList().apply {
            leaving.values.sortedBy { it.first }.forEach { (index, attachment) -> add(index.coerceAtMost(size), attachment) }
        }
    }
}

@Composable
private fun rememberShownAttachments(current: List<OutgoingAttachment>): ShownAttachments {
    val shown = remember { ShownAttachments() }
    remember(current) { shown.update(current) }
    return shown
}

/**
 * Galerie pod popiskem jako v Google Zprávách: panel se zaoblenými horními rohy a úchytem.
 *
 * **Sbalený jen vykukuje** — náhled mřížky, ve kterém jde vybírat, ale ne posouvat (tak se
 * vestavěný picker ve sbaleném stavu chová sám). Rozbalený sahá až pod hlavičku a listuje se
 * v něm.
 *
 * **Úchyt je úchyt, ne tlačítko.** Panel jede s prstem (výška sleduje tah) a po puštění dosedne
 * tam, kam patří: podle rychlosti švihnutí, a když žádné není, podle toho, kam ho uživatel
 * dotáhl — přes půl cesty nahoře, jinak dole. **Polohy jsou jen dvě, zavřít tahem nejde**:
 * galerie k obrazovce patří a nemá kam zmizet. Ťuknutí prstem nedělá nic;
 * první verze úchytem jen přepínala, a úchyt, který nejde táhnout, je tlačítko převlečené za
 * něco jiného. Čtečka obrazovky tahat neumí, proto má rozbalení a sbalení jako akci.
 *
 * **Tah se chytá jen na úchytu**, ne na mřížce: tu kreslí cizí okno (`SurfaceView`), které si
 * dotyky bere samo a k nám je nepustí.
 */
@Composable
private fun GalleryPanel(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    collapsedHeight: Dp,
    expandedHeight: Dp,
    modifier: Modifier = Modifier,
    picker: @Composable (Modifier) -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Dp>()
    val height = remember { Animatable(collapsedHeight, Dp.VectorConverter) }
    var dragging by remember { mutableStateOf(false) }
    val expandedNow by rememberUpdatedState(expanded)

    // Změna zvenku (šipka v hlavičce, gesto zpět, jiná velikost okna) panel dopraví na místo.
    // Během tahu ne — tam výšku drží prst.
    LaunchedEffect(expanded, collapsedHeight, expandedHeight) {
        if (!dragging) height.animateTo(if (expanded) expandedHeight else collapsedHeight, spec)
    }

    fun settle(velocity: Float) {
        val current = height.value
        val fling = with(density) { GALLERY_FLING_VELOCITY.toPx() }
        val expand = when {
            velocity < -fling -> true
            velocity > fling -> false
            else -> current > (collapsedHeight + expandedHeight) / 2
        }
        onExpandedChange(expand)
        scope.launch { height.animateTo(if (expand) expandedHeight else collapsedHeight, spec) }
    }

    val expandLabel = stringResource(Res.string.share_to_expand_gallery)
    val collapseLabel = stringResource(Res.string.share_to_collapse_gallery)
    Surface(
        shape = RoundedCornerShape(topStart = GALLERY_CORNER, topEnd = GALLERY_CORNER),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        // Pružina přestřelí; záporná výška by layout shodila.
        modifier = modifier.fillMaxWidth().height(height.value.coerceAtLeast(0.dp)),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(GALLERY_HANDLE_AREA)
                    .semantics {
                        // Jen pro čtečku obrazovky, dotyk přepínat nemá.
                        if (expandedNow) {
                            collapse { onExpandedChange(false); true }
                        } else {
                            expand { onExpandedChange(true); true }
                        }
                        customActions = listOf(
                            CustomAccessibilityAction(if (expandedNow) collapseLabel else expandLabel) {
                                onExpandedChange(!expandedNow)
                                true
                            },
                        )
                    }
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            val next = height.value - with(density) { delta.toDp() }
                            // Mezi dvěma polohami, nikam dál.
                            scope.launch { height.snapTo(next.coerceIn(collapsedHeight, expandedHeight)) }
                        },
                        onDragStarted = { dragging = true },
                        onDragStopped = { velocity ->
                            dragging = false
                            settle(velocity)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = 32.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                )
            }
            picker(Modifier.fillMaxWidth().weight(1f))
        }
    }
}

/** Jak malá je karta odebraného snímku na konci odchodu. */
private const val LEAVING_SCALE = 0.85f

private val GALLERY_CORNER = 28.dp
private val GALLERY_HANDLE_AREA = 36.dp
/** Sbalená galerie jen vykukuje, jako ve vzoru z Google Zpráv — fotka nahoře zůstává hlavní. */
private const val GALLERY_COLLAPSED_FRACTION = 0.28f
private val GALLERY_COLLAPSED_MAX = 300.dp
/** Rychlost švihnutí (za sekundu), od které rozhoduje směr, a ne to, kam panel dojel. */
private val GALLERY_FLING_VELOCITY = 400.dp

private val FIELD_CORNER = 28.dp
private val SEND_BUTTON = 56.dp
