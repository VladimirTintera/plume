package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.Presence
import eu.tintera.plume.core.telegram.SendState
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.asDayLabel
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
import eu.tintera.plume.core.ui.RichText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.arrow_downward_24dp
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.done_all_24dp
import eu.tintera.plume.resources.error_24dp
import eu.tintera.plume.resources.reply_24dp
import eu.tintera.plume.resources.schedule_24dp
import eu.tintera.plume.resources.send_24dp
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
    val scope = rememberCoroutineScope()

    // Chyby se dřív zahazovaly: `state.error` se vykreslovalo jen u prázdné konverzace, takže
    // neúspěšná reakce nebo odeslání zmizely bez jediné stopy.
    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissError()
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

    /** Změřená výška plovoucího vstupního pole; drží odsazení seznamu v souladu s realitou. */
    var inputHeight by remember { mutableStateOf(0.dp) }

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
            onCopy = viewModel::copyMessage,
            onDelete = viewModel::requestDelete,
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
     * Skok na první nepřečtenou.
     *
     * Zprávu hledáme podle id, ne podle indexu — než efekt doběhne, může seznam narůst o novou
     * zprávu a index by ukazoval jinam.
     */
    LaunchedEffect(state.scrollToMessageId) {
        val targetId = state.scrollToMessageId ?: return@LaunchedEffect
        val index = state.messages.indexOfFirst { it.id == targetId }
        // Před zprávami stojí sponzorované položky, takže index v seznamu není index v LazyColumnu.
        if (index >= 0) listState.scrollUnderTop(index + state.sponsored.size)
        viewModel.consumeScrollTarget()
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

                            // Podtitul jen v soukromém chatu — skupina žádnou dostupnost nemá.
                            state.presence?.let { presence ->
                                Text(
                                    text = presence.describe(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (presence is Presence.Online) {
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
        // Vstupní pole NENÍ `bottomBar`: jako bottomBar by ukusovalo z výšky seznamu a zprávy
        // by před ním končily. Plave nad obsahem, který pod něj zajíždí — viz níž.
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        // Nabídka nad zprávou se kreslí až v bublině — viz `MessageMenuOverlay`.

        // Plný seznam emoji za tlačítkem „+". Zůstává bottom sheetem, protože se v něm listuje —
        // a to popup neumí.
        state.reactionSheet?.let { picker ->
            ReactionPickerSheet(
                picker = picker,
                onPick = { emoji -> viewModel.toggleReaction(picker.messageId, emoji) },
                onDismiss = viewModel::closeMenus,
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

        Box(Modifier.fillMaxSize().padding(padding)) {
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
                    // Dole se odsazuje o výšku vstupního pole, aby nejnovější zpráva v klidovém
                    // stavu nekončila pod ním. Při scrollu už obsah pod pole zajede — to je
                    // rozdíl proti `bottomBar`, který místo ukusoval natrvalo.
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 8.dp,
                        bottom = 8.dp + inputHeight,
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
                    // by bublina problikla jako odebraná a znovu přidaná položka.
                    itemsIndexed(state.messages, key = { _, message -> state.keyOf(message) }) { index, message ->
                        // Seznam jde od nejnovější, takže „předchozí zpráva v čase" je ta
                        // s VYŠŠÍM indexem.
                        val older = state.messages.getOrNull(index + 1)

                        /*
                         * Začíná touhle zprávou nový den?
                         *
                         * `older == null` je nejstarší načtená zpráva. Datum se u ní ukáže taky —
                         * jakmile se dotáhne starší stránka, předěl se posune nahoru. Lepší než
                         * nechat vršek historie bez data.
                         */
                        val startsDay = older == null ||
                            !older.timestamp.isSameDayAs(message.timestamp)

                        /*
                         * Je tohle první nepřečtená zpráva?
                         *
                         * Hranici drží ViewModel z okamžiku otevření chatu, takže se předěl
                         * neposouvá, jak se zprávy postupně označují za přečtené.
                         */
                        val startsUnread = state.unreadBoundaryId?.let { boundary ->
                            message.id > boundary && older != null && older.id <= boundary
                        } == true

                        val flight = state.flight?.takeIf { it.itemKey == state.keyOf(message) }

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
                                quoted = message.replyToMessageId?.let { id ->
                                    byId[id] ?: state.quotes[id]
                                },
                                // V soukromém chatu se jméno odesílatele u zpráv nevyplňuje (dává
                                // smysl jen ve skupinách), takže citace by neměla koho uvést.
                                partnerName = state.info?.title,
                                // Za předělem začíná nový blok, i když píše tentýž člověk — jinak by
                                // první bublina pod datem navazovala na zprávu z minulého dne.
                                previous = older.takeIf { !startsDay },
                                lastReadOutboxMessageId = state.lastReadOutboxMessageId,

                                flight = flight,
                                onToggleReaction = { emoji ->
                                    viewModel.toggleReaction(message.id, emoji)
                                },
                                onRequestMenu = { viewModel.showMessageMenu(message.id) },
                                onReply = { viewModel.replyTo(message.id) },
                                onOpenMedia = { onOpenMedia(message.id) },
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
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // Odznak leží NAD seznamem, ne v něm — musí zůstat u spodní hrany i při scrollu.
            NewMessagesBadge(
                count = pendingNewMessages,
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = inputHeight)
                    .padding(16.dp),
            )

            MessageInput(
                replyingTo = state.replyingTo,
                partnerName = state.info?.title,
                flight = state.flight,
                onCancelReply = viewModel::clearReply,
                onSend = viewModel::send,
                // Výška se měří a vrací zpátky do odsazení seznamu. Natvrdo zadaná konstanta
                // by se rozešla s realitou, jakmile se pole zalomí na víc řádků nebo se nad
                // ním objeví karta s citací.
                onHeightChanged = { inputHeight = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * Odznak „dole čekají nové zprávy".
 *
 * Objeví se jen když uživatel čte historii — dole se totiž nescrolluje automaticky, aby ho nová
 * zpráva nevytrhla ze čtení. Odznak je pak jediná informace, že něco přišlo.
 *
 * Nese počet, ne jen šipku: bez čísla by uživatel nevěděl, jestli se vyplatí skočit dolů.
 */
@Composable
private fun NewMessagesBadge(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = count > 0,
        // Vjezd zespoda: odznak přiletí odtamtud, kam po ťuknutí odskočí.
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shadowElevation = 4.dp,
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painterResource(Res.drawable.arrow_downward_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = newMessagesLabel(count),
                    style = MaterialTheme.typography.labelLarge,
                )
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
    lastReadOutboxMessageId: Long,
    /** Let ze vstupního pole, který přistává právě v téhle bublině; jinak `null`. */
    flight: MessageFlight?,
    previous: ChatMessage?,
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
        message.media != null || message.replyToMessageId != null -> 0
        else -> message.text.emojiClusterCount(MAX_LARGE_EMOJI)
    }
    val bare = largeEmoji > 0 ||
        (message.media is MessageMedia.Sticker && message.replyToMessageId == null)

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

    Row(
        modifier = modifier
            .fillMaxWidth()
            // Zprávy od jednoho odesílatele drží u sebe, nová skupina se oddělí. Tvar bublin
            // to už rozlišuje staženým nárožím, mezera tomu jen dává prostor.
            .padding(top = if (continuesGroup) 0.dp else GROUP_SPACING)
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
    ) {
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
                        // V odchozí bublině nese akcent barva popředí; `primary` by splynula
                        // s pozadím bubliny.
                        accent = if (message.isOutgoing) contentColor else MaterialTheme.colorScheme.primary,
                    )
                }

                message.media?.let { media ->
                    MessageAttachment(
                        media = media,
                        messageId = message.id,
                        onOpen = onOpenMedia,
                        onRequestMenu = onRequestMenu,
                    )
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
    onHeightChanged: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val density = LocalDensity.current

    /*
     * Pruh má barvu POZADÍ, jen průsvitnou.
     *
     * Zprávy pod něj zajíždějí (pole není `bottomBar`, viz výše) a bez pruhu by se pod kartou
     * míchaly s textem. Plná barva by zase udělala ostrý předěl přes celou obrazovku. Průsvit
     * dělá závoj: je vidět, že tam obsah je, ale nepřebíjí psaní.
     */
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = INPUT_SCRIM_ALPHA),
        modifier = modifier.onSizeChanged {
            onHeightChanged(with(density) { it.height.toDp() })
        },
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
                replyingTo?.let { target ->
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
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Zpráva") },
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
                        onSend(draft)
                        draft = ""
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

            FilledIconButton(
                onClick = {
                    onSend(draft)
                    draft = ""
                },
                enabled = draft.isNotBlank(),
            ) {
                Icon(painterResource(Res.drawable.send_24dp), contentDescription = "Odeslat")
            }
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
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
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
 * Odroluje tak, aby položka seděla u HORNÍ hrany a zbytek se četl směrem dolů.
 *
 * `scrollToItem` v obráceném seznamu posadí položku ke spodní hraně, protože tam je jeho začátek.
 * U skoku na nepřečtené je to přesně naopak, než je potřeba: první nepřečtená má být nahoře
 * a nepřečtené pod ní. Dorovnání se počítá až ze **změřeného** layoutu, protože výška bubliny
 * se zprávu od zprávy liší.
 */
private suspend fun LazyListState.scrollUnderTop(index: Int) {
    scrollToItem(index)

    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    // Offset roste od začátku seznamu, tedy v obráceném rozvržení odspodu nahoru.
    val desired = info.viewportSize.height - item.size
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

/** Strop výšky fotky v bublině. Svislé snímky obrazovky by jinak zabraly celou obrazovku. */
private val MAX_PHOTO_HEIGHT = 320.dp

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
            }
        }

        is MessageMedia.Attachment -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            media.thumbnailFileId?.let { thumbnail ->
                RemoteImage(
                    fileId = thumbnail,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(shape),
                )
            }
            Text(
                text = media.label,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        is MessageMedia.Sticker -> StickerImage(media)
    }
}

/**
 * Samolepka.
 *
 * Kreslí se **bez pozadí i bez zaoblení** — samolepka má vlastní tvar s průhledností a rámeček
 * kolem ní by z ní udělal obrázek. Když se nedá vykreslit ani ona, ani její náhled, zbývá emoji,
 * které k ní Telegram vede; velké, ať je poznat, že to je celý obsah zprávy, a ne popisek.
 */
@Composable
private fun StickerImage(sticker: MessageMedia.Sticker) {
    val fileId = sticker.fileId ?: sticker.thumbnailFileId

    if (fileId == null) {
        Text(
            text = sticker.emoji.ifBlank { "Samolepka" },
            style = MaterialTheme.typography.displayMedium,
        )
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

    RemoteImage(
        fileId = fileId,
        contentDescription = sticker.emoji.ifBlank { "Samolepka" },
        contentScale = ContentScale.Fit,
        // Samolepka je průhledná; podložka pod ní by z ní udělala obrázek v rámečku.
        background = Color.Transparent,
        modifier = Modifier.size(width = width, height = height),
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
