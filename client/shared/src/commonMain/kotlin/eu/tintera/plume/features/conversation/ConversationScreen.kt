package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.LinkPreview
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.Presence
import eu.tintera.plume.core.telegram.SendState
import eu.tintera.plume.core.ui.AnimatedSticker
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.asDayLabel
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.core.ui.asRelativeTime
import eu.tintera.plume.core.ui.isSameDayAs
import eu.tintera.plume.core.ui.chatAvatarKey
import eu.tintera.plume.core.ui.emojiClusterCount
import eu.tintera.plume.core.ui.chatTitleKey
import eu.tintera.plume.core.ui.photoKey
import eu.tintera.plume.core.ui.sharedNavElement
import eu.tintera.plume.core.ui.sharedNavText
import eu.tintera.plume.core.ui.sharedFlight
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.rememberUploadProgress
import eu.tintera.plume.core.ui.rememberAttachmentPicker
import eu.tintera.plume.core.ui.RichText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.arrow_downward_24dp
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.done_all_24dp
import eu.tintera.plume.resources.error_24dp
import eu.tintera.plume.resources.edit_24dp
import eu.tintera.plume.resources.reply_24dp
import eu.tintera.plume.resources.push_pin_filled_24dp
import eu.tintera.plume.resources.schedule_24dp
import eu.tintera.plume.resources.send_24dp
import eu.tintera.plume.resources.description_24dp
import eu.tintera.plume.resources.image_24dp
import eu.tintera.plume.resources.attach_file_24dp
import eu.tintera.plume.resources.mood_24dp
import eu.tintera.plume.resources.sticky_note_2_24dp
import eu.tintera.plume.core.settings.ExpressionMode
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt
import kotlin.time.Instant

