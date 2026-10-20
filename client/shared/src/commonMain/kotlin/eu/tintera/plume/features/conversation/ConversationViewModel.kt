package eu.tintera.plume.features.conversation

import kotlinx.coroutines.flow.flowOf
import eu.tintera.plume.features.media.PhotoDescriptions
import eu.tintera.plume.features.conversation.items.EmojiBurst
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import eu.tintera.plume.core.speech.SpeechToText
import eu.tintera.plume.core.ai.ScamKind
import eu.tintera.plume.core.ai.OnDeviceAi
import kotlin.time.Instant
import eu.tintera.plume.core.calendar.CalendarEvents
import eu.tintera.plume.core.contacts.ContactSaver
import eu.tintera.plume.core.currency.ExchangeRates
import eu.tintera.plume.core.currency.HomeRegion
import eu.tintera.plume.core.wifi.WifiJoiner
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.TimeZone
import eu.tintera.plume.core.translation.OnDeviceTranslator
import eu.tintera.plume.core.bookmarks.MessageBookmarks
import eu.tintera.plume.core.snooze.ChatSnoozes
import eu.tintera.plume.core.ui.PhotoMarkup
import eu.tintera.plume.core.ui.PhotoMarkupRenderer
import eu.tintera.plume.core.telegram.MessageSenderOption
import eu.tintera.plume.core.telegram.ModerationChoice
import eu.tintera.plume.core.telegram.QuickReaction
import eu.tintera.plume.core.telegram.PollDraftMedia
import eu.tintera.plume.core.telegram.ReplyQuote
import eu.tintera.plume.core.telegram.ComposedText
import eu.tintera.plume.core.telegram.UnreadMarkKind
import eu.tintera.plume.core.telegram.VideoChatInfo
import eu.tintera.plume.core.telegram.InviteLinkRepository
import eu.tintera.plume.core.telegram.PendingJoinRequests
import eu.tintera.plume.core.telegram.ChatReplyMarkup
import eu.tintera.plume.core.telegram.BotButtonKind
import eu.tintera.plume.core.telegram.SenderInfo
import eu.tintera.plume.core.language.LanguageDetector
import eu.tintera.plume.core.telegram.MentionCandidate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.audio.AudioRecorder
import eu.tintera.plume.core.audio.VoiceKey
import eu.tintera.plume.core.audio.VoicePlayback
import eu.tintera.plume.core.audio.VoicePlayer
import eu.tintera.plume.core.audio.MusicQueue
import eu.tintera.plume.core.audio.NowPlaying
import eu.tintera.plume.core.camera.VideoNoteCamera
import eu.tintera.plume.core.audio.VoiceRecording
import eu.tintera.plume.core.audio.VoiceSpeed
import eu.tintera.plume.core.files.FileOpener
import eu.tintera.plume.core.files.MediaSharer
import eu.tintera.plume.core.files.SaveAsLauncher
import eu.tintera.plume.core.share.ShareRequests
import eu.tintera.plume.core.share.ShareTargets
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.settings.ExpressionMode
import eu.tintera.plume.core.settings.GestureHint
import eu.tintera.plume.core.settings.RecordMode
import eu.tintera.plume.core.telegram.ChatInfo
import eu.tintera.plume.core.navigation.InstantViewRequests
import eu.tintera.plume.core.navigation.BotStartRequests
import eu.tintera.plume.core.navigation.ChannelLimitRequests
import eu.tintera.plume.core.navigation.ContactOffers
import eu.tintera.plume.core.telegram.MessageSchedule
import eu.tintera.plume.core.telegram.MessageViewMetrics
import eu.tintera.plume.core.telegram.SendState
import eu.tintera.plume.core.telegram.MessageEffect
import eu.tintera.plume.core.telegram.SendOptions
import eu.tintera.plume.core.telegram.SpeechTrial
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.LinkPreviewOptions
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ConnectionRepository
import eu.tintera.plume.core.telegram.ConnectionState
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.ProfileRepository
import eu.tintera.plume.core.telegram.ThreadConversationRepository
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.MessageReaction
import eu.tintera.plume.core.telegram.GifItem
import eu.tintera.plume.core.telegram.OutgoingAnimation
import eu.tintera.plume.core.telegram.Place
import eu.tintera.plume.core.telegram.LiveLocationRepository
import eu.tintera.plume.core.telegram.PlaceRepository
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.AttachmentMode
import eu.tintera.plume.core.telegram.SelfDestructType
import eu.tintera.plume.core.telegram.SentMessageUpdate
import eu.tintera.plume.core.telegram.SponsoredMessage
import eu.tintera.plume.core.telegram.StickerItem
import eu.tintera.plume.core.telegram.StickerRepository
import eu.tintera.plume.core.ui.TextClipboard
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.UrlOpener
import eu.tintera.plume.features.conversation.items.MessageAction
import eu.tintera.plume.features.conversation.items.MessageMenuAction
import eu.tintera.plume.resources.conversation_unpin_all_failed
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.paid_media_unsupported
import eu.tintera.plume.resources.story_live_unsupported
import eu.tintera.plume.resources.conversation_history_failed
import eu.tintera.plume.resources.conversation_message_deleted
import eu.tintera.plume.resources.conversation_quote_deleted
import eu.tintera.plume.resources.conversation_repin_failed
import eu.tintera.plume.resources.conversation_unpin_failed
import eu.tintera.plume.resources.conversation_unread_mark_failed
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import eu.tintera.plume.core.telegram.ChatBar
import eu.tintera.plume.features.chats.SpamCleanup
import eu.tintera.plume.features.report.ReportFlow
import eu.tintera.plume.features.proxy.ProxyLinkFlow
import eu.tintera.plume.core.telegram.ProxyRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.collectLatest
import kotlin.time.Clock
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.jetbrains.compose.resources.StringResource
import eu.tintera.plume.core.telegram.InlineQueryDraft
import eu.tintera.plume.core.telegram.InlineResult
import eu.tintera.plume.core.location.GeoPoint

/**
 * Stav a stránkování jedné konverzace.
 *
 * Zprávy se drží **seřazené od nejnovější po nejstarší**, protože stejně je vykresluje
 * `LazyColumn(reverseLayout = true)` — nejnovější dole, stránkování nahoru. Kdyby se držely
 * opačně, každé přidání staré stránky by znamenalo přepočet celého seznamu.
 *
 * **Sám drží jen jádro**: stav ([state]) a jeho plnění z TDLib (`observeUpdates`), stránkování,
 * skoky na zprávu, hlášení přečtení, připnuté zprávy, reklamy a rozcestník akcí z bubliny
 * ([onAction]). Všechno ostatní dělají části (`ConversationComposer`, `ConversationLinks`,
 * `ConversationBots`…), které dostávají sdílený stav a jen ty závislosti, které potřebují;
 * ViewModel na ně deleguje a jeho veřejné API pro obrazovku je tak pořád na jednom místě.
 * Mapa částí: „Skladba ViewModelu" v `docs/poznatky/konverzace-zobrazeni.md`.
 */
