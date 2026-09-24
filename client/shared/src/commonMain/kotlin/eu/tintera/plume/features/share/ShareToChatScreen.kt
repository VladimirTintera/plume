package eu.tintera.plume.features.share

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import eu.tintera.plume.core.telegram.AttachmentMode
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.ui.DarkSystemBars
import eu.tintera.plume.core.ui.EmbeddedMediaPicker
import eu.tintera.plume.core.ui.embeddedMediaPickerAvailable
import eu.tintera.plume.core.ui.rememberEmbeddedMediaSelection
import eu.tintera.plume.core.ui.MEDIA_CARD_CORNER
import eu.tintera.plume.core.ui.ambientBlurStyle
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
import eu.tintera.plume.resources.share_preparing
import eu.tintera.plume.resources.share_to_add_media
import eu.tintera.plume.resources.share_to_caption
import eu.tintera.plume.resources.share_to_collapse_gallery
import eu.tintera.plume.resources.keyboard_arrow_down_24dp
import eu.tintera.plume.resources.perm_media_24dp
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
 * **Vzhled je jazyk prohlížeče médií**: rozostřený snímek jako pozadí ([ambientBlurStyle]),
 * zaoblená karta a bílé ovládání ([DarkSystemBars]). Sdílená fotka je tu hlavní obsah stejně
 * jako v galerii, takže by dvě různá pojetí téhož byla zbytečná.
 */