@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    onBack: () -> Unit,
    onOpenMedia: (messageId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var stickerPickerOpen by rememberSaveable { mutableStateOf(false) }

    // Vybraný soubor je už zkopírovaný v cache aplikace, takže se rovnou posílá.
    val attachmentPicker = rememberAttachmentPicker(
        onPicked = viewModel::sendAttachment,
        onError = viewModel::reportError,
    )

    /**
     * Rozepsaný text vstupního pole. Drží se tady, ne v poli: výběr emoji do něj vkládá na pozici
     * kurzoru a maže z něj, zatímco pole samo je pod sheetem.
     *
     * `TextFieldValue`, ne `String`: u vráceného konceptu i u vloženého emoji je potřeba nastavit
     * kurzor. S holým řetězcem by zůstal na začátku textu.
     */
    var draft by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }

    /** Poslední otevřená část výběru — uložená v nastavení, takže platí i po restartu aplikace. */
    val expressionMode by viewModel.expressionMode.collectAsStateWithLifecycle()
    /** Sada otevřená ťuknutím na samolepku v konverzaci. */
    var openStickerSetId by rememberSaveable { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    // Chyby se dřív zahazovaly: `state.error` se vykreslovalo jen u prázdné konverzace, takže
    // neúspěšná reakce nebo odeslání zmizely bez jediné stopy.
    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissError()
    }

    // Snackbar běží v `snackbarScope`, ne v těle efektu. Vynulování `unpinned` změní klíč efektu,
    // ten se restartuje a zrušil by i snackbar, který se právě ukazuje — takže se neukázal nikdy.
    val snackbarScope = rememberCoroutineScope()

    // Stejná past jako u `unpinned` níž: vynulování mění klíč efektu, takže snackbar běží mimo něj.
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        viewModel.consumeNotice()
        snackbarScope.launch { snackbarHostState.showSnackbar(notice, duration = SnackbarDuration.Short) }
    }

    LaunchedEffect(state.unpinned) {
        val unpinned = state.unpinned ?: return@LaunchedEffect
        // Vynulovat hned, ne až po zavření snackbaru — jinak by se po otočení ukázal znovu.
        viewModel.consumeUnpinned()
        snackbarScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Zpráva odepnuta",
                actionLabel = "Vrátit",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoUnpin(unpinned)
        }
    }

    // `reverseLayout` obrací i význam indexů: index 0 je nejnovější zpráva dole, vyšší indexy
    // jsou starší nahoře. Stránkování do minulosti se tedy spouští na KONCI seznamu.
    val shouldLoadOlder by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { shouldLoadOlder }.collect { if (it) viewModel.loadOlder() }
    }

    // „Jsem dole" = vidím nejnovější zprávu. Při `reverseLayout` je to index 0; kontroluje se
    // i offset, aby se za dole nepovažoval stav, kdy je poslední bublina jen z půlky vidět.
    val atBottom by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }

    // Kolik cizích zpráv přišlo, zatímco uživatel četl historii.
    var pendingNewMessages by rememberSaveable { mutableIntStateOf(0) }
    var lastSeenMessageId by rememberSaveable { mutableStateOf<Long?>(null) }

    /**
     * Změřená výška pruhu s připnutou zprávou; nula, když žádný není. Pruh plave nad seznamem
     * (není v `topBar`), takže jeho výšku Scaffold nezná a měří se ručně.
     */
    var pinnedHeight by remember { mutableStateOf(0.dp) }

    /*
     * Obsluha nabídky nad zprávou.
     *
     * Balík místo šesti parametrů: nabídka se deklaruje UVNITŘ bubliny (jinak by neměla k čemu
     * se ukotvit), takže by je jinak bublina všechny nesla ve své signatuře.
     */
    val menuHandlers = remember(viewModel) {
        MessageMenuHandlers(
            onPick = viewModel::toggleReaction,
            onMore = viewModel::showAllReactions,
            onReply = viewModel::replyTo,
            onEdit = viewModel::startEdit,
            onCopy = viewModel::copyMessage,
            onDelete = viewModel::requestDelete,
            onPin = viewModel::requestPin,
            onUnpin = viewModel::unpinFromMenu,
            onToggleFavorite = viewModel::toggleFavoriteSticker,
            onDismiss = viewModel::closeMenus,
        )
    }

    /*
     * Zprávy podle id, pro dohledání citované.
     *
     * Skládá se jednou na změnu seznamu, ne v každé bublině. Předtím se citace hledala
     * průchodem celého seznamu, takže vykreslení historie stálo kvadraticky — u tisícovky
     * zpráv se to při scrollu pozná.
     */
    val byId = remember(state.messages) { state.messages.associateBy { it.id } }

    /*
     * Položky seznamu: samostatné zprávy a celá alba. Album jsou v Telegramu samostatné zprávy,
     * v konverzaci ale jedna bublina — a na položkách, ne na zprávách, stojí sousedé (předěly dne,
     * seskupení bublin, profilovky) i index v `LazyColumn`.
     */
    val rows = remember(state.messages) { groupAlbums(state.messages) }

    // Klíč položky není u odeslaných zpráv jejich id (viz `ConversationState.itemKeys`), takže
    // co se v seznamu hledá podle klíče, musí mít vlastní mapu.
    val byKey = remember(state.messages, state.itemKeys) { state.messages.associateBy(state::keyOf) }

    /*
     * Datum nejvyšší viditelné zprávy, pro plovoucí štítek při scrollu.
     *
     * Při `reverseLayout` je nahoře POSLEDNÍ z viditelných položek. Bere se klíč, ne index —
     * ze stejného důvodu jako u hlášení přečtení. Položky bez číselného klíče (reklama, řádek
     * načítání) se přeskočí, protože k nim žádná zpráva není.
     */
    val topVisibleKey by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.lastOrNull { it.key is Long }?.key as? Long
        }
    }

    val visibleDay = topVisibleKey?.let { byKey[it]?.timestamp?.asDayLabel() }

    // Štítek se drží i po zmizení data, aby při odeznívání nezůstala prázdná pilulka.
    var floatingDay by remember { mutableStateOf("") }
    LaunchedEffect(visibleDay) {
        if (visibleDay != null) floatingDay = visibleDay
    }

    /*
     * Skok na zprávu — první nepřečtenou, nebo citovanou.
     *
     * Zprávu hledáme podle id, ne podle indexu — než efekt doběhne, může seznam narůst o novou
     * zprávu a index by ukazoval jinam.
     */
    LaunchedEffect(state.scrollTarget) {
        val target = state.scrollTarget ?: return@LaunchedEffect
        // Hledá se ŘÁDEK, ne zpráva: fotka z alba je v seznamu součástí jedné položky.
        val index = rows.indexOfFirst { it.contains(target.messageId) }
        // Před zprávami stojí sponzorované položky, takže index v seznamu není index v LazyColumnu.
        if (index >= 0) listState.scrollToAnchor(index + state.sponsored.size, target.anchor)
        viewModel.consumeScrollTarget()
    }

    /*
     * Tlačítko „dolů" i bez nových zpráv.
     *
     * Kdo odroluje do historie (nebo tam skočí po citaci), neměl jak se vrátit jinak než rolováním.
     * Ukáže se až o kus výš, ne při prvním pixelu — u spodní hrany by jen blikalo pod prstem.
     */
    val farFromBottom by remember {
        derivedStateOf { listState.firstVisibleItemIndex >= SCROLL_DOWN_THRESHOLD_ITEMS }
    }

    val newest = state.messages.firstOrNull()

    /*
     * Nová zpráva dole: seznam se ukotví u spodní hrany UŽ PŘI JEJÍM VLOŽENÍ.
     *
     * `LazyColumn` drží pozici podle klíče první viditelné položky. Nová zpráva na indexu 0 se
     * tedy vložila POD spodní hranu a teprve efekt o snímek později k ní dojížděl — bublina
     * nejdřív nebyla vidět, pak k ní seznam poskočil, a let ze vstupního pole mířil na cíl,
     * který se mu pod rukama posouval. `requestScrollToItem` platí pro nejbližší přeměření,
     * tedy pro tentýž layout, ve kterém se zpráva objeví; proto se volá v kompozici, ne v efektu.
     *
     * Hlídá se klíč, ne id: potvrzení odeslání mění id, ale novou zprávu nepřináší.
     */
    val anchor = remember { NewestItem() }
    if (newest != null) {
        val key = state.keyOf(newest)
        if (key != anchor.key) {
            // První naplnění seznamu není „nová zpráva", jen se zobrazila historie. Smazání
            // nejnovější zprávy klíč taky změní, ale id přitom klesne.
            val arrived = anchor.key != null && newest.id > anchor.id
            // Vlastní zpráva dolů vždycky — jinak by odeslání vypadalo, že se nic nestalo.
            // Cizí jen když je uživatel dole; při čtení historie by ho to vytrhlo ze čtení.
            if (arrived && (newest.isOutgoing || atBottom)) listState.requestScrollToItem(0)
            anchor.key = key
            anchor.id = newest.id
        }
    }

    val newestMessageId = newest?.id

    LaunchedEffect(newestMessageId) {
        val newestId = newestMessageId ?: return@LaunchedEffect
        val previous = lastSeenMessageId
        lastSeenMessageId = newestId

        // První naplnění seznamu není „nová zpráva", jen se zobrazila historie.
        if (previous == null) return@LaunchedEffect
        if (newestId <= previous) return@LaunchedEffect

        // Id zpráv v chatu rostou, takže „nové" jsou ty nad posledním viděným.
        val arrived = state.messages.takeWhile { it.id > previous }

        // Scroll už obstaral `requestScrollToItem` výš; tady se jen počítá odznak.
        when {
            arrived.any { it.isOutgoing } || atBottom -> pendingNewMessages = 0

            // Čtu historii: NEscrollovat, vytrhlo by mě to ze čtení. Místo toho odznak dole.
            else -> pendingNewMessages += arrived.count { !it.isOutgoing }
        }
    }

    // Jakmile se uživatel dostane dolů, odznak nemá co hlásit.
    LaunchedEffect(atBottom) {
        if (atBottom) pendingNewMessages = 0
    }

    // Zhlédnutí reklamy se hlásí, teprve až je karta na obrazovce CELÁ. TDLib to vyžaduje
    // („když je vidět celý text"); hlásit ji při prvním pixelu by znamenalo vykazovat
    // inzerentovi zhlédnutí, která se nestala. Beru přísnější podmínku — celá karta včetně
    // tlačítka — protože rozměr samotného textu odsud nezjistím.
    LaunchedEffect(listState, state.sponsored) {
        if (state.sponsored.isEmpty()) return@LaunchedEffect

        snapshotFlow {
            val info = listState.layoutInfo
            info.visibleItemsInfo
                .filter { item ->
                    item.offset >= info.viewportStartOffset &&
                        item.offset + item.size <= info.viewportEndOffset
                }
                .mapNotNull { it.key as? String }
                .filter { it.startsWith(SPONSORED_KEY_PREFIX) }
                .toSet()
        }
            .debounce(SPONSORED_VIEW_DEBOUNCE_MS)
            .collect { keys ->
                keys.forEach { key ->
                    key.removePrefix(SPONSORED_KEY_PREFIX).toLongOrNull()
                        ?.let(viewModel::reportSponsoredSeen)
                }
            }
    }

    /*
     * Hlášení přečtení.
     *
     * Bere se **klíč položky, ne její index**. Index do `state.messages` nesedí: před zprávami
     * stojí sponzorované položky, takže v kanálu s reklamou byl výběr posunutý o jejich počet
     * a hlásily se za přečtené zprávy, které uživatel neviděl. Klíč zpráv je rovnou jejich id,
     * takže se nemá co rozejít, ani až seznam další položku přibere.
     *
     * Odpočet čeká, až se scrollování uklidní — jinak by při rychlém průletu odešla jedna žádost
     * na každý snímek. Efekt proto **nesmí záviset na `state.messages`**: s ním se restartoval
     * při každé nové zprávě a odpočet začínal znovu od nuly.
     */
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? Long }
        }
            .debounce(READ_DEBOUNCE_MS)
            .collect(viewModel::markRead)
    }

    Scaffold(
        modifier = modifier,
        /*
         * Spodní inset si řeší vstupní pole samo (`navigationBarsPadding` + `imePadding`).
         *
         * Kdyby ho dal i Scaffold do `padding`, započítal by se dvakrát a pole by viselo o výšku
         * systémové lišty výš — `padding` se v obsahu jen aplikuje, inset se tím nekonzumuje.
         * Takhle navíc sahá obsah i závoj pole až pod lištu, stejně jako pod pole samotné.
         */
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        state.info?.let { info ->
                            Avatar(
                                chatId = info.id,
                                title = info.title,
                                avatarFileId = info.avatarFileId,
                                size = 36.dp,
                                modifier = Modifier.sharedNavElement(chatAvatarKey(info.id)),
                            )
                        }
                        Column {
                            Text(
                                text = state.info?.title ?: "Konverzace",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                // Klíč se dá jen když chat známe; než dorazí `info`, není s čím
                                // párovat.
                                modifier = state.info
                                    ?.let { Modifier.sharedNavText(chatTitleKey(it.id)) }
                                    ?: Modifier,
                            )

                            /*
                             * Podtitul: činnost, jinak dostupnost.
                             *
                             * „Píše…" má přednost — je to novější a pro čekajícího zajímavější
                             * informace, a obojí se na řádek nevejde. Skupina dostupnost nemá,
                             * takže tam se podtitul objeví jen při činnosti; výšku hlavičky proto
                             * animuje `AnimatedContent`, jinak by název poskočil.
                             */
                            val subtitle = state.activity?.let { HeaderSubtitle(it.label(), accent = true) }
                                ?: state.presence?.let {
                                    HeaderSubtitle(it.describe(), accent = it is Presence.Online)
                                }
                            val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                            val fastEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

                            AnimatedContent(
                                targetState = subtitle,
                                transitionSpec = { fadeIn(effects) togetherWith fadeOut(fastEffects) },
                                contentAlignment = Alignment.CenterStart,
                                label = "header-subtitle",
                            ) { current ->
                                current ?: return@AnimatedContent
                                Text(
                                    text = current.text,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (current.accent) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = "Zpět")
                    }
                },
            )
        },
        /*
         * Vstupní pole JE `bottomBar`. Scaffold ho změří (i s citací, úpravou, víc řádky
         * a klávesnicí) a podle toho sám postaví snackbar a tlačítko dolů nad něj.
         *
         * Obsah Scaffoldu je přesto přes celou plochu: spodní padding se nedává na kontejner,
         * ale do `contentPadding` seznamu. Zprávy tak pod pole zajíždějí a v klidu nad ním
         * končí. Kdyby šel padding na kontejner, seznam by před polem natvrdo skončil a přes
         * obrazovku by vedl předěl, o který se zprávy při scrollu „zasekávají".
         */
        bottomBar = {
            MessageInput(
                replyingTo = state.replyingTo,
                partnerName = state.info?.title,
                flight = state.flight,
                onCancelReply = viewModel::clearReply,
                onSend = viewModel::send,
                onDraftChanged = viewModel::onDraftChanged,
                editing = state.editing,
                onCancelEdit = viewModel::cancelEdit,
                inputFill = state.inputFill,
                onInputFilled = viewModel::consumeInputFill,
                onOpenStickers = { stickerPickerOpen = true },
                onPickPhoto = attachmentPicker::pickPhoto,
                onPickDocument = attachmentPicker::pickDocument,
                expressionMode = expressionMode,
                draft = draft,
                onDraftValueChange = { draft = it },
            )
        },
        // Tlačítko dolů ve FAB slotu: Scaffold ho drží nad polem a snackbar pak nad ním.
        floatingActionButton = {
            ScrollToBottomButton(
                visible = farFromBottom || pendingNewMessages > 0,
                count = pendingNewMessages,
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        // Nabídka nad zprávou se kreslí až v bublině — viz `MessageMenuOverlay`.

        // Plný seznam emoji za tlačítkem „+". Zůstává bottom sheetem, protože se v něm listuje —
        // a to popup neumí.
        if (stickerPickerOpen) {
            StickerPickerSheet(
                mode = expressionMode,
                onModeChange = viewModel::setExpressionMode,
                // Při úpravě zprávy jen emoji: samolepku do upravované zprávy vložit nejde.
                stickersAvailable = state.editing == null,
                draft = draft,
                // Úprava z výběru emoji je psaní jako každé jiné: ukládá koncept i hlásí „píše…".
                onDraftEdit = {
                    draft = it
                    viewModel.onDraftChanged(it.text)
                },
                onPick = { sticker ->
                    stickerPickerOpen = false
                    viewModel.sendSticker(sticker)
                },
                onDismiss = { stickerPickerOpen = false },
            )
        }

        openStickerSetId?.let { setId ->
            StickerSetSheet(
                setId = setId,
                onPick = { sticker ->
                    openStickerSetId = null
                    viewModel.sendSticker(sticker)
                },
                onDismiss = { openStickerSetId = null },
            )
        }

        state.reactionSheet?.let { picker ->
            ReactionPickerSheet(
                picker = picker,
                onPick = { emoji -> viewModel.toggleReaction(picker.messageId, emoji) },
                onDismiss = viewModel::closeMenus,
            )
        }

        state.pendingPin?.let { pending ->
            PinMessageDialog(
                pending = pending,
                partnerName = state.info?.title,
                onConfirm = viewModel::confirmPin,
                onDismiss = viewModel::cancelPin,
            )
        }

        state.pendingDelete?.let { pending ->
            MessageDeleteDialog(
                pending = pending,
                partnerName = state.info?.title,
                onConfirm = viewModel::confirmDelete,
                onDismiss = viewModel::cancelDelete,
            )
        }

        val layoutDirection = LocalLayoutDirection.current
        Box(
            Modifier
                .fillMaxSize()
                .padding(
                    top = padding.calculateTopPadding(),
                    start = padding.calculateStartPadding(layoutDirection),
                    end = padding.calculateEndPadding(layoutDirection),
                ),
        ) {
            when {
                state.messages.isEmpty() && state.loading ->
                    LoadingIndicator(Modifier.align(Alignment.Center))

                state.messages.isEmpty() -> Text(
                    text = state.error ?: "Zatím tu nejsou žádné zprávy.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )

                else -> LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    // Dole se odsazuje o výšku `bottomBar` (vstupního pole), aby nejnovější
                    // zpráva v klidu nekončila pod ním. Při scrollu obsah pod pole zajede.
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 8.dp + pinnedHeight,
                        bottom = 8.dp + padding.calculateBottomPadding(),
                    ),
                    // Rozestup UVNITŘ skupiny zpráv od jednoho odesílatele. Mezera mezi skupinami
                    // se přidává až v bublině — tady na to není informace o sousedech.
                    verticalArrangement = Arrangement.spacedBy(MESSAGE_SPACING),
                ) {
                    // Reklama patří pod nejnovější zprávu. Při `reverseLayout` je „dole"
                    // začátek seznamu, takže stojí PŘED zprávami.
                    items(state.sponsored, key = { "sponsored-${it.messageId}" }) { sponsored ->
                        SponsoredCard(
                            sponsored = sponsored,
                            onClick = { viewModel.onSponsoredClick(sponsored) },
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }

                    // Klíč NENÍ id: odeslané zprávě TDLib po potvrzení id změní a s novým klíčem
                    // by bublina problikla jako odebraná a znovu přidaná položka. U alba je to klíč
                    // jeho NEJSTARŠÍ zprávy — novější fotky alba dorážejí za ní a klíč nezmění.
                    itemsIndexed(rows, key = { _, row -> state.keyOf(row.oldest) }) { index, row ->
                        val message = row.display
                        // Seznam jde od nejnovější, takže „předchozí zpráva v čase" je ta
                        // s VYŠŠÍM indexem.
                        val olderRow = rows.getOrNull(index + 1)
                        val older = olderRow?.display

                        /*
                         * Začíná touhle zprávou nový den?
                         *
                         * `older == null` je nejstarší načtená zpráva. Datum se u ní ukáže taky —
                         * jakmile se dotáhne starší stránka, předěl se posune nahoru. Lepší než
                         * nechat vršek historie bez data.
                         */
                        val startsDay = older == null ||
                            !older.timestamp.isSameDayAs(row.oldest.timestamp)

                        /*
                         * Je tohle první nepřečtená zpráva?
                         *
                         * Hranici drží ViewModel z okamžiku otevření chatu, takže se předěl
                         * neposouvá, jak se zprávy postupně označují za přečtené.
                         */
                        val startsUnread = state.unreadBoundaryId?.let { boundary ->
                            row.oldest.id > boundary && olderRow != null && olderRow.newest.id <= boundary
                        } == true

                        val flight = state.flight?.takeIf { it.itemKey == state.keyOf(row.oldest) }

                        // Předěl patří NAD zprávu, takže do sloupce před ni. `reverseLayout`
                        // obrací pořadí položek, ne obsah jedné položky.
                        //
                        // Cíl letu se nezjevuje prolnutím: během letu se kreslí v překryvu sdílených
                        // prvků, kam průhlednost položky nedosáhne, a po přistání by pak skočil
                        // z plné viditelnosti do rozjetého prolnutí.
                        Column(
                            Modifier.animateItem(
                                fadeInSpec = if (flight != null) null else spring(stiffness = Spring.StiffnessMediumLow),
                            ),
                        ) {
                            if (startsDay) DayDivider(message.timestamp)
                            if (startsUnread) UnreadDivider()

                            // Systémové oznámení nemá odesílatele, takže nemá ani stranu — kreslí
                            // se na střed a bez bubliny. Reakce, citace ani stav doručení k němu
                            // nepatří.
                            if (message.isService) {
                                ServiceMessage(message)
                                return@Column
                            }

                            MessageBubble(
                                message = message,
                                // Album: zprávy od nejstarší, kreslí je mřížka místo jedné přílohy.
                                album = row.members.takeIf { row.isAlbum },
                                onOpenAlbumItem = { messageId -> onOpenMedia(messageId) },
                                quoted = message.replyToMessageId?.let { id ->
                                    byId[id] ?: state.quotes[id]
                                },
                                // V soukromém chatu se jméno odesílatele u zpráv nevyplňuje (dává
                                // smysl jen ve skupinách), takže citace by neměla koho uvést.
                                partnerName = state.info?.title,
                                highlighted = state.highlightedMessageId?.let(row::contains) == true,
                                onQuoteClick = message.replyToMessageId?.let { id ->
                                    { viewModel.jumpToMessage(id) }
                                },
                                // Za předělem začíná nový blok, i když píše tentýž člověk — jinak by
                                // první bublina pod datem navazovala na zprávu z minulého dne.
                                previous = older.takeIf { !startsDay },
                                /*
                                 * Tvář odesílatele se ukazuje u POSLEDNÍ zprávy jeho bloku, jako
                                 * v Telegramu: blok se čte shora a tvář ho uzavírá. Novější soused
                                 * je o index NÍŽ. Kanál (odesílatel = chat sám) tvář nemá — nese ji
                                 * hlavička a u každého příspěvku by byla pořád tatáž.
                                 */
                                avatar = when {
                                    state.info?.isGroup != true || message.isOutgoing -> SenderAvatar.None
                                    message.senderId == state.info?.id -> SenderAvatar.None
                                    rows.getOrNull(index - 1)?.display.continues(message) -> SenderAvatar.Spacer
                                    else -> SenderAvatar.Shown
                                },
                                lastReadOutboxMessageId = state.lastReadOutboxMessageId,

                                flight = flight,
                                onToggleReaction = { emoji ->
                                    viewModel.toggleReaction(message.id, emoji)
                                },
                                onRequestMenu = { viewModel.showMessageMenu(message.id) },
                                onReply = { viewModel.replyTo(message.id) },
                                // Ťuknutí na samolepku otevře její sadu; na fotku prohlížeč.
                                onOpenMedia = {
                                    val setId = (message.media as? MessageMedia.Sticker)?.setId
                                    if (setId != null) {
                                        if (setId != 0L) openStickerSetId = setId
                                    } else {
                                        onOpenMedia(message.id)
                                    }
                                },
                                // Nabídka se kreslí uvnitř bubliny, které se týká — jinak by neměla
                                // k čemu se ukotvit.
                                menu = state.messageMenu?.takeIf { it.messageId == message.id },
                                menuHandlers = menuHandlers,
                                onLinkClick = viewModel::openLink,
                            )
                        }
                    }

                    if (state.loading) {
                        item(key = "loading") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                LoadingIndicator()
                            }
                        }
                    }
                }
            }

            /*
             * Plovoucí datum.
             *
             * Ukáže se jen při scrollu a zase zmizí. V dlouhé historii je předěl často mimo
             * obrazovku a bez tohohle by se při hledání starší zprávy nedalo poznat, kde zrovna
             * jsem. Je to overlay, ne `stickyHeader`: ten se v obráceném seznamu lepí na jeho
             * začátek, tedy dolů, což je přesně naopak.
             */
            AnimatedVisibility(
                visible = listState.isScrollInProgress && floatingDay.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                CenteredNote(
                    text = floatingDay,
                    // Stín ho odlepí od zpráv, které pod ním projíždějí.
                    shadowElevation = 3.dp,
                    modifier = Modifier.padding(top = 8.dp + pinnedHeight),
                )
            }

            /*
             * Připnuté zprávy.
             *
             * Plavou nad seznamem jako vstupní pole, ne v hlavičce Scaffoldu: seznam pod nimi zajíždí
             * a odsazuje se o jejich ZMĚŘENOU výšku. Kdyby byly součástí `topBar`, ukusovaly by místo
             * i ve chvíli, kdy odjíždějí, a obsah by pod nimi na konci animace poskočil.
             *
             * Rozbalení na všechny je **jen na chvíli**: sbalí se po skoku na zprávu a jakmile se
             * pohne konverzací. Rozbalený seznam zakrývá zprávy a nechat ho tak by znamenalo, že
             * ho uživatel musí pokaždé ručně zavírat.
             */
            var pinnedExpanded by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(listState.isScrollInProgress) {
                if (listState.isScrollInProgress) pinnedExpanded = false
            }

            val density = LocalDensity.current
            AnimatedVisibility(
                visible = state.pinned.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onSizeChanged { pinnedHeight = with(density) { it.height.toDp() } },
            ) {
                // Při odjezdu se musí dál kreslit poslední zprávy, ne prázdný pruh.
                val last = remember { arrayOf(emptyList<ChatMessage>()) }
                if (state.pinned.isNotEmpty()) last[0] = state.pinned

                PinnedMessagesPanel(
                    messages = last[0],
                    expanded = pinnedExpanded,
                    canUnpin = state.canUnpin,
                    onOpen = { message ->
                        pinnedExpanded = false
                        viewModel.jumpToMessage(message.id)
                    },
                    onUnpin = viewModel::unpin,
                    onToggleExpanded = { pinnedExpanded = !pinnedExpanded },
                )
            }

        }
    }
}

