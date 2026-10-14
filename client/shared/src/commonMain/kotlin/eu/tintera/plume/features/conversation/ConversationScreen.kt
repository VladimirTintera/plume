package eu.tintera.plume.features.conversation

import androidx.compose.ui.input.key.key
import eu.tintera.plume.core.telegram.MessageMedia
import androidx.compose.ui.geometry.Offset
import eu.tintera.plume.core.ui.SwipeDirection
import eu.tintera.plume.core.ui.detectHorizontalSwipe
import eu.tintera.plume.core.telegram.VideoChatInfo
import eu.tintera.plume.core.telegram.PendingJoinRequests
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.hazeBlur
import eu.tintera.plume.core.ui.sharedElementClipArea
import eu.tintera.plume.core.ui.rememberSharedElementClip
import eu.tintera.plume.core.ui.LocalSharedElementClip
import androidx.compose.material3.FilledTonalButton
import eu.tintera.plume.resources.summary_me
import org.koin.compose.viewmodel.koinViewModel
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.NavigationEventInfo
import eu.tintera.plume.core.ui.chatTitle
import androidx.compose.animation.animateColorAsState
import eu.tintera.plume.features.conversation.items.SoundingVideoNote
import eu.tintera.plume.features.conversation.items.rememberSoundingVideoNote
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import eu.tintera.plume.core.ui.rememberShownConnection
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import eu.tintera.plume.core.files.rememberSaveAs
import androidx.compose.material3.FabPosition
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import org.koin.compose.koinInject
import eu.tintera.plume.core.telegram.SelfDestructTimers
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import eu.tintera.plume.core.settings.GestureHint
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.Presence
import eu.tintera.plume.core.ui.asDayLabel
import eu.tintera.plume.core.ui.startOfLocalDay
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.core.ui.rememberEmbeddedMediaSelection
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.features.conversation.items.FloatingDayBadge
import eu.tintera.plume.features.conversation.items.ConversationItemContent
import eu.tintera.plume.features.conversation.items.MESSAGE_SPACING
import eu.tintera.plume.features.conversation.items.MenuAnchor
import eu.tintera.plume.features.conversation.items.MessageAction
import eu.tintera.plume.features.conversation.items.mapUrl
import kotlin.math.abs
import eu.tintera.plume.features.conversation.items.MessageItem
import eu.tintera.plume.features.conversation.items.TIME_COLUMN_WIDTH
import eu.tintera.plume.features.conversation.items.ConversationItem
import eu.tintera.plume.features.conversation.items.conversationItems
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_chat_sensitive_show
import eu.tintera.plume.resources.conversation_chat_sensitive_text
import eu.tintera.plume.resources.conversation_chat_sensitive_title
import eu.tintera.plume.resources.visibility_off_24dp
import eu.tintera.plume.resources.conversation_chat_restricted
import eu.tintera.plume.resources.block_24dp
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.resources.conversation_empty
import eu.tintera.plume.resources.conversation_fallback_title
import kotlinx.coroutines.flow.collectLatest
import eu.tintera.plume.core.telegram.SpamBar
import eu.tintera.plume.core.telegram.ChatBar
import eu.tintera.plume.core.telegram.InviteMembersBar
import eu.tintera.plume.core.telegram.JoinRequestBar
import eu.tintera.plume.core.telegram.SharePhoneBar
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    /** Kam konverzace odchází — viz [ConversationNavigation]. */
    navigation: ConversationNavigation,
    modifier: Modifier = Modifier,
) {
    val stateHolder = viewModel.state.collectAsStateWithLifecycle()
    val state by stateHolder

    /*
     * Chat, jehož obsah se neukazuje: omezený (odkrýt nejde), nebo citlivý a uživatel ještě
     * neřekl „přesto zobrazit". Podle toho se řídí obsah, pole, hlavička i pruh připnutých —
     * jedna podmínka, ať některé z nich obsah neprozradí (tak to udělal pruh připnutých).
     * Potvrzení platí pro tohle otevření chatu (i po otočení), ne napořád: napořád je volba
     * v Nastavení → Soukromí.
     */
    var sensitiveAccepted by rememberSaveable(viewModel.chatId) { mutableStateOf(false) }
    val sensitiveWarning = state.info?.sensitive == true && !sensitiveAccepted
    val chatHidden = state.info?.restriction != null || sensitiveWarning

    // Shrnutí modelem v zařízení (experimentální, viz `core/ai`). Vlastní ViewModel s klíčem
    // podle chatu — stejná pojistka jako u konverzace samotné.
    val summary: ChatSummaryViewModel = koinViewModel(key = "summary-${viewModel.chatId}")
    val summaryAvailable by summary.available.collectAsStateWithLifecycle()
    val summaryMe = stringResource(Res.string.summary_me)
    val summaryTitle = state.info?.let { chatTitle(it.id, it.title) }
        ?: stringResource(Res.string.conversation_fallback_title)
    // Návrhy odpovědi. Vlastní ViewModel jako shrnutí a klíčovaný chatem ze stejného důvodu.
    val replies: ReplySuggestionsViewModel = koinViewModel(key = "replies-${viewModel.chatId}")
    val repliesAvailable by replies.available.collectAsStateWithLifecycle()
    val startSummary = {
        // Zdroj se čte až v korutině, PO dotažení historie — a ze stateHolderu, ne z `state`
        // zachyceného při skládání: ten je z doby před dotažením a chyběly by v něm stránky.
        summary.summarize(summaryTitle, summaryMe) {
            viewModel.loadForSummary(SUMMARY_RECENT_MESSAGES)
            val loaded = stateHolder.value
            summarySource(loaded.messages, loaded.unreadBoundaryId)
        }
    }
    // Hledané slovo pro bubliny. Odvozené, aby text bublin překreslila jen změna dotazu, ne každá
    // změna stavu konverzace. Dotaz, ke kterému patří výsledky, ne rozepsaný.
    val searchHighlight = remember {
        derivedStateOf { stateHolder.value.search?.resultsQuery?.takeIf { it.isNotBlank() } }
    }
    // Čte se až v bublině hlasovky, takže změna pozice nepřekresluje celou obrazovku.
    val voicePlayback = viewModel.voicePlayback.collectAsStateWithLifecycle()
    val voiceSpeed = viewModel.voiceSpeed.collectAsStateWithLifecycle()
    val transcribeContext = viewModel.transcribeContext.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    // Hlasovka a kolečko: stav, gesto, oprávnění a kamera — viz `ConversationRecording`.
    val recording = rememberConversationRecording(viewModel)
    // Nový náhled (i hotový po mezistavu) začíná potichu, jako kolečko v bublině.
    val videoNotePlayback = remember(recording.videoNotePreview) { VideoNotePreviewPlayback() }

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

    val chatBar by viewModel.chatBar.collectAsStateWithLifecycle()
    val videoChat by viewModel.videoChat.collectAsStateWithLifecycle()
    val joinRequests by viewModel.joinRequests.collectAsStateWithLifecycle()
    // ✕ schová pruh do té doby, než přibude další žádost — pamatuje se počet, u kterého se
    // schoval. Napořád by správce o nové žádosti nevěděl; při každém otevření chatu by zase
    // otravoval tím, co už viděl.
    var joinRequestsHiddenAt by rememberSaveable(viewModel.chatId) { mutableIntStateOf(0) }
    val spamBar = chatBar as? SpamBar
    var confirmSharePhone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(chatBar) { if (chatBar !is SharePhoneBar) confirmSharePhone = false }
    if (confirmSharePhone) {
        ContactSharePhoneDialog(
            name = state.info?.title.orEmpty(),
            onConfirm = {
                confirmSharePhone = false
                viewModel.sharePhoneNumber()
            },
            onDismiss = { confirmSharePhone = false },
        )
    }
    /** Otevřený sheet z pruhu proti spamu; `null` = zavřený. */
    var spamBarAction by rememberSaveable { mutableStateOf<SpamBarAction?>(null) }



    // Pruh mezitím zmizel (přidán kontakt, zavřeno jinde) — sheet nemá čeho se týkat.
    LaunchedEffect(spamBar) { if (spamBar == null) spamBarAction = null }
    val openSpamBar = spamBar
    val openSpamAction = spamBarAction
    if (openSpamBar != null && openSpamAction != null) {
        SpamBarSheet(
            action = openSpamAction,
            bar = openSpamBar,
            title = state.info?.let { chatTitle(it.id, it.title) }.orEmpty(),
            onConfirm = { cleanup ->
                spamBarAction = null
                viewModel.resolveSpamBar(cleanup)
            },
            onDismiss = { spamBarAction = null },
        )
    }
    // Co je otevřené nad konverzací po ťuknutí v obrazovce (výběry, dialogy), viz `ConversationSheets`.
    val sheets = rememberConversationSheets()


    // Paměť toho, co už je v galerii vybrané. Patří obrazovce: panel se zavřením zahodí, takže
    // by se při dalším otevření dala tatáž fotka vybrat podruhé.
    val mediaSelection = rememberEmbeddedMediaSelection()

    /**
     * Rozepsaný text vstupního pole. Drží se tady, ne v poli: výběr emoji do něj vkládá na pozici
     * kurzoru a maže z něj, zatímco pole samo je pod sheetem.
     *
     * `TextFieldValue`, ne `String`: u vráceného konceptu i u vloženého emoji je potřeba nastavit
     * kurzor. S holým řetězcem by zůstal na začátku textu. Ruční formátování nese [ComposerDraft]
     * vedle textu a posouvá ho s každou změnou, která jde přes `draft = …`.
     */
    val composer = rememberComposerDraft()
    var draft by composer
    // Nápovědy gest napoprvé. Podmínky se čtou až v `snapshotFlow`, obrazovka se kvůli nim neskládá.
    val gestureHints = rememberConversationGestureHints(viewModel, stateHolder) {
        sheets.stickerPicker || sheets.mediaPicker || sheets.locationPicker || sheets.livePeriod || sheets.effectPicker ||
            sheets.scheduleTarget != null || sheets.previewAttachmentIndex != null || recording.isRecording ||
            recording.voicePreview != null || recording.videoNotePreview != null ||
            (stateHolder.value.info?.sensitive == true && !sensitiveAccepted)
    }

    // Komu se má odpovědět, rozhoduje čisté pravidlo nad stavem (testy `ReplySuggestionsTest`).
    val replyTarget = replySuggestionTarget(
        messages = state.messages,
        draft = draft.text,
        editing = state.editing,
        canWrite = state.canWrite,
    )
    val summaryTitleForReply = state.info?.let { chatTitle(it.id, it.title) }
    // Během letu odeslané zprávy ne: pole je už prázdné, ale odeslaná zpráva ještě nedorazila,
    // takže by poslední byla zase ta příchozí — model by se rozjel uprostřed animace a hned
    // nato se zrušil.
    val sending = state.flight != null
    LaunchedEffect(replyTarget, repliesAvailable, summaryTitleForReply, sending) {
        val title = summaryTitleForReply
        if (repliesAvailable && replyTarget != null && title != null && !sending) {
            replies.suggest(replyTarget, title, state.messages, summaryMe)
        } else {
            replies.clear()
        }
    }


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

    ConversationSnackbars(state, viewModel, snackbarHostState)

    ConversationPaging(listState, viewModel, reachedEnd = state.reachedEnd)


    /**
     * Změřená výška pruhů nad seznamem (proti spamu a s připnutou zprávou); nula, když žádný
     * není. Pruhy plavou nad seznamem (nejsou v `topBar`), takže jejich výšku Scaffold nezná
     * a měří se ručně.
     */
    var pinnedHeight by remember { mutableStateOf(0.dp) }

    /** Výška pruhů BEZ řádku docku živé polohy — od jejich spodku se měří, kolik ze zprávy zajelo. */
    var barsHeight by remember { mutableStateOf(0.dp) }

    /**
     * Rozbalený dock živé polohy: mapa s ovládáním nad konverzací. Závoj pod ní NENÍ — psát
     * a scrollovat jde dál, kouzlo je právě v tom, že mapa zůstane na očích při psaní.
     */
    var dockExpanded by rememberSaveable { mutableStateOf(false) }

    /** Kde byla zpráva, nad kterou se otevírá nabídka — hlásí ji zpráva při dlouhém stisku. */
    var menuAnchor by remember { mutableStateOf(MenuAnchor(bounds = { null }, maxWidth = { null })) }

    /** Klíč zprávy, kterou právě kreslí nabídka; v seznamu po ní zůstane prázdné místo. */
    val menuKey = remember { mutableStateOf<Long?>(null) }

    /** Horní hrana vstupního pole v okně; nabídka nad zprávou pod ni nesmí. */
    val contentBottom = remember { mutableStateOf<Float?>(null) }
    // Plave-li nad polem napovídání (příkazy, zmínky, emoji, inline bot), schová se FAB slot.
    var suggestionsFloating by remember { mutableStateOf(false) }

    /** Horní hrana seznamu (spodek hlavičky) v okně; nabídka nad zprávou nad ni nesmí. */
    val contentTop = remember { mutableStateOf<Float?>(null) }

    ConversationEvents(state, viewModel, navigation)
    // Pro lambdy v `itemScope`, která vzniká jednou: volají vždycky aktuální navigaci.
    val currentNavigation by rememberUpdatedState(navigation)


    /*
     * Zpráva, na jejíž tvář se naposledy ťuklo: jen ta nese sdílený prvek do profilu. Přežije
     * i návrat z profilu (a zabití procesu), aby tvář měla kam doletět zpátky.
     */
    val profileFlight = rememberSaveable { mutableStateOf<Long?>(null) }
    val saveAs = rememberSaveAs()
    val currentSaveAs by rememberUpdatedState(saveAs)
    // Příkaz z textu se pošle jen tam, kam se smí psát; jinak zůstane obyčejným textem.
    val canSendCommands = remember(stateHolder) { derivedStateOf { stateHolder.value.canWrite } }
    /*
     * Kolečko se zvukem hraje přehrávač obrazovky, ne položky (viz [SoundingVideoNote]):
     * odscrollované hraje dál v docku. Zpráva se drží i chvíli, kdy v seznamu není (výměna okna).
     */
    val soundingNoteId = state.soundingVideoNote
    val soundingNoteMedia = remember(soundingNoteId) { arrayOfNulls<MessageMedia.VideoNote>(1) }
    (state.messages.firstOrNull { it.id == soundingNoteId }?.media as? MessageMedia.VideoNote)
        ?.let { soundingNoteMedia[0] = it }
    val soundingVideoNote = rememberUpdatedState(
        rememberSoundingVideoNote(
            chatId = viewModel.chatId,
            messageId = soundingNoteId,
            note = soundingNoteMedia[0],
            startMs = { stateHolder.value.soundingVideoNoteFromMs },
            onEnded = { viewModel.onAction(it, MessageAction.VideoNoteSoundEnded) },
        ),
    )
    val itemScope = remember(viewModel) {
        ScreenItemScope(
            viewModel = viewModel,
            state = stateHolder,
            voicePlayback = voicePlayback,
            voiceSpeed = voiceSpeed,
            transcribeContext = transcribeContext,
            contentTop = contentTop,
            onMenuAnchor = { menuAnchor = it },
            menuKey = menuKey,
            timeReveal = { timeReveal.value },
            onOpenMedia = { currentNavigation.onOpenMedia(it) },
            onOpenPollMedia = { messageId, index -> currentNavigation.onOpenPollMedia(messageId, index) },
            onOpenArticleMedia = { messageId, index -> currentNavigation.onOpenArticleMedia(messageId, index) },
            onOpenStickerSet = { sheets.openStickerSetId = it },
            onSearch = { currentNavigation.onSearch(it) },
            // Výběr umístění je aktivita systému, takže ho má obrazovka; ViewModel jen stáhne.
            onSaveFileAs = { messageId -> currentSaveAs?.let { viewModel.saveDocumentAs(messageId, it) } },
            jumpTransition = jumpTransition,
            searchHighlight = searchHighlight,
            profileFlight = profileFlight,
            canSendCommands = canSendCommands,
            soundingVideoNote = soundingVideoNote,
            summary = summary,
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
    val items = rememberConversationItems(state)

    // Klíč položky není u odeslaných zpráv jejich id (viz `ConversationState.itemKeys`), takže
    // co se v seznamu hledá podle klíče, musí mít vlastní mapu.
    val byKey = remember(state.messages, state.itemKeys) { state.messages.associateBy(state::keyOf) }

    /*
     * Datum nejvyšší viditelné zprávy, pro plovoucí štítek při scrollu.
     *
     * Při `reverseLayout` je nahoře POSLEDNÍ z viditelných položek. Bere se klíč, ne index —
     * ze stejného důvodu jako u hlášení přečtení. Položky bez číselného klíče (reklama, řádek
     * načítání) se přeskočí, protože k nim žádná zpráva není.
     *
     * **Počítá se jen to, co leží pod horním odsazením**, tedy pod pruhem připnutých zpráv.
     * Položky pod pruhem (a pod hlavičkou) jsou pro seznam „viditelné", ale čtenář je nevidí —
     * a pruh připnutých podle tohohle klíče volí zprávu. Skok z pruhu posadí připnutou těsně
     * pod něj (`ScrollAnchor.Top`); dokud se počítala i předchozí připnutá schovaná nad ní,
     * přeskočil pruh o jednu (3 → 1 místo 3 → 2). Offset roste v obráceném rozvržení odspodu,
     * viz `scrollToAnchor`.
     */
    val topVisibleKey by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val contentTop = info.viewportEndOffset - info.afterContentPadding
            info.visibleItemsInfo.lastOrNull { it.key is Long && it.offset < contentTop }?.key as? Long
        }
    }

    /*
     * Den horní viditelné zprávy pro plovoucí datum. Odvozuje se jen ZAČÁTEK DNE, ne klíč
     * zprávy: `topVisibleKey` se mění s každou zprávou, která nahoře přejede, a čtený přímo
     * tady přeskládal celou obrazovku (hlavičku, pole, pruhy) zhruba každý osmý snímek scrollu
     * (změřeno Perfettem). Začátek dne se změní jen při přechodu na jiný den.
     */
    val visibleDayStart by remember(listState, byKey) {
        derivedStateOf { topVisibleKey?.let { byKey[it]?.timestamp?.startOfLocalDay() } }
    }
    val visibleDay = visibleDayStart?.asDayLabel()

    /*
     * Dock živé polohy (`LiveLocationDockBar`): kolik ze zprávy s každou živou polohou zajelo pod
     * pruhy nebo pod vstupní pole (`dockProgress`). Měří se od spodku pruhů BEZ řádku docku —
     * s ním by si řádek sám ukusoval zprávu a z plynulého přechodu by byl skok.
     *
     * Zpráva, která v rozvržení není (odscrollovaná, mimo načtené okno), je v docku celá. Bez
     * rozvržení (první snímek) se nedokuje nic, jinak by řádek při otevření chatu problikl.
     */
    val dockDensity = LocalDensity.current
    val dockSource by rememberUpdatedState(state)
    val dockTargets by remember(listState) {
        derivedStateOf {
            val s = dockSource
            val info = listState.layoutInfo
            if (s.liveLocations.isEmpty() || info.visibleItemsInfo.isEmpty()) return@derivedStateOf emptyMap<Long, Float>()
            // 8 dp je horní odsazení seznamu nad pruhy (`contentPadding` níž).
            val top = info.viewportEndOffset - with(dockDensity) { (8.dp + barsHeight).toPx() }
            s.liveLocations.associate { message ->
                val key = s.keyOf(message)
                val item = info.visibleItemsInfo.firstOrNull { it.key == key }
                message.id to (item?.let { dockProgress(it.offset, it.size, top, 0f) } ?: 1f)
            }
        }
    }
    // Kompozice čte jen to, KTERÉ polohy jsou v docku; průběh se čte až při rozmístění řádku.
    val dockedIds by remember(listState) {
        derivedStateOf(structuralEqualityPolicy()) { dockTargets.filterValues { it > DOCK_EPSILON }.keys }
    }
    // Při zasouvání řádku (průběh míří k nule) se dál kreslí poslední polohy, ne nic.
    var lastDockedIds by remember { mutableStateOf(emptySet<Long>()) }
    LaunchedEffect(dockedIds) { if (dockedIds.isNotEmpty()) lastDockedIds = dockedIds }
    val shownDocked = (dockedIds.ifEmpty { lastDockedIds }).let { ids -> state.liveLocations.filter { it.id in ids } }
    // Zpráva s polohou zpátky na obrazovce (nebo sdílení skončilo): dock se sbalí i zmizí.
    LaunchedEffect(dockedIds.isEmpty()) { if (dockedIds.isEmpty()) dockExpanded = false }

    val dockReveal = remember { Animatable(0f) }
    val dockSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(dockReveal) {
        snapshotFlow { dockTargets.values.maxOrNull() ?: 0f }.collectLatest { target ->
            // Tah prstem vede řádek přímo; skok (okno v historii, nová zpráva, rozbalení) se dojede.
            if (abs(target - dockReveal.value) > DOCK_JUMP) dockReveal.animateTo(target, dockSpec) else dockReveal.snapTo(target)
        }
    }

    /*
     * Mini přehrávač v docku (`PlayerDockBar`): hrající hlasovka, skladba nebo kolečko se zvukem,
     * jejichž zpráva odjela z obrazovky. Je ve vlastních funkcích ([rememberPlayerDock],
     * [ConversationPlayerDock]), ne tady: s ním uvnitř měla tahle funkce v dexu 275 registrů
     * a otevření každého chatu padalo na `$composer` = null (viz CLAUDE.md, „Gotchas").
     */
    val playerDock = rememberPlayerDock(
        viewModel = viewModel,
        state = state,
        voicePlayback = voicePlayback,
        soundingVideoNote = soundingVideoNote,
        listState = listState,
        barsHeight = { barsHeight },
        spec = dockSpec,
    )

    // Štítek se drží i po zmizení data, aby při odeznívání nezůstala prázdná pilulka.
    var floatingDay by remember { mutableStateOf("") }
    LaunchedEffect(visibleDay) {
        if (visibleDay != null) floatingDay = visibleDay
    }

    ConversationJumps(state, items, listState, jumpTransition, viewModel)

    /*
     * Tlačítko „dolů" i bez nových zpráv.
     *
     * Kdo odroluje do historie (nebo tam skočí po citaci), neměl jak se vrátit jinak než rolováním.
     * Ukáže se až o kus výš, ne při prvním pixelu — u spodní hrany by jen blikalo pod prstem.
     */
    val farFromBottom by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex >= SCROLL_DOWN_THRESHOLD_ITEMS }
    }
    val measuredSizes = rememberMeasuredSizes(listState)

    val pendingNewMessages = rememberNewMessages(listState, state)

    ConversationViewReporting(listState, stateHolder, viewModel)

    // Nabídka nad zprávou je vrstva NAD Scaffoldem (viz `MessageMenuLayer`), proto obal.
    // Scaffold je zároveň to, co její závoj rozostří (`hazeSource`).
    val hazeState = rememberHazeState()
    /*
     * Zdroj pro mléčné sklo je JEN seznam zpráv (viz `glassStyle`). Kdyby to byl `hazeState`
     * výš, rozostřovaly by se plochy samy sebou: pruh připnutých by nasál plovoucí datum a pás
     * pod polem sám sebe.
     */
    val listHaze = rememberHazeState()
    // Plocha, do které se v překryvu ořezává sdílený prvek letící sem z prohlížeče médií.
    // Tvary, pod které zajede, hlásí vstupní pole — proto už tady, nad Scaffoldem.
    val listClip = rememberSharedElementClip()
    Box(modifier) {
    // Zpět hledání zavře, z chatu se odchází až dalším. `NavigationBackHandler`, ne
    // `BackHandler` — ten by zabil predictive back.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = state.search != null,
        onBackCompleted = viewModel::closeSearch,
    )
    // Rozbalený dock živé polohy zavře zpět dřív, než se odejde z chatu.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = dockExpanded,
        onBackCompleted = { dockExpanded = false },
    )
    // Zpět ukončí výběr, teprve další odejde z chatu — jako u hledání.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = state.selection != null,
        onBackCompleted = viewModel::closeSelection,
    )
    // Otevřený seznam příkazů zavře zpět dřív, než se odejde z chatu.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = state.commandsOpen,
        onBackCompleted = viewModel::closeCommands,
    )
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = sheets.previewAttachmentIndex != null,
        onBackCompleted = { sheets.previewAttachmentIndex = null },
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
        topBar = {
            ConversationTopBar(
                state = state,
                viewModel = viewModel,
                navigation = navigation,
                chatHidden = chatHidden,
                onSummary = startSummary.takeIf { summaryAvailable && state.threadRootId == null },
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
            ConversationBottomBar(
                state = state,
                viewModel = viewModel,
                navigation = navigation,
                composer = composer,
                sheets = sheets,
                recording = recording,
                videoNotePlayback = videoNotePlayback,
                voicePlayback = voicePlayback,
                replies = replies,
                gestureHints = gestureHints,
                mediaSelection = mediaSelection,
                chatHidden = chatHidden,
                listHaze = listHaze,
                listClip = listClip,
                onContentBottom = { contentBottom.value = it },
                onSuggestionsFloating = { suggestionsFloating = it },
            )
        },
        // Tlačítko dolů ve FAB slotu: Scaffold ho drží nad polem a snackbar pak nad ním.
        // Uprostřed, ne vpravo: vpravo by viselo přímo nad tlačítkem odeslání a mikrofonu
        // a ťuknutí o kousek vedle by místo skoku dolů spustilo nahrávání.
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            ConversationFloatingButtons(
                state = state,
                viewModel = viewModel,
                suggestionsFloating = suggestionsFloating,
                farFromBottom = farFromBottom,
                newMessages = pendingNewMessages,
                onScrollDown = {
                    if (state.reachedEnd) {
                        scope.launch {
                            listState.animateScrollToBottom { index ->
                                items.getOrNull(index)?.let { measuredSizes[it.key] }
                            }
                        }
                    } else {
                        viewModel.jumpToLatest()
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        // Nabídka nad zprávou se kreslí až v bublině — viz `MessageMenuOverlay`.

        ConversationDialogs(
            state = state,
            viewModel = viewModel,
            sheets = sheets,
            composer = composer,
            mediaSelection = mediaSelection,
            summary = summary,
            startSummary = startSummary,
        )


        val layoutDirection = LocalLayoutDirection.current
        // Pruh připnutých a vstupní pole leží PŘES seznam — fotka letící pod ně se o ně ořízne.
        val clipDensity = LocalDensity.current
        val obscuredBottom = padding.calculateBottomPadding()
        SideEffect {
            with(clipDensity) {
                listClip.obscuredTop = pinnedHeight.toPx()
                listClip.obscuredBottom = obscuredBottom.toPx()
            }
        }
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
                /*
                 * Omezený celý chat: místo zpráv jen důvod od serveru. Stojí PŘED načítáním —
                 * historie se sice načte, ale ukazovat se nesmí, a točící se indikátor by
                 * sliboval, že přijde.
                 */
                state.info?.restriction != null -> IntroEmptyState(
                    icon = Res.drawable.block_24dp,
                    title = stringResource(Res.string.conversation_chat_restricted),
                    text = state.info?.restriction.orEmpty(),
                    modifier = Modifier.fillMaxSize(),
                )

                // Citlivý chat: napřed varování, obsah až po výslovném „přesto zobrazit".
                sensitiveWarning -> IntroEmptyState(
                    icon = Res.drawable.visibility_off_24dp,
                    title = stringResource(Res.string.conversation_chat_sensitive_title),
                    text = stringResource(Res.string.conversation_chat_sensitive_text),
                    modifier = Modifier.fillMaxSize(),
                    action = {
                        FilledTonalButton(onClick = { sensitiveAccepted = true }) {
                            Text(stringResource(Res.string.conversation_chat_sensitive_show))
                        }
                    },
                )

                state.messages.isEmpty() && state.loading ->
                    LoadingIndicator(Modifier.align(Alignment.Center))

                showsBotIntro(state) -> {
                    val shownConnection = rememberShownConnection(connection)
                    BotIntroPage(
                        intro = state.botIntro ?: return@Box,
                        chatId = viewModel.chatId,
                        title = state.info?.title.orEmpty(),
                        avatarFileId = state.info?.avatarFileId,
                        connection = shownConnection,
                        connectionLabel = shownConnection?.label()?.let { stringResource(it) },
                        online = state.presence is Presence.Online,
                        onAction = { itemScope.onAction(0L, it) },
                    )
                }

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
                 * rozhoduje až po delším a výrazně vodorovném pohybu (`detectHorizontalSwipe`).
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
                        // Kam ořezává seznam, tam se ořezává i fotka letící z prohlížeče zpátky
                        // (viz `SharedElementClip`) — jinak by na konci letu ležela přes hlavičku.
                        .sharedElementClipArea(listClip)
                        .hazeSource(listHaze)
                        .pointerInput(revealMax) {
                            detectHorizontalSwipe(
                                direction = SwipeDirection.Left,
                                onEnd = {
                                    gestureHints.used(GestureHint.RevealTimes)
                                    scope.launch { timeReveal.animateTo(0f, revealSpec) }
                                },
                            ) { delta ->
                                scope.launch {
                                    // Jen doleva a jen po šířku sloupce s časy; dál už není co
                                    // odkrývat a gumové tahání by jen mátlo.
                                    timeReveal.snapTo((timeReveal.value - delta).coerceIn(0f, revealMax))
                                }
                            }
                        },
                ) {
                  CompositionLocalProvider(LocalSharedElementClip provides listClip) {
                  // Na široké ploše zprávy ve sloupci uprostřed; scroll zůstává přes celou plochu.
                  ConversationColumn { inset ->
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
                        start = 12.dp + inset,
                        end = 12.dp + inset,
                        top = 8.dp + pinnedHeight,
                        bottom = 8.dp + padding.calculateBottomPadding(),
                    ),
                    // Rozestup UVNITŘ skupiny zpráv od jednoho odesílatele. Mezera mezi skupinami
                    // se přidává až v bublině — tady na to není informace o sousedech.
                    verticalArrangement = Arrangement.spacedBy(MESSAGE_SPACING),
                ) {
                    items(items, key = { it.key }, contentType = { it.contentType }) { item ->
                        ConversationItemContent(item, itemScope)
                    }
                }
                  }
                  }
                  // Nápověda k odkrývání časů ukazuje k pravému okraji, odkud časy přijedou.
                  RevealTimesHintAnchor(gestureHints, Modifier.align(Alignment.CenterEnd))
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

            val density = LocalDensity.current
            /*
             * Pruh proti spamu a připnuté zprávy plavou nad seznamem spolu a měří se spolu: seznam
             * se odsazuje o výšku obou. Spam stojí nahoře — je důležitější než připnutá zpráva.
             */
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .onSizeChanged { pinnedHeight = with(density) { it.height.toDp() } },
            ) {
                Column(Modifier.onSizeChanged { barsHeight = with(density) { it.height.toDp() } }) {
                    // Při odjezdu se musí dál kreslit poslední pruh, ne nic (stejně jako u připnutých).
                    // Při odjezdu se kreslí poslední pruh — stav je v tu chvíli už prázdný.
                    val lastChatBar = remember { arrayOf<ChatBar?>(null) }
                    if (chatBar != null) lastChatBar[0] = chatBar
                    AnimatedVisibility(
                        visible = chatBar != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        when (val bar = lastChatBar[0]) {
                            is SpamBar -> SpamBarCard(
                                bar = bar,
                                onBlock = { spamBarAction = SpamBarAction.Block },
                                onReport = { spamBarAction = SpamBarAction.Report },
                                onAddContact = viewModel::addSpamBarContact,
                                onUnarchive = viewModel::unarchiveFromSpamBar,
                                onDismiss = viewModel::dismissChatBar,
                            )
                            SharePhoneBar, InviteMembersBar, is JoinRequestBar -> ChatNoticeBarCard(
                                bar = bar,
                                onSharePhone = { confirmSharePhone = true },
                                onInviteMembers = navigation.onAddMembers,
                                onDismiss = viewModel::dismissChatBar,
                            )
                            null -> Unit
                        }
                    }
                    // Žádosti o vstup hned pod spamem: čekají na rozhodnutí, jako on.
                    val shownJoinRequests = joinRequests?.takeIf { it.count > joinRequestsHiddenAt }
                    val lastJoinRequests = remember { arrayOf<PendingJoinRequests?>(null) }
                    if (shownJoinRequests != null) lastJoinRequests[0] = shownJoinRequests
                    AnimatedVisibility(
                        visible = shownJoinRequests != null && !chatHidden,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        lastJoinRequests[0]?.let { requests ->
                            JoinRequestsBar(
                                requests = requests,
                                onClick = navigation.onOpenJoinRequests,
                                onHide = { joinRequestsHiddenAt = requests.count },
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
                    }
                    // Videochat mezi spamem a připnutými: o dění v chatu, ne o obsahu.
                    val lastVideoChat = remember { arrayOf<VideoChatInfo?>(null) }
                    if (videoChat != null) lastVideoChat[0] = videoChat
                    AnimatedVisibility(
                        visible = videoChat != null && !chatHidden,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        lastVideoChat[0]?.let { info ->
                            VideoChatBar(
                                info = info,
                                onClick = viewModel::explainVideoChat,
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
                    }
                    AnimatedVisibility(
                        // Omezený chat: připnutá zpráva by ukázala obsah, který se ukázat nesmí.
                        visible = state.pinned.isNotEmpty() && !chatHidden,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
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
                            onShowAll = { sheets.allPinned = true },
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
                }

                /*
                 * Dock živé polohy: poslední řádek pod pruhy, vysouvá se tím, jak zpráva s polohou
                 * odjíždí z obrazovky. Ve vlákně komentářů polohy nejsou (`liveLocations` prázdné).
                 */
                if (!chatHidden && shownDocked.isNotEmpty()) {
                    LiveLocationDockBar(
                        locations = shownDocked,
                        reveal = { dockReveal.value },
                        expanded = dockExpanded,
                        onToggle = { dockExpanded = !dockExpanded },
                        chatTitle = state.info?.title.orEmpty(),
                        onOpenMap = { message ->
                            (message.media as? MessageMedia.Location)?.let {
                                viewModel.onAction(message.id, MessageAction.OpenLink(it.mapUrl))
                            }
                        },
                        onShowMessage = { message ->
                            dockExpanded = false
                            viewModel.jumpToMessage(message.id)
                        },
                        onStop = { message -> viewModel.onAction(message.id, MessageAction.StopLiveLocation) },
                        onProximityAlert = { message -> viewModel.onAction(message.id, MessageAction.SetProximityAlert) },
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

                // Skladbu z jiného chatu ukazuje i omezený chat — neprozradí z něj nic a zastavit ji jinde nejde.
                ConversationPlayerDock(
                    dock = playerDock,
                    chatHidden = chatHidden,
                    viewModel = viewModel,
                    voicePlayback = voicePlayback,
                    soundingVideoNote = soundingVideoNote,
                    onOpenChat = navigation.onOpenChat,
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

            // Akce u zprávy (ikonka u termínu, účtu, zásilky); stav si čte sám.
            MessageContextHost(viewModel)


            // Nad seznamem, pod vstupním polem: odeslání i koš zůstávají ovladatelné.
            VideoNoteRecordingOverlay(
                visible = recording.isVideoRecording,
                camera = recording.camera,
                recording = { recording.video() },
                modifier = Modifier.fillMaxSize().padding(bottom = obscuredBottom),
                preview = recording.videoNotePreview,
                playback = videoNotePlayback,
            )
            // Zámek nad tlačítkem je tady, ne v liště — sklo pod polem by ho ořízlo.
            RecordLockHost(
                visible = recording.isRecording && recording.gesture.held && !recording.gesture.locked,
                gesture = recording.gesture,
                modifier = Modifier.fillMaxSize().padding(bottom = obscuredBottom),
            )

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

    StagedAttachmentPreviewHost(
        state = state,
        viewModel = viewModel,
        index = sheets.previewAttachmentIndex,
        onIndexChange = { sheets.previewAttachmentIndex = it },
    )
    }
}

/** O kolik položek výš od spodku se objeví tlačítko „dolů". */
private const val SCROLL_DOWN_THRESHOLD_ITEMS = 3

/**
 * Bloky seznamu konverzace (viz [conversationItems]). Vlastní funkce, ne kus [ConversationScreen]:
 * ta je v dexu na hraně registrů (viz CLAUDE.md, „ConversationScreen je v dexu na hraně").
 */
@Composable
private fun rememberConversationItems(state: ConversationState): List<ConversationItem> {
    // Spotřebovaná mizející média: v seznamu jsou oznámením o vypršení hned, ne až po TDLib.
    val expiredMedia by koinInject<SelfDestructTimers>().consumedIds.collectAsState()
    return remember(
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
        state.noticeMenu,
        state.typing,
        // Jen KLÍČE výběru: práva k němu chodí po jednom a seznam bloků se jimi nemění.
        state.selection?.keys,
        expiredMedia,
    ) { conversationItems(state, expiredMedia) }
}

/** Kolik rozborů textu zpráv si obrazovka pamatuje, než mezipaměť zahodí. */
internal const val CONTEXT_CACHE_LIMIT = 500
