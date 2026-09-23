package eu.tintera.plume.features.share

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import eu.tintera.plume.resources.share_to_caption
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

    Box(modifier.fillMaxSize().background(Color.Black)) {
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
                }

                CaptionBar(
                    caption = viewModel.caption,
                    onCaptionChange = viewModel::onCaptionChange,
                    // Dokud se soubory připravují, není co poslat.
                    enabled = !attachments.isNullOrEmpty() && !state.sending,
                    sending = state.sending,
                    onSend = viewModel::send,
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding(),
        ) { Snackbar(it) }
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
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(HEADER_HEIGHT).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onClose) {
            Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.common_close))
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
) {
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
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
                modifier = Modifier.fillMaxWidth(),
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
private val FIELD_CORNER = 28.dp
private val SEND_BUTTON = 56.dp
