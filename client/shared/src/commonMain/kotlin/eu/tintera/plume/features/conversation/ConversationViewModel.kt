package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ai.OnDeviceAiStatus
import eu.tintera.plume.core.ai.ScamKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.mapLatest
import eu.tintera.plume.core.telegram.SpeechTranscript
import eu.tintera.plume.core.telegram.transcribeOffer
import eu.tintera.plume.core.telegram.TranscribeOffer
import eu.tintera.plume.core.ai.OnDeviceAi
import kotlin.time.Instant
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
import okio.Path.Companion.toPath
import okio.FileSystem
import eu.tintera.plume.core.audio.RecordedVoice
import eu.tintera.plume.core.telegram.QuickReaction
import eu.tintera.plume.core.telegram.OutgoingVideoNote
import eu.tintera.plume.resources.conversation_bookmark_removed
import eu.tintera.plume.resources.conversation_bookmark_added
import eu.tintera.plume.resources.summary_me
import eu.tintera.plume.resources.conversation_video_note_send_failed
import eu.tintera.plume.resources.conversation_video_note_start_failed
import eu.tintera.plume.resources.conversation_contact_send_failed
import eu.tintera.plume.resources.conversation_contact_no_phone
import eu.tintera.plume.resources.conversation_contact_load_failed
import eu.tintera.plume.core.telegram.PollDraftMedia
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.ReplyQuote
import eu.tintera.plume.core.telegram.ComposedText
import eu.tintera.plume.core.telegram.FormatSpan
import eu.tintera.plume.core.telegram.composedFormatsOf
import eu.tintera.plume.core.telegram.within
import eu.tintera.plume.core.telegram.UnreadMarkKind
import eu.tintera.plume.resources.bot_location_unavailable
import eu.tintera.plume.core.telegram.BotCommandItem
import kotlinx.coroutines.flow.flowOf
import eu.tintera.plume.resources.video_chat_join_elsewhere
import eu.tintera.plume.core.telegram.VideoChatInfo
import eu.tintera.plume.core.telegram.InviteLinkRepository
import eu.tintera.plume.core.url.UrlCleaner
import eu.tintera.plume.core.telegram.PendingJoinRequests
import eu.tintera.plume.core.telegram.BotChatRequest
import eu.tintera.plume.core.telegram.BotMenuApp
import eu.tintera.plume.core.telegram.BotUsersRequest
import eu.tintera.plume.core.telegram.UnreadMarks
import eu.tintera.plume.core.telegram.ChatReplyMarkup
import eu.tintera.plume.core.telegram.PollComposerMode
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
import eu.tintera.plume.core.telegram.GiveawayStatus
import eu.tintera.plume.resources.invite_request_sent
import eu.tintera.plume.resources.conversation_link_failed
import eu.tintera.plume.resources.conversation_link_user_not_found
import eu.tintera.plume.core.telegram.FolderInvitePreview
import eu.tintera.plume.core.telegram.InvitePreview
import eu.tintera.plume.core.telegram.InviteJoinResult
import eu.tintera.plume.core.telegram.AppScreen
import eu.tintera.plume.core.telegram.InternalLink
import eu.tintera.plume.core.telegram.LoginLinkKind
import eu.tintera.plume.core.telegram.platformLanguageCode
import eu.tintera.plume.core.language.LanguageDetector
import eu.tintera.plume.core.telegram.MessageBody
import eu.tintera.plume.core.telegram.NameMention
import eu.tintera.plume.core.telegram.MentionCandidate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.audio.AudioRecorder
import eu.tintera.plume.core.audio.MIN_DURATION_MS
import eu.tintera.plume.core.audio.VoiceKey
import eu.tintera.plume.core.audio.VoicePlayback
import eu.tintera.plume.core.audio.VoicePlayer
import eu.tintera.plume.core.audio.MusicQueue
import eu.tintera.plume.core.audio.NowPlaying
import eu.tintera.plume.core.audio.VoiceRecorder
import eu.tintera.plume.core.camera.CaptureSlot
import eu.tintera.plume.core.camera.VIDEO_NOTE_MAX_DURATION_MS
import eu.tintera.plume.core.camera.VideoNoteCamera
import eu.tintera.plume.core.camera.prepareVideoNote
import eu.tintera.plume.core.audio.VoiceRecording
import eu.tintera.plume.core.audio.VoiceSpeed
import eu.tintera.plume.core.files.FileOpener
import eu.tintera.plume.core.files.MediaSharer
import eu.tintera.plume.core.files.SaveAsLauncher
import eu.tintera.plume.core.share.ShareRequests
import eu.tintera.plume.core.telegram.LinkUserNotFoundException
import eu.tintera.plume.core.share.ShareTarget
import eu.tintera.plume.core.share.SharedContent
import eu.tintera.plume.core.share.ShareTargets
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.settings.ExpressionMode
import eu.tintera.plume.core.settings.GestureHint
import eu.tintera.plume.core.settings.RecordMode
import eu.tintera.plume.core.telegram.ChatActivity
import eu.tintera.plume.core.telegram.ChatInfo
import eu.tintera.plume.core.telegram.ContentAccess
import eu.tintera.plume.core.telegram.diceEmojiFor
import eu.tintera.plume.core.navigation.BotStartRequests
import eu.tintera.plume.core.telegram.BotIntro
import eu.tintera.plume.resources.conversation_bot_start_failed
import eu.tintera.plume.core.telegram.MessageSchedule
import eu.tintera.plume.core.telegram.MessageEffect
import eu.tintera.plume.core.telegram.SendOptions
import eu.tintera.plume.core.telegram.SpeechTrial
import eu.tintera.plume.resources.conversation_scheduled_load_failed
import eu.tintera.plume.resources.conversation_schedule_failed
import eu.tintera.plume.resources.conversation_scheduled_notice
import eu.tintera.plume.core.telegram.WriteAccess
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.LinkPreview
import eu.tintera.plume.core.telegram.LinkPreviewOptions
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ConnectionRepository
import eu.tintera.plume.core.telegram.ConnectionState
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.ThreadConversationRepository
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.MessageActions
import eu.tintera.plume.core.telegram.MessageInfo
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
import eu.tintera.plume.core.telegram.Presence
import eu.tintera.plume.core.telegram.ReactionDetail
import eu.tintera.plume.core.telegram.AttachmentMode
import eu.tintera.plume.core.telegram.SelfDestructType
import eu.tintera.plume.core.telegram.SendState
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
import eu.tintera.plume.resources.article_load_failed
import eu.tintera.plume.resources.folder_invite_added
import eu.tintera.plume.resources.folder_invite_failed
import eu.tintera.plume.resources.folder_invite_joined
import eu.tintera.plume.resources.chat_bar_phone_shared
import eu.tintera.plume.resources.paid_media_unsupported
import eu.tintera.plume.resources.story_live_unsupported
import eu.tintera.plume.resources.story_unavailable
import eu.tintera.plume.resources.transcribe_failed
import eu.tintera.plume.resources.conversation_selection_limit
import eu.tintera.plume.resources.conversation_attachments_limit
import eu.tintera.plume.resources.conversation_content_not_allowed
import eu.tintera.plume.resources.conversation_gif_send_failed
import eu.tintera.plume.resources.conversation_location_send_failed
import eu.tintera.plume.resources.conversation_self_destruct_album
import eu.tintera.plume.resources.profile_mute_failed
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
import eu.tintera.plume.resources.conversation_recording_finish_failed
import eu.tintera.plume.resources.conversation_recording_start_failed
import eu.tintera.plume.resources.conversation_recording_too_short
import eu.tintera.plume.resources.conversation_repin_failed
import eu.tintera.plume.resources.conversation_send_failed
import eu.tintera.plume.resources.conversation_sticker_send_failed
import eu.tintera.plume.resources.contact_add_failed
import eu.tintera.plume.resources.contact_remove_failed
import eu.tintera.plume.resources.conversation_unpin_failed
import eu.tintera.plume.resources.conversation_unread_mark_failed
import eu.tintera.plume.resources.conversation_voice_send_failed
import eu.tintera.plume.resources.document_download_failed
import eu.tintera.plume.resources.document_open_failed
import eu.tintera.plume.resources.media_save_failed
import eu.tintera.plume.resources.media_saved_as
import eu.tintera.plume.resources.media_share_failed
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
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
    /** Varování u zpráv, které vypadají jako podvod (model v zařízení). */
    private val scamWarnings: ScamWarnings? = null,
) : ViewModel() {

    /** Hledání místa pro sponku → Poloha (sheet [PlacePickerHost]). */
    val places = PlaceSearcher(viewModelScope, placeRepository, chatId)

    /**
     * Stav spojení. Samostatný tok, ne součást [ConversationState]: netýká se chatu, mění se
     * nezávisle na něm a do stavu, který se přepisuje s každou zprávou, nepatří.
     */
    val connection: StateFlow<ConnectionState> = connection.state

    private val voiceRecorder = VoiceRecorder(audioRecorder, viewModelScope)

    /** Probíhající nahrávání hlasovky; samostatný tok ze stejného důvodu jako [voicePlayback]. */
    val voiceRecording: StateFlow<VoiceRecording?> = voiceRecorder.state

    /** Zastavená hlasovka k poslechu před odesláním ([stopRecordingForPreview]); `null` = žádná. */
    private val _voicePreview = MutableStateFlow<VoicePreview?>(null)
    val voicePreview: StateFlow<VoicePreview?> = _voicePreview.asStateFlow()

    /** Zastavené kolečko k přehrání před odesláním ([stopRecordingForPreview]); `null` = žádné. */
    private val _videoNotePreview = MutableStateFlow<VideoNotePreview?>(null)
    val videoNotePreview: StateFlow<VideoNotePreview?> = _videoNotePreview.asStateFlow()

    /** Kamera, kterou do ViewModelu připojila obrazovka (viz [CaptureSlot]). */
    private val cameraSlot = CaptureSlot()

    /** Kolečko jde tímtéž [VoiceRecorder] jako hlasovka: čas, pauzy i zahození krátkého. */
    private val videoRecorder = VoiceRecorder(cameraSlot, viewModelScope)

    /** Probíhající nahrávání kolečka; samostatný tok jako [voiceRecording]. */
    val videoRecording: StateFlow<VoiceRecording?> = videoRecorder.state

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

    /**
     * Přehrávání hlasovky. Samostatný tok, ne součást [state]: pozice se mění dvacetkrát za
     * vteřinu a přepočítávat kvůli tomu celý stav konverzace by bylo zbytečné.
     */
    val voicePlayback: StateFlow<VoicePlayback?> = voicePlayer.state

    /** Zvolená rychlost přehrávání; jedna pro celou aplikaci, proto rovnou z přehrávače. */
    val voiceSpeed: StateFlow<VoiceSpeed> = voicePlayer.speed

    /** Hrající skladba (i z jiného chatu) pro mini přehrávač v docku: název, interpret, další. */
    val nowPlaying: StateFlow<NowPlaying?> = musicQueue.nowPlaying

    /** Pauza / přehrát z mini přehrávače v docku — týká se toho, co právě hraje, ať je to cokoli. */
    fun togglePlaying() {
        val playback = voicePlayer.state.value ?: return
        when {
            playback.loading -> Unit
            playback.playing -> voicePlayer.pause()
            else -> voicePlayer.resume()
        }
    }

    /** Další skladba z mini přehrávače; hraje dál fronta hudby, ne tahle konverzace. */
    fun skipToNextTrack() = musicQueue.skipToNext()

    /** Zastavení z mini přehrávače. Hudbu přes frontu, aby po ní nezůstalo systémové ovládání. */
    fun stopPlaying() {
        if (voicePlayer.state.value?.music == true) musicQueue.stop() else voicePlayer.stop()
    }

    /** Premium a zkušební přepisy řeči — rozhodují, jestli se u hlasovky nabídne přepis. */
    val isPremium: StateFlow<Boolean> = chatRepository.isPremium
    val speechTrial: StateFlow<SpeechTrial?> = chatRepository.speechTrial

    /**
     * Všechno, co rozhoduje o přepisu záznamu, v jednom toku: Premium, zkušební přepisy, jestli
     * přepíše telefon, a přepisy z telefonu v tomhle chatu. Jeden tok kvůli `ConversationScreen`,
     * který je v dexu na hraně a každé další `collectAsState` mu přidá registr.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val transcribeContext: StateFlow<TranscribeContext> = combine(
        chatRepository.isPremium,
        chatRepository.speechTrial,
        combine(settings.onDeviceAiEnabled, settings.onDeviceAiVariant) { enabled, variant -> enabled to variant }
            .mapLatest { (enabled, variant) ->
                val ai = onDeviceAi
                enabled && ai != null && runCatching { ai.canTranscribe(variant) }.getOrDefault(false)
            },
        localTranscripts?.state?.map { all -> all.filterKeys { it.first == chatId }.mapKeys { it.key.second } }
            ?: flowOf(emptyMap()),
    ) { premium, trial, onDevice, local -> TranscribeContext(premium, trial, onDevice, local) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TranscribeContext(chatRepository.isPremium.value, chatRepository.speechTrial.value))

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

    /** Běžící otevírání souborů podle `fileId`; druhé ťuknutí to čekání zruší. */
    private val documentJobs = mutableMapOf<Int, Job>()

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

        // Delší kolečko Telegram nepřijme: po minutě nahrávání skončí a kolečko odejde samo,
        // jako v oficiálních klientech. Pozastavené (nabídka, výběr času) se nepočítá.
        viewModelScope.launch {
            videoRecorder.state.collect { recording ->
                if (recording != null && recording.elapsedMs >= VIDEO_NOTE_MAX_DURATION_MS) sendRecording()
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
                    playNextUnlistened(after = finished.messageId)
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
            repository.contentOpenedUpdates(chatId).collect { messageId -> markListened(messageId) }
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
            // Jen kvůli kanálu bez práva publikovat: místo pole je tam tlačítko Ztlumit.
            chatRepository.muted(chatId).collect { muted -> _state.update { it.copy(muted = muted) } }
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
        // Rozpracovaná nahrávka se při odchodu zahazuje — neposílat nic, co uživatel neodeslal.
        voiceRecorder.cancel()
        videoRecorder.cancel()
        discardVoicePreview()
        discardVideoNotePreview()

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
                _state.update { it.copy(notice = UiText(Res.string.bot_text_copied)) }
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
                voicePlayer.play(VoiceKey(chatId, target.id), startMs = startMs) { voicePath(media) }
                if (!target.isOutgoing && !media.isListened) reportListened(target.id)
            }
            is MessageMedia.Audio -> {
                _state.update { it.copy(soundingVideoNote = null) }
                musicQueue.play(chatId, target.id, media, startMs)
            }
            is MessageMedia.VideoNote -> {
                voicePlayer.stop()
                _state.update { it.copy(soundingVideoNote = target.id, soundingVideoNoteFromMs = startMs) }
                if (!target.isOutgoing && !media.isViewed) reportListened(target.id)
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
        val listened = discreetListened.toList()
        discreetListened.clear()
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
        _state.update { it.copy(translation = null, notice = UiText(notice)) }
    }

    /**
     * Odkaz na zprávu do schránky.
     *
     * Sheet se přitom zavře. Jednak je tím skopírování hotová věc — kdo si o odkaz řekl, ten ho
     * má —, jednak by snackbar s potvrzením zůstal schovaný POD sheetem a nebylo by poznat,
     * že se něco stalo. (Android 13+ ukazuje vlastní náhled schránky, iOS a starší Androidy ne.)
     */
    fun copyMessageLink(link: String) {
        viewModelScope.launch {
            clipboard.copy(link)
            _state.update { it.copy(messageInfo = null, notice = UiText(Res.string.message_info_link_copied)) }
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
            is MessageAction.OpenMention -> openMentioned { chatRepository.chatByUsername(action.username) }
            is MessageAction.OpenUser -> openMentioned { chatRepository.privateChat(action.userId) }
            MessageAction.AddContact -> addSharedContact(messageId)
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
            is MessageAction.ToggleArticleAudio -> toggleArticleAudio(messageId, action.index)
            is MessageAction.SeekArticleAudio ->
                voicePlayer.seek(VoiceKey(chatId, messageId, part = action.index), action.fraction)
            is MessageAction.OpenArticleDocument -> openArticleDocument(messageId, action.index)
            MessageAction.OpenDocument -> openDocument(messageId)
            MessageAction.CancelUpload -> cancelUpload(messageId)
            MessageAction.ExplainPaidMedia -> _state.update { it.copy(notice = UiText(Res.string.paid_media_unsupported)) }
            MessageAction.ExplainLiveStory -> _state.update { it.copy(notice = UiText(Res.string.story_live_unsupported)) }
            is MessageAction.OpenStory -> openStory(StoryTarget(action.posterChatId, action.storyId))
            MessageAction.ToggleVoice -> toggleVoice(messageId)
            MessageAction.ToggleVideoNoteSound -> toggleVideoNoteSound(messageId)
            is MessageAction.PlayMediaAt -> playMediaAt(messageId, action.seconds)
            MessageAction.VideoNoteSoundEnded -> _state.update {
                if (it.soundingVideoNote == messageId) it.copy(soundingVideoNote = null) else it
            }
            MessageAction.CycleVoiceSpeed -> voicePlayer.cycleSpeed()
            MessageAction.Transcribe -> transcribe(messageId)
            is MessageAction.SeekVoice -> seekVoice(messageId, action.fraction)
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
            MessageMenuAction.SelectText -> selectText(messageId)
            MessageMenuAction.Pin -> requestPin(messageId)
            MessageMenuAction.Unpin -> unpinFromMenu(messageId)
            MessageMenuAction.Delete -> requestDelete(messageId)
            MessageMenuAction.Select -> startSelection(messageId)
            MessageMenuAction.Report -> reportMessage(messageId)
            MessageAction.ToggleSelection -> toggleSelection(messageId)
            MessageMenuAction.ShareFile -> shareDocument(messageId)
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
            _state.update {
                it.copy(
                    messageMenu = MessageMenu(
                        messageId = messageId,
                        timestamp = message.timestamp,
                        isOutgoing = message.isOutgoing,
                        emoji = emoji,
                        chosenEmoji = message.reactions.filter { r -> r.isChosen }.map { r -> r.emoji }.toSet(),
                        actions = actions,
                        items = items,
                        favoriteSticker = favorite,
                        bookmarked = bookmarks?.isBookmarked(chatId, messageId),
                        pressedMessageId = pressed?.takeIf { it != messageId },
                        inTranslationLanguage = inTranslationLanguage,
                        explainAvailable = explainAvailable,
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

    /** Stáhne soubor a předá ho systému. */
    private fun openDocument(messageId: Long) = withDocument(messageId) { path, document ->
        if (!fileOpener.open(path, document.mimeType)) {
            _state.update { it.copy(notice = UiText(Res.string.document_open_failed)) }
        }
    }

    /** Médium článku podle pořadí; jen z načtené zprávy — co je vidět, je načtené. */
    private fun articleMedia(messageId: Long, index: Int): MessageMedia? =
        _state.value.messages.firstOrNull { it.id == messageId }?.article?.media?.firstOrNull { it.index == index }?.media

    /** Soubor v článku: stejně jako soubor ve zprávě, jen se hledá v médiích článku. */
    private fun openArticleDocument(messageId: Long, index: Int) {
        val document = articleMedia(messageId, index) as? MessageMedia.Document ?: return
        withDocument(document) { path, _ ->
            if (!fileOpener.open(path, document.mimeType)) {
                _state.update { it.copy(notice = UiText(Res.string.document_open_failed)) }
            }
        }
    }

    /**
     * Zvuk nebo hlasovka v článku. Hraje TENTÝŽ přehrávač jako hlasovky (zvuk je v aplikaci
     * jeden); klíč nese pořadí v článku. Skladba z článku nejde do fronty hudby na pozadí —
     * sousedy fronta hledá mezi zprávami, a médium článku zpráva sama není.
     */
    private fun toggleArticleAudio(messageId: Long, index: Int) {
        val fileId = when (val media = articleMedia(messageId, index)) {
            is MessageMedia.VoiceNote -> media.fileId
            is MessageMedia.Audio -> media.fileId
            else -> return
        }
        _state.update { it.copy(soundingVideoNote = null) }
        voicePlayer.toggle(VoiceKey(chatId, messageId, part = index)) { filePath(fileId) }
    }

    /**
     * Stáhne soubor a otevře systémový dialog sdílení.
     *
     * Položka nabídky nabídku zavírá sama: stahování může chvíli trvat a jeho průběh je vidět
     * v řádku souboru, ne v nabídce — a hláška o výsledku by pod závojem zapadla.
     */
    private fun shareDocument(messageId: Long) {
        closeMenus()
        shareDownloaded(messageId)
    }

    private fun shareDownloaded(messageId: Long) = withDocument(messageId) { path, document ->
        if (!sharer.share(path, document.fileName)) {
            _state.update { it.copy(notice = UiText(Res.string.media_share_failed)) }
        }
    }

    /**
     * Stáhne soubor a nabídne systémový výběr umístění („Uložit jako…").
     *
     * Výběr je aktivita systému, takže [launcher] dodá obrazovka; ViewModel ho jen zavolá, až je
     * soubor na disku. Zavřený výběr (`null`) není chyba a hlášku nedostane.
     */
    fun saveDocumentAs(messageId: Long, launcher: SaveAsLauncher) {
        closeMenus()
        saveDownloadedAs(messageId, launcher)
    }

    private fun saveDownloadedAs(messageId: Long, launcher: SaveAsLauncher) = withDocument(messageId) { path, document ->
        launcher.launch(path, document.fileName) { saved ->
            when (saved) {
                true -> _state.update { it.copy(notice = UiText(Res.string.media_saved_as)) }
                false -> _state.update { it.copy(notice = UiText(Res.string.media_save_failed)) }
                null -> Unit
            }
        }
    }

    /**
     * Stáhne soubor zprávy a pak s ním udělá [use] — otevření, sdílení i „Uložit jako…" jdou
     * touž cestou, takže mají stejný průběh v řádku souboru i stejné hlášky při selhání.
     *
     * Stahuje se až na pokyn — soubory v chatu bývají velké a stahovat je dopředu by znamenalo
     * data za všechno, co kdo poslal. Průběh se drží ve stavu podle `fileId`, takže ho ukáže
     * i bublina alba.
     */
    private fun withDocument(
        messageId: Long,
        use: suspend (path: String, document: MessageMedia.Document) -> Unit,
    ) {
        val document = when (val media = _state.value.messages.firstOrNull { it.id == messageId }?.media) {
            is MessageMedia.Document -> media
            is MessageMedia.Audio -> media.asDocument()
            else -> return
        }
        withDocument(document, use)
    }

    private fun withDocument(
        document: MessageMedia.Document,
        use: suspend (path: String, document: MessageMedia.Document) -> Unit,
    ) {

        // Druhé ťuknutí během čekání ho zruší. Bez toho by se z rozbitého stahování (mrtvé spojení,
        // odpověď, která nedorazí) nedalo dostat ven — kolečko by se točilo napořád.
        documentJobs.remove(document.fileId)?.let { running ->
            running.cancel()
            // Zrušit i stahování v TDLib — jinak by soubor tekl dál a potichu se dostáhl.
            viewModelScope.launch { fileRepository.cancelDownload(document.fileId) }
            _state.update { it.copy(documentDownloads = it.documentDownloads - document.fileId) }
            return
        }

        _state.update { it.copy(documentDownloads = it.documentDownloads + (document.fileId to 0f)) }

        documentJobs[document.fileId] = viewModelScope.launch {
            try {
                logger.d { "Otevírám soubor ${document.fileId} (${document.fileName})" }
                // Ťuknutí je výslovný pokyn: stahování se spustí znovu, i kdyby předchozí zůstalo viset.
                fileRepository.retryDownload(document.fileId)

                val states = fileRepository.file(document.fileId)
                    .onEach { state ->
                        if (state is FileState.Downloading) {
                            _state.update { it.copy(documentDownloads = it.documentDownloads + (document.fileId to state.progress)) }
                        }
                    }

                /*
                 * Na ROZBĚH stahování se čeká jen chvíli, na dokončení pak jak dlouho je potřeba.
                 * Když TDLib neodpoví (mrtvé spojení), zůstal by stav `Pending` napořád; jakmile
                 * ale stahování běží, průběh je vidět a velký soubor smí trvat.
                 */
                val started = withTimeoutOrNull(DOWNLOAD_START_TIMEOUT) { states.first { it !is FileState.Pending } }
                val file = started?.let { first ->
                    if (first is FileState.Ready || first is FileState.Failed) first else states.first { it is FileState.Ready || it is FileState.Failed }
                }
                logger.d { "Soubor ${document.fileId} skončil jako $file" }

                when (file) {
                    is FileState.Ready -> use(file.path, document)
                    else -> _state.update { it.copy(error = UiText(Res.string.document_download_failed)) }
                }
            } finally {
                // I při zrušení (odchod z chatu, druhé ťuknutí) musí průběh ze stavu zmizet.
                documentJobs.remove(document.fileId)
                _state.update { it.copy(documentDownloads = it.documentDownloads - document.fileId) }
            }
        }
    }

    /**
     * Pustí nebo pozastaví hlasovku.
     *
     * Soubor se stáhne až teď — dopředu by to byla data za každou hlasovku v historii. Příchozí
     * neposlechnutá se nahlásí jako poslechnutá (`openMessageContent`) a hned se tak i ukáže;
     * potvrzení ze serveru přijde `contentOpenedUpdates`.
     */
    private fun toggleVoice(messageId: Long) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        (message.media as? MessageMedia.Audio)?.let { audio ->
            _state.update { it.copy(soundingVideoNote = null) }
            musicQueue.toggle(chatId, messageId, audio)
            return
        }
        val voice = message.media as? MessageMedia.VoiceNote ?: return

        // Zvuk hraje vždycky jen jeden: hlasovka umlčí kolečko a naopak.
        _state.update { it.copy(soundingVideoNote = null) }
        voicePlayer.toggle(VoiceKey(chatId, messageId)) { voicePath(voice) }
        if (!message.isOutgoing && !voice.isListened) reportListened(messageId)
    }

    /**
     * Pošle hlasovku nebo kolečko k přepisu. Výsledek nese obsah zprávy a dorazí úpravou
     * ([ConversationRepository.editedMessages]) — napřed rozpracovaný, pak hotový.
     */
    private fun transcribe(messageId: Long) {
        val media = _state.value.messages.firstOrNull { it.id == messageId }?.media
        val (fileId, duration) = when (media) {
            is MessageMedia.VoiceNote -> media.fileId to media.durationSeconds
            is MessageMedia.VideoNote -> media.fileId to media.durationSeconds
            else -> return
        }
        val context = transcribeContext.value
        if (transcribeOffer(context.isPremium, context.trial, duration, context.onDevice) == TranscribeOffer.OnDevice) {
            transcribeOnDevice(messageId, fileId)
            return
        }
        viewModelScope.launch {
            repository.recognizeSpeech(chatId, messageId).onFailure { failure ->
                logger.w(failure) { "Přepis zprávy $messageId se nepodařilo spustit." }
                _state.update { it.copy(notice = failure.userMessage(Res.string.transcribe_failed)) }
            }
        }
    }

    /**
     * Přepis v telefonu (`OnDeviceAi.transcribe`): soubor se stáhne, model ho přepíše a výsledek
     * jde do [LocalTranscripts] — zprávu na serveru měnit nejde. Běží v [appScope]: přepis trvá
     * vteřiny až desítky vteřin a odchod z chatu ho nemá zahodit.
     */
    private fun transcribeOnDevice(messageId: Long, fileId: Int) {
        val ai = onDeviceAi ?: return
        val local = localTranscripts ?: return
        if (local.get(chatId, messageId) is SpeechTranscript.Pending) return
        local.set(chatId, messageId, SpeechTranscript.Pending(""))
        appScope.launch {
            try {
                val file = fileRepository.file(fileId).first { it is FileState.Ready || it is FileState.Failed }
                val path = (file as? FileState.Ready)?.path ?: throw IllegalStateException("Soubor $fileId se nestáhl")
                var text = ""
                ai.transcribe(settings.onDeviceAiVariant.value, path, platformLanguageCode).collect { generated ->
                    text = generated.text
                    local.set(chatId, messageId, SpeechTranscript.Pending(text))
                }
                local.set(
                    chatId,
                    messageId,
                    if (text.isBlank()) SpeechTranscript.Failed(SpeechTranscript.Failed.Reason.Other) else SpeechTranscript.Done(text.trim()),
                )
            } catch (e: CancellationException) {
                local.set(chatId, messageId, SpeechTranscript.Failed(SpeechTranscript.Failed.Reason.Other))
                throw e
            } catch (e: Exception) {
                logger.w(e) { "Přepis zprávy $messageId v telefonu se nepovedl." }
                local.set(chatId, messageId, SpeechTranscript.Failed(SpeechTranscript.Failed.Reason.Other))
            }
        }
    }

    /**
     * Pustí video kolečko se zvukem, nebo ho vrátí do tiché smyčky.
     *
     * Kolečko se zvukem je jedno na konverzaci a hrající hlasovka se zastaví. Příchozí
     * nezhlédnuté se nahlásí jako zhlédnuté — protějšek to vidí stejně jako u hlasovky.
     */
    private fun toggleVideoNoteSound(messageId: Long) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        val note = message.media as? MessageMedia.VideoNote ?: return
        if (_state.value.soundingVideoNote == messageId) {
            _state.update { it.copy(soundingVideoNote = null) }
            return
        }
        voicePlayer.stop()
        _state.update { it.copy(soundingVideoNote = messageId, soundingVideoNoteFromMs = 0L) }
        if (!message.isOutgoing && !note.isViewed) reportListened(messageId)
    }

    /**
     * Poslechnuté hlasovky a zhlédnutá kolečka, která diskrétní čtení zatím NENAHLÁSILO —
     * odejdou s přečtením chatu ([readNow]). Odchod z chatu je zahodí: protějšek se o poslechu
     * nedozví a po návratu je hlasovka zase neposlechnutá, jak ji vede server.
     */
    private val discreetListened = LinkedHashSet<Long>()

    /**
     * Označí hlasovku za poslechnutou lokálně i na serveru. Při diskrétním čtení jen lokálně
     * ([discreetListened]): poslech je stejné prozrazení jako přečtení.
     */
    private fun reportListened(messageId: Long) {
        markListened(messageId)
        if (settings.discreetReading.value) {
            discreetListened += messageId
            logger.d { "Diskrétní čtení: poslech zprávy $messageId se nehlásí" }
            return
        }
        viewModelScope.launch {
            repository.openContent(chatId, messageId).onFailure { failure ->
                logger.w(failure) { "Poslechnutí hlasovky nebo kolečka se nepodařilo nahlásit." }
            }
        }
    }

    /**
     * Začne nahrávat hlasovku. Oprávnění k mikrofonu řeší obrazovka dřív, než sem dojde.
     * Hrající hlasovka se zastaví — nahrával by se i její zvuk z reproduktoru.
     */
    fun startRecording() {
        if (!_state.value.content.voiceNotes) {
            _state.update { it.copy(notice = UiText(Res.string.conversation_content_not_allowed)) }
            return
        }
        if (videoRecorder.state.value != null) return
        silencePlayback()
        voiceRecorder.start().onFailure {
            _state.update { it.copy(error = UiText(Res.string.conversation_recording_start_failed)) }
        }
    }

    /**
     * Připojí kameru, kterou drží obrazovka. Odpojením (obrazovka se rozkládá — otočení, odchod)
     * se rozpracované kolečko zahodí: kamera s kompozicí končí a nahrávat by nebylo do čeho.
     */
    fun attachCamera(camera: VideoNoteCamera) {
        cameraSlot.attach(camera) { videoRecorder.cancel() }
    }

    fun detachCamera(camera: VideoNoteCamera) {
        cameraSlot.detach(camera)
    }

    /** Začne nahrávat kolečko. Oprávnění ke kameře a mikrofonu řeší obrazovka, jako u hlasovky. */
    fun startVideoRecording() {
        if (!_state.value.content.videoNotes) {
            _state.update { it.copy(notice = UiText(Res.string.conversation_content_not_allowed)) }
            return
        }
        if (voiceRecorder.state.value != null) return
        silencePlayback()
        videoRecorder.start().onFailure {
            _state.update { it.copy(error = UiText(Res.string.conversation_video_note_start_failed)) }
        }
    }

    /** Přepne, co nahraje podržení tlačítka. Zápis jde přes [appScope], jako ostatní volby. */
    fun setRecordMode(mode: RecordMode) {
        appScope.launch { settings.setRecordMode(mode) }
    }

    /** Hrající hlasovka i kolečko se zvukem by se nahrály z reproduktoru. */
    private fun silencePlayback() {
        voicePlayer.stop()
        _state.update { it.copy(soundingVideoNote = null) }
    }

    fun cancelRecording() {
        voiceRecorder.cancel()
        videoRecorder.cancel()
    }

    /**
     * Pozastaví nahrávání po dobu nabídky odeslání a výběru času. Jinak by se do hlasovky
     * nahrálo, jak uživatel vybírá — a šustění na konci by se z ní nedalo dostat.
     */
    fun pauseRecording() {
        voiceRecorder.pause()
        videoRecorder.pause()
    }

    /** Nabídka se zavřela bez volby — nahrává se dál. */
    fun resumeRecording() {
        voiceRecorder.resume()
        videoRecorder.resume()
    }

    /**
     * Ukončí nahrávání a hlasovku nebo kolečko odešle — jako samolepka: bez letu ze vstupního
     * pole, rozepsaný text zůstává, citace se spotřebuje. Příliš krátká nahrávka se zahodí
     * s vysvětlením.
     */
    fun sendRecording(options: SendOptions = SendOptions()) {
        readAfterOwnMessage()
        when {
            videoRecorder.state.value != null -> sendVideoRecording(options)
            voiceRecorder.state.value != null -> sendVoiceRecording(options)
            _voicePreview.value != null -> sendVoicePreview(options)
            _videoNotePreview.value != null -> sendVideoNotePreview(options)
            // Dvojí ťuknutí na odeslání: druhé by našlo nahrávání už ukončené a hlásilo nesmysl.
            else -> Unit
        }
    }

    private fun sendVoiceRecording(options: SendOptions) {
        viewModelScope.launch {
            val tooShort = (voiceRecorder.state.value?.elapsedMs ?: 0L) < MIN_DURATION_MS
            val recorded = voiceRecorder.finish()
            if (recorded == null) {
                reportRecordingLost(tooShort)
                return@launch
            }
            sendVoice(recorded, options)
        }
    }

    private suspend fun sendVoice(recorded: RecordedVoice, options: SendOptions) {
        sendMedia(Res.string.conversation_voice_send_failed, scheduled = options.schedule != null) { replyTo, quote ->
            repository.sendVoiceNote(
                chatId, recorded.path, recorded.durationSeconds, recorded.waveform, replyTo, quote, options,
            )
        }
    }

    /**
     * Zastaví zamčené nahrávání a nechá hlasovku k poslechu, místo aby ji rovnou odeslal.
     * Odejde až tlačítkem odeslání ([sendRecording]), koš ji zahodí ([discardVoicePreview]).
     */
    fun stopRecordingForPreview() {
        if (videoRecorder.state.value != null) {
            stopVideoRecordingForPreview()
            return
        }
        if (voiceRecorder.state.value == null || _voicePreview.value != null) return
        val tooShort = (voiceRecorder.state.value?.elapsedMs ?: 0L) < MIN_DURATION_MS
        // Mezistav hned: dokončení (na iOS kódování) chvíli trvá a karta mezitím nemá ukázat pole.
        val finishing = VoicePreview(voice = null)
        _voicePreview.value = finishing
        viewModelScope.launch {
            val recorded = voiceRecorder.finish()
            // Koš během dokončování: náhled už nikdo nechce, soubor pryč.
            if (_voicePreview.value !== finishing) {
                recorded?.let { deleteQuietly(it.path) }
                return@launch
            }
            if (recorded == null) {
                _voicePreview.value = null
                reportRecordingLost(tooShort)
                return@launch
            }
            _voicePreview.value = VoicePreview(recorded, canContinue = voiceRecorder.canContinue(recorded))
        }
    }

    /** Pustí nebo pozastaví náhled. Hrající hlasovka v konverzaci se tím zastaví, jako vždy. */
    fun toggleVoicePreview() {
        val voice = _voicePreview.value?.voice ?: return
        voicePlayer.toggle(voicePreviewKey(chatId)) { voice.path }
    }

    fun seekVoicePreview(fraction: Float) {
        voicePlayer.seek(voicePreviewKey(chatId), fraction)
    }

    /**
     * Nahrávat dál po poslechu náhledu: nové nahrávání naváže na hotovou hlasovku (čas běží od
     * jejího konce) a po ■ nebo odeslání se oba úseky spojí do jedné ([VoiceRecorder.start]).
     * Nahrávání je rovnou zamčené — spouští ho ťuknutí, ne podržení; zamčení drží obrazovka.
     */
    fun continueVoicePreview() {
        val preview = _voicePreview.value?.takeIf { it.canContinue } ?: return
        val voice = preview.voice ?: return
        if (videoRecorder.state.value != null) return
        silencePlayback()
        voiceRecorder.start(continuing = voice)
            .onSuccess { _voicePreview.value = null }
            .onFailure { _state.update { it.copy(error = UiText(Res.string.conversation_recording_start_failed)) } }
    }

    /** Koš u náhledu, i odchod z chatu: nic, co uživatel neodeslal, neodejde, a soubor se smaže. */
    fun discardVoicePreview() {
        val preview = _voicePreview.value ?: return
        _voicePreview.value = null
        stopVoicePreviewPlayback()
        preview.voice?.path?.let(::deleteQuietly)
    }

    private fun deleteQuietly(path: String) {
        runCatching { FileSystem.SYSTEM.delete(path.toPath(), mustExist = false) }
            .onFailure { failure -> logger.w(failure) { "Zahozený náhled nahrávky nejde smazat." } }
    }

    private fun sendVoicePreview(options: SendOptions) {
        val voice = _voicePreview.value?.voice ?: return
        _voicePreview.value = null
        stopVoicePreviewPlayback()
        viewModelScope.launch { sendVoice(voice, options) }
    }

    /** Hraje-li právě náhled, zastaví ho; cizí hlasovky ani hudby se to netýká. */
    private fun stopVoicePreviewPlayback() {
        if (voicePlayer.state.value?.key == voicePreviewKey(chatId)) voicePlayer.stop()
    }

    /**
     * Kolečko se po skončení ještě připraví (délka, strana, první snímek) — až tady, ne během
     * nahrávání: soubor je hotový teprve teď a na iOS se navíc ořezává do čtverce.
     */
    private fun sendVideoRecording(options: SendOptions) {
        viewModelScope.launch {
            val tooShort = (videoRecorder.state.value?.elapsedMs ?: 0L) < MIN_DURATION_MS
            val recorded = videoRecorder.finish()
            if (recorded == null) {
                reportRecordingLost(tooShort)
                return@launch
            }
            sendMedia(Res.string.conversation_video_note_send_failed, scheduled = options.schedule != null) { replyTo, quote ->
                runCatching { prepareVideoNote(recorded.path) }
                    .onFailure { failure -> logger.w(failure) { "Kolečko nejde připravit k odeslání." } }
                    .fold(
                        onSuccess = { note -> repository.sendVideoNote(chatId, note, replyTo, quote, options) },
                        onFailure = { failure -> Result.failure(failure) },
                    )
            }
        }
    }

    /**
     * Zastaví zamčené kolečko a nechá ho k přehrání, místo aby ho rovnou odeslalo — jako
     * [stopRecordingForPreview] u hlasovky. Připraví se hned (délka, strana, první snímek):
     * náhled přehrává hotový soubor a odeslání pak nemá na co čekat.
     */
    private fun stopVideoRecordingForPreview() {
        if (_videoNotePreview.value != null) return
        val tooShort = (videoRecorder.state.value?.elapsedMs ?: 0L) < MIN_DURATION_MS
        // Mezistav hned: dokončení (zmenšení na Androidu, ořez na iOS) trvá vteřiny.
        val finishing = VideoNotePreview(note = null)
        _videoNotePreview.value = finishing
        viewModelScope.launch {
            val recorded = videoRecorder.finish()
            val note = recorded?.let {
                runCatching { prepareVideoNote(it.path) }
                    .onFailure { failure -> logger.w(failure) { "Kolečko nejde připravit k náhledu." } }
                    .getOrNull()
            }
            // Koš během dokončování: náhled už nikdo nechce, soubory pryč.
            if (_videoNotePreview.value !== finishing) {
                note?.let(::deleteVideoNote) ?: recorded?.let { deleteQuietly(it.path) }
                return@launch
            }
            if (note == null) {
                _videoNotePreview.value = null
                recorded?.let { deleteQuietly(it.path) }
                reportRecordingLost(tooShort && recorded == null)
                return@launch
            }
            // Kolečko těsně pod minutovým stropem: navázaný úsek by po zlomku vteřiny odešel sám.
            val roomLeft = recorded.durationMs < VIDEO_NOTE_MAX_DURATION_MS - MIN_DURATION_MS
            _videoNotePreview.value = VideoNotePreview(
                note,
                recorded,
                canContinue = roomLeft && videoRecorder.canContinue(recorded),
            )
        }
    }

    /**
     * Nahrávat dál po přehrání náhledu kolečka — jako [continueVoicePreview]: kamera se znovu
     * otevře, čas běží od konce náhledu a po ■ nebo odeslání se obě kolečka spojí do jednoho
     * ([eu.tintera.plume.core.camera.VideoNoteCamera.join]). Náhledový snímek patří starému
     * souboru, takže jde pryč hned; nový se přečte z výsledku.
     */
    fun continueVideoNotePreview() {
        val preview = _videoNotePreview.value?.takeIf { it.canContinue } ?: return
        val recorded = preview.recorded ?: return
        if (voiceRecorder.state.value != null) return
        silencePlayback()
        videoRecorder.start(continuing = recorded)
            .onSuccess {
                _videoNotePreview.value = null
                preview.note?.thumbnail?.path?.let(::deleteQuietly)
            }
            .onFailure { _state.update { it.copy(error = UiText(Res.string.conversation_video_note_start_failed)) } }
    }

    /** Koš u náhledu kolečka, i odchod z chatu: nic neodejde a soubory se smažou. */
    fun discardVideoNotePreview() {
        val preview = _videoNotePreview.value ?: return
        _videoNotePreview.value = null
        preview.note?.let(::deleteVideoNote)
    }

    private fun deleteVideoNote(note: OutgoingVideoNote) {
        deleteQuietly(note.path)
        note.thumbnail?.path?.let(::deleteQuietly)
    }

    private fun sendVideoNotePreview(options: SendOptions) {
        val note = _videoNotePreview.value?.note ?: return
        _videoNotePreview.value = null
        viewModelScope.launch {
            sendMedia(Res.string.conversation_video_note_send_failed, scheduled = options.schedule != null) { replyTo, quote ->
                repository.sendVideoNote(chatId, note, replyTo, quote, options)
            }
        }
    }

    private fun reportRecordingLost(tooShort: Boolean) {
        _state.update {
            if (tooShort) it.copy(notice = UiText(Res.string.conversation_recording_too_short))
            else it.copy(error = UiText(Res.string.conversation_recording_finish_failed))
        }
    }

    private fun seekVoice(messageId: Long, fraction: Float) {
        voicePlayer.seek(VoiceKey(chatId, messageId), fraction)
    }

    /**
     * Po dohrání pustí NÁSLEDUJÍCÍ hlasovku, a jen když je příchozí a neposlechnutá.
     *
     * Řetězí se tím poslech dávky hlasovek, které někdo poslal za sebou — to je ten případ, kvůli
     * kterému funkce existuje. Přeskakovat k neposlechnuté někde dál v historii by naopak
     * překvapilo: uživatel si pustil jednu starou hlasovku a najednou mu hraje něco úplně jiného.
     * Proto se bere jen ta nejbližší novější, a když poslechnutá je, řetěz končí.
     *
     * Vlastní odchozí hlasovku nikdo „neposlechl" v tomhle smyslu, takže na ní řetěz taky končí.
     */
    private fun playNextUnlistened(after: Long) {
        // Skladby řetězí MusicQueue (hrají i bez konverzace); hlasovky se hudbou neřetězí.
        val finished = _state.value.messages.firstOrNull { it.id == after }
        if (finished?.media is MessageMedia.Audio) return
        val next = _state.value.messages
            .filter { it.id > after && it.media is MessageMedia.VoiceNote }
            .minByOrNull { it.id }
            ?: return
        val voice = next.media as? MessageMedia.VoiceNote ?: return
        if (next.isOutgoing || voice.isListened) return

        voicePlayer.play(VoiceKey(chatId, next.id)) { voicePath(voice) }
        reportListened(next.id)
    }

    /** Počká na stažený soubor hlasovky; stahování začne až tímhle odběrem. */
    private suspend fun voicePath(voice: MessageMedia.VoiceNote): String = filePath(voice.fileId)

    /** Počká na stažený soubor; stahování začne až tímhle odběrem. */
    private suspend fun filePath(fileId: Int): String =
        when (val file = fileRepository.file(fileId).first { it is FileState.Ready || it is FileState.Failed }) {
            is FileState.Ready -> file.path
            is FileState.Failed -> error(file.message)
            else -> error("Nedosažitelné")
        }

    private fun markListened(messageId: Long) {
        _state.update { state ->
            state.copy(
                messages = state.messages.map { message ->
                    if (message.id != messageId) return@map message
                    when (val media = message.media) {
                        is MessageMedia.VoiceNote ->
                            if (media.isListened) message else message.copy(media = media.copy(isListened = true))
                        is MessageMedia.VideoNote ->
                            if (media.isViewed) message else message.copy(media = media.copy(isViewed = true))
                        else -> message
                    }
                },
            )
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
 * Sloučí novou dávku do stávajícího seznamu.
 *
 * Deduplikuje podle id (překryv stránek i editace přijdou jako tatáž zpráva) a udrží řazení
 * od nejnovější. Novější verze téže zprávy přebíjí starší.
 */
private fun merge(current: List<ChatMessage>, incoming: List<ChatMessage>): List<ChatMessage> {
    if (incoming.isEmpty()) return current

    val byId = LinkedHashMap<Long, ChatMessage>(current.size + incoming.size)
    current.forEach { byId[it.id] = it }
    incoming.forEach { byId[it.id] = it }

    return byId.values.sortedByDescending { it.id }
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
 * Text na cestě ze vstupního pole do bubliny.
 *
 * Existuje jen po dobu animace. [itemKey] je `null`, dokud TDLib nevrátí rozepsanou zprávu —
 * do té doby se text kreslí u vstupního pole, pak ho převezme bublina.
 *
 * Míří na **klíč položky**, ne na id zprávy. Potvrzení ze serveru často dorazí dřív, než let
 * doběhne, a zprávě změní id; klíč zůstává (viz [ConversationState.itemKeys]).
 *
 * [id] rozlišuje jednotlivé lety: bez něj by dvě rychle po sobě odeslané zprávy sdílely jeden
 * klíč a animace by se popraly.
 */
data class MessageFlight(
    val id: Long,
    val text: String,
    val itemKey: Long?,
) {
    /** Klíč sdíleného prvku. Musí být na obou stranách stejný, jinak se prvky nespárují. */
    val sharedKey: String get() = "message-flight-$id"
}

/**
 * Naváže čekající let na právě příchozí zprávu, pokud je to ta odesílaná.
 *
 * Pozná se podle textu a stavu `Sending` — id zatím neznáme, `send` ho ještě nevrátil. Zpráva
 * odeslaná z jiného zařízení v tomhle stavu nepřijde, takže se nespáruje omylem.
 */
private fun MessageFlight?.landingOn(message: ChatMessage): MessageFlight? = when {
    this == null || itemKey != null -> this
    message.isOutgoing && message.sendState == SendState.Sending && message.text == text ->
        copy(itemKey = message.id)
    else -> this
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

/** Členové napovídaní ke zmínce, a ke kterému dotazu patří. */
data class MentionSuggestions(val query: String, val candidates: List<MentionCandidate>)

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

/**
 * Text, který má obrazovka vložit do vstupního pole.
 *
 * [onlyIfEmpty] u konceptu z TDLib: než dorazí, mohl uživatel začít psát a přepsat mu to by bylo
 * horší než o koncept přijít. Úprava zprávy a návrat z ní pole naopak přepisují vždycky.
 */
data class InputFill(
    val text: String,
    val onlyIfEmpty: Boolean,
    /** Ruční formátování [text] (koncept, upravovaná zpráva). */
    val formats: List<FormatSpan> = emptyList(),
)

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

/** Kolik stránek nejvýš dotáhne [ConversationViewModel.loadForSummary]. */
private const val MAX_SUMMARY_PAGES = 4

/** Pauza v psaní, po které se v chatu hledá. Každý dotaz jde na server. */
private const val SEARCH_DEBOUNCE_MS = 300L

/** Jak dlouho zůstane zpráva po skoku zvýrazněná. */
private const val HIGHLIGHT_DURATION_MS = 2500L

/** Kam na obrazovce má zpráva po skoku dosednout. */
enum class ScrollAnchor {
    /** K horní hraně — čte se od ní dolů. Skok na první nepřečtenou. */
    Top,

    /** Na střed — kolem je vidět, na co zpráva navazuje. Skok na citaci. */
    Center,

    /** Úplně dolů (index 0). Návrat z okna v historii k nejnovějším zprávám. */
    Bottom,
}

/** Požadavek na odrolování ke zprávě. Obrazovka ho po provedení vynuluje. */
data class ScrollTarget(
    val messageId: Long,
    val anchor: ScrollAnchor,
    /** Dojet k ní, ne skočit — jen když zpráva v seznamu už byla (viz `jumpToMessage`). */
    val animate: Boolean = false,
    /**
     * Je starší než to, co bylo v seznamu? Vyplněné jen u zprávy, která načtená nebyla — u načtené
     * to obrazovka pozná sama z pozice v seznamu.
     */
    val older: Boolean? = null,
)

/** Otevřený výběr člena: dotaz a nalezení členové. */
data class MemberPicker(val query: String = "", val members: List<SenderInfo> = emptyList(), val loading: Boolean = true)

/** Chat k otevření nad konverzací; s [messageId] se v něm skočí na zprávu. */
data class ChatTarget(
    val chatId: Long,
    val messageId: Long? = null,
    /** Vlákno komentářů v [chatId] (kořenová zpráva); `null` = celý chat. */
    val threadId: Long? = null,
    /** Téma skupiny s tématy; `null` = celý chat. */
    val topicId: Int? = null,
    /**
     * Skupina s tématy bez určeného tématu ani zprávy: otevře se seznam témat, ne konverzace —
     * stejně jako ze seznamu chatů. Zjišťuje ViewModel ([ConversationViewModel.openTarget]).
     */
    val forum: Boolean = false,
)

/** Video k otevření v prohlížeči od [seconds] (čas v médiu). */
data class MediaAt(val messageId: Long, val seconds: Int)

/** Otevřený náhled pozvánky; [joining] = přidávání běží a tlačítko je neaktivní. */
data class InviteSheetState(val link: String, val preview: InvitePreview, val joining: Boolean = false)

/**
 * Náhled sdílené složky. Vybrané jsou zpočátku všechny chaty, ke kterým se dá připojit — tak to
 * dělají oficiální klienti; kdo nechce do některého, odškrtne ho.
 */
data class FolderInviteSheetState(
    val link: String,
    val preview: FolderInvitePreview,
    val selected: Set<Long> = preview.missingChats.mapTo(LinkedHashSet()) { it.id },
    val adding: Boolean = false,
) {
    /**
     * Novou složku jde přidat i bez připojení k chatům, když v některých už uživatel je (složka
     * nebude prázdná). Do složky, kterou už má, se přidává jen to, co vybral.
     */
    val canAdd: Boolean
        get() = selected.isNotEmpty() || (preview.folderId == 0 && preview.alreadyJoined > 0)
}

/** Skok na zprávu, která se teprve načítá; obrazovka mezitím odjíždí směrem k ní. */
data class PendingJump(val messageId: Long, val older: Boolean)

/**
 * Otevřené informace o zprávě.
 *
 * [info] je `null`, dokud se načítají — sheet se otevře hned s indikátorem, místo aby vyskočil
 * prázdný a obsah do něj naskákal. Kus dat je v [message], protože ho konverzace už má: text,
 * odesílatel i stav doručení se kvůli sheetu nemusí ptát serveru znovu.
 */
data class MessageInfoSheet(
    val message: ChatMessage,
    val info: MessageInfo? = null,
)

/** Připomínka zprávy [messageId]; [suggestedAt] = termín ze zprávy (sekundy od epochy), `null` = žádný. */
/** Co rozhoduje o přepisu záznamu — viz [ConversationViewModel.transcribeContext]. */
data class TranscribeContext(
    val isPremium: Boolean = false,
    val trial: SpeechTrial? = null,
    /** Umí přepis model v telefonu (zapnutá AI a připravený model se zvukem). */
    val onDevice: Boolean = false,
    /** Přepisy z telefonu v tomhle chatu podle id zprávy. */
    val local: Map<Long, SpeechTranscript> = emptyMap(),
)

data class RemindRequest(
    val messageId: Long,
    val suggestedAt: Long?,
    /** Termín hledá model v zařízení (pravidla nic nenašla); sheet ukáže „Hledám termín…". */
    val suggesting: Boolean = false,
    /** [suggestedAt] je odhad modelu, ne pravidel — sheet to řekne ikonou. */
    val fromModel: Boolean = false,
)

/**
 * Otevřený překlad zprávy do jazyka [languageCode]. [body] je `null`, dokud překlad nedorazí;
 * [failed] = server ho nevydal.
 */
data class TranslationSheet(
    val message: ChatMessage,
    val languageCode: String,
    val body: MessageBody? = null,
    /** Proč se to nepovedlo; `null`, dokud se čeká nebo když se to povedlo. */
    val error: UiText? = null,
    /** Shrnutí od serveru místo překladu — tentýž sheet, jiné volání a jiné texty. */
    val summary: Boolean = false,
    /**
     * Jestli smí výsledek do schránky. Překlad se u chráněného obsahu nenabízí vůbec, shrnutí
     * ano (dostupnost řídí server) — jen bez kopírování, chráněný obsah ven nesmí ani převyprávěný.
     */
    val canCopy: Boolean = true,
    /** Překlad v telefonu, ne na serveru; `null` = telefon to neumí (přepínač se nekreslí). */
    val onDevice: Boolean? = null,
    /** Pro překlad v telefonu chybí jazykové balíčky; čeká se, až stažení uživatel potvrdí. */
    val needsDownload: Boolean = false,
    /** Stahují se jazykové balíčky (trvá to déle než překlad, sheet to říká). */
    val downloading: Boolean = false,
)

/**
 * Otevřený detail reakcí: kdo a čím reagoval.
 *
 * Načítá se až při otevření — čip u zprávy nese jen emoji a počet, takže jména a profilovky
 * nikdo jiný nepotřebuje. [loading] drží sheet otevřený s indikátorem, místo aby vyskočil
 * prázdný a obsah do něj naskákal.
 */
data class ReactionDetails(
    val messageId: Long,
    val loading: Boolean = true,
    val items: List<ReactionDetail> = emptyList(),
    /** Reakce zprávy s počty, jak byly při otevření — podle nich jsou tlačítka filtru. */
    val counts: List<MessageReaction> = emptyList(),
    /** Vybraný filtr (klíč reakce); `null` = všechny. */
    val filter: String? = null,
)

/**
 * Otevřená nabídka nad zprávou.
 *
 * Jsou v ní dvě různé věci: **rychlé reakce** (pár emoji na jedno ťuknutí) a **akce** nad zprávou.
 * Proto se kreslí jako dva samostatné kusy, ne jako jeden seznam — reagovat a odpovědět nejsou
 * varianty téhož.
 *
 * Nese jen data, **žádné souřadnice**. Kde přesně bublina na obrazovce leží, ví layout, ne
 * ViewModel; ten by se tím navázal na rozvržení, které se mění s velikostí okna.
 */
data class MessageMenu(
    val messageId: Long,
    /**
     * Kdy zpráva přišla. Čas se jinak dá přečíst jen tahem doleva a to je gesto, ke kterému se
     * čtečka obrazovky nedostane — v nabídce proto stojí jako první řádek.
     */
    val timestamp: Int,
    /** Podle strany bubliny se nabídka zarovná, aby vyrůstala od zprávy, ne od kraje obrazovky. */
    val isOutgoing: Boolean,
    /** Nabídnutá emoji. Prázdné je běžný stav (Uložené zprávy), ne chyba. */
    val emoji: List<String>,
    /** Emoji, která uživatel u téhle zprávy už dal — v rychlém řádku se zvýrazní. */
    val chosenEmoji: Set<String>,
    /** Co server dovolí; načítá se až při otevření nabídky. */
    val actions: MessageActions,
    /**
     * Kandidáti do nabídky od bloku — ten ví, co zpráva JE (holý text, samolepka, připnutá…).
     * Nabídka z nich ukáže ty, které server dovolí ([MessageMenuAction.presentation]).
     */
    val items: List<MessageMenuAction>,
    /**
     * Je samolepka zprávy v oblíbených? `null`, když zpráva samolepka není — pak se položka
     * nenabízí vůbec.
     */
    val favoriteSticker: Boolean?,
    /** Je zpráva v záložkách (`MessageBookmarks`)? `null` = záložky nejsou k dispozici, položka se nenabízí. */
    val bookmarked: Boolean? = null,
    /**
     * Zpráva pod prstem, když se liší od [messageId]: fotka uprostřed alba. Blok alba patří zprávě
     * s popiskem (nebo nejstarší), takže bez tohohle odpověď citovala tu, ne fotku, na kterou
     * uživatel podržel. Ostatní položky (reakce, přeposlání, smazání) jdou dál na celý blok.
     */
    val pressedMessageId: Long? = null,
    /**
     * Text zprávy je už v jazyce překladu (rozpoznáno v zařízení, [alreadyInLanguage]) —
     * „Přeložit" se nenabízí. Nerozpoznaný jazyk je `false`: položka navíc nevadí, chybějící ano.
     */
    val inTranslationLanguage: Boolean = false,
    /** Umí telefon vysvětlit zprávu modelem (zapnutá AI, model zařízení podporuje)? */
    val explainAvailable: Boolean = false,
) {
    /** Do rychlého řádku se vejde jen pár; zbytek je za „+". */
    val quickEmoji: List<String> get() = emoji.take(MAX_QUICK_REACTIONS)

    /** „+" má smysl jen tehdy, když za ním něco navíc je. */
    val hasMoreEmoji: Boolean get() = emoji.size > MAX_QUICK_REACTIONS
}

/**
 * Zpráva čekající na potvrzení smazání.
 *
 * Drží se **zvlášť od [MessageMenu]**: mezi zavřením nabídky a potvrzením nesmí zůstat viset
 * obojí. Stejný důvod jako u `target`/`pendingDelete` v akcích nad chatem.
 */
/** Nabídka nad systémovým oznámením; otevírá se jen s právem ho smazat. */
data class NoticeMenu(
    val messageId: Long,
    val canDeleteForSelf: Boolean,
    val canDeleteForAll: Boolean,
)

data class PendingDelete(
    val messageId: Long,
    /** Co se opravdu smaže: zpráva sama, u alba všechny jeho načtené zprávy. */
    val messageIds: List<Long> = listOf(messageId),
    val canDeleteForSelf: Boolean,
    val canDeleteForAll: Boolean,
    /**
     * Kolik zpráv to je z pohledu uživatele — u alba jedna, z výběru tolik, kolik jich vybral.
     * Není to `messageIds.size`: album je v bublině jedna věc, i když je to pět zpráv.
     */
    val count: Int = 1,
)

/** Na co míří „Odpovědět" z nabídky: na fotku pod prstem, jinak na zprávu bloku. */
internal val MessageMenu.replyTarget: Long get() = pressedMessageId ?: messageId

/**
 * Režim výběru víc zpráv; `null` = vypnutý.
 *
 * Vybírají se **bloky seznamu** ([keys], u alba klíč celého alba), ne jednotlivé zprávy —
 * v konverzaci je album jedna věc a mazat nebo přeposílat půlku alba nedává smysl. Na id zpráv
 * se klíče rozbalí až při akci (`albumMemberIds`), stejně jako u nabídky nad jednou zprávou.
 *
 * Práva od serveru se načítají pro každý vybraný blok zvlášť ([rights]) — z nich se teprve
 * poskládá, co smí výběr JAKO CELEK. Dokud nedorazí všechna, akce se nenabízejí: tlačítko,
 * které se po ťuknutí zeptá serveru a pak zhasne, je horší než tlačítko, které chvíli není.
 */
data class MessageSelection(
    val keys: Set<Long> = emptySet(),
    /** Práva vybraného bloku; klíč tu chybí, dokud odpověď nedorazí. */
    val rights: Map<Long, MessageActions> = emptyMap(),
    /** Vybrané bloky, ze kterých je co zkopírovat. Text zná konverzace, ne server. */
    val withText: Set<Long> = emptySet(),
) {
    val count: Int get() = keys.size

    /** Práva ještě nedorazila u všech vybraných. */
    val loading: Boolean get() = keys.any { it !in rights }

    private val ready: Boolean get() = keys.isNotEmpty() && !loading

    /**
     * Akce platí pro CELÝ výběr, takže se práva skládají přes `all`, ne `any`. Přeposlat půlku
     * vybraného a o druhé mlčet by znamenalo, že uživatel neví, co odešlo.
     */
    val canForward: Boolean get() = ready && rights.values.all { it.canForward }

    val canCopy: Boolean get() = ready && withText.isNotEmpty() && rights.values.all { it.canCopy }

    val canDelete: Boolean get() = ready && rights.values.all { it.canDeleteForSelf || it.canDeleteForAll }

    /** Nahlásit jde jen výběr, u kterého server nahlášení dovolí VŠEM zprávám (`canReportChat`). */
    val canReport: Boolean get() = ready && rights.values.all { it.canReport }

    /** Smazat i ostatním jde, jen když to jde u všech vybraných. */
    val canDeleteForAll: Boolean get() = ready && rights.values.all { it.canDeleteForAll }

    val canDeleteForSelf: Boolean get() = ready && rights.values.all { it.canDeleteForSelf }
}

/** Zpráva čekající na potvrzení připnutí. Zvlášť od [MessageMenu] ze stejného důvodu jako [PendingDelete]. */
data class PendingPin(
    val messageId: Long,
    val scope: PinScope,
)

/** Komu se připnutí týká — podle toho dialog nabízí jinou volbu. */
enum class PinScope {
    /** Volba: připnout i protějšku. */
    Private,

    /** Volba: upozornit členy. */
    Group,

    /** Volba: upozornit odběratele. */
    Channel,
}

/**
 * Kolik emoji se vejde do rychlého řádku.
 *
 * Šest jako v Google Messages — na jeden řádek se vejdou i na úzkém telefonu vedle tlačítka „+".
 */
private const val MAX_QUICK_REACTIONS = 6

/**
 * Strop výběru. `deleteMessages` i `forwardMessages` berou v TDLib nejvýš sto zpráv naráz, takže
 * vyšší výběr by skončil chybou až u akce — a projít víc než sto zpráv ťuknutím stejně nikdo nechce.
 */
internal const val MAX_SELECTED_MESSAGES = 100

/** Mezi zprávami ve schránce zůstane prázdný řádek — jinak by dlouhé zprávy splynuly v jednu. */
private const val SELECTION_COPY_SEPARATOR = "\n\n"

/**
 * Činnost v chatu pro konverzaci. [sender] je `null` v soukromém chatu (tvář nese [ChatInfo])
 * a tam, kde se odesílatele nepodařilo zjistit.
 */
data class Typing(val activity: ChatActivity, val sender: SenderInfo?)

/**
 * Které zprávy z viditelných položek ([visibleKeys] jsou KLÍČE, ne id) se hlásí jako zhlédnuté.
 *
 * Cizí zprávy vždycky; album celé, protože je v seznamu jednou položkou s klíčem nejstarší
 * zprávy a ostatní fotky by jinak zůstaly v počtu nepřečtených napořád. Vlastní zprávu tehdy,
 * když nese nepřečtené reakce nebo hlasy v anketě — přečte je právě zhlédnutí —, **a když leží
 * nad hranicí nepřečtených** ([ConversationState.unreadBoundaryId]).
 *
 * To poslední je kvůli tématům: server v tématu počítá jako nepřečtené i VLASTNÍ zprávy nad
 * hranicí (servisní „přibyli členové" po přidání, zpráva poslaná z jiného zařízení). Cizí zpráva
 * nad hranicí žádná nebyla, takže se nehlásilo nic, hranice stála a odznak „2" u tématu zůstal
 * napořád, i když v něm uživatel četl a psal. Nahlášená vlastní zpráva hranici posune přes ni.
 */
/** Zprávy, jejichž zhlédnutí něco přečte — nepřečtená reakce nebo hlas v anketě. */
internal fun ConversationState.unreadMarkedIds(): Set<Long> =
    messages.filter { it.hasUnreadReactions || it.hasUnreadPollVotes }.mapTo(HashSet()) { it.id }

internal fun ConversationState.messagesToView(visibleKeys: Set<Long>): List<Long> {
    val albums = messages
        .filter { keyOf(it) in visibleKeys }
        .map { it.mediaAlbumId }
        .filter { it != 0L }
        .toSet()
    return messages
        .filter {
            (!it.isOutgoing || it.hasUnreadReactions || it.hasUnreadPollVotes || it.id > (unreadBoundaryId ?: Long.MAX_VALUE)) &&
                (keyOf(it) in visibleKeys || it.mediaAlbumId in albums)
        }
        .map { it.id }
}

/** Otevřený dialog upozornění na blízkost: u které zprávy a jaký poloměr je nastavený teď. */
data class ProximityAlertPrompt(val messageId: Long, val currentRadius: Int)

data class ConversationState(
    val info: ChatInfo? = null,
    /** Vlákno komentářů (kořenová zpráva); `null` u běžné konverzace. */
    val threadRootId: Long? = null,
    /** Téma skupiny s tématy; `null` u běžné konverzace. */
    val forumTopicId: Int? = null,
    /** Téma Uložených zpráv; `null` u běžné konverzace. */
    val savedTopicId: Long? = null,
    /**
     * Nepřečtené zmínky a reakce v chatu — tlačítka „@" a „❤" nad tlačítkem dolů. Ve vlákně
     * komentářů zůstávají nulové: počty patří celé diskusní skupině.
     */
    val unreadMarks: UnreadMarks = UnreadMarks(),
    /** Zpráva otevřená k výběru úryvku textu; `null` = sheet je zavřený. */
    val selectingText: ChatMessage? = null,
    /** Dostupnost protějšku pro podtitul hlavičky. `null` u skupin a kanálů. */
    val presence: Presence? = null,
    /**
     * Co protějšek (ve skupině kdokoli) právě dělá. Kreslí se v seznamu zpráv tam, kam jeho zpráva
     * naskočí (`TypingItem`), ne v hlavičce.
     */
    val typing: Typing? = null,
    /** Otevřená nabídka nad zprávou (rychlé reakce + akce). */
    val messageMenu: MessageMenu? = null,
    /** Otevřená nabídka nad systémovým oznámením (jen smazání). */
    val noticeMenu: NoticeMenu? = null,
    /** Reaguje dvojité ťuknutí na zprávu rychlou reakcí? Z nastavení aplikace. */
    val doubleTapReaction: Boolean = true,
    /**
     * Nepřečtené zprávy, které diskrétní čtení (`AppSettings.discreetReading`) drží
     * nenahlášené; 0, když je volba vypnutá. Podle toho se ukáže „Označit jako přečtené".
     */
    val discreetUnread: Int = 0,
    /** Zpráva čekající na potvrzení smazání. */
    val pendingDelete: PendingDelete? = null,
    /** Zpráva čekající na potvrzení připnutí. */
    val pendingPin: PendingPin? = null,
    /** Zpráva, na kterou se právě chystá odpověď. Zobrazuje se nad vstupním polem. */
    val replyingTo: ChatMessage? = null,
    /**
     * Citovaný úryvek [replyingTo], když se neodpovídá na celou zprávu (označený text
     * v nabídce nad zprávou → „Citovat"). Bez [replyingTo] nemá smysl a maže se s ním.
     */
    val replyQuote: ReplyQuote? = null,
    /**
     * Citované zprávy, které nejsou v [messages] — doplněné jednotlivě.
     * Náhled odpovědi se hledá nejdřív v [messages], teprve pak tady.
     */
    val quotes: Map<Long, ChatMessage> = emptyMap(),
    /** Protějšek přečetl odchozí zprávy až po tohle id včetně. */
    val lastReadOutboxMessageId: Long = 0L,
    /**
     * Poslední zpráva, kterou uživatel při otevření chatu měl přečtenou. Nad ní se kreslí předěl
     * „Nepřečtené zprávy". `null`, když chat nepřečtené neměl.
     */
    val unreadBoundaryId: Long? = null,
    /** Zpráva, na kterou má obrazovka odrolovat. Vynuluje se po provedení. */
    val scrollTarget: ScrollTarget? = null,
    /**
     * Text k vložení do vstupního pole — koncept z TDLib, text upravované zprávy nebo návrat
     * z úpravy. Obrazovka ho po použití vynuluje ([ConversationViewModel.consumeInputFill]).
     */
    val inputFill: InputFill? = null,
    /** Připnuté zprávy pro pruh pod hlavičkou, od nejnovější. */
    val pinned: List<ChatMessage> = emptyList(),
    /** Smí uživatel připnuté zprávy odepínat. */
    val canUnpin: Boolean = false,
    /** Otevřený detail reakcí; `null`, když je zavřený. */
    val reactionDetails: ReactionDetails? = null,
    /** Otevřené informace o zprávě; `null`, když je sheet zavřený. */
    val messageInfo: MessageInfoSheet? = null,
    /** Otevřený překlad zprávy; `null`, když je sheet zavřený. */
    val translation: TranslationSheet? = null,
    /**
     * Zpráva, u které si uživatel ťuknutím vyžádal odznak přečtení; `null`, když žádná.
     *
     * Jedno id, ne množina: ťuknutí na jinou zprávu odznak přepne tam. Odchod z chatu ho zahodí
     * i s celým stavem — je to zobrazení na chvíli, ne nastavení.
     */
    val readReceiptFor: Long? = null,
    /** Průběh stahování souborů, které uživatel právě otevírá (podle `fileId`). */
    val documentDownloads: Map<Int, Float> = emptyMap(),
    /** Zprávy, které uživatel chce přeposlat; obrazovka na to otevře výběr cílového chatu. */
    val forwardRequest: List<Long>? = null,
    /** Zpráva, ke které se vybírá čas připomínky (`RemindSheet`); `null` = nic. */
    val remindRequest: RemindRequest? = null,
    /** Režim výběru víc zpráv; `null` = vypnutý (běžná konverzace). */
    val selection: MessageSelection? = null,
    /** Přílohy vybrané k odeslání, čekající nad vstupním polem na popisek a odeslání. */
    val attachments: List<OutgoingAttachment> = emptyList(),
    /**
     * Upravené fotky nad polem podle SOUČASNÉ cesty: z čeho vznikly a jakými tahy. Další úprava
     * začíná z originálu s těmito tahy (viz [ConversationViewModel.applyPhotoMarkup]).
     */
    val photoEdits: Map<String, PhotoEdit> = emptyMap(),
    /** Úprava fotky se právě vykresluje do souboru. */
    val markupBusy: Boolean = false,
    /** Krátké potvrzení akce, která v konverzaci nic viditelného nezmění (snackbar). */
    val notice: UiText? = null,
    /** Právě odepnutá zpráva — obrazovka k ní ukáže snackbar s „Vrátit". */
    val unpinned: ChatMessage? = null,
    /** Uživatel, kterého karta kontaktu právě přidala — obrazovka ukáže snackbar s „Vrátit". */
    val addedContact: Long? = null,
    /** Zpráva, jejíž text se právě upravuje ve vstupním poli; `null` při běžném psaní. */
    val editing: ChatMessage? = null,
    /** Náhled odkazu v rozepsaném textu, jak ho vrátil server; `null`, když text odkaz nemá. */
    val linkPreview: LinkPreview? = null,
    /** Odkazy v rozepsaném textu, jak stojí za sebou; víc než jeden = jde vybrat, ke kterému náhled. */
    val linkPreviewLinks: List<String> = emptyList(),
    /** Co uživatel s náhledem udělal (vybral odkaz, vypnul, velikost, poloha). Patří ke konceptu. */
    val linkPreviewOptions: LinkPreviewOptions = LinkPreviewOptions(),
    /** Zpráva krátce zvýrazněná po skoku na citaci; `null` většinu času. */
    val highlightedMessageId: Long? = null,
    /** Skok na nenačtenou zprávu, která se právě načítá (viz [PendingJump]). */
    val pendingJump: PendingJump? = null,
    /** Hledání v chatu; `null` = zavřené (hlavička ukazuje chat, dole je vstupní pole). */
    val search: InChatSearch? = null,
    /** Jednorázová událost: otevřít chat ze zmínky nebo odkazu, případně se skokem na zprávu. */
    val openChat: ChatTarget? = null,
    /** Jednorázová událost: odkaz na obrazovku aplikace (`tg://settings/…`, `tg://newgroup`). */
    val openScreen: AppScreen? = null,
    /** Jednorázová událost: otevřít příběh v prohlížeči (citace odpovědi, odkaz na příběh). */
    val openStory: StoryTarget? = null,
    /** Přihlašovací odkaz, který se z ťuknutí neotevírá — jen upozornění; `null` = žádný. */
    val loginWarning: LoginLinkKind? = null,
    /** Jednorázová událost: odkaz `t.me/bot?startgroup=…` — otevřít výběr skupiny. */
    val addBotToGroup: InternalLink.BotStartInGroup? = null,
    /** Tlačítko bota s inline dotazem do jiného chatu: obrazovka otevře výběr chatu. */
    val switchInline: SwitchInlinePick? = null,
    /** Jednorázová událost: otevřít profil chatu (odesílatele ze skupiny). */
    val openProfile: Long? = null,
    /** Výběr člena pro hledání podle odesílatele; `null` = zavřený. */
    val memberPicker: MemberPicker? = null,
    /** Náhled chatu z pozvánky; `null` = zavřený. */
    val invite: InviteSheetState? = null,
    /** Náhled sdílené složky z odkazu `t.me/addlist/…`; `null` = zavřený. */
    val folderInvite: FolderInviteSheetState? = null,
    /** Vlastní klávesnice bota nebo vynucená odpověď v chatu; `null` = žádná. */
    val replyMarkup: ChatReplyMarkup? = null,
    /** Příkazy botů v chatu — napovídání po „/" a seznam z tlačítka u pole; prázdné = žádní boti. */
    val botCommands: List<BotCommandItem> = emptyList(),
    /** Seznam příkazů otevřený tlačítkem u pole (bez ohledu na to, co je v poli). */
    val commandsOpen: Boolean = false,
    /** Mini aplikace z tlačítka nabídky bota; když je, stojí u pole místo tlačítka příkazů. */
    val botMenuApp: BotMenuApp? = null,
    /** Po kolika sekundách se nové zprávy chatu samy mažou; 0 = nemažou (odznak v hlavičce). */
    val autoDeleteTime: Int = 0,
    /** Rozepsaný dotaz na inline bota (`@gif kočka`) a jeho výsledky; `null` = žádný. */
    val inline: InlineQueryState? = null,
    /** Napovídání zmínky (`@ja`): členové k dotazu u kurzoru; `null` = nic se nenapovídá. */
    val mentionSuggestions: MentionSuggestions? = null,
    /** Tlačítko bota chce sdílet telefonní číslo a čeká se na potvrzení. */
    val confirmSharePhone: Boolean = false,
    /** Tlačítko bota chce polohu; po potvrzení ji zjistí obrazovka. */
    val confirmShareLocation: Boolean = false,
    /** Odkaz z tlačítka bota, který čeká na potvrzení; `null` = žádný. */
    val confirmLink: String? = null,
    /** Tlačítko bota chce uživatele přihlásit na web; čeká se na potvrzení v dialogu. */
    val loginRequest: LoginRequest? = null,
    /** Upozornění od bota, které se musí potvrdit (`showAlert`); `null` = žádné. */
    val botAlert: String? = null,
    /** Tlačítko bota, na jehož odpověď se čeká; `null` = žádné. */
    val pressingBotButton: BotButtonRef? = null,
    /** Video kolečko, které hraje se zvukem; `null` = všechna hrají potichu (nebo vůbec). */
    val soundingVideoNote: Long? = null,
    /** Odkud kolečko se zvukem hraje (čas v médiu, viz [MessageAction.PlayMediaAt]); jinak nula. */
    val soundingVideoNoteFromMs: Long = 0L,
    /** Video otevřít v prohlížeči od daného místa (čas v médiu); `null` = nic. */
    val openMediaAt: MediaAt? = null,
    /** Popis bota; `null` = chat není s botem (a Start se nenabízí). */
    val botIntro: BotIntro? = null,
    /** Parametr ze startovacího odkazu, který čeká na Start ([BotStartRequests]). */
    val botStartParameter: String? = null,
    /** Start odešel a čeká se na odpověď — tlačítko je neaktivní. */
    val startingBot: Boolean = false,
    /** Má chat naplánované zprávy? Pak je u pole hodiny, které otevřou jejich seznam. */
    val hasScheduledMessages: Boolean = false,
    /** Otevřený seznam naplánovaných zpráv; `null` = zavřený. */
    val scheduledMessages: List<ChatMessage>? = null,
    /** Zpráva s anketou, jejíž hlasující ukazuje sheet; `null` = zavřený. */
    val pollVoters: Long? = null,
    /** Sheet se stavem účasti v soutěži; `null` = zavřený. */
    val giveaway: GiveawaySheet? = null,
    /** Zpráva s anketou, do které se přidává odpověď (dialog); `null` = zavřený. */
    val addingPollOption: Long? = null,
    /** Anketa, kterou uživatel chce ukončit a čeká se na potvrzení; `null` = žádná. */
    val pendingStopPoll: Long? = null,
    /** Useknuté články, které se právě dočítají („Číst dál"). */
    val loadingArticles: Set<Long> = emptySet(),
    /** Sheet výběru kontaktu ze sponky; `null` = zavřený. */
    val contactPicker: ContactPicker? = null,
    /** Dialog upozornění na blízkost u vlastní živé polohy; `null` = zavřený. */
    val proximityAlert: ProximityAlertPrompt? = null,
    /**
     * Živé polohy, které se v chatu právě sdílí (vlastní napřed) — pro dock nad konverzací.
     * Nezávisí na načtené historii: zpráva s polohou může být dávno odscrollovaná nebo mimo okno.
     */
    val liveLocations: List<ChatMessage> = emptyList(),
    /** Kontakt, který čeká na potvrzení odeslání (jméno a číslo); `null` = žádný. */
    val pendingContact: MessageMedia.Contact? = null,
    /** Jednorázová událost: tlačítko bota chce formulář nové ankety (v tomhle režimu). */
    val openPollComposer: PollComposerMode? = null,
    /** Jednorázová událost: tlačítko bota chce výběr lidí nebo chatu. */
    val openBotShare: BotShareRequest? = null,
    /** Mini aplikace k otevření — obrazovka ji otevře a událost vynuluje. */
    val openWebApp: WebAppRequest? = null,
    /** Jednorázová událost: otevřít sheet sady samolepek z odkazu `t.me/addstickers/…`. */
    val openStickerSet: Long? = null,
    /** Běží přidání ke skupině / kanálu ([ConversationViewModel.join]). */
    val joining: Boolean = false,
    /** Ztlumení chatu; `null`, dokud se nepřečte. Ukazuje se jen v kanálu bez práva publikovat. */
    val muted: Boolean? = null,
    /** Reklamy k zobrazení. Podle Telegram ToS se nesmí filtrovat ani skrývat. */
    val sponsored: List<SponsoredMessage> = emptyList(),
    /** Text právě letící ze vstupního pole do bubliny; `null` když se nic neodesílá. */
    val flight: MessageFlight? = null,
    /** Od nejnovější po nejstarší. */
    val messages: List<ChatMessage> = emptyList(),
    /**
     * Klíč položky v seznamu pro zprávy, kterým TDLib po odeslání změnil id: nové id → dočasné.
     *
     * Bez toho by potvrzení doručení znamenalo pro `LazyColumn` odebrání jedné položky a přidání
     * jiné — bublina by problikla. Ostatní zprávy tu nejsou a klíčem je jim rovnou id.
     */
    val itemKeys: Map<Long, Long> = emptyMap(),
    val loading: Boolean = false,
    val reachedStart: Boolean = false,
    /**
     * Načtené okno sahá až k NEJNOVĚJŠÍ zprávě chatu. Po skoku na starou zprávu (hledání,
     * citace) je v [messages] jen okno kolem ní a nejnovější zprávy chybí — dokud se k nim
     * nedostránkuje, nové zprávy se do seznamu nepřidávají, jinak by v něm vznikla díra.
     */
    val reachedEnd: Boolean = true,
    /**
     * Pořadové číslo načteného okna. Zvedá se, kdykoli se [messages] VYMĚNÍ celé (skok do
     * historie, návrat k přítomnosti), ne při stránkování. Obrazovka podle něj zakládá nový stav
     * seznamu — viz `listState` v `ConversationScreen`.
     */
    val window: Int = 0,
    val error: UiText? = null,
) {
    /** Konverzace zúžená na část chatu (vlákno komentářů, téma): hlavička patří jí, ne chatu. */
    val narrowed: Boolean get() = threadRootId != null || forumTopicId != null || savedTopicId != null

    /**
     * Jestli se u připravené přílohy smí nabídnout mizení — pro [attachmentCount] příloh.
     * Pravidlo je [selfDestructAllowed]; dokud hlavička chatu nedorazí, nesmí se nic.
     */
    /** Do chatu se smí psát: vstupní pole, odpověď swipem. Dokud hlavička nedorazí, bere se, že ano. */
    val canWrite: Boolean
        get() = info == null || (!info.canJoin && info.writeAccess == WriteAccess.Allowed)

    /**
     * Které druhy obsahu chat přijme. Dokud hlavička nedorazí, neomezuje se nic — ikona, která
     * by problikla a zmizela, je horší než ikona, po které přijde chyba jednou za čas.
     */
    val content: ContentAccess
        get() = info?.content ?: ContentAccess.All

    fun canSelfDestruct(attachmentCount: Int = attachments.size): Boolean =
        info != null && selfDestructAllowed(!info.isGroup, info.isSavedMessages, attachmentCount)

    /**
     * Klíč BLOKU, do kterého zpráva patří — u alba klíč celého alba, ne ťuknuté fotky.
     * `null`, když zpráva v načteném okně není.
     */
    fun blockKeyOf(messageId: Long): Long? {
        val message = messages.firstOrNull { it.id == messageId } ?: return null
        val album = message.mediaAlbumId
        // Blok nese klíč své NEJSTARŠÍ zprávy, stejně jako položka v seznamu (viz `conversationItems`).
        val oldest = if (album == 0L) message else messages.filter { it.mediaAlbumId == album }.minBy { it.id }
        return keyOf(oldest)
    }

    /**
     * Id vybraných zpráv od nejstarší, i s ostatními zprávami vybraných alb.
     *
     * Vzestupně podle id: id zpráv jsou v chatu rostoucí, takže je to chronologické pořadí —
     * a v něm se má kopírovat i přeposílat.
     */
    fun selectedMessageIds(): List<Long> {
        val keys = selection?.keys.orEmpty()
        if (keys.isEmpty()) return emptyList()
        return messages.filter { keyOf(it) in keys }
            .flatMap { albumMemberIds(it.id) }
            .distinct()
            .sorted()
    }

    /** Id všech načtených zpráv alba, do kterého zpráva patří; u samostatné zprávy jen ona. */
    fun albumMemberIds(messageId: Long): List<Long> {
        val album = messages.firstOrNull { it.id == messageId }?.mediaAlbumId ?: return listOf(messageId)
        if (album == 0L) return listOf(messageId)
        return messages.filter { it.mediaAlbumId == album }.map { it.id }
    }

    /** Stabilní klíč položky zprávy; přežije změnu id po odeslání. */
    fun keyOf(message: ChatMessage): Long = itemKeys[message.id] ?: message.id

    /**
     * Zpráva, která dorazila updatem nebo se vrátila z odeslání.
     *
     * **Rozepsanou zprávu, kterou už nahradilo potvrzení odeslání, ZAHODÍ.** Potvrzení totiž
     * nemusí dorazit až po ní: `newMessages` a `sentMessageUpdates` jsou dva samostatné odběry
     * a jejich pořadí není ničím zaručené. Když se prohodí, vrátila by se do seznamu dočasná
     * verze zprávy, která už v něm je pod novým id — a protože si podle [itemKeys] obě drží
     * TENTÝŽ klíč, `LazyColumn` na to spadl („Key … was already used").
     *
     * Ověřuje se to proti HODNOTÁM [itemKeys], tedy dočasným id potvrzených zpráv. Klíče jsou
     * nová id a těch se dotaz netýká.
     */
    fun withMessage(message: ChatMessage): ConversationState = when {
        message.id in itemKeys.values -> this
        reachedEnd -> copy(messages = merge(messages, listOf(message)))
        /*
         * Okno je v minulosti (viz [reachedEnd]). Cizí nová zpráva by za oknem udělala díru,
         * takže se jen zahodí — dorazí se stránkováním k novějším. Vlastní ODESÍLANÁ zpráva ale
         * znamená, že uživatel chce být dole: seznam začne znovu od ní, starší se dotáhnou
         * obvyklým stránkováním a obrazovka odroluje dolů.
         */
        message.isOutgoing && message.sendState == SendState.Sending -> copy(
            messages = listOf(message),
            reachedStart = false,
            reachedEnd = true,
            window = window + 1,
            scrollTarget = ScrollTarget(message.id, ScrollAnchor.Bottom),
        )
        else -> this
    }

    /**
     * Okno kolem zprávy (skok do historie). Nahradí, co bylo načtené — mezi starým a novým
     * oknem by jinak zůstala díra. O obou koncích se neví nic, zjistí se stránkováním.
     */
    fun withWindow(page: List<ChatMessage>): ConversationState =
        copy(messages = merge(emptyList(), page), reachedStart = false, reachedEnd = false, window = window + 1)

    /**
     * Stránka novějších zpráv nad [newestId]. Jen když okno mezitím nikdo nevyměnil (skok jinam,
     * odeslání zprávy) — jinak by se do nového seznamu vmíchala stránka ze starého. Stránka bez
     * novějších zpráv znamená, že okno došlo k přítomnosti.
     */
    fun withNewerPage(newestId: Long, page: List<ChatMessage>): ConversationState {
        if (reachedEnd || messages.firstOrNull()?.id != newestId) return this
        val newer = page.filter { it.id > newestId }
        return copy(messages = merge(messages, newer), reachedEnd = newer.isEmpty())
    }

    /** Stránka starších zpráv pod [oldestId]; stejná pojistka jako [withNewerPage]. */
    fun withOlderPage(oldestId: Long, page: List<ChatMessage>): ConversationState {
        if ((messages.lastOrNull()?.id ?: 0L) != oldestId) return this
        return copy(messages = merge(messages, page), reachedStart = page.isEmpty())
    }

    /**
     * Potvrzení (nebo selhání) odeslání. TDLib zprávě ZMĚNÍ id, takže ji nestačí aktualizovat —
     * původní se musí ze seznamu odebrat, jinak by tam byla dvakrát.
     *
     * Klíč položky ale zůstává PŮVODNÍ. Změna klíče znamená pro `LazyColumn` jinou položku: stará
     * by odprchla, nová naskočila a z potvrzení doručení by byl problik celé bubliny — navíc
     * uprostřed letu ze vstupního pole, jehož cíl by zmizel.
     */
    fun withSentMessage(oldId: Long, message: ChatMessage): ConversationState = copy(
        messages = merge(messages.filterNot { it.id == oldId }, listOf(message)),
        itemKeys = itemKeys - oldId + (message.id to (itemKeys[oldId] ?: oldId)),
    )
}

/** Jak dlouho se čeká, než se stahování souboru vůbec rozběhne. */
private val DOWNLOAD_START_TIMEOUT = 30.seconds

/** Tlačítko bota určené pozicí v klávesnici zprávy. */
data class BotButtonRef(val messageId: Long, val row: Int, val column: Int)

private fun UnsupportedBotButton.explanation(): StringResource = when (this) {
    UnsupportedBotButton.SwitchInline -> Res.string.bot_unsupported_switch_inline
    UnsupportedBotButton.Buy -> Res.string.bot_unsupported_buy
    UnsupportedBotButton.Game -> Res.string.bot_unsupported_game
    UnsupportedBotButton.Password -> Res.string.bot_unsupported_password
}

/**
 * Nápověda do prázdného pole, kterou si přeje bot; `null` = výchozí. U vynucené odpovědi jen
 * tehdy, když se opravdu odpovídá na tu zprávu — jinak by nápověda mluvila o něčem jiném.
 */
val ConversationState.inputPlaceholder: String?
    get() = when (val markup = replyMarkup) {
        is ChatReplyMarkup.Keyboard -> markup.placeholder.ifEmpty { null }
        is ChatReplyMarkup.ForceReply -> markup.placeholder.takeIf { it.isNotEmpty() && replyingTo?.id == markup.messageId }
        null -> null
    }

private fun UnsupportedKeyboardButton.explanation(): StringResource = when (this) {
    UnsupportedKeyboardButton.ManagedBot -> Res.string.bot_unsupported_managed_bot
}

/** Jak dlouho se po vynucené odpovědi čeká, než zpráva bota dorazí do seznamu. */
private const val FORCE_REPLY_WAIT = 3_000L

/**
 * Spotřebovala odpověď odeslaná jinde ([sent], formulář ankety) tu rozepsanou tady? Jen když
 * se odpovídá pořád na tutéž zprávu — mezitím mohl uživatel začít odpovídat jinam.
 *
 * Odpověď se nezahazuje hned při otevření formuláře — kdo ho zavře, má ji mít dál.
 */
/**
 * Karty sdíleného kontaktu [userId] nabízejí (ne)přidání do kontaktů podle [addable] — po přidání
 * a po jeho vrácení. Týká se všech karet téhož člověka v konverzaci, ne jen té ťuknuté.
 */
internal fun ConversationState.withContactAddable(userId: Long, addable: Boolean): ConversationState =
    copy(
        messages = messages.map { message ->
            val contact = message.media as? MessageMedia.Contact
            if (contact?.userId == userId) message.copy(media = contact.copy(canAddToContacts = addable)) else message
        },
    )

internal fun ConversationState.replyConsumedBy(sent: ReplyRequest): Boolean =
    replyingTo?.id == sent.messageId


/** Výběr kontaktu ke sdílení; filtruje se lokálně podle jména, kontaktů bývají desítky. */
data class ContactPicker(
    val loading: Boolean = false,
    val contacts: List<ChatSummary> = emptyList(),
    val query: String = "",
    /** Vlastní účet — řádek „Můj kontakt" nad kontakty; `null`, dokud se nenačte. */
    val me: MessageMedia.Contact? = null,
) {
    /** Vlastní kontakt se nabízí jen bez dotazu: hledá se v kontaktech, ne v sobě. */
    val showsMe: Boolean get() = me != null && query.isBlank()

    val shown: List<ChatSummary>
        get() = query.trim().let { q -> if (q.isEmpty()) contacts else contacts.filter { it.title.contains(q, ignoreCase = true) } }
}

/** Náhled odkazu konceptu, odložený po dobu úpravy zprávy ([ConversationViewModel]). */
private data class LinkPreviewStash(
    val preview: LinkPreview? = null,
    val links: List<String> = emptyList(),
    val options: LinkPreviewOptions = LinkPreviewOptions(),
)

/**
 * Mini aplikace, kterou chce konverzace otevřít. [launch] = přes `openWebApp` (tlačítko pod
 * zprávou, nabídka bota), jinak jen adresa (`getWebAppUrl`: vlastní klávesnice, tlačítko nad
 * výsledky inline bota). [keyboardButtonText] jen u vlastní klávesnice — s ním smí mini aplikace
 * poslat data. [main] = hlavní mini aplikace bota (`getMainWebApp`), [shortName] = pojmenovaná
 * z odkazu `t.me/bot/app`; [url] je u obou prázdná.
 */
data class WebAppRequest(
    val botUserId: Long,
    val url: String,
    val launch: Boolean,
    val keyboardButtonText: String? = null,
    val main: Boolean = false,
    val startParameter: String = "",
    /** Pojmenovaná mini aplikace z odkazu `t.me/bot/app` (`getWebAppLinkUrl`). */
    val shortName: String = "",
)

/** Výběr pro tlačítko bota „vyber lidi" ([users]) / „vyber chat" ([chat]) ve zprávě [messageId]. */
data class BotShareRequest(val messageId: Long, val users: BotUsersRequest? = null, val chat: BotChatRequest? = null)

/** Přihlášení na web z tlačítka bota, které čeká na potvrzení. */
data class LoginRequest(val messageId: Long, val buttonId: Long, val prompt: LoginUrlPrompt.Confirm)

/** Tlačítko bota s inline dotazem do chatu, který se teprve vybere ([SwitchInlineTarget.Chosen]). */
data class SwitchInlinePick(val bot: String, val query: String, val target: SwitchInlineTarget.Chosen)

/**
 * Strop pro rozpoznání jazyka při otevírání nabídky nad zprávou. Rychlé je v řádu milisekund,
 * jen první volání po startu načítá model; nabídka na něj čekat nemá.
 */
private const val LANGUAGE_DETECTION_TIMEOUT_MS = 300L

/** Sheet se stavem účasti v soutěži ze zprávy [messageId]; [status] je `null`, dokud nedorazí. */
data class GiveawaySheet(
    val messageId: Long,
    val status: GiveawayStatus? = null,
    val failed: Boolean = false,
    val codeCopied: Boolean = false,
)

/** Příběh, který se má otevřít v prohlížeči: kdo ho zveřejnil a jeho id. */
data class StoryTarget(val posterChatId: Long, val storyId: Int)

/** Cesta, pod kterou přílohu zná vestavěná galerie (viz [galleryPath] nad záznamy úprav). */
fun ConversationState.galleryPath(path: String): String = photoEdits.galleryPath(path)
