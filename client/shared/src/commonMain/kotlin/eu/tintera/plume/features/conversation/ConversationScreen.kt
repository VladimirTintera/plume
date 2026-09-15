package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.Presence
import eu.tintera.plume.core.telegram.SendState
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.asRelativeTime
import eu.tintera.plume.core.ui.chatAvatarKey
import eu.tintera.plume.core.ui.emojiClusterCount
import eu.tintera.plume.core.ui.chatTitleKey
import eu.tintera.plume.core.ui.sharedNavElement
import eu.tintera.plume.core.ui.sharedNavText
import eu.tintera.plume.core.ui.sharedFlight
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.arrow_downward_24dp
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.done_all_24dp
import eu.tintera.plume.resources.error_24dp
import eu.tintera.plume.resources.schedule_24dp
import eu.tintera.plume.resources.send_24dp
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Instant

@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    onBack: () -> Unit,
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

    val newestMessageId = state.messages.firstOrNull()?.id

    LaunchedEffect(newestMessageId) {
        val newest = newestMessageId ?: return@LaunchedEffect
        val previous = lastSeenMessageId
        lastSeenMessageId = newest

        // První naplnění seznamu není „nová zpráva", jen se zobrazila historie.
        if (previous == null) return@LaunchedEffect
        if (newest <= previous) return@LaunchedEffect

        // Id zpráv v chatu rostou, takže „nové" jsou ty nad posledním viděným.
        val arrived = state.messages.takeWhile { it.id > previous }

        when {
            // Vlastní odeslaná zpráva: odrolovat vždycky. Bez toho by odeslání z pohledu
            // uživatele vypadalo, že se nic nestalo — bublina by přistála mimo obrazovku.
            arrived.any { it.isOutgoing } -> {
                pendingNewMessages = 0
                listState.animateScrollToItem(0)
            }

            atBottom -> {
                pendingNewMessages = 0
                listState.animateScrollToItem(0)
            }

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

    // Přečtení se hlásí až když se scrollování uklidní — jinak by při rychlém průletu odešla
    // jedna žádost na každý snímek.
    LaunchedEffect(listState, state.messages) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }
            .debounce(READ_DEBOUNCE_MS)
            .collect { indices ->
                val visible = indices.mapNotNull { state.messages.getOrNull(it) }
                viewModel.markRead(visible.filterNot { it.isOutgoing }.map { it.id })
            }
    }

    Scaffold(
        modifier = modifier,
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
        bottomBar = {
            MessageInput(
                replyingTo = state.replyingTo,
                partnerName = state.info?.title,

                flight = state.flight,
                onCancelReply = viewModel::clearReply,
                onSend = viewModel::send,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        state.reactionPicker?.let { picker ->
            ReactionPickerSheet(
                picker = picker,
                onPick = { emoji -> viewModel.toggleReaction(picker.messageId, emoji) },
                onReply = { viewModel.replyTo(picker.messageId) },
                onDismiss = viewModel::hideReactionPicker,
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
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
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

                    items(state.messages, key = { it.id }) { message ->
                        val index = state.messages.indexOf(message)

                        // Systémové oznámení nemá odesílatele, takže nemá ani stranu — kreslí se
                        // na střed a bez bubliny. Reakce, citace ani stav doručení k němu nepatří.
                        if (message.isService) {
                            ServiceMessage(message, Modifier.animateItem())
                            return@items
                        }

                        MessageBubble(
                            message = message,
                            quoted = message.replyToMessageId?.let { id ->
                                state.messages.firstOrNull { it.id == id } ?: state.quotes[id]
                            },
                            // V soukromém chatu se jméno odesílatele u zpráv nevyplňuje (dává
                            // smysl jen ve skupinách), takže citace by neměla koho uvést.
                            partnerName = state.info?.title,
                            // Seznam jde od nejnovější, takže „předchozí zpráva v čase"
                            // je ta s VYŠŠÍM indexem.
                            previous = state.messages.getOrNull(index + 1),
                            lastReadOutboxMessageId = state.lastReadOutboxMessageId,

                            flight = state.flight,
                            onToggleReaction = { emoji ->
                                viewModel.toggleReaction(message.id, emoji)
                            },
                            onRequestReaction = { viewModel.showReactionPicker(message.id) },
                            modifier = Modifier.animateItem(),
                        )
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

            // Odznak leží NAD seznamem, ne v něm — musí zůstat u spodní hrany i při scrollu.
            NewMessagesBadge(
                count = pendingNewMessages,
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
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
    flight: MessageFlight?,
    previous: ChatMessage?,
    onToggleReaction: (String) -> Unit,
    onRequestReaction: () -> Unit,
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

    // Odchozí = primaryContainer, příchozí = surfaceContainerHigh. Vrstvení řeší surface role,
    // ne elevation — viz konvence v CLAUDE.md.
    val containerColor = when {
        bare -> Color.Transparent
        message.isOutgoing -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when {
        // Bez pozadí se kreslí na plochu obrazovky, takže barva popředí musí patřit k NÍ,
        // ne k barvě bubliny, která tam není.
        bare -> MaterialTheme.colorScheme.onSurface
        message.isOutgoing -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    val corner = BUBBLE_CORNER
    val tight = BUBBLE_TAIL
    val shape = if (message.isOutgoing) {
        RoundedCornerShape(corner, if (continuesGroup) tight else corner, tight, corner)
    } else {
        RoundedCornerShape(if (continuesGroup) tight else corner, corner, corner, tight)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            // Zprávy od jednoho odesílatele drží u sebe, nová skupina se oddělí. Tvar bublin
            // to už rozlišuje staženým nárožím, mezera tomu jen dává prostor.
            .padding(top = if (continuesGroup) 0.dp else GROUP_SPACING),
        horizontalArrangement = if (message.isOutgoing) Arrangement.End else Arrangement.Start,
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
                    flight?.takeIf { it.messageId == message.id }
                        ?.let { Modifier.sharedFlight(it.sharedKey, visible = true) }
                        ?: Modifier,
                )
                // Dlouhý stisk je jediné gesto na bublině — krátké ťuknutí si šetříme
                // na otevírání médií, až přibude.
                .combinedClickable(onLongClick = onRequestReaction, onClick = {}),
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

                quoted?.let { QuotedMessage(it, partnerName = partnerName) }

                message.media?.let { media -> MessageAttachment(media) }

                // Prázdný text u zprávy, která má jen fotku — pak se nekreslí nic.
                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        // Míň emoji = větší. Jedno je gesto, tři už jsou věta, takže se
                        // velikost s počtem krotí.
                        style = when (largeEmoji) {
                            0 -> MaterialTheme.typography.bodyLarge
                            1 -> MaterialTheme.typography.displayLarge
                            2 -> MaterialTheme.typography.displayMedium
                            else -> MaterialTheme.typography.displaySmall
                        },
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
    }
}

@Composable
private fun MessageInput(
    replyingTo: ChatMessage?,
    partnerName: String?,
    flight: MessageFlight?,
    onCancelReply: () -> Unit,
    onSend: (String) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }

    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
      Column {
        replyingTo?.let { target ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuotedMessage(target, partnerName = partnerName, modifier = Modifier.weight(1f))
                IconButton(onClick = onCancelReply) {
                    Icon(
                        painterResource(Res.drawable.close_24dp),
                        contentDescription = "Zrušit odpověď",
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.weight(1f)) {
            TextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Zpráva") },
                maxLines = 6,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    // Podtržení u pole uvnitř vlastního chrome ruší; tvar nese zaoblení.
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
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
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(BUBBLE_CORNER, BUBBLE_CORNER, BUBBLE_TAIL, BUBBLE_CORNER),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .sharedFlight(current.sharedKey, visible = current.messageId == null),
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
                .background(MaterialTheme.colorScheme.primary),
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
                color = MaterialTheme.colorScheme.primary,
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
 * Stav doručení odchozí zprávy.
 *
 * Čtyři stavy ve třech ikonách, jak je zvykem: hodiny (odesílá se), fajfka (na serveru),
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
    val (icon, description) = when {
        sendState == SendState.Sending -> Res.drawable.schedule_24dp to "Odesílá se"
        sendState == SendState.Failed -> Res.drawable.error_24dp to "Odeslání selhalo"
        read -> Res.drawable.done_all_24dp to "Přečteno"
        else -> Res.drawable.check_24dp to "Odesláno"
    }

    // Selhání je jediný stav, který uživatel musí zaregistrovat — dostane chybovou barvu.
    val color = if (sendState == SendState.Failed) {
        MaterialTheme.colorScheme.error
    } else {
        tint.copy(alpha = 0.7f)
    }

    // Crossfade, ať přechod hodiny → fajfka → dvojitá fajfka neproblikne.
    Crossfade(targetState = icon, label = "delivery") { current ->
        Icon(
            painter = painterResource(current),
            contentDescription = description,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * Příloha uvnitř bubliny.
 *
 * Fotka si drží **poměr stran z metadat zprávy**, ne z načteného obrázku — díky tomu má bublina
 * správnou výšku od začátku a seznam nepodskočí, až se obrázek dotáhne.
 */
@Composable
private fun MessageAttachment(media: MessageMedia) {
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
                        .clip(shape),
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
 * Na střed, malým písmem a v `surfaceContainer` — má to být poznámka na okraji, ne zpráva.
 * Čas se nevypisuje: u „někdo se připojil" je minuta k ničemu a řádek by jen zdvojnásobil.
 */
@Composable
private fun ServiceMessage(message: ChatMessage, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(BUBBLE_CORNER),
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Text(
                text = message.text,
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
