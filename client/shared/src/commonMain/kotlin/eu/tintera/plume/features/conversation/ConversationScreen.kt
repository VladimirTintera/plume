package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.adaptive.ContentWidth
import eu.tintera.plume.core.adaptive.centeredColumn
import androidx.compose.ui.input.key.key
import eu.tintera.plume.resources.saved_topic_read_only
import eu.tintera.plume.resources.saved_topic_open_chat
import eu.tintera.plume.resources.service_upgraded_open_new
import eu.tintera.plume.core.ui.savedTopicTitle
import eu.tintera.plume.core.ui.SavedTopicAvatar
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.SavedTopicKind
import androidx.compose.runtime.DisposableEffect
import eu.tintera.plume.core.ui.resolveNow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import eu.tintera.plume.core.camera.rememberVideoNoteCamera
import eu.tintera.plume.core.camera.rememberVideoNotePermission
import eu.tintera.plume.core.settings.RecordMode
import eu.tintera.plume.resources.conversation_camera_permission
import eu.tintera.plume.core.ui.SwipeDirection
import eu.tintera.plume.core.ui.detectHorizontalSwipe
import androidx.compose.ui.text.intl.Locale
import eu.tintera.plume.resources.conversation_bot_start
import androidx.compose.ui.composed
import eu.tintera.plume.core.ui.AutoDeleteBadge
import eu.tintera.plume.core.telegram.VideoChatInfo
import eu.tintera.plume.core.telegram.PendingJoinRequests
import eu.tintera.plume.core.telegram.AppScreen
import eu.tintera.plume.core.telegram.InternalLink
import eu.tintera.plume.core.ui.SecretChatLock
import eu.tintera.plume.core.ui.SecureScreen
import eu.tintera.plume.core.ui.VerificationBadge
import eu.tintera.plume.core.ui.EmojiStatusBadge
import eu.tintera.plume.core.telegram.PollComposerMode
import eu.tintera.plume.core.telegram.ReplyKeyboardButtonKind
import eu.tintera.plume.core.telegram.ChatReplyMarkup
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.hazeBlur
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.sharedElementClipArea
import eu.tintera.plume.core.ui.rememberSharedElementClip
import eu.tintera.plume.core.ui.LocalSharedElementClip
import eu.tintera.plume.core.ui.LocalSharedElementOcclusion
import eu.tintera.plume.core.ui.sharedElementOcclusionBand
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.FilledTonalButton
import eu.tintera.plume.core.telegram.ContentAccess
import eu.tintera.plume.core.telegram.WriteAccess
import eu.tintera.plume.core.ui.confirm
import eu.tintera.plume.core.ui.reject
import eu.tintera.plume.core.ui.tick
import eu.tintera.plume.core.ui.toggle
import eu.tintera.plume.resources.conversation_not_member
import eu.tintera.plume.resources.conversation_secret_closed
import eu.tintera.plume.resources.conversation_secret_pending
import eu.tintera.plume.resources.conversation_topic_closed
import eu.tintera.plume.resources.conversation_write_restricted
import androidx.compose.ui.draw.clip
import eu.tintera.plume.resources.profile_open
import eu.tintera.plume.features.calls.ConversationCallButtons
import eu.tintera.plume.resources.comments_title
import eu.tintera.plume.resources.summary_me
import org.koin.compose.viewmodel.koinViewModel
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.NavigationEventInfo
import eu.tintera.plume.core.ui.chatTitle
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import eu.tintera.plume.core.ui.KeyboardContent
import eu.tintera.plume.features.conversation.items.SoundingVideoNote
import eu.tintera.plume.features.conversation.items.rememberSoundingVideoNote
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import eu.tintera.plume.core.ui.avatarPolygon
import eu.tintera.plume.core.ui.rememberShownConnection
import eu.tintera.plume.core.ui.rememberConnectionIndicator
import eu.tintera.plume.core.ui.OnlineDot
import eu.tintera.plume.resources.presence_online
import eu.tintera.plume.core.ui.RING_STROKE
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import eu.tintera.plume.core.files.rememberSaveAs
import androidx.compose.material3.FabPosition
import eu.tintera.plume.core.telegram.commandSuggestions
import eu.tintera.plume.core.location.rememberDeviceLocation
import eu.tintera.plume.core.telegram.inlineQueryOf
import eu.tintera.plume.core.telegram.mentionInsertion
import eu.tintera.plume.core.telegram.mentionQueryAt
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import eu.tintera.plume.core.audio.rememberRecordPermission
import eu.tintera.plume.core.settings.GestureHint
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.UnreadMarkKind
import eu.tintera.plume.core.ui.describe
import eu.tintera.plume.core.telegram.Presence
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.ForumTopicIcon
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.asDayLabel
import eu.tintera.plume.core.ui.startOfLocalDay
import eu.tintera.plume.core.ui.chatAvatarKey
import eu.tintera.plume.core.ui.chatTitleKey
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.core.ui.embeddedMediaPickerAvailable
import eu.tintera.plume.core.ui.rememberEmbeddedMediaSelection
import eu.tintera.plume.core.ui.rememberAttachmentPicker
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.sharedNavElement
import eu.tintera.plume.core.ui.sharedNavText
import eu.tintera.plume.features.conversation.items.FloatingDayBadge
import eu.tintera.plume.features.conversation.items.ConversationItemContent
import eu.tintera.plume.features.conversation.items.DayDividerItem
import eu.tintera.plume.features.conversation.items.MESSAGE_SPACING
import eu.tintera.plume.features.conversation.items.MenuAnchor
import eu.tintera.plume.features.conversation.items.MessageAction
import eu.tintera.plume.features.conversation.items.mapUrl
import kotlin.math.abs
import eu.tintera.plume.features.conversation.items.MessageItem
import eu.tintera.plume.features.conversation.items.SPONSORED_KEY_PREFIX
import eu.tintera.plume.features.conversation.items.TIME_COLUMN_WIDTH
import eu.tintera.plume.features.conversation.items.UnreadDividerItem
import eu.tintera.plume.features.conversation.items.ConversationItem
import eu.tintera.plume.features.conversation.items.conversationItems
import eu.tintera.plume.features.conversation.items.describe
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
import eu.tintera.plume.resources.conversation_microphone_permission
import eu.tintera.plume.resources.conversation_undo
import eu.tintera.plume.resources.conversation_unpinned
import eu.tintera.plume.resources.spam_bar_contact_added
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import eu.tintera.plume.core.telegram.SpamBar
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.ChatBar
import eu.tintera.plume.core.telegram.InviteMembersBar
import eu.tintera.plume.core.telegram.JoinRequestBar
import eu.tintera.plume.core.telegram.SharePhoneBar
import eu.tintera.plume.features.report.ReportHost
import eu.tintera.plume.features.proxy.ProxyLinkHost
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    onBack: () -> Unit,
    onOpenMedia: (messageId: Long) -> Unit,
    /** Video od daného místa — čas v médiu („1:23" v textu). */
    onOpenMediaAt: (messageId: Long, seconds: Int) -> Unit = { messageId, _ -> onOpenMedia(messageId) },
    onForward: (messageIds: List<Long>) -> Unit,
    /** Příloha ankety v prohlížeči; `index` viz [MessageAction.OpenPollMedia]. */
    onOpenPollMedia: (messageId: Long, index: Int) -> Unit = { _, _ -> },
    /** Médium článku v prohlížeči; `index` viz [MessageAction.OpenArticleMedia]. */
    onOpenArticleMedia: (messageId: Long, index: Int) -> Unit = { _, _ -> },
    /** Příběh v prohlížeči — citace odpovědi na příběh, odkaz `t.me/jméno/s/N`. */
    onOpenStory: (StoryTarget) -> Unit = {},
    /**
     * Otevřít chat nad konverzací (zmínka, odkaz, komentáře); kam přesně — zpráva, vlákno,
     * téma, seznam témat —, říká [ChatTarget].
     */
    onOpenChat: (ChatTarget) -> Unit,
    /** Hashtag v textu: hledání zpráv s tagem v seznamu chatů. */
    onSearch: (query: String) -> Unit,
    /** Ťuknutí na hlavičku: profil chatu. */
    onOpenProfile: () -> Unit,
    /** Profil jiného chatu nad konverzací — odesílatel ze skupiny, na jehož tvář se ťuklo. */
    onOpenUserProfile: (chatId: Long) -> Unit,
    /**
     * Chat se z pruhu vrátil z archivu mezi chaty. Jen konverzace otevřená Z ARCHIVU s tím
     * něco dělá (přestěhuje se do hlavního seznamu); jinde se nic měnit nemusí.
     */
    onUnarchived: () -> Unit = {},
    /** Chat je smazaný nebo opuštěný (⋮ v hlavičce, pruh proti spamu) — zavřít všechno, co ho ukazuje. */
    onChatGone: () -> Unit = onBack,
    /** Sponka → Anketa (a tlačítko bota „vytvořit anketu"): formulář nové ankety nad konverzací. */
    onCreatePoll: (PollComposerRequest) -> Unit = {},
    /** Mini aplikace bota — obrazovka přes celou plochu nad konverzací. */
    onOpenWebApp: (WebAppRequest) -> Unit = {},
    /** Tlačítko bota „vyber lidi / chat": výběr nad konverzací. */
    onOpenBotShare: (BotShareRequest) -> Unit = {},
    /** Odkaz `t.me/bot?startgroup=…`: výběr skupiny, do které bota přidat. */
    onAddBotToGroup: (InternalLink.BotStartInGroup) -> Unit = {},
    /** Odkaz na obrazovku aplikace (`tg://settings/…`, `tg://newgroup`) — navigaci řeší shell. */
    onOpenScreen: (AppScreen) -> Unit = {},
    /** Tlačítko bota s inline dotazem do jiného chatu: výběr chatu. */
    onSwitchInline: (SwitchInlinePick) -> Unit = {},
    /** Pruh „Pozvat členy" nad čerstvou skupinou: výběr lidí. */
    onAddMembers: () -> Unit = {},
    /** Pruh s čekajícími žádostmi o vstup: otevře jejich seznam. */
    onOpenJoinRequests: () -> Unit = {},
    /**
     * Uložené zprávy jako seznam témat podle zdrojového chatu. `null` = nenabízet (každá jiná
     * konverzace i téma samo).
     */
    onViewAsChats: (() -> Unit)? = null,
    /**
     * Rychlý přepínač (podržení šipky zpět): vybraný chat místo téhle konverzace. `null` =
     * nenabízet (bublina konverzace).
     */
    onSwitchChat: ((ChatSummary) -> Unit)? = null,
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
    val replySuggestions by replies.suggestions.collectAsStateWithLifecycle()
    // Emoji k rozepsanému slovu. Jazyk zařízení a angličtina, jako hledání ve výběru emoji.
    val emojiWords: EmojiSuggestionsViewModel = koinViewModel(key = "emoji-words-${viewModel.chatId}")
    val emojiSuggestions by emojiWords.suggestions.collectAsStateWithLifecycle()
    val emojiLanguages = remember { listOf(Locale.current.language, "en").distinct() }
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
    // Nahrávání se mění po 50 ms; obrazovka čte jen jestli běží, čas a úrovně až pruh nahrávání.
    val voiceRecordingState = viewModel.voiceRecording.collectAsStateWithLifecycle()
    val videoRecordingState = viewModel.videoRecording.collectAsStateWithLifecycle()
    val isRecording by remember {
        derivedStateOf { voiceRecordingState.value != null || videoRecordingState.value != null }
    }
    val isVideoRecording by remember { derivedStateOf { videoRecordingState.value != null } }
    val voicePreview by viewModel.voicePreview.collectAsStateWithLifecycle()
    val videoNotePreview by viewModel.videoNotePreview.collectAsStateWithLifecycle()
    // Nový náhled (i hotový po mezistavu) začíná potichu, jako kolečko v bublině.
    val videoNotePlayback = remember(videoNotePreview) { VideoNotePreviewPlayback() }
    val recordGesture = remember { RecordGestureState() }
    val recordPermission = rememberRecordPermission()
    val videoNotePermission = rememberVideoNotePermission()
    val preferredRecordMode by viewModel.recordMode.collectAsStateWithLifecycle()
    // Kamera žije s obrazovkou, ViewModel si ji jen půjčuje (viz `CaptureSlot`).
    val videoNoteCamera = rememberVideoNoteCamera()
    DisposableEffect(videoNoteCamera) {
        videoNoteCamera?.let(viewModel::attachCamera)
        onDispose { videoNoteCamera?.let(viewModel::detachCamera) }
    }
    // Kolečko jen tam, kde ho chat dovolí A kde je čím ho natočit.
    val videoNotesAllowed = state.content.videoNotes && videoNoteCamera != null
    val recordMode = recordModeFor(preferredRecordMode, state.content.voiceNotes, videoNotesAllowed)

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
    val chatGone by viewModel.chatGone.collectAsStateWithLifecycle()
    /** Otevřený sheet z pruhu proti spamu; `null` = zavřený. */
    var spamBarAction by rememberSaveable { mutableStateOf<SpamBarAction?>(null) }

    // Chat se smazal (⋮ v hlavičce, pruh proti spamu) — konverzace nemá co ukazovat.
    LaunchedEffect(chatGone) { if (chatGone) onChatGone() }
    val unarchived by viewModel.unarchived.collectAsStateWithLifecycle()
    LaunchedEffect(unarchived) { if (unarchived) onUnarchived() }

    ReportHost(viewModel.reportFlow, snackbarHostState)
    ProxyLinkHost(viewModel.proxyLinkFlow, snackbarHostState)

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

    // Snackbar o odepnutí se skládá v efektu, kde `stringResource` volat nejde — text se proto
    // přečte tady v kompozici.
    val unpinnedMessage = stringResource(Res.string.conversation_unpinned)
    val contactAddedMessage = stringResource(Res.string.spam_bar_contact_added)
    val undoLabel = stringResource(Res.string.conversation_undo)

    // Vybrané soubory jsou už zkopírované v cache aplikace; čekají nad polem na popisek a odeslání.
    val attachmentPicker = rememberAttachmentPicker(
        onPicked = viewModel::addAttachments,
        onError = viewModel::reportError,
    )

    /*
     * Galerie se kreslí rovnou nad vstupním polem, kde ji systém umí vložit dovnitř aplikace
     * (`EmbeddedMediaPicker`); jinde zůstává systémový výběr, tedy odchod na vlastní obrazovku.
     * Rozhoduje se tady, aby o tom `AttachButton` nemusel vědět — nabídka je v obou případech
     * stejná.
     */
    val embeddedMediaPicker = embeddedMediaPickerAvailable()
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
            sheets.scheduleTarget != null || sheets.previewAttachmentIndex != null || isRecording ||
            voicePreview != null || videoNotePreview != null ||
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


    /** Poslední otevřená část výběru — uložená v nastavení, takže platí i po restartu aplikace. */
    val expressionMode by viewModel.expressionMode.collectAsStateWithLifecycle()
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
        snackbarHostState.showSnackbar(message.resolveNow())
        viewModel.dismissError()
    }

    // Snackbar běží v `snackbarScope`, ne v těle efektu. Vynulování `unpinned` změní klíč efektu,
    // ten se restartuje a zrušil by i snackbar, který se právě ukazuje — takže se neukázal nikdy.
    val snackbarScope = rememberCoroutineScope()

    // Stejná past jako u `unpinned` níž: vynulování mění klíč efektu, takže snackbar běží mimo něj.
    // I text se čte AŽ TAM: `getString` je `suspend`, a dokud se četl v těle efektu, restart po
    // vynulování ho zrušil — hláška se ukázala, jen když byl text náhodou už načtený.
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        viewModel.consumeNotice()
        snackbarScope.launch {
            val text = notice.resolveNow()
            snackbarHostState.showSnackbar(text, duration = SnackbarDuration.Short)
        }
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

    // Přidaný kontakt z karty ve zprávě: stejný vzor jako odepnutí — vynulovat hned, snackbar mimo efekt.
    LaunchedEffect(state.addedContact) {
        val userId = state.addedContact ?: return@LaunchedEffect
        viewModel.consumeAddedContact()
        snackbarScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = contactAddedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoAddContact(userId)
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

    val currentOnOpenMedia by rememberUpdatedState(onOpenMedia)
    val currentOnOpenPollMedia by rememberUpdatedState(onOpenPollMedia)
    val currentOnOpenArticleMedia by rememberUpdatedState(onOpenArticleMedia)
    val currentOnSearch by rememberUpdatedState(onSearch)
    val currentOnOpenChat by rememberUpdatedState(onOpenChat)

    // Chat ze zmínky nebo odkazu: ViewModel ho najde, navigace je věc obrazovky. Událost se
    // nuluje hned, jinak by se po otočení otevřel znovu.
    val currentOnOpenMediaAt by rememberUpdatedState(onOpenMediaAt)
    LaunchedEffect(state.openMediaAt) {
        val target = state.openMediaAt ?: return@LaunchedEffect
        viewModel.consumeOpenMediaAt()
        currentOnOpenMediaAt(target.messageId, target.seconds)
    }
    LaunchedEffect(state.openChat) {
        val target = state.openChat ?: return@LaunchedEffect
        viewModel.consumeOpenChat()
        currentOnOpenChat(target)
    }
    val currentOnOpenScreen by rememberUpdatedState(onOpenScreen)
    LaunchedEffect(state.openScreen) {
        val screen = state.openScreen ?: return@LaunchedEffect
        viewModel.consumeOpenScreen()
        currentOnOpenScreen(screen)
    }
    val currentOnOpenStory by rememberUpdatedState(onOpenStory)
    LaunchedEffect(state.openStory) {
        val story = state.openStory ?: return@LaunchedEffect
        viewModel.consumeOpenStory()
        currentOnOpenStory(story)
    }
    val currentOnAddBotToGroup by rememberUpdatedState(onAddBotToGroup)
    LaunchedEffect(state.addBotToGroup) {
        val link = state.addBotToGroup ?: return@LaunchedEffect
        viewModel.consumeAddBotToGroup()
        currentOnAddBotToGroup(link)
    }
    val currentOnSwitchInline by rememberUpdatedState(onSwitchInline)
    LaunchedEffect(state.switchInline) {
        val pick = state.switchInline ?: return@LaunchedEffect
        viewModel.consumeSwitchInline()
        currentOnSwitchInline(pick)
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
            onOpenMedia = { currentOnOpenMedia(it) },
            onOpenPollMedia = { messageId, index -> currentOnOpenPollMedia(messageId, index) },
            onOpenArticleMedia = { messageId, index -> currentOnOpenArticleMedia(messageId, index) },
            onOpenStickerSet = { sheets.openStickerSetId = it },
            onSearch = { currentOnSearch(it) },
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
    val measuredSizes = rememberMeasuredSizes(listState)

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
     *
     * Hlásí se ZMĚNA, a proto se vedle viditelných položek čtou i zprávy s nepřečtenou reakcí
     * nebo hlasem v anketě (`unreadMarkedIds`). Reakce na zprávu, která už je vidět, viditelné
     * položky nezmění — bez toho by zůstala nepřečtená a tlačítko „❤" by viselo nad zprávou,
     * na kterou se uživatel právě dívá, dokud by nezaroloval.
     */
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? Long } to state.unreadMarkedIds()
        }
            .debounce(READ_DEBOUNCE_MS)
            .collect { (keys, _) -> viewModel.markRead(keys) }
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
                    onJumpToDate = viewModel::jumpToDate.takeIf { viewModel.canJumpToDate },
                )
                return@topBar
            }
            TopAppBar(
                title = {
                    // Hlavička vede do profilu chatu — tak to znají uživatelé ze všech klientů.
                    // Uložené zprávy profil nemají (je to vlastní účet, nic k ukázání).
                    val profileLabel = stringResource(Res.string.profile_open)
                    /*
                     * Stav spojení nese avatar (zvlní se a točí), stejně jako fotka účtu
                     * v seznamu chatů — ne text pod názvem. Čtečce obrazovky ho řekne popis
                     * stavu hlavičky.
                     */
                    val shownConnection = rememberShownConnection(connection)
                    val connectionLabel = shownConnection?.label()?.let { stringResource(it) }
                    // „Online" už v podtitulu není, nese ho jen tečka — čtečce ho proto řekne
                    // popis stavu hlavičky, stejně jako výpadek spojení (ten má přednost: bez
                    // spojení je dostupnost stará hodnota).
                    val onlineLabel = stringResource(Res.string.presence_online)
                        .takeIf { state.presence is Presence.Online && state.info?.isSavedMessages == false }
                    val headerState = connectionLabel ?: onlineLabel
                    val kind = when {
                        state.info?.isChannel == true -> ChatKind.Channel
                        state.info?.isGroup == true -> ChatKind.Group
                        else -> ChatKind.Private
                    }
                    val indicator = rememberConnectionIndicator(
                        connection = shownConnection,
                        avatarSize = HEADER_AVATAR,
                        grownSize = HEADER_AVATAR_GROWN,
                        base = kind.avatarPolygon(),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                // Vlákno komentářů vlastní profil nemá; profil skupiny by vedl pryč.
                                enabled = state.info?.isSavedMessages == false && state.threadRootId == null,
                                onClickLabel = profileLabel,
                                onClick = onOpenProfile,
                            )
                            .semantics { headerState?.let { stateDescription = it } }
                            // Místo pro zvětšený avatar s prstencem: řádek ořezává (tvar vlnky
                            // stisku), takže bez rezervy by prstenec usekl vlevo i nahoře a dole.
                            .padding(horizontal = HEADER_RING_ROOM, vertical = HEADER_RING_ROOM),
                    ) {
                        // Nad profilem bota je jeho tvář velká uprostřed obrazovky a nese i tečku
                        // a výpadek spojení (`BotIntroPage`); v hlavičce by byla podruhé.
                        val headerAvatar = !showsBotIntro(state)
                        // Téma: jeho ikona místo fotky skupiny. Nic neletí — seznam témat je
                        // jiná obrazovka a fotka skupiny v ní v řádku není.
                        state.info?.topic?.let { topic ->
                            ForumTopicIcon(
                                icon = topic.icon,
                                name = topic.name,
                                isGeneral = topic.isGeneral,
                                size = HEADER_AVATAR,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        }
                        // Téma Uložených zpráv: fotka chatu, ze kterého se ukládalo (Moje poznámky
                        // záložka). Taky nic neletí, řádek v seznamu témat je jiná obrazovka.
                        state.info?.savedTopic?.let { topic ->
                            SavedTopicAvatar(topic = topic, size = HEADER_AVATAR, modifier = Modifier.padding(end = 12.dp))
                        }
                        state.info?.takeIf { it.topic == null && it.savedTopic == null }?.let { info ->
                          AnimatedVisibility(
                              visible = headerAvatar,
                              enter = fadeIn() + expandHorizontally(),
                              exit = fadeOut() + shrinkHorizontally(),
                          ) {
                            // Mezera ke jménu je součást avataru, ne řádku: schovaný avatar
                            // by jinak nechal před jménem díru.
                            Box(Modifier.padding(end = 12.dp)) {
                                Avatar(
                                    chatId = info.avatarColorId,
                                    title = info.title,
                                    avatarFileId = info.avatarFileId,
                                    size = HEADER_AVATAR,
                                    isSavedMessages = info.isSavedMessages,
                                    // Stejný tvar jako v seznamu, odkud avatar přiletěl. Při výpadku
                                    // ořezává indikátor — vlastní tvar by vlnky u skupiny schoval.
                                    shape = if (indicator.isShaping) RectangleShape else kind.avatarShape(),
                                    // Vlákno komentářů je jiná obrazovka než chat skupiny; sdílený
                                    // klíč by se přetahoval s její hlavičkou i s položkou v seznamu.
                                    modifier = Modifier
                                        .then(
                                            if (state.threadRootId == null) {
                                                Modifier.sharedNavElement(chatAvatarKey(info.id))
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .then(with(indicator) { Modifier.avatar() }),
                                )
                                with(indicator) { Spacer(Modifier.matchParentSize().ring()) }
                                // Jako v seznamu chatů. Bez spojení je dostupnost stará hodnota
                                // a tečka by přeškrtla prstenec.
                                if (shownConnection == null && state.presence is Presence.Online && !info.isSavedMessages) {
                                    OnlineDot(
                                        rim = MaterialTheme.colorScheme.surface,
                                        size = HEADER_ONLINE_DOT,
                                        modifier = Modifier.align(Alignment.BottomEnd),
                                        // Letí s avatarem ze seznamu chatů; ve vlákně ne, stejně
                                        // jako avatar.
                                        chatId = info.id.takeIf { state.threadRootId == null },
                                    )
                                }
                            }
                          }
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                // Tajný chat: zámek a zákaz snímků obrazovky. Efekt patří celé
                                // obrazovce; tady je proto, že `ConversationScreen` je v dexu na
                                // hraně registrů (viz CLAUDE.md) a hlavička je vlastní lambda.
                                val secret = state.info?.isSecret == true
                                SecureScreen(enabled = secret)
                                if (secret && !state.narrowed) SecretChatLock()
                                Text(
                                    text = when {
                                        state.threadRootId != null -> stringResource(Res.string.comments_title)
                                        state.info?.topic != null -> state.info?.topic?.name.orEmpty()
                                        state.info?.savedTopic != null -> savedTopicTitle(state.info!!.savedTopic!!)
                                        else -> state.info?.let { chatTitle(it.id, it.title) }
                                            ?: stringResource(Res.string.conversation_fallback_title)
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    // Klíč se dá jen když chat známe; než dorazí `info`, není s čím
                                    // párovat. Váha bez vyplnění: dlouhý název se zkrátí, štítek
                                    // za ním zůstane vidět.
                                    modifier = Modifier.weight(1f, fill = false).then(
                                        state.info
                                            ?.takeIf { !state.narrowed }
                                            ?.let { Modifier.sharedNavText(chatTitleKey(it.id)) }
                                            ?: Modifier,
                                    ),
                                )
                                // Podvod a falešný účet musí být vidět tam, kde se píše — ve
                                // vlákně komentářů je hlavička o skupině, ne o účtu.
                                state.info?.verification
                                    ?.takeIf { !state.narrowed }
                                    ?.let { VerificationBadge(it) }
                                state.info?.emojiStatus
                                    ?.takeIf { !state.narrowed }
                                    ?.let { EmojiStatusBadge(it) }
                                // Automatické mazání: co se tu napíše, samo zmizí. Ve vlákně
                                // komentářů by patřilo celé skupině, ne vláknu.
                                state.autoDeleteTime
                                    .takeIf { it > 0 && !state.narrowed }
                                    ?.let { AutoDeleteBadge(it) }
                            }

                            /*
                             * Podtitul: kdy byl protějšek naposledy vidět. Stav spojení tu NENÍ, ten
                             * nese avatar — a „online" taky ne, to říká tečka na avataru. Dvakrát
                             * totéž hned vedle sebe byl jen šum.
                             *
                             * Bez spojení se ale dostupnost schová: dokud spojení nestojí, je
                             * „naposledy viděn" jen stará hodnota, kterou nemá co obnovit.
                             *
                             * „Píše…" tu NENÍ: kreslí se v seznamu zpráv tam, kam zpráva naskočí
                             * (`TypingItem`), jako v Google Zprávách. Výšku hlavičky animuje
                             * `AnimatedContent`, jinak by název při objevení podtitulu poskočil.
                             */
                            // Ve vlákně komentářů a v tématu říká podtitul, ve které skupině se diskutuje.
                            val threadSubtitle = state.info
                                ?.takeIf { state.narrowed }
                                ?.let { HeaderSubtitle(chatTitle(it.id, it.title)) }
                            val subtitle = threadSubtitle ?: state.presence
                                ?.takeIf { shownConnection == null }
                                // Vlastní dostupnost v Uložených zprávách nic neříká — byl bys
                                // pořád „online", protože se na ně právě díváš.
                                ?.takeUnless { state.info?.isSavedMessages == true }
                                ?.takeUnless { it is Presence.Online }
                                ?.let { HeaderSubtitle(it.describe()) }
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    // Podržení nabídne rychlý přepínač chatů; ťuknutí je dál „zpět".
                    ChatSwitcherBackButton(chatId = viewModel.chatId, onBack = onBack, onSwitch = onSwitchChat)
                },
                actions = {
                    // Hlavní akce jsou hovor a videohovor, všechno ostatní je za ⋮.
                    ConversationCallButtons(state.info, state.callRequest, viewModel::consumeCallRequest)
                    // Prázdný chat s botem je jeho profil — není co shrnout ani hledat.
                    // Omezený chat nemá co ukázat, tedy ani co shrnout nebo hledat.
                    ConversationMenu(
                        state = state,
                        viewModel = viewModel,
                        showsContent = !showsBotIntro(state) && !chatHidden,
                        onSummary = startSummary.takeIf { summaryAvailable && state.threadRootId == null },
                        // Uložené zprávy podle zdrojového chatu: jen u celých Uložených zpráv, ne v tématu.
                        onViewAsChats = onViewAsChats.takeIf { state.info?.isSavedMessages == true && !state.narrowed },
                    )
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
            // Na široké ploše ve sloupci uprostřed, stejně jako zprávy nad ním.
            Box(
                Modifier
                    .centeredColumn(ContentWidth.Conversation)
                    .onGloballyPositioned { contentBottom.value = it.boundsInWindow().top },
            ) {
            /*
             * Lišty místo pole (Přidat se, Start, Ztlumit, věta „nejde psát") mají pod sebou
             * TENTÝŽ pás skla jako pole. Seznam zpráv pod spodní lištu zajíždí (spodek je
             * `contentPadding`, ne konec plochy), takže bez pásu prosvítaly bubliny zpod tlačítka.
             */
            val barGlass = Modifier.hazeBlur(
                input = HazeInput.Sources(listHaze),
                style = glassStyle(
                    color = MaterialTheme.colorScheme.surface,
                    alpha = GLASS_TINT_ALPHA,
                    fallbackAlpha = INPUT_SCRIM_ALPHA,
                ),
            )
            // Při výběru patří spodek akcím nad ním; rozepsaný text zůstává v konceptu.
            state.selection?.let { selection ->
                SelectionToolbar(
                    selection = selection,
                    onCopy = viewModel::copySelection,
                    onCopyWithNames = viewModel::copySelectionWithNames,
                    onCopyMarkdown = viewModel::copySelectionAsMarkdown,
                    onForward = viewModel::forwardSelection,
                    onReport = viewModel::reportSelection,
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
            // Omezený chat: do chatu, který se nedá ukázat, se ani psát nedá.
            if (chatHidden) return@Box
            if (state.info?.canJoin == true) {
                JoinBar(joining = state.joining, onJoin = viewModel::join, modifier = barGlass)
                return@Box
            }
            // Převedená základní skupina: psát se dá jen do té nové, tak tam vede.
            state.info?.upgradedToChatId?.let { upgraded ->
                JoinBar(
                    joining = false,
                    onJoin = { onOpenChat(ChatTarget(upgraded)) },
                    label = Res.string.service_upgraded_open_new,
                    modifier = barGlass,
                )
                return@Box
            }
            // Nespuštěný bot (nebo čekající parametr z odkazu): Start místo pole.
            if (showsBotStart(state)) {
                JoinBar(
                    joining = state.startingBot,
                    onJoin = {
                        haptics.confirm()
                        viewModel.startBot()
                    },
                    label = Res.string.conversation_bot_start,
                    modifier = barGlass,
                )
                return@Box
            }
            // Kde se psát nesmí, pole by skončilo chybou až po odeslání. Kanál aspoň nabídne ztlumení.
            when (state.info?.writeAccess) {
                WriteAccess.ChannelReadOnly -> {
                    MuteBar(muted = state.muted, onToggle = viewModel::toggleMute, modifier = barGlass)
                    return@Box
                }
                WriteAccess.Restricted -> {
                    ReadOnlyNote(Res.string.conversation_write_restricted, barGlass)
                    return@Box
                }
                WriteAccess.NotMember -> {
                    ReadOnlyNote(Res.string.conversation_not_member, barGlass)
                    return@Box
                }
                WriteAccess.TopicClosed -> {
                    ReadOnlyNote(Res.string.conversation_topic_closed, barGlass)
                    return@Box
                }
                WriteAccess.SecretPending -> {
                    ReadOnlyNote(Res.string.conversation_secret_pending, barGlass)
                    return@Box
                }
                WriteAccess.SecretClosed -> {
                    ReadOnlyNote(Res.string.conversation_secret_closed, barGlass)
                    return@Box
                }
                // Téma Uložených zpráv uložené odjinud: odeslat do něj TDLib nedovolí. Zdrojový
                // chat aspoň jde otevřít — tam se odpovídá; bez známého chatu jen proč.
                WriteAccess.SavedTopic -> {
                    val source = (state.info?.savedTopic?.kind as? SavedTopicKind.FromChat)?.chatId
                    if (source != null) {
                        JoinBar(
                            joining = false,
                            onJoin = { onOpenChat(ChatTarget(source)) },
                            label = Res.string.saved_topic_open_chat,
                            modifier = barGlass,
                        )
                    } else {
                        ReadOnlyNote(Res.string.saved_topic_read_only, barGlass)
                    }
                    return@Box
                }
                WriteAccess.Allowed, null -> Unit
            }
            Column {
            ReplySuggestionChips(
                suggestions = replySuggestions,
                // Návrh se jen VLOŽÍ do pole; odeslání zůstává na uživateli. `onDraftChanged`
                // musí jít taky — uloží koncept a ohlásí „píše…", jako by to napsal sám.
                onPick = { suggestion ->
                    draft = TextFieldValue(suggestion, selection = TextRange(suggestion.length))
                    viewModel.onDraftChanged(composer.composed)
                    replies.consume()
                },
            )
            // Vlastní klávesnice bota stojí NAD polem (viz `ReplyKeyboardPanel`); při úpravě
            // zprávy ne — tlačítko by poslalo novou zprávu uprostřed opravy staré.
            (state.replyMarkup as? ChatReplyMarkup.Keyboard)?.takeIf { state.editing == null }?.let { keyboard ->
                // Použitá jednorázová klávesnice se otevírá sbalená i po návratu do chatu — TDLib
                // si použití nepamatuje a klávesnice v chatu zůstává (viz `keyboardUsed`).
                val usedKeyboard by viewModel.usedKeyboardMessageId.collectAsStateWithLifecycle()
                var collapsed by rememberSaveable(keyboard.messageId) {
                    mutableStateOf(keyboard.oneTime && usedKeyboard == keyboard.messageId)
                }
                ReplyKeyboardPanel(
                    keyboard = keyboard,
                    collapsed = collapsed,
                    onToggle = { collapsed = !collapsed },
                    onPress = { row, column ->
                        haptics.confirm()
                        // Jednorázová klávesnice se po použití SCHOVÁ, nezmizí (Bot API
                        // `one_time_keyboard`) — šipkou jde vrátit. Vysvětlení u tlačítka, které
                        // Plume neumí, použití není.
                        val kind = keyboard.rows.getOrNull(row)?.getOrNull(column)?.kind
                        if (keyboard.oneTime && kind !is ReplyKeyboardButtonKind.Unsupported) collapsed = true
                        viewModel.pressReplyKeyboardButton(row, column)
                    },
                )
            }
            // Příkazy botů: napovídání podle rozepsaného „/…", nebo celý seznam z tlačítka u pole.
            val typedCommands = commandSuggestions(draft.text, state.botCommands)
            // Inline bot (`@gif kočka`): výsledky nad polem. S přílohami je text popisek a při úpravě
            // text staré zprávy — ani jedno není dotaz na bota.
            val inlineDraft = remember(draft.text, state.editing, state.attachments.isEmpty()) {
                inlineQueryOf(draft.text).takeIf { state.editing == null && state.attachments.isEmpty() }
            }
            // Zmínka (`@ja` u kurzoru): členové skupiny nad polem. Kurzor, ne celý text — zmínka se
            // píše kdekoli ve větě. S rozepsaným dotazem na inline bota se nenapovídá, to je jiné „@".
            val mentionQuery = remember(draft, inlineDraft) {
                draft.takeIf { inlineDraft == null && it.selection.collapsed }
                    ?.let { mentionQueryAt(it.text, it.selection.start) }
            }
            LaunchedEffect(mentionQuery?.query) { viewModel.mentionQuery(mentionQuery?.query) }
            // Emoji ke slovu u kurzoru. Ne vedle jiného napovídání — dotaz na inline bota a zmínka
            // mají nad polem přednost a emoji plovoucí přes jejich panel by ho zakryla.
            val emojiWord = remember(draft, inlineDraft, mentionQuery, typedCommands) {
                draft.takeIf {
                    inlineDraft == null && mentionQuery == null && typedCommands == null && it.selection.collapsed
                }?.let { emojiWordAt(it.text, it.selection.start) }
            }
            LaunchedEffect(emojiWord?.word) { emojiWords.lookup(emojiWord?.word, emojiLanguages) }
            val shownEmojis = emojiSuggestions?.takeIf { it.word == emojiWord?.word }?.emojis.orEmpty()
            // Samolepky k emoji, které je v poli samo. Jen kde se samolepka smí poslat a pole
            // je obyčejná zpráva — ne při úpravě ani s přílohou, kde by emoji byl popisek.
            val stickerEmoji = remember(draft.text, inlineDraft, state.editing, state.attachments.isEmpty(), state.content.stickers) {
                stickerSuggestionEmoji(draft.text).takeIf {
                    inlineDraft == null && state.editing == null && state.attachments.isEmpty() && state.content.stickers
                }
            }
            val shownStickers = rememberStickerSuggestions(stickerEmoji, viewModel.chatId)
            LaunchedEffect(inlineDraft) { viewModel.inlineQuery(inlineDraft) }
            // Bot, který chce polohu, ji dostane přibližnou — dotaz na oprávnění přijde až u něj.
            val inlineLocation = rememberDeviceLocation()
            val locationBot = state.inline?.bot?.takeIf { it.needLocation }?.userId
            LaunchedEffect(locationBot) {
                if (locationBot != null) inlineLocation.request(viewModel::setInlineLocation)
            }
            /*
             * Napovídání při psaní PLAVE nad polem a místo v rozvržení nezabírá (viz
             * `FloatingSuggestion`): mění se s každým písmenem a konverzace pod ním nesmí
             * poskakovat. Stojí těsně nad polem, u textu, ke kterému patří. Návrhy odpovědi
             * a klávesnice bota výš zůstávají v rozvržení — viz `ReplySuggestionChips`.
             */
            val suggestionGlass = Modifier.hazeBlur(
                input = HazeInput.Sources(listHaze),
                // Inverzní plocha, jako snackbar (viz `FloatingSuggestion`).
                style = glassStyle(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    alpha = FLOATING_SUGGESTION_TINT_ALPHA,
                    fallbackAlpha = GLASS_FALLBACK_ALPHA,
                ),
            )
            val commandsShown = state.editing == null && (typedCommands != null || state.commandsOpen)
            // Plovoucí napovídání leží tam, kde jinak stojí tlačítko dolů a zmínky — ty se
            // mezitím schovají, jinak by visely přes seznam příkazů nebo přes emoji.
            val floatingShown = (commandsShown && state.botCommands.isNotEmpty()) ||
                (mentionQuery != null && state.mentionSuggestions?.candidates.orEmpty().isNotEmpty()) ||
                (emojiWord != null && shownEmojis.isNotEmpty()) ||
                (stickerEmoji != null && shownStickers.isNotEmpty()) ||
                inlineDraft != null
            SideEffect { suggestionsFloating = floatingShown }
            Column(Modifier.floatAbove()) {
                BotCommandsPanel(
                    commands = typedCommands ?: state.botCommands.takeIf { state.commandsOpen }.orEmpty(),
                    visible = commandsShown,
                    onPick = { command ->
                        haptics.confirm()
                        // Rozepsané „/st" byl ten příkaz — pole se vyprázdní a koncept s ním.
                        if (typedCommands != null) {
                            draft = TextFieldValue()
                            viewModel.onDraftChanged(composer.composed)
                        }
                        viewModel.closeCommands()
                        viewModel.sendCommand(command.text)
                    },
                    glass = suggestionGlass,
                )
                MentionSuggestionsPanel(
                    candidates = state.mentionSuggestions?.candidates.orEmpty(),
                    visible = mentionQuery != null,
                    onPick = { candidate ->
                        val query = mentionQuery ?: return@MentionSuggestionsPanel
                        val (text, cursor) = mentionInsertion(draft.text, draft.selection.start, query, candidate)
                        draft = TextFieldValue(text, selection = TextRange(cursor))
                        viewModel.mentionPicked(candidate)
                        viewModel.onDraftChanged(composer.composed)
                    },
                    glass = suggestionGlass,
                )
                EmojiSuggestionsPanel(
                    emojis = shownEmojis,
                    visible = emojiWord != null,
                    onPick = { emoji ->
                        val word = emojiWord ?: return@EmojiSuggestionsPanel
                        haptics.confirm()
                        val (text, cursor) = emojiReplacement(draft.text, word, emoji)
                        draft = TextFieldValue(text, selection = TextRange(cursor))
                        emojiWords.picked(emoji)
                        viewModel.onDraftChanged(composer.composed)
                    },
                    glass = suggestionGlass,
                )
                StickerSuggestionsPanel(
                    stickers = shownStickers,
                    visible = stickerEmoji != null,
                    onPick = { sticker ->
                        haptics.confirm()
                        viewModel.sendSticker(sticker)
                        // Emoji v poli byla ta samolepka — pole se vyprázdní a koncept s ním.
                        draft = TextFieldValue()
                        viewModel.onDraftChanged(composer.composed)
                    },
                    glass = suggestionGlass,
                )
                InlineResultsPanel(
                    inline = state.inline,
                    visible = inlineDraft != null,
                    onPick = { result ->
                        haptics.confirm()
                        viewModel.sendInlineResult(result)
                        // Dotaz v poli byl ten výsledek — pole se vyprázdní a koncept s ním.
                        draft = TextFieldValue()
                        viewModel.onDraftChanged(composer.composed)
                    },
                    onButton = viewModel::pressInlineButton,
                    onLoadMore = viewModel::loadMoreInline,
                    glass = suggestionGlass,
                )
            }
            // Fotka letící z prohlížeče zajede pod kartu pole a tlačítko, ne jen za čáru nad nimi.
            CompositionLocalProvider(LocalSharedElementOcclusion provides listClip) {
            MessageInput(
                gestureHints = gestureHints,
                glass = Modifier.sharedElementOcclusionBand().hazeBlur(
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
                replyQuote = state.replyQuote?.text,
                partnerName = state.info?.title,
                flight = state.flight,
                onCancelReply = viewModel::clearReply,
                // Odeslání je potvrzení akce; dosud ho doprovázel jen let bubliny. Hlásí se
                // v okamžiku ťuknutí, ne po odpovědi serveru — odezva patří ke gestu.
                onSend = { text, options ->
                    haptics.confirm()
                    viewModel.send(text, options)
                },
                sendMenu = sendMenuOffer(state),
                onSchedule = { sheets.scheduleTarget = ScheduleTarget.Draft },
                onPickEffect = {
                    viewModel.loadEffects()
                    sheets.effectPicker = true
                },
                onOpenScheduled = viewModel::openScheduled.takeIf { state.hasScheduledMessages },
                onDraftChanged = viewModel::onDraftChanged,
                editing = state.editing,
                onCancelEdit = viewModel::cancelEdit,
                placeholder = state.inputPlaceholder,
                linkPreview = composerLinkPreview(
                    preview = state.linkPreview,
                    options = state.linkPreviewOptions,
                    editingCaption = state.editing?.composerEdit() == ComposerEdit.Caption,
                    hasAttachments = state.attachments.isNotEmpty(),
                ),
                linkPreviewLinks = state.linkPreviewLinks,
                linkPreviewOptions = state.linkPreviewOptions,
                onLinkPreviewOptions = viewModel::setLinkPreviewOptions,
                inputFill = state.inputFill,
                onInputFilled = viewModel::consumeInputFill,
                onOpenStickers = { sheets.stickerPicker = true },
                onPickMedia = { if (embeddedMediaPicker) sheets.mediaPicker = true else attachmentPicker.pickMedia() },
                isRecording = isRecording,
                recording = { voiceRecordingState.value ?: videoRecordingState.value },
                recordMode = recordMode,
                canSwitchRecordMode = canSwitchRecordMode(state.content.voiceNotes, videoNotesAllowed),
                onSwitchRecordMode = {
                    viewModel.setRecordMode(if (recordMode == RecordMode.VideoNote) RecordMode.Voice else RecordMode.VideoNote)
                },
                // Zastavené kolečko je pořád kolečko: odeslání se tak jmenuje i v náhledu.
                recordingKind = if (isVideoRecording || videoNotePreview != null) RecordMode.VideoNote else RecordMode.Voice,
                recordGesture = recordGesture,
                onStartRecording = { held ->
                    val video = recordMode == RecordMode.VideoNote
                    // Odpověď bez dialogu přijde hned, uvnitř `request`; z dialogu až potom.
                    var immediate = true
                    (if (video) videoNotePermission else recordPermission).request { granted ->
                        when {
                            !granted -> {
                                haptics.reject()
                                viewModel.reportError(
                                    UiText(if (video) Res.string.conversation_camera_permission else Res.string.conversation_microphone_permission),
                                )
                            }
                            // Oprávnění přišlo až z dialogu: prst mezitím tlačítko pustil, a nahrávat
                            // bez něj by bylo nahrávání, o kterém uživatel neví. Podrží znovu.
                            held && !immediate -> Unit
                            else -> {
                                // Nahrávání běží, dokud ho uživatel neukončí — přepnutý stav, ne
                                // hotová akce. Odepřené oprávnění je naopak odmítnutí.
                                haptics.toggle(on = true)
                                if (video) viewModel.startVideoRecording() else viewModel.startRecording()
                            }
                        }
                    }
                    immediate = false
                },
                onCancelRecording = {
                    haptics.reject()
                    viewModel.cancelRecording()
                },
                voicePreview = voicePreview,
                voicePreviewPlayback = { voicePlayback.value?.takeIf { it.key.isVoicePreview } },
                onStopRecording = {
                    // Mikrofon se zavírá — přepnutý stav, jako při spuštění.
                    haptics.toggle(on = false)
                    viewModel.stopRecordingForPreview()
                },
                onToggleVoicePreview = viewModel::toggleVoicePreview,
                onSeekVoicePreview = viewModel::seekVoicePreview,
                onContinueVoicePreview = {
                    haptics.toggle(on = true)
                    viewModel.continueVoicePreview()
                },
                onDiscardVoicePreview = {
                    haptics.reject()
                    viewModel.discardVoicePreview()
                },
                videoNotePreview = videoNotePreview,
                videoNotePlayback = videoNotePlayback,
                onDiscardVideoNotePreview = {
                    haptics.reject()
                    viewModel.discardVideoNotePreview()
                },
                onContinueVideoNotePreview = {
                    haptics.toggle(on = true)
                    viewModel.continueVideoNotePreview()
                },
                onSendRecording = { options ->
                    haptics.confirm()
                    viewModel.sendRecording(options)
                },
                // Nabídka nad hlasovkou nahrávání pozastaví, zavření bez volby ho obnoví.
                onVoiceMenu = { open -> if (open) viewModel.pauseRecording() else viewModel.resumeRecording() },
                onScheduleRecording = { sheets.scheduleTarget = ScheduleTarget.Voice },
                onPickDocument = attachmentPicker::pickDocument,
                // Anketa jde jen tam, kde to dovolí právo i druh chatu (`ContentAccess.polls`).
                onCreatePoll = {
                    onCreatePoll(PollComposerRequest(PollComposerMode.Any, state.replyingTo?.id, state.replyQuote))
                }.takeIf { state.content.polls },
                // Poloha je „základní zpráva" jako text: smí se všude, kde je vstupní pole.
                onSendLocation = { sheets.locationPicker = true },
                // Kontakt je taky „základní zpráva" (TDLib ho řadí k textu), jako poloha.
                onShareContact = viewModel::openContactPicker,
                onToggleCommands = viewModel::toggleCommands.takeIf { state.botCommands.isNotEmpty() },
                menuApp = state.botMenuApp,
                onOpenMenuApp = viewModel::openBotMenuApp,
                attachments = state.attachments,
                // Vykreslovaná úprava fotky blokuje odeslání stejně jako kopírování z galerie.
                attachmentsPreparing = mediaSelection.preparing || state.markupBusy,
                onRemoveAttachment = viewModel::removeAttachment,
                onOpenAttachment = { index -> sheets.previewAttachmentIndex = index },
                onSetAttachmentMode = viewModel::setAttachmentMode,
                content = state.content,
                selfDestructAllowed = state.canSelfDestruct(attachmentCount = 1),
                previewAttachmentIndex = sheets.previewAttachmentIndex,
                expressionMode = expressionMode,
                composer = composer,
                onKeyboardContent = { received ->
                    when (received) {
                        // GIF odchází hned, jako samolepka; obrázek čeká nad polem jako z výběru.
                        is KeyboardContent.Animation -> viewModel.sendAnimation(received.animation)
                        is KeyboardContent.Image -> viewModel.addAttachments(listOf(received.photo))
                        is KeyboardContent.File -> viewModel.addAttachments(listOf(received.document))
                    }
                },
                onKeyboardContentFailed = viewModel::reportError,
            )
            }
            }
            }
        },
        // Tlačítko dolů ve FAB slotu: Scaffold ho drží nad polem a snackbar pak nad ním.
        // Uprostřed, ne vpravo: vpravo by viselo přímo nad tlačítkem odeslání a mikrofonu
        // a ťuknutí o kousek vedle by místo skoku dolů spustilo nahrávání.
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            // Nad polem zrovna plave napovídání (viz `FloatingSuggestion`) — tlačítka by visela
            // přes něj, takže na tu chvíli ustoupí.
            AnimatedVisibility(
                visible = !suggestionsFloating,
                enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Zmínky a reakce nad tlačítkem dolů: jsou vidět, i když je uživatel dole — zmínka
            // bývá hluboko v nepřečtené historii a tlačítko dolů o ní nic neřekne.
            UnreadMarkButton(
                kind = UnreadMarkKind.Mention,
                count = state.unreadMarks.mentions,
                onClick = viewModel::jumpToUnreadMark,
                onReadAll = viewModel::readAllMarks,
            )
            UnreadMarkButton(
                kind = UnreadMarkKind.Reaction,
                count = state.unreadMarks.reactions,
                onClick = viewModel::jumpToUnreadMark,
                onReadAll = viewModel::readAllMarks,
            )
            UnreadMarkButton(
                kind = UnreadMarkKind.PollVote,
                count = state.unreadMarks.pollVotes,
                onClick = viewModel::jumpToUnreadMark,
                onReadAll = viewModel::readAllMarks,
            )
            // Diskrétní čtení: přečtení se samo nehlásí, tak jde poslat ručně. Jen v chatu samém —
            // ve vlákně a v tématu by „přečíst chat" přečetlo i to, co uživatel neviděl.
            MarkReadChip(
                visible = state.discreetUnread > 0 && state.threadRootId == null && state.forumTopicId == null,
                onClick = viewModel::readNow,
            )
            ScrollToBottomButton(
                // V okně v historii vede dolů i od spodní hrany — nejnovější zprávy nejsou načtené.
                visible = farFromBottom || pendingNewMessages > 0 || !state.reachedEnd,
                count = pendingNewMessages,
                onClick = {
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
            }
            }
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

        // Jednorázová událost od tlačítka bota. Navigace je synchronní, takže vynulování potom
        // efekt nezruší uprostřed (past „Vrátit" v docs/poznatky/konverzace-pohyb.md se týká práce, která teprve běží).
        state.openPollComposer?.let { mode ->
            LaunchedEffect(mode) {
                onCreatePoll(PollComposerRequest(mode, state.replyingTo?.id, state.replyQuote))
                viewModel.consumeOpenPollComposer()
            }
        }
        state.openBotShare?.let { request ->
            LaunchedEffect(request) {
                viewModel.consumeOpenBotShare()
                onOpenBotShare(request)
            }
        }
        state.openWebApp?.let { request ->
            LaunchedEffect(request) {
                viewModel.consumeOpenWebApp()
                onOpenWebApp(request)
            }
        }

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
                                onInviteMembers = onAddMembers,
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
                                onClick = onOpenJoinRequests,
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
                    onOpenChat = onOpenChat,
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
                visible = isVideoRecording,
                camera = videoNoteCamera,
                recording = { videoRecordingState.value },
                modifier = Modifier.fillMaxSize().padding(bottom = obscuredBottom),
                preview = videoNotePreview,
                playback = videoNotePlayback,
            )
            // Zámek nad tlačítkem je tady, ne v liště — sklo pod polem by ho ořízlo.
            RecordLockHost(
                visible = isRecording && recordGesture.held && !recordGesture.locked,
                gesture = recordGesture,
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

/** Avatar v hlavičce. */
private val HEADER_AVATAR = 36.dp

/** Avatar v hlavičce při výpadku spojení (viz `ConnectionIndicator`). */
private val HEADER_AVATAR_GROWN = 42.dp

/** Rezerva řádku hlavičky kolem avataru pro jeho zvětšení a půl tahu prstence. */
private val HEADER_RING_ROOM = (HEADER_AVATAR_GROWN + RING_STROKE - HEADER_AVATAR) / 2

/** Tečka dostupnosti na 36dp avataru — v seznamu chatů je 14 dp na 52dp. */
private val HEADER_ONLINE_DOT = 11.dp

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

private const val READ_DEBOUNCE_MS = 400L

/** Jak dlouho musí být reklama v klidu na obrazovce, než se zhlédnutí nahlásí. */
private const val SPONSORED_VIEW_DEBOUNCE_MS = 1000L

/**
 * Průsvit pruhu pod vstupním polem.
 *
 * Dost na to, aby text pod ním nerušil, ale ne plná barva — ta by udělala ostrý předěl přes
 * celou obrazovku, což je právě to, co se odstraňovalo.
 */
private const val INPUT_SCRIM_ALPHA = 0.93f

/** Řádek pod názvem chatu: naposledy viděn, nebo ve vlákně název skupiny. */
private data class HeaderSubtitle(val text: String)

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