/**
 * Tlačítko „dolů", s počtem nových zpráv, když nějaké čekají.
 *
 * Objeví se, když je uživatel v historii — dole se totiž nescrolluje automaticky, aby ho nová
 * zpráva nevytrhla ze čtení. Bez nových zpráv je to jen kulatá šipka; s nimi se roztáhne
 * o počet, protože bez čísla by uživatel nevěděl, jestli se vyplatí skočit dolů.
 */
@Composable
private fun ScrollToBottomButton(
    visible: Boolean,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        // Vjezd zespoda: tlačítko přiletí odtamtud, kam po ťuknutí odskočí.
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shadowElevation = 4.dp,
        ) {
            Row(
                // Bez počtu je z toho kruh: šířka i výška jsou dané touhle minimální plochou.
                Modifier
                    .heightIn(min = SCROLL_DOWN_BUTTON_SIZE)
                    .widthIn(min = SCROLL_DOWN_BUTTON_SIZE)
                    .padding(horizontal = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painterResource(Res.drawable.arrow_downward_24dp),
                    contentDescription = if (count > 0) null else "Na konec konverzace",
                    modifier = Modifier.size(18.dp),
                )

                // Mezera je v obsahu, ne ve `spacedBy`: skrytý AnimatedVisibility je pořád potomek
                // a rozestup by kruh bez počtu nafoukl do šišky.
                AnimatedVisibility(
                    visible = count > 0,
                    enter = expandHorizontally() + fadeIn(),
                    exit = shrinkHorizontally() + fadeOut(),
                ) {
                    // Při zmizení se musí dál kreslit poslední nenulový počet, ne „0 zpráv".
                    // Obyčejné pole, ne stav: zápis do stavu v kompozici by jen vynutil další.
                    val shown = remember { intArrayOf(count) }
                    if (count > 0) shown[0] = count

                    Text(
                        text = newMessagesLabel(shown[0]),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

/** České skloňování: 1 zpráva, 2–4 zprávy, 5+ zpráv. */
private fun newMessagesLabel(count: Int): String = when {
    count == 1 -> "1 nová zpráva"
    count in 2..4 -> "$count nové zprávy"
    else -> "$count nových zpráv"
}

/**
 * Bublina jedné zprávy.
 *
 * Zprávy téhož odesílatele v řadě se **seskupují**: jméno se ukáže jen u první a nároží u sebe
 * navazujících bublin se zaoblí míň. Bez toho vypadá delší monolog jako dvacet nesouvisejících
 * kartiček.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MessageBubble(
    message: ChatMessage,
    quoted: ChatMessage?,
    partnerName: String?,
    /** Krátce po skoku na citaci — zpráva, na kterou se skočilo. */
    highlighted: Boolean,
    /** Ťuknutí na citaci; `null` u zprávy, která nic necituje. */
    onQuoteClick: (() -> Unit)?,
    lastReadOutboxMessageId: Long,
    /** Let ze vstupního pole, který přistává právě v téhle bublině; jinak `null`. */
    flight: MessageFlight?,
    previous: ChatMessage?,
    /** Zprávy alba od nejstarší; `null` u samostatné zprávy. */
    album: List<ChatMessage>?,
    /** Ťuknutí na fotku v albu — otevře tu konkrétní zprávu. */
    onOpenAlbumItem: (messageId: Long) -> Unit,
    avatar: SenderAvatar,
    onToggleReaction: (String) -> Unit,
    onRequestMenu: () -> Unit,
    onReply: () -> Unit,
    onOpenMedia: () -> Unit,
    /** Vyplněné jen u zprávy, nad kterou je nabídka otevřená. */
    menu: MessageMenu?,
    menuHandlers: MessageMenuHandlers,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val continuesGroup = previous != null &&
        previous.isOutgoing == message.isOutgoing &&
        previous.senderId == message.senderId

    /*
     * Zprávy, které jsou celé jedno gesto — pár emoji nebo samolepka — se kreslí bez bubliny.
     *
     * Bublina kolem nich nemá co ohraničovat: samolepka má vlastní tvar s průhledností a velká
     * emoji jsou obrázek samy o sobě. Podmínkou je, že na zprávě nic dalšího nevisí; u odpovědi
     * se citace bez pozadí ztratí, takže ta si bublinu drží.
     */
    val largeEmoji = when {
        // Popisek alba z pár emoji je pořád popisek pod fotkami, ne velké emoji bez bubliny.
        album != null || message.media != null || message.replyToMessageId != null -> 0
        else -> message.text.emojiClusterCount(MAX_LARGE_EMOJI)
    }
    val bare = largeEmoji > 0 ||
        (
            (message.media is MessageMedia.Sticker || message.media is MessageMedia.AnimatedEmoji) &&
                message.replyToMessageId == null
            )

    /*
     * Odchozí bublina je `primary`, ne `primaryContainer`.
     *
     * S `primaryContainer` byly obě strany konverzace světlé plochy s podobnou světlostí a při
     * rychlém průletu očima nebylo poznat, kdo co napsal — rozlišovala je jen strana. Sytá
     * `primary` proti světlé `surfaceContainerHigh` dělá ten rozdíl barvou, takže se čte
     * i bez čtení zarovnání. Stejným způsobem to řeší Google Messages.
     *
     * Vrstvení jinak drží surface role, ne elevation — viz konvence v CLAUDE.md.
     */
    val containerColor = when {
        bare -> Color.Transparent
        message.isOutgoing -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when {
        // Bez pozadí se kreslí na plochu obrazovky, takže barva popředí musí patřit k NÍ,
        // ne k barvě bubliny, která tam není.
        bare -> MaterialTheme.colorScheme.onSurface
        message.isOutgoing -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }

    val corner = BUBBLE_CORNER
    val tight = BUBBLE_TAIL
    val shape = if (message.isOutgoing) {
        RoundedCornerShape(corner, if (continuesGroup) tight else corner, tight, corner)
    } else {
        RoundedCornerShape(if (continuesGroup) tight else corner, corner, corner, tight)
    }

    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    val drag = remember { Animatable(0f) }
    val threshold = with(density) { REPLY_THRESHOLD.toPx() }
    val maxDrag = with(density) { REPLY_MAX_DRAG.toPx() }
    // Spec se musí vzít v kompozici — `onDragStopped` je obyčejná suspend funkce, ne composable.
    val returnSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    /** Prah už byl v tomhle tahu překročen; hlídá, aby haptika i odpověď odešly jen jednou. */
    var armed by remember { mutableStateOf(false) }

    /*
     * Zvýraznění po skoku: pruh přes celou šířku, který se rozsvítí a pomalu vyhasne.
     *
     * Přes celou šířku, ne jen bublina — obarvit bublinu by u odchozí `primary` nebylo vidět
     * a u příchozí by to vypadalo jako změna stavu zprávy. Průhlednost se čte až při kreslení,
     * takže vyhasínání nic nerekomponuje.
     */
    val highlightAlpha by animateFloatAsState(
        targetValue = if (highlighted) HIGHLIGHT_ALPHA else 0f,
        animationSpec = if (highlighted) {
            MaterialTheme.motionScheme.fastEffectsSpec()
        } else {
            MaterialTheme.motionScheme.slowEffectsSpec()
        },
        label = "highlight",
    )
    val highlightColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            // Zprávy od jednoho odesílatele drží u sebe, nová skupina se oddělí. Tvar bublin
            // to už rozlišuje staženým nárožím, mezera tomu jen dává prostor.
            .padding(top = if (continuesGroup) 0.dp else GROUP_SPACING)
            .drawBehind {
                if (highlightAlpha > 0f) {
                    drawRoundRect(
                        color = highlightColor,
                        alpha = highlightAlpha,
                        cornerRadius = CornerRadius(HIGHLIGHT_CORNER.toPx()),
                    )
                }
            }
            /*
             * Tažení do strany = odpověď.
             *
             * `draggable` s vodorovnou orientací, ne `detectHorizontalDragGestures`: ten konzumuje
             * hned od prvního pixelu a rozbil by svislý scroll seznamu. Takhle o směru rozhodne
             * až první pohyb za slop a seznam si svislé tahy nechá.
             *
             * Táhne se **doprava u obou stran konverzace**. Zrcadlit směr podle odesílatele by
             * znamenalo, že uživatel musí před každým gestem přemýšlet, čí zpráva to je.
             */
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    scope.launch {
                        // Za prahem klade tah odpor — jinak by šla bublina odtáhnout přes půl
                        // obrazovky a nebylo by poznat, že se dál nic nestane.
                        val resistance = if (drag.value < threshold) 1f else DRAG_RESISTANCE
                        val next = (drag.value + delta * resistance).coerceIn(0f, maxDrag)
                        drag.snapTo(next)

                        if (!armed && next >= threshold) {
                            armed = true
                            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        }
                    }
                },
                onDragStopped = {
                    // Odpověď odchází při PUŠTĚNÍ, ne při překročení prahu. Kdo si to rozmyslí,
                    // vrátí prst zpátky a nic se nestane.
                    if (armed && drag.value >= threshold) onReply()
                    armed = false
                    drag.animateTo(0f, returnSpec)
                },
            ),
        horizontalArrangement = if (message.isOutgoing) Arrangement.End else Arrangement.Start,
        // Tvář sedí u spodní hrany bubliny — u dlouhé zprávy by uprostřed nebylo poznat, ke které
        // části bloku patří.
        verticalAlignment = Alignment.Bottom,
    ) {
      // Mimo posouvaný `Box`: při tažení na odpověď jede bublina, tvář zůstává na místě.
      when (avatar) {
          SenderAvatar.None -> Unit
          SenderAvatar.Spacer -> Spacer(Modifier.width(SENDER_AVATAR_SLOT))
          SenderAvatar.Shown -> Avatar(
              chatId = message.senderId,
              title = message.senderName.orEmpty(),
              avatarFileId = message.senderAvatarFileId,
              size = SENDER_AVATAR_SIZE,
              modifier = Modifier.padding(end = SENDER_AVATAR_SLOT - SENDER_AVATAR_SIZE),
          )
      }

      Box(
          // Posun se čte až ve fázi layoutu (`offset { }`), ne v kompozici. Jinak by se celá
          // bublina i s obsahem přeskládávala na každý snímek tahu.
          Modifier.offset { IntOffset(drag.value.roundToInt(), 0) },
      ) {
        Surface(
            color = containerColor,
            contentColor = contentColor,
            shape = shape,
            modifier = Modifier
                .widthIn(max = 560.dp)
                // Cílová strana letu ze vstupního pole. Sdílí se CELÁ bublina, ne jen text —
                // kdyby se sdílel jen text, doletěl by sám a pozadí by pod ním na konci
                // skokem naskočilo.
                .then(
                    flight?.let { Modifier.sharedFlight(it.sharedKey, visible = true) }
                        ?: Modifier,
                )
                // Ořez MUSÍ být před klikáním. `Surface` si tvarem ořezává jen svoje pozadí,
                // a to až za modifikátory, které dostane zvenku — vlnka dlouhého stisku by se
                // tedy kreslila do obdélníku a přetekla přes zaoblená nároží.
                .clip(shape)
                // Dlouhý stisk je jediné gesto na bublině — krátké ťuknutí si šetříme
                // na otevírání médií, až přibude.
                .combinedClickable(onLongClick = onRequestMenu, onClick = {}),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                if (!continuesGroup) {
                    message.senderName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                quoted?.let {
                    QuotedMessage(
                        message = it,
                        partnerName = partnerName,
                        onClick = onQuoteClick,
                        onLongClick = onRequestMenu,
                        // V odchozí bublině nese akcent barva popředí; `primary` by splynula
                        // s pozadím bubliny.
                        accent = if (message.isOutgoing) contentColor else MaterialTheme.colorScheme.primary,
                    )
                }

                if (album != null) {
                    AlbumContent(
                        members = album,
                        onOpen = onOpenAlbumItem,
                        onRequestMenu = onRequestMenu,
                    )
                } else {
                    message.media?.let { media ->
                        MessageAttachment(
                            media = media,
                            messageId = message.id,
                            onOpen = onOpenMedia,
                            onRequestMenu = onRequestMenu,
                            uploading = message.sendState == SendState.Sending,
                        )
                    }
                }

                // Prázdný text u zprávy, která má jen fotku — pak se nekreslí nic.
                if (message.text.isNotBlank()) {
                    RichText(
                        text = message.text,
                        entities = message.entities,
                        // Míň emoji = větší. Jedno je gesto, tři už jsou věta, takže se
                        // velikost s počtem krotí.
                        style = when (largeEmoji) {
                            0 -> MaterialTheme.typography.bodyLarge
                            1 -> MaterialTheme.typography.displayLarge
                            2 -> MaterialTheme.typography.displayMedium
                            else -> MaterialTheme.typography.displaySmall
                        },
                        // V odchozí bublině je pozadí samo `primary`, takže odkaz musí vzít
                        // barvu popředí — jinak by byl neviditelný. Stejná past jako u citace.
                        linkColor = if (message.isOutgoing) contentColor else MaterialTheme.colorScheme.primary,
                        onLinkClick = onLinkClick,
                        onLongClick = onRequestMenu,
                    )
                }

                message.linkPreview?.let { preview ->
                    LinkPreviewCard(
                        preview = preview,
                        // Stejný důvod jako u citace: v odchozí bublině by `primary` splynula s pozadím.
                        accent = if (message.isOutgoing) contentColor else MaterialTheme.colorScheme.primary,
                        onOpen = { onLinkClick(preview.url) },
                        onLongClick = onRequestMenu,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                ReactionRow(
                    reactions = message.reactions,
                    onToggle = onToggleReaction,
                    modifier = Modifier.padding(top = 6.dp),
                )

                Row(
                    Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = buildString {
                            append(message.timestamp.asTimeOfDay())
                            if (message.isEdited) append(" · upraveno")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.7f),
                    )

                    message.sendState?.let { sendState ->
                        DeliveryIcon(
                            sendState = sendState,
                            read = message.id <= lastReadOutboxMessageId,
                            tint = contentColor,
                        )
                    }
                }
            }
        }

        /*
         * Nabídka visí v kompozici POD bublinou, i když se kreslí přes celou obrazovku.
         *
         * Popup si díky tomu vezme rozměry bubliny sám — `calculatePosition` dostane
         * `anchorBounds` toho prvku, pod kterým je deklarovaný — a nikam se nemusí předávat
         * souřadnice. V layoutu je to prvek nulové velikosti, takže se bublina nezvětší.
         *
         * Past: nesmí viset uvnitř OBSAHU bubliny. Tam by ukotvením byl vnitřní `Column`, tedy
         * plocha zmenšená o jeho odsazení, a díra v závoji by byla o kus menší než bublina.
         * Proto ten `Box` kolem — obepíná `Surface` přesně.
         */
        /*
         * Šipka odpovědi.
         *
         * Sedí hned vedle bubliny a jede s ní, takže je jasné, čeho se gesto týká. Je uvnitř
         * `Box`u kolem bubliny a posazená `offset`em mimo něj — do layoutu tím nezasahuje,
         * takže nic neodsouvá a neovlivňuje šířku zprávy.
         */
        ReplyHint(
            progress = { (drag.value / threshold).coerceIn(0f, 1f) },
            // Vlevo od bubliny u OBOU stran konverzace: tam vzniká mezera, když se táhne doprava.
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = -REPLY_ICON_SLOT),
        )

        menu?.let { MessageMenuOverlay(menu = it, bubbleShape = shape, handlers = menuHandlers) }
      }
    }
}

@Composable
private fun MessageInput(
    replyingTo: ChatMessage?,
    partnerName: String?,
    flight: MessageFlight?,
    onCancelReply: () -> Unit,
    onSend: (String) -> Unit,
    /** Jen ruční úpravy textu — vyprázdnění po odeslání se nehlásí, o to se stará `onSend`. */
    onDraftChanged: (String) -> Unit,
    /** Upravovaná zpráva; karta pak místo citace ukazuje, co se upravuje. */
    editing: ChatMessage?,
    onCancelEdit: () -> Unit,
    /** Text, který se má jednou vložit do pole (koncept, úprava). */
    inputFill: InputFill?,
    onInputFilled: () -> Unit,
    onOpenStickers: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickDocument: () -> Unit,
    /** Ikona tlačítka výběru ukazuje, čím se výběr otevře — emoji, nebo samolepkami. */
    expressionMode: ExpressionMode,
    /** Rozepsaný text; drží ho obrazovka, protože do něj vkládá i výběr emoji. */
    draft: TextFieldValue,
    onDraftValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {

    /*
     * Vložení textu zvenku. Koncept z minula jde JEN do prázdného pole — než ho TDLib vydá, mohl
     * uživatel začít psát. Úprava zprávy pole přepisuje vždycky. Kurzor na konec, ať se píše dál.
     */
    LaunchedEffect(inputFill) {
        val fill = inputFill ?: return@LaunchedEffect
        if (!fill.onlyIfEmpty || draft.text.isEmpty()) {
            onDraftValueChange(TextFieldValue(fill.text, selection = TextRange(fill.text.length)))
        }
        onInputFilled()
    }

    // Úprava se spouští z nabídky nad zprávou, takže pole fokus nemá. Bez něj by po „Upravit"
    // nebylo poznat, že se něco stalo, a klávesnici by si musel uživatel vyvolat sám.
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(editing?.id) {
        if (editing == null) return@LaunchedEffect
        focusRequester.requestFocus()
        keyboard?.show()
    }

    /*
     * Pruh má barvu POZADÍ, jen průsvitnou.
     *
     * Zprávy pod něj zajíždějí (padding `bottomBar` jde do `contentPadding` seznamu, viz výše)
     * a bez pruhu by se pod kartou
     * míchaly s textem. Plná barva by zase udělala ostrý předěl přes celou obrazovku. Průsvit
     * dělá závoj: je vidět, že tam obsah je, ale nepřebíjí psaní.
     */
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = INPUT_SCRIM_ALPHA),
        modifier = modifier,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            /*
             * JEDNA karta na citaci i na psaní.
             *
             * Dřív to byly dva samostatné kusy nad sebou a odpověď působila jako cizí prvek,
             * co se vsunul mezi konverzaci a vstup. Takhle je vidět, že citace patří k tomu,
             * co se právě píše.
             *
             * Pole uvnitř je průhledné — kontejner nese karta, ne ono; dvě zaoblené plochy
             * v sobě by vypadaly jako chyba.
             */
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f),
            ) {
              Column {
                // Úprava má přednost před citací: odpověď se při ní neruší, jen se na chvíli schová.
                if (editing != null) {
                    EditingHeader(message = editing, onCancel = onCancelEdit)
                } else replyingTo?.let { target ->
                    Row(
                        Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        QuotedMessage(
                            message = target,
                            partnerName = partnerName,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onCancelReply) {
                            Icon(
                                painterResource(Res.drawable.close_24dp),
                                contentDescription = "Zrušit odpověď",
                            )
                        }
                    }
                }

                Box {
            TextField(
                value = draft,
                onValueChange = {
                    val changed = it.text != draft.text
                    onDraftValueChange(it)
                    // Pohyb kurzoru nebo výběr taky volá `onValueChange`; psaní to není.
                    if (changed) onDraftChanged(it.text)
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                placeholder = { Text("Zpráva") },
                // Přílohy jen při psaní — k úpravě textové zprávy příloha nepatří.
                leadingIcon = if (editing == null) {
                    { AttachButton(onPickPhoto = onPickPhoto, onPickDocument = onPickDocument) }
                } else {
                    null
                },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            // Sheet a klávesnice by se jinak přetahovaly o spodek obrazovky.
                            keyboard?.hide()
                            onOpenStickers()
                        },
                    ) {
                        // Ikona slibuje, co se otevře: poslední použitou část výběru. Při úpravě
                        // zprávy jde otevřít jen emoji — samolepka do textu upravované zprávy nepatří.
                        if (editing != null || expressionMode == ExpressionMode.Emoji) {
                            Icon(painterResource(Res.drawable.mood_24dp), contentDescription = "Emoji")
                        } else {
                            Icon(painterResource(Res.drawable.sticky_note_2_24dp), contentDescription = "Samolepky")
                        }
                    }
                },
                maxLines = 6,
                colors = TextFieldDefaults.colors(
                    // Kontejner i podtržení nese karta kolem; pole samo nekreslí nic.
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        onSend(draft.text)
                        onDraftValueChange(TextFieldValue())
                    },
                ),
            )

            // Startovní strana letu: **už vypadá jako bublina**, ne jen jako text. Sdílí se celý
            // kontejner, takže po cestě roste i pozadí — kdyby se sdílel jen text, doletěl by
            // sám a bublina by pod ním na konci skokem naskočila.
            //
            // Kreslí se přes vyprázdněné pole, takže odeslání vypadá, jako by se napsaný text
            // proměnil v bublinu a odletěl.
            flight?.let { current ->
                Surface(
                    // MUSÍ sedět s barvou cílové bubliny, jinak zpráva po cestě změní barvu.
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(BUBBLE_CORNER, BUBBLE_CORNER, BUBBLE_TAIL, BUBBLE_CORNER),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .sharedFlight(current.sharedKey, visible = current.itemKey == null),
                ) {
                    Text(
                        text = current.text,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
                }
              }
            }

            /*
             * Odeslání je velikost MEDIUM (56 dp), ne výchozí small (40 dp).
             *
             * 56 dp je přesně minimální výška jednořádkového `TextField`, takže tlačítko se
             * spodním zarovnáním řádku sedí na střed pole bez jakéhokoli dopočítávaného odsazení.
             * Když se pole zalomí na víc řádků nebo nad ním visí citace, zůstane u posledního
             * řádku — tam, kde se píše. Small tlačítko sedělo u spodní hrany o 8 dp níž než střed.
             *
             * Je to hlavní akce obrazovky, takže větší plocha dává smysl i sama o sobě. Large
             * (96 dp) by už bylo vyšší než pole a přebíjelo by psaní.
             *
             * Tvar se při stisku mění podle M3 Expressive (`shapes`), kruh → zaoblený čtverec.
             */
            FilledIconButton(
                onClick = {
                    onSend(draft.text)
                    onDraftValueChange(TextFieldValue())
                },
                enabled = draft.text.isNotBlank(),
                shapes = IconButtonDefaults.shapes(
                    shape = IconButtonDefaults.mediumRoundShape,
                    pressedShape = IconButtonDefaults.mediumPressedShape,
                ),
                modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
            ) {
                // Při úpravě fajfka: nic se neposílá, jen potvrzuje změna.
                Icon(
                    painterResource(if (editing != null) Res.drawable.check_24dp else Res.drawable.send_24dp),
                    contentDescription = if (editing != null) "Uložit úpravu" else "Odeslat",
                    modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                )
            }
        }
    }
}

