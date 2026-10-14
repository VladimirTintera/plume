package eu.tintera.plume.features.conversation

import kotlinx.coroutines.flow.filterNotNull
import eu.tintera.plume.core.speech.SpeechToText
import eu.tintera.plume.core.ai.OnDeviceAiStatus
import eu.tintera.plume.core.ai.ScamKind
import eu.tintera.plume.core.ai.OnDeviceAi
import kotlin.time.Instant
import eu.tintera.plume.core.calendar.CalendarEvent
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.core.calendar.CalendarEvents
import eu.tintera.plume.core.contacts.ContactSaver
import eu.tintera.plume.core.telegram.platformLanguageCode
import eu.tintera.plume.features.conversation.items.ContextVerb
import eu.tintera.plume.features.conversation.items.contextMenuItems
import eu.tintera.plume.core.currency.ExchangeRates
import eu.tintera.plume.core.currency.HomeRegion
import eu.tintera.plume.core.wifi.WifiJoin
import eu.tintera.plume.core.wifi.WifiJoiner
import eu.tintera.plume.resources.conversation_jump_date_failed
import eu.tintera.plume.resources.conversation_jump_date_empty
import eu.tintera.plume.resources.message_context_wifi_failed
import eu.tintera.plume.resources.message_context_wifi_settings
import eu.tintera.plume.core.contacts.NewContact
import eu.tintera.plume.resources.message_context_no_contacts
import eu.tintera.plume.core.location.mapsSearchUrl
import eu.tintera.plume.resources.message_context_no_calendar
import eu.tintera.plume.resources.message_context_flight_title
import eu.tintera.plume.resources.message_context_flight_arrival_title
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.TimeZone
import eu.tintera.plume.resources.translation_device_download_failed
import eu.tintera.plume.resources.translation_device_same
import eu.tintera.plume.resources.translation_device_unsupported
import eu.tintera.plume.resources.translation_device_unknown
import eu.tintera.plume.core.translation.deviceTranslationCode
import eu.tintera.plume.core.translation.OnDeviceTranslator
import eu.tintera.plume.resources.conversation_remind_failed
import eu.tintera.plume.resources.conversation_remind_set
import eu.tintera.plume.core.telegram.ForwardOptions
import eu.tintera.plume.core.bookmarks.MessageBookmarks
import eu.tintera.plume.core.snooze.ChatSnoozes
import eu.tintera.plume.core.ui.PhotoMarkup
import eu.tintera.plume.core.ui.PhotoMarkupRenderer
import eu.tintera.plume.resources.markup_failed
import eu.tintera.plume.core.telegram.QuickReaction
import eu.tintera.plume.resources.conversation_bookmark_removed
import eu.tintera.plume.resources.conversation_bookmark_added
import eu.tintera.plume.resources.summary_me
import eu.tintera.plume.resources.conversation_contact_send_failed
import eu.tintera.plume.resources.conversation_contact_no_phone
import eu.tintera.plume.resources.conversation_contact_load_failed
import eu.tintera.plume.core.telegram.PollDraftMedia
import eu.tintera.plume.core.telegram.ReplyQuote
import eu.tintera.plume.core.telegram.ComposedText
import eu.tintera.plume.core.telegram.composedFormatsOf
import eu.tintera.plume.core.telegram.within
import eu.tintera.plume.core.telegram.UnreadMarkKind
import eu.tintera.plume.resources.bot_location_unavailable
import kotlinx.coroutines.flow.flowOf
import eu.tintera.plume.resources.video_chat_join_elsewhere
import eu.tintera.plume.core.telegram.VideoChatInfo
import eu.tintera.plume.core.telegram.InviteLinkRepository
import eu.tintera.plume.core.url.UrlCleaner
import eu.tintera.plume.core.telegram.PendingJoinRequests
import eu.tintera.plume.core.telegram.ChatReplyMarkup
import eu.tintera.plume.core.telegram.ReplyKeyboardButtonKind
import eu.tintera.plume.core.telegram.UnsupportedKeyboardButton
import eu.tintera.plume.resources.poll_add_option_failed
import eu.tintera.plume.resources.bot_unsupported_managed_bot
import eu.tintera.plume.core.telegram.BotButtonKind
import eu.tintera.plume.core.telegram.SwitchInlineTarget
import eu.tintera.plume.core.telegram.inlineQueryText
import eu.tintera.plume.core.telegram.UnsupportedBotButton
import eu.tintera.plume.resources.bot_answer
import eu.tintera.plume.resources.bot_button_failed
import eu.tintera.plume.resources.poll_retract_failed
import eu.tintera.plume.resources.poll_stop_failed
import eu.tintera.plume.resources.poll_vote_failed
import eu.tintera.plume.resources.bot_text_copied
import eu.tintera.plume.resources.bot_login_failed
import eu.tintera.plume.core.telegram.LoginUrlPrompt
import eu.tintera.plume.resources.bot_unsupported_switch_inline
import eu.tintera.plume.resources.bot_unsupported_buy
import eu.tintera.plume.resources.bot_unsupported_game
import eu.tintera.plume.resources.bot_unsupported_password
import eu.tintera.plume.core.telegram.SenderInfo
import eu.tintera.plume.core.telegram.ForwardOrigin
import eu.tintera.plume.resources.invite_request_sent
import eu.tintera.plume.resources.conversation_link_failed
import eu.tintera.plume.resources.contact_link_expired
import eu.tintera.plume.resources.conversation_link_user_not_found
import eu.tintera.plume.core.telegram.InviteJoinResult
import eu.tintera.plume.core.telegram.InternalLink
import eu.tintera.plume.core.telegram.platformLanguageCode
import eu.tintera.plume.core.language.LanguageDetector
import eu.tintera.plume.core.telegram.MessageBody
import eu.tintera.plume.core.telegram.NameMention
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
import eu.tintera.plume.core.telegram.ContactLinkExpiredException
import eu.tintera.plume.core.telegram.LinkUserNotFoundException
import eu.tintera.plume.core.share.ShareTarget
import eu.tintera.plume.core.share.SharedContent
import eu.tintera.plume.core.share.ShareTargets
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.settings.ExpressionMode
import eu.tintera.plume.core.settings.GestureHint
import eu.tintera.plume.core.settings.RecordMode
import eu.tintera.plume.core.telegram.ChatInfo
import eu.tintera.plume.core.telegram.diceEmojiFor
import eu.tintera.plume.core.navigation.InstantViewRequests
import eu.tintera.plume.core.navigation.BotStartRequests
import eu.tintera.plume.resources.conversation_bot_start_failed
import eu.tintera.plume.core.telegram.MessageSchedule
import eu.tintera.plume.core.telegram.MessageEffect
import eu.tintera.plume.core.telegram.SendOptions
import eu.tintera.plume.core.telegram.SpeechTrial
import eu.tintera.plume.resources.conversation_scheduled_load_failed
import eu.tintera.plume.resources.conversation_schedule_failed
import eu.tintera.plume.resources.conversation_scheduled_notice
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.LinkPreview
import eu.tintera.plume.core.telegram.LinkPreviewOptions
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ConnectionRepository
import eu.tintera.plume.core.telegram.ConnectionState
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.ThreadConversationRepository
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.MessageActions
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.MessageReaction
import eu.tintera.plume.core.telegram.GifItem
import eu.tintera.plume.core.telegram.OutgoingAnimation
import eu.tintera.plume.core.telegram.Place
import eu.tintera.plume.resources.location_live_stop_failed
import eu.tintera.plume.resources.location_proximity_failed
import eu.tintera.plume.resources.location_live_unavailable
import eu.tintera.plume.core.telegram.LiveLocationRepository
import eu.tintera.plume.core.telegram.LiveFix
import eu.tintera.plume.core.telegram.PlaceRepository
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.AttachmentMode
import eu.tintera.plume.core.telegram.SelfDestructType
import eu.tintera.plume.core.telegram.SentMessageUpdate
import eu.tintera.plume.core.telegram.SponsoredMessage
import eu.tintera.plume.core.telegram.StickerItem
import eu.tintera.plume.core.telegram.StickerRepository
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.TextClipboard
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.core.ui.UrlOpener
import eu.tintera.plume.features.conversation.items.MessageAction
import eu.tintera.plume.features.conversation.items.MessageMenuAction
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_resend_failed
import eu.tintera.plume.resources.conversation_send_failed_unknown
import eu.tintera.plume.resources.article_load_failed
import eu.tintera.plume.resources.folder_invite_added
import eu.tintera.plume.resources.folder_invite_failed
import eu.tintera.plume.resources.folder_invite_joined
import eu.tintera.plume.resources.chat_bar_phone_shared
import eu.tintera.plume.resources.paid_media_unsupported
import eu.tintera.plume.resources.story_live_unsupported
import eu.tintera.plume.resources.story_unavailable
import eu.tintera.plume.resources.conversation_selection_limit
import eu.tintera.plume.resources.conversation_attachments_limit
import eu.tintera.plume.resources.conversation_content_not_allowed
import eu.tintera.plume.resources.conversation_gif_send_failed
import eu.tintera.plume.resources.conversation_location_send_failed
import eu.tintera.plume.resources.conversation_self_destruct_album
import eu.tintera.plume.resources.profile_action_failed
import eu.tintera.plume.resources.profile_mute_failed
import eu.tintera.plume.resources.secret_chat_close_failed
import eu.tintera.plume.resources.secret_chat_closed
import eu.tintera.plume.resources.conversation_attachments_send_failed
import eu.tintera.plume.resources.conversation_cancel_send_failed
import eu.tintera.plume.resources.conversation_delete_failed
import eu.tintera.plume.resources.conversation_edit_failed
import eu.tintera.plume.resources.conversation_favorite_added
import eu.tintera.plume.resources.conversation_favorite_failed
import eu.tintera.plume.resources.conversation_favorite_removed
import eu.tintera.plume.resources.conversation_history_failed
import eu.tintera.plume.resources.conversation_pin_failed
import eu.tintera.plume.resources.conversation_join_failed
import eu.tintera.plume.resources.conversation_mention_not_found
import eu.tintera.plume.resources.comments_unavailable
import eu.tintera.plume.resources.conversation_search_failed
import eu.tintera.plume.resources.conversation_message_deleted
import eu.tintera.plume.resources.conversation_quote_deleted
import eu.tintera.plume.resources.conversation_reaction_failed
import eu.tintera.plume.resources.conversation_repin_failed
import eu.tintera.plume.resources.conversation_send_failed
import eu.tintera.plume.resources.conversation_sticker_send_failed
import eu.tintera.plume.resources.contact_add_failed
import eu.tintera.plume.resources.call_unavailable
import eu.tintera.plume.resources.contact_remove_failed
import eu.tintera.plume.resources.conversation_unpin_failed
import eu.tintera.plume.resources.conversation_unread_mark_failed
import eu.tintera.plume.resources.share_failed_files
import eu.tintera.plume.resources.message_info_failed
import eu.tintera.plume.resources.message_info_link_copied
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import eu.tintera.plume.core.telegram.ChatBar
import eu.tintera.plume.core.telegram.SharePhoneBar
import eu.tintera.plume.features.chats.SpamCleanup
import eu.tintera.plume.features.chats.cleanUpSpam
import eu.tintera.plume.features.report.ReportFlow
import eu.tintera.plume.features.proxy.ProxyLinkFlow
import eu.tintera.plume.core.telegram.ProxyRepository
import eu.tintera.plume.core.telegram.UnsupportedProxyException
import eu.tintera.plume.resources.proxy_unsupported
import eu.tintera.plume.resources.chats_blocked
import eu.tintera.plume.resources.chats_unarchived
import eu.tintera.plume.resources.spam_bar_contact_added
import eu.tintera.plume.resources.spam_bar_failed
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.jetbrains.compose.resources.StringResource
import eu.tintera.plume.core.telegram.InlineBotInfo
import eu.tintera.plume.core.telegram.InlineQueryDraft
import eu.tintera.plume.core.telegram.InlineQueryState
import eu.tintera.plume.core.telegram.InlineResult
import eu.tintera.plume.core.telegram.InlineResultsButton
import eu.tintera.plume.core.location.GeoPoint
import kotlin.time.Duration.Companion.milliseconds
import eu.tintera.plume.core.telegram.inlineQueryOf
import eu.tintera.plume.resources.conversation_via_bot_draft_busy
import eu.tintera.plume.resources.translation_copied
import eu.tintera.plume.resources.server_summary_copied
import eu.tintera.plume.resources.translation_failed
import eu.tintera.plume.resources.server_summary_failed

