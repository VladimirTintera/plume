package eu.tintera.plume.features.conversation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import eu.tintera.plume.core.audio.VoicePlayback
import eu.tintera.plume.core.audio.VoiceRecording
import eu.tintera.plume.core.audio.rememberRecordPermission
import eu.tintera.plume.core.settings.ExpressionMode
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.Presence
import eu.tintera.plume.core.telegram.SponsoredMessage
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.asDayLabel
import eu.tintera.plume.core.ui.asRelativeTime
import eu.tintera.plume.core.ui.chatAvatarKey
import eu.tintera.plume.core.ui.chatTitleKey
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.core.ui.rememberAttachmentPicker
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.sharedFlight
import eu.tintera.plume.core.ui.sharedNavElement
import eu.tintera.plume.core.ui.sharedNavText
import eu.tintera.plume.features.conversation.items.BUBBLE_CORNER
import eu.tintera.plume.features.conversation.items.CenteredNote
import eu.tintera.plume.features.conversation.items.ConversationItemScope
import eu.tintera.plume.features.conversation.items.DayDividerItem
import eu.tintera.plume.features.conversation.items.MESSAGE_SPACING
import eu.tintera.plume.features.conversation.items.MenuAnchor
import eu.tintera.plume.features.conversation.items.MessageAction
import eu.tintera.plume.features.conversation.items.MessageItem
import eu.tintera.plume.features.conversation.items.QuotedMessage
import eu.tintera.plume.features.conversation.items.SPONSORED_KEY_PREFIX
import eu.tintera.plume.features.conversation.items.TIME_COLUMN_WIDTH
import eu.tintera.plume.features.conversation.items.UnreadDividerItem
import eu.tintera.plume.features.conversation.items.conversationItems
import eu.tintera.plume.features.conversation.items.describe
import eu.tintera.plume.features.conversation.items.label
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.arrow_downward_24dp
import eu.tintera.plume.resources.attach_file_24dp
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.conversation_attach
import eu.tintera.plume.resources.conversation_attach_file
import eu.tintera.plume.resources.conversation_attach_media
import eu.tintera.plume.resources.conversation_cancel_edit
import eu.tintera.plume.resources.conversation_cancel_reply
import eu.tintera.plume.resources.conversation_collapse
import eu.tintera.plume.resources.conversation_editing
import eu.tintera.plume.resources.conversation_empty
import eu.tintera.plume.resources.conversation_fallback_title
import eu.tintera.plume.resources.conversation_input_caption
import eu.tintera.plume.resources.conversation_input_message
import eu.tintera.plume.resources.conversation_microphone_permission
import eu.tintera.plume.resources.conversation_new_messages
import eu.tintera.plume.resources.conversation_open_emoji
import eu.tintera.plume.resources.conversation_open_stickers
import eu.tintera.plume.resources.conversation_pick_chat
import eu.tintera.plume.resources.conversation_record_voice
import eu.tintera.plume.resources.conversation_save_edit
import eu.tintera.plume.resources.conversation_scroll_to_end
import eu.tintera.plume.resources.conversation_send
import eu.tintera.plume.resources.conversation_send_voice
import eu.tintera.plume.resources.conversation_show_all_pinned
import eu.tintera.plume.resources.conversation_undo
import eu.tintera.plume.resources.conversation_unpinned
import eu.tintera.plume.resources.description_24dp
import eu.tintera.plume.resources.edit_24dp
import eu.tintera.plume.resources.menu_unpin
import eu.tintera.plume.resources.mic_24dp
import eu.tintera.plume.resources.mood_24dp
import eu.tintera.plume.resources.perm_media_24dp
import eu.tintera.plume.resources.presence_last_month
import eu.tintera.plume.resources.presence_last_week
import eu.tintera.plume.resources.presence_offline
import eu.tintera.plume.resources.presence_online
import eu.tintera.plume.resources.presence_recently
import eu.tintera.plume.resources.push_pin_filled_24dp
import eu.tintera.plume.resources.send_24dp
import eu.tintera.plume.resources.sticky_note_2_24dp
import kotlin.time.Clock
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    onBack: () -> Unit,
    onOpenMedia: (messageId: Long) -> Unit,
    onForward: (messageIds: List<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stateHolder = viewModel.state.collectAsStateWithLifecycle()
    val state by stateHolder
    // Čte se až v bublině hlasovky, takže změna pozice nepřekresluje celou obrazovku.
    val voicePlayback = viewModel.voicePlayback.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    // Nahrávání se mění po 50 ms; obrazovka čte jen jestli běží, čas a úrovně až pruh nahrávání.
    val voiceRecordingState = viewModel.voiceRecording.collectAsStateWithLifecycle()
    val isRecording by remember { derivedStateOf { voiceRecordingState.value != null } }
    val recordPermission = rememberRecordPermission()

    // Výběr cílového chatu je vlastní obrazovka, takže požadavek z nabídky se předá navigaci.
    val forwardRequest = state.forwardRequest
    LaunchedEffect(forwardRequest) {
        if (forwardRequest != null) {
            onForward(forwardRequest)
            viewModel.consumeForwardRequest()
        }
    }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var stickerPickerOpen by rememberSaveable { mutableStateOf(false) }

    // Snackbar o odepnutí se skládá v efektu, kde `stringResource` volat nejde — text se proto
    // přečte tady v kompozici.
    val unpinnedMessage = stringResource(Res.string.conversation_unpinned)
    val undoLabel = stringResource(Res.string.conversation_undo)

    // Vybrané soubory jsou už zkopírované v cache aplikace; čekají nad polem na popisek a odeslání.
    val attachmentPicker = rememberAttachmentPicker(
        onPicked = viewModel::addAttachments,
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

    /*
     * Odkrývání časů tahem doleva.
     *
     * Posouvá se CELÁ plocha zpráv, ne jedna bublina: čas u jedné zprávy sám o sobě neřekne,
     * kdy přišly ty okolo, a u jediné bubliny by to navíc vypadalo jako druhá polovina swipu
     * na odpověď. Stav je proto tady, ne v položce, a čte se až při kreslení vrstvy — jinak by
     * každý snímek gesta překreslil celou konverzaci.
     */
    val timeReveal = remember { Animatable(0f) }
    val revealMax = with(LocalDensity.current) { TIME_COLUMN_WIDTH.toPx() }
    val revealSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    // Chyby se dřív zahazovaly: `state.error` se vykreslovalo jen u prázdné konverzace, takže
    // neúspěšná reakce nebo odeslání zmizely bez jediné stopy.
    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(getString(message.resource, *message.args.toTypedArray()))
        viewModel.dismissError()
    }

    // Snackbar běží v `snackbarScope`, ne v těle efektu. Vynulování `unpinned` změní klíč efektu,
    // ten se restartuje a zrušil by i snackbar, který se právě ukazuje — takže se neukázal nikdy.
    val snackbarScope = rememberCoroutineScope()

    // Stejná past jako u `unpinned` níž: vynulování mění klíč efektu, takže snackbar běží mimo něj.
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        viewModel.consumeNotice()
        val text = getString(notice.resource, *notice.args.toTypedArray())
        snackbarScope.launch { snackbarHostState.showSnackbar(text, duration = SnackbarDuration.Short) }
    }

    LaunchedEffect(state.unpinned) {
        val unpinned = state.unpinned ?: return@LaunchedEffect
        // Vynulovat hned, ne až po zavření snackbaru — jinak by se po otočení ukázal znovu.
        viewModel.consumeUnpinned()
        snackbarScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = unpinnedMessage,
                actionLabel = undoLabel,
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

    /** Kde byla zpráva, nad kterou se otevírá nabídka — hlásí ji zpráva při dlouhém stisku. */
    var menuAnchor by remember { mutableStateOf(MenuAnchor(bounds = { null }, maxWidth = { null })) }

    /** Klíč zprávy, kterou právě kreslí nabídka; v seznamu po ní zůstane prázdné místo. */
    val menuKey = remember { mutableStateOf<Long?>(null) }

    /** Horní hrana vstupního pole v okně; nabídka nad zprávou pod ni nesmí. */
    val contentBottom = remember { mutableStateOf<Float?>(null) }

    /** Horní hrana seznamu (spodek hlavičky) v okně; nabídka nad zprávou nad ni nesmí. */
    val contentTop = remember { mutableStateOf<Float?>(null) }

    val currentOnOpenMedia by rememberUpdatedState(onOpenMedia)
    val itemScope = remember(viewModel) {
        ScreenItemScope(
            viewModel = viewModel,
            state = stateHolder,
            voicePlayback = voicePlayback,
            contentTop = contentTop,
            onMenuAnchor = { menuAnchor = it },
            menuKey = menuKey,
            timeReveal = { timeReveal.value },
            onOpenMedia = { currentOnOpenMedia(it) },
            onOpenStickerSet = { openStickerSetId = it },
        )
    }

    /*
     * Bloky seznamu (viz [ConversationItem]). Všechno, co vyžaduje znát sousedy — předěly,
     * seskupení bublin, profilovky, odznak přečtení, citace — spočítá builder jednou na změnu
     * vstupů, ne každá položka zvlášť.
     *
     * Klíče jsou jen ta pole stavu, ze kterých builder čte. Celý `state` by seznam přestavoval
     * i při každém kroku průběhu stahování.
     */
    val items = remember(
        state.messages,
        state.itemKeys,
        state.sponsored,
        state.loading,
        state.info,
        state.unreadBoundaryId,
        state.readReceiptFor,
        state.lastReadOutboxMessageId,
        state.quotes,
        state.highlightedMessageId,
        state.flight,
        state.messageMenu,
    ) { conversationItems(state) }

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
        // Hledá se BLOK, ne zpráva: fotka z alba je v seznamu součástí jedné položky.
        var index = items.indexOfFirst { it.containsMessage(target.messageId) }
        // Nahoru se posazuje i předěl nad zprávou — u první nepřečtené je to právě to, co má
        // uživatel vidět. Předěl je v obráceném seznamu ZA zprávou.
        if (index >= 0 && target.anchor == ScrollAnchor.Top) {
            while (items.getOrNull(index + 1).let { it is UnreadDividerItem || it is DayDividerItem }) index++
        }
        if (index >= 0) listState.scrollToAnchor(index, target.anchor)
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

    // Nabídka nad zprávou je vrstva NAD Scaffoldem (viz `MessageMenuLayer`), proto obal.
    // Scaffold je zároveň to, co její závoj rozostří (`hazeSource`).
    val hazeState = rememberHazeState()
    Box(modifier) {
    Scaffold(
        modifier = Modifier.fillMaxSize().hazeSource(hazeState),
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
                                text = state.info?.title ?: stringResource(Res.string.conversation_fallback_title),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                // Klíč se dá jen když chat známe; než dorazí `info`, není s čím
                                // párovat.
                                modifier = state.info
                                    ?.let { Modifier.sharedNavText(chatTitleKey(it.id)) }
                                    ?: Modifier,
                            )

                            /*
                             * Podtitul: stav spojení, jinak činnost, jinak dostupnost.
                             *
                             * Nepřipojený klient je nade vším: dokud spojení nestojí, je „píše…"
                             * i „naposledy viděn" jen stará hodnota, kterou nemá co obnovit —
                             * ukázat ji místo vysvětlení, proč nic nechodí, by bylo zavádějící.
                             * Mezi zbylými dvěma má přednost „píše…": je to novější a pro
                             * čekajícího zajímavější informace, a obojí se na řádek nevejde.
                             *
                             * Skupina dostupnost nemá, takže tam se podtitul objeví jen při
                             * činnosti; výšku hlavičky proto animuje `AnimatedContent`, jinak by
                             * název poskočil.
                             */
                            val subtitle = connection.label()?.let { HeaderSubtitle(stringResource(it), accent = false) }
                                ?: state.activity?.let { HeaderSubtitle(stringResource(it.label()), accent = true) }
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
                        Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
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
            // Horní hrana pole je spodní mez pro nabídku nad zprávou (viz `contentBottom`).
            Box(Modifier.onGloballyPositioned { contentBottom.value = it.boundsInWindow().top }) {
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
                onPickMedia = attachmentPicker::pickMedia,
                isRecording = isRecording,
                recording = { voiceRecordingState.value },
                onStartRecording = {
                    recordPermission.request { granted ->
                        if (granted) {
                            viewModel.startRecording()
                        } else {
                            viewModel.reportError(UiText(Res.string.conversation_microphone_permission))
                        }
                    }
                },
                onCancelRecording = viewModel::cancelRecording,
                onSendRecording = viewModel::sendRecording,
                onPickDocument = attachmentPicker::pickDocument,
                attachments = state.attachments,
                onRemoveAttachment = viewModel::removeAttachment,
                expressionMode = expressionMode,
                draft = draft,
                onDraftValueChange = { draft = it },
            )
            }
        },
        // Tlačítko dolů ve FAB slotu: Scaffold ho drží nad polem a snackbar pak nad ním.
        // Uprostřed, ne vpravo: vpravo by viselo přímo nad tlačítkem odeslání a mikrofonu
        // a ťuknutí o kousek vedle by místo skoku dolů spustilo nahrávání.
        floatingActionButtonPosition = FabPosition.Center,
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

        state.messageInfo?.let { sheet ->
            MessageInfoSheet(
                sheet = sheet,
                onCopyLink = viewModel::copyMessageLink,
                onDismiss = viewModel::dismissMessageInfo,
            )
        }

        state.reactionDetails?.let { details ->
            ReactionDetailsSheet(details = details, onDismiss = viewModel::dismissReactionDetails)
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
                )
                .onGloballyPositioned { contentTop.value = it.boundsInWindow().top },
        ) {
            when {
                state.messages.isEmpty() && state.loading ->
                    LoadingIndicator(Modifier.align(Alignment.Center))

                state.messages.isEmpty() -> Text(
                    text = state.error?.resolve() ?: stringResource(Res.string.conversation_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )

                /*
                 * Gesto patří SEZNAMU, ne položce.
                 *
                 * Bublina si bere jen tah doprava (odpověď) a doleva nic nespotřebuje, takže
                 * gesto propadne sem. Svislý scroll má přednost: seznam je potomek, dostane
                 * události dřív a jeho spotřebování tenhle detektor zruší — a detektor se
                 * rozhoduje až po delším a výrazně vodorovném pohybu (`timeRevealDecision`).
                 */
                else -> Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(revealMax) {
                            detectTimeReveal(
                                onEnd = { scope.launch { timeReveal.animateTo(0f, revealSpec) } },
                            ) { delta ->
                                scope.launch {
                                    // Jen doleva a jen po šířku sloupce s časy; dál už není co
                                    // odkrývat a gumové tahání by jen mátlo.
                                    timeReveal.snapTo((timeReveal.value - delta).coerceIn(0f, revealMax))
                                }
                            }
                        },
                ) {
                  LazyColumn(
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
                    items(items, key = { it.key }, contentType = { it.contentType }) { item ->
                        with(item) { Content(itemScope) }
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

    MessageMenuLayer(
        menu = state.messageMenu,
        item = state.messageMenu?.let { menu ->
            items.firstOrNull { it is MessageItem && it.frame.message.id == menu.messageId } as? MessageItem
        },
        anchor = menuAnchor,
        contentTop = contentTop.value,
        contentBottom = contentBottom.value,
        itemScope = itemScope,
        hazeState = hazeState,
        onShownChange = { menuKey.value = it },
    )
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
                    contentDescription = if (count > 0) null else stringResource(Res.string.conversation_scroll_to_end),
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

/** Skloňování řeší `plurals` v prostředcích — čeština má tři tvary, angličtina dva. */
@Composable
private fun newMessagesLabel(count: Int): String =
    pluralStringResource(Res.plurals.conversation_new_messages, count, count)

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
    onPickMedia: () -> Unit,
    onPickDocument: () -> Unit,
    isRecording: Boolean,
    /** Čte se až v pruhu nahrávání — čas a úrovně se mění dvacetkrát za vteřinu. */
    recording: () -> VoiceRecording?,
    onStartRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onSendRecording: () -> Unit,
    /** Přílohy čekající na odeslání; text pole je pak jejich popisek. */
    attachments: List<OutgoingAttachment>,
    onRemoveAttachment: (Int) -> Unit,
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

    // Úprava zprávy přílohy schová — odeslání by je stejně neposlalo.
    val withAttachments = attachments.isNotEmpty() && editing == null
    val keyboard = LocalSoftwareKeyboardController.current

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
                } else {
                  StagedAttachmentsStrip(attachments = attachments, onRemove = onRemoveAttachment)
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
                                contentDescription = stringResource(Res.string.conversation_cancel_reply),
                            )
                        }
                    }
                  }
                }

                // Při nahrávání místo pole pruh s časem; citace nad ním zůstává, hlasovka na ni odpoví.
                if (isRecording) {
                    RecordingBar(recording = recording, onCancel = onCancelRecording)
                } else Box {
            TextField(
                value = draft,
                onValueChange = {
                    val changed = it.text != draft.text
                    onDraftValueChange(it)
                    // Pohyb kurzoru nebo výběr taky volá `onValueChange`; psaní to není.
                    if (changed) onDraftChanged(it.text)
                },
                modifier = Modifier.fillMaxWidth(),
                // S přílohami je text jejich popisek.
                placeholder = { Text(stringResource(if (withAttachments) Res.string.conversation_input_caption else Res.string.conversation_input_message)) },
                // Přílohy jen při psaní — k úpravě textové zprávy příloha nepatří.
                leadingIcon = if (editing == null) {
                    { AttachButton(onPickMedia = onPickMedia, onPickDocument = onPickDocument) }
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
                            Icon(painterResource(Res.drawable.mood_24dp), contentDescription = stringResource(Res.string.conversation_open_emoji))
                        } else {
                            Icon(painterResource(Res.drawable.sticky_note_2_24dp), contentDescription = stringResource(Res.string.conversation_open_stickers))
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
                keyboardOptions = KeyboardOptions(
                    // Zpráva jsou věty, ne jedno slovo: klávesnice má po tečce nabídnout velké
                    // písmeno sama. Výchozí `None` nutí uživatele přepínat shift ručně.
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
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
                    // Odesílaná zpráva je vždycky nejnovější, takže pod ní nic nenavazuje
                    // a všechna nároží jsou zaoblená. Dřív tu bylo spodní stažené, protože takové
                    // bylo i v bublině; se seskupením podle obou sousedů to platit přestalo.
                    shape = RoundedCornerShape(BUBBLE_CORNER),
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
             * Tvar se při stisku mění podle M3 Expressive (`shapes`), kruh → zaoblený čtverec;
             * při změně funkce projde „sušenkou" (`SendButtonMorphShape`).
             */
            /*
             * Prázdné pole bez příloh = mikrofon (jako v Telegramu i WhatsAppu): tlačítko, které by
             * jinak bylo neaktivní, dostane smysl. Ťuknutí nahrávání spustí, odeslání ho ukončí.
             * Při úpravě zprávy mikrofon není — hlasovka úprava není.
             */
            val showMic = !isRecording && editing == null && draft.text.isBlank() && !withAttachments
            val kind = when {
                editing != null -> SendButtonKind.Save
                showMic -> SendButtonKind.Mic
                else -> SendButtonKind.Send
            }

            /*
             * Změna funkce tlačítka se animuje: tvar přeletí z kruhu přes „sušenku" zpátky do kruhu
             * a ikona se v půlce vymění. Tlačítko se mění samo od sebe (začne se psát, pole se
             * vyprázdní) a skoková výměna ikony se dala přehlédnout — a ťuknout pak na mikrofon
             * místo odeslání.
             */
            val morph = remember { Animatable(0f) }
            var shownKind by remember { mutableStateOf(kind) }
            val morphIn = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
            val morphOut = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
            LaunchedEffect(kind) {
                if (kind == shownKind) return@LaunchedEffect
                shownKind = kind
                morph.animateTo(1f, morphIn)
                morph.animateTo(0f, morphOut)
            }
            // Pružina přestřelí pod nulu; záporný průběh by kruh zdeformoval.
            val morphProgress = morph.value.coerceIn(0f, 1f)

            FilledIconButton(
                onClick = {
                    when {
                        isRecording -> onSendRecording()
                        showMic -> onStartRecording()
                        else -> {
                            onSend(draft.text)
                            onDraftValueChange(TextFieldValue())
                        }
                    }
                },
                // Přílohy jdou poslat i bez popisku.
                enabled = isRecording || showMic || draft.text.isNotBlank() || withAttachments,
                // Mimo přechod zůstává stisk podle M3 Expressive (kruh → zaoblený čtverec).
                shapes = if (morphProgress > 0f) {
                    val shape = SendButtonMorphShape(morphProgress)
                    IconButtonShapes(shape = shape, pressedShape = shape)
                } else {
                    IconButtonDefaults.shapes(
                        shape = IconButtonDefaults.mediumRoundShape,
                        pressedShape = IconButtonDefaults.mediumPressedShape,
                    )
                },
                modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
            ) {
                val iconScale = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
                val iconFade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                AnimatedContent(
                    targetState = kind,
                    transitionSpec = {
                        (scaleIn(iconScale, initialScale = 0.4f) + fadeIn(iconFade))
                            .togetherWith(scaleOut(iconScale, targetScale = 0.4f) + fadeOut(iconFade))
                    },
                    label = "send-button-icon",
                ) { shown ->
                    // Při úpravě fajfka: nic se neposílá, jen potvrzuje změna.
                    Icon(
                        painterResource(
                            when (shown) {
                                SendButtonKind.Save -> Res.drawable.check_24dp
                                SendButtonKind.Mic -> Res.drawable.mic_24dp
                                SendButtonKind.Send -> Res.drawable.send_24dp
                            },
                        ),
                        contentDescription = stringResource(
                            when {
                                shown == SendButtonKind.Save -> Res.string.conversation_save_edit
                                shown == SendButtonKind.Mic -> Res.string.conversation_record_voice
                                isRecording -> Res.string.conversation_send_voice
                                else -> Res.string.conversation_send
                            },
                        ),
                        modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                    )
                }
            }
        }
    }
}

/** Co tlačítko u vstupního pole právě dělá. Změna mezi nimi se animuje. */
private enum class SendButtonKind { Mic, Send, Save }

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
                    Text(
                        if (expanded) {
                            stringResource(Res.string.conversation_collapse)
                        } else {
                            pluralStringResource(Res.plurals.conversation_show_all_pinned, messages.size, messages.size)
                        },
                    )
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
                    contentDescription = stringResource(Res.string.menu_unpin),
                    modifier = Modifier.size(18.dp),
                )
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
private fun AttachButton(onPickMedia: () -> Unit, onPickDocument: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(Res.drawable.attach_file_24dp), contentDescription = stringResource(Res.string.conversation_attach))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.conversation_attach_media)) },
                leadingIcon = { Icon(painterResource(Res.drawable.perm_media_24dp), contentDescription = null) },
                onClick = {
                    open = false
                    keyboard?.hide()
                    onPickMedia()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.conversation_attach_file)) },
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
                text = stringResource(Res.string.conversation_editing),
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
            Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.conversation_cancel_edit))
        }
    }
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
                stringResource(Res.string.conversation_pick_chat),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
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
 * [ConversationItemScope] nad ViewModelem obrazovky.
 *
 * Stav se čte přes [State] až uvnitř volání, takže se k jeho změnám přihlásí jen to UI, které
 * hodnotu opravdu kreslí (průběh stahování, pozice hlasovky), ne celá položka.
 */
