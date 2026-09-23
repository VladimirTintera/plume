package eu.tintera.plume.features.conversation

import androidx.compose.ui.platform.LocalHapticFeedback
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.hazeBlur
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.ui.avatarShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import eu.tintera.plume.core.telegram.ContentAccess
import eu.tintera.plume.core.telegram.WriteAccess
import eu.tintera.plume.core.ui.confirm
import eu.tintera.plume.core.ui.reject
import eu.tintera.plume.core.ui.tick
import eu.tintera.plume.core.ui.toggle
import eu.tintera.plume.resources.conversation_not_member
import eu.tintera.plume.resources.conversation_write_restricted
import eu.tintera.plume.resources.notifications_24dp
import eu.tintera.plume.resources.notifications_off_24dp
import eu.tintera.plume.resources.profile_mute
import eu.tintera.plume.resources.profile_unmute
import org.jetbrains.compose.resources.StringResource
import eu.tintera.plume.core.telegram.AttachmentMode
import androidx.compose.ui.draw.clip
import eu.tintera.plume.resources.profile_open
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.animation.core.AnimationSpec
import eu.tintera.plume.resources.conversation_search
import eu.tintera.plume.resources.search_24dp
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Button
import eu.tintera.plume.resources.conversation_join
import androidx.compose.foundation.layout.imePadding
import eu.tintera.plume.core.ui.chatTitle
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.BasicTextField
import eu.tintera.plume.core.ui.ClipboardMediaKind
import eu.tintera.plume.core.ui.ClipboardPasteSuggestion
import eu.tintera.plume.core.ui.KeyboardContent
import eu.tintera.plume.core.ui.KeyboardContentReceiver
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import eu.tintera.plume.features.conversation.items.WavyShape
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import eu.tintera.plume.core.files.rememberSaveAs
import eu.tintera.plume.core.ui.animateMorph
import eu.tintera.plume.core.ui.morphSpring
import eu.tintera.plume.core.ui.ShapeMorphs
import eu.tintera.plume.core.ui.MorphShape
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
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
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
import eu.tintera.plume.core.ui.describe
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.Presence
import eu.tintera.plume.core.telegram.SponsoredMessage
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.accentOn
import eu.tintera.plume.core.ui.asDayLabel
import eu.tintera.plume.core.ui.chatAvatarKey
import eu.tintera.plume.core.ui.chatTitleKey
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.core.ui.rememberAttachmentPicker
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.sharedFlight
import eu.tintera.plume.core.ui.sharedNavElement
import eu.tintera.plume.core.ui.sharedNavText
import eu.tintera.plume.features.conversation.items.BUBBLE_CORNER
import eu.tintera.plume.features.conversation.items.FloatingDayBadge
import eu.tintera.plume.features.conversation.items.ConversationItemScope
import eu.tintera.plume.features.conversation.items.DayDividerItem
import eu.tintera.plume.features.conversation.items.MESSAGE_SPACING
import eu.tintera.plume.features.conversation.items.MenuAnchor
import eu.tintera.plume.features.conversation.items.MessageAction
import eu.tintera.plume.features.conversation.items.MessageMenuAction
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
import eu.tintera.plume.resources.clipboard_has_gif
import eu.tintera.plume.resources.clipboard_has_image
import eu.tintera.plume.resources.conversation_editing
import eu.tintera.plume.resources.conversation_editing_caption
import eu.tintera.plume.resources.conversation_open_gifs
import eu.tintera.plume.resources.gif_box_24dp
import eu.tintera.plume.resources.conversation_empty
import eu.tintera.plume.resources.conversation_fallback_title
import eu.tintera.plume.resources.conversation_input_caption
import eu.tintera.plume.resources.conversation_input_message
import eu.tintera.plume.resources.conversation_microphone_permission
import eu.tintera.plume.resources.conversation_new_messages
import eu.tintera.plume.resources.conversation_open_emoji
import eu.tintera.plume.resources.conversation_open_stickers
import eu.tintera.plume.resources.conversation_record_voice
import eu.tintera.plume.resources.conversation_save_edit
import eu.tintera.plume.resources.conversation_scroll_to_end
import eu.tintera.plume.resources.conversation_send
import eu.tintera.plume.resources.conversation_send_voice
import eu.tintera.plume.resources.conversation_undo
import eu.tintera.plume.resources.conversation_unpinned
import eu.tintera.plume.resources.description_24dp
import eu.tintera.plume.resources.edit_24dp
import eu.tintera.plume.resources.mic_24dp
import eu.tintera.plume.resources.mood_24dp
import eu.tintera.plume.resources.perm_media_24dp
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
    /** Zmínka v textu: otevřít chat nad touhle konverzací. */
    /** Otevřít chat nad konverzací (zmínka, odkaz); s [messageId] se skočí na zprávu. */
    onOpenChat: (chatId: Long, messageId: Long?) -> Unit,
    /** Hashtag v textu: hledání zpráv s tagem v seznamu chatů. */
    onSearch: (query: String) -> Unit,
    /** Ťuknutí na hlavičku: profil chatu. */
    onOpenProfile: () -> Unit,
    /** Profil jiného chatu nad konverzací — odesílatel ze skupiny, na jehož tvář se ťuklo. */
    onOpenUserProfile: (chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stateHolder = viewModel.state.collectAsStateWithLifecycle()
    val state by stateHolder
    // Hledané slovo pro bubliny. Odvozené, aby text bublin překreslila jen změna dotazu, ne každá
    // změna stavu konverzace. Dotaz, ke kterému patří výsledky, ne rozepsaný.
    val searchHighlight = remember {
        derivedStateOf { stateHolder.value.search?.resultsQuery?.takeIf { it.isNotBlank() } }
    }
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
    /*
     * Stav seznamu patří jednomu OKNU zpráv (`window`). Když se seznam vymění celý (skok do
     * historie, návrat k přítomnosti), odejdou všechny položky naráz a `animateItem` je nechá
     * doznít na místě — mizející předěl „Dnes" pak visel přes zprávy z roku 2021 a jezdil
     * se scrollem. Nový stav seznamu s novým oknem nemá co animovat pryč.
     */
    val listState = rememberSaveable(state.window, saver = LazyListState.Saver) { LazyListState() }
    // Přechod na zprávu, ke které se nedá dojet (daleko, nebo nenačtená) — viz `JumpTransition`.
    val jumpTransition = remember { JumpTransition() }
    val snackbarHostState = remember { SnackbarHostState() }
    var stickerPickerOpen by rememberSaveable { mutableStateOf(false) }
    var previewAttachmentIndex by rememberSaveable { mutableStateOf<Int?>(null) }

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
    val haptics = LocalHapticFeedback.current

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
        // Chyba po akci uživatele je jediné místo, kde odezva hlásí NEúspěch — snackbar se dá
        // přehlédnout, hlavně u odeslání, které mezitím vypadá jako hotové.
        haptics.reject()
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
    val shouldLoadOlder by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { shouldLoadOlder }.collect { if (it) viewModel.loadOlder() }
    }

    /*
     * Okno v historii (po skoku na starou zprávu) se dotahuje i k PŘÍTOMNOSTI — na začátku
     * seznamu, tedy u spodní hrany. Jestli je okno v historii, ví ViewModel (`reachedEnd`);
     * obrazovka jen hlásí, že je uživatel blízko.
     */
    val shouldLoadNewer by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex < LOAD_MORE_THRESHOLD }
    }
    LaunchedEffect(listState, state.reachedEnd) {
        if (state.reachedEnd) return@LaunchedEffect
        snapshotFlow { shouldLoadNewer }.collect { if (it) viewModel.loadNewer() }
    }

    // „Jsem dole" = vidím nejnovější zprávu. Při `reverseLayout` je to index 0; kontroluje se
    // i offset, aby se za dole nepovažoval stav, kdy je poslední bublina jen z půlky vidět.
    val atBottom by remember(listState) {
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
    val currentOnSearch by rememberUpdatedState(onSearch)
    val currentOnOpenChat by rememberUpdatedState(onOpenChat)

    // Chat ze zmínky nebo odkazu: ViewModel ho najde, navigace je věc obrazovky. Událost se
    // nuluje hned, jinak by se po otočení otevřel znovu.
    LaunchedEffect(state.openChat) {
        val target = state.openChat ?: return@LaunchedEffect
        viewModel.consumeOpenChat()
        currentOnOpenChat(target.chatId, target.messageId)
    }
    val currentOnOpenUserProfile by rememberUpdatedState(onOpenUserProfile)
    LaunchedEffect(state.openProfile) {
        val target = state.openProfile ?: return@LaunchedEffect
        viewModel.consumeOpenProfile()
        currentOnOpenUserProfile(target)
    }

    /*
     * Zpráva, na jejíž tvář se naposledy ťuklo: jen ta nese sdílený prvek do profilu. Přežije
     * i návrat z profilu (a zabití procesu), aby tvář měla kam doletět zpátky.
     */
    val profileFlight = rememberSaveable { mutableStateOf<Long?>(null) }
    val saveAs = rememberSaveAs()
    val currentSaveAs by rememberUpdatedState(saveAs)
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
            onSearch = { currentOnSearch(it) },
            // Výběr umístění je aktivita systému, takže ho má obrazovka; ViewModel jen stáhne.
            onSaveFileAs = { messageId -> currentSaveAs?.let { viewModel.saveDocumentAs(messageId, it) } },
            jumpTransition = jumpTransition,
            searchHighlight = searchHighlight,
            profileFlight = profileFlight,
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
        state.reachedEnd,
        state.loading,
        state.info,
        state.unreadBoundaryId,
        state.readReceiptFor,
        state.lastReadOutboxMessageId,
        state.quotes,
        state.highlightedMessageId,
        state.search?.current,
        state.flight,
        state.messageMenu,
        state.typing,
        // Jen KLÍČE výběru: práva k němu chodí po jednom a seznam bloků se jimi nemění.
        state.selection?.keys,
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
    val topVisibleKey by remember(listState) {
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
    val jumpSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val leaveMoveSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val leaveFadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val gatherSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val restoreSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val leaveDistance = with(LocalDensity.current) { JUMP_LEAVE_DISTANCE.toPx() }
    val gatherDistance = with(LocalDensity.current) { JUMP_GATHER_DISTANCE.toPx() }
    // Seznam se během dojíždění mění (dotahují se stránky), takže se cíl hledá v AKTUÁLNÍM seznamu.
    val currentItems by rememberUpdatedState(items)
    val indexByKey = remember(items) { items.withIndex().associate { (index, item) -> item.key to index } }
    jumpTransition.indexOf = { indexByKey[it] ?: -1 }

    /*
     * Skok na nenačtenou zprávu: seznam začne odjíždět HNED, načítání okna kolem zprávy běží
     * mezitím. Když se zpráva nenajde, seznam se vrátí.
     */
    LaunchedEffect(state.pendingJump) {
        val pending = state.pendingJump
        when {
            state.scrollTarget != null -> Unit
            pending != null -> jumpTransition.leave(
                distance = if (pending.older) leaveDistance else -leaveDistance,
                moveSpec = leaveMoveSpec,
                fadeSpec = leaveFadeSpec,
            )
            else -> jumpTransition.restore(restoreSpec)
        }
    }

    LaunchedEffect(state.scrollTarget) {
        val target = state.scrollTarget ?: return@LaunchedEffect
        // Hledá se BLOK, ne zpráva: fotka z alba je v seznamu součástí jedné položky.
        var index = items.indexOfFirst { it.containsMessage(target.messageId) }
        // Nahoru se posazuje i předěl nad zprávou — u první nepřečtené je to právě to, co má
        // uživatel vidět. Předěl je v obráceném seznamu ZA zprávou.
        if (index >= 0 && target.anchor == ScrollAnchor.Top) {
            while (items.getOrNull(index + 1).let { it is UnreadDividerItem || it is DayDividerItem }) index++
        }
        /*
         * Na citaci a mezi výsledky hledání se DOJÍŽDÍ (`animateToAnchor`), ke vzdálené nebo
         * nenačtené zprávě se PŘEJEDE (`JumpTransition`). První nepřečtená při otevření chatu
         * a návrat dolů skočí hned.
         */
        val findCurrent = { currentItems.indexOfFirst { it.containsMessage(target.messageId) } }
        when {
            index < 0 -> jumpTransition.reset()
            target.anchor != ScrollAnchor.Center -> {
                jumpTransition.reset()
                listState.scrollToAnchor(index, target.anchor)
            }
            target.animate && listState.itemsAwayFrom(index) <= MAX_ANIMATED_JUMP_ITEMS -> {
                jumpTransition.reset()
                listState.animateToAnchor(index, target.anchor, jumpSpec, findCurrent)
            }
            else -> {
                // Starší zprávy jsou nahoře, obsah tedy odjíždí dolů.
                val older = target.older ?: (index > listState.firstVisibleItemIndex)
                jumpTransition.leave(
                    distance = if (older) leaveDistance else -leaveDistance,
                    moveSpec = leaveMoveSpec,
                    fadeSpec = leaveFadeSpec,
                )
                val now = findCurrent().takeIf { it >= 0 } ?: index
                listState.scrollToAnchor(now, target.anchor)
                currentItems.getOrNull(now)?.let {
                    jumpTransition.arrive(it.key, now, gatherDistance, gatherSpec)
                }
            }
        }
        viewModel.consumeScrollTarget()
    }

    /*
     * Tlačítko „dolů" i bez nových zpráv.
     *
     * Kdo odroluje do historie (nebo tam skočí po citaci), neměl jak se vrátit jinak než rolováním.
     * Ukáže se až o kus výš, ne při prvním pixelu — u spodní hrany by jen blikalo pod prstem.
     */
    val farFromBottom by remember(listState) {
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
            // Dotažená stránka k přítomnosti (okno v historii) taky posune nejnovější klíč, ale
            // nic nepřišlo — `live` je, jestli předchozí stav končil u přítomnosti.
            val arrived = anchor.key != null && newest.id > anchor.id && anchor.live
            anchor.lastWasArrival = arrived
            // Vlastní zpráva dolů vždycky — jinak by odeslání vypadalo, že se nic nestalo.
            // Cizí jen když je uživatel dole; při čtení historie by ho to vytrhlo ze čtení.
            if (arrived && (newest.isOutgoing || atBottom)) listState.requestScrollToItem(0)
            anchor.key = key
            anchor.id = newest.id
        }
        anchor.live = state.reachedEnd
    }

    /*
     * „Píše…" se vkládá pod nejnovější zprávu, a seznam drží pozici podle první viditelné
     * položky — tou je zpráva, takže by se indikátor vložil POD spodní hranu a nebyl by vidět.
     * Kdo je dole, tomu se seznam ukotví i s ním; kdo čte historii, toho nevytrhne.
     */
    val typingShown = state.typing != null && state.reachedEnd
    val typingAnchor = remember { booleanArrayOf(false) }
    if (typingShown != typingAnchor[0]) {
        if (typingShown && atBottom) listState.requestScrollToItem(0)
        typingAnchor[0] = typingShown
    }

    val newestMessageId = newest?.id

    LaunchedEffect(newestMessageId) {
        val newestId = newestMessageId ?: return@LaunchedEffect
        val previous = lastSeenMessageId
        lastSeenMessageId = newestId

        // První naplnění seznamu není „nová zpráva", jen se zobrazila historie.
        if (previous == null) return@LaunchedEffect
        if (newestId <= previous) return@LaunchedEffect

        // Stránka dotažená k přítomnosti není „nové zprávy" — odznak by lhal.
        if (!anchor.lastWasArrival) return@LaunchedEffect

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
    /*
     * Zdroj pro mléčné sklo je JEN seznam zpráv (viz `glassStyle`). Kdyby to byl `hazeState`
     * výš, rozostřovaly by se plochy samy sebou: pruh připnutých by nasál plovoucí datum a pás
     * pod polem sám sebe.
     */
    val listHaze = rememberHazeState()
    Box(modifier) {
    // Zpět hledání zavře, z chatu se odchází až dalším. `NavigationBackHandler`, ne
    // `BackHandler` — ten by zabil predictive back.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = state.search != null,
        onBackCompleted = viewModel::closeSearch,
    )
    // Zpět ukončí výběr, teprve další odejde z chatu — jako u hledání.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = state.selection != null,
        onBackCompleted = viewModel::closeSelection,
    )
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = previewAttachmentIndex != null,
        onBackCompleted = { previewAttachmentIndex = null },
    )

    /*
     * Plocha stránky při hledání USTOUPÍ o stupeň (`surfaceContainerLowest`): je to třetí signál
     * režimu vedle pilulky v hlavičce a plovoucího toolbaru, a jediný, který je vidět i koutkem
     * oka nad samotnými zprávami.
     *
     * **Ustupuje se OD bublin, ne k nim.** Posun opačným směrem (`surfaceContainerLow` a výš) je
     * sice výraznější, ale míří k `surfaceContainerHigh` příchozí bubliny — plocha by se
     * přiblížila tomu, co má nést, a zprávy by při hledání četly hůř než jindy. Takhle se rozdíl
     * naopak o kousek zvětší a nálezy vyskočí. Je to proto jemné vědomě: silnější tónování
     * v tomhle směru role nenabízejí.
     */
    val pageColor by animateColorAsState(
        targetValue = if (state.search != null) {
            MaterialTheme.colorScheme.surfaceContainerLowest
        } else {
            MaterialTheme.colorScheme.background
        },
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec<Color>(),
        label = "conversation-page",
    )

    Scaffold(
        // Barva popředí se z plochy neodvozuje: `surfaceContainerLowest` mapování nemá a fallback
        // by závisel na tom, co je zrovna v `LocalContentColor`.
        containerColor = pageColor,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxSize().hazeSource(hazeState),
        /*
         * Spodní inset si řeší vstupní pole samo (`windowInsetsPadding` se `safeDrawing`).
         *
         * Kdyby ho dal i Scaffold do `padding`, započítal by se dvakrát a pole by viselo o výšku
         * systémové lišty výš — `padding` se v obsahu jen aplikuje, inset se tím nekonzumuje.
         * Takhle navíc sahá obsah i závoj pole až pod lištu, stejně jako pod pole samotné.
         */
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = topBar@{
            state.selection?.let { selection ->
                SelectionTopBar(selection = selection, onClose = viewModel::closeSelection)
                return@topBar
            }
            state.search?.let { search ->
                InChatSearchTopBar(
                    initialQuery = search.query,
                    onQueryChange = viewModel::onSearchQuery,
                    onClose = viewModel::closeSearch,
                    sender = search.sender,
                    // Podle člena jen ve skupině: v soukromém chatu jsou odesílatelé dva a v kanálu
                    // píše jen kanál.
                    onPickSender = viewModel::openMemberPicker.takeIf { state.info?.let { it.isGroup && !it.isChannel } == true },
                    onRemoveSender = { viewModel.setSearchSender(null) },
                )
                return@topBar
            }
            TopAppBar(
                title = {
                    // Hlavička vede do profilu chatu — tak to znají uživatelé ze všech klientů.
                    // Uložené zprávy profil nemají (je to vlastní účet, nic k ukázání).
                    val profileLabel = stringResource(Res.string.profile_open)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                enabled = state.info?.isSavedMessages == false,
                                onClickLabel = profileLabel,
                                onClick = onOpenProfile,
                            ),
                    ) {
                        state.info?.let { info ->
                            Avatar(
                                chatId = info.id,
                                title = info.title,
                                avatarFileId = info.avatarFileId,
                                size = 36.dp,
                                isSavedMessages = info.isSavedMessages,
                                // Stejný tvar jako v seznamu, odkud avatar přiletěl.
                                shape = when {
                                    info.isChannel -> ChatKind.Channel
                                    info.isGroup -> ChatKind.Group
                                    else -> ChatKind.Private
                                }.avatarShape(),
                                modifier = Modifier.sharedNavElement(chatAvatarKey(info.id)),
                            )
                        }
                        Column {
                            Text(
                                text = state.info?.let { chatTitle(it.id, it.title) }
                                    ?: stringResource(Res.string.conversation_fallback_title),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                // Klíč se dá jen když chat známe; než dorazí `info`, není s čím
                                // párovat.
                                modifier = state.info
                                    ?.let { Modifier.sharedNavText(chatTitleKey(it.id)) }
                                    ?: Modifier,
                            )

                            /*
                             * Podtitul: stav spojení, jinak dostupnost.
                             *
                             * Nepřipojený klient je nade vším: dokud spojení nestojí, je „naposledy
                             * viděn" jen stará hodnota, kterou nemá co obnovit — ukázat ji místo
                             * vysvětlení, proč nic nechodí, by bylo zavádějící.
                             *
                             * „Píše…" tu NENÍ: kreslí se v seznamu zpráv tam, kam zpráva naskočí
                             * (`TypingItem`), jako v Google Zprávách. Skupina dostupnost nemá,
                             * takže se podtitul objeví jen bez spojení; výšku hlavičky proto
                             * animuje `AnimatedContent`, jinak by název poskočil.
                             */
                            val subtitle = connection.label()?.let { HeaderSubtitle(stringResource(it), accent = false) }
                                // Vlastní dostupnost v Uložených zprávách nic neříká — byl bys
                                // pořád „online", protože se na ně právě díváš.
                                ?: state.presence?.takeUnless { state.info?.isSavedMessages == true }?.let {
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
                actions = {
                    IconButton(onClick = viewModel::openSearch) {
                        Icon(
                            painterResource(Res.drawable.search_24dp),
                            contentDescription = stringResource(Res.string.conversation_search),
                        )
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
            // Při výběru patří spodek akcím nad ním; rozepsaný text zůstává v konceptu.
            state.selection?.let { selection ->
                SelectionToolbar(
                    selection = selection,
                    onCopy = viewModel::copySelection,
                    onForward = viewModel::forwardSelection,
                    onDelete = viewModel::deleteSelection,
                )
                return@Box
            }
            // Při hledání patří spodek šipkám mezi výsledky; rozepsaný text zůstává v konceptu.
            state.search?.let { search ->
                InChatSearchNavigator(
                    search = search,
                    // Procházení po dílcích — tik, ne potvrzení: výsledků bývají stovky.
                    onOlder = {
                        haptics.tick()
                        viewModel.searchOlder()
                    },
                    onNewer = {
                        haptics.tick()
                        viewModel.searchNewer()
                    },
                )
                return@Box
            }
            if (state.info?.canJoin == true) {
                JoinBar(joining = state.joining, onJoin = viewModel::join)
                return@Box
            }
            // Kde se psát nesmí, pole by skončilo chybou až po odeslání. Kanál aspoň nabídne ztlumení.
            when (state.info?.writeAccess) {
                WriteAccess.ChannelReadOnly -> {
                    MuteBar(muted = state.muted, onToggle = viewModel::toggleMute)
                    return@Box
                }
                WriteAccess.Restricted -> {
                    ReadOnlyNote(Res.string.conversation_write_restricted)
                    return@Box
                }
                WriteAccess.NotMember -> {
                    ReadOnlyNote(Res.string.conversation_not_member)
                    return@Box
                }
                WriteAccess.Allowed, null -> Unit
            }
            MessageInput(
                glass = Modifier.hazeBlur(
                    input = HazeInput.Sources(listHaze),
                    style = glassStyle(
                        color = MaterialTheme.colorScheme.surface,
                        alpha = GLASS_TINT_ALPHA,
                        // Bez rozostření zůstane dřívější krycí pás; jinak by pod ním prosvítaly
                        // zprávy a psaní by se v nich ztratilo.
                        fallbackAlpha = INPUT_SCRIM_ALPHA,
                    ),
                ),
                replyingTo = state.replyingTo,
                partnerName = state.info?.title,
                flight = state.flight,
                onCancelReply = viewModel::clearReply,
                // Odeslání je potvrzení akce; dosud ho doprovázel jen let bubliny. Hlásí se
                // v okamžiku ťuknutí, ne po odpovědi serveru — odezva patří ke gestu.
                onSend = { text ->
                    haptics.confirm()
                    viewModel.send(text)
                },
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
                            // Nahrávání běží, dokud ho uživatel neukončí — přepnutý stav, ne
                            // hotová akce. Odepřené oprávnění je naopak odmítnutí.
                            haptics.toggle(on = true)
                            viewModel.startRecording()
                        } else {
                            haptics.reject()
                            viewModel.reportError(UiText(Res.string.conversation_microphone_permission))
                        }
                    }
                },
                onCancelRecording = {
                    haptics.reject()
                    viewModel.cancelRecording()
                },
                onSendRecording = {
                    haptics.confirm()
                    viewModel.sendRecording()
                },
                onPickDocument = attachmentPicker::pickDocument,
                attachments = state.attachments,
                onRemoveAttachment = viewModel::removeAttachment,
                onOpenAttachment = { index -> previewAttachmentIndex = index },
                onSetAttachmentMode = viewModel::setAttachmentMode,
                content = state.content,
                selfDestructAllowed = state.canSelfDestruct(attachmentCount = 1),
                previewAttachmentIndex = previewAttachmentIndex,
                expressionMode = expressionMode,
                draft = draft,
                onDraftValueChange = { draft = it },
                onKeyboardContent = { received ->
                    when (received) {
                        // GIF odchází hned, jako samolepka; obrázek čeká nad polem jako z výběru.
                        is KeyboardContent.Animation -> viewModel.sendAnimation(received.animation)
                        is KeyboardContent.Image -> viewModel.addAttachments(listOf(received.photo))
                    }
                },
                onKeyboardContentFailed = viewModel::reportError,
            )
            }
        },
        // Tlačítko dolů ve FAB slotu: Scaffold ho drží nad polem a snackbar pak nad ním.
        // Uprostřed, ne vpravo: vpravo by viselo přímo nad tlačítkem odeslání a mikrofonu
        // a ťuknutí o kousek vedle by místo skoku dolů spustilo nahrávání.
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            ScrollToBottomButton(
                // V okně v historii vede dolů i od spodní hrany — nejnovější zprávy nejsou načtené.
                visible = farFromBottom || pendingNewMessages > 0 || !state.reachedEnd,
                count = pendingNewMessages,
                onClick = {
                    if (state.reachedEnd) {
                        scope.launch { listState.animateScrollToItem(0) }
                    } else {
                        viewModel.jumpToLatest()
                    }
                },
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
                stickersAvailable = state.editing == null && state.content.stickers,
                draft = draft,
                // Úprava z výběru emoji je psaní jako každé jiné: ukládá koncept i hlásí „píše…".
                onDraftEdit = {
                    draft = it
                    viewModel.onDraftChanged(it.text)
                },
                onPick = { sticker ->
                    stickerPickerOpen = false
                    haptics.confirm()
                    viewModel.sendSticker(sticker)
                },
                chatId = viewModel.chatId,
                onPickGif = { gif ->
                    stickerPickerOpen = false
                    haptics.confirm()
                    viewModel.sendGif(gif)
                },
                onDismiss = { stickerPickerOpen = false },
            )
        }

        // Sada z odkazu `t.me/addstickers/…` otevře týž sheet jako ťuknutí na samolepku.
        LaunchedEffect(state.openStickerSet) {
            val setId = state.openStickerSet ?: return@LaunchedEffect
            viewModel.consumeOpenStickerSet()
            openStickerSetId = setId
        }

        state.memberPicker?.let { picker ->
            MemberPickerSheet(
                picker = picker,
                onQueryChange = viewModel::onMemberQuery,
                onPick = viewModel::setSearchSender,
                onDismiss = viewModel::closeMemberPicker,
            )
        }

        state.invite?.let { invite ->
            InviteSheet(invite = invite, onJoin = viewModel::joinInvite, onDismiss = viewModel::dismissInvite)
        }

        openStickerSetId?.let { setId ->
            StickerSetSheet(
                setId = setId,
                onPick = { sticker ->
                    openStickerSetId = null
                    haptics.confirm()
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

        state.selectingText?.let { message ->
            SelectTextSheet(message = message, onDismiss = viewModel::dismissTextSelection)
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
                        /*
                         * `LazyColumn` ořezává jen ve směru scrollu, tedy svisle. Sloupec časů
                         * čekající za pravým okrajem byl schovaný jen proto, že seznam končil
                         * u okraje obrazovky; s bočním insetem (výřez vpravo) prosvítal. Ořez
                         * drží i odtaženou bublinu v jejím panelu.
                         */
                        .clipToBounds()
                        .hazeSource(listHaze)
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
                    // Odjezd při přechodu na vzdálenou zprávu; čte se až ve vrstvě.
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        alpha = jumpTransition.listAlpha
                        translationY = jumpTransition.listShift
                    },
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
                FloatingDayBadge(
                    text = floatingDay,
                    glass = Modifier.hazeBlur(
                        input = HazeInput.Sources(listHaze),
                        style = glassStyle(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            alpha = GLASS_TEXT_TINT_ALPHA,
                            fallbackAlpha = GLASS_FALLBACK_ALPHA,
                        ),
                    ),
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
             * Ukazuje se jedna zpráva (viz `PinnedMessagesBar`), a **která, rozhoduje scroll**
             * (`pinnedForScroll`): ta, kterou uživatel právě míjí. Pruh tím slouží i jako
             * orientace v historii. Nedrží se proto žádný stav — odvozuje se z klíče nejvyšší
             * viditelné položky, takže nová připnutá zpráva výběr nerozhodí.
             */
            var allPinnedOpen by rememberSaveable { mutableStateOf(false) }

            val density = LocalDensity.current
            AnimatedVisibility(
                visible = state.pinned.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onSizeChanged { pinnedHeight = with(density) { it.height.toDp() } },
            ) {
                // Při odjezdu se musí dál kreslit poslední zpráva, ne prázdný pruh.
                val last = remember { arrayOf(emptyList<ChatMessage>()) }
                if (state.pinned.isNotEmpty()) last[0] = state.pinned
                val pinned = last[0]

                PinnedMessagesBar(
                    messages = pinned,
                    index = pinnedIndex(pinned, pinnedForScroll(pinned, topVisibleKey)),
                    canUnpin = state.canUnpin,
                    // Ťuknutí skočí na ukázanou zprávu, a to KOTVOU NAHORU: zpráva pak stojí nad
                    // viditelnou částí, takže pruh přejde na předchozí připnutou a další ťuknutí
                    // vede o krok zpět. Se středem se nad cílem odkryje kus historie a pruh
                    // přeskočí rovnou k nejstarší.
                    onOpen = { message -> viewModel.jumpToMessage(message.id, anchor = ScrollAnchor.Top) },
                    onUnpin = viewModel::unpin,
                    onShowAll = { allPinnedOpen = true },
                    glass = Modifier.hazeBlur(
                        input = HazeInput.Sources(listHaze),
                        style = glassStyle(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            alpha = GLASS_TEXT_TINT_ALPHA,
                            fallbackAlpha = GLASS_FALLBACK_ALPHA,
                        ),
                    ),
                )
            }

            if (allPinnedOpen && state.pinned.isNotEmpty()) {
                PinnedMessagesSheet(
                    messages = state.pinned,
                    canUnpin = state.canUnpin,
                    onOpen = { message -> viewModel.jumpToMessage(message.id, anchor = ScrollAnchor.Top) },
                    onUnpin = viewModel::unpin,
                    onDismiss = { allPinnedOpen = false },
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

    val lastPreviewed = remember { arrayOf<Pair<Int, OutgoingAttachment>?>(null) }
    if (previewAttachmentIndex != null) {
        val att = state.attachments.getOrNull(previewAttachmentIndex!!)
        if (att != null) {
            lastPreviewed[0] = previewAttachmentIndex!! to att
        }
    }
    val currentTarget = if (previewAttachmentIndex != null) {
        previewAttachmentIndex?.let { idx -> state.attachments.getOrNull(idx)?.let { idx to it } }
    } else {
        lastPreviewed[0]
    }

    AnimatedVisibility(
        visible = previewAttachmentIndex != null && currentTarget != null,
        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
    ) {
        currentTarget?.let { (index, attachment) ->
            StagedAttachmentPreview(
                attachment = attachment,
                isOpen = previewAttachmentIndex != null,
                onDismiss = { previewAttachmentIndex = null },
                onDelete = {
                    viewModel.removeAttachment(index)
                    previewAttachmentIndex = null
                },
                onSetMode = { mode ->
                    viewModel.setAttachmentMode(index, mode)
                },
                selfDestructAllowed = state.canSelfDestruct(),
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
    /** Mléčné sklo nad seznamem: pás je průhledný, barvu i rozostření nese sklo. */
    glass: Modifier,
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
    onOpenAttachment: (Int) -> Unit = {},
    onSetAttachmentMode: (Int, AttachmentMode) -> Unit = { _, _ -> },
    /** Soukromý chat, kde se u jedné přílohy smí nabídnout mizení (`canSelfDestruct`). */
    selfDestructAllowed: Boolean = false,
    previewAttachmentIndex: Int? = null,
    /** Ikona tlačítka výběru ukazuje, čím se výběr otevře — emoji, nebo samolepkami. */
    expressionMode: ExpressionMode,
    /** Rozepsaný text; drží ho obrazovka, protože do něj vkládá i výběr emoji. */
    draft: TextFieldValue,
    onDraftValueChange: (TextFieldValue) -> Unit,
    /**
     * Které druhy obsahu chat přijme. Co se poslat nesmí, se nenabízí: sponka schová položku
     * (a bez jediné i sama sebe), mikrofon ustoupí odeslání, samolepky zůstanou na emoji.
     */
    content: ContentAccess = ContentAccess.All,
    /** GIF nebo obrázek vložený z klávesnice (Gboard), už zkopírovaný do cache. */
    onKeyboardContent: (KeyboardContent) -> Unit = {},
    onKeyboardContentFailed: (UiText) -> Unit = {},
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
    // Nahoře, ne u pole: podle fokusu se ukazuje i návrh vložení ze schránky nad polem.
    val fieldInteraction = remember { MutableInteractionSource() }
    val fieldFocused by fieldInteraction.collectIsFocusedAsState()

    /*
     * Pruh má barvu POZADÍ, jen průsvitnou.
     *
     * Zprávy pod něj zajíždějí (padding `bottomBar` jde do `contentPadding` seznamu, viz výše)
     * a bez pruhu by se pod kartou
     * míchaly s textem. Plná barva by zase udělala ostrý předěl přes celou obrazovku. Průsvit
     * dělá závoj: je vidět, že tam obsah je, ale nepřebíjí psaní.
     */
    Surface(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.then(glass),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                /*
                 * Spodek (lišta, klávesnice) i boky. Naležato leží na boku výřez kamery nebo
                 * navigační lišta se třemi tlačítky a tlačítko odeslání by jinak zajelo pod ně.
                 * Levý inset vedle railu už zkonzumoval shell.
                 */
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                )
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
            /*
             * Při nahrávání se karta ROZVLNÍ a přebarví. Pruh nahrávání vypadá jinak než pole,
             * ale na místě zůstává — bez změny karty se dal přehlédnout otevřený mikrofon.
             * Vlnka je v aplikaci „hlas": stejně (jen mělčeji a bez pohybu) jsou zvlněné
             * hlasovky v konverzaci. Stojí, neobíhá — živost nese dýchající tlačítko vedle.
             */
            val recordingWave by animateFloatAsState(
                targetValue = if (isRecording) 1f else 0f,
                animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
                label = "composer-recording-wave",
            )
            val cardColor by animateColorAsState(
                targetValue = if (isRecording) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
                label = "composer-recording-color",
            )
            Surface(
                shape = if (recordingWave > 0f) {
                    WavyShape(ComposerShape, progress = recordingWave, phase = 0f)
                } else {
                    ComposerShape
                },
                color = cardColor,
                modifier = Modifier.weight(1f),
            ) {
              Column {
                // Úprava má přednost před citací: odpověď se při ní neruší, jen se na chvíli schová.
                if (editing != null) {
                    EditingHeader(message = editing, onCancel = onCancelEdit)
                } else {
                  StagedAttachmentsStrip(
                      attachments = attachments,
                      onRemove = onRemoveAttachment,
                      onOpen = onOpenAttachment,
                      onSetMode = onSetAttachmentMode,
                      selfDestructAllowed = selfDestructAllowed,
                      previewAttachmentIndex = previewAttachmentIndex,
                  )
                  replyingTo?.let { target ->
                    // Citace je horní pruh karty, barevně oddělený — stejně jako na kartě zprávy,
                    // ne subkartička v kartě.
                    ComposerHeader {
                        QuotedMessage(
                            message = target,
                            partnerName = partnerName,
                            accent = composerHeaderAccent(),
                            contained = false,
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
                  /*
                   * GIF nebo obrázek ve schránce: na iOS nabídne vložení systémovým tlačítkem,
                   * které se na povolení neptá (viz `ClipboardPasteSuggestion`). Jen s fokusem
                   * v poli — návrh patří k psaní, jako návrhy klávesnice.
                   */
                  ClipboardPasteSuggestion(
                      active = fieldFocused && !isRecording,
                      acceptAnimations = content.stickers,
                      acceptImages = content.photos,
                      onContent = onKeyboardContent,
                      onError = onKeyboardContentFailed,
                  ) { kind, pasteButton ->
                      ComposerHeader {
                          Text(
                              text = stringResource(
                                  when (kind) {
                                      ClipboardMediaKind.Animation -> Res.string.clipboard_has_gif
                                      ClipboardMediaKind.Image -> Res.string.clipboard_has_image
                                  },
                              ),
                              style = MaterialTheme.typography.labelLarge,
                              modifier = Modifier.weight(1f).padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                          )
                          pasteButton()
                      }
                  }
                }

                // Při nahrávání místo pole pruh s časem; citace nad ním zůstává, hlasovka na ni odpoví.
                if (isRecording) {
                    RecordingBar(recording = recording, onCancel = onCancelRecording)
                } else Row(verticalAlignment = Alignment.Bottom) {
            /*
             * Ikony stojí VEDLE pole, ne v jeho `leadingIcon` / `trailingIcon`. `TextField`
             * sloty centruje na výšku pole, takže s víceřádkovou zprávou ikony ujížděly doprostřed;
             * tady se drží dole u posledního řádku, stejně jako odesílací tlačítko vedle karty.
             * Odsazení 4 dp je (56 − 48) / 2: tlačítko pak sedí na střed jednořádkového pole.
             */
            // Přílohy jen při psaní — k úpravě textové zprávy příloha nepatří.
            if (editing == null && content.anyAttachment) {
                AttachButton(
                    onPickMedia = onPickMedia,
                    onPickDocument = onPickDocument,
                    // Skupina může povolit fotky a zakázat soubory; nabídne se jen dovolené.
                    canPickMedia = content.photos || content.videos,
                    canPickDocument = content.documents,
                    contentColor = inputIconColor(),
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }
            Box(Modifier.weight(1f)) {
            /*
             * `BasicTextField` s dekorací, ne `TextField`: ten má vnitřní odsazení 16 dp napevno
             * a vedle sponky (48dp tlačítko, glyf má kolem sebe dalších 12 dp) z toho byla
             * mezera skoro 30 dp, než začal text. U ikony stačí 4 dp — glyf má svůj vzduch.
             * Bez sponky (úprava zprávy) zůstává výchozích 16 dp od hrany karty.
             */
            val fieldColors = TextFieldDefaults.colors(
                // Kontejner i podtržení nese karta kolem; pole samo nekreslí nic.
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            )
            // Klávesnice nabízí GIFy a samolepky jen poli, které řekne, že je přijme.
            // Při úpravě zprávy ne — k upravovanému textu příloha nepatří, stejně jako sponka.
            KeyboardContentReceiver(
                acceptAnimations = editing == null && content.stickers,
                acceptImages = editing == null && content.photos,
                onContent = onKeyboardContent,
                onError = onKeyboardContentFailed,
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = {
                        val changed = it.text != draft.text
                        onDraftValueChange(it)
                        // Pohyb kurzoru nebo výběr taky volá `onValueChange`; psaní to není.
                        if (changed) onDraftChanged(it.text)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = TextFieldDefaults.MinHeight),
                    textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 6,
                    interactionSource = fieldInteraction,
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
                    decorationBox = { innerTextField ->
                        TextFieldDefaults.DecorationBox(
                            value = draft.text,
                            innerTextField = innerTextField,
                            enabled = true,
                            singleLine = false,
                            visualTransformation = VisualTransformation.None,
                            interactionSource = fieldInteraction,
                            // S přílohami je text jejich popisek.
                            placeholder = { Text(stringResource(if (withAttachments) Res.string.conversation_input_caption else Res.string.conversation_input_message)) },
                            colors = fieldColors,
                            contentPadding = TextFieldDefaults.contentPaddingWithoutLabel(
                                start = if (editing == null) FIELD_ICON_GAP else FIELD_EDGE_PADDING,
                                end = FIELD_ICON_GAP,
                            ),
                            container = {},
                        )
                    },
                )
            }

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
            IconButton(
                onClick = {
                    // Sheet a klávesnice by se jinak přetahovaly o spodek obrazovky.
                    keyboard?.hide()
                    onOpenStickers()
                },
                colors = IconButtonDefaults.iconButtonColors(contentColor = inputIconColor()),
                modifier = Modifier.padding(end = 4.dp, bottom = 4.dp),
            ) {
                // Ikona slibuje, co se otevře: poslední použitou část výběru. Při úpravě
                // zprávy jde otevřít jen emoji — samolepka do textu upravované zprávy nepatří.
                when {
                    editing != null || !content.stickers || expressionMode == ExpressionMode.Emoji ->
                        Icon(painterResource(Res.drawable.mood_24dp), contentDescription = stringResource(Res.string.conversation_open_emoji))
                    expressionMode == ExpressionMode.Gifs ->
                        Icon(painterResource(Res.drawable.gif_box_24dp), contentDescription = stringResource(Res.string.conversation_open_gifs))
                    else ->
                        Icon(painterResource(Res.drawable.sticky_note_2_24dp), contentDescription = stringResource(Res.string.conversation_open_stickers))
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
             * Tvar nese stav: mikrofon je „sušenka", odeslání kruh (`MorphShape`, `ShapeMorphs.Rest`);
             * kruh se při stisku mění podle M3 Expressive na zaoblený čtverec.
             */
            /*
             * Prázdné pole bez příloh = mikrofon (jako v Telegramu i WhatsAppu): tlačítko, které by
             * jinak bylo neaktivní, dostane smysl. Ťuknutí nahrávání spustí, odeslání ho ukončí.
             * Při úpravě zprávy mikrofon není — hlasovka úprava není.
             */
            // Bez práva na hlasovky mikrofon není — tlačítko zůstane odesláním, jen neaktivním.
            val showMic = !isRecording && editing == null && draft.text.isBlank() && !withAttachments && content.voiceNotes

            /*
             * Prázdný POPISEK je platná úprava — tím se popisek odebere —, takže tlačítko musí jít
             * zmáčknout i s prázdným polem. U textu zprávy ne: prázdnou zprávu server odmítne.
             */
            val removableCaption = editing?.plainTextEdit() == PlainTextEdit.Caption
            val kind = when {
                editing != null -> SendButtonKind.Save
                showMic -> SendButtonKind.Mic
                else -> SendButtonKind.Send
            }

            /*
             * Tvar nese stav: mikrofon (klidový stav prázdného pole) je „sušenka" z M3 Expressive,
             * odeslání a uložení úpravy kruh. Změna se animuje morfem mezi nimi a ikona se vymění
             * zvětšením. Tlačítko se mění samo od sebe (začne se psát, pole se vyprázdní) a skoková
             * výměna ikony se dala přehlédnout — a ťuknout pak na mikrofon místo odeslání.
             * Stav se tak pozná i bez ikony a barvy, třeba koutkem oka nebo na slunci.
             */
            // Pružina přestřelí za oba konce; mimo 0..1 by se tvar zdeformoval.
            val morphProgress = animateMorph(kind == SendButtonKind.Mic, "send-button-shape").coerceIn(0f, 1f)

            /*
             * Při nahrávání tlačítko DÝCHÁ: pomalý morf kruh ↔ SoftBurst. Tečka v pruhu nahrávání
             * už nebliká — dva blikající prvky vedle sebe by byly šum — a živost nese tlačítko,
             * kterým se nahrávání i odesílá.
             */
            val breathing = if (isRecording) {
                rememberInfiniteTransition(label = "recording-breath").animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(RECORDING_BREATH_MS), RepeatMode.Reverse),
                    label = "recording-breath-progress",
                ).value
            } else {
                0f
            }

            /*
             * Barva říká, jak silná akce to je. Mikrofon je klidový stav prázdného pole, takže
             * tonální; odeslání i uložení úpravy jsou hlavní akce obrazovky, takže plná `primary`
             * (jako odchozí bublina). Tlačítko tak „ožije", jakmile je co poslat. Víc barev by
             * z něj udělalo semafor — barva by přestala znamenat důležitost.
             */
            val colorSpec = MaterialTheme.motionScheme.slowEffectsSpec<Color>()
            val containerColor by animateColorAsState(
                targetValue = if (kind == SendButtonKind.Mic) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
                animationSpec = colorSpec,
                label = "send-button-container",
            )
            val contentColor by animateColorAsState(
                targetValue = if (kind == SendButtonKind.Mic) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onPrimary
                },
                animationSpec = colorSpec,
                label = "send-button-content",
            )

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
                // Přílohy jdou poslat i bez popisku a popisek jde i odebrat.
                enabled = isRecording || showMic || draft.text.isNotBlank() || withAttachments || removableCaption,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = containerColor,
                    contentColor = contentColor,
                ),
                // Kruh (odeslání) se při stisku mění podle M3 Expressive na zaoblený čtverec.
                // Sušenka a tvary během přechodu stisk nemění: mezi obecnými tvary IconButton
                // neanimuje a skok by vypadal jako chyba.
                shapes = if (breathing > 0f) {
                    val shape = MorphShape(ShapeMorphs.Live, breathing, rotation = RECORDING_BREATH_ROTATION)
                    IconButtonShapes(shape = shape, pressedShape = shape)
                } else if (morphProgress > 0f) {
                    val shape = MorphShape(ShapeMorphs.Rest, morphProgress, rotation = ShapeMorphs.REST_ROTATION)
                    IconButtonShapes(shape = shape, pressedShape = shape)
                } else {
                    IconButtonDefaults.shapes(
                        shape = IconButtonDefaults.mediumRoundShape,
                        pressedShape = IconButtonDefaults.mediumPressedShape,
                    )
                },
                modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
            ) {
                // Ikona jede stejnou pružinou jako tvar — rychlejší by se vyměnila dřív, než je
                // morf vůbec vidět.
                val iconScale = morphSpring<Float>()
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

/** Tvar karty vstupního pole; při nahrávání se z něj rozvlní [WavyShape]. */
private val ComposerShape = RoundedCornerShape(24.dp)

/** Odsazení textu od ikony vedle pole — glyf má v 48dp tlačítku kolem sebe svých 12 dp. */
private val FIELD_ICON_GAP = 4.dp

/** Odsazení textu od hrany karty, kde ikona není (úprava zprávy); výchozí u `TextField`. */
private val FIELD_EDGE_PADDING = 16.dp

/** Půlperioda dýchání tlačítka při nahrávání. */
private const val RECORDING_BREATH_MS = 900

/** O kolik se tlačítko při nádechu pootočí. */
private const val RECORDING_BREATH_ROTATION = 30f

/**
 * Barva ikon v kartě vstupního pole (přílohy, emoji/samolepky).
 *
 * Výchozí `onSurfaceVariant` byla na `surfaceContainerHigh` karty zašedlá a špatně čitelná.
 * `primary` je to, čím M3 odlišuje ovládání od textu (placeholder zůstává šedý), a na kartě
 * má kontrast v obou režimech — role drží hierarchii, ne pevnou světlost.
 */
@Composable
private fun inputIconColor() = MaterialTheme.colorScheme.primary

/** Co tlačítko u vstupního pole právě dělá. Změna mezi nimi se animuje. */
private enum class SendButtonKind { Mic, Send, Save }

/**
 * Horní pruh karty nad vstupním polem (citace, úprava): tónovaná plocha přes celou šířku karty,
 * stejně jako citace na kartě zprávy. Zaoblení nahoře nese karta, pruh je rovný.
 */
@Composable
private fun ComposerHeader(content: @Composable RowScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * Akcent uvnitř [ComposerHeader] — `tertiary`, dokud je na jeho ploše vidět.
 *
 * `tertiary` na `tertiaryContainer` je zkratka, kterou M3 negarantuje: v paletě se zvýšeným
 * kontrastem se v tmavém režimu obě potkají na téže světlosti a popisek zmizí. Tatáž past jako
 * u citace na kartě zprávy — viz [accentOn].
 */
@Composable
private fun composerHeaderAccent(): Color = accentOn(
    background = MaterialTheme.colorScheme.tertiaryContainer,
    accent = MaterialTheme.colorScheme.tertiary,
    fallback = MaterialTheme.colorScheme.onTertiaryContainer,
)

/**
 * Hlavička karty při úpravě zprávy.
 *
 * Vypadá jako citace (ikona, popisek, původní text), protože plní stejnou roli: říká, k čemu
 * se vztahuje to, co se píše. Původní text zůstává vidět, i když ho uživatel v poli přepíše.
 *
 * **Popisek přílohy to musí říct**: fotka v konverzaci zůstane, ať se v poli píše cokoli, takže
 * bez toho by nebylo poznat, že se upravuje popisek, a ne zpráva. U přílohy bez popisku se
 * původní text nekreslí vůbec — prázdný řádek by vypadal jako chyba vykreslení.
 */
@Composable
private fun EditingHeader(message: ChatMessage, onCancel: () -> Unit) = ComposerHeader {
    val accent = composerHeaderAccent()
    val caption = message.plainTextEdit() == PlainTextEdit.Caption
    Column(Modifier.weight(1f).padding(start = 14.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(Res.drawable.edit_24dp),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.padding(end = 4.dp).size(16.dp),
            )
            Text(
                text = stringResource(
                    if (caption) Res.string.conversation_editing_caption else Res.string.conversation_editing,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = accent,
            )
        }
        if (message.text.isNotBlank()) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    IconButton(onClick = onCancel) {
        Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.conversation_cancel_edit))
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
    if (anchor == ScrollAnchor.Bottom) {
        scrollToItem(0)
        return
    }
    scrollToItem(index)

    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    // Offset roste od začátku seznamu, tedy v obráceném rozvržení odspodu nahoru. Nula je horní
    // hrana spodního odsazení, `visibleHeight` spodní hrana horního.
    val visibleHeight = info.viewportEndOffset - info.afterContentPadding
    val desired = when (anchor) {
        ScrollAnchor.Top -> visibleHeight - item.size
        ScrollAnchor.Center -> (visibleHeight - item.size) / 2
        ScrollAnchor.Bottom -> 0
    }.coerceAtLeast(0)
    scrollBy((item.offset - desired).toFloat())
}

/**
 * Jako [scrollToAnchor], ale seznam k položce DOJEDE — u skoku na citaci a mezi výsledky
 * hledání je jinak vidět jen okamžité přeskočení a ztratí se, kterým směrem a jak daleko zpráva je.
 *
 * Položka mimo obrazovku nemá změřenou polohu, takže se napřed dojede na odhad z průměrné výšky
 * viditelných položek a teprve pak se poloha doladí podle skutečného layoutu. Daleko
 * ([MAX_ANIMATED_JUMP_ITEMS]) se nedojíždí: rolování přes desítky zpráv by trvalo a nic by
 * neukázalo, a navíc by skládalo všechno mezi.
 */
/** O kolik položek je [index] mimo viditelnou část seznamu; 0 = je vidět. */
private fun LazyListState.itemsAwayFrom(index: Int): Int {
    val visible = layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) return Int.MAX_VALUE
    return when {
        index < visible.first().index -> visible.first().index - index
        index > visible.last().index -> index - visible.last().index
        else -> 0
    }
}

/** Jak daleko seznam při přechodu odjede, než zmizí. */
private val JUMP_LEAVE_DISTANCE = 96.dp

/** Z jaké vzdálenosti k nalezené zprávě sjedou sousedé (nejbližší; vzdálenější z dál). */
private val JUMP_GATHER_DISTANCE = 56.dp

private suspend fun LazyListState.animateToAnchor(
    index: Int,
    anchor: ScrollAnchor,
    spec: AnimationSpec<Float>,
    /** Index cíle v AKTUÁLNÍM seznamu. Během jízdy se mohou dotáhnout stránky a index se posune. */
    currentIndex: () -> Int,
) {
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return scrollToAnchor(index, anchor)
    val first = visible.first()
    val last = visible.last()
    val distance = when {
        index < first.index -> first.index - index
        index > last.index -> index - last.index
        else -> 0
    }
    if (distance > MAX_ANIMATED_JUMP_ITEMS) return scrollToAnchor(index, anchor)

    val visibleHeight = info.viewportEndOffset - info.afterContentPadding
    fun desired(size: Int) = when (anchor) {
        ScrollAnchor.Top -> visibleHeight - size
        ScrollAnchor.Center -> (visibleHeight - size) / 2
        ScrollAnchor.Bottom -> 0
    }.coerceAtLeast(0)

    val item = visible.firstOrNull { it.index == index }
    if (item == null) {
        // Odhad polohy: průměrná položka krát počet položek k cíli. Offset roste od spodní hrany
        // nahoru (obrácený seznam), vyšší index je výš.
        val average = visible.sumOf { it.size } / visible.size
        val estimatedOffset = if (index > last.index) {
            last.offset + last.size + (distance - 1) * average
        } else {
            first.offset - distance * average
        }
        animateScrollBy((estimatedOffset - desired(average)).toFloat(), spec)
    }
    // Doladění podle skutečného layoutu; odhad se mohl minout, nebo cíl vůbec není vidět.
    val now = currentIndex().takeIf { it >= 0 } ?: return
    val landed = layoutInfo.visibleItemsInfo.firstOrNull { it.index == now }
        ?: return scrollToAnchor(now, anchor)
    val correction = (landed.offset - desired(landed.size)).toFloat()
    if (correction != 0f) animateScrollBy(correction, spec)
}

/** Nejvýš o tolik položek se na citaci / výsledek hledání dojíždí; dál se skočí. */
private const val MAX_ANIMATED_JUMP_ITEMS = 40

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
    private val onSearch: (query: String) -> Unit,
    private val onSaveFileAs: (messageId: Long) -> Unit,
    private val jumpTransition: JumpTransition,
    private val searchHighlight: State<String?>,
    private val profileFlight: MutableState<Long?>,
) : ConversationItemScope {
    override fun isProfileFlight(messageId: Long): Boolean = profileFlight.value == messageId

    override fun searchHighlight(): String? = searchHighlight.value

    override fun gatherShift(key: Any): Float = jumpTransition.shiftOf(key)

    override fun gatherAlpha(key: Any): Float = jumpTransition.alphaOf(key)

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

    /**
     * Navigace, sheet nad obrazovkou a systémový výběr umístění jsou věc obrazovky; všechno
     * ostatní jde do ViewModelu.
     */
    override fun onAction(messageId: Long, action: MessageAction) = when (action) {
        MessageAction.OpenMedia -> onOpenMedia(messageId)
        is MessageAction.OpenStickerSet -> onOpenStickerSet(action.setId)
        is MessageAction.SearchHashtag -> onSearch(action.tag)
        MessageMenuAction.SaveFileAs -> onSaveFileAs(messageId)
        is MessageAction.OpenSenderProfile -> {
            // Klíč letu musí stát dřív, než se profil otevře; chat se hledá asynchronně.
            profileFlight.value = messageId
            viewModel.onAction(messageId, action)
        }
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

    /** Předchozí stav končil u přítomnosti — jen pak je novější nejnovější zpráva příchozí. */
    var live: Boolean = true

    /** Poslední změna nejnovější zprávy byla příchod, ne dotažená stránka. Čte ho odznak dole. */
    var lastWasArrival: Boolean = false
}

private const val LOAD_MORE_THRESHOLD = 5

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

/** Hodiny serveru a telefonu se můžou o chvilku lišit; o tolik starší zpráva je ještě příchod. */
private const val ARRIVAL_TOLERANCE_SECONDS = 5

/**
 * Místo vstupního pole ve skupině nebo kanálu, kde uživatel není (otevřeno z globálního hledání).
 * Plné tlačítko přes šířku — je to jediná akce, kterou tu jde udělat.
 */
@Composable
private fun JoinBar(joining: Boolean, onJoin: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Button(
            onClick = onJoin,
            enabled = !joining,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Text(stringResource(Res.string.conversation_join))
        }
    }
}

/**
 * Místo vstupního pole v kanálu, kde uživatel nepublikuje: ztlumit / zapnout zvuk, jako
 * v oficiálním klientu. Popisek říká, co se stane, ne stav (stejně jako v profilu chatu).
 */
@Composable
private fun MuteBar(muted: Boolean?, onToggle: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        FilledTonalButton(
            onClick = onToggle,
            enabled = muted != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Icon(
                painterResource(if (muted == true) Res.drawable.notifications_24dp else Res.drawable.notifications_off_24dp),
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (muted == true) Res.string.profile_unmute else Res.string.profile_mute))
        }
    }
}

/** Místo vstupního pole tam, kde psát nejde a nic se s tím udělat nedá — aspoň proč. */
@Composable
private fun ReadOnlyNote(text: StringResource) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
    )
}