/**
 * Stav a stránkování jedné konverzace.
 *
 * Zprávy se drží **seřazené od nejnovější po nejstarší**, protože stejně je vykresluje
 * `LazyColumn(reverseLayout = true)` — nejnovější dole, stránkování nahoru. Kdyby se držely
 * opačně, každé přidání staré stránky by znamenalo přepočet celého seznamu.
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

    private val _state = MutableStateFlow(ConversationState(threadRootId = threadRootId, forumTopicId = forumTopicId, savedTopicId = savedTopicId))
    val state: StateFlow<ConversationState> = _state.asStateFlow()

    /**
     * Zprávy tohohle chatu, u kterých se varuje před podvodem, podle id. Samostatný tok, ne
     * součást [ConversationState]: přibývá zřídka, nezávisle na zprávách, a čte se až v položce,
     * ne v `ConversationScreen` (ten je v dexu na hraně).
     */
    val scamVerdicts: StateFlow<Map<Long, ScamKind>> = (
        scamWarnings?.verdicts?.combine(settings.scamWarningsEnabled) { all, on ->
            if (on) all.filterKeys { it.first == chatId }.mapKeys { it.key.second } else emptyMap()
        } ?: flowOf(emptyMap())
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Posudky termínů modelem pro zprávy tohohle chatu podle (zpráva, otisk textu), nebo `null`,
     * když se akce u zpráv nenabízejí (vypnuté v nastavení, chat s chráněným obsahem — z něj
     * nesmí nic ven, ani do kalendáře). Samostatný tok ze stejného důvodu jako [scamVerdicts].
     */
    val contextVerdicts: StateFlow<Map<Pair<Long, Int>, EventVerdict>?> = combine(
        messageContexts?.verdicts ?: flowOf(emptyMap()),
        settings.messageContextEnabled,
        _state.map { it.info?.protectsContent == true }.distinctUntilChanged(),
    ) { all, on, protected ->
        if (!on || protected) {
            null
        } else {
            all.filterKeys { it.chatId == chatId }.mapKeys { (key, _) -> key.messageId to key.textHash }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _contextSheet = MutableStateFlow<ContextSheet?>(null)

    /** Měna uživatele podle regionu telefonu; částky v ní se v akcích u zprávy nepřevádějí. */
    val homeCurrency: String? = currency?.code

    /** Používá uživatel metrické jednotky? Převádějí se jen jednotky té druhé soustavy. */
    val metricUnits: Boolean = currency?.metric ?: true

    /** Otevřený sheet akcí u zprávy (`MessageContextSheet`); `null` = zavřený. */
    val contextSheet: StateFlow<ContextSheet?> = _contextSheet.asStateFlow()

    /**
     * Pruh nad konverzací (spam od neznámého, nasdílení čísla, žádost o vstup). Samostatný tok
     * ze stejného důvodu jako [connection]: mění se zřídka a nezávisle na zprávách.
     */
    val chatBar: StateFlow<ChatBar?> = chatRepository.chatBar(chatId)
        // Převedená základní skupina: TDLib u ní dál drží pruh „Pozvat členy", jenže zvát
        // a psát se dá jen do té nové. Pruh tam nic nenabízí.
        .combine(_state.map { it.info?.upgradedToChatId != null }.distinctUntilChanged()) { bar, upgraded ->
            bar.takeIf { !upgraded }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Videochat skupiny / živé vysílání kanálu pro pruh nad konverzací. Ve vlákně komentářů ne —
     * patří diskusní skupině, ne příspěvku.
     */
    val videoChat: StateFlow<VideoChatInfo?> =
        (if (threadRootId == null) chatRepository.videoChat(chatId) else flowOf(null))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Čekající žádosti o vstup pro pruh nad konverzací. TDLib je posílá jen správci, který je smí
     * vyřizovat (`Chat.pendingJoinRequests`), takže tu o právech rozhodovat netřeba. Ve vlákně
     * komentářů ne, ze stejného důvodu jako videochat.
     */
    val joinRequests: StateFlow<PendingJoinRequests?> =
        (if (threadRootId == null) inviteLinks.pendingJoinRequests(chatId) else flowOf(null))
            .map { requests -> requests?.takeIf { it.count > 0 } }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Ťuknutí na pruh videochatu: připojit se jde zatím jen jinde. */
    fun explainVideoChat() = _state.update { it.copy(notice = UiText(Res.string.video_chat_join_elsewhere)) }

    /** Průvodce nahlášením — z nabídky nad zprávou i z pruhu proti spamu. */
    val reportFlow = ReportFlow(viewModelScope, chatRepository::report)

    /** Proxy z odkazu `t.me/proxy` — přidá se až po potvrzení v dialogu. */
    val proxyLinkFlow = ProxyLinkFlow(viewModelScope, proxies)

    private val _chatGone = MutableStateFlow(false)

    /**
     * Chat se z pruhu proti spamu smazal — obrazovka konverzaci zavře. Stav, ne událost:
     * obrazovka si ho vyzvedne, i kdyby zrovna nebyla složená.
     */
    val chatGone: StateFlow<Boolean> = _chatGone.asStateFlow()

    private val _unarchived = MutableStateFlow(false)

    /**
     * Automaticky archivovaný chat se z pruhu vrátil mezi chaty. Obrazovka otevřená z archivu
     * se tím přestěhuje do hlavního seznamu — v archivu už chat není. Stav ze stejného důvodu
     * jako [chatGone].
     */
    val unarchived: StateFlow<Boolean> = _unarchived.asStateFlow()

    /** Vynucené odpovědi, na které se pole už přepnulo — jednou na zprávu. */
    private val forceReplied = mutableSetOf<Long>()

    private val logger = Logger.withTag("Conversation")

    /** Nahrávání hlasovky a kolečka — viz [ConversationRecorder]. */
    private val recorder = ConversationRecorder(
        chatId = chatId,
        state = _state,
        scope = viewModelScope,
        appScope = appScope,
        repository = repository,
        settings = settings,
        voicePlayer = voicePlayer,
        audioRecorder = audioRecorder,
        media = { failure, scheduled, send -> sendMedia(failure, scheduled, send) },
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
    private val playback = ConversationPlayback(
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
        logger = logger,
    )
    val voicePlayback: StateFlow<VoicePlayback?> get() = playback.voicePlayback
    val voiceSpeed: StateFlow<VoiceSpeed> get() = playback.voiceSpeed
    val nowPlaying: StateFlow<NowPlaying?> get() = playback.nowPlaying
    fun togglePlaying() = playback.togglePlaying()
    fun skipToNextTrack() = playback.skipToNextTrack()
    fun stopPlaying() = playback.stopPlaying()
    val isPremium: StateFlow<Boolean> get() = playback.isPremium
    val speechTrial: StateFlow<SpeechTrial?> get() = playback.speechTrial
    val transcribeContext: StateFlow<TranscribeContext> get() = playback.transcribeContext

    /** Soubory ve zprávách: stažení a otevření, sdílení, uložení — viz [ConversationDocuments]. */
    private val documents = ConversationDocuments(
        state = _state,
        scope = viewModelScope,
        fileRepository = fileRepository,
        fileOpener = fileOpener,
        sharer = sharer,
        closeMenus = ::closeMenus,
        logger = logger,
    )
    fun saveDocumentAs(messageId: Long, launcher: SaveAsLauncher) = documents.saveDocumentAs(messageId, launcher)

    /** Serializuje stránkování — bez toho by dva rychlé scrolly načetly tutéž stránku dvakrát. */
    private val loadMutex = Mutex()

    /** Rozlišuje jednotlivé lety textu do bubliny, když uživatel odešle dvě zprávy rychle po sobě. */
    private var flightCounter = 0L

    /**
     * Kdy naposledy odešlo „píšu". `null`, když protějšek o psaní neví (nic neodešlo, nebo se
     * mezitím zrušilo či odeslalo).
     */
    private var typingSentAt: TimeMark? = null

    /**
     * Poslední text z pole i s ručním formátováním, jak ho hlásí obrazovka. Kvůli ukládání
     * konceptu.
     */
    private var draft = ComposedText("")

    /**
     * Koncept z TDLib je vyřízený (vrácený do pole, nebo žádný není). Sdílený text se vkládá až
     * po něm: koncept jde jen do prázdného pole, takže sdílení před ním by ho z pole vytlačilo.
     */
    private val draftRestored = CompletableDeferred<Unit>()

    /** Koncept odložený po dobu úpravy zprávy; po ní se vrátí do pole i s formátováním. */
    private var draftBeforeEdit = ComposedText("")

    /**
     * Náhled odkazu konceptu, odložený s ním. Úprava má vlastní náhled a volbu (z upravované
     * zprávy), a kdyby je nepřepsala, odešla by upravená zpráva s volbou rozepsaného konceptu.
     */
    private var linkPreviewBeforeEdit = LinkPreviewStash()

    /** Odložené uložení konceptu; každá další úprava ho posune. */
    private var draftSave: Job? = null

    /** Odložené zjištění náhledu odkazu v rozepsaném textu; každý úhoz ho posune. */
    private var linkPreviewJob: Job? = null

    /**
     * Text, ke kterému se náhled naposledy zjišťoval. Nemusí být [draft]: pole vyplněné
     * sdílením nebo konceptem `onDraftChanged` nevolá, a výběr odkazu se ptá právě k tomuhle.
     */
    private var linkPreviewText = ""

    /** Běžící inline dotaz; další úhoz ho zruší a pustí nový (s pauzou). */
    private var inlineJob: Job? = null

    /** Běžící hledání členů ke zmínce; další úhoz ho zruší. */
    private var mentionJob: Job? = null

    /**
     * Zmínky jménem vložené z napovídání a čekající na odeslání (viz [NameMention]). Pole je holý
     * text, takže entitu s id uživatele nese tohle; s vyprázdněným polem nebo odesláním mizí.
     */
    private val nameMentions = mutableListOf<NameMention>()

    /** Poslední dotaz z pole — kvůli zopakování, až dorazí poloha. */
    private var inlineDraft: InlineQueryDraft? = null

    /** Poloha pro boty, kteří o ni stojí ([InlineBotInfo.needLocation]); zjišťuje ji obrazovka. */
    private var inlineLocation: GeoPoint? = null

    /**
     * Na polohu se u bota, který o ni stojí, už jednou počkalo (zjistila se, nebo ji uživatel
     * nedovolil či nepřišla do limitu). Do té doby se dotaz NEPOSÍLÁ: bez polohy by bot odpověděl
     * něčím jiným a výsledky by se po jejím příchodu vyměnily pod prstem.
     */
    private var inlineLocationResolved = false

    /**
     * Boti, kteří na inline dotaz odpověděli „neumím" — dál se jich s každým písmenem neptáme.
     * Jen po dobu konverzace: bot si inline režim může zapnout.
     */
    private val notInlineBots = mutableSetOf<Long>()

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
                                val event = findMessageContext(message.text, message.entities, sentAt, zone, homeCurrency, metricUnits).event
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

        // Automatické mazání se ukazuje v hlavičce; mění se i z jiného zařízení nebo od protějšku.
        viewModelScope.launch {
            repository.autoDeleteTime(chatId).collect { seconds -> _state.update { it.copy(autoDeleteTime = seconds) } }
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
                    replyFromViewer(request.messageId)
                }
            }
        }

        // Anketa se zakládá ve vlastním formuláři a rozepsanou odpověď si odnesla; formulář
        // ohlásí, že odešla (i naplánovaná), a odpověď zmizí jako po odeslání textu.
        viewModelScope.launch {
            replyRequests.sent.collect { sent ->
                if (sent?.chatId == chatId) {
                    replyRequests.consumeSent(chatId)
                    if (_state.value.replyConsumedBy(sent)) clearReply()
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
                    openSearch()
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
                    draft = ComposedText(it, info.draftFormats)
                    refreshLinkPreview(it)
                }
                info.draftReplyToMessageId?.let { restoreDraftReply(it, info.draftReplyQuote) }
                // Nalezená zpráva má přednost před nepřečtenými — kvůli ní chat otevřel.
                if (initialMessageId != null) {
                    releaseReads(jumped = false)
                    jumpToMessage(initialMessageId, fromSearch = true)
                } else {
                    releaseReads(jumped = loadUntilUnread())
                }
            }.onFailure { releaseReads(jumped = false) }
            draftRestored.complete(Unit)
        }

        viewModelScope.launch {
            // Inline dotaz z tlačítka bota v jiném chatu, pro který si uživatel vybral tenhle.
            // Až po konceptu: ten jde do prázdného pole a dotaz by jinak vytlačil.
            inlineQueryRequests.request.collect { request ->
                if (request?.chatId == chatId) {
                    draftRestored.await()
                    inlineQueryRequests.consume(chatId)
                    useInlineBot(request.bot, request.query)
                }
            }
        }

        viewModelScope.launch {
            // Sdílení ze systému do tohohle chatu. Odběr trvá po celou dobu konverzace: chat mohl
            // být otevřený pod výběrem a vybraný až potom.
            shareRequests.deliveries(chatId).collect { content ->
                draftRestored.await()
                receiveShared(content)
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
        if (forumTopicId == null && savedTopicId == null) viewModelScope.launch {
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
                    _state.update { it.copy(error = failure.reported(Res.string.conversation_unpin_failed)) }
                    refreshPinned()
                }
        }
    }

    /** „Vrátit" ze snackbaru. */
    fun undoUnpin(message: ChatMessage) {
        viewModelScope.launch {
            repository.pin(chatId, message.id).onFailure { failure ->
                _state.update { it.copy(error = failure.reported(Res.string.conversation_repin_failed)) }
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
                        it.copy(loading = false, error = failure.reported(Res.string.conversation_history_failed))
                    }
                }
        }
    }

    /** Běžící hledání v chatu (dotaz s pauzou, dotažení stránky); nový dotaz ho ruší. */
    private var searchJob: Job? = null

    /** Běžící hledání členů pro filtr podle odesílatele. */
    private var memberJob: Job? = null

    /** Lupa v hlavičce: hledání v chatu. */
    fun openSearch() {
        if (_state.value.search == null) _state.update { it.copy(search = InChatSearch()) }
    }

    fun closeSearch() {
        searchJob?.cancel()
        memberJob?.cancel()
        _state.update { it.copy(search = null, memberPicker = null) }
    }

    /**
     * Skok na datum jde jen v celém chatu: `getChatMessageByDate` zná celý chat, takže ve vlákně
     * komentářů nebo v tématu by trefil zprávu mimo něj.
     */
    val canJumpToDate: Boolean get() = threadRootId == null && forumTopicId == null && savedTopicId == null

    /**
     * Skok na první zprávu od začátku dne [epochSeconds]. Hledání se zavře — kalendář je jeho
     * součást, ale cíl je konverzace — a skočí se jako na citaci, s kotvou nahoře. Po výměně
     * okna stojí cíl s předělem dne zhruba v půlce obrazovky a nad ním poslední zpráva před
     * vybraným dnem (ověřeno na emulátoru) — je vidět, kde den začíná.
     */
    fun jumpToDate(epochSeconds: Int) {
        viewModelScope.launch {
            repository.firstMessageFrom(chatId, epochSeconds)
                .onSuccess { messageId ->
                    if (messageId == null) {
                        _state.update { it.copy(error = UiText(Res.string.conversation_jump_date_empty)) }
                    } else {
                        closeSearch()
                        jumpToMessage(messageId, anchor = ScrollAnchor.Top)
                    }
                }
                .onFailure { failure -> _state.update { it.copy(error = failure.userMessage(Res.string.conversation_jump_date_failed)) } }
        }
    }

    /**
     * Dotaz v hledání. Hledá se po krátké pauze v psaní a rovnou se skočí na nejnovější
     * výsledek — kdo hledá, chce vidět, kde to je, ne seznam.
     */
    fun onSearchQuery(text: String) {
        _state.update { state -> state.search?.let { state.copy(search = it.copy(query = text)) } ?: state }
        restartSearch(debounce = true)
    }

    /**
     * Hledání jen ve zprávách jednoho člena skupiny (`null` = filtr pryč). Hledá se hned, bez pauzy
     * — výběr je hotové rozhodnutí, ne psaní. S odesílatelem stačí prázdný dotaz: výsledkem jsou
     * všechny jeho zprávy.
     */
    fun setSearchSender(sender: SenderInfo?) {
        _state.update { state ->
            state.search?.let { state.copy(search = it.copy(sender = sender), memberPicker = null) } ?: state
        }
        restartSearch(debounce = false)
    }

    /** Spustí hledání pro současný text a odesílatele; rozběhnuté předchozí zruší. */
    private fun restartSearch(debounce: Boolean) {
        searchJob?.cancel()
        val search = _state.value.search ?: return
        if (!search.hasCriteria) {
            _state.update { state -> state.search?.let { state.copy(search = it.cleared()) } ?: state }
            return
        }
        val query = search.query.trim()
        val senderId = search.sender?.id
        searchJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            _state.update { state -> state.search?.let { state.copy(search = it.copy(loading = true)) } ?: state }
            repository.searchInChat(chatId, query, senderUserId = senderId)
                .onSuccess { page ->
                    _state.update { state ->
                        state.search?.let { state.copy(search = it.withFirstPage(query, page, senderId)) } ?: state
                    }
                    _state.value.search?.current?.let { jumpToMessage(it, fromSearch = true) }
                }
                .onFailure { failure ->
                    val message = failure.reported(Res.string.conversation_search_failed)
                    _state.update { state ->
                        state.copy(error = message, search = state.search?.copy(loading = false))
                    }
                }
        }
    }

    /** Výběr člena pro hledání podle odesílatele. */
    fun openMemberPicker() {
        _state.update { it.copy(memberPicker = MemberPicker()) }
        onMemberQuery("")
    }

    fun closeMemberPicker() {
        memberJob?.cancel()
        _state.update { it.copy(memberPicker = null) }
    }

    /** Hledání člena podle jména, s krátkou pauzou v psaní; prázdné = první členové skupiny. */
    fun onMemberQuery(text: String) {
        _state.update { state -> state.memberPicker?.let { state.copy(memberPicker = it.copy(query = text, loading = true)) } ?: state }
        memberJob?.cancel()
        memberJob = viewModelScope.launch {
            if (text.isNotEmpty()) delay(SEARCH_DEBOUNCE_MS)
            repository.searchMembers(chatId, text.trim())
                .onSuccess { members ->
                    _state.update { state ->
                        state.memberPicker?.let { state.copy(memberPicker = it.copy(members = members, loading = false)) } ?: state
                    }
                }
                .onFailure { failure ->
                    logger.w(failure) { "Členy chatu se nepodařilo načíst." }
                    _state.update { state ->
                        state.memberPicker?.let { state.copy(memberPicker = it.copy(members = emptyList(), loading = false)) } ?: state
                    }
                }
        }
    }

    /** Šipka nahoru: starší výsledek. Na konci načtených se dotáhne další stránka. */
    fun searchOlder() {
        val search = _state.value.search ?: return
        search.older()?.let { moved ->
            _state.update { it.copy(search = moved) }
            moved.current?.let { jumpToMessage(it, fromSearch = true) }
            return
        }
        val from = search.nextFromMessageId ?: return
        if (search.loading) return
        searchJob = viewModelScope.launch {
            _state.update { state -> state.search?.let { state.copy(search = it.copy(loading = true)) } ?: state }
            repository.searchInChat(chatId, search.resultsQuery, fromMessageId = from, senderUserId = search.resultsSenderId)
                .onSuccess { page ->
                    _state.update { state ->
                        val loaded = state.search?.withNextPage(search.resultsQuery, page, search.resultsSenderId)
                        state.copy(search = loaded?.older() ?: loaded)
                    }
                    _state.value.search?.current?.takeIf { it != search.current }?.let { jumpToMessage(it, fromSearch = true) }
                }
                .onFailure { failure ->
                    val message = failure.reported(Res.string.conversation_search_failed)
                    _state.update { state -> state.copy(error = message, search = state.search?.copy(loading = false)) }
                }
        }
    }

    /** Šipka dolů: novější výsledek. */
    fun searchNewer() {
        val moved = _state.value.search?.newer() ?: return
        _state.update { it.copy(search = moved) }
        moved.current?.let { jumpToMessage(it, fromSearch = true) }
    }

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
                            it.copy(loading = false, error = failure.reported(Res.string.conversation_history_failed))
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
                        it.copy(loading = false, error = failure.reported(Res.string.conversation_history_failed))
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
                    _state.update { it.copy(error = failure.reported(Res.string.conversation_unread_mark_failed)) }
                },
            )
        }
    }

    /** Podržení tlačítka „@" nebo „❤" → „Označit vše jako přečtené". */
    fun readAllMarks(kind: UnreadMarkKind) {
        viewModelScope.launch {
            repository.readAllMarks(chatId, kind).onFailure { failure ->
                _state.update { it.copy(error = failure.reported(Res.string.conversation_unread_mark_failed)) }
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
                    )
                }
                resolveMissingQuotes()
            }
        }

        viewModelScope.launch {
            repository.replyMarkup(chatId).collect { markup ->
                _state.update { it.copy(replyMarkup = markup) }
                if (markup is ChatReplyMarkup.ForceReply) applyForceReply(markup.messageId)
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
                    if (has) loadScheduled() else _state.update { it.copy(scheduledMessages = null) }
                }
            }
        }

        viewModelScope.launch {
            repository.presenceUpdates(chatId).collect { presence ->
                _state.update { it.copy(presence = presence) }
            }
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
                if (_state.value.editing?.id in removed) cancelEdit()
            }
        }
    }

    /** Poslední otevřená část výběru emoji/samolepek; řídí ikonu ve vstupním poli. */
    val expressionMode: StateFlow<ExpressionMode> = settings.expressionMode

    /**
     * Uloží, která část výběru byla otevřená. Zápis běží v `appScope`: přepnutí těsně před odchodem
     * z chatu se musí uložit, i když se `viewModelScope` mezitím zruší.
     */
    fun setExpressionMode(mode: ExpressionMode) {
        appScope.launch { settings.setExpressionMode(mode) }
    }

    /**
     * Odešle samolepku z výběru.
     *
     * Bez letu ze vstupního pole — samolepka z pole nevyletěla, přišla ze sheetu, který se právě
     * zavírá. Rozepsaný text zůstává: samolepka ho nenahrazuje, jen se odeslala mezi tím.
     * Citace se spotřebuje stejně jako u textu.
     */
    fun sendSticker(sticker: StickerItem, options: SendOptions = SendOptions()) {
        // Výběr samolepek se bez práva neotevře, ale sada jde otevřít i ťuknutím na samolepku
        // v historii — a z ní se posílá taky. Hláška je lepší než chyba od serveru.
        if (!_state.value.content.stickers) {
            _state.update { it.copy(notice = UiText(Res.string.conversation_content_not_allowed)) }
            return
        }
        sendMedia(Res.string.conversation_sticker_send_failed, scheduled = options.schedule != null) { replyTo, quote ->
            repository.sendSticker(chatId, sticker, replyTo, quote, options)
        }
    }

    /** Odešle GIF z výběru. Stejné právo a stejné chování jako [sendSticker]. */
    fun sendGif(gif: GifItem, options: SendOptions = SendOptions()) {
        if (!_state.value.content.stickers) {
            _state.update { it.copy(notice = UiText(Res.string.conversation_content_not_allowed)) }
            return
        }
        sendMedia(Res.string.conversation_gif_send_failed, scheduled = options.schedule != null) { replyTo, quote ->
            repository.sendGif(chatId, gif, replyTo, quote, options)
        }
    }

    /**
     * Odešle polohu (sponka → Poloha): vlastní polohu, nebo místo z hledání. Hned, bez čekání nad
     * polem, jako samolepku — rozepsaný text zůstává a citace se spotřebuje. Poloha je v TDLib
     * „základní zpráva" (`canSendBasicMessages`), takže platí totéž právo jako pro psaní.
     */
    fun sendLocation(place: Place) {
        if (!_state.value.canWrite || _state.value.editing != null) return
        sendMedia(Res.string.conversation_location_send_failed) { replyTo, quote ->
            repository.sendLocation(chatId, place, replyTo, quote)
        }
    }

    /**
     * Začne sdílet živou polohu na [livePeriod] sekund (nebo „dokud nevypnu"), z polohy [point],
     * kterou už zjistila obrazovka. Další polohy posílá platforma ([LiveLocationRepository]
     * zprávu uvidí v seznamu aktivních a služba se spustí). `null` = poloha nedovolená nebo
     * nezjištěná — musí to být vidět, jinak by volba vypadala, že nic neudělala.
     */
    fun shareLiveLocation(point: GeoPoint?, livePeriod: Int) {
        if (!_state.value.canWrite || _state.value.editing != null) return
        if (point == null) {
            _state.update { it.copy(notice = UiText(Res.string.location_live_unavailable)) }
            return
        }
        sendMedia(Res.string.conversation_location_send_failed) { replyTo, quote ->
            repository.sendLiveLocation(chatId, LiveFix(point.latitude, point.longitude), livePeriod, replyTo, quote)
        }
    }

    /** „Zastavit sdílení" pod vlastní živou polohou. Potvrzení přijde úpravou zprávy. */
    private fun stopLiveLocation(messageId: Long) {
        viewModelScope.launch {
            liveLocations.stop(chatId, messageId).onFailure { failure ->
                _state.update { it.copy(error = failure.reported(Res.string.location_live_stop_failed)) }
            }
        }
    }

    /** „Upozornit na blízkost" pod vlastní živou polohou: dialog se vzdálenostmi. */
    private fun openProximityAlert(messageId: Long) {
        // Z docku jde i u zprávy, která v načtené historii není.
        val message = _state.value.messages.firstOrNull { it.id == messageId }
            ?: _state.value.liveLocations.firstOrNull { it.id == messageId }
        val radius = (message?.media as? MessageMedia.Location)?.proximityAlertRadius ?: return
        _state.update { it.copy(proximityAlert = ProximityAlertPrompt(messageId, radius)) }
    }

    fun dismissProximityAlert() {
        _state.update { it.copy(proximityAlert = null) }
    }

    /**
     * Nastaví upozornění na blízkost ([radiusMeters] 0 ho vypne). Výsledek přijde úpravou zprávy
     * jako každý posun polohy, takže se tady nic lokálně nepřepisuje.
     */
    fun setProximityAlert(radiusMeters: Int) {
        val prompt = _state.value.proximityAlert ?: return
        _state.update { it.copy(proximityAlert = null) }
        viewModelScope.launch {
            liveLocations.setProximityAlert(chatId, prompt.messageId, radiusMeters).onFailure { failure ->
                _state.update { it.copy(error = failure.reported(Res.string.location_proximity_failed)) }
            }
        }
    }

    /**
     * Sponka → Kontakt: sheet se seznamem kontaktů. Kontakty se čtou při otevření — sheet se
     * otevírá jednou za čas a seznam by jinak ležel v paměti každé konverzace.
     */
    fun openContactPicker() {
        _state.update { it.copy(contactPicker = ContactPicker(loading = true)) }
        viewModelScope.launch {
            // Vlastní účet: Uložené zprávy jsou chat s ním, id chatu je id uživatele. Kdo nemá
            // číslo vidět (nikdy by nemělo nastat), řádek nedostane.
            val me = chatRepository.savedMessagesChatId.value
                ?.let { repository.contactCard(it).getOrNull() }
                ?.takeIf { it.phone.isNotBlank() }
            chatRepository.contacts()
                .onSuccess { contacts ->
                    _state.update { state ->
                        state.copy(contactPicker = state.contactPicker?.copy(loading = false, contacts = contacts, me = me))
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(contactPicker = null, error = failure.reported(Res.string.conversation_contact_load_failed))
                    }
                }
        }
    }

    fun searchContacts(query: String) {
        _state.update { state -> state.copy(contactPicker = state.contactPicker?.copy(query = query)) }
    }

    fun dismissContactPicker() {
        _state.update { it.copy(contactPicker = null) }
    }

    /**
     * Vybraný kontakt se neposílá hned: dialog ukáže jméno a ČÍSLO, které odejde. Posílá se cizí
     * telefonní číslo a uživatel má vidět přesně co. Kontakt se skrytým číslem poslat nejde.
     */
    fun pickContact(userId: Long) {
        _state.update { it.copy(contactPicker = null) }
        viewModelScope.launch {
            repository.contactCard(userId)
                .onSuccess { card ->
                    _state.update {
                        if (card.phone.isBlank()) {
                            it.copy(notice = UiText(Res.string.conversation_contact_no_phone))
                        } else {
                            it.copy(pendingContact = card)
                        }
                    }
                }
                .onFailure { failure ->
                    _state.update { it.copy(error = failure.reported(Res.string.conversation_contact_send_failed)) }
                }
        }
    }

    fun cancelContact() {
        _state.update { it.copy(pendingContact = null) }
    }

    /** Kontakt odchází hned, jako poloha: citaci spotřebuje, rozepsaný text nechá. */
    fun confirmContact() {
        val userId = _state.value.pendingContact?.userId ?: return
        _state.update { it.copy(pendingContact = null) }
        if (!_state.value.canWrite || _state.value.editing != null) return
        sendMedia(Res.string.conversation_contact_send_failed) { replyTo, quote ->
            repository.sendContact(chatId, userId, replyTo, quote)
        }
    }

    /**
     * Odešle GIF vložený z klávesnice — hned, bez čekání nad polem, jako samolepku. Rozepsaný text
     * zůstává a citace se spotřebuje. GIFy vede TDLib pod týmž právem jako samolepky
     * (`canSendOtherMessages`); klávesnici se bez něj GIF nenabízí, ale pojistka patří i sem.
     */
    fun sendAnimation(animation: OutgoingAnimation) {
        if (!_state.value.content.stickers || _state.value.editing != null) {
            _state.update { it.copy(notice = UiText(Res.string.conversation_content_not_allowed)) }
            return
        }
        sendMedia(Res.string.conversation_gif_send_failed) { replyTo, quote ->
            repository.sendAnimation(chatId, animation, replyTo, quote)
        }
    }

    /**
     * Přílohy z výběru se **neposílají hned**, ale čekají nad vstupním polem: uživatel k nim napíše
     * popisek, některou odebere nebo přidá další, a teprve odeslání je pošle (jednu jako zprávu,
     * víc jako album). Pravidla skládání viz [stageAttachments].
     */
    fun addAttachments(picked: List<OutgoingAttachment>) {
        // Výběr fotek vydá fotky i videa najednou; co chat nepřijme, se sem nedostane.
        val allowed = allowedByContent(picked, _state.value.content)
        val forbidden = allowed.size < picked.size
        val staged = stageAttachments(_state.value.attachments, allowed)
        // Z jedné mizející fotky se přidáním další stává album, a v něm mizení není dovolené.
        val dropped = dropForbiddenSelfDestruct(
            staged.attachments,
            allowed = _state.value.canSelfDestruct(staged.attachments.size),
        )
        _state.update {
            it.copy(
                attachments = dropped ?: staged.attachments,
                notice = when {
                    forbidden -> UiText(Res.string.conversation_content_not_allowed)
                    staged.overflow -> UiText(Res.string.conversation_attachments_limit, MAX_ATTACHMENTS)
                    dropped != null -> UiText(Res.string.conversation_self_destruct_album)
                    else -> it.notice
                },
            )
        }
    }

    /**
     * Obsah sdílený ze systému: soubory k připraveným přílohám nad polem, text do pole.
     *
     * Nic se neposílá — uživatel má vidět, co odejde, a k souborům napsat popisek. Rozepsaný
     * koncept se **doplní, nepřepíše**: kdo sdílí odkaz do chatu, kde má něco rozepsaného,
     * nechce o to přijít.
     */
    private fun receiveShared(content: SharedContent) {
        shareRequests.consume(content.id)
        content.attachments?.takeIf { it.isNotEmpty() }?.let(::addAttachments)
        content.text?.let { shared ->
            val text = if (draft.text.isBlank()) shared else draft.text.trimEnd() + "\n" + shared
            // Formátování konceptu platí dál — sdílený text se připojuje až za něj.
            val formats = if (draft.text.isBlank()) emptyList() else draft.formats.within(text.length)
            _state.update { it.copy(inputFill = InputFill(text, onlyIfEmpty = false, formats = formats)) }
            /*
             * Pole se vyplní bez `onDraftChanged`, takže se text zapisuje sám: jinak by ho
             * ViewModel neznal a první uložení konceptu (třeba po změně volby náhledu) by
             * zapsalo prázdný koncept — sdílený text by po odchodu z chatu zmizel.
             */
            draft = ComposedText(text, formats)
            scheduleDraftSave()
            // Sdílený odkaz je přitom typický případ náhledu.
            refreshLinkPreview(text)
        }
        if (content.failed > 0) {
            _state.update { it.copy(notice = UiText(Res.string.share_failed_files)) }
        }
    }

    fun removeAttachment(index: Int) {
        _state.update { state ->
            state.copy(attachments = state.attachments.filterIndexed { i, _ -> i != index })
        }
    }

    /**
     * Odebere přílohy podle CESTY — to je jediné, co o nich ví vestavěná galerie: odznačenou
     * fotku hlásí jako soubor, ne jako pořadí nad polem (viz `EmbeddedMediaPicker`). Upravená
     * fotka má cestu novou, galerie ale zná tu původní ([galleryPath]).
     */
    fun removeAttachments(paths: List<String>) {
        if (paths.isEmpty()) return
        val removed = paths.toSet()
        _state.update { state ->
            state.copy(attachments = state.attachments.filterNot { state.galleryPath(it.path) in removed })
        }
    }

    /** Nabízí se úprava fotky? */
    val canMarkupPhotos: Boolean get() = photoMarkup != null

    /**
     * Uloží úpravu fotky nad polem: tahy, otočení a ořez se vykreslí do NOVÉ kopie originálu
     * a příloha dostane její cestu i rozměry. Prázdná úprava vrátí originál. Fotka se hledá
     * podle cesty, ne pořadí — mezitím mohla ubýt jiná příloha.
     */
    fun applyPhotoMarkup(path: String, markup: PhotoMarkup) {
        val renderer = photoMarkup ?: return
        val state = _state.value
        val photo = state.attachments.firstOrNull { it.path == path } as? OutgoingAttachment.Photo ?: return
        val edit = state.photoEdits[path]
        if (markup == edit.markupOrEmpty) return
        if (markup.isEmpty) {
            replacePhoto(path, revertedPhoto(photo, edit))
            return
        }
        _state.update { it.copy(markupBusy = true) }
        viewModelScope.launch {
            runCatching { renderer.renderEdit(photo, edit, markup) }
                .onSuccess { edited -> replacePhoto(path, edited) }
                .onFailure { failure ->
                    logger.w(failure) { "Úpravu fotky $path se nepodařilo vykreslit." }
                    _state.update { it.copy(notice = UiText(Res.string.markup_failed)) }
                }
            _state.update { it.copy(markupBusy = false) }
        }
    }

    private fun replacePhoto(path: String, edited: EditedPhoto) {
        _state.update { state ->
            state.copy(
                attachments = state.attachments.withEditedPhoto(path, edited),
                photoEdits = state.photoEdits.withEditedPhoto(path, edited),
            )
        }
    }

    fun setAttachmentMode(index: Int, mode: AttachmentMode) {
        val selfDestruct = mode is AttachmentMode.ViewOnce || mode is AttachmentMode.Timer
        // Volba se nenabízí tam, kde neprojde; kdyby sem přesto došla, zpráva by skončila chybou.
        if (selfDestruct && !_state.value.canSelfDestruct()) return
        _state.update { state ->
            val updated = state.attachments.mapIndexed { i, attachment ->
                if (i == index) {
                    when (attachment) {
                        is OutgoingAttachment.Photo -> attachment.copy(mode = mode)
                        is OutgoingAttachment.Video -> attachment.copy(mode = mode)
                        is OutgoingAttachment.Document -> attachment
                    }
                } else {
                    attachment
                }
            }
            state.copy(attachments = updated)
        }
    }

    fun toggleAttachmentSpoiler(index: Int) {
        val current = _state.value.attachments.getOrNull(index) ?: return
        val newMode = if (current.mode is AttachmentMode.Spoiler) AttachmentMode.Standard else AttachmentMode.Spoiler
        setAttachmentMode(index, newMode)
    }

    fun setAttachmentSelfDestruct(index: Int, type: SelfDestructType?) {
        val newMode = when (type) {
            is SelfDestructType.Immediately -> AttachmentMode.ViewOnce
            is SelfDestructType.Timer -> AttachmentMode.Timer(type.seconds)
            null -> AttachmentMode.Standard
        }
        setAttachmentMode(index, newMode)
    }

    /**
     * Odešle připravené přílohy s popiskem.
     *
     * Jako odeslání textu: koncept se zahodí, citace se spotřebuje, „píšu" se zapomene. Na rozdíl od
     * textové zprávy TDLib u příloh koncept sám nesmaže, takže se maže výslovně. Bez letu ze vstupního
     * pole — přílohy z pole nevyletěly. Když odeslání selže, přílohy se vrátí nad pole, ať se nemusí
     * vybírat znovu.
     */
    /** Otevře seznam naplánovaných zpráv. */
    fun openScheduled() {
        _state.update { it.copy(scheduledMessages = it.scheduledMessages ?: emptyList()) }
        loadScheduled()
    }

    fun closeScheduled() {
        _state.update { it.copy(scheduledMessages = null) }
    }

    private fun loadScheduled() {
        viewModelScope.launch {
            repository.scheduledMessages(chatId)
                .onSuccess { list ->
                    // Zavřený mezitím zůstane zavřený; prázdný se zavře sám.
                    _state.update { state ->
                        if (state.scheduledMessages == null) state
                        else state.copy(scheduledMessages = list.takeIf { it.isNotEmpty() })
                    }
                }
                .onFailure { failure ->
                    _state.update { it.copy(error = failure.reported(Res.string.conversation_scheduled_load_failed)) }
                }
        }
    }

    /**
     * Naplánovanou zprávu odešle hned ([schedule] `null`), nebo přeplánuje. Seznam se pak čte
     * znovu — co se stalo, ví server; odeslaná zpráva dorazí do konverzace jako každá nová.
     */
    fun reschedule(messageId: Long, schedule: MessageSchedule?) {
        viewModelScope.launch {
            repository.reschedule(chatId, messageId, schedule)
                .onSuccess { loadScheduled() }
                .onFailure { failure ->
                    _state.update { it.copy(error = failure.reported(Res.string.conversation_schedule_failed)) }
                }
        }
    }

    fun deleteScheduled(messageId: Long) {
        viewModelScope.launch {
            repository.deleteMessages(chatId, listOf(messageId), revoke = true)
                .onSuccess { loadScheduled() }
                .onFailure { failure ->
                    _state.update { it.copy(error = failure.reported(Res.string.conversation_delete_failed)) }
                }
        }
    }

    private fun sendAttachments(caption: ComposedText, attachments: List<OutgoingAttachment>, options: SendOptions) {
        typingSentAt = null
        draftSave?.cancel()
        draftSave = null
        draft = ComposedText("")
        resetLinkPreview()

        val replyTo = _state.value.replyingTo?.id
        val quote = _state.value.replyQuote
        val mentions = takeNameMentions()
        clearReply()
        _state.update { it.copy(attachments = emptyList()) }

        viewModelScope.launch {
            repository.sendAttachments(chatId, attachments, caption.text, replyTo, quote, options, mentions, caption.formats)
                .onSuccess { pending ->
                    shareTargets.reportUsed(chatId)
                    if (options.schedule != null) {
                        _state.update { it.copy(notice = UiText(Res.string.conversation_scheduled_notice)) }
                        repository.saveDraft(chatId, "", null)
                        return@onSuccess
                    }
                    _state.update { state ->
                        // Potvrzení mohlo dorazit dřív než výsledek — viz `send`.
                        val fresh = pending.filterNot { it.id in state.itemKeys.values }
                        state.copy(messages = merge(state.messages, fresh))
                    }
                    repository.saveDraft(chatId, "", null)
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(
                            error = failure.reported(Res.string.conversation_attachments_send_failed),
                            attachments = it.attachments.ifEmpty { attachments },
                        )
                    }
                }
        }
    }

    /**
     * Text chyby do stavu obrazovky. Zaloguje surovou příčinu (a tím ji pošle i do kontextu
     * hlášení pádů) a vrátí větu pro uživatele.
     *
     * Hláška od TDLib se na obrazovku nedostane — je to anglická konstanta jako
     * `MESSAGE_EDIT_TIME_EXPIRED`, viz [userMessage]. `w` je tu správná závažnost: server řekl
     * ne, což je očekávané selhání, ne chyba, kolem které by se měl v konzoli zakládat problém.
     */
    private fun Throwable.reported(message: StringResource): UiText {
        logger.w(this) { message.key }
        return userMessage(message)
    }

    /**
     * Přidat se ke skupině nebo kanálu z globálního hledání. Po úspěchu se místo tlačítka
     * vrátí vstupní pole (v kanálu ho server stejně nepustí, ale to platí pro každý kanál).
     */
    fun join() {
        if (_state.value.joining) return
        _state.update { it.copy(joining = true) }
        viewModelScope.launch {
            repository.joinChat(chatId)
                .onSuccess { _state.update { it.copy(info = it.info?.copy(canJoin = false)) } }
                .onFailure { failure ->
                    val message = failure.reported(Res.string.conversation_join_failed)
                    _state.update { it.copy(error = message) }
                }
            _state.update { it.copy(joining = false) }
        }
    }

    /** Hláška z platformy (výběr souboru selhal) do snackbaru. */
    fun reportError(message: UiText) {
        _state.update { it.copy(error = message) }
    }

    private fun sendMedia(
        failureMessage: StringResource,
        scheduled: Boolean = false,
        send: suspend (replyTo: Long?, quote: ReplyQuote?) -> Result<ChatMessage>,
    ) {
        val replyTo = _state.value.replyingTo?.id
        val quote = _state.value.replyQuote
        clearReply()

        viewModelScope.launch {
            send(replyTo, quote)
                .onSuccess { pending ->
                    shareTargets.reportUsed(chatId)
                    // Naplánovaná do konverzace nepatří, jen se ohlásí — jako naplánovaný text.
                    if (scheduled) {
                        _state.update { it.copy(notice = UiText(Res.string.conversation_scheduled_notice)) }
                        return@onSuccess
                    }
                    // Potvrzení mohlo dorazit dřív než výsledek; `withMessage` to ošetří.
                    _state.update { it.withMessage(pending) }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(error = failure.reported(if (scheduled) Res.string.conversation_schedule_failed else failureMessage))
                    }
                }
        }
    }

    private val _effectChoices = MutableStateFlow<List<MessageEffect>?>(null)

    /**
     * Efekty nabídnuté ve výběru „Odeslat s efektem"; `null`, dokud se nenačtou. Čtou se až při
     * otevření výběru ([loadEffects]) — každý efekt je dotaz a většina odeslání efekt nechce.
     */
    val effectChoices: StateFlow<List<MessageEffect>?> = _effectChoices.asStateFlow()

    fun loadEffects() {
        if (_effectChoices.value != null) return
        viewModelScope.launch {
            _effectChoices.value = repository.sendableEffects(chatRepository.availableEffectIds.value)
        }
    }

    /**
     * Odešle obsah pole. [options] přichází z nabídky nad odesílacím tlačítkem (bez zvuku,
     * naplánovat, s efektem); obyčejné ťuknutí posílá výchozí.
     */
    fun send(text: ComposedText, options: SendOptions = SendOptions()) {
        val sanitized = if (settings.stripTrackingParameters.value) UrlCleaner.cleanComposedText(text) else text
        val composed = sanitized.trimmed()
        val trimmed = composed.text

        // S přílohami je text popisek a smí být prázdný. Úprava zprávy přílohy nemá.
        val attachments = _state.value.attachments
        if (attachments.isNotEmpty() && _state.value.editing == null) {
            // Dokud se úprava fotky vykresluje, příloha má ještě cestu k NEUPRAVENÉ fotce —
            // odeslala by se i s tím, co měl uživatel začerněné. Tlačítko je mezitím vypnuté;
            // tohle je pojistka pro Enter z klávesnice.
            if (_state.value.markupBusy) return
            readAfterOwnMessage()
            sendAttachments(composed, attachments, options)
            return
        }

        // Úprava se řeší dřív než prázdné pole: prázdný POPISEK je platná úprava (odebere ho),
        // takže o tom rozhoduje `sendEdit` podle toho, co se vlastně upravuje.
        _state.value.editing?.let { target ->
            sendEdit(target, composed)
            return
        }

        if (trimmed.isEmpty()) return
        readAfterOwnMessage()

        // Odeslaná zpráva psaní ukončí sama; další úhoz má „píšu" ohlásit hned, ne až za pět vteřin.
        typingSentAt = null

        // Koncept smaže TDLib sám (`clearDraft` při odeslání). Čekající uložení by ho vrátilo zpátky
        // se starým textem, takže se musí zahodit; obrazovka pole vyprázdní bez `onDraftChanged`.
        draftSave?.cancel()
        draftSave = null
        draft = ComposedText("")

        // Samotné 🎲 (🎯, 🏀, ⚽, 🎳, 🎰 — seznam posílá server) odejde jako hod, jako
        // u oficiálních klientů. Hod je v právech „ostatní zprávy", tedy totéž co samolepka;
        // kde se smí jen text, odejde emoji jako text. Neletí z pole — jako samolepka.
        val dice = diceEmojiFor(trimmed, chatRepository.diceEmojis.value).takeIf { options == SendOptions() }
        if (dice != null && _state.value.content.stickers) {
            resetLinkPreview()
            sendMedia(Res.string.conversation_send_failed) { replyTo, quote ->
                repository.sendDice(chatId, dice, replyTo, quote)
            }
            return
        }

        val linkPreview = _state.value.linkPreviewOptions.forSending(shown = _state.value.linkPreview)
        resetLinkPreview()

        val replyTo = _state.value.replyingTo?.id
        val quote = _state.value.replyQuote
        // Rozepsaná odpověď se zruší hned, ne až po potvrzení — jinak by při pomalé síti šlo
        // odeslat druhou zprávu se stejnou citací.
        clearReply()
        val mentions = takeNameMentions()

        // Naplánovaná zpráva do konverzace nepatří: neletí, nepřidává se, jen se ohlásí.
        if (options.schedule != null) {
            viewModelScope.launch {
                repository.send(chatId, trimmed, replyTo, linkPreview, quote, options, mentions, composed.formats)
                    .onSuccess { _state.update { it.copy(notice = UiText(Res.string.conversation_scheduled_notice)) } }
                    .onFailure { failure ->
                        _state.update { it.copy(error = failure.reported(Res.string.conversation_schedule_failed)) }
                    }
            }
            return
        }

        // Text „vyletí" ze vstupního pole do bubliny. Musí se zapamatovat hned při odeslání,
        // protože pole se vyprázdní dřív, než dorazí zpráva — a bez toho by nebylo co animovat.
        val flight = MessageFlight(id = ++flightCounter, text = trimmed, itemKey = null)
        _state.update { it.copy(flight = flight) }

        viewModelScope.launch {
            repository.send(chatId, trimmed, replyTo, linkPreview, quote, options, mentions, composed.formats)
                // TDLib vrací rozepsanou zprávu okamžitě, takže bublina naskočí bez čekání
                // na server. Skutečné id dorazí potvrzením a zprávu v seznamu nahradí.
                .onSuccess { pending ->
                    shareTargets.reportUsed(chatId)
                    // Na vynucenou odpověď se odpovědělo; TDLib ji po klientovi chce odebrat.
                    val force = _state.value.replyMarkup as? ChatReplyMarkup.ForceReply
                    if (force != null && force.messageId == replyTo) {
                        repository.dismissReplyMarkup(chatId, force.messageId)
                    }
                    _state.update {
                        // Mezitím ji už mohlo dorazit i potvrzení — pak se dočasná verze do
                        // seznamu vracet nesmí (`withMessage`).
                        it.withMessage(pending).copy(
                            // Většinou už let navázal `newMessages`; tohle je záloha pro opačné
                            // pořadí. Cizí let (další rychle odeslaná zpráva) se nechává být.
                            flight = it.flight?.let { f ->
                                if (f.id == flight.id && f.itemKey == null) f.copy(itemKey = pending.id) else f
                            },
                        )
                    }

                    // Let se uklidí až po doběhnutí animace. Kdyby se zrušil dřív, zmizel by
                    // klíč sdíleného prvku uprostřed přechodu a text by skokem zmizel.
                    delay(FLIGHT_DURATION_MS)
                    _state.update { state ->
                        state.copy(flight = state.flight?.takeIf { it.id != flight.id })
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(
                            error = failure.reported(Res.string.conversation_send_failed),
                            flight = it.flight?.takeIf { f -> f.id != flight.id },
                        )
                    }
                }
        }
    }

    /**
     * Obrazovka hlásí změnu rozepsaného textu; podle ní se protějšku ohlašuje „píše…".
     *
     * Server činnost zhasne sám asi po šesti vteřinách, takže se při psaní **opakuje** — ale
     * nejvýš jednou za [TYPING_REPEAT], ne na každý úhoz. Vymazání celého textu psaní výslovně
     * ukončí: kdo si to rozmyslel, nemá protějšku ještě pět vteřin „psát".
     */
    fun onDraftChanged(text: ComposedText) {
        // Při úpravě v poli není koncept, ale text staré zprávy. Uložit ho jako koncept by přepsalo
        // ten odložený, a ohlašovat „píše…" kvůli opravě překlepu by protějšek mátlo.
        _state.value.editing?.let { editing ->
            // Náhled odkazu ale patří i k úpravě textu: upravená zpráva odejde s ním.
            if (editing.composerEdit() == ComposerEdit.Text) refreshLinkPreview(text.text)
            return
        }
        // Psaní zavře seznam příkazů otevřený tlačítkem; napovídání „/…" se řídí textem samo.
        if (_state.value.commandsOpen) _state.update { it.copy(commandsOpen = false) }

        val textChanged = text.text != draft.text
        draft = text
        // Vyprázdněné pole = zpráva, ke které zmínky patřily, je pryč.
        if (text.text.isBlank()) nameMentions.clear()
        scheduleDraftSave()
        // Změna jen formátování („Tučné" nad výběrem) není psaní: náhled ani „píše…" se nemění.
        if (!textChanged) return
        refreshLinkPreview(text.text)

        if (text.text.isBlank()) {
            if (typingSentAt == null) return
            typingSentAt = null
            reportTyping(false)
            return
        }

        val last = typingSentAt
        if (last != null && last.elapsedNow() < TYPING_REPEAT) return
        typingSentAt = TimeSource.Monotonic.markNow()
        reportTyping(true)
    }

    /**
     * Uloží koncept s odstupem od poslední úpravy.
     *
     * Ne na každý úhoz: `setChatDraftMessage` se propisuje na server a do ostatních zařízení.
     * Hodnoty se berou **teď**, na hlavním vlákně — úloha běží v `appScope` mimo něj.
     */
    private fun scheduleDraftSave() {
        val text = draft
        val replyTo = _state.value.replyingTo?.id
        val quote = _state.value.replyQuote
        val linkPreview = _state.value.linkPreviewOptions

        draftSave?.cancel()
        draftSave = appScope.launch {
            delay(DRAFT_SAVE_DELAY_MS)
            saveDraft(text, replyTo, linkPreview, quote)
        }
    }

    private suspend fun saveDraft(
        text: ComposedText,
        replyTo: Long?,
        linkPreview: LinkPreviewOptions? = null,
        quote: ReplyQuote? = null,
    ) {
        repository.saveDraft(chatId, text.text, replyTo, linkPreview, quote, text.formats).onFailure { failure ->
            logger.w(failure) { "Koncept se nepodařilo uložit." }
        }
    }

    /**
     * Doplní citaci konceptu. Zprávu hledá v načtené historii, jinak si ji vyžádá — koncept
     * může odpovídat na cokoli.
     */
    private fun restoreDraftReply(messageId: Long, quote: ReplyQuote?) {
        viewModelScope.launch {
            val message = _state.value.messages.firstOrNull { it.id == messageId }
                ?: repository.message(chatId, messageId).getOrNull()
                ?: return@launch
            // Mezitím mohl uživatel začít odpovídat na něco jiného; to má přednost.
            _state.update { if (it.replyingTo == null) it.copy(replyingTo = message, replyQuote = quote) else it }
        }
    }

    /**
     * Zjistí náhled odkazu v rozepsaném textu, s pauzou po posledním úhozu
     * ([LINK_PREVIEW_DELAY_MS]) — `getLinkPreview` jde na síť a TDLib ho žádá nevolat často.
     *
     * Prázdné pole zahodí náhled i volbu (i vypnutí): je to nová zpráva. Vypnutý náhled se
     * nezjišťuje vůbec, dokud se pole nevyprázdní.
     */
    private fun refreshLinkPreview(text: String, delayMs: Long = LINK_PREVIEW_DELAY_MS) {
        linkPreviewJob?.cancel()
        linkPreviewText = text
        if (text.isBlank()) {
            // Vymazaný text úpravy není nová zpráva: volba upravované zprávy platí dál.
            if (_state.value.editing != null) {
                _state.update { it.copy(linkPreview = null, linkPreviewLinks = emptyList()) }
            } else {
                resetLinkPreview()
            }
            return
        }
        if (_state.value.linkPreviewOptions.disabled) return
        linkPreviewJob = viewModelScope.launch {
            delay(delayMs)
            val result = repository.linkPreview(text, _state.value.linkPreviewOptions.url).getOrElse { failure ->
                // Bez náhledu se dá psát i poslat dál; server si ho k odeslané zprávě vyrobí sám.
                logger.w(failure) { "Náhled odkazu se nepodařilo zjistit." }
                return@launch
            }
            _state.update {
                it.copy(
                    linkPreview = result.preview,
                    linkPreviewLinks = result.links,
                    linkPreviewOptions = it.linkPreviewOptions.forPreview(
                        previous = it.linkPreview,
                        next = result.preview,
                        links = result.links,
                    ),
                )
            }
        }
    }

    private fun resetLinkPreview() {
        linkPreviewJob?.cancel()
        linkPreviewJob = null
        _state.update {
            it.copy(linkPreview = null, linkPreviewLinks = emptyList(), linkPreviewOptions = LinkPreviewOptions())
        }
    }

    /**
     * Uživatel změnil volbu náhledu (odkaz, velikost, poloha, ✕). Patří ke konceptu, ukládá se
     * s ním. Jiný odkaz chce nový náhled hned — ťuknutí není psaní, na pauzu se nečeká.
     */
    fun setLinkPreviewOptions(options: LinkPreviewOptions) {
        val linkChanged = options.url != _state.value.linkPreviewOptions.url
        _state.update { it.copy(linkPreviewOptions = options) }
        // Při úpravě volba patří upravované zprávě, ne konceptu.
        if (_state.value.editing == null) scheduleDraftSave()
        if (linkChanged) refreshLinkPreview(linkPreviewText, delayMs = 0)
    }

    /**
     * Obrazovka text [fill] do pole vložila. Bez toho by se po otočení vložil znovu, i smazaný.
     *
     * **Nuluje se jen TEN text, který obrazovka vložila**, ne jakýkoli. Při otevření chatu jdou
     * dva hned po sobě — koncept z TDLib a za ním sdílený odkaz — a obrazovka vyřizuje ten první,
     * zatímco ve stavu už čeká druhý. Bezpodmínečné vynulování ho smazalo dřív, než ho obrazovka
     * uviděla: pole zůstalo prázdné, ViewModel přitom text vedl jako koncept, uložil ho do TDLib
     * a další sdílení ho připojilo k novému — odkaz se objevil až napodruhé, a to dvakrát.
     * Porovnává se instance: dvě vložení téhož textu jsou pořád dvě vložení.
     */
    fun consumeInputFill(fill: InputFill) {
        _state.update { if (it.inputFill === fill) it.copy(inputFill = null) else it }
    }

    /**
     * Přepne vstupní pole do úpravy zprávy.
     *
     * Rozepsaný text se **odloží a po skončení úpravy vrátí**. Úprava je vsuvka — kdo si
     * uprostřed psaní všimne překlepu v minulé zprávě, nechce kvůli tomu přijít o rozepsanou.
     */
    private fun startEdit(messageId: Long) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        val formats = composedFormatsOf(message.text, message.entities) ?: return
        enterEdit(message, ComposedText(message.text, formats), message.linkPreviewOptions ?: LinkPreviewOptions())
    }

    /**
     * Pole v úpravě [target] s textem [text] a volbou náhledu [linkOptions]. Náhled se zjistí
     * hned — text se do pole vkládá bez `onDraftChanged` a na pauzu v psaní není proč čekat.
     */
    private fun enterEdit(target: ChatMessage, text: ComposedText, linkOptions: LinkPreviewOptions) {
        // Přepnutí z jedné úpravy do druhé nesmí přepsat odložený koncept textem první úpravy.
        if (_state.value.editing == null) {
            draftBeforeEdit = draft
            linkPreviewBeforeEdit = _state.value.let {
                LinkPreviewStash(it.linkPreview, it.linkPreviewLinks, it.linkPreviewOptions)
            }
        }
        linkPreviewJob?.cancel()
        _state.update {
            it.copy(
                editing = target,
                messageMenu = null,
                inputFill = InputFill(text.text, onlyIfEmpty = false, formats = text.formats),
                linkPreview = null,
                linkPreviewLinks = emptyList(),
                linkPreviewOptions = linkOptions,
            )
        }
        if (target.composerEdit() == ComposerEdit.Text) refreshLinkPreview(text.text, delayMs = 0)
    }

    /** Zruší úpravu a vrátí do pole odložený koncept. */
    fun cancelEdit() {
        if (_state.value.editing == null) return
        leaveEdit()
    }

    private fun leaveEdit() {
        draft = draftBeforeEdit
        linkPreviewJob?.cancel()
        linkPreviewText = draftBeforeEdit.text
        val stash = linkPreviewBeforeEdit
        _state.update {
            it.copy(
                editing = null,
                inputFill = InputFill(draftBeforeEdit.text, onlyIfEmpty = false, formats = draftBeforeEdit.formats),
                linkPreview = stash.preview,
                linkPreviewLinks = stash.links,
                linkPreviewOptions = stash.options,
            )
        }
    }

    /**
     * Odešle úpravu.
     *
     * Z úprav se odchází **hned**, ne až po odpovědi serveru — výsledek dorazí updatem jako každá
     * jiná úprava a čekat na něj s rozepsaným polem by působilo zaseknutě. Když úprava selže,
     * vrátí se do pole i s textem, jinak by o něj uživatel přišel.
     *
     * **Popisek přílohy je jiné volání než text zprávy** (`editMessageCaption`): úprava textu by
     * popisek nezměnila. Prázdný popisek se tím odebere, kdežto prázdný text by zprávu jen nechal,
     * jak je — server ji odmítne a uživatel by o obsah přišel.
     */
    private fun sendEdit(target: ChatMessage, text: ComposedText) {
        val kind = target.composerEdit() ?: return
        val sanitized = if (settings.stripTrackingParameters.value) UrlCleaner.cleanComposedText(text) else text
        if (kind == ComposerEdit.Text && sanitized.text.isEmpty()) return

        val linkOptions = _state.value.linkPreviewOptions
        val linkPreview = linkOptions.forEdit(sanitized.text, shown = _state.value.linkPreview)
        leaveEdit()
        // Beze změny není co posílat; server by vrátil chybu „zpráva se nezměnila". Změna jen
        // formátování (tučné slovo) je ale změna.
        val optionsChanged = kind == ComposerEdit.Text &&
            linkOptions != (target.linkPreviewOptions ?: LinkPreviewOptions())
        val original = ComposedText(target.text, composedFormatsOf(target.text, target.entities).orEmpty()).trimmed()
        if (sanitized == original && !optionsChanged) return

        viewModelScope.launch {
            val edited = when (kind) {
                ComposerEdit.Text -> repository.editText(chatId, target.id, sanitized.text, linkPreview, sanitized.formats)
                ComposerEdit.Caption -> repository.editCaption(chatId, target.id, sanitized.text, sanitized.formats)
            }
            edited.onFailure { failure ->
                _state.update { it.copy(error = failure.reported(Res.string.conversation_edit_failed)) }
                enterEdit(target, text, linkOptions)
            }
        }
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

        val pending = draftSave ?: return
        if (!pending.isActive) return
        pending.cancel()

        val text = draft
        val replyTo = _state.value.replyingTo?.id
        val quote = _state.value.replyQuote
        val linkPreview = _state.value.linkPreviewOptions
        appScope.launch { saveDraft(text, replyTo, linkPreview, quote) }
    }

    private fun reportTyping(typing: Boolean) {
        // Diskrétní čtení skrývá i psaní. Ukončení psaní projde — nic neprozradí a zhasne
        // „píše…" ohlášené ještě před zapnutím volby.
        if (typing && settings.discreetReading.value) {
            logger.d { "Diskrétní čtení: psaní se nehlásí" }
            return
        }
        viewModelScope.launch {
            repository.sendTyping(chatId, typing).onFailure { failure ->
                // Třeba kanál, kam uživatel psát nesmí. Chyba kvůli „píše…" by jen obtěžovala.
                logger.d(failure) { "Činnost v chatu se nepodařilo ohlásit." }
            }
        }
    }

    /**
     * Otevře odkaz ze zprávy. Odkaz na Telegram otevře aplikace sama ([InternalLink]), ostatní
     * jde do prohlížeče.
     *
     * - chat (`t.me/jméno`) se otevře nad touhle konverzací, jako zmínka;
     * - odkaz na zprávu v TOMHLE chatu na ni skočí na místě (dojetím nebo přejezdem, se
     *   zvýrazněním), v jiném chatu ho otevře se skokem na zprávu;
     * - pozvánka ukáže náhled chatu ([ConversationState.invite]) — přidává se až na potvrzení;
     * - sada samolepek otevře svůj sheet.
     *
     * Rozpoznání je lokální volání TDLib, takže běžný webový odkaz jde do prohlížeče bez prodlevy.
     */
    private fun openLink(url: String, confirmExternal: Boolean = false) {
        viewModelScope.launch {
            chatRepository.resolveLink(url)
                .onSuccess { link ->
                    when (link) {
                        null -> if (confirmExternal) {
                            _state.update { it.copy(confirmLink = url) }
                        } else {
                            urlOpener.open(url)
                        }
                        is InternalLink.Chat -> openChatLink(link)
                        // Výběr chatu otevře shell sám — sdílení bez vybraného chatu (viz ShareRequests).
                        is InternalLink.ShareText -> shareRequests.start(link.text, hasFiles = false)
                        is InternalLink.Message -> when {
                            // Tentýž chat, a v tématu jen totéž téma — zpráva z jiného tématu
                            // v téhle historii není, skok by skončil „už není k dispozici".
                            // V tématu Uložených zpráv odkaz téma nenese, takže se otevře celý chat.
                            link.chatId == chatId && savedTopicId == null &&
                                (forumTopicId == null || link.topicId == forumTopicId) ->
                                link.messageId?.let { jumpToMessage(it, fromSearch = true) }
                            else -> openTarget(ChatTarget(link.chatId, link.messageId, topicId = link.topicId))
                        }
                        is InternalLink.Invite -> _state.update { it.copy(invite = InviteSheetState(link.link, link.preview)) }
                        is InternalLink.FolderInvite -> _state.update {
                            it.copy(folderInvite = FolderInviteSheetState(link.link, link.preview))
                        }
                        is InternalLink.StickerSet -> _state.update { it.copy(openStickerSet = link.setId) }
                        is InternalLink.Proxy -> proxyLinkFlow.open(link.config)
                        is InternalLink.BotStart -> openBotStart(link)
                        // Skupinu vybírá obrazovka výběru; přidání se potvrzuje až tam.
                        is InternalLink.BotStartInGroup -> _state.update { it.copy(addBotToGroup = link) }
                        is InternalLink.MainWebApp -> requestWebApp(
                            WebAppRequest(link.botUserId, url = "", launch = false, main = true, startParameter = link.startParameter),
                        )
                        is InternalLink.Screen -> _state.update { it.copy(openScreen = link.screen) }
                        is InternalLink.LoginWarning -> _state.update { it.copy(loginWarning = link.kind) }
                        is InternalLink.Story -> openStory(StoryTarget(link.posterChatId, link.storyId))
                        is InternalLink.WebApp -> requestWebApp(
                            WebAppRequest(
                                link.botUserId,
                                url = "",
                                launch = false,
                                startParameter = link.startParameter,
                                shortName = link.shortName,
                            ),
                        )
                    }
                }
                .onFailure { failure ->
                    val message = when (failure) {
                        is LinkUserNotFoundException -> UiText(Res.string.conversation_link_user_not_found)
                        is ContactLinkExpiredException -> UiText(Res.string.contact_link_expired)
                        is UnsupportedProxyException -> UiText(Res.string.proxy_unsupported)
                        else -> failure.reported(Res.string.conversation_link_failed)
                    }
                    _state.update { it.copy(error = message) }
                }
        }
    }

    /**
     * Odkaz na chat. Profil (`?profile`) se otevře nad touhle konverzací; jinak chat, a text
     * z odkazu (`?text=…`) čeká v jeho poli jako sdílený text — připojí se za rozepsaný koncept,
     * nic se neodesílá. Jde přes [ShareRequests] s vybraným chatem: konverzace si ho vyzvedne,
     * až obnoví vlastní koncept, a v tomhle chatu ho vyzvedne rovnou.
     */
    private fun openChatLink(link: InternalLink.Chat) {
        if (link.openProfile) {
            _state.update { it.copy(openProfile = link.chatId) }
            return
        }
        link.draftText?.let { text ->
            shareRequests.start(text, hasFiles = false, target = ShareTarget(link.chatId, confirm = false))
        }
        if (link.chatId != chatId) openTarget(ChatTarget(link.chatId))
    }

    /**
     * Startovací odkaz bota. S `autostart` se bot spustí hned, jinak se parametr odloží
     * ([BotStartRequests]) a chat bota nabídne Start — parametr píše odkaz, ne uživatel, a co
     * bot s ním udělá, se uživatel dozví až po ťuknutí.
     */
    private fun openBotStart(link: InternalLink.BotStart) {
        if (link.autostart) {
            viewModelScope.launch {
                repository.startBot(link.chatId, link.parameter).onFailure { failure ->
                    _state.update { it.copy(error = failure.reported(Res.string.conversation_bot_start_failed)) }
                }
            }
        } else {
            botStartRequests.request(link.chatId, link.parameter)
        }
        if (link.chatId != chatId) openTarget(ChatTarget(link.chatId))
    }

    /** Tlačítko Start: pošle „/start" s parametrem z odkazu, bez odkazu prázdný. */
    fun startBot() {
        if (_state.value.startingBot) return
        val parameter = _state.value.botStartParameter.orEmpty()
        _state.update { it.copy(startingBot = true) }
        viewModelScope.launch {
            repository.startBot(chatId, parameter)
                .onSuccess { pending ->
                    botStartRequests.consume(chatId)
                    _state.update { it.withMessage(pending).copy(startingBot = false) }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(startingBot = false, error = failure.reported(Res.string.conversation_bot_start_failed))
                    }
                }
        }
    }

    /** Odkaz z tlačítka bota potvrzený v dialogu. */
    fun openConfirmedLink() {
        val url = _state.value.confirmLink ?: return
        _state.update { it.copy(confirmLink = null) }
        urlOpener.open(url)
    }

    fun dismissLink() = _state.update { it.copy(confirmLink = null) }

    /**
     * Tlačítko „přihlásit na web" (`getLoginUrlInfo`).
     *
     * - **Web se dozví jméno, uživatelské jméno a fotku účtu**, takže se přihlášení potvrzuje
     *   dialogem s doménou ([LoginRequest]) — tlačítko popisuje bot a vést může kamkoli.
     * - Když server řekne „otevřít rovnou", jde to cestou obyčejného odkazu, i s potvrzením
     *   adresy, pokud ho server nevypne (`skipConfirmation`).
     * - **Chyba = obyčejný odkaz** (tak to předepisuje TDLib): tlačítko nezůstane mrtvé.
     */
    private fun loginWithButton(messageId: Long, button: BotButtonKind.LoginUrl) {
        viewModelScope.launch {
            repository.loginUrlInfo(chatId, messageId, button.buttonId)
                .onSuccess { prompt ->
                    when (prompt) {
                        is LoginUrlPrompt.Open ->
                            if (prompt.skipConfirmation) urlOpener.open(prompt.url) else openLink(prompt.url, confirmExternal = true)
                        is LoginUrlPrompt.Confirm ->
                            _state.update { it.copy(loginRequest = LoginRequest(messageId, button.buttonId, prompt)) }
                    }
                }
                .onFailure { openLink(button.url, confirmExternal = true) }
        }
    }

    /** Přihlášení potvrzené v dialogu; [allowWriteAccess] = bot smí uživateli psát. */
    fun confirmLogin(allowWriteAccess: Boolean) {
        val request = _state.value.loginRequest ?: return
        _state.update { it.copy(loginRequest = null) }
        viewModelScope.launch {
            repository.loginUrl(chatId, request.messageId, request.buttonId, allowWriteAccess)
                .onSuccess(urlOpener::open)
                // Přihlásit se nepovedlo, ale web uživatel chtěl — otevře se bez přihlášení a řekne se proč.
                .onFailure {
                    _state.update { it.copy(notice = UiText(Res.string.bot_login_failed)) }
                    urlOpener.open(request.prompt.url)
                }
        }
    }

    fun dismissLogin() = _state.update { it.copy(loginRequest = null) }

    fun dismissBotAlert() = _state.update { it.copy(botAlert = null) }

    /**
     * Tlačítko bota pod zprávou.
     *
     * - **Odkaz mimo Telegram se napřed potvrzuje.** U odkazu v textu uživatel adresu vidí,
     *   na tlačítku ne — popisek píše bot a „Ověřit účet" může vést kamkoli.
     * - **Na odpověď bota se čeká jen u jednoho tlačítka naráz.** Druhé ťuknutí během čekání by
     *   poslalo zpětné volání podruhé a u kontrolní otázky s jedním pokusem spálilo odpověď.
     * - Co Plume neumí, se VYSVĚTLÍ, tlačítko nezmizí (viz [BotButtonKind.Unsupported]).
     */
    private fun pressBotButton(messageId: Long, row: Int, column: Int) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        val button = message.keyboard?.rows?.getOrNull(row)?.getOrNull(column) ?: return
        when (val kind = button.kind) {
            BotButtonKind.Callback -> askBot(messageId, row, column, button.text)
            is BotButtonKind.Url -> openLink(kind.url, confirmExternal = true)
            is BotButtonKind.CopyText -> viewModelScope.launch {
                clipboard.copy(kind.text)
                if (!clipboard.confirmsCopy) _state.update { it.copy(notice = UiText(Res.string.bot_text_copied)) }
            }
            is BotButtonKind.User -> openMentioned { chatRepository.privateChat(kind.userId) }
            is BotButtonKind.WebApp -> requestWebApp(
                WebAppRequest(botOf(message), kind.url, launch = true),
            )
            BotButtonKind.Disabled -> Unit
            is BotButtonKind.LoginUrl -> loginWithButton(messageId, kind)
            is BotButtonKind.SwitchInline -> when (val target = kind.target) {
                SwitchInlineTarget.Current -> useInlineBot(kind.bot, kind.query)
                is SwitchInlineTarget.Chosen ->
                    _state.update { it.copy(switchInline = SwitchInlinePick(kind.bot, kind.query, target)) }
            }
            is BotButtonKind.Unsupported -> _state.update { it.copy(notice = UiText(kind.feature.explanation())) }
        }
    }

    private fun askBot(messageId: Long, row: Int, column: Int, text: String) {
        if (_state.value.pressingBotButton != null) return
        _state.update { it.copy(pressingBotButton = BotButtonRef(messageId, row, column)) }
        viewModelScope.launch {
            repository.pressBotButton(chatId, messageId, row, column, text)
                .onSuccess { answer ->
                    _state.update { state ->
                        when {
                            answer.text.isEmpty() -> state
                            answer.alert -> state.copy(botAlert = answer.text)
                            else -> state.copy(notice = UiText(Res.string.bot_answer, answer.text))
                        }
                    }
                    // Hra nebo start jiného bota; odkaz od bota je stejně skrytý jako na tlačítku.
                    if (answer.url.isNotEmpty()) openLink(answer.url, confirmExternal = true)
                }
                .onFailure { failure ->
                    val message = failure.reported(Res.string.bot_button_failed)
                    _state.update { it.copy(error = message) }
                }
            _state.update { it.copy(pressingBotButton = null) }
        }
    }

    /**
     * Hlas v anketě; prázdný seznam hlas zruší („Zrušit hlas" v nabídce).
     *
     * Nic se lokálně nepřepisuje: TDLib po `setPollAnswer` sám změní obsah zprávy — napřed
     * s `isBeingChosen` (u odpovědi se točí indikátor), po potvrzení se skutečnými počty.
     * Obojí dorazí přes [ConversationRepository.editedMessages] jako každá úprava.
     */
    private fun vote(messageId: Long, answerIndexes: List<Int>) {
        val poll = _state.value.messages.firstOrNull { it.id == messageId }?.poll ?: return
        // Hlas, který by server odmítl, se neposílá: uzavřená anketa, druhý hlas bez zrušení.
        val allowed = if (answerIndexes.isEmpty()) poll.canRetract else poll.canVote
        if (!allowed) return
        viewModelScope.launch {
            repository.vote(chatId, messageId, answerIndexes).onFailure { failure ->
                val message = failure.reported(
                    if (answerIndexes.isEmpty()) Res.string.poll_retract_failed else Res.string.poll_vote_failed,
                )
                _state.update { it.copy(error = message) }
            }
        }
    }

    fun cancelStopPoll() {
        _state.update { it.copy(pendingStopPoll = null) }
    }

    /** Uzavření ankety se ukáže samo — TDLib změní obsah zprávy a výsledky se odkryjí. */
    fun confirmStopPoll() {
        val messageId = _state.value.pendingStopPoll ?: return
        _state.update { it.copy(pendingStopPoll = null) }
        viewModelScope.launch {
            repository.stopPoll(chatId, messageId).onFailure { failure ->
                _state.update { it.copy(error = failure.reported(Res.string.poll_stop_failed)) }
            }
        }
    }

    /** Obrazovka formulář ankety otevřela. */
    /**
     * Mini aplikace bota. Souhlas před prvním otevřením u bota se ptá až obrazovka mini aplikace
     * (`WebAppViewModel`) — vstupů je víc než konverzace.
     */
    private fun requestWebApp(request: WebAppRequest) {
        _state.update { it.copy(openWebApp = request) }
    }

    fun consumeOpenWebApp() = _state.update { it.copy(openWebApp = null) }

    /** Bot, kterému zpráva s tlačítkem patří: odesílatel (kladné id = uživatel), jinak chat s botem. */
    private fun botOf(message: ChatMessage?): Long = message?.senderId?.takeIf { it > 0 } ?: chatId

    fun consumeOpenBotShare() = _state.update { it.copy(openBotShare = null) }

    fun consumeOpenPollComposer() {
        _state.update { it.copy(openPollComposer = null) }
    }

    fun dismissPollVoters() {
        _state.update { it.copy(pollVoters = null) }
    }

    /**
     * Stav účasti v soutěži. Sheet se otevře hned a stav dorazí ze serveru (`getGiveawayInfo`);
     * kdyby se čekalo s otevřením, ťuknutí by na chvíli vypadalo, že nic neudělalo.
     */
    private fun showGiveaway(messageId: Long) {
        _state.update { it.copy(giveaway = GiveawaySheet(messageId), messageMenu = null) }
        viewModelScope.launch {
            val result = repository.giveawayStatus(chatId, messageId)
                .onFailure { logger.w(it) { "Stav soutěže $messageId se nepodařilo zjistit" } }
            _state.update { state ->
                val sheet = state.giveaway?.takeIf { it.messageId == messageId } ?: return@update state
                state.copy(giveaway = sheet.copy(status = result.getOrNull(), failed = result.isFailure))
            }
        }
    }

    fun dismissGiveaway() {
        _state.update { it.copy(giveaway = null) }
    }

    /** Dárkový kód Premium do schránky; potvrzení ukáže sheet sám — snackbar by byl pod ním. */
    fun copyGiftCode(code: String) {
        viewModelScope.launch { clipboard.copy(code) }
        _state.update { state -> state.copy(giveaway = state.giveaway?.copy(codeCopied = true)) }
    }

    /**
     * Ťuknutí na hlasujícího: uživatel otevře soukromý chat, chat (anonymní správce, kanál)
     * sám sebe. Id drží konvenci odesílatele — kladné uživatel, záporné chat.
     */
    fun openPollVoter(senderId: Long) {
        _state.update { it.copy(pollVoters = null) }
        if (senderId > 0) {
            openMentioned { chatRepository.privateChat(senderId) }
        } else if (senderId != chatId) {
            openTarget(ChatTarget(senderId))
        }
    }

    /** Přidat se přes pozvánku z náhledu. Po přidání se chat otevře. */
    fun joinInvite() {
        val invite = _state.value.invite?.takeUnless { it.joining } ?: return
        _state.update { it.copy(invite = invite.copy(joining = true)) }
        viewModelScope.launch {
            chatRepository.joinByInvite(invite.link)
                .onSuccess { result ->
                    when (result) {
                        is InviteJoinResult.Joined -> {
                            _state.update { it.copy(invite = null) }
                            openTarget(ChatTarget(result.chatId))
                        }
                        InviteJoinResult.Requested ->
                            _state.update { it.copy(invite = null, notice = UiText(Res.string.invite_request_sent)) }
                    }
                }
                .onFailure { failure ->
                    val message = failure.reported(Res.string.conversation_join_failed)
                    _state.update { it.copy(invite = it.invite?.copy(joining = false), error = message) }
                }
        }
    }

    fun dismissInvite() {
        _state.update { it.copy(invite = null) }
    }

    fun toggleFolderInviteChat(chatId: Long) {
        _state.update { state ->
            val invite = state.folderInvite?.takeUnless { it.adding } ?: return@update state
            val selected = if (chatId in invite.selected) invite.selected - chatId else invite.selected + chatId
            state.copy(folderInvite = invite.copy(selected = selected))
        }
    }

    /**
     * Přidá sdílenou složku (nebo jen nové chaty do složky, kterou uživatel už má). Do vybraných
     * chatů se tím uživatel přidá; složka se pak objeví v řádku nad seznamem chatů sama
     * (`UpdateChatFolders`), takže se nikam nenaviguje — stačí hláška.
     */
    fun addFolderInvite() {
        val invite = _state.value.folderInvite?.takeUnless { it.adding || !it.canAdd } ?: return
        _state.update { it.copy(folderInvite = invite.copy(adding = true)) }
        viewModelScope.launch {
            chatRepository.addFolderByInvite(invite.link, invite.preview.missingChats.map { it.id }.filter { it in invite.selected })
                .onSuccess {
                    val done = if (invite.preview.folderId == 0) Res.string.folder_invite_added else Res.string.folder_invite_joined
                    _state.update { it.copy(folderInvite = null, notice = UiText(done, invite.preview.title)) }
                }
                .onFailure { failure ->
                    val message = failure.reported(Res.string.folder_invite_failed)
                    _state.update { it.copy(folderInvite = it.folderInvite?.copy(adding = false), error = message) }
                }
        }
    }

    fun dismissFolderInvite() {
        _state.update { it.copy(folderInvite = null) }
    }

    /** Obrazovka sheet sady otevřela. */
    fun consumeOpenStickerSet() {
        _state.update { it.copy(openStickerSet = null) }
    }

    /**
     * Zmínka v textu: najde chat a obrazovka ho otevře nad touhle konverzací ([ConversationState.openChat]).
     * Zmínka téhož chatu (sebe sama v Uložených zprávách, kanál ve vlastním příspěvku) nikam nevede.
     */
    private fun openMentioned(resolve: suspend () -> Result<Long>) {
        viewModelScope.launch {
            resolve()
                .onSuccess { target -> if (target != chatId) openTarget(ChatTarget(target)) }
                .onFailure { failure ->
                    val message = failure.reported(Res.string.conversation_mention_not_found)
                    _state.update { it.copy(error = message) }
                }
        }
    }

    /**
     * Chat k otevření nad konverzací. Skupina s tématy bez určeného tématu a zprávy se otevře
     * seznamem témat, jako ze seznamu chatů — zmínka `@skupina` i odkaz `t.me/skupina` by jinak
     * vedly do promíchané konverzace celé skupiny. Zjištění je lokální dotaz.
     */
    private fun openTarget(target: ChatTarget) {
        viewModelScope.launch {
            val forum = target.messageId == null && target.threadId == null && target.topicId == null &&
                chatRepository.isForum(target.chatId)
            _state.update { it.copy(openChat = target.copy(forum = forum)) }
        }
    }

    /**
     * Zdroj přeposlané zprávy. Původní příspěvek se otevře jako odkaz na zprávu: v tomhle chatu
     * skokem na místě, jinde konverzací se skokem na ni (i v kanálu, kde uživatel není — ten
     * dostane „Přidat se").
     */
    /**
     * Komentáře k příspěvku kanálu: vlákno v připojené diskusní skupině. Kde to je, ví jen
     * server (`getMessageThread`), navigace je věc obrazovky — jako u zmínky.
     */
    /**
     * Dočte useknutý článek („Číst dál"). Celý přijde jako nová podoba téže zprávy a nahradí ji
     * v seznamu; repository si ho pamatuje, takže další převody zprávy (reakce, přečtení)
     * nevrátí zase začátek.
     */
    private fun readFullArticle(messageId: Long) {
        if (messageId in _state.value.loadingArticles) return
        _state.update { it.copy(loadingArticles = it.loadingArticles + messageId) }
        viewModelScope.launch {
            repository.fullArticle(chatId, messageId)
                .onSuccess { message ->
                    _state.update { it.copy(messages = merge(it.messages, listOf(message))) }
                }
                .onFailure { failure ->
                    logger.w(failure) { "Článek $messageId se nepodařilo dočíst." }
                    _state.update { it.copy(notice = failure.userMessage(Res.string.article_load_failed)) }
                }
            _state.update { it.copy(loadingArticles = it.loadingArticles - messageId) }
        }
    }

    private fun openComments(messageId: Long) {
        viewModelScope.launch {
            repository.commentThread(chatId, messageId)
                .onSuccess { thread ->
                    _state.update { it.copy(openChat = ChatTarget(thread.chatId, threadId = thread.rootMessageId)) }
                }
                .onFailure { failure ->
                    _state.update { it.copy(error = failure.reported(Res.string.comments_unavailable)) }
                }
        }
    }

    private fun openForwardOrigin(origin: ForwardOrigin) {
        when (origin) {
            is ForwardOrigin.User -> openMentioned { chatRepository.privateChat(origin.userId) }
            is ForwardOrigin.Chat -> when {
                origin.chatId != chatId -> openTarget(ChatTarget(origin.chatId, origin.messageId))
                else -> origin.messageId?.let { jumpToMessage(it) }
            }
            // Skrytý účet server neprozradí, značka se u něj nedá ťuknout.
            is ForwardOrigin.Hidden -> Unit
        }
    }

    /**
     * Profil odesílatele ze skupiny. Profil stojí na CHATU, takže se napřed najde (a případně
     * lokálně založí) soukromý chat s ním — stejně jako při ťuknutí na člena v profilu skupiny.
     */
    private fun openSenderProfile(userId: Long) {
        viewModelScope.launch {
            chatRepository.privateChat(userId)
                .onSuccess { target -> _state.update { it.copy(openProfile = target) } }
                .onFailure { failure ->
                    _state.update { it.copy(error = failure.reported(Res.string.conversation_mention_not_found)) }
                }
        }
    }

    /** Obrazovka profil otevřela; jednorázová událost jako [consumeOpenChat]. */
    fun consumeOpenProfile() {
        _state.update { it.copy(openProfile = null) }
    }

    /** Obrazovka chat otevřela. Bez toho by se po otočení displeje otevřel znovu. */
    /**
     * Čas v médiu („1:23"). Médium je v téže zprávě, nebo ve zprávě, na kterou odpovídá
     * (`hasTimestampedMedia`) — tak to definuje TDLib. Video se otevře v prohlížeči od toho
     * místa (navigace je věc obrazovky, proto stav [ConversationState.openMediaAt]); hlasovka,
     * skladba a kolečko hrají v chatu, jako po ťuknutí na ně; video z náhledu odkazu se otevře na
     * webu od toho místa.
     */
    private fun playMediaAt(messageId: Long, seconds: Int) {
        val state = _state.value
        val message = state.messages.firstOrNull { it.id == messageId } ?: return
        val target = timestampTarget(message, state.messages, state.quotes) ?: return
        val startMs = seconds * 1000L
        when (val media = target.media) {
            is MessageMedia.Video -> _state.update { it.copy(openMediaAt = MediaAt(target.id, seconds)) }
            is MessageMedia.VoiceNote -> {
                _state.update { it.copy(soundingVideoNote = null) }
                voicePlayer.play(VoiceKey(chatId, target.id), startMs = startMs) { playback.voicePath(media) }
                if (!target.isOutgoing && !media.isListened) playback.reportListened(target.id)
            }
            is MessageMedia.Audio -> {
                _state.update { it.copy(soundingVideoNote = null) }
                musicQueue.play(chatId, target.id, media, startMs)
            }
            is MessageMedia.VideoNote -> {
                voicePlayer.stop()
                _state.update { it.copy(soundingVideoNote = target.id, soundingVideoNoteFromMs = startMs) }
                if (!target.isOutgoing && !media.isViewed) playback.reportListened(target.id)
            }
            // Video v náhledu odkazu (YouTube…) v aplikaci nepřehráváme: otevře se jeho web
            // od toho místa, kde to adresa umí (`videoLinkAt`).
            else -> target.linkPreview?.takeIf { it.isVideo }?.let { openLink(videoLinkAt(it.url, seconds)) }
        }
    }

    fun consumeOpenMediaAt() {
        _state.update { it.copy(openMediaAt = null) }
    }

    fun consumeOpenChat() {
        _state.update { it.copy(openChat = null) }
    }

    fun consumeOpenScreen() {
        _state.update { it.copy(openScreen = null) }
    }

    fun consumeOpenStory() {
        _state.update { it.copy(openStory = null) }
    }

    /**
     * Příběh z citace odpovědi nebo z odkazu: s fotkou či videem do prohlížeče, jinak jen věta.
     * Ptá se až teď — příběh mezitím mohl vypršet, a prohlížeč, který se otevře a nemá co
     * ukázat, je horší než hláška nad polem.
     */
    private fun openStory(target: StoryTarget) {
        viewModelScope.launch {
            val story = repository.story(target.posterChatId, target.storyId)
            when {
                story.content != null -> _state.update { it.copy(openStory = target) }
                story.isLive -> _state.update { it.copy(notice = UiText(Res.string.story_live_unsupported)) }
                else -> _state.update { it.copy(notice = UiText(Res.string.story_unavailable)) }
            }
        }
    }

    fun dismissLoginWarning() = _state.update { it.copy(loginWarning = null) }

    fun consumeAddBotToGroup() {
        _state.update { it.copy(addBotToGroup = null) }
    }

    fun consumeSwitchInline() {
        _state.update { it.copy(switchInline = null) }
    }

    /**
     * Vynucená odpověď: pole se samo přepne na odpověď na zprávu bota — jednou na zprávu, a jen
     * když se zrovna neupravuje ani neodpovídá na něco jiného. Rozepsanou odpověď by jinak
     * přepsala pod rukama.
     *
     * Zpráva bota do seznamu dorazí `newMessages`, klidně až po tomhle updatu, takže se na ni
     * chvíli počká.
     */
    private fun applyForceReply(messageId: Long) {
        if (!forceReplied.add(messageId)) return
        viewModelScope.launch {
            withTimeoutOrNull(FORCE_REPLY_WAIT) { _state.first { state -> state.messages.any { it.id == messageId } } }
                ?: return@launch
            val state = _state.value
            if (state.editing == null && state.replyingTo == null) replyTo(messageId)
        }
    }

    /**
     * Tlačítko vlastní klávesnice bota.
     *
     * Ve skupině jde text jako ODPOVĚĎ na zprávu s klávesnicí — tak to dělá oficiální klient
     * a bot mezi desítkami zpráv pozná, že patří jemu. V soukromém chatu je citace šum.
     */
    fun pressReplyKeyboardButton(row: Int, column: Int) {
        val keyboard = _state.value.replyMarkup as? ChatReplyMarkup.Keyboard ?: return
        val button = keyboard.rows.getOrNull(row)?.getOrNull(column) ?: return
        when (val kind = button.kind) {
            ReplyKeyboardButtonKind.Text -> viewModelScope.launch {
                repository.sendKeyboardText(chatId, button.text, keyboard.replyTarget())
                    .onSuccess { keyboardUsed(keyboard) }
                    .onFailure { failure ->
                        _state.update { it.copy(error = failure.reported(Res.string.conversation_send_failed)) }
                    }
            }
            // Číslo je údaj o uživateli; odejde až po potvrzení.
            ReplyKeyboardButtonKind.PhoneNumber -> _state.update { it.copy(confirmSharePhone = true) }
            // Poloha taky; zjišťuje ji až obrazovka po potvrzení (oprávnění a GPS jsou její).
            ReplyKeyboardButtonKind.Location -> _state.update { it.copy(confirmShareLocation = true) }
            // Výběr lidí a chatu je obrazovka nad konverzací; tlačítko patří zprávě s klávesnicí.
            is ReplyKeyboardButtonKind.Users ->
                _state.update { it.copy(openBotShare = BotShareRequest(keyboard.messageId, users = kind.request)) }
            is ReplyKeyboardButtonKind.Chat ->
                _state.update { it.copy(openBotShare = BotShareRequest(keyboard.messageId, chat = kind.request)) }
            // Formulář je obrazovka nad konverzací; otevírá ho obrazovka, ViewModel jen řekne jaký.
            is ReplyKeyboardButtonKind.Poll -> _state.update { it.copy(openPollComposer = kind.mode) }
            is ReplyKeyboardButtonKind.WebApp -> {
                val owner = _state.value.messages.firstOrNull { it.id == keyboard.messageId }
                requestWebApp(
                    WebAppRequest(botOf(owner), kind.url, launch = false, keyboardButtonText = button.text),
                )
            }
            is ReplyKeyboardButtonKind.Unsupported ->
                _state.update { it.copy(notice = UiText(kind.feature.explanation())) }
        }
    }

    /**
     * Tlačítko nabídky bota: mini aplikace přes `openWebApp`, jako z tlačítka pod zprávou —
     * TDLib bere adresu z `botMenuButton` právě tam. Soukromý chat s botem má id bota.
     */
    fun openBotMenuApp() {
        val app = _state.value.botMenuApp ?: return
        closeCommands()
        // Odkaz `t.me/bot/app` se zpracuje jako odkaz (TDLib to u tlačítka nabídky předepisuje).
        if (app.isLink) openLink(app.url) else requestWebApp(WebAppRequest(chatId, app.url, launch = true))
    }

    /** Tlačítko příkazů u pole: otevře seznam, druhé ťuknutí ho zavře. */
    fun toggleCommands() = _state.update { it.copy(commandsOpen = !it.commandsOpen) }

    fun closeCommands() = _state.update { it.copy(commandsOpen = false) }

    /**
     * `/příkaz` ťuknutý v textu zprávy. Jde jako samostatná zpráva a **rozepsaný koncept nechá
     * být** (`sendKeyboardText`, jako tlačítko vlastní klávesnice) — ťuknutí na příkaz v historii
     * není odeslání toho, co je v poli. Kam se psát nesmí, tam se nepošle nic; příkaz se tam ani
     * neukazuje jako klikatelný.
     */
    fun sendCommand(command: String) = sendBotCommand(command)

    private fun sendBotCommand(command: String) {
        if (!_state.value.canWrite) return
        viewModelScope.launch {
            repository.sendKeyboardText(chatId, command, replyToMessageId = null).onFailure { failure ->
                _state.update { it.copy(error = failure.reported(Res.string.conversation_send_failed)) }
            }
        }
    }

    /**
     * Rozepsaná zmínka u kurzoru ([mentionQueryAt]); `null` = u kurzoru se zmínka nepíše.
     *
     * Jen ve skupině (v soukromém chatu je koho zmínit jen jeden, v kanálu nikdo nepíše) a mimo
     * úpravu zprávy — úprava posílá holý text a zmínka jménem by z ní zmizela. Hledá se po krátké
     * pauze a dalším písmenem se zahodí; dosavadní návrhy mezitím zůstávají, panel neproblikne.
     */
    fun mentionQuery(query: String?) {
        mentionJob?.cancel()
        val info = _state.value.info
        val allowed = info != null && info.isGroup && !info.isChannel && _state.value.canWrite && _state.value.editing == null
        if (query == null || !allowed) {
            if (_state.value.mentionSuggestions != null) _state.update { it.copy(mentionSuggestions = null) }
            return
        }
        mentionJob = viewModelScope.launch {
            if (query.isNotEmpty()) delay(MENTION_QUERY_DELAY)
            repository.mentionCandidates(chatId, query)
                .onSuccess { found -> _state.update { it.copy(mentionSuggestions = MentionSuggestions(query, found)) } }
                .onFailure { failure -> logger.i { "Členy ke zmínce se nepodařilo najít: ${failure.message}" } }
        }
    }

    /**
     * Vybraný člen z napovídání. Pole přepisuje obrazovka ([mentionInsertion]); tady se jen
     * zapamatuje zmínka jménem — s uživatelským jménem odkaz vyrobí server sám.
     */
    fun mentionPicked(candidate: MentionCandidate) {
        mentionJob?.cancel()
        _state.update { it.copy(mentionSuggestions = null) }
        if (candidate.username == null) nameMentions += NameMention(candidate.firstName, candidate.userId)
    }

    /** Zmínky jménem pro odesílanou zprávu; s odesláním se zapomenou. */
    private fun takeNameMentions(): List<NameMention> = nameMentions.toList().also { nameMentions.clear() }

    /**
     * Rozepsaný text z pole jako dotaz na inline bota ([inlineQueryOf]); `null` = text dotaz není.
     *
     * Volá ho obrazovka při každé změně pole — i té, která jde mimo [onDraftChanged] (vrácený
     * koncept, „přes @bot" ťuknuté u zprávy). Dotaz jde po pauze v psaní ([INLINE_QUERY_DELAY]):
     * bot odpovídá ze serveru a dotaz na každé písmeno by ho zahltil. Předchozí výsledky mezitím
     * zůstávají vidět ([InlineQueryState.withQuery]).
     *
     * Kde se psát nesmí nebo se upravuje zpráva, se nic neptá — výsledek by nebylo kam poslat.
     */
    fun inlineQuery(draft: InlineQueryDraft?) {
        if (draft == inlineDraft) return
        inlineDraft = draft
        inlineJob?.cancel()
        if (draft == null || !_state.value.canWrite || _state.value.editing != null) {
            if (_state.value.inline != null) _state.update { it.copy(inline = null) }
            return
        }
        inlineJob = viewModelScope.launch {
            delay(INLINE_QUERY_DELAY)
            val bot = repository.inlineBot(draft.username).getOrNull()?.takeIf { it.userId !in notInlineBots }
            if (bot == null) {
                _state.update { it.copy(inline = null) }
                return@launch
            }
            _state.update { state ->
                val current = state.inline?.takeIf { it.bot.userId == bot.userId }
                state.copy(inline = current?.withQuery(draft.query) ?: InlineQueryState(bot, draft.query))
            }
            // Polohu zjišťuje obrazovka (podle `inline.bot.needLocation`) a dotaz pošle
            // [setInlineLocation], až ji má — nebo ví, že nebude.
            if (bot.needLocation && !inlineLocationResolved) return@launch
            loadInlinePage(bot, draft.query, offset = "")
        }
    }

    /**
     * „přes @bot" ťuknuté u zprávy: do pole se dá `@bot `, a výsledky se tím rovnou načtou.
     *
     * Rozepsanou zprávu to NEPŘEPÍŠE — ťuknutí na značku není důvod přijít o text. Přepíše se jen
     * prázdné pole nebo jiný rozepsaný dotaz na bota; jinak hláška, proč se nic nestalo.
     */
    private fun useInlineBot(username: String, query: String = "") {
        if (!_state.value.canWrite || _state.value.editing != null) return
        if (draft.text.isNotBlank() && inlineQueryOf(draft.text) == null) {
            _state.update { it.copy(notice = UiText(Res.string.conversation_via_bot_draft_busy)) }
            return
        }
        // Dotaz, ne text pro čtenáře: `@bot ` a mezera, za kterou se píše; u tlačítka bota
        // rovnou i jeho dotaz.
        val text = inlineQueryText(username, query)
        _state.update { it.copy(inputFill = InputFill(text, onlyIfEmpty = false)) }
        onDraftChanged(ComposedText(text))
    }

    /** Další stránka výsledků — dotáhne ji panel, když dojede na konec. */
    fun loadMoreInline() {
        val inline = _state.value.inline?.takeIf { it.canLoadMore } ?: return
        _state.update { it.copy(inline = inline.copy(loading = true)) }
        inlineJob = viewModelScope.launch { loadInlinePage(inline.bot, inline.query, inline.nextOffset) }
    }

    private suspend fun loadInlinePage(bot: InlineBotInfo, query: String, offset: String) {
        val location = inlineLocation.takeIf { bot.needLocation }
        repository.inlineResults(bot.userId, chatId, query, offset, location)
            .onSuccess { page ->
                _state.update { state ->
                    state.copy(inline = state.inline?.withPage(query, page, append = offset.isNotEmpty()))
                }
            }
            .onFailure { failure ->
                // Bot, který inline dotazy neumí, odpoví chybou — to není selhání, jen „tohle
                // není inline bot". Panel se schová, jako by `@jméno` byla obyčejná zmínka.
                logger.i { "Inline dotaz na @${bot.username} selhal: ${failure.message}" }
                // Jediné znění, podle kterého jde „neumím" poznat (ověřeno `strings libtdjsonjava.so`).
                if (failure.message?.contains("doesn't support inline queries") == true) notInlineBots += bot.userId
                _state.update { state ->
                    val inline = state.inline ?: return@update state
                    if (inline.resultsQuery == null) state.copy(inline = null) else state.copy(inline = inline.withFailure(query))
                }
            }
    }

    /**
     * Poloha pro bota, který o ni stojí; `null` = nedovolená nebo nezjištěná. Čekající dotaz
     * odejde (s polohou, nebo bez ní), a se změněnou polohou se zopakuje.
     */
    fun setInlineLocation(location: GeoPoint?) {
        val waiting = !inlineLocationResolved
        inlineLocationResolved = true
        val changed = location != null && location != inlineLocation
        if (location != null) inlineLocation = location
        if (!waiting && !changed) return
        val draft = inlineDraft ?: return
        inlineDraft = null
        inlineQuery(draft)
    }

    /**
     * Odešle výsledek inline dotazu. Rozepsaný dotaz v poli tím byl spotřebovaný — pole vyprázdní
     * obrazovka (přes [onDraftChanged], takže se smaže i koncept), citace se spotřebuje jako
     * u samolepky. Odchází hned, bez letu z pole.
     */
    fun sendInlineResult(result: InlineResult) {
        val inline = _state.value.inline ?: return
        if (!_state.value.canWrite || _state.value.editing != null) return
        inlineJob?.cancel()
        inlineDraft = null
        _state.update { it.copy(inline = null) }
        sendMedia(Res.string.conversation_send_failed) { replyTo, quote ->
            repository.sendInlineResult(chatId, inline.queryId, result.id, replyTo, quote)
        }
    }

    /**
     * Tlačítko nad výsledky. „Nastavit u bota" otevře chat s botem a rovnou ho spustí s parametrem
     * od bota — tak to dělají oficiální klienti (Bot API `switch_pm`); mini aplikaci Plume neumí.
     */
    fun pressInlineButton() {
        val inline = _state.value.inline ?: return
        when (val button = inline.button) {
            is InlineResultsButton.StartBot ->
                openBotStart(InternalLink.BotStart(inline.bot.userId, button.parameter, autostart = true))
            is InlineResultsButton.WebApp ->
                requestWebApp(WebAppRequest(inline.bot.userId, button.url, launch = false))
            null -> Unit
        }
    }

    fun sharePhone() {
        val keyboard = _state.value.replyMarkup as? ChatReplyMarkup.Keyboard
        _state.update { it.copy(confirmSharePhone = false) }
        viewModelScope.launch {
            repository.shareOwnPhone(chatId, keyboard?.replyTarget())
                .onSuccess { keyboard?.let(::keyboardUsed) }
                .onFailure { failure ->
                    _state.update { it.copy(error = failure.reported(Res.string.conversation_send_failed)) }
                }
        }
    }

    fun dismissSharePhone() = _state.update { it.copy(confirmSharePhone = false) }

    fun dismissShareLocation() = _state.update { it.copy(confirmShareLocation = false) }

    /**
     * Poloha z tlačítka klávesnice bota, už zjištěná obrazovkou. `null` = uživatel nedovolil
     * polohu, nebo ji zařízení nezjistilo — to musí být vidět, jinak by ťuknutí vypadalo, že nic
     * neudělalo. Jako odpověď ve skupině, stejně jako text tlačítka.
     */
    fun sendKeyboardLocation(point: GeoPoint?) {
        _state.update { it.copy(confirmShareLocation = false) }
        if (point == null) {
            _state.update { it.copy(notice = UiText(Res.string.bot_location_unavailable)) }
            return
        }
        val keyboard = _state.value.replyMarkup as? ChatReplyMarkup.Keyboard
        viewModelScope.launch {
            repository.sendLocation(chatId, Place("", "", point.latitude, point.longitude), keyboard?.replyTarget(), quote = null)
                .onSuccess { keyboard?.let(::keyboardUsed) }
                .onFailure { failure ->
                    _state.update { it.copy(error = failure.reported(Res.string.conversation_location_send_failed)) }
                }
        }
    }

    private fun ChatReplyMarkup.replyTarget(): Long? = messageId.takeIf { _state.value.info?.isGroup == true }

    /**
     * Jednorázová klávesnice se po použití ohlásí TDLib (`deleteChatReplyMarkup`) — chce to po
     * klientovi výslovně. Klávesnice tím NEZMIZÍ: podle Bot API se jen schová a zůstává
     * dostupná, což dělá obrazovka sbalením panelu.
     */
    private fun keyboardUsed(keyboard: ChatReplyMarkup.Keyboard) {
        if (!keyboard.oneTime) return
        settings.markOneTimeKeyboardUsed(chatId, keyboard.messageId)
        viewModelScope.launch {
            repository.dismissReplyMarkup(chatId, keyboard.messageId).onFailure { failure ->
                logger.w(failure) { "Jednorázovou klávesnici bota se nepodařilo odebrat." }
            }
        }
    }

    /**
     * Nastaví zprávu, na kterou se odpovídá, případně jen její úryvek ([quote]). Zobrazí se nad
     * vstupním polem.
     */
    private fun replyTo(messageId: Long, quote: ReplyQuote? = null) {
        // Swipe na odpověď se nabízí u každé zprávy; kde se psát nesmí, není kam odpovědět.
        if (!_state.value.canWrite) return
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        _state.update { it.copy(replyingTo = message, replyQuote = quote, messageMenu = null) }
        // Citace je součást konceptu — jen když už se něco píše, jinak není co ukládat.
        if (draft.text.isNotBlank()) scheduleDraftSave()
    }

    /**
     * Odpověď z prohlížeče médií. Prohlížeč listuje fotkami celého chatu, takže zpráva nemusí být
     * v načtené historii konverzace — pak se dočte (lokálně), citace ji nepotřebuje mít v seznamu.
     */
    private fun replyFromViewer(messageId: Long) {
        if (!_state.value.canWrite) return
        if (_state.value.messages.any { it.id == messageId }) {
            replyTo(messageId)
            return
        }
        viewModelScope.launch {
            repository.message(chatId, messageId).onSuccess { message ->
                _state.update { it.copy(replyingTo = message, replyQuote = null, messageMenu = null) }
                if (draft.text.isNotBlank()) scheduleDraftSave()
            }
        }
    }

    /** Přidá do ankety odpověď z dialogu. Nová odpověď přijde úpravou obsahu zprávy. */
    fun addPollOption(text: String, media: PollDraftMedia?) {
        val messageId = _state.value.addingPollOption ?: return
        val trimmed = text.trim()
        _state.update { it.copy(addingPollOption = null) }
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.addPollOption(chatId, messageId, trimmed, media).onFailure { failure ->
                _state.update { it.copy(error = failure.reported(Res.string.poll_add_option_failed)) }
            }
        }
    }

    fun dismissAddPollOption() {
        _state.update { it.copy(addingPollOption = null) }
    }

    /** Ztlumit / zapnout zvuk kanálu z pruhu místo vstupního pole. Potvrzení přijde updatem. */
    fun toggleMute() {
        val muted = _state.value.muted ?: return
        viewModelScope.launch {
            chatRepository.setMuted(chatId, !muted).onFailure { failure ->
                _state.update { it.copy(error = failure.reported(Res.string.profile_mute_failed)) }
            }
        }
    }

    /**
     * „Citovat" z nabídky nad zprávou s označeným textem: odpověď jen na úryvek.
     *
     * Místo úryvku se hledá v textu zprávy; TDLib ho bere jen jako odhad a repository si přesné
     * místo i formátování dohledá. Úryvek, který v textu není (nabídka ho neměla pustit), znamená
     * obyčejnou odpověď na celou zprávu.
     */
    private fun quoteReply(messageId: Long, text: String) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        val position = message.text.indexOf(text)
        replyTo(messageId, quote = ReplyQuote(text, position).takeIf { position >= 0 })
    }

    fun clearReply() {
        _state.update { it.copy(replyingTo = null, replyQuote = null) }
        if (draft.text.isNotBlank()) scheduleDraftSave()
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
     * Přepne reakci.
     *
     * Stav se **mění lokálně hned**, ještě než dorazí potvrzení ze serveru — jinak by mezi ťuknutím
     * a odpovědí byla viditelná prodleva a gesto by působilo rozbitě. Když volání selže, změna se
     * vrátí zpět. Server pak stejně pošle autoritativní stav přes `interactionUpdates`.
     */
    private fun toggleReaction(messageId: Long, emoji: String) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        val existing = message.reactions.firstOrNull { it.emoji == emoji }
        val add = existing?.isChosen != true

        val before = message.reactions
        applyReactions(messageId, before.toggled(emoji, add))

        viewModelScope.launch {
            repository.setReaction(chatId, messageId, emoji, add).onFailure { failure ->
                applyReactions(messageId, before)
                _state.update { it.copy(error = failure.reported(Res.string.conversation_reaction_failed)) }
            }
        }
        closeMenus()
    }

    /**
     * Rychlá reakce dvojitým ťuknutím: přepne reakci účtu ([QuickReaction]) jako ťuknutí na ni
     * v nabídce — už přidanou tedy odebere.
     *
     * **Dostupnost se ověřuje dřív, než se cokoli změní.** Kanál si sadu reakcí omezuje,
     * v Uložených zprávách reakce nejsou vůbec, a vlastní emoji může chtít Premium. Optimisticky
     * přidaná reakce, kterou server odmítne, by problikla a skončila chybovou hláškou — u gesta,
     * o kterém uživatel nemusí ani vědět, že ho udělal. Nedostupná reakce proto neudělá nic.
     * Odebrání vlastní reakce se neověřuje: ta zjevně dostupná byla.
     */
    private fun quickReact(messageId: Long) {
        val reaction = quickReaction.reaction.value ?: return
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        if (message.reactions.any { it.emoji == reaction && it.isChosen }) {
            toggleReaction(messageId, reaction)
            return
        }
        viewModelScope.launch {
            val available = repository.availableReactions(chatId, messageId).getOrNull() ?: return@launch
            if (reaction in available) toggleReaction(messageId, reaction)
        }
    }

    /**
     * Informace o zprávě z nabídky: kdy odešla, kdy ji kdo přečetl, kdo ji zhlédl a odkaz na ni.
     *
     * Načítá se až tady. Skoro žádná zpráva nemá všechna tři práva (`canGetReadDate`,
     * `canGetViewers`, `canGetLink`), takže ptát se dopředu by znamenalo tři dotazy na každou
     * zprávu v historii kvůli sheetu, který se otevře jednou za čas.
     */
    /**
     * Otevře text zprávy k výběru úryvku.
     *
     * Nabídku to ukončí, jako „Informace" — je to jednorázová akce, která si otevře vlastní
     * sheet. Text se bere týž, co by šel do schránky ([copyableText]), takže se výběr nabízí
     * přesně nad tím, co zpráva opravdu nese.
     */
    private fun selectText(messageId: Long) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        _state.update { it.copy(messageMenu = null, selectingText = message) }
    }

    fun dismissTextSelection() {
        _state.update { it.copy(selectingText = null) }
    }

    private fun showMessageInfo(messageId: Long) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        _state.update { it.copy(messageMenu = null, messageInfo = MessageInfoSheet(message = message)) }

        viewModelScope.launch {
            repository.messageInfo(chatId, messageId)
                .onSuccess { info ->
                    _state.update { state ->
                        // Mezitím mohl uživatel sheet zavřít nebo otevřít u jiné zprávy.
                        val open = state.messageInfo?.takeIf { it.message.id == messageId }
                            ?: return@update state
                        state.copy(messageInfo = open.copy(info = info))
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(
                            messageInfo = null,
                            error = failure.reported(Res.string.message_info_failed),
                        )
                    }
                }
        }
    }

    fun dismissMessageInfo() {
        _state.update { it.copy(messageInfo = null) }
    }

    /**
     * Přeloží text zprávy do zvoleného jazyka ([AppSettings.translationLanguage], jinak jazyk
     * aplikace, [platformLanguageCode]) a ukáže ho v sheetu.
     *
     * Sheet se otevře hned s načítáním — překlad jde přes server a trvá. Selhání zůstane v sheetu
     * jako věta, ne snackbar: pod modálním sheetem by snackbar nebyl vidět.
     */
    private fun showTranslation(messageId: Long) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        _state.update { it.copy(messageMenu = null) }
        translate(message, translationTarget())
    }

    /** Jazyk překladu: zvolený v nastavení, jinak jazyk aplikace. */
    private fun translationTarget(): String =
        settings.translationLanguage.value ?: normalizedLanguageCode(platformLanguageCode)

    /**
     * Shrnutí zprávy od serveru (`summarizeMessage`) v témž sheetu jako překlad a ve stejném
     * jazyce — kdo si překlady nastavil do češtiny, chce v ní i shrnutí. Nabízí se jen u zprávy,
     * u které ho server dovolí ([ChatMessage.summaryLanguage]).
     */
    private fun showSummary(messageId: Long) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        val canCopy = _state.value.messageMenu?.actions?.canCopy ?: false
        _state.update { it.copy(messageMenu = null) }
        translate(
            message,
            translationTarget(),
            summary = true,
            canCopy = canCopy,
        )
    }

    /**
     * Přeloží otevřenou zprávu do jiného jazyka a jazyk si zapamatuje pro další překlady. Jazyk
     * aplikace se neukládá jako kód, ale jako „výchozí" (`null`) — po změně jazyka aplikace se
     * pak překládá do nového.
     */
    fun changeTranslationLanguage(code: String) {
        val open = _state.value.translation ?: return
        settings.setTranslationLanguage(code.takeUnless { it == normalizedLanguageCode(platformLanguageCode) })
        translate(open.message, code, open.summary, open.canCopy)
    }

    private fun translate(
        message: ChatMessage,
        language: String,
        summary: Boolean = false,
        canCopy: Boolean = true,
        forceServer: Boolean = false,
    ) {
        // Shrnutí dělá jen server; překlad v telefonu, když si ho uživatel zapnul.
        val translator = onDeviceTranslator?.takeUnless { summary }
        val onDevice = translator != null && settings.translateOnDevice.value && !forceServer
        _state.update {
            it.copy(
                translation = TranslationSheet(
                    message,
                    language,
                    summary = summary,
                    canCopy = canCopy,
                    onDevice = if (translator != null) onDevice else null,
                ),
            )
        }
        if (onDevice) {
            translateOnDevice(message, language, download = false)
            return
        }

        viewModelScope.launch {
            val result = if (summary) {
                // Ve vlastním jazyce zprávy se shrnutí nepřekládá — server by překládal sám do sebe.
                val target = language.takeUnless { it == message.summaryLanguage?.let(::normalizedLanguageCode) }
                repository.summarizeMessage(chatId, message.id, target)
            } else {
                repository.translateMessage(chatId, message.id, language)
            }
            _state.update { state ->
                // Mezitím mohl uživatel sheet zavřít, otevřít u jiné zprávy nebo vybrat jiný jazyk.
                val open = state.translation
                    ?.takeIf { it.message.id == message.id && it.languageCode == language && it.summary == summary && it.onDevice != true }
                    ?: return@update state
                state.copy(
                    translation = result.fold(
                        onSuccess = { open.copy(body = it) },
                        onFailure = { failure ->
                            logger.w(failure) { if (summary) "Zprávu se nepodařilo shrnout." else "Zprávu se nepodařilo přeložit." }
                            open.copy(
                                error = failure.userMessage(
                                    if (summary) Res.string.server_summary_failed else Res.string.translation_failed,
                                ),
                            )
                        },
                    ),
                )
            }
        }
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

    /** Uživatel skryl varování před podvodem u zprávy. */
    /** Ťuknutí na ikonku akcí u zprávy: sheet s tím, co se ve zprávě našlo. */
    fun openMessageContext(messageId: Long, findings: List<ContextFinding>) {
        if (findings.isEmpty()) return
        val rates = exchangeRates?.takeIf { findings.any { it is ContextFinding.Money } }
        _contextSheet.value = ContextSheet(messageId, findings, homeCurrency, if (rates != null) RatesState.Loading else null)
        rates ?: return
        viewModelScope.launch {
            val table = rates.table()
            _contextSheet.update { open ->
                // Sheet mezitím mohl zavřít nebo otevřít jiný; kurz patří jen tomu, kdo o něj žádal.
                if (open?.messageId != messageId) open else open.copy(rates = table?.let(RatesState::Loaded) ?: RatesState.Failed)
            }
        }
    }

    /**
     * Položka akce v nabídce nad zprávou: co nic neukazuje, se provede rovnou, převody a QR platba
     * otevřou sheet (tentýž jako ikonka).
     */
    private fun runContextItem(messageId: Long, item: MessageMenuAction.ContextItem) {
        val findings = _state.value.messageMenu?.takeIf { it.messageId == messageId }?.contextFindings.orEmpty()
        val finding = findings.getOrNull(item.index)
        closeMenus()
        when (item.verb) {
            ContextVerb.Calendar -> (finding as? ContextFinding.Event)?.let(::addToCalendar)
            ContextVerb.FlightCalendar, ContextVerb.ArrivalCalendar ->
                (finding as? ContextFinding.Flight)?.let { addFlightToCalendar(messageId, it) }
            ContextVerb.FlightStatus -> (finding as? ContextFinding.Flight)?.let { trackParcel(it.trackingUrl()) }
            ContextVerb.Maps -> (finding as? ContextFinding.Address)?.let { openMaps(it.text) }
            ContextVerb.SaveContact -> (finding as? ContextFinding.Contact)?.let(::saveContact)
            ContextVerb.JoinWifi -> (finding as? ContextFinding.Wifi)?.let(::joinWifi)
            ContextVerb.CopyCode -> (finding as? ContextFinding.Code)?.let { copyFromContext(it.value) }
            ContextVerb.CopyBooking -> (finding as? ContextFinding.Booking)?.let { copyFromContext(it.value) }
            ContextVerb.TrackParcel -> (finding as? ContextFinding.Parcel)?.let { trackParcel(it.trackingUrl(platformLanguageCode)) }
            ContextVerb.Payment, ContextVerb.Money, ContextVerb.Units, ContextVerb.Wifi -> openMessageContext(messageId, findings)
        }
    }

    /** „Akce ze zprávy…" v nabídce nad zprávou: tentýž sheet jako ikonka. */
    private fun openContextFromMenu(messageId: Long) {
        val findings = _state.value.messageMenu?.takeIf { it.messageId == messageId }?.contextFindings.orEmpty()
        closeMenus()
        openMessageContext(messageId, findings)
    }

    /**
     * Akce z textu zprávy pro nabídku nad ní — totéž, co ukazuje ikonka (`messageContext`
     * v položce), jen bez ohledu na vypínač ikonky. Termín, který model posoudil, se bere
     * podle jeho posudku; nejistý bez posudku se nenabízí.
     */
    private fun contextFindingsOf(message: ChatMessage): List<ContextFinding> {
        if (!message.offersContext() || _state.value.info?.protectsContent == true) return emptyList()
        // U zprávy s varováním před podvodem ne — QR platba k podvodu je to, co podvodník chce.
        if (scamVerdicts.value.containsKey(message.id)) return emptyList()
        val zone = TimeZone.currentSystemDefault()
        val sentAt = Instant.fromEpochSeconds(message.timestamp.toLong()).toLocalDateTime(zone)
        val found = findMessageContext(message.text, message.entities, sentAt, zone, homeCurrency, metricUnits)
        if (found.isEmpty) return emptyList()
        val verdict = messageContexts?.verdicts?.value?.get(MessageContexts.Key(chatId, message.id, message.text.hashCode()))
        return found.contextFindings(message.text, verdict, Clock.System.now().toLocalDateTime(zone))
    }

    fun closeMessageContext() {
        _contextSheet.value = null
    }

    /** Událost do kalendáře; editor kalendáře ji ukáže a uloží až uživatel. */
    fun addToCalendar(event: ContextFinding.Event) {
        val calendar = calendarEvents ?: return
        val added = calendar.add(
            CalendarEvent(
                title = event.title,
                description = event.description,
                place = event.place,
                start = event.start,
                allDay = event.allDay,
            ),
        )
        if (added) {
            closeMessageContext()
        } else {
            _state.update { it.copy(notice = UiText(Res.string.message_context_no_calendar)) }
        }
    }

    /**
     * Let do kalendáře: „Let OK 123" (nebo „Přílet OK 123") s časem ze zprávy v pásmu letiště,
     * koncem podle přistání (nebo odhadu z trasy), letištěm jako místem a trasou a zprávou
     * v popisu, kde bývá i kód rezervace.
     */
    fun addFlightToCalendar(messageId: Long, flight: ContextFinding.Flight) {
        val calendar = calendarEvents ?: return
        val start = flight.at ?: return
        val text = _state.value.messages.firstOrNull { it.id == messageId }?.text?.trim().orEmpty()
        val schedule = flight.schedule(TimeZone.currentSystemDefault())
        // Místo je letiště, odkud se letí (u příletu kam); trasa jde na začátek popisu.
        val airport = (if (flight.arrival) flight.to else flight.from)?.let(AIRPORTS::get)
        val route = listOfNotNull(flight.from, flight.to).takeIf { it.size == 2 }?.joinToString(" \u2192 ")
        val description = listOfNotNull(route, text.takeIf { it.isNotEmpty() }).joinToString("\n\n")
        viewModelScope.launch {
            val name = "${flight.airline} ${flight.number}"
            val title = readString(if (flight.arrival) Res.string.message_context_flight_arrival_title else Res.string.message_context_flight_title, name)
            val added = calendar.add(
                CalendarEvent(
                    title = title,
                    description = description,
                    place = airport?.label,
                    start = start,
                    allDay = flight.allDay,
                    begin = schedule?.start,
                    end = schedule?.end,
                ),
            )
            if (added) {
                closeMessageContext()
            } else {
                _state.update { it.copy(notice = UiText(Res.string.message_context_no_calendar)) }
            }
        }
    }

    /** Údaj z akcí u zprávy (číslo účtu, částka, číslo zásilky) do schránky. */
    fun copyFromContext(text: String) {
        viewModelScope.launch {
            clipboard.copy(text)
            if (!clipboard.confirmsCopy) _state.update { it.copy(notice = UiText(Res.string.bot_text_copied)) }
        }
    }

    /** Sledování zásilky u dopravce nebo stav letu. Adresu skládáme sami, takže se na odkaz neptá. */
    fun trackParcel(url: String) {
        closeMessageContext()
        openLink(url, confirmExternal = false)
    }

    /** Umí telefon nabídnout připojení k Wi-Fi ze zprávy? Na iOS ne (viz `WifiJoiner`). */
    val canJoinWifi: Boolean get() = wifiJoiner?.canJoin == true

    /**
     * Připojení k Wi-Fi ze zprávy. Kde systém dialog „Uložit síť?" nemá a otevře se jen nastavení,
     * heslo jde do schránky — v nastavení se pak jen vloží.
     */
    fun joinWifi(wifi: ContextFinding.Wifi) {
        val joiner = wifiJoiner ?: return
        when (joiner.join(wifi.ssid, wifi.password)) {
            WifiJoin.Dialog -> closeMessageContext()
            WifiJoin.Settings -> {
                closeMessageContext()
                viewModelScope.launch {
                    clipboard.copy(wifi.password)
                    _state.update { it.copy(notice = UiText(Res.string.message_context_wifi_settings, wifi.ssid)) }
                }
            }
            WifiJoin.Failed -> _state.update { it.copy(notice = UiText(Res.string.message_context_wifi_failed)) }
        }
    }

    /** Kontakt ze zprávy do systémového editoru kontaktů; uloží ho až uživatel. */
    fun saveContact(contact: ContextFinding.Contact) {
        val saver = contactSaver ?: return
        if (saver.save(NewContact(name = contact.name, phone = contact.phone, email = contact.email))) {
            closeMessageContext()
        } else {
            _state.update { it.copy(notice = UiText(Res.string.message_context_no_contacts)) }
        }
    }

    /** Adresa nebo místo z akcí u zprávy v mapách platformy (hledání textem). */
    fun openMaps(query: String) {
        closeMessageContext()
        urlOpener.open(mapsSearchUrl(query))
    }

    /** „Připomenout…" z akcí u termínu — tentýž sheet jako z nabídky nad zprávou. */
    fun remindFromContext(messageId: Long) {
        closeMessageContext()
        requestRemind(messageId)
    }

    fun dismissScamWarning(messageId: Long) {
        scamWarnings?.dismiss(chatId, messageId)
    }

    fun dismissTranslation() {
        _state.update { it.copy(translation = null) }
    }

    /**
     * Přepínač „Překládat v telefonu" v sheetu: zapamatuje se pro další překlady a otevřená
     * zpráva se přeloží znovu tou cestou, kterou si uživatel právě vybral.
     */
    fun setTranslateOnDevice(onDevice: Boolean) {
        settings.setTranslateOnDevice(onDevice)
        val open = _state.value.translation ?: return
        if (onDevice) {
            _state.update { it.copy(translation = open.copy(body = null, error = null, onDevice = true, needsDownload = false)) }
            translateOnDevice(open.message, open.languageCode, download = false)
        } else {
            translate(open.message, open.languageCode, open.summary, open.canCopy, forceServer = true)
        }
    }

    /** Uživatel potvrdil stažení jazykových balíčků. */
    fun downloadTranslationModels() {
        val open = _state.value.translation?.takeIf { it.needsDownload } ?: return
        translateOnDevice(open.message, open.languageCode, download = true)
    }

    /** Překlad v telefonu nejde (neznámý jazyk, nepodporovaný pár) — jednou přes server. */
    fun translateOnServer() {
        val open = _state.value.translation ?: return
        translate(open.message, open.languageCode, open.summary, open.canCopy, forceServer = true)
    }

    /**
     * Překlad v telefonu. Jazyk zprávy rozpozná zařízení ([languageDetector]) — nic se kvůli
     * tomu neposílá. Chybějící jazykové balíčky se nestahují samy: sheet se zeptá
     * ([TranslationSheet.needsDownload]) a teprve [download] je stáhne.
     */
    private fun translateOnDevice(message: ChatMessage, language: String, download: Boolean) {
        val translator = onDeviceTranslator ?: return
        fun update(change: (TranslationSheet) -> TranslationSheet) = _state.update { state ->
            val open = state.translation
                ?.takeIf { it.message.id == message.id && it.languageCode == language && it.onDevice == true }
                ?: return@update state
            state.copy(translation = change(open))
        }
        update { it.copy(body = null, error = null, needsDownload = false, downloading = download) }

        viewModelScope.launch {
            val text = message.text
            val source = languageDetector.detect(text)?.let(::deviceTranslationCode)
            val target = deviceTranslationCode(language)
            val problem = when {
                source == null -> Res.string.translation_device_unknown
                target == null || !translator.supports(source) || !translator.supports(target) ->
                    Res.string.translation_device_unsupported
                source == target -> Res.string.translation_device_same
                else -> null
            }
            if (problem != null || source == null || target == null) {
                update { it.copy(error = UiText(problem ?: Res.string.translation_failed), downloading = false) }
                return@launch
            }
            if (!translator.hasModels(source, target)) {
                if (!download) {
                    update { it.copy(needsDownload = true) }
                    return@launch
                }
                if (translator.download(source, target).isFailure) {
                    update { it.copy(error = UiText(Res.string.translation_device_download_failed), downloading = false) }
                    return@launch
                }
            }
            val result = translator.translate(text, source, target)
            update { open ->
                result.fold(
                    onSuccess = { open.copy(body = MessageBody(it), downloading = false) },
                    onFailure = { open.copy(error = UiText(Res.string.translation_failed), downloading = false) },
                )
            }
        }
    }

    /**
     * Zkopíruje překlad a sheet zavře. Potvrzení jde snackbarem až po zavření — pod modálním
     * sheetem by vidět nebylo (stejně jako u odkazu v „Informace").
     */
    fun copyTranslation() {
        val open = _state.value.translation?.takeIf { it.canCopy } ?: return
        val text = open.body?.text ?: return
        viewModelScope.launch { clipboard.copy(text) }
        val notice = if (open.summary) Res.string.server_summary_copied else Res.string.translation_copied
        _state.update { it.copy(translation = null, notice = if (clipboard.confirmsCopy) it.notice else UiText(notice)) }
    }

    /**
     * Odkaz na zprávu do schránky.
     *
     * Sheet se přitom zavře. Jednak je tím skopírování hotová věc — kdo si o odkaz řekl, ten ho
     * má —, jednak by snackbar s potvrzením zůstal schovaný POD sheetem a nebylo by poznat,
     * že se něco stalo. Android 13+ ukazuje vlastní náhled schránky, takže tam snackbar odpadá
     * (viz [TextClipboard.confirmsCopy]); iOS a starší Androidy ho nemají.
     */
    fun copyMessageLink(link: String) {
        viewModelScope.launch {
            clipboard.copy(link)
            _state.update {
                it.copy(
                    messageInfo = null,
                    notice = if (clipboard.confirmsCopy) it.notice else UiText(Res.string.message_info_link_copied),
                )
            }
        }
    }

    /**
     * Ťuknutí na vlastní zprávu: ukáže u ní odznak přečtení, druhé ťuknutí ho schová.
     *
     * Nic se nikam neposílá — stav přečtení je v `lastReadOutboxMessageId`, posuvném ukazateli
     * na chatu, takže se odvodí porovnáním id. Cizí a servisní zprávy odznak nemají, takže se
     * u nich ťuknutí zahodí.
     */
    private fun toggleReadReceipt(messageId: Long) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        if (!message.isOutgoing || message.isService) return

        _state.update { it.copy(readReceiptFor = messageId.takeIf { id -> id != it.readReceiptFor }) }
    }

    /**
     * Ťuknutí na čip reakce: otevře detail a dotáhne, kdo reagoval.
     *
     * Reakci to **nepřepíná**. Přidat i odebrat jde z nabídky nad zprávou, kde je vybraná reakce
     * zvýrazněná a ťuknutí na ni ji sundá — jinak by se ťuknutí na čip pralo o význam
     * s otevřením detailu.
     */
    private fun showReactionDetails(messageId: Long) {
        val counts = _state.value.messages.firstOrNull { it.id == messageId }?.reactions.orEmpty()
        _state.update { it.copy(reactionDetails = ReactionDetails(messageId = messageId, counts = counts)) }
        loadReactionDetails(messageId, filter = null)
    }

    /**
     * Zúží detail na jednu reakci ([emoji]), `null` vrátí všechny. Dosavadní seznam zůstává, dokud
     * nedorazí nový — výměna za indikátor by sheet pod prstem smrštila a zase natáhla.
     */
    fun filterReactionDetails(emoji: String?) {
        val open = _state.value.reactionDetails ?: return
        if (open.filter == emoji) return
        _state.update { it.copy(reactionDetails = open.copy(filter = emoji)) }
        loadReactionDetails(open.messageId, emoji)
    }

    private fun loadReactionDetails(messageId: Long, filter: String?) {
        viewModelScope.launch {
            repository.reactionDetails(chatId, messageId, filter)
                .onSuccess { details ->
                    _state.update { state ->
                        // Mezitím mohl uživatel sheet zavřít, otevřít u jiné zprávy nebo přepnout filtr.
                        val open = state.reactionDetails?.takeIf { it.messageId == messageId && it.filter == filter }
                            ?: return@update state
                        state.copy(reactionDetails = open.copy(loading = false, items = details))
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(
                            reactionDetails = null,
                            error = failure.reported(Res.string.conversation_reaction_failed),
                        )
                    }
                }
        }
    }

    fun dismissReactionDetails() {
        _state.update { it.copy(reactionDetails = null) }
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
            is MessageAction.ShowMenu -> showMessageMenu(messageId, action.items, action.pressed)
            MessageAction.CloseMenu -> closeMenus()
            MessageAction.ShowNoticeMenu -> showNoticeMenu(messageId)
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
            is MessageAction.React -> toggleReaction(messageId, action.emoji)
            MessageAction.QuickReact -> quickReact(messageId)
            MessageAction.ShowReactions -> showReactionDetails(messageId)
            MessageAction.ToggleReadReceipt -> toggleReadReceipt(messageId)
            MessageAction.SwipeReply -> replyTo(messageId)
            // U alba na fotku, na které stisk začal, ne na zprávu s popiskem, které patří blok.
            MessageMenuAction.Reply -> replyTo(_state.value.messageMenu?.takeIf { it.messageId == messageId }?.replyTarget ?: messageId)
            is MessageAction.JumpTo -> jumpToMessage(action.messageId)
            is MessageAction.PressBotButton -> pressBotButton(messageId, action.row, action.column)
            is MessageAction.Vote -> vote(messageId, action.answerIndexes)
            MessageAction.ShowPollVoters -> _state.update { it.copy(pollVoters = messageId) }
            MessageAction.AddPollOption -> _state.update { it.copy(addingPollOption = messageId) }
            MessageMenuAction.RetractVote -> {
                closeMenus()
                vote(messageId, emptyList())
            }
            MessageMenuAction.StopPoll -> _state.update { it.copy(messageMenu = null, pendingStopPoll = messageId) }
            is MessageAction.OpenLink -> openLink(action.url)
            is MessageAction.OpenInstantView -> instantViewRequests?.request(action.url) ?: openLink(action.url)
            is MessageAction.OpenMention -> openMentioned { chatRepository.chatByUsername(action.username) }
            is MessageAction.OpenUser -> openMentioned { chatRepository.privateChat(action.userId) }
            MessageAction.AddContact -> addSharedContact(messageId)
            MessageAction.CallBack -> callBack(messageId)
            MessageAction.StopLiveLocation -> stopLiveLocation(messageId)
            MessageAction.SetProximityAlert -> openProximityAlert(messageId)
            is MessageAction.OpenChat -> openMentioned { Result.success(action.chatId) }
            MessageAction.ShowGiveaway -> showGiveaway(messageId)
            is MessageAction.SendBotCommand -> sendBotCommand(action.command)
            is MessageAction.UseInlineBot -> useInlineBot(action.username)
            is MessageAction.OpenSenderProfile -> openSenderProfile(action.userId)
            is MessageAction.OpenForwardOrigin -> openForwardOrigin(action.origin)
            MessageAction.OpenComments -> openComments(messageId)
            MessageAction.ReadFullArticle -> readFullArticle(messageId)
            is MessageAction.ToggleArticleAudio -> playback.toggleArticleAudio(messageId, action.index)
            is MessageAction.SeekArticleAudio ->
                voicePlayer.seek(VoiceKey(chatId, messageId, part = action.index), action.fraction)
            is MessageAction.OpenArticleDocument -> documents.openArticleDocument(messageId, action.index)
            MessageAction.OpenDocument -> documents.openDocument(messageId)
            MessageAction.CancelUpload -> cancelUpload(messageId)
            MessageAction.ExplainPaidMedia -> _state.update { it.copy(notice = UiText(Res.string.paid_media_unsupported)) }
            MessageAction.ExplainLiveStory -> _state.update { it.copy(notice = UiText(Res.string.story_live_unsupported)) }
            is MessageAction.OpenStory -> openStory(StoryTarget(action.posterChatId, action.storyId))
            MessageAction.ToggleVoice -> playback.toggleVoice(messageId)
            MessageAction.ToggleVideoNoteSound -> playback.toggleVideoNoteSound(messageId)
            is MessageAction.PlayMediaAt -> playMediaAt(messageId, action.seconds)
            MessageAction.VideoNoteSoundEnded -> _state.update {
                if (it.soundingVideoNote == messageId) it.copy(soundingVideoNote = null) else it
            }
            MessageAction.CycleVoiceSpeed -> voicePlayer.cycleSpeed()
            MessageAction.Transcribe -> playback.transcribe(messageId)
            is MessageAction.SeekVoice -> playback.seekVoice(messageId, action.fraction)
            MessageMenuAction.Edit -> startEdit(messageId)
            MessageMenuAction.Copy -> copyMessage(messageId)
            is MessageAction.QuoteReply -> quoteReply(messageId, action.text)
            is MessageAction.CopyText -> copyText(action.text)
            MessageMenuAction.ToggleFavoriteSticker -> toggleFavoriteSticker(messageId)
            MessageMenuAction.ToggleBookmark -> toggleBookmark(messageId)
            MessageMenuAction.Forward -> requestForward(messageId)
            MessageMenuAction.Remind -> requestRemind(messageId)
            MessageMenuAction.Info -> showMessageInfo(messageId)
            MessageMenuAction.Translate -> showTranslation(messageId)
            MessageMenuAction.Summarize -> showSummary(messageId)
            // Vysvětlení pouští obrazovka (sheet shrnutí); tady se jen zavře nabídka.
            MessageMenuAction.Explain -> _state.update { it.copy(messageMenu = null) }
            MessageMenuAction.ContextActions -> openContextFromMenu(messageId)
            is MessageMenuAction.ContextItem -> runContextItem(messageId, action)
            MessageMenuAction.SelectText -> selectText(messageId)
            MessageMenuAction.Pin -> requestPin(messageId)
            MessageMenuAction.Unpin -> unpinFromMenu(messageId)
            MessageMenuAction.Delete -> requestDelete(messageId)
            MessageMenuAction.Resend -> resend(messageId)
            MessageMenuAction.Select -> startSelection(messageId)
            MessageMenuAction.Report -> reportMessage(messageId)
            MessageAction.ToggleSelection -> toggleSelection(messageId)
            MessageMenuAction.ShareFile -> documents.shareDocument(messageId)
            MessageAction.OpenMedia, is MessageAction.OpenPollMedia, is MessageAction.OpenArticleMedia,
            is MessageAction.OpenStickerSet,
            is MessageAction.SearchHashtag,
            MessageMenuAction.SaveFileAs ->
                logger.w { "Akce $action patří obrazovce, do ViewModelu nemá chodit." }
        }
    }

    /**
     * Nabídnout „Vysvětlit"? Se zapnutou AI v zařízení a modelem, který telefon umí (stažený být
     * nemusí — sheet pak řekne, kde ho stáhnout, stejně jako u shrnutí konverzace).
     */
    private suspend fun explainAvailable(): Boolean {
        val ai = onDeviceAi ?: return false
        if (!settings.onDeviceAiEnabled.value) return false
        return runCatching { ai.status(settings.onDeviceAiVariant.value) != OnDeviceAiStatus.Unsupported }.getOrDefault(false)
    }

    /** Nabídka s kandidáty, které nabídl blok; co z nich server dovolí, se zjistí tady. */
    private fun showMessageMenu(messageId: Long, items: List<MessageMenuAction>, pressed: Long? = null) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return

        viewModelScope.launch {
            val sticker = message.media as? MessageMedia.Sticker

            val (emoji, actions, favorite, inTranslationLanguage) = coroutineScope {
                val emojiTask = async { repository.availableReactions(chatId, messageId) }
                val actionsTask = async { repository.messageActions(chatId, messageId) }
                // Oblíbenost jen u samolepky; seznam oblíbených je krátký a TDLib ho má lokálně.
                val favoriteTask = async {
                    sticker?.let { s ->
                        stickerRepository.favoriteStickers().first().any { it.sendFileId == s.stickerFileId }
                    }
                }
                // Rozpoznání jazyka jen kvůli „Přeložit", a se stropem: nabídka čeká na všechny
                // úlohy a pomalé rozpoznání (studený model) ji nesmí zdržet — pak se prostě nabízí.
                val languageTask = async {
                    val text = languageDetectionText(message.text)
                    if (MessageMenuAction.Translate !in items || text == null) return@async false
                    val detected = withTimeoutOrNull(LANGUAGE_DETECTION_TIMEOUT_MS) { languageDetector.detect(text) }
                    alreadyInLanguage(detected, translationTarget())
                }
                MenuLoad(
                    emojiTask.await().getOrDefault(emptyList()),
                    actionsTask.await().getOrElse { failure ->
                        logger.w(failure) { "Příznaky zprávy $messageId se nepodařilo zjistit." }
                        MessageActions.Fallback
                    },
                    favoriteTask.await(),
                    languageTask.await(),
                )
            }

            val explainAvailable = MessageMenuAction.Explain in items && explainAvailable()
            val contextFindings = if (MessageMenuAction.ContextActions in items) contextFindingsOf(message) else emptyList()
            // Kandidát na akce z textu se rozbalí na položky jednotlivých akcí.
            val shownItems = items.flatMap { item ->
                if (item == MessageMenuAction.ContextActions) contextMenuItems(contextFindings, canJoinWifi) else listOf(item)
            }
            _state.update {
                it.copy(
                    messageMenu = MessageMenu(
                        messageId = messageId,
                        timestamp = message.timestamp,
                        isOutgoing = message.isOutgoing,
                        emoji = emoji,
                        chosenEmoji = message.reactions.filter { r -> r.isChosen }.map { r -> r.emoji }.toSet(),
                        actions = actions,
                        items = shownItems,
                        favoriteSticker = favorite,
                        bookmarked = bookmarks?.isBookmarked(chatId, messageId),
                        pressedMessageId = pressed?.takeIf { it != messageId },
                        inTranslationLanguage = inTranslationLanguage,
                        explainAvailable = explainAvailable,
                        contextFindings = contextFindings,
                        resendable = failedGroup(it.messages, messageId).let { group ->
                            group.isNotEmpty() && group.all { m -> m.sendFailure?.canRetry == true }
                        },
                    ),
                )
            }
        }
    }

    /**
     * Přidá zprávu do záložek, nebo ji z nich odebere. Otisk zprávy (text, odesílatel) se skládá
     * teď, v jazyce aplikace — záložka pak v seznamu nepotřebuje zprávu načítat. Výsledek hlásí
     * snackbar, v konverzaci se nic nezmění.
     */
    private fun toggleBookmark(messageId: Long) {
        val bookmarks = bookmarks ?: return
        val menu = _state.value.messageMenu?.takeIf { it.messageId == messageId } ?: return
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        closeMenus()

        if (menu.bookmarked == true) {
            bookmarks.remove(chatId, messageId)
            _state.update { it.copy(notice = UiText(Res.string.conversation_bookmark_removed)) }
            return
        }
        viewModelScope.launch {
            // Stejný otisk jako řádek přepisu výběru: „[Fotka] popisek", text, nebo popis.
            val preview = transcriptBody(message, resolve = { it.resolveNow() }).orEmpty()
            val sender = if (message.isOutgoing) null else message.senderName ?: _state.value.info?.title
            if (bookmarks.add(chatId, messageId, preview, sender, message.timestamp)) {
                _state.update { it.copy(notice = UiText(Res.string.conversation_bookmark_added)) }
            }
        }
    }

    private data class MenuLoad(
        val emoji: List<String>,
        val actions: MessageActions,
        val favorite: Boolean?,
        val inTranslationLanguage: Boolean,
    )

    /**
     * Přidá samolepku ze zprávy do oblíbených, nebo ji z nich odebere.
     *
     * V konverzaci se tím nic nezmění, takže výsledek hlásí snackbar — jinak by po zavření nabídky
     * nebylo poznat, že se něco stalo.
     */
    private fun toggleFavoriteSticker(messageId: Long) {
        val menu = _state.value.messageMenu?.takeIf { it.messageId == messageId } ?: return
        val favorite = menu.favoriteSticker ?: return
        val sticker = _state.value.messages.firstOrNull { it.id == messageId }?.media as? MessageMedia.Sticker
            ?: return
        closeMenus()

        viewModelScope.launch {
            stickerRepository.setFavorite(sticker.stickerFileId, !favorite)
                .onSuccess {
                    _state.update {
                        it.copy(notice = UiText(if (favorite) Res.string.conversation_favorite_removed else Res.string.conversation_favorite_added))
                    }
                }
                .onFailure { failure ->
                    _state.update { it.copy(error = failure.reported(Res.string.conversation_favorite_failed)) }
                }
        }
    }

    /** Obrazovka oznámení ukázala. */
    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    private fun closeMenus() {
        _state.update { it.copy(messageMenu = null, noticeMenu = null) }
    }

    /**
     * Nabídka nad systémovým oznámením. Nabízí jen smazání, takže když ho server nedovolí
     * (oznámení v kanálu, kde účet není správce), neotevře se vůbec — prázdná nabídka nebo
     * „Smazat", které skončí chybou, by byly horší než nic.
     */
    private fun showNoticeMenu(messageId: Long) {
        viewModelScope.launch {
            val actions = repository.messageActions(chatId, messageId).getOrElse { failure ->
                logger.w(failure) { "Příznaky oznámení $messageId se nepodařilo zjistit." }
                return@launch
            }
            if (!actions.canDeleteForSelf && !actions.canDeleteForAll) return@launch
            _state.update {
                it.copy(noticeMenu = NoticeMenu(messageId, actions.canDeleteForSelf, actions.canDeleteForAll))
            }
        }
    }

    /**
     * Zkopíruje text zprávy do schránky.
     *
     * Kopíruje se text, jak ho vidí uživatel — bez entit. Formátování má smysl v Telegramu,
     * ale do cizí aplikace se stejně přenese jen holý řetězec.
     */
    private fun copyMessage(messageId: Long) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        copyText(message.copyableText())
    }

    /** Zkopíruje text — celou zprávu, nebo úryvek označený v nabídce nad zprávou. */
    private fun copyText(text: String) {
        viewModelScope.launch { clipboard.copy(text) }
        closeMenus()
    }

    /**
     * Připnutí z nabídky.
     *
     * Ve skupině, kanálu i soukromém chatu se nejdřív zeptá — každý z nich má jednu volbu, která
     * mění, koho se to dotkne (upozornit členy / připnout i protějšku). V Uložených zprávách
     * žádná taková volba není, takže se připíná rovnou.
     */
    private fun requestPin(messageId: Long) {
        val info = _state.value.info
        if (info == null || info.isSavedMessages) {
            closeMenus()
            pin(messageId, notify = false, onlyForSelf = false)
            return
        }

        _state.update {
            it.copy(
                messageMenu = null,
                pendingPin = PendingPin(
                    messageId = messageId,
                    scope = when {
                        info.isChannel -> PinScope.Channel
                        info.isGroup -> PinScope.Group
                        else -> PinScope.Private
                    },
                ),
            )
        }
    }

    fun cancelPin() {
        _state.update { it.copy(pendingPin = null) }
    }

    /** [option] je zaškrtávátko dialogu: upozornit členy, nebo v soukromém chatu připnout i protějšku. */
    fun confirmPin(option: Boolean) {
        val pending = _state.value.pendingPin ?: return
        _state.update { it.copy(pendingPin = null) }

        when (pending.scope) {
            // Připnutí v soukromém chatu nikoho neupozorňuje — oznámení o něm by bylo jen šum
            // navíc k servisní zprávě, kterou protějšek stejně uvidí.
            PinScope.Private -> pin(pending.messageId, notify = false, onlyForSelf = !option)
            PinScope.Group, PinScope.Channel -> pin(pending.messageId, notify = option, onlyForSelf = false)
        }
    }

    /**
     * Pruh připnutých se neaktualizuje ručně: `UpdateMessageIsPinned` spustí nové čtení seznamu,
     * stejně jako u odepnutí jinde.
     */
    private fun pin(messageId: Long, notify: Boolean, onlyForSelf: Boolean) {
        viewModelScope.launch {
            repository.pin(chatId, messageId, notify = notify, onlyForSelf = onlyForSelf)
                .onFailure { failure ->
                    _state.update { it.copy(error = failure.reported(Res.string.conversation_pin_failed)) }
                }
        }
    }

    /** Odepnutí z nabídky. Chová se stejně jako křížek v pruhu, včetně „Vrátit". */
    private fun unpinFromMenu(messageId: Long) {
        val message = _state.value.pinned.firstOrNull { it.id == messageId }
            ?: _state.value.messages.firstOrNull { it.id == messageId }
        closeMenus()
        if (message != null) unpin(message)
    }

    /** Otevře potvrzení smazání. Nevratná akce se nedělá na jedno ťuknutí. */
    /**
     * Pošle znovu neodeslanou zprávu (u alba všechny jeho neodeslané zprávy naráz, viz
     * [failedGroup]). Zprávu, kterou znovu poslat nejde, neposílá — řekne, proč neodešla:
     * ťuknutí na „Neodesláno" musí něco udělat, i když opakování nepomůže.
     *
     * Obrazovka se srovná sama z updatů: TDLib starou zprávu smaže a novou ohlásí jako novou
     * ve stavu odesílání, takže se tady nic nepřepisuje.
     */
    private fun resend(messageId: Long) {
        _state.update { it.copy(messageMenu = null) }
        val group = failedGroup(_state.value.messages, messageId)
        if (group.isEmpty()) return
        group.firstOrNull { it.sendFailure?.canRetry != true }?.let { blocked ->
            val reason = blocked.sendFailure?.reason ?: UiText(Res.string.conversation_send_failed_unknown)
            _state.update { it.copy(notice = reason) }
            return
        }
        viewModelScope.launch {
            repository.resend(chatId, group.map { it.id }).onFailure { failure ->
                logger.w(failure) { "Zprávu $messageId se nepodařilo poslat znovu." }
                _state.update { it.copy(notice = failure.userMessage(Res.string.conversation_resend_failed)) }
            }
        }
    }

    private fun requestDelete(messageId: Long) {
        val menu = _state.value.messageMenu ?: return
        _state.update {
            it.copy(
                messageMenu = null,
                pendingDelete = PendingDelete(
                    messageId = messageId,
                    // Nabídka nad albem maže celé album — v bublině je to jedna věc.
                    messageIds = it.albumMemberIds(messageId),
                    canDeleteForAll = menu.actions.canDeleteForAll,
                    canDeleteForSelf = menu.actions.canDeleteForSelf,
                ),
            )
        }
    }

    /**
     * „Nahlásit" z nabídky nad zprávou: průvodce s touhle zprávou (u alba s celým albem, jako
     * u mazání). Server se pak zeptá na důvod — zprávy mu tím odpadne dodávat zvlášť.
     */
    private fun reportMessage(messageId: Long) {
        val ids = _state.value.albumMemberIds(messageId)
        closeMenus()
        reportFlow.start(chatId, messageIds = ids)
    }

    /**
     * „Přidat do kontaktů" na kartě sdíleného kontaktu. Karta tlačítko schová hned po úspěchu —
     * zpráva se kvůli tomu znovu nepřevádí — a snackbar nabídne „Vrátit": přidání jde vzít
     * zpátky a dialog před vratnou akcí jen zdržuje.
     */
    private fun addSharedContact(messageId: Long) {
        viewModelScope.launch {
            repository.addSharedContact(chatId, messageId)
                .onSuccess { userId ->
                    _state.update { it.withContactAddable(userId, addable = false).copy(addedContact = userId) }
                }
                .onFailure { failure ->
                    logger.w(failure) { "Přidání kontaktu ze zprávy $messageId selhalo." }
                    _state.update { it.copy(error = failure.userMessage(Res.string.contact_add_failed)) }
                }
        }
    }

    /**
     * Zavolat zpátky ze zprávy o hovoru: videohovor zase videem, když ho protějšek přijímá.
     * Samotné vytočení (s dotazem na mikrofon a kameru) dělá obrazovka ([ConversationState.callRequest]).
     */
    private fun callBack(messageId: Long) {
        val call = _state.value.messages.firstOrNull { it.id == messageId }?.media as? MessageMedia.Call ?: return
        val info = _state.value.info
        if (info?.callUserId == null) {
            _state.update { it.copy(error = UiText(Res.string.call_unavailable)) }
            return
        }
        _state.update { it.copy(callRequest = CallRequest(video = call.isVideo && info.callSupportsVideo)) }
    }

    fun consumeCallRequest() {
        _state.update { it.copy(callRequest = null) }
    }

    /** „Vrátit" ze snackbaru po přidání kontaktu. */
    fun undoAddContact(userId: Long) {
        viewModelScope.launch {
            chatRepository.removeContact(userId)
                .onSuccess { _state.update { it.withContactAddable(userId, addable = true) } }
                .onFailure { failure ->
                    logger.w(failure) { "Odebrání kontaktu $userId selhalo." }
                    _state.update { it.copy(error = failure.userMessage(Res.string.contact_remove_failed)) }
                }
        }
    }

    /** Obrazovka snackbar k přidanému kontaktu ukázala. */
    fun consumeAddedContact() {
        _state.update { it.copy(addedContact = null) }
    }

    /** Pruh proti spamu: přidat protějšek do kontaktů. Pruh pak zmizí sám (update od serveru). */
    fun addSpamBarContact() {
        viewModelScope.launch {
            chatRepository.addToContacts(chatId)
                .onSuccess { _state.update { it.copy(notice = UiText(Res.string.spam_bar_contact_added)) } }
                .onFailure { failure ->
                    logger.w(failure) { "Přidání do kontaktů z chatu $chatId selhalo." }
                    _state.update { it.copy(error = failure.userMessage(Res.string.spam_bar_failed)) }
                }
        }
    }

    /** „Vrátit mezi chaty" z pruhu u chatu, který server archivoval sám. */
    fun unarchiveFromSpamBar() {
        viewModelScope.launch {
            chatRepository.unarchiveFromSpamBar(chatId)
                .onSuccess {
                    _state.update { it.copy(notice = UiText(Res.string.chats_unarchived)) }
                    _unarchived.value = true
                }
                .onFailure { failure ->
                    logger.w(failure) { "Vrácení chatu $chatId z archivu selhalo." }
                    _state.update { it.copy(error = failure.userMessage(Res.string.spam_bar_failed)) }
                }
        }
    }

    /** Nasdílí vlastní číslo protějšku (pruh [SharePhoneBar], po potvrzení dialogem). */
    fun sharePhoneNumber() {
        viewModelScope.launch {
            chatRepository.sharePhoneNumber(chatId)
                .onSuccess { _state.update { it.copy(notice = UiText(Res.string.chat_bar_phone_shared)) } }
                .onFailure { failure ->
                    logger.w(failure) { "Nasdílení čísla v chatu $chatId selhalo." }
                    _state.update { it.copy(error = failure.userMessage(Res.string.spam_bar_failed)) }
                }
        }
    }

    /** Zavře pruh nad konverzací napořád. */
    fun dismissChatBar() {
        viewModelScope.launch {
            chatRepository.dismissChatBar(chatId).onFailure { failure ->
                logger.w(failure) { "Pruh proti spamu v chatu $chatId nejde zavřít." }
                _state.update { it.copy(error = failure.userMessage(Res.string.spam_bar_failed)) }
            }
        }
    }

    /**
     * Akce z pruhu proti spamu se vším, co si uživatel přibalil. Pořadí je nahlásit →
     * zablokovat → smazat, jako u smazání v seznamu chatů ([cleanUpSpam]): nahlásit jde jen
     * existující chat a po chybě blokování se nesmí smazat nic.
     */
    fun resolveSpamBar(cleanup: SpamBarCleanup) {
        reportFlow.thenAfterReport(cleanup.report, chatId) {
            cleanUpSpam(
                cleanup = SpamCleanup(block = cleanup.block),
                block = { chatRepository.setBlocked(chatId, true) },
                delete = {
                    if (cleanup.delete) {
                        chatRepository.deleteOrLeave(chatId).onSuccess { _chatGone.value = true }
                    } else {
                        Result.success(Unit)
                    }
                },
            ).onSuccess {
                if (cleanup.block && !cleanup.delete) {
                    _state.update { it.copy(notice = UiText(Res.string.chats_blocked)) }
                }
            }.onFailure { failure ->
                logger.w(failure) { "Vyřízení spamu v chatu $chatId selhalo." }
                _state.update { it.copy(error = failure.userMessage(Res.string.spam_bar_failed)) }
            }
        }
    }

    /** Nahlášení celého chatu z nabídky ⋮ v hlavičce, bez mazání. */
    fun reportChat() {
        reportFlow.start(chatId)
    }

    /**
     * Smazání chatu / odchod ze skupiny nebo kanálu z nabídky ⋮ v hlavičce, s volbami ze sheetu
     * smazání — stejné pořadí jako v seznamu chatů ([cleanUpSpam]). Po úspěchu se konverzace
     * zavře ([chatGone]).
     */
    fun deleteOrLeave(cleanup: SpamCleanup) {
        reportFlow.thenAfterReport(cleanup.report, chatId) {
            cleanUpSpam(
                cleanup = cleanup,
                block = { chatRepository.setBlocked(chatId, true) },
                delete = { chatRepository.deleteOrLeave(chatId) },
            )
                .onSuccess { _chatGone.value = true }
                .onFailure { failure -> _state.update { it.copy(error = failure.reported(Res.string.profile_action_failed)) } }
        }
    }

    /**
     * Ukončí tajný chat z nabídky ⋮. Historie zůstane, psát do něj už nepůjde ani protějšku —
     * místo pole se ukáže věta o ukončeném chatu, až to ohlásí `accessUpdates`.
     */
    fun closeSecretChat() {
        viewModelScope.launch {
            chatRepository.closeSecretChat(chatId)
                .onSuccess { _state.update { it.copy(notice = UiText(Res.string.secret_chat_closed)) } }
                .onFailure { failure -> _state.update { it.copy(error = failure.reported(Res.string.secret_chat_close_failed)) } }
        }
    }

    /**
     * „Přeposlat" z nabídky. Obrazovka si o cíl řekne sama (otevře výběr chatu), takže se tu jen
     * zapamatuje, co se přeposílá — u alba celé album, jako u mazání.
     */
    private fun requestForward(messageId: Long) {
        _state.update { it.copy(messageMenu = null, forwardRequest = it.albumMemberIds(messageId)) }
    }

    /**
     * Sheet připomínky; termín ze zprávy ([reminderTimeFromText]) jde nahoru jako první volba.
     * Počítá se od odeslání zprávy a nabízí se, jen když ještě nebyl (a je aspoň za minutu,
     * jako u plánování).
     */
    private fun requestRemind(messageId: Long) {
        val message = _state.value.messages.firstOrNull { it.id == messageId }
        val zone = TimeZone.currentSystemDefault()
        val sentAt = message?.let { Instant.fromEpochSeconds(it.timestamp.toLong()).toLocalDateTime(zone) }
        val fromRules = sentAt?.let { reminderTimeFromText(message.text, it) }
        val suggested = fromRules?.toInstant(zone)?.epochSeconds?.takeIf { scheduleValid(it, Clock.System.now().epochSeconds) }
        _state.update { it.copy(messageMenu = null, remindRequest = RemindRequest(messageId, suggested)) }

        // Model jen tam, kde pravidla nenašla NIC. Termín, který pravidla našla a který už
        // minul, model nespraví — a ptát se ho by jen zdrželo sheet.
        val model = reminderTimeModel ?: return
        if (fromRules != null || sentAt == null) return
        viewModelScope.launch {
            if (!model.available()) return@launch
            updateRemind(messageId) { it.copy(suggesting = true) }
            val at = model.suggest(message.text, sentAt)?.toInstant(zone)?.epochSeconds
                ?.takeIf { scheduleValid(it, Clock.System.now().epochSeconds) }
            updateRemind(messageId) { it.copy(suggesting = false, suggestedAt = at, fromModel = at != null) }
        }
    }

    /** Změna otevřeného sheetu připomínky — jen když je pořád otevřený u téže zprávy. */
    private fun updateRemind(messageId: Long, change: (RemindRequest) -> RemindRequest) = _state.update { state ->
        val open = state.remindRequest?.takeIf { it.messageId == messageId } ?: return@update state
        state.copy(remindRequest = change(open))
    }

    fun closeRemind() {
        _state.update { it.copy(remindRequest = null) }
    }

    /**
     * Připomínka: zpráva (u alba celé album) se naplánovaně přepošle do Uložených zpráv na
     * [at] (sekundy od epochy). Telegram na zprávu doručenou z naplánovaných v Uložených
     * zprávách upozorní — tak „Připomínky" dělají oficiální klienti, a nepotřebuje to vlastní
     * notifikaci ani oprávnění k budíku. Připomínka žije na serveru, takže přijde i na jiném
     * zařízení a přežije přeinstalaci.
     */
    fun remind(messageId: Long, at: Long) {
        val ids = _state.value.albumMemberIds(messageId)
        _state.update { it.copy(remindRequest = null) }
        val saved = chatRepository.savedMessagesChatId.value ?: return
        viewModelScope.launch {
            val result = repository.forward(chatId, ids, saved, ForwardOptions(schedule = MessageSchedule.At(at)))
            result.onFailure { logger.w(it) { "Připomínku se nepodařilo naplánovat." } }
            val notice = if (result.isSuccess) Res.string.conversation_remind_set else Res.string.conversation_remind_failed
            _state.update { it.copy(notice = UiText(notice)) }
        }
    }

    fun consumeForwardRequest() {
        _state.update { it.copy(forwardRequest = null) }
    }

    /**
     * Vstup do režimu výběru z nabídky nad zprávou — s tou zprávou rovnou vybranou.
     *
     * Nabídka se tím zavře: výběr je jiný režim celé konverzace, ne další akce nad jednou zprávou.
     */
    private fun startSelection(messageId: Long) {
        _state.update { it.copy(messageMenu = null, selection = MessageSelection()) }
        toggleSelection(messageId)
    }

    /**
     * Přidá blok do výběru, nebo ho z něj vyndá. Poslední odebraný výběr ukončí — zůstat
     * v režimu s nulou vybraných by byl stav, ve kterém nejde nic dělat a není jasné proč.
     */
    private fun toggleSelection(messageId: Long) {
        val state = _state.value
        val selection = state.selection ?: return
        val key = state.blockKeyOf(messageId) ?: return

        if (key in selection.keys) {
            val keys = selection.keys - key
            _state.update {
                it.copy(
                    selection = if (keys.isEmpty()) {
                        null
                    } else {
                        selection.copy(keys = keys, rights = selection.rights - key, withText = selection.withText - key)
                    },
                )
            }
            return
        }

        if (selection.count >= MAX_SELECTED_MESSAGES) {
            _state.update { it.copy(notice = UiText(Res.string.conversation_selection_limit, listOf(MAX_SELECTED_MESSAGES))) }
            return
        }

        // Popisek alba nemusí být na ťuknuté fotce, takže se text hledá v celém bloku.
        val hasText = state.albumMemberIds(messageId)
            .mapNotNull { id -> state.messages.firstOrNull { it.id == id } }
            .any { it.copyableText().isNotBlank() }
        _state.update {
            it.copy(
                selection = selection.copy(
                    keys = selection.keys + key,
                    withText = if (hasText) selection.withText + key else selection.withText,
                ),
            )
        }

        // Práva se ptáme na každý vybraný blok zvlášť — dopředu pro celou historii by to byl
        // dotaz na každou zprávu, a výběr má strop, takže jich nikdy nebude moc.
        viewModelScope.launch {
            val actions = repository.messageActions(chatId, messageId).getOrElse { MessageActions.Fallback }
            _state.update { state ->
                val current = state.selection ?: return@update state
                // Než odpověď dorazila, mohl uživatel blok z výběru vyndat nebo výběr zavřít.
                if (key !in current.keys) return@update state
                state.copy(selection = current.copy(rights = current.rights + (key to actions)))
            }
        }
    }

    fun closeSelection() {
        _state.update { it.copy(selection = null) }
    }

    /** Texty vybraných zpráv pod sebe, od nejstarší. Zprávy bez textu se vynechají. */
    fun copySelection() {
        val state = _state.value
        val text = state.selectedMessageIds()
            .mapNotNull { id -> state.messages.firstOrNull { it.id == id } }
            .mapNotNull { it.copyableText().takeIf(String::isNotBlank) }
            .joinToString(SELECTION_COPY_SEPARATOR)
        if (text.isBlank()) return

        closeSelection()
        viewModelScope.launch { clipboard.copy(text) }
    }

    /**
     * Vybrané zprávy jako přepis s časem a jménem, od nejstarší — pro poznámky nebo jazykový
     * model (viz [selectionTranscript]). Překládá se teď (`resolveNow`), schránka jazyk neudrží.
     */
    fun copySelectionWithNames() = copySelectionAs { messages, me, title -> selectionTranscript(messages, me, title) }

    /** Vybrané zprávy jako Markdown pro poznámky, i s formátováním (viz [selectionMarkdown]). */
    fun copySelectionAsMarkdown() = copySelectionAs { messages, me, title -> selectionMarkdown(messages, me, title) }

    private fun copySelectionAs(format: suspend (List<ChatMessage>, me: String, chatTitle: String) -> String) {
        val state = _state.value
        val messages = state.selectedMessageIds().mapNotNull { id -> state.messages.firstOrNull { it.id == id } }
        if (messages.isEmpty()) return
        val chatTitle = state.info?.title.orEmpty()

        closeSelection()
        viewModelScope.launch {
            val text = format(messages, UiText(Res.string.summary_me).resolveNow(), chatTitle)
            if (text.isNotBlank()) clipboard.copy(text)
        }
    }

    /** Výběr do přeposlání; cíl si vyžádá obrazovka, stejně jako u jedné zprávy. */
    fun forwardSelection() {
        val ids = _state.value.selectedMessageIds()
        if (ids.isEmpty()) return
        _state.update { it.copy(selection = null, forwardRequest = ids) }
    }

    /**
     * Nahlásit celý výběr moderátorům: tentýž průvodce jako u jedné zprávy, jen s víc zprávami.
     * Server se zeptá na důvod; zavření průvodce nic neodešle.
     */
    fun reportSelection() {
        val ids = _state.value.selectedMessageIds()
        if (ids.isEmpty() || _state.value.selection?.canReport != true) return
        _state.update { it.copy(selection = null) }
        reportFlow.start(chatId, messageIds = ids)
    }

    /** Potvrzení smazání celého výběru. Práva jsou už načtená, takže se dialog zeptá rovnou. */
    fun deleteSelection() {
        val state = _state.value
        val selection = state.selection ?: return
        val ids = state.selectedMessageIds()
        if (ids.isEmpty()) return

        _state.update {
            it.copy(
                selection = null,
                pendingDelete = PendingDelete(
                    messageId = ids.first(),
                    messageIds = ids,
                    canDeleteForSelf = selection.canDeleteForSelf,
                    canDeleteForAll = selection.canDeleteForAll,
                    count = selection.count,
                ),
            )
        }
    }

    fun cancelDelete() {
        _state.update { it.copy(pendingDelete = null) }
    }

    /**
     * Smaže zprávu.
     *
     * Ze seznamu se **neodstraňuje ručně** — TDLib pošle `UpdateDeleteMessages` a ten se už
     * zpracovává. Předběhnout server by znamenalo riskovat, že zpráva zmizí z obrazovky
     * a na serveru zůstane.
     */
    fun confirmDelete(revoke: Boolean) {
        val pending = _state.value.pendingDelete ?: return
        _state.update { it.copy(pendingDelete = null) }

        viewModelScope.launch {
            repository.deleteMessages(chatId, pending.messageIds, revoke)
                .onFailure { failure ->
                    _state.update {
                        it.copy(error = failure.reported(Res.string.conversation_delete_failed))
                    }
                }
        }
    }

    /**
     * Zruší odesílání přílohy.
     *
     * Rozepsanou zprávu stačí smazat: TDLib tím zastaví i nahrávání. Ze seznamu zmizí updatem
     * o smazání jako každá jiná. Hláška jen při selhání — mezitím mohla zpráva doběhnout.
     */
    private fun cancelUpload(messageId: Long) {
        viewModelScope.launch {
            repository.deleteMessages(chatId, listOf(messageId), revoke = true)
                .onFailure { failure ->
                    _state.update { it.copy(error = failure.reported(Res.string.conversation_cancel_send_failed)) }
                }
        }
    }

    private fun applyReactions(messageId: Long, reactions: List<MessageReaction>) {
        _state.update { state ->
            state.copy(
                messages = state.messages.map {
                    if (it.id == messageId) it.copy(reactions = reactions) else it
                },
            )
        }
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

/**
 * Jak často se při psaní opakuje „píšu".
 *
 * Telegram činnost zhasíná zhruba po šesti vteřinách; pět nechává rezervu na zpoždění sítě,
 * aby „píše…" u protějšku při souvislém psaní neproblikávalo.
 */
private val TYPING_REPEAT = 5.seconds

/**
 * Pauza v psaní, po které jde inline dotaz na bota. Kratší než u náhledu odkazu: výsledky se
 * ukazují průběžně a čekání je znát, delší by dotaz na každé písmeno jen oddálilo.
 */
private val INLINE_QUERY_DELAY = 400.milliseconds

/** Pauza v psaní, po které se hledají členové ke zmínce. Hledání je lokální, stačí krátká. */
private val MENTION_QUERY_DELAY = 150.milliseconds

/**
 * Odstup ukládání konceptu od poslední úpravy.
 *
 * Koncept se propisuje na server a do ostatních zařízení, takže ne na každý úhoz. Při odchodu
 * z chatu se čekání přeskočí (`onCleared`), takže delší odstup nic neriskuje.
 */
private const val DRAFT_SAVE_DELAY_MS = 1500L

/**
 * Pauza v psaní, po které se zjišťuje náhled odkazu. Kratší než ukládání konceptu: náhled má
 * naskočit, než uživatel dopíše větu za odkazem, ale ne na každý znak adresy.
 */
private const val LINK_PREVIEW_DELAY_MS = 700L

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

/** Pauza v psaní, po které se v chatu hledá. Každý dotaz jde na server. */
private const val SEARCH_DEBOUNCE_MS = 300L

/** Jak dlouho zůstane zpráva po skoku zvýrazněná. */
private const val HIGHLIGHT_DURATION_MS = 2500L

/**
 * Strop výběru. `deleteMessages` i `forwardMessages` berou v TDLib nejvýš sto zpráv naráz, takže
 * vyšší výběr by skončil chybou až u akce — a projít víc než sto zpráv ťuknutím stejně nikdo nechce.
 */
internal const val MAX_SELECTED_MESSAGES = 100

/** Mezi zprávami ve schránce zůstane prázdný řádek — jinak by dlouhé zprávy splynuly v jednu. */
private const val SELECTION_COPY_SEPARATOR = "\n\n"


private fun UnsupportedBotButton.explanation(): StringResource = when (this) {
    UnsupportedBotButton.SwitchInline -> Res.string.bot_unsupported_switch_inline
    UnsupportedBotButton.Buy -> Res.string.bot_unsupported_buy
    UnsupportedBotButton.Game -> Res.string.bot_unsupported_game
    UnsupportedBotButton.Password -> Res.string.bot_unsupported_password
}

private fun UnsupportedKeyboardButton.explanation(): StringResource = when (this) {
    UnsupportedKeyboardButton.ManagedBot -> Res.string.bot_unsupported_managed_bot
}

/** Jak dlouho se po vynucené odpovědi čeká, než zpráva bota dorazí do seznamu. */
private const val FORCE_REPLY_WAIT = 3_000L

/** Náhled odkazu konceptu, odložený po dobu úpravy zprávy ([ConversationViewModel]). */
private data class LinkPreviewStash(
    val preview: LinkPreview? = null,
    val links: List<String> = emptyList(),
    val options: LinkPreviewOptions = LinkPreviewOptions(),
)

/**
 * Strop pro rozpoznání jazyka při otevírání nabídky nad zprávou. Rychlé je v řádu milisekund,
 * jen první volání po startu načítá model; nabídka na něj čekat nemá.
 */
private const val LANGUAGE_DETECTION_TIMEOUT_MS = 300L