@OptIn(ExperimentalLayoutApi::class)
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
    val attachments = state.attachments

    val close by rememberUpdatedState(onClose)
    val sent by rememberUpdatedState(onSent)
    LaunchedEffect(state.gone) { if (state.gone) close() }
    LaunchedEffect(state.sent) { if (state.sent) sent() }

    /*
     * Odchod BEZ odeslání sdílení zahodí — ať už ✕, systémovým gestem zpět (to jde přes
     * `NavDisplay`, ne přes obrazovku), nebo přepnutím sekce. Stejné pravidlo jako u výběru chatu:
     * bez toho by obsah zůstal viset a po obnově aktivity by obrazovka naskočila znovu.
     */
    DisposableEffect(Unit) { onDispose { viewModel.abandon() } }

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

    val pagerState = rememberPagerState { attachments?.size ?: 0 }
    val current = attachments?.getOrNull(pagerState.currentPage)

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
     * Při psaní popisku ustoupí klávesnici — obě by se o spodek obrazovky přetahovaly a fotka
     * nahoře by se smrskla na proužek. Zpátky ji vrátí tlačítko v poli; totéž po „Hotovo"
     * v galerii. Kde vestavěný výběr není (starší Android), obrazovka zůstává bez galerie.
     */
    val galleryAvailable = embeddedMediaPickerAvailable()
    var galleryOpen by rememberSaveable { mutableStateOf(true) }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) { if (imeVisible) galleryOpen = false }
    val galleryShown = galleryAvailable && galleryOpen && !imeVisible && attachments != null
    val mediaSelection = rememberEmbeddedMediaSelection()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current

    var galleryExpanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(galleryShown) { if (!galleryShown) galleryExpanded = false }

    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Obsah galerie jede pod navigační lištu, jako v sheetu v konverzaci.
    val collapsedHeight = navigationBar + minOf(windowHeight * GALLERY_COLLAPSED_FRACTION, GALLERY_COLLAPSED_MAX)
    var headerHeight by remember { mutableStateOf(0.dp) }

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

        AmbientBackdrop(previewPath = current?.previewPath, modifier = Modifier.fillMaxSize())

        // Ovládání leží na rozostřené fotce, takže bílá — role z `colorScheme` by ve světlém
        // motivu dala tmavý text na tmavé ploše. Tatáž výjimka jako v prohlížeči médií.
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(Modifier.fillMaxSize()) {
                Recipient(
                    title = state.chatTitle,
                    avatarFileId = state.avatarFileId,
                    chatId = state.chatId,
                    isSavedMessages = state.isSavedMessages,
                    kind = state.kind,
                    position = attachments
                        ?.takeIf { it.size > 1 }
                        ?.let { stringResource(Res.string.media_position, pagerState.currentPage + 1, it.size) },
                    onClose = onClose,
                    onCollapseGallery = if (galleryShown && galleryExpanded) {
                        { galleryExpanded = false }
                    } else {
                        null
                    },
                    modifier = Modifier.onSizeChanged { headerHeight = with(density) { it.height.toDp() } },
                )

                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (attachments == null) {
                        // Soubory se ještě kopírují (velké video chvíli trvá) — obrazovka je
                        // vidět hned, ať je jasné, kam obsah míří.
                        Preparing()
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            key = { attachments.getOrNull(it)?.path ?: it },
                            modifier = Modifier.fillMaxSize(),
                        ) { page ->
                            attachments.getOrNull(page)?.let { attachment ->
                                SharedPhoto(
                                    attachment = attachment,
                                    canSelfDestruct = state.canSelfDestruct,
                                    onRemove = { viewModel.removeAttachment(page) },
                                    onSetMode = { viewModel.setMode(page, it) },
                                )
                            }
                        }
                    }

                    // Nad polem, ne u spodku obrazovky: galerii kreslí cizí okno (`SurfaceView`),
                    // které leží nad vším v naší vrstvě, a hláška pod ním by nebyla vidět.
                    SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter)) {
                        Snackbar(it)
                    }
                }

                CaptionBar(
                    caption = viewModel.caption,
                    onCaptionChange = viewModel::onCaptionChange,
                    // Dokud se soubory připravují, není co poslat.
                    enabled = !attachments.isNullOrEmpty() && !state.sending,
                    sending = state.sending,
                    onSend = viewModel::send,
                    onOpenGallery = if (galleryAvailable && !galleryShown && attachments != null) {
                        {
                            focus.clearFocus()
                            keyboard?.hide()
                            galleryOpen = true
                        }
                    } else {
                        null
                    },
                    // S galerií pod sebou spodní lišta pole nic neodsouvá — sahá k ní galerie.
                    modifier = if (galleryShown) Modifier else Modifier.navigationBarsPadding().imePadding(),
                )

                /*
                 * Sbalená galerie má v rozvržení vlastní místo — snímek nad ní i pole jsou vidět
                 * celé. Rozbalená už místo nezabírá, jen vyjede PŘES snímek a pole až pod hlavičku
                 * (tak to dělají Google Zprávy); zmenšovat snímek na proužek by nic nepřineslo.
                 */
                if (galleryShown) Spacer(Modifier.height(collapsedHeight))
            }

            if (galleryShown) {
                val height by animateDpAsState(
                    targetValue = if (galleryExpanded) expandedHeight else collapsedHeight,
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                )
                GalleryPanel(
                    expanded = galleryExpanded,
                    onExpandedChange = { galleryExpanded = it },
                    onDismiss = { galleryOpen = false },
                    height = height,
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) { pickerModifier ->
                    // Po odebrání snímku ✕ se galerie otevře znovu: zaškrtnutí si obnovuje jen
                    // při otevření a odebraná fotka by v ní jinak zůstala zaškrtnutá.
                    key(state.removals) {
                        EmbeddedMediaPicker(
                            selection = mediaSelection,
                            pickedPaths = attachments.orEmpty().map { it.path },
                            allowPhotos = true,
                            allowVideos = true,
                            expanded = galleryExpanded,
                            onPicked = viewModel::addAttachments,
                            onUnpicked = viewModel::removeAttachments,
                            onError = viewModel::reportError,
                            onDone = { galleryOpen = false },
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
                text = stringResource(Res.string.share_to_recipient, title),
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

/** Jeden sdílený snímek: karta jako v galerii, ✕ na odebrání a volba režimu. */
@Composable
private fun SharedPhoto(
    attachment: OutgoingAttachment,
    canSelfDestruct: Boolean,
    onRemove: () -> Unit,
    onSetMode: (AttachmentMode) -> Unit,
) {
    val spoilerStyle = rememberSpoilerBlurStyle()
    var modeSheet by remember { mutableStateOf(false) }
    val isVideo = attachment is OutgoingAttachment.Video

    if (modeSheet) {
        AttachmentModeSheet(
            currentMode = attachment.mode,
            isVideo = isVideo,
            selfDestructAllowed = canSelfDestruct,
            onSelectMode = onSetMode,
            onDismiss = { modeSheet = false },
        )
    }

    Box(Modifier.fillMaxSize().padding(horizontal = PAGE_INSET, vertical = 8.dp), contentAlignment = Alignment.Center) {
        /*
         * Karta má rozměry SNÍMKU, ne plochy.
         *
         * S obrázkem vepsaným do plochy (`ContentScale.Fit` ve `fillMaxSize`) drží zaoblení
         * i tlačítka plocha, takže rohy fotky zůstanou hranaté a ✕ visí kus vedle ní. Poměr
         * stran je v připravené příloze, tak se z něj rámeček spočítá — u snímku na výšku se
         * napřed vyčerpá výška, u snímku na šířku šířka.
         */
        val ratio = attachment.previewRatio
        Box(
            Modifier
                .then(
                    if (ratio != null) {
                        Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = ratio < 1f)
                    } else {
                        Modifier.fillMaxSize()
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(MEDIA_CARD_CORNER))) {
                AsyncImage(
                    // Coil potřebuje schéma, holá cesta by se vyhodnotila jako relativní URI.
                    model = "file://${attachment.previewPath}",
                    contentDescription = null,
                    // Rámeček už poměr stran drží, takže `Crop` jen dorovná zaokrouhlení a nic
                    // neukrojí — sdílený snímek má být vidět CELÝ, je to to jediné, co odejde.
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().spoilerBlur(
                        hasSpoiler = attachment.hasSpoiler,
                        revealed = false,
                        style = spoilerStyle,
                    ),
                )
                if (attachment.hasSpoiler) SpoilerOverlay(visible = true)
            }

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

        // Spoiler, mizení i „jako soubor" nabízí tentýž sheet jako nad vstupním polem — jedno
        // místo, kde se o způsobu odeslání rozhoduje.
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

/** Soubory se kopírují do cache aplikace; velké video to chvíli trvá. */
@Composable
private fun Preparing() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LoadingIndicator(color = LocalContentColor.current)
        Text(stringResource(Res.string.share_preparing), style = MaterialTheme.typography.bodyMedium)
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
    /** Vrátí galerii pod pole; `null`, když je galerie vidět nebo tu žádná není. */
    onOpenGallery: (() -> Unit)?,
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
                if (onOpenGallery != null) {
                    // 4 dp = (56 − 48) / 2: ikona sedí na středu jednořádkového pole a u víc řádků
                    // zůstane u posledního, jako sponka ve vstupním poli konverzace.
                    IconButton(onClick = onOpenGallery, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)) {
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

/**
 * Rozostřený snímek jako pozadí, jako v galerii.
 *
 * Prolíná se, ne skokem: při listování dávkou by výměna pozadí pod prstem vypadala jako
 * překreslení. Ořez je nutný ze stejného důvodu jako v prohlížeči — rozostření pokrývá jen
 * plochu obrazovky.
 */
@Composable
private fun BoxScope.AmbientBackdrop(previewPath: String?, modifier: Modifier = Modifier) {
    val hazeState = rememberHazeState()
    val style = ambientBlurStyle()

    Box(modifier.clipToBounds().background(Color.Black)) {
        Crossfade(previewPath, label = "share-backdrop") { path ->
            if (path != null) {
                Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
                    AsyncImage(
                        model = "file://$path",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        Box(Modifier.fillMaxSize().hazeBlur(input = HazeInput.Sources(hazeState), style = style))
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
private val PAGE_INSET = 16.dp
private val BADGE_BUTTON = 40.dp
private val BADGE_INSET = 8.dp
/**
 * Galerie pod popiskem jako v Google Zprávách: panel se zaoblenými horními rohy a úchytem.
 *
 * **Sbalený jen vykukuje** — náhled mřížky, ve kterém jde vybírat, ale ne posouvat (tak se
 * vestavěný picker ve sbaleném stavu chová sám). Tahem za úchyt nahoru nebo ťuknutím na něj se
 * rozbalí přes většinu obrazovky a teprve pak se v něm listuje; tahem dolů se zase sbalí
 * a ze sbaleného zavře.
 *
 * **Tah se chytá jen na úchytu**, ne na mřížce: tu kreslí cizí okno (`SurfaceView`), které si
 * dotyky bere samo a k nám je nepustí.
 */
@Composable
private fun GalleryPanel(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    height: Dp,
    modifier: Modifier = Modifier,
    picker: @Composable (Modifier) -> Unit,
) {
    val expandedNow by rememberUpdatedState(expanded)
    Surface(
        shape = RoundedCornerShape(topStart = GALLERY_CORNER, topEnd = GALLERY_CORNER),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth().height(height),
    ) {
        Column {
            var dragged by remember { mutableStateOf(0f) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(GALLERY_HANDLE_AREA)
                    .clickable(
                        onClickLabel = stringResource(Res.string.share_to_add_media),
                        onClick = { onExpandedChange(!expandedNow) },
                    )
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { dragged += it },
                        onDragStarted = { dragged = 0f },
                        onDragStopped = { velocity ->
                            val up = dragged < -GALLERY_DRAG_THRESHOLD || velocity < -GALLERY_FLING_VELOCITY
                            val down = dragged > GALLERY_DRAG_THRESHOLD || velocity > GALLERY_FLING_VELOCITY
                            when {
                                up && !expandedNow -> onExpandedChange(true)
                                down && expandedNow -> onExpandedChange(false)
                                down -> onDismiss()
                            }
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

private val GALLERY_CORNER = 28.dp
private val GALLERY_HANDLE_AREA = 36.dp
/** Sbalená galerie jen vykukuje, jako ve vzoru z Google Zpráv — fotka nahoře zůstává hlavní. */
private const val GALLERY_COLLAPSED_FRACTION = 0.28f
private val GALLERY_COLLAPSED_MAX = 300.dp
/** Tah za úchyt v pixelech a rychlost švihnutí v px/s, od kterých se panel přepne. */
private const val GALLERY_DRAG_THRESHOLD = 48f
private const val GALLERY_FLING_VELOCITY = 800f

private val FIELD_CORNER = 28.dp
private val SEND_BUTTON = 56.dp