class ConversationViewModel(
    val chatId: Long,
    /** Zpráva, na kterou se po otevření skočí místo na první nepřečtenou (nalezená v hledání). */
    private val initialMessageId: Long?,
    /**
     * Vlákno komentářů: kořenová zpráva v diskusní skupině [chatId]; `null` u běžné konverzace.
     * Rozdíly ve zdroji zpráv řeší [repository] (je to [ThreadConversationRepository]), tady jen
     * to, co je vidět: hlavička a citace kořene.
     */
    private val threadRootId: Long? = null,
    /**
     * Téma skupiny s tématy; `null` u běžné konverzace. Zdroj zpráv zužuje [repository]
     * (`inForumTopic`), tady jen to, co je vidět: hlavička a plánování.
     */
    private val forumTopicId: Int? = null,
    /**
     * Téma Uložených zpráv (zprávy uložené z jednoho chatu); `null` u běžné konverzace. Zdroj
     * zpráv zužuje [repository] (`inSavedTopic`), tady jen hlavička.
     */
    private val savedTopicId: Long? = null,
    /** Vlákno přímých zpráv kanálu; `null` u běžné konverzace. Zdroj zpráv zužuje [repository]. */
    private val directTopicId: Long? = null,
    private val repository: ConversationRepository,
    /**
     * Jen kvůli „píše…". Činnost v chatu už sleduje repository chatů — i s odpočtem, který ji zhasne,
     * kdyby se ztratilo ukončení, a s odfiltrováním vlastní činnosti. Druhá kopie téže logiky
     * v konverzaci by se s ní dřív nebo později rozešla.
     */
    private val chatRepository: ChatRepository,
    /** Jen kvůli oblíbeným samolepkám v nabídce nad zprávou. */
    private val stickerRepository: StickerRepository,
    private val settings: AppSettings,
    /**
     * Rozsah aplikace, jen pro ukládání konceptu. `viewModelScope` se při odchodu z chatu zruší
     * dřív, než by poslední úprava stihla odejít — a právě ta je nejdůležitější.
     */
    private val appScope: CoroutineScope,
    private val urlOpener: UrlOpener,
    private val clipboard: TextClipboard,
    /** Jen kvůli „Přeložit": zpráva, která už je v jazyce překladu, ho nenabízí. */
    private val languageDetector: LanguageDetector,
    /** Jen kvůli hlasovkám: soubor se stáhne až při prvním přehrání. */
    private val fileRepository: FileRepository,
    private val voicePlayer: VoicePlayer,
    private val musicQueue: MusicQueue,
    private val fileOpener: FileOpener,
    private val sharer: MediaSharer,
    /** Obsah sdílený ze systému, pro který uživatel vybral tenhle chat. */
    private val shareRequests: ShareRequests,
    /**
     * Každé odeslání chat nahlásí jako použitý — z toho si Android skládá chaty v nabídce
     * „Sdílet" (Direct Share).
     */
    private val shareTargets: ShareTargets,
    audioRecorder: AudioRecorder,
    connection: ConnectionRepository,
    /** „Otevři hledání" z profilu chatu (tlačítko Hledat). */
    private val searchRequests: ConversationSearchRequests,
    /** Místa v okolí pro sponku → Poloha. */
    placeRepository: PlaceRepository,
    /** Parametr ze startovacího odkazu bota, který čeká na tlačítko Start. */
    private val botStartRequests: BotStartRequests,
    /** Kontaktní odkaz ze zprávy: profil s „Přidat do kontaktů" jako hlavní akcí. */
    private val contactOffers: ContactOffers,
    /** Limit skupin a kanálů při vstupu: sheet s neaktivními k opuštění ukazuje shell. */
    private val channelLimit: ChannelLimitRequests? = null,
    /** „Odpovědět" z prohlížeče médií. */
    private val replyRequests: ConversationReplyRequests,
    /** Inline dotaz z tlačítka bota v jiném chatu ([BotButtonKind.SwitchInline]). */
    private val inlineQueryRequests: InlineQueryRequests,
    /** Jen kvůli pruhu s čekajícími žádostmi o vstup (vidí ho správce, který je smí vyřizovat). */
    private val inviteLinks: InviteLinkRepository,
    proxies: ProxyRepository,
    /** Sdílení živé polohy: zastavení z bubliny. Posouvá ji platforma (služba), ne konverzace. */
    private val liveLocations: LiveLocationRepository,
    /** Reakce na dvojité ťuknutí; nastavení účtu, ne chatu. */
    private val quickReaction: QuickReaction,
    /** Záložky zpráv; bez nich se „Přidat do záložek" nenabízí. */
    private val bookmarks: MessageBookmarks? = null,
    /** Hudba ve vlastním profilu; bez ní se u skladby „Přidat do profilu" nenabízí. */
    private val profiles: ProfileRepository? = null,
    /** Skok na zprávu ze záložky v profilu; bez něj se nečeká. */
    private val jumpRequests: ConversationJumpRequests? = null,
    /** Odložené chaty: otevřený chat z bloku připomínek v seznamu zmizí. */
    snoozes: ChatSnoozes? = null,
    /** Kreslení do fotky před odesláním; bez něj se „Upravit" nenabízí. */
    private val photoMarkup: PhotoMarkupRenderer? = null,
    /** Překlad v telefonu; bez něj (iOS) přepínač v sheetu překladu není. */
    private val onDeviceTranslator: OnDeviceTranslator? = null,
    /** Termín připomínky od modelu v zařízení, když ho nenajdou pravidla. */
    private val reminderTimeModel: ReminderTimeModel? = null,
    /** Přepis řeči v telefonu, když ho server nedá (bez Premium). */
    private val onDeviceAi: OnDeviceAi? = null,
    private val localTranscripts: LocalTranscripts? = null,
    /** Uložené popisy fotek (z prohlížeče) — bubliny je dají čtečce obrazovky. */
    photoDescriptions: PhotoDescriptions? = null,
    /** Přepis systémem telefonu, když Gemma není (Android 13+, nainstalovaný jazyk). */
    private val speechToText: SpeechToText? = null,
    /** Varování u zpráv, které vypadají jako podvod (model v zařízení). */
    private val scamWarnings: ScamWarnings? = null,
    /** Posudky termínů ze zpráv modelem v zařízení (ikonka akcí u zprávy). */
    private val messageContexts: MessageContexts? = null,
    /** Přidání události do kalendáře z akcí u zprávy; bez něj se termín nenabízí. */
    private val calendarEvents: CalendarEvents? = null,
    /** Uložení kontaktu z akcí u zprávy. */
    private val contactSaver: ContactSaver? = null,
    /** Připojení k Wi-Fi z akcí u zprávy; bez něj (iOS) jen heslo a QR kód. */
    private val wifiJoiner: WifiJoiner? = null,
    /** Kurzy ČNB pro převod cizí měny v akcích u zprávy. */
    private val exchangeRates: ExchangeRates? = null,
    /** Měna a jednotky uživatele podle regionu telefonu. */
    currency: HomeRegion? = null,
    /** Čtečka Instant View z náhledu odkazu; bez ní se odkaz otevře v prohlížeči. */
    private val instantViewRequests: InstantViewRequests? = null,
) : ViewModel() {

    /** Hledání místa pro sponku → Poloha (sheet [PlacePickerHost]). */
    val places = PlaceSearcher(viewModelScope, placeRepository, chatId)

    /**
     * Stav spojení. Samostatný tok, ne součást [ConversationState]: netýká se chatu, mění se
     * nezávisle na něm a do stavu, který se přepisuje s každou zprávou, nepatří.
     */
    val connection: StateFlow<ConnectionState> = connection.state

    /** Co nahraje podržení tlačítka v prázdném poli (volba přežije i odchod z chatu). */
    val recordMode: StateFlow<RecordMode> = settings.recordMode

    /** Nápovědy gest, které uživatel už viděl (napříč chaty i spuštěními). */
    val seenGestureHints: StateFlow<Set<GestureHint>> = settings.seenGestureHints

    fun markGestureHintSeen(hint: GestureHint) = settings.markGestureHintSeen(hint)

    /**
     * Zpráva s jednorázovou klávesnicí bota, kterou uživatel v tomhle chatu už použil — panel
     * se pak otevře sbalený (viz `AppSettings.usedOneTimeKeyboards`).
     */
    val usedKeyboardMessageId: StateFlow<Long?> = settings.usedOneTimeKeyboards
        .map { it[chatId] }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settings.usedOneTimeKeyboards.value[chatId])

    private val _state = MutableStateFlow(ConversationState(
            threadRootId = threadRootId,
            forumTopicId = forumTopicId,
            savedTopicId = savedTopicId,
            directTopicId = directTopicId,
        ))
    val state: StateFlow<ConversationState> = _state.asStateFlow()

    /** Popisy fotek v tomhle chatu podle id zprávy (viz [PhotoDescriptions]). */
    val photoDescriptions: StateFlow<Map<Long, String>> = (photoDescriptions?.forChat(chatId) ?: flowOf(emptyMap()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Průvodce nahlášením — z nabídky nad zprávou i z pruhu proti spamu. */
    val reportFlow = ReportFlow(viewModelScope, chatRepository::report)

    /**
     * Průvodce nahlášením reklamy. Tentýž sheet a tatáž pravidla kroků, jen jiné volání
     * (`reportChatSponsoredMessage`); id reklamy jede jako jediná zpráva požadavku.
     */
    val sponsoredReportFlow = ReportFlow(viewModelScope) { chatId, request ->
        repository.reportSponsored(chatId, request.messageIds.single(), request.optionId)
    }

    /** Proxy z odkazu `t.me/proxy` — přidá se až po potvrzení v dialogu. */
    val proxyLinkFlow = ProxyLinkFlow(viewModelScope, proxies)

    private val logger = Logger.withTag("Conversation")

    /** Skok na zprávu pro části, které ho potřebují (hledání, odkazy…) — viz [jumpToMessage]. */
    private val jumper = object : MessageJumper {
        override fun jumpToMessage(messageId: Long, fromSearch: Boolean, anchor: ScrollAnchor) =
            this@ConversationViewModel.jumpToMessage(messageId, fromSearch, anchor)
    }

    /** Odeslání média pro části, které posílají samy (nahrávání, inline boti…) — viz [sendMedia]. */
    private val mediaSender = object : MediaSender {
        override fun sendMedia(
            failureMessage: StringResource,
            scheduled: Boolean,
            send: suspend (replyTo: Long?, quote: ReplyQuote?) -> Result<ChatMessage>,
        ) = composer.sendMedia(failureMessage, scheduled, send)
    }

    /** Nahrávání hlasovky a kolečka — viz [ConversationRecorder]. */
    private val recorder: ConversationRecorder = ConversationRecorder(
        chatId = chatId,
        state = _state,
        scope = viewModelScope,
        appScope = appScope,
        repository = repository,
        settings = settings,
        voicePlayer = voicePlayer,
        audioRecorder = audioRecorder,
        media = mediaSender,
        readAfterOwnMessage = ::readAfterOwnMessage,
        logger = logger,
    )
    val voiceRecording: StateFlow<VoiceRecording?> = recorder.voiceRecording
    val voicePreview: StateFlow<VoicePreview?> = recorder.voicePreview
    val videoRecording: StateFlow<VoiceRecording?> = recorder.videoRecording
    val videoNotePreview: StateFlow<VideoNotePreview?> = recorder.videoNotePreview
    fun startRecording() = recorder.startRecording()
    fun startVideoRecording() = recorder.startVideoRecording()
    fun attachCamera(camera: VideoNoteCamera) = recorder.attachCamera(camera)
    fun detachCamera(camera: VideoNoteCamera) = recorder.detachCamera(camera)
    fun setRecordMode(mode: RecordMode) = recorder.setRecordMode(mode)
    fun cancelRecording() = recorder.cancelRecording()
    fun pauseRecording() = recorder.pauseRecording()
    fun resumeRecording() = recorder.resumeRecording()
    fun sendRecording(options: SendOptions = SendOptions()) = recorder.sendRecording(options)
    fun stopRecordingForPreview() = recorder.stopRecordingForPreview()
    fun toggleVoicePreview() = recorder.toggleVoicePreview()
    fun seekVoicePreview(fraction: Float) = recorder.seekVoicePreview(fraction)
    fun continueVoicePreview() = recorder.continueVoicePreview()
    fun discardVoicePreview() = recorder.discardVoicePreview()
    fun continueVideoNotePreview() = recorder.continueVideoNotePreview()
    fun discardVideoNotePreview() = recorder.discardVideoNotePreview()

    /** Přehrávání hlasovek, koleček a hudby a přepis řeči — viz [ConversationPlayback]. */
    private val playback: ConversationPlayback = ConversationPlayback(
        chatId = chatId,
        state = _state,
        scope = viewModelScope,
        appScope = appScope,
        repository = repository,
        chatRepository = chatRepository,
        settings = settings,
        voicePlayer = voicePlayer,
        musicQueue = musicQueue,
        fileRepository = fileRepository,
        localTranscripts = localTranscripts,
        onDeviceAi = onDeviceAi,
        speechToText = speechToText,
        openLink = { links.openLink(it) },
        logger = logger,
    )
    val voicePlayback: StateFlow<VoicePlayback?> get() = playback.voicePlayback
    val voiceSpeed: StateFlow<VoiceSpeed> get() = playback.voiceSpeed
    val nowPlaying: StateFlow<NowPlaying?> get() = playback.nowPlaying
    fun togglePlaying() = playback.togglePlaying()
    fun skipToNextTrack() = playback.skipToNextTrack()
    fun stopPlaying() = playback.stopPlaying()
    fun restartAudio(messageId: Long) = playback.restartAudio(messageId)
    fun consumeResumedAudio() = _state.update { it.copy(resumedAudio = null) }
    val isPremium: StateFlow<Boolean> get() = playback.isPremium
    val speechTrial: StateFlow<SpeechTrial?> get() = playback.speechTrial
    val transcribeContext: StateFlow<TranscribeContext> get() = playback.transcribeContext

    /** Soubory ve zprávách: stažení a otevření, sdílení, uložení — viz [ConversationDocuments]. */
    private val documents: ConversationDocuments = ConversationDocuments(
        state = _state,
        scope = viewModelScope,
        fileRepository = fileRepository,
        openFile = fileOpener::open,
        share = sharer::share,
        closeMenus = { menu.closeMenus() },
        logger = logger,
    )
    fun saveDocumentAs(messageId: Long, launcher: SaveAsLauncher) = documents.saveDocumentAs(messageId, launcher)

    /** Překlad a shrnutí zprávy — viz [ConversationTranslation]. */
    private val translation: ConversationTranslation = ConversationTranslation(
        chatId = chatId,
        state = _state,
        scope = viewModelScope,
        repository = repository,
        settings = settings,
        onDeviceTranslator = onDeviceTranslator,
        languageDetector = languageDetector,
        copyToClipboard = clipboard::copy,
        clipboardConfirmsCopy = clipboard.confirmsCopy,
        logger = logger,
    )
    fun changeTranslationLanguage(code: String) = translation.changeTranslationLanguage(code)
    fun dismissTranslation() = translation.dismissTranslation()
    fun setTranslateOnDevice(onDevice: Boolean) = translation.setTranslateOnDevice(onDevice)
    fun downloadTranslationModels() = translation.downloadTranslationModels()
    fun translateOnServer() = translation.translateOnServer()
    fun copyTranslation() = translation.copyTranslation()

    /** Akce u zprávy a posudky modelu (termíny, podvod) — viz [ConversationMessageContext]. */
    private val messageContext: ConversationMessageContext = ConversationMessageContext(
        chatId = chatId,
        state = _state,
        scope = viewModelScope,
        settings = settings,
        openUrl = urlOpener::open,
        copyToClipboard = clipboard::copy,
        clipboardConfirmsCopy = clipboard.confirmsCopy,
        scamWarnings = scamWarnings,
        messageContexts = messageContexts,
        addCalendarEvent = calendarEvents?.let { it::add },
        saveNewContact = contactSaver?.let { it::save },
        joinWifiNetwork = wifiJoiner?.let { it::join },
        canJoinWifi = wifiJoiner?.canJoin == true,
        shareFile = sharer::share,
        loadRates = exchangeRates?.let { it::table },
        homeCurrency = currency?.code,
        metricUnits = currency?.metric ?: true,
        closeMenus = { menu.closeMenus() },
        openLink = { links.openLink(it) },
        requestRemind = { messageActions.requestRemind(it) },
        logger = logger,
    )
    val scamVerdicts: StateFlow<Map<Long, ScamKind>> get() = messageContext.scamVerdicts
    val contextVerdicts: StateFlow<Map<Pair<Long, Int>, EventVerdict>?> get() = messageContext.contextVerdicts
    val homeCurrency: String? get() = messageContext.homeCurrency
    val metricUnits: Boolean get() = messageContext.metricUnits
    val contextSheet: StateFlow<ContextSheet?> get() = messageContext.contextSheet
    fun openMessageContext(messageId: Long, findings: List<ContextFinding>) = messageContext.openMessageContext(messageId, findings)
    fun closeMessageContext() = messageContext.closeMessageContext()
    fun addToCalendar(event: ContextFinding.Event) = messageContext.addToCalendar(event)
    fun addFlightToCalendar(messageId: Long, flight: ContextFinding.Flight) = messageContext.addFlightToCalendar(messageId, flight)
    fun copyInSheet(text: String) = messageContext.copyInSheet(text)
    fun trackParcel(url: String) = messageContext.trackParcel(url)
    val canJoinWifi: Boolean get() = messageContext.canJoinWifi
    fun joinWifi(wifi: ContextFinding.Wifi) = messageContext.joinWifi(wifi)
    val canSharePaymentQr: Boolean get() = messageContext.canSharePaymentQr
    fun sharePaymentQr(payment: ContextFinding.Payment) = messageContext.sharePaymentQr(payment)
    fun saveContact(contact: ContextFinding.Contact) = messageContext.saveContact(contact)
    fun openMaps(query: String) = messageContext.openMaps(query)
    fun remindFromContext(messageId: Long) = messageContext.remindFromContext(messageId)
    fun dismissScamWarning(messageId: Long) = messageContext.dismissScamWarning(messageId)

    /** Hledání v chatu a výběr člena pro filtr — viz [ConversationSearch]. */
    private val search: ConversationSearch = ConversationSearch(
        chatId = chatId,
        state = _state,
        scope = viewModelScope,
        repository = repository,
        jumper = jumper,
        logger = logger,
    )
    fun openSearch() = search.openSearch()
    fun closeSearch() = search.closeSearch()
    fun jumpToDate(epochSeconds: Int) = search.jumpToDate(epochSeconds)
    fun onSearchQuery(text: String) = search.onSearchQuery(text)
    fun setSearchSender(sender: SenderInfo?) = search.setSearchSender(sender)
    fun openMemberPicker() = search.openMemberPicker()
    fun closeMemberPicker() = search.closeMemberPicker()
    fun onMemberQuery(text: String) = search.onMemberQuery(text)
    fun searchOlder() = search.searchOlder()
    fun searchNewer() = search.searchNewer()

    /** Chat jako celek: pruhy nad konverzací, vstup, ztlumení, smazání — viz [ConversationChatActions]. */
    private val chatActions: ConversationChatActions = ConversationChatActions(
        chatId = chatId,
        threadRootId = threadRootId,
        state = _state,
        scope = viewModelScope,
        repository = repository,
        chatRepository = chatRepository,
        inviteLinks = inviteLinks,
        reportFlow = reportFlow,
        logger = logger,
        // `links` vzniká až níž; lambda na něj sáhne teprve při ťuknutí na „Přidat se".
        awaitGuardBot = { guard, onApproved -> links.awaitGuardBot(guard, onApproved) },
        // `bots` vzniká až níž; sáhne se na ně teprve po odblokování bota.
        restartBot = { bots.startBot() },
        onChannelLimit = { channelLimit?.request() },
    )
    val chatBar: StateFlow<ChatBar?> get() = chatActions.chatBar
    val videoChat: StateFlow<VideoChatInfo?> get() = chatActions.videoChat
    val joinRequests: StateFlow<PendingJoinRequests?> get() = chatActions.joinRequests
    fun explainVideoChat() = chatActions.explainVideoChat()
    val chatGone: StateFlow<Boolean> get() = chatActions.chatGone
    val unarchived: StateFlow<Boolean> get() = chatActions.unarchived
    fun join() = chatActions.join()
    fun unblock() = chatActions.unblock()
    fun toggleMute() = chatActions.toggleMute()
    fun toggleDefaultSilent() = chatActions.toggleDefaultSilent()
    fun addSpamBarContact() = chatActions.addSpamBarContact()
    fun unarchiveFromSpamBar() = chatActions.unarchiveFromSpamBar()
    fun sharePhoneNumber() = chatActions.sharePhoneNumber()
    fun dismissChatBar() = chatActions.dismissChatBar()
    fun resolveSpamBar(cleanup: SpamBarCleanup) = chatActions.resolveSpamBar(cleanup)
    fun reportChat() = chatActions.reportChat()
    fun deleteOrLeave(cleanup: SpamCleanup) = chatActions.deleteOrLeave(cleanup)
    fun closeSecretChat() = chatActions.closeSecretChat()
    fun clearHistory(from: Int?, to: Int?, forAll: Boolean) = chatActions.clearHistory(from, to, forAll)
    fun viewAsTopics(onDone: () -> Unit) = chatActions.viewAsTopics(onDone)

    /** Napovídání při psaní: zmínky a inline boti — viz [ConversationSuggestions]. */
    private val suggestions: ConversationSuggestions = ConversationSuggestions(
        chatId = chatId,
        state = _state,
        scope = viewModelScope,
        repository = repository,
        media = mediaSender,
        composedDraft = { composer.draft },
        onDraftChanged = { composer.onDraftChanged(it) },
        openBotStart = { links.openBotStart(it) },
        requestWebApp = { links.requestWebApp(it) },
        logger = logger,
    )
    fun mentionQuery(query: String?, atStart: Boolean) = suggestions.mentionQuery(query, atStart)
    fun hashtagQuery(query: String?) = suggestions.hashtagQuery(query)
    fun hashtagPicked() = suggestions.hashtagPicked()
    fun removeHashtag(hashtag: String) = suggestions.removeHashtag(hashtag)
    fun mentionPicked(candidate: MentionCandidate) = suggestions.mentionPicked(candidate)
    fun inlineQuery(draft: InlineQueryDraft?) = suggestions.inlineQuery(draft)
    fun loadMoreInline() = suggestions.loadMoreInline()
    fun setInlineLocation(location: GeoPoint?) = suggestions.setInlineLocation(location)
    fun sendInlineResult(result: InlineResult) = suggestions.sendInlineResult(result)
    fun pressInlineButton() = suggestions.pressInlineButton()

    /** Kam vedou odkazy a ťuknutí: chaty, zprávy, příběhy, pozvánky, mini aplikace — viz [ConversationLinks]. */
    private val links: ConversationLinks = ConversationLinks(
        chatId = chatId,
        forumTopicId = forumTopicId,
        // Vlákno přímých zpráv odkaz na zprávu nenese, takže se s ním zachází jako s tématem
        // Uložených zpráv: odkaz otevře celý chat místo skoku.
        savedTopicId = savedTopicId ?: directTopicId,
        state = _state,
        scope = viewModelScope,
        repository = repository,
        chatRepository = chatRepository,
        openUrl = urlOpener::open,
        shareRequests = shareRequests,
        botStartRequests = botStartRequests,
        contactOffers = contactOffers,
        openProxy = proxyLinkFlow::open,
        jumper = jumper,
        logger = logger,
        onChannelLimit = { channelLimit?.request() },
    )
    fun openConfirmedLink() = links.openConfirmedLink()
    fun dismissLink() = links.dismissLink()
    fun consumeOpenWebApp() = links.consumeOpenWebApp()
    fun joinInvite() = links.joinInvite()
    fun dismissInvite() = links.dismissInvite()
    fun toggleFolderInviteChat(chatId: Long) = links.toggleFolderInviteChat(chatId)
    fun addFolderInvite() = links.addFolderInvite()
    fun dismissFolderInvite() = links.dismissFolderInvite()
    fun consumeOpenStickerSet() = links.consumeOpenStickerSet()
    fun consumeOpenProfile() = links.consumeOpenProfile()
    fun consumeOpenMediaAt() = links.consumeOpenMediaAt()
    fun consumeOpenChat() = links.consumeOpenChat()
    fun consumeOpenScreen() = links.consumeOpenScreen()
    fun consumeOpenStory() = links.consumeOpenStory()
    fun dismissLoginWarning() = links.dismissLoginWarning()
    fun consumeAddBotToGroup() = links.consumeAddBotToGroup()
    fun consumeSwitchInline() = links.consumeSwitchInline()

    /** Boti: tlačítka, klávesnice, příkazy, ankety, přihlášení — viz [ConversationBots]. */
    private val bots: ConversationBots = ConversationBots(
        chatId = chatId,
        state = _state,
        scope = viewModelScope,
        repository = repository,
        chatRepository = chatRepository,
        settings = settings,
        openUrl = urlOpener::open,
        copyToClipboard = clipboard::copy,
        clipboardConfirmsCopy = clipboard.confirmsCopy,
        botStartRequests = botStartRequests,
        links = links,
        suggestions = suggestions,
        // Vynucená odpověď přichází od bota, ne z ruky uživatele: klávesnici nevysouvá.
        replyTo = { composer.replyTo(it, focusInput = false) },
        logger = logger,
    )
    fun startBot() = bots.startBot()
    fun confirmLogin(allowWriteAccess: Boolean) = bots.confirmLogin(allowWriteAccess)
    fun dismissLogin() = bots.dismissLogin()
    fun dismissBotAlert() = bots.dismissBotAlert()
    fun cancelStopPoll() = bots.cancelStopPoll()
    fun confirmStopPoll() = bots.confirmStopPoll()
    fun consumeOpenBotShare() = bots.consumeOpenBotShare()
    fun consumeOpenPollComposer() = bots.consumeOpenPollComposer()
    fun dismissPollVoters() = bots.dismissPollVoters()
    fun dismissGiveaway() = bots.dismissGiveaway()
    fun copyGiftCode(code: String) = bots.copyGiftCode(code)
    fun openPollVoter(senderId: Long) = bots.openPollVoter(senderId)
    fun pressReplyKeyboardButton(row: Int, column: Int) = bots.pressReplyKeyboardButton(row, column)
    fun openBotMenuApp() = bots.openBotMenuApp()
    fun toggleCommands() = bots.toggleCommands()
    fun closeCommands() = bots.closeCommands()
    fun sendCommand(command: String) = bots.sendCommand(command)
    fun sharePhone() = bots.sharePhone()
    fun dismissSharePhone() = bots.dismissSharePhone()
    fun dismissShareLocation() = bots.dismissShareLocation()
    fun sendKeyboardLocation(point: GeoPoint?) = bots.sendKeyboardLocation(point)
    fun addPollOption(text: String, media: PollDraftMedia?) = bots.addPollOption(text, media)
    fun dismissAddPollOption() = bots.dismissAddPollOption()
    fun confirmDeletePollOption() = bots.confirmDeletePollOption()
    fun cancelDeletePollOption() = bots.cancelDeletePollOption()

    /** Seznamy úkolů: odškrtnutí a doplnění úkolu — viz [ConversationChecklists]. */
    private val checklists: ConversationChecklists = ConversationChecklists(
        chatId = chatId,
        state = _state,
        scope = viewModelScope,
        repository = repository,
        isPremium = { chatRepository.isPremium.value },
        logger = logger,
    )

    fun addChecklistTask(text: String) = checklists.addTask(text)
    fun consumeOpenChecklistEditor() = checklists.consumeOpenEditor()
    fun dismissAddChecklistTask() = checklists.dismissAddTask()

    /** Kým se do skupiny píše — viz [ConversationSendAs]. */
    private val sendAs: ConversationSendAs = ConversationSendAs(
        chatId = chatId,
        state = _state,
        scope = viewModelScope,
        repository = repository,
        logger = logger,
    )
    fun refreshMessageSenders() = sendAs.refresh()
    fun chooseMessageSender(option: MessageSenderOption) = sendAs.choose(option)

    /** Naplánované zprávy — viz [ConversationScheduled]. */
    private val scheduled: ConversationScheduled = ConversationScheduled(
        chatId = chatId,
        state = _state,
        scope = viewModelScope,
        repository = repository,
        logger = logger,
    )
    private val pendingReplies: ConversationPendingReplies = ConversationPendingReplies(
        chatId = chatId,
        state = _state,
        scope = viewModelScope,
        repository = repository,
        logger = logger,
    )
    fun stopPendingReply() = pendingReplies.stop()

    fun openScheduled() = scheduled.openScheduled()
    fun closeScheduled() = scheduled.closeScheduled()
    fun reschedule(messageId: Long, schedule: MessageSchedule?) = scheduled.reschedule(messageId, schedule)
    fun deleteScheduled(messageId: Long) = scheduled.deleteScheduled(messageId)

    /** Psaní: koncept, odpověď, úprava, náhled odkazu, odeslání — viz [ConversationComposer]. */
    private val composer: ConversationComposer = ConversationComposer(
        chatId = chatId,
        state = _state,
        scope = viewModelScope,
        appScope = appScope,
        repository = repository,
        chatRepository = chatRepository,
        settings = settings,
        reportUsed = shareTargets::reportUsed,
        suggestions = suggestions,
        readAfterOwnMessage = { readAfterOwnMessage() },
        sendAttachments = { caption, files, options -> attachments.sendAttachments(caption, files, options) },
        logger = logger,
    )
    val effectChoices: StateFlow<List<MessageEffect>?> get() = composer.effectChoices
    fun loadEffects() = composer.loadEffects()
    fun send(text: ComposedText, options: SendOptions = SendOptions()) = composer.send(text, options)
    fun onDraftChanged(text: ComposedText) = composer.onDraftChanged(text)
    fun setLinkPreviewOptions(options: LinkPreviewOptions) = composer.setLinkPreviewOptions(options)
    fun consumeInputFill(fill: InputFill) = composer.consumeInputFill(fill)
    fun cancelEdit() = composer.cancelEdit()
    fun replaceEditMedia(picked: List<OutgoingAttachment>) = composer.replaceEditMedia(picked)
    fun keepEditMedia() = composer.keepEditMedia()
    fun replaceEditGif(gif: GifItem) = composer.replaceEditGif(gif)
    fun clearReply() = composer.clearReply()

    /** Přílohy a co se posílá mimo text: samolepky, GIFy, poloha, kontakt, soubory — viz [ConversationAttachments]. */
    private val attachments: ConversationAttachments = ConversationAttachments(
        chatId = chatId,
        state = _state,
        scope = viewModelScope,
        appScope = appScope,
        repository = repository,
        chatRepository = chatRepository,
        settings = settings,
        shareRequests = shareRequests,
        reportUsed = shareTargets::reportUsed,
        liveLocations = liveLocations,
        photoMarkup = photoMarkup,
        composer = composer,
        suggestions = suggestions,
        logger = logger,
    )
    val expressionMode: StateFlow<ExpressionMode> get() = attachments.expressionMode
    fun setExpressionMode(mode: ExpressionMode) = attachments.setExpressionMode(mode)
    fun sendSticker(sticker: StickerItem, options: SendOptions = SendOptions()) = attachments.sendSticker(sticker, options)
    fun sendGif(gif: GifItem, options: SendOptions = SendOptions()) = attachments.sendGif(gif, options)
    fun sendLocation(place: Place) = attachments.sendLocation(place)
    fun shareLiveLocation(point: GeoPoint?, livePeriod: Int) = attachments.shareLiveLocation(point, livePeriod)
    fun dismissProximityAlert() = attachments.dismissProximityAlert()
    fun setProximityAlert(radiusMeters: Int) = attachments.setProximityAlert(radiusMeters)
    fun openContactPicker() = attachments.openContactPicker()
    fun searchContacts(query: String) = attachments.searchContacts(query)
    fun dismissContactPicker() = attachments.dismissContactPicker()
    fun pickContact(userId: Long) = attachments.pickContact(userId)
    fun cancelContact() = attachments.cancelContact()
    fun confirmContact() = attachments.confirmContact()
    fun sendAnimation(animation: OutgoingAnimation) = attachments.sendAnimation(animation)
    fun addAttachments(picked: List<OutgoingAttachment>) = attachments.addAttachments(picked)
    fun removeAttachment(index: Int) = attachments.removeAttachment(index)
    fun removeAttachments(paths: List<String>) = attachments.removeAttachments(paths)
    val canMarkupPhotos: Boolean get() = attachments.canMarkupPhotos
    fun applyPhotoMarkup(path: String, markup: PhotoMarkup) = attachments.applyPhotoMarkup(path, markup)
    fun setAttachmentMode(index: Int, mode: AttachmentMode) = attachments.setAttachmentMode(index, mode)
    fun toggleAttachmentSpoiler(index: Int) = attachments.toggleAttachmentSpoiler(index)
    fun setAttachmentSelfDestruct(index: Int, type: SelfDestructType?) = attachments.setAttachmentSelfDestruct(index, type)

    /** Nabídka nad zprávou a co z ní nevede jinam: kopírování, informace, záložky — viz [ConversationMessageMenu]. */
    private val menu: ConversationMessageMenu = ConversationMessageMenu(
        chatId = chatId,
        state = _state,
        scope = viewModelScope,
        repository = repository,
        settings = settings,
        copyToClipboard = clipboard::copy,
        clipboardConfirmsCopy = clipboard.confirmsCopy,
        languageDetector = languageDetector,
        onDeviceAi = onDeviceAi,
        bookmarks = bookmarks,
        stickerRepository = stickerRepository,
        profiles = profiles,
        messageContext = messageContext,
        translation = translation,
        logger = logger,
    )
    fun dismissTextSelection() = menu.dismissTextSelection()
    fun dismissMessageInfo() = menu.dismissMessageInfo()
    fun copyMessageLink(link: String) = menu.copyMessageLink(link)

    /** Reakce na zprávy — viz [ConversationReactions]. */
    private val reactions: ConversationReactions = ConversationReactions(
        chatId = chatId,
        state = _state,
        scope = viewModelScope,
        repository = repository,
        quickReaction = quickReaction,
        closeMenus = { menu.closeMenus() },
        logger = logger,
    )
    fun filterReactionDetails(emoji: String?) = reactions.filterReactionDetails(emoji)
    fun dismissReactionDetails() = reactions.dismissReactionDetails()

    /** Efekty animovaných emoji, které vidí obě strany — viz [ConversationEmojiBursts]. */
    private val emojiBurstsPart: ConversationEmojiBursts = ConversationEmojiBursts(
        chatId = chatId,
        state = _state,
        scope = viewModelScope,
        repository = repository,
        logger = logger,
    )
    internal val emojiBursts: StateFlow<Map<Long, EmojiBurst>> get() = emojiBurstsPart.bursts
    internal fun tapAnimatedEmoji(messageId: Long) = emojiBurstsPart.tap(messageId)
    internal fun emojiBurstShown(burst: EmojiBurst) = emojiBurstsPart.shown(burst)

    /** Akce se zprávou: připnutí, smazání, přeposlání, připomínka, nahlášení — viz [ConversationMessageActions]. */
    private val messageActions: ConversationMessageActions = ConversationMessageActions(
        chatId = chatId,
        state = _state,
        scope = viewModelScope,
        repository = repository,
        chatRepository = chatRepository,
        reminderTimeModel = reminderTimeModel,
        reportFlow = reportFlow,
        closeMenus = { menu.closeMenus() },
        unpin = { unpin(it) },
        logger = logger,
    )
    fun cancelPin() = messageActions.cancelPin()
    fun declineSuggestedPost(comment: String) = messageActions.declineSuggestedPost(comment)
    fun cancelDeclineSuggestedPost() = messageActions.cancelDeclineSuggestedPost()
    fun confirmPin(option: Boolean) = messageActions.confirmPin(option)
    fun consumeCallRequest() = messageActions.consumeCallRequest()
    fun undoAddContact(userId: Long) = messageActions.undoAddContact(userId)
    fun consumeAddedContact() = messageActions.consumeAddedContact()
    fun closeRemind() = messageActions.closeRemind()
    fun remind(messageId: Long, at: Long) = messageActions.remind(messageId, at)
    fun consumeForwardRequest() = messageActions.consumeForwardRequest()
    fun cancelDelete() = messageActions.cancelDelete()
    fun confirmDelete(revoke: Boolean, choice: ModerationChoice) = messageActions.confirmDelete(revoke, choice)

    /** Výběr víc zpráv — viz [ConversationSelection]. */
    private val selection: ConversationSelection = ConversationSelection(
        chatId = chatId,
        state = _state,
        scope = viewModelScope,
        repository = repository,
        copyToClipboard = clipboard::copy,
        reportFlow = reportFlow,
        logger = logger,
    )
    fun closeSelection() = selection.closeSelection()
    fun copySelection() = selection.copySelection()
    fun copySelectionWithNames() = selection.copySelectionWithNames()
    fun copySelectionAsMarkdown() = selection.copySelectionAsMarkdown()
    fun forwardSelection() = selection.forwardSelection()
    fun reportSelection() = selection.reportSelection()
    fun deleteSelection() {
        selection.deleteSelection()
        messageActions.loadSenderModeration()
    }

    /** Serializuje stránkování — bez toho by dva rychlé scrolly načetly tutéž stránku dvakrát. */
    private val loadMutex = Mutex()

    /** Už nahlášená zhlédnutí reklam — každé se hlásí jen jednou. */
    private val reportedSponsoredViews = mutableSetOf<Long>()

    /**
     * Ohlášení TDLib, že je chat otevřený (viz [ConversationRepository.openChat]).
     *
     * Běží v [appScope], ne ve `viewModelScope`: ten se při odchodu z chatu zruší a půlka páru
     * by nikdy neodešla — TDLib si otevření počítá, takže by chat zůstal navždy „otevřený",
     * nebo by se zavřel chat, který se ani neotevřel. Zavření na tuhle úlohu proto čeká.
     */
    private val chatOpened = appScope.launch { repository.openChat(chatId) }

    /**
     * Přečtení viditelných zpráv, se kterým se čeká, než se ví, kam konverzace po otevření skočí
     * (`null` = nečeká se).
     *
     * Chat se otevírá u nejnovějších zpráv a skok na první nepřečtenou přijde, až se historie
     * dotáhne. Dokud byly nejnovější zprávy vidět déle než odpočet hlášení (400 ms), nahlásily se
     * jako přečtené — a server posunul hranici až na konec. Ve vlákně komentářů, kde se historie
     * tahá ze serveru, to tak dopadlo pokaždé: příští otevření už nemělo kam skočit, přestože
     * počet nepřečtených zůstal.
     */
    // Deklarace MUSÍ stát před `init`: ten může hlášení pustit ještě během konstrukce.
    private var heldReads: List<Long>? = emptyList()

    /** Pustí hlášení přečtení; když se skákalo, podržené (nejnovější zprávy) propadne. */
    private fun releaseReads(jumped: Boolean) {
        val held = heldReads ?: return
        heldReads = null
        if (!jumped) markRead(held)
    }

    init {
        // Vlastní emoji v úpravě zprávy: smí se jen tam, kde se smí poslat (lokální dotaz).
        viewModelScope.launch {
            val allowed = runCatching { stickerRepository.canUseCustomEmoji(chatId) }.getOrDefault(false)
            _state.update { it.copy(customEmojiAllowed = allowed) }
        }

        // Posouzení podvodu: nejnovější příchozí zprávy, jak se načítají a přicházejí. Starší
        // historie ne — model by se pustil do všeho, co uživatel proscrolluje, a varování u
        // zprávy z loňska nikomu nepomůže.
        scamWarnings?.let { warnings ->
            viewModelScope.launch {
                _state.map { it.info to it.messages.take(SCAM_RECENT_MESSAGES) }
                    .distinctUntilChanged()
                    .collect { (info, recent) ->
                        if (info != null && scamCheckApplies(info)) recent.forEach { warnings.check(chatId, it) }
                    }
            }
        }

        // Termíny z nejnovějších zpráv posoudí model (když je zapnutý a připravený); bez něj
        // nabízejí akce pravidla sama a tady se nic neděje.
        messageContexts?.let { contexts ->
            contexts.reopen(chatId)
            // Rozbor pravidly je drahý (desítky regulárních výrazů na zprávu) a seznam se mění
            // i několikrát za jedno odeslání — dřív se tu na hlavním vlákně rozebíralo všech
            // dvacet zpráv pokaždé znovu a let odeslané zprávy drhnul. Proto: jen se zapnutou AI
            // (bez ní `check` výsledek zahodí), každá podoba zprávy jednou a mimo hlavní vlákno.
            viewModelScope.launch {
                val scanned = HashSet<Pair<Long, Int>>()
                _state.map { it.info to it.messages.take(CONTEXT_RECENT_MESSAGES) }
                    .distinctUntilChanged()
                    .collect { (info, recent) ->
                        if (info == null || info.protectsContent || !contexts.enabled) return@collect
                        val fresh = recent.filter { it.offersContext() && scanned.add(it.id to it.text.hashCode()) }
                        if (fresh.isEmpty()) return@collect
                        val zone = TimeZone.currentSystemDefault()
                        val events = withContext(Dispatchers.Default) {
                            fresh.mapNotNull { message ->
                                val sentAt = Instant.fromEpochSeconds(message.timestamp.toLong()).toLocalDateTime(zone)
                                val event = findMessageContext(message.text, message.entities, sentAt, zone, messageContext.homeCurrency, messageContext.metricUnits).event
                                event?.let { Triple(message, sentAt, it.start) }
                            }
                        }
                        events.forEach { (message, sentAt, start) -> contexts.check(chatId, message.id, message.text, sentAt, start) }
                    }
            }
        }

        viewModelScope.launch {
            voicePlayer.errors.collect { message -> _state.update { it.copy(error = message) } }
        }
        viewModelScope.launch {
            settings.doubleTapReaction.collect { on -> _state.update { it.copy(doubleTapReaction = on) } }
        }
        // Čip „Označit jako přečtené" při diskrétním čtení: kolik nepřečtených v chatu čeká.
        viewModelScope.launch {
            combine(settings.discreetReading, chatRepository.unreadCount(chatId)) { on, unread -> if (on) unread else 0 }
                .distinctUntilChanged()
                .collect { unread -> _state.update { it.copy(discreetUnread = unread) } }
        }

        // Příkazy botů se čtou jednou při otevření; bot je mění zřídka a `getUserFullInfo` je
        // po prvním dotazu lokální. Nezjištěné = žádné, napovídání se neukáže.
        viewModelScope.launch {
            repository.botCommands(chatId).onSuccess { commands -> _state.update { it.copy(botCommands = commands) } }
        }
        // Tlačítko nabídky bota s mini aplikací: totéž čtení, jen pro soukromý chat s botem.
        viewModelScope.launch {
            repository.botMenuApp(chatId).onSuccess { app -> _state.update { it.copy(botMenuApp = app) } }
        }

        // Kým se do skupiny píše; mění se i z jiného zařízení.
        sendAs.observe()

        // Automatické mazání se ukazuje v hlavičce; mění se i z jiného zařízení nebo od protějšku.
        viewModelScope.launch {
            repository.autoDeleteTime(chatId).collect { seconds -> _state.update { it.copy(autoDeleteTime = seconds) } }
        }

        // Dlouhá nahrávka pokračuje, kde uživatel minule přestal — obrazovka to řekne snackbarem.
        viewModelScope.launch {
            playback.resumes.collect { resumed -> _state.update { it.copy(resumedAudio = resumed) } }
        }

        // Dohraná hlasovka pustí další neposlechnutou; která to je, ví jedině konverzace.
        viewModelScope.launch {
            voicePlayer.completions.collect { finished ->
                // Zvuk z článku (`part`) se neřetězí — „další neposlechnutá" po článku nedává smysl.
                if (finished.chatId == chatId && !finished.isVoicePreview && finished.part == VoiceKey.NO_PART) {
                    playback.playNextUnlistened(after = finished.messageId)
                }
            }
        }

        // Odpovědět z prohlížeče médií: prohlížeč se zavře a odpověď se připraví tady.
        viewModelScope.launch {
            replyRequests.request.collect { request ->
                if (request?.chatId == chatId) {
                    replyRequests.consume(chatId)
                    composer.replyFromViewer(request.messageId)
                }
            }
        }

        // Anketa se zakládá ve vlastním formuláři a rozepsanou odpověď si odnesla; formulář
        // ohlásí, že odešla (i naplánovaná), a odpověď zmizí jako po odeslání textu.
        viewModelScope.launch {
            replyRequests.sent.collect { sent ->
                if (sent?.chatId == chatId) {
                    replyRequests.consumeSent(chatId)
                    if (_state.value.replyConsumedBy(sent)) composer.clearReply()
                }
            }
        }

        // Vynořený odložený chat splnil účel, ať se otevřel odkudkoli (seznam, notifikace,
        // hledání). Chat, který ještě spí, zůstává odložený.
        snoozes?.opened(chatId)

        // Záložka z profilu: profil se zavře a konverzace skočí na zprávu.
        viewModelScope.launch {
            jumpRequests?.target?.collect { requested ->
                if (requested?.first == chatId) {
                    jumpRequests.consume(chatId)
                    jumpToMessage(requested.second)
                }
            }
        }

        // Hledat z profilu: profil se zavře a hledání se otevře tady, v konverzaci.
        viewModelScope.launch {
            searchRequests.chatId.collect { requested ->
                if (requested == chatId) {
                    searchRequests.consume(chatId)
                    search.openSearch()
                }
            }
        }

        viewModelScope.launch {
            repository.contentOpenedUpdates(chatId).collect { messageId -> playback.markListened(messageId) }
        }

        viewModelScope.launch {
            repository.chatInfo(chatId).onSuccess { info ->
                _state.update {
                    it.copy(
                        info = info,
                        lastReadOutboxMessageId = info.lastReadOutboxMessageId,
                        /*
                         * Hranice nepřečtených se zachytí JEDNOU při otevření a už se nemění.
                         *
                         * Kdyby se posouvala podle toho, co se právě označilo za přečtené, předěl
                         * by uživateli utíkal pod rukama — a to je jediné místo, podle kterého
                         * pozná, kde přestal.
                         */
                        unreadBoundaryId = info.lastReadInboxMessageId
                            .takeIf { id -> info.unreadCount > 0 && id != 0L },
                        // Koncept z minula (nebo z jiného zařízení) se vrátí do pole.
                        inputFill = info.draftText?.let { text ->
                            InputFill(text, onlyIfEmpty = true, formats = info.draftFormats)
                        },
                        linkPreviewOptions = info.draftLinkPreview ?: LinkPreviewOptions(),
                    )
                }
                info.draftText?.let {
                    composer.draft = ComposedText(it, info.draftFormats)
                    composer.refreshLinkPreview(it)
                }
                info.draftReplyToMessageId?.let { composer.restoreDraftReply(it, info.draftReplyQuote) }
                // Nalezená zpráva má přednost před nepřečtenými — kvůli ní chat otevřel.
                if (initialMessageId != null) {
                    releaseReads(jumped = false)
                    jumpToMessage(initialMessageId, fromSearch = true)
                } else {
                    releaseReads(jumped = loadUntilUnread())
                }
            }.onFailure { releaseReads(jumped = false) }
            composer.draftRestored.complete(Unit)
        }

        viewModelScope.launch {
            // Inline dotaz z tlačítka bota v jiném chatu, pro který si uživatel vybral tenhle.
            // Až po konceptu: ten jde do prázdného pole a dotaz by jinak vytlačil.
            inlineQueryRequests.request.collect { request ->
                if (request?.chatId == chatId) {
                    composer.draftRestored.await()
                    inlineQueryRequests.consume(chatId)
                    suggestions.useInlineBot(request.bot, request.query)
                }
            }
        }

        viewModelScope.launch {
            // Sdílení do tohohle chatu. Odběr trvá po celou dobu konverzace: obsah z čipu
            // v shellu přijde, až uživatel v otevřené konverzaci ťukne na „Vložit sem".
            shareRequests.deliveries(chatId).collect { content ->
                composer.draftRestored.await()
                attachments.receiveShared(content)
            }
        }

        viewModelScope.launch {
            // Mimo kanály vrací TDLib prázdný seznam, takže se nemusí ptát na typ chatu.
            repository.sponsoredMessages(chatId).onSuccess { sponsored ->
                _state.update { it.copy(sponsored = sponsored) }
            }
        }

        loadOlder()
        observeUpdates()
        observePinned()
        observeLiveLocations()
    }

    /**
     * Živé polohy pro dock nad konverzací ([ConversationState.liveLocations]).
     *
     * Odkud: při otevření [ConversationRepository.recentLiveLocations] (vlastní i cizí), pak nové
     * a upravené zprávy (`observeUpdates`) a vlastní sdílení, které hlídá [LiveLocationRepository]
     * — to dorazí i u zprávy, kterou žádný update konverzace nepřinesl. Zastavená poloha přijde
     * úpravou s dobou zkrácenou na okamžik zastavení a odejde tím sama; prošlou odebere odpočet
     * na nejbližší konec.
     *
     * Ve vlákně komentářů dock není: poloha patří diskusní skupině.
     */
    private fun observeLiveLocations() {
        if (threadRootId != null) return
        viewModelScope.launch {
            repository.recentLiveLocations(chatId).onSuccess { found ->
                _state.update { state ->
                    state.copy(
                        liveLocations = found.fold(state.liveLocations) { list, message -> list.withLiveLocation(message, now()) },
                    )
                }
            }
        }
        // V tématu jen zprávy tématu — ty přinese hledání a nové zprávy (repository je zužuje),
        // seznam vlastních sdílení o tématech neví.
        if (forumTopicId == null && savedTopicId == null && directTopicId == null) viewModelScope.launch {
            liveLocations.active.collect { active ->
                active.filter { it.chatId == chatId && _state.value.liveLocations.none { m -> m.id == it.messageId } }
                    .forEach { live ->
                        repository.message(chatId, live.messageId).onSuccess { message ->
                            _state.update { it.copy(liveLocations = it.liveLocations.withLiveLocation(message, now())) }
                        }
                    }
            }
        }
        viewModelScope.launch {
            _state.map { it.liveLocations.nextLiveEnd() }
                .distinctUntilChanged()
                .collectLatest { end ->
                    if (end == null) return@collectLatest
                    delay((end - now()).coerceAtLeast(0).seconds)
                    _state.update { it.copy(liveLocations = it.liveLocations.withoutEnded(now())) }
                }
        }
    }

    private fun now(): Long = Clock.System.now().epochSeconds

    /**
     * Připnuté zprávy.
     *
     * Na změnu se seznam **čte znovu**, ne odvozuje z updatu: update nese jen jednu zprávu a „odepnout
     * vše" jich pošle celou dávku. `conflate` z dávky udělá jedno čtení — dokud jedno běží, další
     * signály se slijí do jednoho. Odběr stojí před prvním čtením, aby se mezi nimi nic neztratilo.
     */
    private fun observePinned() {
        viewModelScope.launch {
            repository.pinnedMessageChanges(chatId)
                .onStart { emit(Unit) }
                .conflate()
                .collect { refreshPinned() }
        }
    }

    private suspend fun refreshPinned() {
        repository.pinnedMessages(chatId)
            .onSuccess { pinned ->
                _state.update { it.copy(pinned = pinned.messages, canUnpin = pinned.canUnpin) }
            }
            .onFailure { failure -> logger.w(failure) { "Připnuté zprávy se nepodařilo načíst." } }
    }

    /**
     * Odepne zprávu.
     *
     * Z pruhu zmizí **hned**: ťuknutí na křížek, po kterém řádek chvíli visí, působí jako nefunkční.
     * Potvrzení dorazí updatem a seznam se přečte znovu; při selhání se přečte taky, takže se
     * zpráva vrátí. Místo potvrzovacího dialogu je „Vrátit" ve snackbaru — odepnutí jde vzít
     * zpátky, a dialog před vratnou akcí jen zdržuje.
     */
    fun unpin(message: ChatMessage) {
        _state.update { it.copy(pinned = it.pinned.filterNot { m -> m.id == message.id }) }

        viewModelScope.launch {
            repository.unpin(chatId, message.id)
                .onSuccess { _state.update { it.copy(unpinned = message) } }
                .onFailure { failure ->
                    _state.update { it.copy(error = failure.reported(logger, Res.string.conversation_unpin_failed)) }
                    refreshPinned()
                }
        }
    }

    /**
     * Odepne všechny připnuté (v tématu jen jeho). Pruh zmizí hned, jako u jedné zprávy; při
     * selhání se seznam přečte znovu. „Vrátit" tu není — vracet by se muselo připnout každou
     * zprávu zvlášť, proto se obrazovka ptá předem.
     */
    fun unpinAll() {
        _state.update { it.copy(pinned = emptyList()) }
        viewModelScope.launch {
            repository.unpinAll(chatId).onFailure { failure ->
                _state.update { it.copy(error = failure.reported(logger, Res.string.conversation_unpin_all_failed)) }
                refreshPinned()
            }
        }
    }

    /** „Vrátit" ze snackbaru. */
    fun undoUnpin(message: ChatMessage) {
        viewModelScope.launch {
            repository.pin(chatId, message.id).onFailure { failure ->
                _state.update { it.copy(error = failure.reported(logger, Res.string.conversation_repin_failed)) }
            }
        }
    }

    /** Obrazovka snackbar k odepnutí ukázala. */
    fun consumeUnpinned() {
        _state.update { it.copy(unpinned = null) }
    }

    /**
     * Nahlásí zhlédnutí reklamy. Volá obrazovka, teprve až je vidět celá.
     *
     * Hlásí se **jen jednou za relaci** — opakované hlášení téhož zhlédnutí při každém scrollu
     * by nafouklo statistiku inzerenta.
     */
    fun reportSponsoredSeen(messageId: Long) {
        if (!reportedSponsoredViews.add(messageId)) return
        viewModelScope.launch { repository.reportSponsoredView(chatId, messageId) }
    }

    /**
     * Nahlásí, jak dlouho a jak celé byly zprávy vidět (`sendMessageViewMetrics`). Volá
     * obrazovka, když zpráva zmizí, obrazovka zanikne nebo zobrazení přesáhne 5 minut
     * (`MessageViewMetricsTracker`); [metrics] nesou KLÍČE položek, ne id.
     *
     * Běží v [appScope]: poslední dávka odchází při zavírání obrazovky, kdy se `viewModelScope`
     * ruší. Diskrétní čtení nehlásí nic — jako u přečtení se tím nemá prozradit, co uživatel viděl.
     */
    fun reportViewMetrics(metrics: List<MessageViewMetrics>) {
        if (metrics.isEmpty() || settings.discreetReading.value) return
        val current = _state.value
        val reports = metrics.mapNotNull { viewed ->
            // Rozesílaná zpráva má dočasné id, které server nezná.
            val message = current.messages.firstOrNull { current.keyOf(it) == viewed.messageId } ?: return@mapNotNull null
            if (message.sendState == SendState.Sending || message.sendState == SendState.Failed) return@mapNotNull null
            viewed.copy(messageId = message.id)
        }
        if (reports.isEmpty()) return
        appScope.launch {
            reports.forEach { viewed ->
                repository.reportViewMetrics(chatId, viewed).onFailure { failure ->
                    logger.w(failure) { "Metriky zobrazení zprávy ${viewed.messageId} se nepodařilo nahlásit." }
                }
            }
        }
    }

    /**
     * Zpracuje kliknutí na reklamu.
     *
     * Hlášení odchází **před** otevřením odkazu — jakmile se otevře prohlížeč, aplikace jde do
     * pozadí a doručení by už nebylo jisté.
     */
    fun onSponsoredClick(sponsored: SponsoredMessage) {
        viewModelScope.launch {
            repository.reportSponsoredClick(chatId, sponsored.messageId)
            urlOpener.open(sponsored.url)
        }
    }

    /**
     * Nahlášení reklamy. Po nahlášení (i po skrytí reklam nebo když už reklama neexistuje)
     * zmizí z konverzace, jako v oficiálním klientovi — ukazovat dál něco, co uživatel právě
     * nahlásil, by působilo, že se nic nestalo. Další reklamy se načtou při příštím otevření.
     */
    fun reportSponsored(sponsored: SponsoredMessage) {
        sponsoredReportFlow.start(chatId, messageIds = listOf(sponsored.messageId)) {
            _state.update { state -> state.copy(sponsored = state.sponsored.filterNot { it.messageId == sponsored.messageId }) }
        }
    }

    /**
     * Načte další stránku směrem do minulosti.
     *
     * Konec historie se pozná jen podle prázdné odpovědi — TDLib žádný příznak „jsi na začátku"
     * nedává. Proto [ConversationState.reachedStart].
     */
    fun loadOlder() {
        viewModelScope.launch { loadOlderPage() }
    }

    /**
     * Načte jednu stránku do minulosti.
     *
     * Na zámek se **čeká**, nepřeskakuje se. Kdyby se volání při probíhajícím načítání rovnou
     * vzdalo, dotažení kvůli skoku na nepřečtené by se minulo s prvním načtením při otevření
     * chatu a spotřebovalo by všechny pokusy naprázdno.
     */
    private suspend fun loadOlderPage() {
        loadMutex.withLock {
            if (_state.value.loading || _state.value.reachedStart) return@withLock
            _state.update { it.copy(loading = true) }

            // Nula znamená „od nejnovější"; jinak navazujeme pod nejstarší už načtenou zprávou.
            val from = _state.value.messages.lastOrNull()?.id ?: 0L

            repository.history(chatId = chatId, fromMessageId = from)
                .onSuccess { page ->
                    _state.update { state ->
                        state.withOlderPage(oldestId = from, page = page).copy(loading = false, error = null)
                    }
                    resolveMissingQuotes()
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(loading = false, error = failure.reported(logger, Res.string.conversation_history_failed))
                    }
                }
        }
    }

    /**
     * Skok na datum jde jen v celém chatu: `getChatMessageByDate` zná celý chat, takže ve vlákně
     * komentářů nebo v tématu by trefil zprávu mimo něj.
     */
    val canJumpToDate: Boolean get() =
        threadRootId == null && forumTopicId == null && savedTopicId == null && directTopicId == null

    /**
     * Stránka směrem k PŘÍTOMNOSTI — jen když je načtené okno v historii ([ConversationState.reachedEnd]
     * neplatí). Obrazovka ji chce, když se uživatel blíží ke spodní hraně seznamu.
     */
    fun loadNewer() {
        viewModelScope.launch {
            loadMutex.withLock {
                val state = _state.value
                if (state.loading || state.reachedEnd) return@withLock
                val newestId = state.messages.firstOrNull()?.id ?: return@withLock
                _state.update { it.copy(loading = true) }

                // `fromMessageId` je ve výsledku taky, proto o jednu víc; `withNewerPage` ji odfiltruje.
                repository.history(chatId = chatId, fromMessageId = newestId, limit = PAGE_SIZE + 1, offset = -PAGE_SIZE)
                    .onSuccess { page ->
                        _state.update { it.withNewerPage(newestId, page).copy(loading = false, error = null) }
                        resolveMissingQuotes()
                    }
                    .onFailure { failure ->
                        _state.update {
                            it.copy(loading = false, error = failure.reported(logger, Res.string.conversation_history_failed))
                        }
                    }
            }
        }
    }

    /**
     * Okno kolem zprávy: půl stránky starších, půl novějších. Nahradí načtený seznam, takže se
     * dá skočit libovolně hluboko — historie se nedotahuje od nejnovější až k cíli.
     *
     * S [requireTarget] se okno použije jen, když v něm zpráva je (smazaná zpráva by jinak
     * zahodila, co uživatel četl, a nikam by neskočila). Vrací, jestli se okno použilo.
     */
    private suspend fun loadAround(messageId: Long, requireTarget: Boolean): Boolean = loadMutex.withLock {
        _state.update { it.copy(loading = true) }
        repository.history(chatId = chatId, fromMessageId = messageId, limit = PAGE_SIZE, offset = -PAGE_SIZE / 2)
            .fold(
                onSuccess = { page ->
                    val usable = page.isNotEmpty() && (!requireTarget || page.any { it.id == messageId })
                    _state.update { if (usable) it.withWindow(page).copy(loading = false, error = null) else it.copy(loading = false) }
                    if (usable) resolveMissingQuotes()
                    usable
                },
                onFailure = { failure ->
                    _state.update {
                        it.copy(loading = false, error = failure.reported(logger, Res.string.conversation_history_failed))
                    }
                    false
                },
            )
    }

    /**
     * Z okna v historii zpátky k nejnovějším zprávám (tlačítko „dolů"). Seznam začne znovu od
     * nejnovější — dostránkovat se k ní by znamenalo načíst všechno mezi.
     */
    fun jumpToLatest() {
        viewModelScope.launch {
            loadMutex.withLock {
                _state.update { it.copy(messages = emptyList(), reachedStart = false, reachedEnd = true, window = it.window + 1) }
            }
            loadOlderPage()
            val newest = _state.value.messages.firstOrNull() ?: return@launch
            _state.update { it.copy(scrollTarget = ScrollTarget(newest.id, ScrollAnchor.Bottom)) }
        }
    }

    /** Běžící hledání nejstarší zmínky nebo reakce; druhé ťuknutí mezitím nic nespustí. */
    private var markJob: Job? = null

    /**
     * Tlačítko „@" nebo „❤": skok na nejstarší nepřečtenou zmínku, nebo vlastní zprávu
     * s nepřečtenou reakcí.
     *
     * Cíl se hned hlásí jako zhlédnutý, nečeká se, až ho obrazovka ohlásí sama. Jinak by další
     * ťuknutí během pár set milisekund našlo TUTÉŽ zprávu a tlačítko by nevedlo dál. Přečte se
     * tím i historie po ni — stejně jako kdyby na ni uživatel doroloval.
     */
    fun jumpToUnreadMark(kind: UnreadMarkKind) {
        if (markJob?.isActive == true) return
        markJob = viewModelScope.launch {
            repository.oldestUnreadMark(chatId, kind).fold(
                onSuccess = { messageId ->
                    messageId ?: return@fold
                    jumpToMessage(messageId, fromSearch = true)
                    // Při diskrétním čtení ne: zhlédnutá zmínka by přečetla i historii po ni.
                    // Tlačítko pak vede pořád na tutéž zmínku, dokud se chat nepřečte.
                    if (!settings.discreetReading.value) repository.markRead(chatId, listOf(messageId))
                },
                onFailure = { failure ->
                    _state.update { it.copy(error = failure.reported(logger, Res.string.conversation_unread_mark_failed)) }
                },
            )
        }
    }

    /** Podržení tlačítka „@" nebo „❤" → „Označit vše jako přečtené". */
    fun readAllMarks(kind: UnreadMarkKind) {
        viewModelScope.launch {
            repository.readAllMarks(chatId, kind).onFailure { failure ->
                _state.update { it.copy(error = failure.reported(logger, Res.string.conversation_unread_mark_failed)) }
            }
        }
    }

    /**
     * Dotáhne historii tak daleko, aby v ní byla hranice nepřečtených, a pak na ni skočí.
     *
     * Načítá se **od nejnovější dozadu**, ne od hranice dopředu. TDLib by uměl i to (záporný
     * `offset` v `getChatHistory`), ale stránkování by pak muselo umět oba směry: kotva uprostřed
     * znamená, že se dá dojít i na konec toho, co je načtené, směrem k novějším. Za tu složitost
     * to nestojí — pár stránek zpátky je z lokální databáze rychlých.
     *
     * Proto je tu [MAX_UNREAD_PAGES]: pár stránek od nejnovější pokryje obvyklý případ a seznam
     * zůstane souvislý až k přítomnosti. Kdo má nepřečtených stovky, dostane okno kolem hranice
     * ([loadAround]) a k novějším se dostránkuje.
     */
    private suspend fun loadUntilUnread(): Boolean {
        val boundary = _state.value.unreadBoundaryId ?: return false

        // Hranice je načtená, jakmile máme aspoň jednu zprávu z přečtené části. Bez ní se předěl
        // stejně nevykreslí, takže skok nemá kam mířit.
        val loaded = loadUntil(MAX_UNREAD_PAGES) { messages -> messages.any { it.id <= boundary } } ||
            // Hranice je poslední přečtená zpráva a mohla být smazaná — okno je dobré i bez ní.
            loadAround(boundary, requireTarget = false)
        if (!loaded) return false

        // První nepřečtená je nejstarší PŘÍCHOZÍ nad hranicí — vlastní odpověď nepřečtená není.
        val firstUnread = firstUnreadId(_state.value.messages, boundary) ?: return false
        _state.update { it.copy(scrollTarget = ScrollTarget(firstUnread, ScrollAnchor.Top)) }
        return true
    }

    /**
     * Dotahuje starší stránky, dokud načtené zprávy nesplní [loaded], nejvýš však [maxPages].
     *
     * Vrací, jestli podmínka nakonec platí. Konec historie ukončí dotahování taky — dál už
     * není co načíst.
     */
    private suspend fun loadUntil(maxPages: Int, loaded: (List<ChatMessage>) -> Boolean): Boolean {
        var pages = 0
        while (pages < maxPages) {
            val state = _state.value
            if (state.reachedStart || loaded(state.messages)) break
            loadOlderPage()
            pages++
        }
        return loaded(_state.value.messages)
    }

    /**
     * Dotáhne historii, aby mělo shrnutí co shrnovat.
     *
     * Hned po otevření chatu je načtená jediná stránka ([PAGE_SIZE]), takže bez tohohle kroku
     * shrnutí „posledních [SUMMARY_RECENT_MESSAGES] zpráv" vezme jen ji — a výsledek by závisel
     * na tom, kolik toho uživatel předtím odscrolloval. Nepřečtené se dotahují až za hranici,
     * jinak stačí [count] zpráv.
     *
     * Strop stránek je nutný: v chatu s tisíci nepřečtenými by se jinak dotahovalo dlouho
     * a do limitu vstupu se stejně vejde zlomek.
     */
    suspend fun loadForSummary(count: Int) {
        val boundary = _state.value.unreadBoundaryId
        loadUntil(MAX_SUMMARY_PAGES) { messages -> summaryHasEnough(messages, boundary, count) }
    }

    /**
     * Skočí na citovanou zprávu a krátce ji zvýrazní.
     *
     * Citace míří často na zprávu, která už je načtená — pak je skok okamžitý. Jinak se načte
     * okno kolem ní ([loadAround]), takže skočit jde libovolně hluboko, i na zprávu z hledání
     * starou roky.
     *
     * Zvýraznění je nutné. Po skoku uprostřed historie by jinak nebylo poznat, na kterou
     * z bublin na obrazovce citace mířila.
     */
    /**
     * Skok na zprávu. [anchor] `Center` je pro citaci a hledání — kolem je vidět, na co zpráva
     * navazuje. Pruh připnutých posílá `Top`: ukazuje tu připnutou, kterou uživatel míjí, takže
     * ji skok musí posadit NAD viditelnou část, aby pruh mohl přejít na předchozí. Se středem
     * se nad cílem odkryje kus historie a pruh přeskočí rovnou k nejstarší připnuté.
     */
    fun jumpToMessage(
        messageId: Long,
        fromSearch: Boolean = false,
        anchor: ScrollAnchor = ScrollAnchor.Center,
    ) {
        viewModelScope.launch {
            // Dojíždí se jen ke zprávě, která už v seznamu byla. Po výměně okna (`loadAround`) je
            // seznam nový a začíná u spodní hrany okna — tam se dotahují novější zprávy a indexy by
            // se dojíždění posouvaly pod rukama.
            val alreadyLoaded = _state.value.messages.any { it.id == messageId }
            // Nenačtená zpráva: obrazovka hned začne odjíždět (viz `JumpTransition`), načítání
            // mezitím běží. Id rostou s časem, takže starší = menší id než nejstarší načtená.
            val older = !alreadyLoaded && _state.value.messages.lastOrNull()?.let { messageId < it.id } != false
            if (!alreadyLoaded) _state.update { it.copy(pendingJump = PendingJump(messageId, older)) }
            val loaded = alreadyLoaded || loadAround(messageId, requireTarget = true)
            if (!loaded) {
                _state.update { it.copy(pendingJump = null) }
                // Okno kolem ní zprávu neobsahuje — byla smazaná (nebo nešla načíst, to ohlásilo
                // `loadAround` samo).
                _state.update {
                    if (it.error != null) it else it.copy(
                        error = UiText(if (fromSearch) Res.string.conversation_message_deleted else Res.string.conversation_quote_deleted),
                    )
                }
                return@launch
            }

            _state.update {
                it.copy(
                    scrollTarget = ScrollTarget(
                        messageId,
                        anchor,
                        animate = alreadyLoaded,
                        older = older.takeUnless { alreadyLoaded },
                    ),
                    highlightedMessageId = messageId,
                )
            }

            delay(HIGHLIGHT_DURATION_MS)
            // Mezitím mohl uživatel skočit jinam; cizí zvýraznění se nemaže.
            _state.update {
                if (it.highlightedMessageId == messageId) it.copy(highlightedMessageId = null) else it
            }
        }
    }

    /** Obrazovka hlásí, že skok proběhl. Bez toho by se opakoval při každé rekompozici. */
    fun consumeScrollTarget() {
        _state.update { it.copy(scrollTarget = null, pendingJump = null) }
    }

    private fun observeUpdates() {
        viewModelScope.launch {
            repository.newMessages(chatId).collect { message ->
                _state.update {
                    // Rozepsaná zpráva chodí tudy DŘÍV, než `send` vrátí výsledek. Let se proto
                    // musí navázat už tady, ve stejné změně stavu, ve které bublina vzniká —
                    // jinak se na snímek vykreslí na místě, zmizí a teprve pak přiletí. Let se
                    // váže i na zprávu, kterou `withMessage` zahodí: klíč položky je pořád její
                    // dočasné id.
                    it.withMessage(message).copy(
                        flight = it.flight.landingOn(message),
                        liveLocations = it.liveLocations.withLiveLocation(message, now()),
                        // Dopsaná odpověď bota nahradí rozepsanou v témže snímku.
                        pendingReply = it.pendingReply.afterNewMessage(message),
                        pendingReplacedBy = if (it.pendingReply != null && !message.isOutgoing) message.id else it.pendingReplacedBy,
                    )
                }
                resolveMissingQuotes()
            }
        }

        pendingReplies.observe()

        viewModelScope.launch {
            repository.replyMarkup(chatId).collect { markup ->
                _state.update { it.copy(replyMarkup = markup) }
                if (markup is ChatReplyMarkup.ForceReply) bots.applyForceReply(markup.messageId)
            }
        }

        viewModelScope.launch {
            repository.editedMessages(chatId).collect { message ->
                _state.update {
                    it.copy(
                        messages = merge(it.messages, listOf(message)),
                        // Upravená připnutá zpráva se musí přepsat i v pruhu, jinak by ukazoval starý text.
                        pinned = it.pinned.map { pinned -> if (pinned.id == message.id) message else pinned },
                        // Každý posun živé polohy je úprava zprávy — i v docku, i když zpráva
                        // v načtené historii není.
                        liveLocations = it.liveLocations.withLiveLocation(message, now()),
                    )
                }
            }
        }

        // Počty jsou za celý chat; ve vlákně komentářů by vedly do zpráv mimo něj.
        if (threadRootId == null) {
            viewModelScope.launch {
                repository.unreadMarks(chatId).collect { marks ->
                    _state.update { it.copy(unreadMarks = marks) }
                }
            }
            viewModelScope.launch {
                repository.unreadMarkChanges(chatId).collect { change ->
                    _state.update { state ->
                        state.copy(
                            messages = state.messages.map { message ->
                                when {
                                    message.id != change.messageId -> message
                                    change.kind == UnreadMarkKind.PollVote -> message.copy(hasUnreadPollVotes = change.hasUnread)
                                    else -> message.copy(hasUnreadReactions = change.hasUnread, newReactions = change.reactions)
                                }
                            },
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            repository.interactionUpdates(chatId).collect { update ->
                _state.update { state ->
                    state.copy(
                        messages = state.messages.map { message ->
                            if (message.id == update.messageId) {
                                message.copy(reactions = update.reactions, postStats = update.postStats)
                            } else {
                                message
                            }
                        },
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.sentMessageUpdates(chatId).collect { update ->
                // TDLib po potvrzení zprávě ZMĚNÍ id, takže ji nestačí aktualizovat — původní
                // se musí ze seznamu odebrat, jinak by tam zůstala dvakrát.
                val (oldId, message) = when (update) {
                    is SentMessageUpdate.Succeeded -> update.oldMessageId to update.message
                    is SentMessageUpdate.Failed -> update.oldMessageId to update.message
                }

                _state.update { state ->
                    state.withSentMessage(oldId, message).copy(
                        // Potvrzená zpráva dostane nové id; v docku nesmí zůstat ta dočasná.
                        liveLocations = state.liveLocations.filterNot { it.id == oldId }.withLiveLocation(message, now()),
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.readOutboxUpdates(chatId).collect { lastReadId ->
                _state.update { it.copy(lastReadOutboxMessageId = lastReadId) }
            }
        }

        viewModelScope.launch {
            /*
             * Kdo píše, se kreslí jeho tváří v místě, kam jeho zpráva naskočí. V soukromém chatu je
             * to protějšek (odesílatel = chat), tvář nese [ChatInfo]; jinde se dohledá — z keše
             * odesílatelů, takže opakované ohlášení se na nic neptá. Když to nejde, trojtečka
             * zůstane bez tváře: vědět, ŽE někdo píše, je důležitější než kdo.
             */
            chatRepository.typing(chatId)
                // V tématu jen psaní v něm; ve skupině jako celku píše kdokoli kdekoli.
                .map { typing -> typing?.takeIf { forumTopicId == null || it.topicId == forumTopicId } }
                .map { typing ->
                    typing?.let {
                        Typing(
                            activity = it.activity,
                            sender = if (it.senderId == chatId) null else repository.sender(it.senderId).getOrNull(),
                        )
                    }
                }
                .collect { typing -> _state.update { it.copy(typing = typing) } }
        }

        viewModelScope.launch {
            // Práva psát se mění za běhu: správce je změní, uživatel se přidá (i odjinud).
            repository.accessUpdates(chatId).collect { access ->
                _state.update {
                    it.copy(
                        info = it.info?.copy(
                            canJoin = access.canJoin,
                            writeAccess = access.writeAccess,
                            content = access.content,
                        ),
                    )
                }
            }
        }

        viewModelScope.launch {
            // Přejmenování a nová fotka za běhu. `chatInfo` se čte jednou při otevření, takže
            // hlavička jinak ukazovala starý název a fotku, dokud se chat neotevřel znovu.
            // Před načtením `info` se nic nepřepisuje — to pak přijde rovnou čerstvé.
            chatRepository.appearance(chatId).filterNotNull().collect { appearance ->
                _state.update {
                    it.copy(info = it.info?.copy(title = appearance.title, avatarFileId = appearance.avatarFileId))
                }
            }
        }

        viewModelScope.launch {
            // Ztlumit v nabídce ⋮ a v kanálu bez práva publikovat místo pole.
            chatRepository.muted(chatId).collect { muted -> _state.update { it.copy(muted = muted) } }
        }

        viewModelScope.launch {
            // Nahlásit a volby ve sheetu smazání (nabídka ⋮). Bez nich se nahlášení nenabídne.
            chatRepository.spamActions(chatId)
                .onSuccess { spam -> _state.update { it.copy(spam = spam) } }
                .onFailure { failure -> logger.w(failure) { "Volby proti spamu u chatu $chatId se nepodařilo zjistit." } }
        }

        viewModelScope.launch {
            // Zablokování odjinud (profil, jiné zařízení): sheet smazání jinak dál nabízel
            // „a zablokovat" u někoho, kdo už zablokovaný je, nebo naopak ne.
            chatRepository.blockChanges().filter { it.chatId == chatId }.collect { change ->
                _state.update { it.copy(spam = it.spam?.copy(isBlocked = change.blocked)) }
            }
        }

        viewModelScope.launch {
            // Ochrana obsahu přepnutá správcem za běhu: výběr textu a akce, které obsah vynášejí
            // ven, se řídí `protectsContent`, a ten se jinak četl jen při otevření chatu.
            repository.protectedContentUpdates(chatId).collect { protects ->
                _state.update { it.copy(info = it.info?.copy(protectsContent = protects)) }
            }
        }

        // Chat s botem: popis do prázdného chatu a tlačítko Start. Ve vlákně komentářů bot není.
        if (threadRootId == null) {
            viewModelScope.launch {
                repository.botIntro(chatId).onSuccess { intro -> _state.update { it.copy(botIntro = intro) } }
            }
            viewModelScope.launch {
                botStartRequests.parameter(chatId).collect { parameter ->
                    _state.update { it.copy(botStartParameter = parameter) }
                }
            }
        }

        viewModelScope.launch {
            repository.hasScheduledMessages(chatId).collect { has ->
                _state.update { it.copy(hasScheduledMessages = has) }
                // Otevřený seznam se přečte znovu: naplánovaná zpráva mohla mezitím odejít.
                if (_state.value.scheduledMessages != null) {
                    if (has) scheduled.loadScheduled() else _state.update { it.copy(scheduledMessages = null) }
                }
            }
        }

        viewModelScope.launch {
            repository.presenceUpdates(chatId).collect { presence ->
                _state.update { it.copy(presence = presence) }
            }
        }

        viewModelScope.launch {
            // „128 členů, 12 online" v hlavičce skupiny. Online hlásí TDLib jen otevřenému
            // chatu (`openChat` výš), a dokud je otevřený, posílá změny sám.
            chatRepository.groupMembers(chatId).collect { members -> _state.update { it.copy(members = members) } }
        }

        viewModelScope.launch {
            repository.deletedMessages(chatId).collect { ids ->
                val removed = ids.toSet()
                _state.update { state ->
                    state.copy(
                        messages = state.messages.filterNot { it.id in removed },
                        itemKeys = state.itemKeys - removed,
                        liveLocations = state.liveLocations.filterNot { it.id in removed },
                    )
                }
                // Smazaná připnutá zpráva: nejnovější připnutá je teď jiná (nebo žádná).
                if (_state.value.pinned.any { it.id in removed }) refreshPinned()
                // Upravovaná zpráva zmizela (smazaná jinde) — není co upravovat.
                if (_state.value.editing?.id in removed) composer.cancelEdit()
            }
        }
    }

    /** Hláška z platformy (výběr souboru selhal) do snackbaru. */
    fun reportError(message: UiText) {
        _state.update { it.copy(error = message) }
    }

    /**
     * Odchod z chatu: čekající uložení konceptu odejde hned, ne až po odpočtu.
     *
     * Běží v `appScope`, takže ho zrušení ViewModelu nezastaví.
     */
    override fun onCleared() {
        // Konverzace je pryč, takže se na ni uživatel nedívá. Musí odejít DŘÍV než cokoli, co
        // může skončit předčasným `return` — TDLib by jinak chat považoval za otevřený dál.
        appScope.launch {
            chatOpened.join()
            repository.closeChat(chatId)
        }

        // Hlasovka z chatu, který se zavřel, nemá kde ukazovat průběh ani jak se zastavit.
        // Hudba hraje dál — ovládá se z notifikace a zamykací obrazovky (MusicQueue).
        voicePlayer.stop(chatId, keepMusic = true)
        recorder.clear()
        composer.saveOnLeave()
    }

    /**
     * Doplní citované zprávy, které nejsou v načtené historii.
     *
     * Odpovídá se skoro vždy na něco nedávného, takže většina citací se najde v už načtených
     * zprávách zadarmo. Tohle řeší zbytek — typicky odpověď na starou zprávu hluboko v historii.
     */
    private fun resolveMissingQuotes() {
        val loaded = _state.value.messages.associateBy { it.id }
        val missing = _state.value.messages
            .mapNotNull { it.replyToMessageId }
            .filter { it !in loaded && it !in _state.value.quotes }
            .distinct()

        if (missing.isEmpty()) return

        viewModelScope.launch {
            missing.forEach { messageId ->
                repository.message(chatId, messageId).onSuccess { quoted ->
                    _state.update { it.copy(quotes = it.quotes + (messageId to quoted)) }
                }
            }
        }
    }

    /**
     * Označí viditelné zprávy za přečtené. Volá obrazovka podle toho, co je reálně na displeji.
     *
     * Vlastní zprávy se odfiltrují **tady**, ne v obrazovce: ta zná jen id viditelných položek
     * a dohledávat si k nim zprávy by znamenalo držet je kvůli tomu v kompozici.
     */
    fun markRead(messageIds: List<Long>) {
        if (messageIds.isEmpty()) return
        // Diskrétní čtení: viditelné zprávy se nehlásí vůbec. Přečtení pošle až vlastní zpráva
        // ([readAfterOwnMessage]) nebo „Označit jako přečtené" ([readNow]).
        if (settings.discreetReading.value) {
            logger.d { "Diskrétní čtení: ${messageIds.size} viditelných zpráv se nehlásí" }
            return
        }
        if (heldReads != null) {
            heldReads = messageIds
            return
        }

        // Obrazovka posílá klíče položek, ne id — u odeslaných zpráv se liší.
        val incoming = _state.value.messagesToView(messageIds.toSet())
        if (incoming.isEmpty()) return
        viewModelScope.launch { repository.markRead(chatId, incoming) }
    }

    /**
     * Diskrétní čtení: vlastní zpráva v chatu prozradí, že uživatel chat četl, takže se přečte
     * celý — protějšek by jinak viděl odpověď na zprávu, kterou „nečetl". Bez diskrétního čtení
     * nic: přečtení hlásí seznam sám, jak se zprávy ukazují.
     */
    private fun readAfterOwnMessage() {
        if (settings.discreetReading.value) readNow()
    }

    /**
     * „Označit jako přečtené" při diskrétním čtení: přečte celý chat (nejnovější zprávu, zmínky
     * a reakce), stejně jako tatáž volba v seznamu chatů.
     */
    fun readNow() {
        // Poslech nahlášený s přečtením: kdo chat přečetl, ten hlasovku taky poslechl.
        val listened = playback.discreetListened.toList()
        playback.discreetListened.clear()
        viewModelScope.launch {
            chatRepository.readChat(chatId).onFailure { failure ->
                logger.w(failure) { "Chat se nepodařilo označit jako přečtený." }
            }
            listened.forEach { messageId ->
                repository.openContent(chatId, messageId).onFailure { failure ->
                    logger.w(failure) { "Poslechnutí hlasovky nebo kolečka se nepodařilo nahlásit." }
                }
            }
        }
    }

    /**
     * Otevře nabídku nad zprávou.
     *
     * Emoji a příznaky akcí se načítají **současně** — jsou to dva nezávislé dotazy a nabídka
     * čeká na oba, takže sériově by se čekání zdvojnásobilo.
     *
     * Prázdná nabídka emoji **není chyba**: v Uložených zprávách Telegram místo reakcí používá
     * tagy a `getMessageAvailableReactions` vrátí prázdný seznam. Dřív se kvůli tomu nabídka
     * neotevřela vůbec a nešlo ani odpovědět.
     */
    /**
     * Jediný vstup akcí nad zprávou — z bloků seznamu i z nabídky (viz [MessageAction]).
     *
     * Navigaci ([MessageAction.OpenMedia]) a sheet sady samolepek ([MessageAction.OpenStickerSet])
     * vyřizuje obrazovka dřív, než sem akce dojde; tady by neměly co dělat.
     */
    fun onAction(messageId: Long, action: MessageAction) {
        when (action) {
            is MessageAction.ShowMenu -> menu.showMessageMenu(messageId, action.items, action.pressed, action.pollOptionId, action.selection)
            MessageAction.CloseMenu -> menu.closeMenus()
            MessageAction.ApproveSuggestedPost -> messageActions.approveSuggestedPost(messageId)
            MessageAction.DeclineSuggestedPost -> messageActions.askDeclineSuggestedPost(messageId)
            MessageAction.ShowOriginalContent -> messageActions.showOriginalContent(messageId)
            MessageAction.ShowNoticeMenu -> menu.showNoticeMenu(messageId)
            is MessageAction.DeleteNotice -> _state.update {
                it.copy(
                    noticeMenu = null,
                    pendingDelete = PendingDelete(
                        messageId = messageId,
                        canDeleteForAll = action.canDeleteForAll,
                        canDeleteForSelf = action.canDeleteForSelf,
                    ),
                )
            }
            is MessageAction.React -> reactions.toggleReaction(messageId, action.emoji)
            MessageAction.QuickReact -> reactions.quickReact(messageId)
            MessageAction.ShowReactions -> reactions.showReactionDetails(messageId)
            MessageAction.ToggleReadReceipt -> menu.toggleReadReceipt(messageId)
            MessageAction.SwipeReply -> composer.replyTo(messageId)
            // U alba na fotku, na které stisk začal, ne na zprávu s popiskem, které patří blok.
            MessageMenuAction.Reply -> composer.replyTo(_state.value.messageMenu?.takeIf { it.messageId == messageId }?.replyTarget ?: messageId)
            is MessageAction.JumpTo -> jumpToMessage(action.messageId)
            is MessageAction.PressBotButton -> bots.pressBotButton(messageId, action.row, action.column)
            is MessageAction.Vote -> bots.vote(messageId, action.answerIndexes)
            MessageAction.ShowPollVoters -> _state.update { it.copy(pollVoters = messageId) }
            MessageAction.AddPollOption -> _state.update { it.copy(addingPollOption = messageId) }
            is MessageAction.MarkChecklistTask -> checklists.mark(messageId, action.taskId, action.done)
            MessageAction.AddChecklistTask -> checklists.openAddTask(messageId)
            MessageMenuAction.RetractVote -> {
                menu.closeMenus()
                bots.vote(messageId, emptyList())
            }
            MessageMenuAction.StopPoll -> _state.update { it.copy(messageMenu = null, pendingStopPoll = messageId) }
            MessageMenuAction.DeletePollOption -> bots.requestDeletePollOption(messageId)
            MessageMenuAction.EditChecklist -> {
                menu.closeMenus()
                checklists.openEditor(messageId)
            }
            is MessageAction.OpenLink -> links.openLink(action.url)
            is MessageAction.OpenInstantView -> instantViewRequests?.request(action.url) ?: links.openLink(action.url)
            is MessageAction.OpenMention -> links.openMentioned { chatRepository.chatByUsername(action.username) }
            is MessageAction.OpenUser -> links.openMentioned { chatRepository.privateChat(action.userId) }
            MessageAction.AddContact -> messageActions.addSharedContact(messageId)
            MessageAction.AcceptSuggestedBirthdate -> messageActions.acceptSuggestedBirthdate(messageId)
            MessageAction.AcceptSuggestedPhoto -> messageActions.acceptSuggestedPhoto(messageId)
            MessageAction.CallBack -> messageActions.callBack(messageId)
            MessageAction.StopLiveLocation -> attachments.stopLiveLocation(messageId)
            MessageAction.SetProximityAlert -> attachments.openProximityAlert(messageId)
            is MessageAction.OpenChat -> links.openMentioned { Result.success(action.chatId) }
            MessageAction.ShowGiveaway -> bots.showGiveaway(messageId)
            is MessageAction.SendBotCommand -> bots.sendBotCommand(action.command)
            is MessageAction.UseInlineBot -> suggestions.useInlineBot(action.username)
            is MessageAction.OpenSenderProfile -> links.openSenderProfile(action.userId)
            is MessageAction.OpenForwardOrigin -> links.openForwardOrigin(action.origin)
            MessageAction.OpenComments -> links.openComments(messageId)
            MessageAction.ReadFullArticle -> links.readFullArticle(messageId)
            is MessageAction.ToggleArticleAudio -> playback.toggleArticleAudio(messageId, action.index)
            is MessageAction.SeekArticleAudio ->
                voicePlayer.seek(VoiceKey(chatId, messageId, part = action.index), action.fraction)
            is MessageAction.OpenArticleDocument -> documents.openArticleDocument(messageId, action.index)
            MessageAction.OpenDocument -> documents.openDocument(messageId)
            MessageAction.CancelUpload -> messageActions.cancelUpload(messageId)
            MessageAction.ExplainPaidMedia -> _state.update { it.copy(notice = UiText(Res.string.paid_media_unsupported)) }
            MessageAction.ExplainLiveStory -> _state.update { it.copy(notice = UiText(Res.string.story_live_unsupported)) }
            is MessageAction.OpenStory -> links.openStory(StoryTarget(action.posterChatId, action.storyId))
            MessageAction.ToggleVoice -> playback.toggleVoice(messageId)
            MessageAction.ToggleVideoNoteSound -> playback.toggleVideoNoteSound(messageId)
            is MessageAction.PlayMediaAt -> playback.playMediaAt(messageId, action.seconds)
            MessageAction.VideoNoteSoundEnded -> _state.update {
                if (it.soundingVideoNote == messageId) it.copy(soundingVideoNote = null) else it
            }
            MessageAction.CycleVoiceSpeed -> voicePlayer.cycleSpeed()
            MessageAction.Transcribe -> playback.transcribe(messageId)
            is MessageAction.SeekVoice -> playback.seekVoice(messageId, action.fraction)
            // Výměnu přílohy dovoluje server zprávu po zprávě; ví to jen nabídka, která se právě zavírá.
            MessageMenuAction.Edit -> composer.startEdit(
                messageId,
                canEditMedia = _state.value.messageMenu?.takeIf { it.messageId == messageId }?.actions?.canEditMedia == true,
            )
            MessageMenuAction.Copy -> menu.copyMessage(messageId)
            is MessageAction.QuoteReply -> composer.quoteReply(messageId, action.text)
            is MessageAction.CopyText -> menu.copyText(action.text)
            MessageMenuAction.ToggleFavoriteSticker -> menu.toggleFavoriteSticker(messageId)
            MessageMenuAction.ToggleProfileAudio -> menu.toggleProfileAudio(messageId)
            MessageMenuAction.ToggleBookmark -> menu.toggleBookmark(messageId)
            MessageMenuAction.Forward -> messageActions.requestForward(messageId)
            MessageMenuAction.Remind -> messageActions.requestRemind(messageId)
            MessageMenuAction.Info -> menu.showMessageInfo(messageId)
            MessageMenuAction.Translate -> translation.showTranslation(messageId)
            MessageMenuAction.Summarize -> translation.showSummary(messageId)
            // Vysvětlení pouští obrazovka (sheet shrnutí); tady se jen zavře nabídka.
            MessageMenuAction.Explain -> _state.update { it.copy(messageMenu = null) }
            MessageMenuAction.ContextActions -> messageContext.openContextFromMenu(messageId)
            is MessageMenuAction.ContextItem -> messageContext.runContextItem(messageId, action)
            MessageMenuAction.SelectText -> menu.selectText(messageId)
            MessageMenuAction.Pin -> messageActions.requestPin(messageId)
            MessageMenuAction.Unpin -> messageActions.unpinFromMenu(messageId)
            MessageMenuAction.Delete -> messageActions.requestDelete(messageId)
            MessageMenuAction.Resend -> messageActions.resend(messageId)
            MessageMenuAction.Select -> selection.startSelection(messageId)
            MessageMenuAction.Report -> messageActions.reportMessage(messageId)
            MessageAction.ToggleSelection -> selection.toggleSelection(messageId)
            MessageMenuAction.ShareFile -> documents.shareDocument(messageId)
            MessageAction.OpenMedia, is MessageAction.OpenPollMedia, is MessageAction.OpenArticleMedia,
            is MessageAction.OpenStickerSet,
            is MessageAction.SearchHashtag,
            MessageMenuAction.SaveFileAs ->
                logger.w { "Akce $action patří obrazovce, do ViewModelu nemá chodit." }
        }
    }

    /** Obrazovka oznámení ukázala. */
    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }
}

/**
 * Spočítá, jak budou reakce vypadat po přepnutí. Reakce, která klesne na nulu, ze seznamu zmizí —
 * čip s nulou by vypadal jako chyba.
 */
internal fun List<MessageReaction>.toggled(emoji: String, add: Boolean): List<MessageReaction> {
    val existing = firstOrNull { it.emoji == emoji }

    return when {
        // Vlastní avatar u nově přidané reakce zatím neznáme — dorazí se serverovým updatem
        // o chvíli později. Optimisticky se ukáže jen počet, což je lepší než čekat na data.
        existing == null && add ->
            this + MessageReaction(emoji, count = 1, isChosen = true)

        existing == null -> this

        add -> map {
            if (it.emoji == emoji) it.copy(count = it.count + 1, isChosen = true) else it
        }

        else -> mapNotNull {
            when {
                it.emoji != emoji -> it
                it.count <= 1 -> null
                else -> it.copy(count = it.count - 1, isChosen = false)
            }
        }
    }
}

/** Doba, po kterou se drží klíč sdíleného prvku po přistání v bublině. */
internal const val FLIGHT_DURATION_MS = 450L

/**
 * Kolik stránek historie se kvůli skoku na nepřečtené dotáhne navíc.
 *
 * Při třiceti zprávách na stránku to pokryje sto padesát nepřečtených, což je víc, než kolik
 * jich v běžném chatu bývá. Nad tím se skok vzdá — čekat na deset dotazů při otevření chatu
 * je horší než doscrollovat ručně.
 */
private const val MAX_UNREAD_PAGES = 5

/**
 * Zpráv na stránku historie — i pro okno kolem zprávy ([ConversationViewModel.jumpToMessage])
 * a stránku k novějším. Stejná jako výchozí `limit` v `ConversationRepository.history`.
 */
private const val PAGE_SIZE = 30

/** Kolik nejnovějších zpráv se posuzuje kvůli podvodu (viz [ScamWarnings]). */
private const val SCAM_RECENT_MESSAGES = 20

/**
 * Kolik nejnovějších zpráv dostane k termínu posudek modelu (viz [MessageContexts]). Starší
 * termín skoro vždycky minul a nenabízí se tak jako tak.
 */
private const val CONTEXT_RECENT_MESSAGES = 20

/** Kolik stránek nejvýš dotáhne [ConversationViewModel.loadForSummary]. */
private const val MAX_SUMMARY_PAGES = 4

/** Jak dlouho zůstane zpráva po skoku zvýrazněná. */
private const val HIGHLIGHT_DURATION_MS = 2500L

/**
 * Strop výběru. `deleteMessages` i `forwardMessages` berou v TDLib nejvýš sto zpráv naráz, takže
 * vyšší výběr by skončil chybou až u akce — a projít víc než sto zpráv ťuknutím stejně nikdo nechce.
 */
internal const val MAX_SELECTED_MESSAGES = 100