/**
 * Připnuté zprávy pod hlavičkou.
 *
 * Sbalené ukazují nejvýš [PINNED_COLLAPSED] nejnovějších. Víc by z hlavičky udělalo půl obrazovky,
 * jedna by zase schovala, že připnutých je víc — a hledat ty ostatní by znamenalo projít historii.
 * Zbytek je za „Zobrazit všechny"; rozbalený seznam se listuje a má strop výšky.
 *
 * Každý řádek skočí na svou zprávu (stejnou cestou jako citace) a má vlastní odepnutí, když to
 * právo dovolí.
 */
@Composable
private fun PinnedMessagesPanel(
    messages: List<ChatMessage>,
    expanded: Boolean,
    canUnpin: Boolean,
    onOpen: (ChatMessage) -> Unit,
    onUnpin: (ChatMessage) -> Unit,
    onToggleExpanded: () -> Unit,
) {
    val overflow = messages.size > PINNED_COLLAPSED

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        // Rozbalený seznam leží přes zprávy; stín ho od nich odlepí.
        shadowElevation = if (expanded && overflow) 3.dp else 0.dp,
        modifier = Modifier.fillMaxWidth().animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            if (expanded && overflow) {
                LazyColumn(Modifier.heightIn(max = PINNED_EXPANDED_MAX_HEIGHT)) {
                    items(messages, key = { it.id }) { message ->
                        PinnedMessageRow(message, canUnpin, onOpen, onUnpin)
                    }
                }
            } else {
                messages.take(PINNED_COLLAPSED).forEach { message ->
                    key(message.id) { PinnedMessageRow(message, canUnpin, onOpen, onUnpin) }
                }
            }

            if (overflow) {
                TextButton(
                    onClick = onToggleExpanded,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(if (expanded) "Sbalit" else "Zobrazit všechny (${messages.size})")
                }
            }
        }
    }
}