private class ScreenItemScope(
    private val viewModel: ConversationViewModel,
    private val state: State<ConversationState>,
    private val voicePlayback: State<VoicePlayback?>,
    private val contentTop: State<Float?>,
    private val onMenuAnchor: (MenuAnchor) -> Unit,
    private val menuKey: State<Long?>,
    override val timeReveal: () -> Float,
    private val onOpenMedia: (messageId: Long) -> Unit,
    private val onOpenStickerSet: (setId: Long) -> Unit,
) : ConversationItemScope {
    override fun voicePlayback(messageId: Long): VoicePlayback? =
        voicePlayback.value?.takeIf { it.key.messageId == messageId }

    override fun documentProgress(fileId: Int): Float? = state.value.documentDownloads[fileId]

    override fun contentTop(): Float? = contentTop.value

    override fun onMenuAnchor(anchor: MenuAnchor) = onMenuAnchor.invoke(anchor)

    override fun isInMenu(key: Long): Boolean = menuKey.value == key

    /** Kdy se konverzace otevřela; starší zprávy jsou historie, ne příchod. */
    private val openedAt = Clock.System.now().epochSeconds
    private val arrived = HashSet<Long>()

    override fun claimArrival(key: Long, timestamp: Int): Boolean =
        timestamp >= openedAt - ARRIVAL_TOLERANCE_SECONDS && arrived.add(key)

    /** Navigace a sheet nad obrazovkou jsou věc obrazovky; všechno ostatní jde do ViewModelu. */
    override fun onAction(messageId: Long, action: MessageAction) = when (action) {
        MessageAction.OpenMedia -> onOpenMedia(messageId)
        is MessageAction.OpenStickerSet -> onOpenStickerSet(action.setId)
        else -> viewModel.onAction(messageId, action)
    }

    override fun openSponsored(sponsored: SponsoredMessage) = viewModel.onSponsoredClick(sponsored)
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

/** Kolik připnutých zpráv je vidět bez rozbalení. */
private const val PINNED_COLLAPSED = 3

/** Řádek připnuté zprávy. Zároveň minimální dotyková plocha. */
private val PINNED_ROW_HEIGHT = 44.dp

/** Strop výšky rozbaleného seznamu připnutých; nad ním se listuje. Konverzace musí zůstat vidět. */
private val PINNED_EXPANDED_MAX_HEIGHT = 320.dp

/** O kolik položek výš od spodku se objeví tlačítko „dolů". */
private const val SCROLL_DOWN_THRESHOLD_ITEMS = 3

/** Tlačítko „dolů" bez počtu je kruh téhle velikosti — zároveň minimální dotyková plocha. */
private val SCROLL_DOWN_BUTTON_SIZE = 48.dp

private const val READ_DEBOUNCE_MS = 400L

/** Prefix klíče reklamní položky; podle něj se v seznamu pozná pro hlášení zhlédnutí. */

/** Jak dlouho musí být reklama v klidu na obrazovce, než se zhlédnutí nahlásí. */
private const val SPONSORED_VIEW_DEBOUNCE_MS = 1000L

/**
 * Průsvit pruhu pod vstupním polem.
 *
 * Dost na to, aby text pod ním nerušil, ale ne plná barva — ta by udělala ostrý předěl přes
 * celou obrazovku, což je právě to, co se odstraňovalo.
 */
private const val INPUT_SCRIM_ALPHA = 0.93f

/** Řádek pod názvem chatu. [accent] = živá informace (online, píše…), jinak tlumeně. */
private data class HeaderSubtitle(val text: String, val accent: Boolean)

/**
 * „Naposledy viděn" do hlavičky.
 *
 * Telegram dostupnost **záměrně rozmazává**: kdo si to v soukromí vypne, je vidět jen jako
 * „naposledy nedávno" a přesný čas nedostaneme. Proto se nikde nedopočítává nic přesnějšího,
 * než co server dal — vymyslet minutu, kterou neznáme, by bylo horší než neurčitost.
 */
@Composable
private fun Presence.describe(): String = when (this) {
    Presence.Online -> stringResource(Res.string.presence_online)
    Presence.Recently -> stringResource(Res.string.presence_recently)
    Presence.LastWeek -> stringResource(Res.string.presence_last_week)
    Presence.LastMonth -> stringResource(Res.string.presence_last_month)
    is Presence.Offline -> stringResource(Res.string.presence_offline, wasOnline.asRelativeTime())
}

/** Hodiny serveru a telefonu se můžou o chvilku lišit; o tolik starší zpráva je ještě příchod. */
private const val ARRIVAL_TOLERANCE_SECONDS = 5