@Composable
private fun PinnedMessageRow(
    message: ChatMessage,
    canUnpin: Boolean,
    onOpen: (ChatMessage) -> Unit,
    onUnpin: (ChatMessage) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = PINNED_ROW_HEIGHT)
            .clickable { onOpen(message) }
            // Bez odepnutí je vpravo prázdno; odsazení drží text od kraje stejně jako vlevo.
            .padding(start = 16.dp, end = if (canUnpin) 4.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painterResource(Res.drawable.push_pin_filled_24dp),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            // Připnutá fotka bez popisku by byla prázdný řádek.
            text = message.text.ifBlank { message.media.describe() }.replace('\n', ' '),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (canUnpin) {
            IconButton(onClick = { onUnpin(message) }) {
                Icon(
                    painterResource(Res.drawable.close_24dp),
                    contentDescription = "Odepnout",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Náhled odkazu pod textem.
 *
 * Tvarem vychází z citace — proužek vlevo v barvě akcentu — protože jde o totéž: vložený cizí
 * obsah, který se musí na první pohled lišit od vlastního textu bubliny. Vlastní pozadí nemá;
 * druhá plocha v bublině by v odchozí `primary` vypadala jako nálepka.
 *
 * Ťuknutí otevře odkaz stejnou cestou jako odkaz v textu (přes ViewModel). Dlouhý stisk se musí
 * zopakovat, jinak by ho náhled k bublině nepustil — stejná past jako u fotky a citace.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun LinkPreviewCard(
    preview: LinkPreview,
    accent: Color,
    onOpen: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Proužek se kreslí za obsahem, ne jako `Box` s `fillMaxHeight` v řádku s
    // `height(IntrinsicSize.Min)`. Velký obrázek níž sedí v `BoxWithConstraints` (SubcomposeLayout)
    // a ten intrinsické míry neumí — aplikace spadla při zobrazení každého takového náhledu.
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onLongClick)
            .drawBehind {
                drawRoundRect(
                    color = accent,
                    size = Size(LINK_PREVIEW_STRIPE.toPx(), size.height),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
            .padding(start = LINK_PREVIEW_STRIPE + 8.dp),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    preview.siteName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelLarge,
                            color = accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    preview.title?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    preview.description?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = LINK_PREVIEW_DESCRIPTION_LINES,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Malý obrázek vedle textu, jen když odesílatel nechtěl velký.
                if (!preview.largePhoto) {
                    preview.photo?.let { photo ->
                        RemoteImage(
                            fileId = photo.fileId,
                            thumbnailFileId = photo.thumbnailFileId,
                            contentDescription = null,
                            modifier = Modifier
                                .size(LINK_PREVIEW_THUMBNAIL)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }

            if (preview.largePhoto) {
                preview.photo?.let { photo ->
                    // Výška ručně, ne `aspectRatio` — ten by strop z `heightIn` neuplatnil (viz fotka).
                    BoxWithConstraints(Modifier.padding(top = 4.dp)) {
                        val ratio = if (photo.width > 0 && photo.height > 0) {
                            photo.width.toFloat() / photo.height.toFloat()
                        } else {
                            LINK_PREVIEW_FALLBACK_RATIO
                        }
                        RemoteImage(
                            fileId = photo.fileId,
                            thumbnailFileId = photo.thumbnailFileId,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((maxWidth / ratio).coerceAtMost(MAX_PHOTO_HEIGHT))
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tlačítko příloh s nabídkou Fotka / Soubor.
 *
 * Dvě položky, ne rovnou výběr: Photo Picker ukazuje jen fotky a výběr dokumentu nemá náhledy, takže
 * jeden systémový výběr pro obojí by byl horší v obou případech. Klávesnice se zavře, než se otevře
 * systémový výběr — jinak by po návratu vyskočila přes bublinu s nahráváním.
 */
@Composable
private fun AttachButton(onPickPhoto: () -> Unit, onPickDocument: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(Res.drawable.attach_file_24dp), contentDescription = "Přiložit")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Fotka") },
                leadingIcon = { Icon(painterResource(Res.drawable.image_24dp), contentDescription = null) },
                onClick = {
                    open = false
                    keyboard?.hide()
                    onPickPhoto()
                },
            )
            DropdownMenuItem(
                text = { Text("Soubor") },
                leadingIcon = { Icon(painterResource(Res.drawable.description_24dp), contentDescription = null) },
                onClick = {
                    open = false
                    keyboard?.hide()
                    onPickDocument()
                },
            )
        }
    }
}

/**
 * Hlavička karty při úpravě zprávy.
 *
 * Vypadá jako citace (proužek, popisek, původní text), protože plní stejnou roli: říká, k čemu
 * se vztahuje to, co se píše. Původní text zůstává vidět, i když ho uživatel v poli přepíše.
 */
@Composable
private fun EditingHeader(message: ChatMessage, onCancel: () -> Unit) {
    Row(
        Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(Res.drawable.edit_24dp),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp).size(18.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = "Úprava zprávy",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onCancel) {
            Icon(painterResource(Res.drawable.close_24dp), contentDescription = "Zrušit úpravu")
        }
    }
}

/**
 * Citovaná zpráva — v bublině nad obsahem odpovědi i nad vstupním polem.
 *
 * Svislý pruh vlevo nese barvu `primary`; je to jediný prvek, který cituje cizí obsah, a musí být
 * na první pohled odlišitelný od vlastního textu bubliny.
 */
@Composable
private fun QuotedMessage(
    message: ChatMessage,
    partnerName: String?,
    modifier: Modifier = Modifier,
    /**
     * Barva proužku a jména.
     *
     * Musí jít předat: uvnitř odchozí bubliny je pozadí `primary`, takže natvrdo zadaná
     * `primary` by citaci udělala neviditelnou — stejná barva na stejné barvě.
     */
    accent: Color = MaterialTheme.colorScheme.primary,
    /**
     * Ťuknutí skočí na citovanou zprávu. `null` nad vstupním polem, kde se na ni skákat nemá
     * proč — uživatel ji právě vybral.
     */
    onClick: (() -> Unit)? = null,
    /**
     * Dlouhý stisk se musí zopakovat — potomek s vlastním klikáním ho k bublině nepustí, takže
     * by nad citací nešla otevřít nabídka. Stejná past jako u fotky.
     */
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .then(
                if (onClick != null) {
                    // Ořez kvůli vlnce; citace nemá vlastní pozadí, takže bez něj by se kreslila
                    // do ostrého obdélníku.
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier
                },
            )
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )

        Column {
            Text(
                text = when {
                    message.isOutgoing -> "Vy"
                    // Ve skupině nese jméno sama zpráva; v soukromém chatu je odesílatel
                    // vždycky protějšek, takže se vezme název chatu.
                    else -> message.senderName ?: partnerName ?: "Odpověď"
                },
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // U zprávy bez textu (samotná fotka) by citace byla prázdná.
                text = message.text.ifBlank { message.media.describe() },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun MessageMedia?.describe(): String = when (this) {
    is MessageMedia.Photo -> "Fotka"
    is MessageMedia.Attachment -> label
    // V citaci není kam kreslit obrázek, takže samolepka se popíše svým emoji.
    is MessageMedia.Sticker -> emoji.ifBlank { "Samolepka" }
    is MessageMedia.AnimatedEmoji -> emoji
    null -> ""
}

/** Unixový čas v sekundách → „14:32" v místní zóně. */
private fun Int.asTimeOfDay(): String {
    val local = Instant.fromEpochSeconds(this.toLong())
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}

/**
 * Co se ukáže v detail panelu, když je dost místa na dva panely, ale žádný chat není vybraný.
 * Bez tohohle by tam zela prázdná plocha, což vypadá jako chyba vykreslení.
 */
@Composable
fun ConversationPlaceholder(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                "Vyber chat vlevo.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Šipka, která se odkrývá při tažení zprávy.
 *
 * [progress] je **funkce, ne hodnota**. Čte se až ve vykreslovací fázi (`graphicsLayer`), takže
 * se při tahu nic nerekomponuje — jinak by každý snímek gesta znamenal přeskládání bubliny.
 */
@Composable
private fun ReplyHint(progress: () -> Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(REPLY_ICON_SIZE)
            .graphicsLayer {
                val current = progress()
                alpha = current
                // Roste s tahem, takže je poznat, jak daleko k prahu zbývá.
                val scale = REPLY_ICON_MIN_SCALE + (1f - REPLY_ICON_MIN_SCALE) * current
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(Res.drawable.reply_24dp),
            // Popis by se předčítal u každé zprávy v seznamu; gesto samo o sobě není prvek,
            // se kterým by čtečka obrazovky pracovala.
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Odroluje tak, aby položka seděla u horní hrany, nebo na středu.
 *
 * `scrollToItem` v obráceném seznamu posadí položku ke SPODNÍ hraně, protože tam je jeho začátek.
 * U skoku na nepřečtené je to přesně naopak, než je potřeba: první nepřečtená má být nahoře
 * a nepřečtené pod ní. U citace zase patří na střed, aby bylo vidět, na co navazuje. Dorovnání se
 * počítá až ze **změřeného** layoutu, protože výška bubliny se zprávu od zprávy liší.
 *
 * Počítá se s **odsazením obsahu**. Dole je pod ním vstupní pole, takže viditelná plocha začíná
 * nad ním; dřív se horní hrana brala z celé výšky viewportu a položka zajela o výšku pole pod
 * horní okraj.
 */
private suspend fun LazyListState.scrollToAnchor(index: Int, anchor: ScrollAnchor) {
    scrollToItem(index)

    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    // Offset roste od začátku seznamu, tedy v obráceném rozvržení odspodu nahoru. Nula je horní
    // hrana spodního odsazení, `visibleHeight` spodní hrana horního.
    val visibleHeight = info.viewportEndOffset - info.afterContentPadding
    val desired = when (anchor) {
        ScrollAnchor.Top -> visibleHeight - item.size
        ScrollAnchor.Center -> (visibleHeight - item.size) / 2
    }.coerceAtLeast(0)
    scrollBy((item.offset - desired).toFloat())
}

/**
 * Předěl „Nepřečtené zprávy".
 *
 * Akcentní barva, ne šedá jako u data: je to jediná věc na obrazovce, kterou uživatel po otevření
 * chatu opravdu hledá.
 */
@Composable
private fun UnreadDivider(modifier: Modifier = Modifier) {
    CenteredNote(
        text = "Nepřečtené zprávy",
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.padding(vertical = DAY_DIVIDER_SPACING),
    )
}

/**
 * Tvář odesílatele vedle bubliny ve skupině.
 *
 * [Spacer] drží místo u zpráv uprostřed bloku — bez něj by bubliny téhož člověka skákaly
 * doleva a doprava podle toho, u které zrovna tvář je.
 */
private enum class SenderAvatar { None, Spacer, Shown }

/**
 * Navazuje tahle (novější) zpráva na [older] ve stejném bloku?
 *
 * Stejná pravidla jako seskupení bublin: stejná strana, stejný odesílatel, stejný den. Servisní
 * oznámení blok přeruší — kreslí se na střed a tvář nad ním by visela ve vzduchu.
 */
private fun ChatMessage?.continues(older: ChatMessage): Boolean =
    this != null &&
        !isService &&
        isOutgoing == older.isOutgoing &&
        senderId == older.senderId &&
        timestamp.isSameDayAs(older.timestamp)

/**
 * Naposledy zpracovaná nejnovější zpráva, pro ukotvení seznamu dole.
 *
 * Obyčejná třída, ne `mutableStateOf`: zapisuje se v kompozici a zápis do snapshot stavu by
 * obrazovku jen znovu rekomponoval, aniž by se tím cokoli změnilo.
 */
private class NewestItem {
    var key: Long? = null
    var id: Long = 0L
}

private const val LOAD_MORE_THRESHOLD = 5

/** Kolik připnutých zpráv je vidět bez rozbalení. */
private const val PINNED_COLLAPSED = 3

/** Řádek připnuté zprávy. Zároveň minimální dotyková plocha. */
private val PINNED_ROW_HEIGHT = 44.dp

/** Strop výšky rozbaleného seznamu připnutých; nad ním se listuje. Konverzace musí zůstat vidět. */
private val PINNED_EXPANDED_MAX_HEIGHT = 320.dp

/** Profilovka odesílatele ve skupině. Menší než v seznamu chatů — doprovází bublinu, nevede ji. */
private val SENDER_AVATAR_SIZE = 32.dp

/** Místo pro profilovku i s mezerou k bublině; stejné u zpráv s tváří i bez ní. */
private val SENDER_AVATAR_SLOT = 40.dp

/** O kolik položek výš od spodku se objeví tlačítko „dolů". */
private const val SCROLL_DOWN_THRESHOLD_ITEMS = 3

/** Tlačítko „dolů" bez počtu je kruh téhle velikosti — zároveň minimální dotyková plocha. */
private val SCROLL_DOWN_BUTTON_SIZE = 48.dp

/** Jak výrazné je zvýraznění zprávy po skoku. Jemné: má ukázat místo, ne překrýt obsah. */
private const val HIGHLIGHT_ALPHA = 0.14f

private val HIGHLIGHT_CORNER = 12.dp
private const val READ_DEBOUNCE_MS = 400L

/** Prefix klíče reklamní položky; podle něj se v seznamu pozná pro hlášení zhlédnutí. */
private const val SPONSORED_KEY_PREFIX = "sponsored-"

/** Jak dlouho musí být reklama v klidu na obrazovce, než se zhlédnutí nahlásí. */
private const val SPONSORED_VIEW_DEBOUNCE_MS = 1000L

/** Rozestup mezi zprávami téhož odesílatele. Patří k sobě, tak se drží u sebe. */
private val MESSAGE_SPACING = 4.dp

/** Mezera navíc tam, kde začíná nová skupina — jiný odesílatel, jiný blok. */
private val GROUP_SPACING = 8.dp

/** Vzduch kolem předělu dne. Odděluje bloky, takže potřebuje víc než rozestup mezi zprávami. */
private val DAY_DIVIDER_SPACING = 6.dp

/**
 * Průsvit pruhu pod vstupním polem.
 *
 * Dost na to, aby text pod ním nerušil, ale ne plná barva — ta by udělala ostrý předěl přes
 * celou obrazovku, což je právě to, co se odstraňovalo.
 */
private const val INPUT_SCRIM_ALPHA = 0.93f

/** Zaoblení bubliny. Sdílí ho i startovní strana letu, aby se tvar po cestě neměnil. */
private val BUBBLE_CORNER = 18.dp

/** Stažené nároží u navazujících bublin téhož odesílatele. */
private val BUBBLE_TAIL = 4.dp

/** Popis náhledu odkazu. Víc řádků by z bubliny udělalo článek. */
private const val LINK_PREVIEW_DESCRIPTION_LINES = 3

/** Malý obrázek vedle textu náhledu. */
private val LINK_PREVIEW_THUMBNAIL = 56.dp

/** Šířka proužku vlevo od náhledu odkazu — stejná jako u citace. */
private val LINK_PREVIEW_STRIPE = 3.dp

/** Poměr stran velkého obrázku náhledu bez rozměrů — typický obrázek pro sdílení odkazu. */
private const val LINK_PREVIEW_FALLBACK_RATIO = 1.91f

/** Kolečko s průběhem nahrávání přes přílohu. */
private val UPLOAD_INDICATOR_SIZE = 48.dp

/** Spára mezi fotkami alba. */
private val ALBUM_GAP = 2.dp

/** Strop výšky řádku alba s víc fotkami — dvě čtvercové fotky na širokém displeji by byly obří. */
private val ALBUM_TILE_MAX_HEIGHT = 200.dp

/** Strop výšky fotky v bublině. Svislé snímky obrazovky by jinak zabraly celou obrazovku. */
private val MAX_PHOTO_HEIGHT = 320.dp

/**
 * Animované emoji. Menší než samolepka — je to pořád „jedno emoji", ne obrázek — ale větší než
 * znak v `displayLarge`, protože animace má kolem kresby vlastní vzduch.
 */
private val ANIMATED_EMOJI_SIZE = 112.dp

/** Delší hrana samolepky. Telegram je kreslí zhruba takhle velké. */
private val STICKER_SIZE = 160.dp

/** Nad tímhle počtem už emoji netvoří gesto, ale odstavec, a kreslí se normálně velké. */
private const val MAX_LARGE_EMOJI = 3

/**
 * Jak daleko se musí zpráva odtáhnout, aby se z gesta stala odpověď.
 *
 * Dost na to, aby to nešlo udělat omylem při scrollu, ale ne tolik, aby se palec musel
 * překládat.
 */
private val REPLY_THRESHOLD = 56.dp

/** Kam až se dá zpráva odtáhnout. Za prahem už jen kus navíc, aby gesto mělo doraz. */
private val REPLY_MAX_DRAG = 76.dp

/** Jak moc se tah za prahem brzdí. */
private const val DRAG_RESISTANCE = 0.3f

private val REPLY_ICON_SIZE = 32.dp

/** O kolik vlevo od bubliny šipka sedí — velikost ikony a mezera. */
private val REPLY_ICON_SLOT = 40.dp

/** Jak malá je šipka na začátku tahu. */
private const val REPLY_ICON_MIN_SCALE = 0.6f

/** Stav doručení tak, jak se kreslí — čtyři stavy, každý s vlastní ikonou, popisem a barvou. */
private enum class Delivery { Sending, Sent, Read, Failed }

/**
 * Stav doručení odchozí zprávy.
 *
 * Čtyři stavy ve čtyřech ikonách, jak je zvykem: hodiny (odesílá se), fajfka (na serveru),
 * dvojitá fajfka (protějšek přečetl), vykřičník (selhalo).
 *
 * „Přečteno" nepochází ze zprávy, ale z ukazatele na chatu — proto se sem předává zvlášť.
 */
@Composable
private fun DeliveryIcon(
    sendState: SendState,
    read: Boolean,
    tint: Color,
) {
    val delivery = when {
        sendState == SendState.Sending -> Delivery.Sending
        sendState == SendState.Failed -> Delivery.Failed
        read -> Delivery.Read
        else -> Delivery.Sent
    }

    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val fastEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

    /*
     * Nová ikona naskočí zvětšením, stará jen rychle zmizí. NE `Crossfade`.
     *
     * Crossfade prolíná obě ikony současně, takže v půlce přechodu jsou obě zhruba napůl
     * průhledné přes sebe. Fajfka a dvojitá fajfka se kryjí, a společná část tak na okamžik
     * zeslábne — to je ten problik. Navíc jel na pevném `tween` mimo `MotionScheme` a barvu
     * i popis bral zvenku, takže odcházející hodiny při selhání zčervenaly dřív, než zmizely.
     *
     * `using null`: ikony mají stejnou velikost, není co animovat, a ořez přechodu by uřízl
     * pružný přeběh zvětšení.
     */
    AnimatedContent(
        targetState = delivery,
        transitionSpec = {
            (fadeIn(effects) + scaleIn(spatial, initialScale = DELIVERY_ICON_INITIAL_SCALE)) togetherWith
                fadeOut(fastEffects) using null
        },
        contentAlignment = Alignment.Center,
        label = "delivery",
    ) { current ->
        val (icon, description) = when (current) {
            Delivery.Sending -> Res.drawable.schedule_24dp to "Odesílá se"
            Delivery.Sent -> Res.drawable.check_24dp to "Odesláno"
            Delivery.Read -> Res.drawable.done_all_24dp to "Přečteno"
            Delivery.Failed -> Res.drawable.error_24dp to "Odeslání selhalo"
        }

        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            // Selhání je jediný stav, který uživatel musí zaregistrovat — dostane chybovou barvu.
            // Počítá se z `current`, ne z cílového stavu: odcházející ikona si barvu drží.
            tint = if (current == Delivery.Failed) {
                MaterialTheme.colorScheme.error
            } else {
                tint.copy(alpha = 0.7f)
            },
            modifier = Modifier.size(14.dp),
        )
    }
}

/** Jak malá nová ikona doručení naskočí. Dost na to, aby změna šla postřehnout koutkem oka. */
private const val DELIVERY_ICON_INITIAL_SCALE = 0.5f

/**
 * Příloha uvnitř bubliny.
 *
 * Fotka si drží **poměr stran z metadat zprávy**, ne z načteného obrázku — díky tomu má bublina
 * správnou výšku od začátku a seznam nepodskočí, až se obrázek dotáhne.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MessageAttachment(
    media: MessageMedia,
    messageId: Long,
    onOpen: () -> Unit,
    onRequestMenu: () -> Unit,
    /** Odesílaná zpráva: přes přílohu se kreslí průběh nahrávání. */
    uploading: Boolean = false,
) {
    val shape = RoundedCornerShape(12.dp)

    when (media) {
        is MessageMedia.Photo -> {
            val ratio = if (media.width > 0 && media.height > 0) {
                media.width.toFloat() / media.height.toFloat()
            } else {
                // Neznámé rozměry: čtverec je méně rušivý než nulová výška.
                1f
            }

            // Výška se počítá ručně, ne přes `aspectRatio`: ten si ji dopočítá ze šířky a strop
            // z předřazeného `heightIn` neuplatní, takže svislý snímek obrazovky vyrostl přes
            // celou bublinu a popisek pod ním se s obrázkem překryl.
            BoxWithConstraints {
                val height = (maxWidth / ratio).coerceAtMost(MAX_PHOTO_HEIGHT)

                RemoteImage(
                    fileId = media.fileId,
                    thumbnailFileId = media.thumbnailFileId,
                    contentDescription = "Fotka",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height)
                        .clip(shape)
                        .sharedNavElement(photoKey(messageId))
                        /*
                         * Gesta patří FOTCE, ne celé bublině.
                         *
                         * Kdyby ťuknutí odchytávala bublina, sebralo by ho i odkazům v popisku.
                         * Dlouhý stisk se musí zopakovat tady: potomek s vlastním klikáním ho
                         * nepustí k rodiči, takže by nad fotkou nešla otevřít nabídka.
                         */
                        .combinedClickable(onClick = onOpen, onLongClick = onRequestMenu),
                )

                if (uploading) {
                    UploadIndicator(fileId = media.fileId, modifier = Modifier.align(Alignment.Center))
                }
            }
        }

        is MessageMedia.Attachment -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val uploadingFile = media.fileId?.takeIf { uploading }
            if (uploadingFile != null) {
                // Místo náhledu průběh: u odesílaného souboru je to jediná informace, která se mění.
                UploadIndicator(fileId = uploadingFile)
            } else {
                media.thumbnailFileId?.let { thumbnail ->
                    RemoteImage(
                        fileId = thumbnail,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(shape),
                    )
                }
            }
            Text(
                text = media.label,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        is MessageMedia.Sticker -> StickerImage(
            sticker = media,
            /*
             * Ťuknutí otevře sadu. Bez vlnky: samolepka je průhledný tvar bez bubliny a obdélníková
             * vlnka přes něj by vypadala jako chyba. Dlouhý stisk se musí zopakovat — potomek
             * s vlastním klikáním ho k bublině nepustí (stejná past jako u fotky).
             */
            modifier = Modifier.combinedClickable(
                interactionSource = null,
                indication = null,
                onClick = onOpen,
                onLongClick = onRequestMenu,
            ),
        )
        is MessageMedia.AnimatedEmoji -> AnimatedEmojiImage(media)
    }
}

/**
 * Průběh nahrávání odesílané přílohy.
 *
 * Vlnitý kruh z M3 Expressive na tmavém kolečku — musí být čitelný přes jakoukoli fotku, proto
 * závoj `scrim` a bílá, stejně jako ovládání v prohlížeči médií. Dokud TDLib nahrávání neohlásí
 * (připravuje soubor), točí se neurčitě; pak ukazuje skutečný podíl.
 */
@Composable
private fun UploadIndicator(fileId: Int, modifier: Modifier = Modifier) {
    val progress = rememberUploadProgress(fileId)

    Box(
        modifier
            .size(UPLOAD_INDICATOR_SIZE)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        if (progress == null) {
            CircularWavyProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
        } else {
            CircularWavyProgressIndicator(
                progress = { progress },
                color = Color.White,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

/**
 * Obsah alba v bublině.
 *
 * Fotky (a videa s náhledem) jako **mřížka** — celé album je vidět najednou, jako v ostatních
 * klientech. Carousel by část alba schoval a jeho vodorovný tah by se pral se swipem na odpověď.
 * Album dokumentů nebo audia (ta se nemíchají s fotkami) je seznam příloh pod sebou.
 */
@Composable
private fun AlbumContent(
    members: List<ChatMessage>,
    onOpen: (messageId: Long) -> Unit,
    onRequestMenu: () -> Unit,
) {
    val visual = members.all { it.media.isAlbumTile() }
    if (!visual) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            members.forEach { member ->
                member.media?.let { media ->
                    MessageAttachment(
                        media = media,
                        messageId = member.id,
                        onOpen = { onOpen(member.id) },
                        onRequestMenu = onRequestMenu,
                        uploading = member.sendState == SendState.Sending,
                    )
                }
            }
        }
        return
    }

    Column(
        // Zaoblení nese celá mřížka, ne jednotlivé dlaždice: uvnitř jsou jen tenké spáry.
        Modifier.clip(RoundedCornerShape(12.dp)),
        verticalArrangement = Arrangement.spacedBy(ALBUM_GAP),
    ) {
        var start = 0
        albumRowSizes(members.size).forEach { size ->
            val rowMembers = members.subList(start, start + size)
            start += size

            BoxWithConstraints {
                // Jedna fotka na řádek podle svého poměru stran (se stropem), víc fotek čtverce —
                // řádek se tak nikdy nerozjede do nekonečné výšky u svislých snímků.
                val single = rowMembers.singleOrNull()
                val height = if (single != null) {
                    (maxWidth / single.media.albumRatio()).coerceAtMost(MAX_PHOTO_HEIGHT)
                } else {
                    ((maxWidth - ALBUM_GAP * (size - 1)) / size).coerceAtMost(ALBUM_TILE_MAX_HEIGHT)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(ALBUM_GAP)) {
                    rowMembers.forEach { member ->
                        AlbumTile(
                            member = member,
                            onOpen = { onOpen(member.id) },
                            onRequestMenu = onRequestMenu,
                            modifier = Modifier.weight(1f).height(height),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dlaždice alba. Fotka jde do prohlížeče se sdíleným prvkem, stejně jako samostatná fotka; video
 * zatím jen ukazuje náhled a popisek, přehrávat neumíme.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun AlbumTile(
    member: ChatMessage,
    onOpen: () -> Unit,
    onRequestMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val media = member.media) {
        is MessageMedia.Photo -> RemoteImage(
            fileId = media.fileId,
            thumbnailFileId = media.thumbnailFileId,
            contentDescription = "Fotka",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .sharedNavElement(photoKey(member.id))
                // Dlouhý stisk se opakuje — potomek s vlastním klikáním ho k bublině nepustí.
                .combinedClickable(onClick = onOpen, onLongClick = onRequestMenu),
        )

        is MessageMedia.Attachment -> Box(modifier.combinedClickable(onClick = {}, onLongClick = onRequestMenu)) {
            media.thumbnailFileId?.let { thumbnail ->
                RemoteImage(
                    fileId = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            ) {
                Text(
                    text = media.label,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        else -> Box(modifier)
    }
}

/** Patří příloha do mřížky? Fotka, nebo video či GIF s náhledem; dokumenty a audio ne. */
private fun MessageMedia?.isAlbumTile(): Boolean = when (this) {
    is MessageMedia.Photo -> true
    is MessageMedia.Attachment -> thumbnailFileId != null
    else -> false
}

/** Poměr stran dlaždice samotné na řádku. Video bez rozměrů je 16:9, neznámá fotka čtverec. */
private fun MessageMedia?.albumRatio(): Float = when (this) {
    is MessageMedia.Photo -> if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 1f
    else -> 16f / 9f
}

/**
 * Kolik fotek jde do kterého řádku mřížky, shora dolů.
 *
 * Pravidla místo výpočtu mozaiky podle poměrů stran: řádky po 2–3 vypadají vyrovnaně bez ohledu na
 * to, co je na fotkách, a jedna velká fotka nahoře jen u lichých malých alb, kde by jinak jedna
 * zbyla sama dole. Telegram dovolí nejvýš 10 zpráv v albu.
 */
internal fun albumRowSizes(count: Int): List<Int> = when (count) {
    0 -> emptyList()
    1 -> listOf(1)
    2 -> listOf(2)
    3 -> listOf(1, 2)
    4 -> listOf(2, 2)
    5 -> listOf(2, 3)
    6 -> listOf(3, 3)
    7 -> listOf(2, 2, 3)
    8 -> listOf(2, 3, 3)
    9 -> listOf(3, 3, 3)
    else -> {
        // Nad deset (ze serveru nepřijde, ale nerozbije se to): řádky po třech, zbytek do prvního.
        val rest = count % 3
        (if (rest == 0) emptyList() else listOf(rest)) + List(count / 3) { 3 }
    }
}

/**
 * Samolepka.
 *
 * Kreslí se **bez pozadí i bez zaoblení** — samolepka má vlastní tvar s průhledností a rámeček
 * kolem ní by z ní udělal obrázek. Když se nedá vykreslit ani ona, ani její náhled, zbývá emoji,
 * které k ní Telegram vede; velké, ať je poznat, že to je celý obsah zprávy, a ne popisek.
 *
 * Animovaná `.tgs` hraje přes [AnimatedSticker] a do té doby (i když se načíst nepodaří) ukazuje
 * statický náhled. `.webm` zatím zůstává jen náhledem.
 */
@Composable
private fun StickerImage(sticker: MessageMedia.Sticker, modifier: Modifier = Modifier) {
    val description = sticker.emoji.ifBlank { "Samolepka" }
    val staticFileId = sticker.fileId ?: sticker.thumbnailFileId

    if (staticFileId == null && sticker.animationFileId == null) {
        Text(text = description, style = MaterialTheme.typography.displayMedium)
        return
    }

    // Poměr stran z metadat zprávy, ne z načteného souboru — jinak by seznam poskočil,
    // až se obrázek dotáhne. Nulové rozměry samolepka mít nemá, ale dělit se jimi nedá,
    // takže z ní v tom případě je čtverec.
    val ratio = if (sticker.width > 0 && sticker.height > 0) {
        sticker.width.toFloat() / sticker.height.toFloat()
    } else {
        1f
    }

    val width = if (ratio >= 1f) STICKER_SIZE else STICKER_SIZE * ratio
    val height = if (ratio >= 1f) STICKER_SIZE / ratio else STICKER_SIZE
    val size = modifier.size(width = width, height = height)

    val still: @Composable () -> Unit = {
        if (staticFileId != null) {
            RemoteImage(
                fileId = staticFileId,
                contentDescription = description,
                contentScale = ContentScale.Fit,
                // Samolepka je průhledná; podložka pod ní by z ní udělala obrázek v rámečku.
                background = Color.Transparent,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Animace bez náhledu: než se načte, drží místo aspoň emoji.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = description, style = MaterialTheme.typography.displayMedium)
            }
        }
    }

    val animation = sticker.animationFileId
    if (animation != null) {
        AnimatedSticker(
            fileId = animation,
            contentDescription = description,
            modifier = size,
            placeholder = still,
        )
    } else {
        Box(size) { still() }
    }
}

/**
 * Animované emoji.
 *
 * Než se animace načte (a když se načíst nepodaří), stojí na jejím místě tentýž znak velkým písmem,
 * jak se emoji kreslilo dřív — přechod na animaci je tak výměna obrázku za obrázek, ne naskočení
 * z prázdna. Velikost místa je pevná, aby se bublina při výměně nepřeskládala.
 */
@Composable
private fun AnimatedEmojiImage(emoji: MessageMedia.AnimatedEmoji) {
    AnimatedSticker(
        fileId = emoji.animationFileId,
        contentDescription = emoji.emoji,
        modifier = Modifier.size(ANIMATED_EMOJI_SIZE),
        placeholder = {
            Text(text = emoji.emoji, style = MaterialTheme.typography.displayLarge)
        },
    )
}

/**
 * Systémové oznámení o dění v chatu.
 *
 * Čas se u něj nevypisuje: u „někdo se připojil" je minuta k ničemu a řádek by jen zdvojnásobil.
 */
@Composable
private fun ServiceMessage(message: ChatMessage, modifier: Modifier = Modifier) {
    CenteredNote(text = message.text, modifier = modifier)
}

/**
 * Předěl dne.
 *
 * Bez něj se v delší historii nedá poznat, kde končí včerejšek — u zprávy se vypisují jen hodiny,
 * protože datum má nést právě tohle.
 *
 * Vypadá stejně jako systémové oznámení schválně: obojí je poznámka na okraji konverzace, ne
 * zpráva od někoho. Dvě různá pojetí téhož by seznam jen roztříštila.
 */
@Composable
private fun DayDivider(timestamp: Int, modifier: Modifier = Modifier) {
    CenteredNote(
        text = timestamp.asDayLabel(),
        // Víc místa než u oznámení: předěl odděluje bloky, takže potřebuje kolem sebe vzduch.
        modifier = modifier.padding(vertical = DAY_DIVIDER_SPACING),
    )
}

/**
 * Poznámka na střed konverzace.
 *
 * Malé písmo v `surfaceContainer` — nemá to soupeřit s bublinami, jen říct, co se mezi nimi děje.
 */
@Composable
private fun CenteredNote(
    text: String,
    modifier: Modifier = Modifier,
    /** Nenulová jen u plovoucí varianty, která leží nad zprávami místo mezi nimi. */
    shadowElevation: Dp = 0.dp,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(BUBBLE_CORNER),
            color = color,
            contentColor = contentColor,
            shadowElevation = shadowElevation,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** Řádek pod názvem chatu. [accent] = živá informace (online, píše…), jinak tlumeně. */
private data class HeaderSubtitle(val text: String, val accent: Boolean)

/**
 * „Naposledy viděn" do hlavičky.
 *
 * Telegram dostupnost **záměrně rozmazává**: kdo si to v soukromí vypne, je vidět jen jako
 * „naposledy nedávno" a přesný čas nedostaneme. Proto se nikde nedopočítává nic přesnějšího,
 * než co server dal — vymyslet minutu, kterou neznáme, by bylo horší než neurčitost.
 */
private fun Presence.describe(): String = when (this) {
    Presence.Online -> "online"
    Presence.Recently -> "naposledy nedávno"
    Presence.LastWeek -> "naposledy tento týden"
    Presence.LastMonth -> "naposledy tento měsíc"
    is Presence.Offline -> "naposledy " + wasOnline.asRelativeTime()
}
