package eu.tintera.plume.features.conversation

import eu.tintera.plume.resources.conversation_contact_send_failed
import eu.tintera.plume.resources.conversation_contact_no_phone
import eu.tintera.plume.resources.conversation_contact_load_failed
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.ReplyQuote
import eu.tintera.plume.core.telegram.UnreadMarkKind
import eu.tintera.plume.core.telegram.BotCommandItem
import eu.tintera.plume.core.telegram.UnreadMarks
import eu.tintera.plume.core.telegram.ChatReplyMarkup
import eu.tintera.plume.core.telegram.PollComposerMode
import eu.tintera.plume.core.telegram.ReplyKeyboardButtonKind
import eu.tintera.plume.core.telegram.UnsupportedKeyboardButton
import eu.tintera.plume.resources.poll_add_option_failed
import eu.tintera.plume.resources.bot_unsupported_location
import eu.tintera.plume.resources.bot_unsupported_pick_chat
import eu.tintera.plume.resources.bot_unsupported_managed_bot
import eu.tintera.plume.core.telegram.BotButtonKind
import eu.tintera.plume.core.telegram.UnsupportedBotButton
import eu.tintera.plume.resources.bot_answer
import eu.tintera.plume.resources.bot_button_failed
import eu.tintera.plume.resources.poll_retract_failed
import eu.tintera.plume.resources.poll_stop_failed
import eu.tintera.plume.resources.poll_vote_failed
import eu.tintera.plume.resources.bot_text_copied
import eu.tintera.plume.resources.bot_unsupported_web_app
import eu.tintera.plume.resources.bot_unsupported_login_url
import eu.tintera.plume.resources.bot_unsupported_switch_inline
import eu.tintera.plume.resources.bot_unsupported_buy
import eu.tintera.plume.resources.bot_unsupported_game
import eu.tintera.plume.resources.bot_unsupported_password
import eu.tintera.plume.core.telegram.SenderInfo
import eu.tintera.plume.core.telegram.ForwardOrigin
import eu.tintera.plume.resources.invite_request_sent
import eu.tintera.plume.resources.conversation_link_failed
import eu.tintera.plume.core.telegram.InvitePreview
import eu.tintera.plume.core.telegram.InviteJoinResult
import eu.tintera.plume.core.telegram.InternalLink
import eu.tintera.plume.core.telegram.platformLanguageCode
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
import eu.tintera.plume.core.audio.VoiceRecorder
import eu.tintera.plume.core.audio.VoiceRecording
import eu.tintera.plume.core.audio.VoiceSpeed
import eu.tintera.plume.core.files.FileOpener
import eu.tintera.plume.core.files.MediaSharer
import eu.tintera.plume.core.files.SaveAsLauncher
import eu.tintera.plume.core.share.ShareRequests
import eu.tintera.plume.core.share.SharedContent
import eu.tintera.plume.core.share.ShareTargets
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.settings.ExpressionMode
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
import eu.tintera.plume.core.ui.UrlOpener
import eu.tintera.plume.features.conversation.items.MessageAction
import eu.tintera.plume.features.conversation.items.MessageMenuAction
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.paid_media_unsupported
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
import eu.tintera.plume.core.telegram.SpamBar
import eu.tintera.plume.features.chats.SpamCleanup
import eu.tintera.plume.features.chats.cleanUpSpam
import eu.tintera.plume.features.report.ReportFlow
import eu.tintera.plume.resources.chats_blocked
import eu.tintera.plume.resources.chats_unarchived
import eu.tintera.plume.resources.spam_bar_contact_added
import eu.tintera.plume.resources.spam_bar_failed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
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
    /** Jen kvůli hlasovkám: soubor se stáhne až při prvním přehrání. */
    private val fileRepository: FileRepository,
    private val voicePlayer: VoicePlayer,
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

    /**
     * Přehrávání hlasovky. Samostatný tok, ne součást [state]: pozice se mění dvacetkrát za
     * vteřinu a přepočítávat kvůli tomu celý stav konverzace by bylo zbytečné.
     */
    val voicePlayback: StateFlow<VoicePlayback?> = voicePlayer.state

    /** Zvolená rychlost přehrávání; jedna pro celou aplikaci, proto rovnou z přehrávače. */
    val voiceSpeed: StateFlow<VoiceSpeed> = voicePlayer.speed

    private val _state = MutableStateFlow(ConversationState(threadRootId = threadRootId))
    val state: StateFlow<ConversationState> = _state.asStateFlow()

    /**
     * Pruh proti spamu nad konverzací (chat od neznámého). Samostatný tok ze stejného důvodu
     * jako [connection]: mění se zřídka a nezávisle na zprávách.
     */
    val spamBar: StateFlow<SpamBar?> = chatRepository.spamBar(chatId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Průvodce nahlášením — z nabídky nad zprávou i z pruhu proti spamu. */
    val reportFlow = ReportFlow(viewModelScope, chatRepository::report)

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

    /** Poslední text z pole, jak ho hlásí obrazovka. Kvůli ukládání konceptu. */
    private var draftText = ""

    /**
     * Koncept z TDLib je vyřízený (vrácený do pole, nebo žádný není). Sdílený text se vkládá až
     * po něm: koncept jde jen do prázdného pole, takže sdílení před ním by ho z pole vytlačilo.
     */
    private val draftRestored = CompletableDeferred<Unit>()

    /** Koncept odložený po dobu úpravy zprávy; po ní se vrátí do pole. */
    private var draftBeforeEdit = ""

    /** Odložené uložení konceptu; každá další úprava ho posune. */
    private var draftSave: Job? = null

    /** Odložené zjištění náhledu odkazu v rozepsaném textu; každý úhoz ho posune. */
    private var linkPreviewJob: Job? = null

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

    init {
        viewModelScope.launch {
            voicePlayer.errors.collect { message -> _state.update { it.copy(error = message) } }
        }

        // Příkazy botů se čtou jednou při otevření; bot je mění zřídka a `getUserFullInfo` je
        // po prvním dotazu lokální. Nezjištěné = žádné, napovídání se neukáže.
        viewModelScope.launch {
            repository.botCommands(chatId).onSuccess { commands -> _state.update { it.copy(botCommands = commands) } }
        }

        // Automatické mazání se ukazuje v hlavičce; mění se i z jiného zařízení nebo od protějšku.
        viewModelScope.launch {
            repository.autoDeleteTime(chatId).collect { seconds -> _state.update { it.copy(autoDeleteTime = seconds) } }
        }

        // Dohraná hlasovka pustí další neposlechnutou; která to je, ví jedině konverzace.
        viewModelScope.launch {
            voicePlayer.completions.collect { finished ->
                if (finished.chatId == chatId) playNextUnlistened(after = finished.messageId)
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
                        inputFill = info.draftText?.let { text -> InputFill(text, onlyIfEmpty = true) },
                        linkPreviewOptions = info.draftLinkPreview ?: LinkPreviewOptions(),
                    )
                }
                info.draftText?.let {
                    draftText = it
                    refreshLinkPreview(it)
                }
                info.draftReplyToMessageId?.let { restoreDraftReply(it, info.draftReplyQuote) }
                // Nalezená zpráva má přednost před nepřečtenými — kvůli ní chat otevřel.
                if (initialMessageId != null) {
                    jumpToMessage(initialMessageId, fromSearch = true)
                } else {
                    loadUntilUnread()
                }
            }
            draftRestored.complete(Unit)
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
    }

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
        // Hledání ve vlákně by hledalo v celé diskusní skupině (`searchChatMessages` bez tématu).
        if (threadRootId != null) return
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
                    repository.markRead(chatId, listOf(messageId))
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
    private suspend fun loadUntilUnread() {
        val boundary = _state.value.unreadBoundaryId ?: return

        // Hranice je načtená, jakmile máme aspoň jednu zprávu z přečtené části. Bez ní se předěl
        // stejně nevykreslí, takže skok nemá kam mířit.
        val loaded = loadUntil(MAX_UNREAD_PAGES) { messages -> messages.any { it.id <= boundary } } ||
            // Hranice je poslední přečtená zpráva a mohla být smazaná — okno je dobré i bez ní.
            loadAround(boundary, requireTarget = false)
        if (!loaded) return

        // První nepřečtená je nejstarší PŘÍCHOZÍ nad hranicí — vlastní odpověď nepřečtená není.
        val firstUnread = firstUnreadId(_state.value.messages, boundary) ?: return
        _state.update { it.copy(scrollTarget = ScrollTarget(firstUnread, ScrollAnchor.Top)) }
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
                    it.withMessage(message).copy(flight = it.flight.landingOn(message))
                }
                // Anketa se zakládá ve vlastním formuláři a rozepsanou odpověď si odnesla; tady
                // se pozná, že odešla, a odpověď zmizí jako po odeslání textu.
                if (_state.value.pollConsumesReply(message)) clearReply()
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
                                    else -> message.copy(hasUnreadReactions = change.hasUnread)
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

                _state.update { state -> state.withSentMessage(oldId, message) }
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
    fun sendSticker(sticker: StickerItem) {
        // Výběr samolepek se bez práva neotevře, ale sada jde otevřít i ťuknutím na samolepku
        // v historii — a z ní se posílá taky. Hláška je lepší než chyba od serveru.
        if (!_state.value.content.stickers) {
            _state.update { it.copy(notice = UiText(Res.string.conversation_content_not_allowed)) }
            return
        }
        sendMedia(Res.string.conversation_sticker_send_failed) { replyTo, quote ->
            repository.sendSticker(chatId, sticker, replyTo, quote)
        }
    }

    /** Odešle GIF z výběru. Stejné právo a stejné chování jako [sendSticker]. */
    fun sendGif(gif: GifItem) {
        if (!_state.value.content.stickers) {
            _state.update { it.copy(notice = UiText(Res.string.conversation_content_not_allowed)) }
            return
        }
        sendMedia(Res.string.conversation_gif_send_failed) { replyTo, quote ->
            repository.sendGif(chatId, gif, replyTo, quote)
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
            val text = if (draftText.isBlank()) shared else draftText.trimEnd() + "\n" + shared
            _state.update { it.copy(inputFill = InputFill(text, onlyIfEmpty = false)) }
            // Pole se vyplní bez `onDraftChanged`; sdílený odkaz je přitom typický případ náhledu.
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
     * fotku hlásí jako soubor, ne jako pořadí nad polem (viz `EmbeddedMediaPicker`).
     */
    fun removeAttachments(paths: List<String>) {
        if (paths.isEmpty()) return
        val removed = paths.toSet()
        _state.update { state ->
            state.copy(attachments = state.attachments.filterNot { it.path in removed })
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

    private fun sendAttachments(caption: String, attachments: List<OutgoingAttachment>, options: SendOptions) {
        typingSentAt = null
        draftSave?.cancel()
        draftSave = null
        draftText = ""
        resetLinkPreview()

        val replyTo = _state.value.replyingTo?.id
        val quote = _state.value.replyQuote
        val mentions = takeNameMentions()
        clearReply()
        _state.update { it.copy(attachments = emptyList()) }

        viewModelScope.launch {
            repository.sendAttachments(chatId, attachments, caption, replyTo, quote, options, mentions)
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
        send: suspend (replyTo: Long?, quote: ReplyQuote?) -> Result<ChatMessage>,
    ) {
        val replyTo = _state.value.replyingTo?.id
        val quote = _state.value.replyQuote
        clearReply()

        viewModelScope.launch {
            send(replyTo, quote)
                .onSuccess { pending ->
                    shareTargets.reportUsed(chatId)
                    // Potvrzení mohlo dorazit dřív než výsledek; `withMessage` to ošetří.
                    _state.update { it.withMessage(pending) }
                }
                .onFailure { failure ->
                    _state.update { it.copy(error = failure.reported(failureMessage)) }
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
    fun send(text: String, options: SendOptions = SendOptions()) {
        val trimmed = text.trim()

        // S přílohami je text popisek a smí být prázdný. Úprava zprávy přílohy nemá.
        val attachments = _state.value.attachments
        if (attachments.isNotEmpty() && _state.value.editing == null) {
            sendAttachments(trimmed, attachments, options)
            return
        }

        // Úprava se řeší dřív než prázdné pole: prázdný POPISEK je platná úprava (odebere ho),
        // takže o tom rozhoduje `sendEdit` podle toho, co se vlastně upravuje.
        _state.value.editing?.let { target ->
            sendEdit(target, trimmed)
            return
        }

        if (trimmed.isEmpty()) return

        // Odeslaná zpráva psaní ukončí sama; další úhoz má „píšu" ohlásit hned, ne až za pět vteřin.
        typingSentAt = null

        // Koncept smaže TDLib sám (`clearDraft` při odeslání). Čekající uložení by ho vrátilo zpátky
        // se starým textem, takže se musí zahodit; obrazovka pole vyprázdní bez `onDraftChanged`.
        draftSave?.cancel()
        draftSave = null
        draftText = ""

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
                repository.send(chatId, trimmed, replyTo, linkPreview, quote, options, mentions)
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
            repository.send(chatId, trimmed, replyTo, linkPreview, quote, options, mentions)
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
    fun onDraftChanged(text: String) {
        // Při úpravě v poli není koncept, ale text staré zprávy. Uložit ho jako koncept by přepsalo
        // ten odložený, a ohlašovat „píše…" kvůli opravě překlepu by protějšek mátlo.
        if (_state.value.editing != null) return
        // Psaní zavře seznam příkazů otevřený tlačítkem; napovídání „/…" se řídí textem samo.
        if (_state.value.commandsOpen) _state.update { it.copy(commandsOpen = false) }

        draftText = text
        // Vyprázdněné pole = zpráva, ke které zmínky patřily, je pryč.
        if (text.isBlank()) nameMentions.clear()
        scheduleDraftSave()
        refreshLinkPreview(text)

        if (text.isBlank()) {
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
        val text = draftText
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
        text: String,
        replyTo: Long?,
        linkPreview: LinkPreviewOptions? = null,
        quote: ReplyQuote? = null,
    ) {
        repository.saveDraft(chatId, text, replyTo, linkPreview, quote).onFailure { failure ->
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
    private fun refreshLinkPreview(text: String) {
        linkPreviewJob?.cancel()
        if (text.isBlank()) {
            resetLinkPreview()
            return
        }
        if (_state.value.linkPreviewOptions.disabled) return
        linkPreviewJob = viewModelScope.launch {
            delay(LINK_PREVIEW_DELAY_MS)
            val preview = repository.linkPreview(text).getOrElse { failure ->
                // Bez náhledu se dá psát i poslat dál; server si ho k odeslané zprávě vyrobí sám.
                logger.w(failure) { "Náhled odkazu se nepodařilo zjistit." }
                return@launch
            }
            _state.update {
                it.copy(
                    linkPreview = preview,
                    linkPreviewOptions = it.linkPreviewOptions.forPreview(previous = it.linkPreview, next = preview),
                )
            }
        }
    }

    private fun resetLinkPreview() {
        linkPreviewJob?.cancel()
        linkPreviewJob = null
        _state.update { it.copy(linkPreview = null, linkPreviewOptions = LinkPreviewOptions()) }
    }

    /** Uživatel změnil volbu náhledu (velikost, poloha, ✕). Patří ke konceptu, ukládá se s ním. */
    fun setLinkPreviewOptions(options: LinkPreviewOptions) {
        _state.update { it.copy(linkPreviewOptions = options) }
        scheduleDraftSave()
    }

    /** Obrazovka text do pole vložila. Bez toho by se po otočení vložil znovu, i smazaný. */
    fun consumeInputFill() {
        _state.update { it.copy(inputFill = null) }
    }

    /**
     * Přepne vstupní pole do úpravy zprávy.
     *
     * Rozepsaný text se **odloží a po skončení úpravy vrátí**. Úprava je vsuvka — kdo si
     * uprostřed psaní všimne překlepu v minulé zprávě, nechce kvůli tomu přijít o rozepsanou.
     */
    private fun startEdit(messageId: Long) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        // Přepnutí z jedné úpravy do druhé nesmí přepsat odložený koncept textem první úpravy.
        if (_state.value.editing == null) draftBeforeEdit = draftText

        _state.update {
            it.copy(
                editing = message,
                messageMenu = null,
                inputFill = InputFill(message.text, onlyIfEmpty = false),
            )
        }
    }

    /** Zruší úpravu a vrátí do pole odložený koncept. */
    fun cancelEdit() {
        if (_state.value.editing == null) return
        leaveEdit()
    }

    private fun leaveEdit() {
        draftText = draftBeforeEdit
        _state.update {
            it.copy(editing = null, inputFill = InputFill(draftBeforeEdit, onlyIfEmpty = false))
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
    private fun sendEdit(target: ChatMessage, text: String) {
        val kind = target.plainTextEdit() ?: return
        if (kind == PlainTextEdit.Text && text.isEmpty()) return

        leaveEdit()
        // Beze změny není co posílat; server by vrátil chybu „zpráva se nezměnila".
        if (text == target.text.trim()) return

        viewModelScope.launch {
            val edited = when (kind) {
                PlainTextEdit.Text -> repository.editText(chatId, target.id, text)
                PlainTextEdit.Caption -> repository.editCaption(chatId, target.id, text)
            }
            edited.onFailure { failure ->
                if (_state.value.editing == null) draftBeforeEdit = draftText
                _state.update {
                    it.copy(
                        error = failure.reported(Res.string.conversation_edit_failed),
                        editing = target,
                        inputFill = InputFill(text, onlyIfEmpty = false),
                    )
                }
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
        voicePlayer.stop(chatId)
        // Rozpracovaná nahrávka se při odchodu zahazuje — neposílat nic, co uživatel neodeslal.
        voiceRecorder.cancel()

        val pending = draftSave ?: return
        if (!pending.isActive) return
        pending.cancel()

        val text = draftText
        val replyTo = _state.value.replyingTo?.id
        val quote = _state.value.replyQuote
        val linkPreview = _state.value.linkPreviewOptions
        appScope.launch { saveDraft(text, replyTo, linkPreview, quote) }
    }

    private fun reportTyping(typing: Boolean) {
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
                        is InternalLink.Chat ->
                            if (link.chatId != chatId) _state.update { it.copy(openChat = ChatTarget(link.chatId)) }
                        is InternalLink.Message -> when {
                            link.chatId == chatId -> link.messageId?.let { jumpToMessage(it, fromSearch = true) }
                            else -> _state.update { it.copy(openChat = ChatTarget(link.chatId, link.messageId)) }
                        }
                        is InternalLink.Invite -> _state.update { it.copy(invite = InviteSheetState(link.link, link.preview)) }
                        is InternalLink.StickerSet -> _state.update { it.copy(openStickerSet = link.setId) }
                        is InternalLink.BotStart -> openBotStart(link)
                    }
                }
                .onFailure { failure ->
                    val message = failure.reported(Res.string.conversation_link_failed)
                    _state.update { it.copy(error = message) }
                }
        }
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
        if (link.chatId != chatId) _state.update { it.copy(openChat = ChatTarget(link.chatId)) }
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
            BotButtonKind.Disabled -> Unit
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
    fun consumeOpenPollComposer() {
        _state.update { it.copy(openPollComposer = null) }
    }

    fun dismissPollVoters() {
        _state.update { it.copy(pollVoters = null) }
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
            _state.update { it.copy(openChat = ChatTarget(senderId)) }
        }
    }

    /** Přidat se přes pozvánku z náhledu. Po přidání se chat otevře. */
    fun joinInvite() {
        val invite = _state.value.invite?.takeUnless { it.joining } ?: return
        _state.update { it.copy(invite = invite.copy(joining = true)) }
        viewModelScope.launch {
            chatRepository.joinByInvite(invite.link)
                .onSuccess { result ->
                    _state.update {
                        when (result) {
                            is InviteJoinResult.Joined -> it.copy(invite = null, openChat = ChatTarget(result.chatId))
                            InviteJoinResult.Requested -> it.copy(invite = null, notice = UiText(Res.string.invite_request_sent))
                        }
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
                .onSuccess { target -> if (target != chatId) _state.update { it.copy(openChat = ChatTarget(target)) } }
                .onFailure { failure ->
                    val message = failure.reported(Res.string.conversation_mention_not_found)
                    _state.update { it.copy(error = message) }
                }
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
                origin.chatId != chatId -> _state.update { it.copy(openChat = ChatTarget(origin.chatId, origin.messageId)) }
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
     * skladba a kolečko hrají v chatu, jako po ťuknutí na ně.
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
                voicePlayer.play(VoiceKey(chatId, target.id), raiseToEar = false, startMs = startMs) { filePath(media.fileId) }
            }
            is MessageMedia.VideoNote -> {
                voicePlayer.stop()
                _state.update { it.copy(soundingVideoNote = target.id, soundingVideoNoteFromMs = startMs) }
                if (!target.isOutgoing && !media.isViewed) reportListened(target.id)
            }
            // Video v náhledu odkazu (YouTube…) v aplikaci nepřehráváme; čas nemá kam vést.
            else -> Unit
        }
    }

    fun consumeOpenMediaAt() {
        _state.update { it.copy(openMediaAt = null) }
    }

    fun consumeOpenChat() {
        _state.update { it.copy(openChat = null) }
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
            // Formulář je obrazovka nad konverzací; otevírá ho obrazovka, ViewModel jen řekne jaký.
            is ReplyKeyboardButtonKind.Poll -> _state.update { it.copy(openPollComposer = kind.mode) }
            is ReplyKeyboardButtonKind.Unsupported ->
                _state.update { it.copy(notice = UiText(kind.feature.explanation())) }
        }
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
            loadInlinePage(bot, draft.query, offset = "")
        }
    }

    /**
     * „přes @bot" ťuknuté u zprávy: do pole se dá `@bot `, a výsledky se tím rovnou načtou.
     *
     * Rozepsanou zprávu to NEPŘEPÍŠE — ťuknutí na značku není důvod přijít o text. Přepíše se jen
     * prázdné pole nebo jiný rozepsaný dotaz na bota; jinak hláška, proč se nic nestalo.
     */
    private fun useInlineBot(username: String) {
        if (!_state.value.canWrite || _state.value.editing != null) return
        if (draftText.isNotBlank() && inlineQueryOf(draftText) == null) {
            _state.update { it.copy(notice = UiText(Res.string.conversation_via_bot_draft_busy)) }
            return
        }
        // Dotaz, ne text pro čtenáře: `@bot ` a mezera, za kterou se píše.
        val query = "@$username "
        _state.update { it.copy(inputFill = InputFill(query, onlyIfEmpty = false)) }
        onDraftChanged(query)
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

    /** Poloha pro bota, který o ni stojí. Dotaz se s ní zopakuje. */
    fun setInlineLocation(location: GeoPoint?) {
        if (location == null || location == inlineLocation) return
        inlineLocation = location
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
                _state.update { it.copy(notice = UiText(Res.string.bot_unsupported_web_app)) }
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

    private fun ChatReplyMarkup.replyTarget(): Long? = messageId.takeIf { _state.value.info?.isGroup == true }

    /**
     * Jednorázová klávesnice se po použití ohlásí TDLib (`deleteChatReplyMarkup`) — chce to po
     * klientovi výslovně. Klávesnice tím NEZMIZÍ: podle Bot API se jen schová a zůstává
     * dostupná, což dělá obrazovka sbalením panelu.
     */
    private fun keyboardUsed(keyboard: ChatReplyMarkup.Keyboard) {
        if (!keyboard.oneTime) return
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
        if (draftText.isNotBlank()) scheduleDraftSave()
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
                if (draftText.isNotBlank()) scheduleDraftSave()
            }
        }
    }

    /** Přidá do ankety odpověď z dialogu. Nová odpověď přijde úpravou obsahu zprávy. */
    fun addPollOption(text: String) {
        val messageId = _state.value.addingPollOption ?: return
        val trimmed = text.trim()
        _state.update { it.copy(addingPollOption = null) }
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.addPollOption(chatId, messageId, trimmed).onFailure { failure ->
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
        if (draftText.isNotBlank()) scheduleDraftSave()
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
     * Přeloží text zprávy do jazyka aplikace ([platformLanguageCode]) a ukáže ho v sheetu.
     *
     * Sheet se otevře hned s načítáním — překlad jde přes server a trvá. Selhání zůstane v sheetu
     * jako věta, ne snackbar: pod modálním sheetem by snackbar nebyl vidět.
     */
    private fun showTranslation(messageId: Long) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        val language = platformLanguageCode
        _state.update { it.copy(messageMenu = null, translation = TranslationSheet(message, language)) }

        viewModelScope.launch {
            val result = repository.translateMessage(chatId, messageId, language)
            _state.update { state ->
                // Mezitím mohl uživatel sheet zavřít nebo otevřít u jiné zprávy.
                val open = state.translation?.takeIf { it.message.id == messageId } ?: return@update state
                state.copy(
                    translation = result.fold(
                        onSuccess = { open.copy(body = it) },
                        onFailure = { failure ->
                            logger.w(failure) { "Zprávu se nepodařilo přeložit." }
                            open.copy(failed = true)
                        },
                    ),
                )
            }
        }
    }

    fun dismissTranslation() {
        _state.update { it.copy(translation = null) }
    }

    /**
     * Zkopíruje překlad a sheet zavře. Potvrzení jde snackbarem až po zavření — pod modálním
     * sheetem by vidět nebylo (stejně jako u odkazu v „Informace").
     */
    fun copyTranslation() {
        val text = _state.value.translation?.body?.text ?: return
        viewModelScope.launch { clipboard.copy(text) }
        _state.update { it.copy(translation = null, notice = UiText(Res.string.translation_copied)) }
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
            is MessageAction.ShowMenu -> showMessageMenu(messageId, action.items)
            MessageAction.CloseMenu -> closeMenus()
            is MessageAction.React -> toggleReaction(messageId, action.emoji)
            MessageAction.ShowReactions -> showReactionDetails(messageId)
            MessageAction.ToggleReadReceipt -> toggleReadReceipt(messageId)
            MessageAction.SwipeReply, MessageMenuAction.Reply -> replyTo(messageId)
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
            is MessageAction.SendBotCommand -> sendBotCommand(action.command)
            is MessageAction.UseInlineBot -> useInlineBot(action.username)
            is MessageAction.OpenSenderProfile -> openSenderProfile(action.userId)
            is MessageAction.OpenForwardOrigin -> openForwardOrigin(action.origin)
            MessageAction.OpenComments -> openComments(messageId)
            MessageAction.OpenDocument -> openDocument(messageId)
            MessageAction.CancelUpload -> cancelUpload(messageId)
            MessageAction.ExplainPaidMedia -> _state.update { it.copy(notice = UiText(Res.string.paid_media_unsupported)) }
            MessageAction.ToggleVoice -> toggleVoice(messageId)
            MessageAction.ToggleVideoNoteSound -> toggleVideoNoteSound(messageId)
            is MessageAction.PlayMediaAt -> playMediaAt(messageId, action.seconds)
            MessageAction.VideoNoteSoundEnded -> _state.update {
                if (it.soundingVideoNote == messageId) it.copy(soundingVideoNote = null) else it
            }
            MessageAction.CycleVoiceSpeed -> voicePlayer.cycleSpeed()
            is MessageAction.SeekVoice -> seekVoice(messageId, action.fraction)
            MessageMenuAction.Edit -> startEdit(messageId)
            MessageMenuAction.Copy -> copyMessage(messageId)
            is MessageAction.QuoteReply -> quoteReply(messageId, action.text)
            is MessageAction.CopyText -> copyText(action.text)
            MessageMenuAction.ToggleFavoriteSticker -> toggleFavoriteSticker(messageId)
            MessageMenuAction.Forward -> requestForward(messageId)
            MessageMenuAction.Info -> showMessageInfo(messageId)
            MessageMenuAction.Translate -> showTranslation(messageId)
            MessageMenuAction.SelectText -> selectText(messageId)
            MessageMenuAction.Pin -> requestPin(messageId)
            MessageMenuAction.Unpin -> unpinFromMenu(messageId)
            MessageMenuAction.Delete -> requestDelete(messageId)
            MessageMenuAction.Select -> startSelection(messageId)
            MessageMenuAction.Report -> reportMessage(messageId)
            MessageAction.ToggleSelection -> toggleSelection(messageId)
            MessageMenuAction.ShareFile -> shareDocument(messageId)
            MessageAction.OpenMedia, is MessageAction.OpenPollMedia, is MessageAction.OpenStickerSet,
            is MessageAction.SearchHashtag,
            MessageMenuAction.SaveFileAs ->
                logger.w { "Akce $action patří obrazovce, do ViewModelu nemá chodit." }
        }
    }

    /** Nabídka s kandidáty, které nabídl blok; co z nich server dovolí, se zjistí tady. */
    private fun showMessageMenu(messageId: Long, items: List<MessageMenuAction>) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return

        viewModelScope.launch {
            val sticker = message.media as? MessageMedia.Sticker

            val (emoji, actions, favorite) = coroutineScope {
                val emojiTask = async { repository.availableReactions(chatId, messageId) }
                val actionsTask = async { repository.messageActions(chatId, messageId) }
                // Oblíbenost jen u samolepky; seznam oblíbených je krátký a TDLib ho má lokálně.
                val favoriteTask = async {
                    sticker?.let { s ->
                        stickerRepository.favoriteStickers().first().any { it.sendFileId == s.stickerFileId }
                    }
                }
                Triple(
                    emojiTask.await().getOrDefault(emptyList()),
                    actionsTask.await().getOrElse { failure ->
                        logger.w(failure) { "Příznaky zprávy $messageId se nepodařilo zjistit." }
                        MessageActions.Fallback
                    },
                    favoriteTask.await(),
                )
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
                        items = items,
                        favoriteSticker = favorite,
                    ),
                )
            }
        }
    }

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
        _state.update { it.copy(messageMenu = null) }
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

    /** Zavře pruh proti spamu napořád. */
    fun dismissSpamBar() {
        viewModelScope.launch {
            chatRepository.dismissSpamBar(chatId).onFailure { failure ->
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
            voicePlayer.toggle(VoiceKey(chatId, messageId), raiseToEar = false) { filePath(audio.fileId) }
            return
        }
        val voice = message.media as? MessageMedia.VoiceNote ?: return

        // Zvuk hraje vždycky jen jeden: hlasovka umlčí kolečko a naopak.
        _state.update { it.copy(soundingVideoNote = null) }
        voicePlayer.toggle(VoiceKey(chatId, messageId)) { voicePath(voice) }
        if (!message.isOutgoing && !voice.isListened) reportListened(messageId)
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

    /** Označí hlasovku za poslechnutou lokálně i na serveru. */
    private fun reportListened(messageId: Long) {
        markListened(messageId)
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
        voicePlayer.stop()
        _state.update { it.copy(soundingVideoNote = null) }
        voiceRecorder.start().onFailure {
            _state.update { it.copy(error = UiText(Res.string.conversation_recording_start_failed)) }
        }
    }

    fun cancelRecording() {
        voiceRecorder.cancel()
    }

    /**
     * Ukončí nahrávání a hlasovku odešle — jako samolepka: bez letu ze vstupního pole, rozepsaný
     * text zůstává, citace se spotřebuje. Příliš krátká nahrávka se zahodí s vysvětlením.
     */
    fun sendRecording() {
        // Dvojí ťuknutí na odeslání: druhé by našlo nahrávání už ukončené a hlásilo nesmysl.
        if (voiceRecorder.state.value == null) return
        viewModelScope.launch {
            val tooShort = (voiceRecorder.state.value?.elapsedMs ?: 0L) < MIN_DURATION_MS
            val recorded = voiceRecorder.finish()
            if (recorded == null) {
                _state.update {
                    if (tooShort) it.copy(notice = UiText(Res.string.conversation_recording_too_short))
                    else it.copy(error = UiText(Res.string.conversation_recording_finish_failed))
                }
                return@launch
            }
            sendMedia(Res.string.conversation_voice_send_failed) { replyTo, quote ->
                repository.sendVoiceNote(chatId, recorded.path, recorded.durationSeconds, recorded.waveform, replyTo, quote)
            }
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
        // Skladby jdou jako playlist: po dohrání další novější skladba v chatu, poslechnutá
        // nebo ne (u hudby „poslechnuto" neexistuje). Hlasovky se hudbou neřetězí a naopak.
        val finished = _state.value.messages.firstOrNull { it.id == after }
        if (finished?.media is MessageMedia.Audio) {
            val next = _state.value.messages
                .filter { it.id > after && it.media is MessageMedia.Audio }
                .minByOrNull { it.id } ?: return
            val audio = next.media as? MessageMedia.Audio ?: return
            voicePlayer.play(VoiceKey(chatId, next.id), raiseToEar = false) { filePath(audio.fileId) }
            return
        }
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
)

/** Video k otevření v prohlížeči od [seconds] (čas v médiu). */
data class MediaAt(val messageId: Long, val seconds: Int)

/** Otevřený náhled pozvánky; [joining] = přidávání běží a tlačítko je neaktivní. */
data class InviteSheetState(val link: String, val preview: InvitePreview, val joining: Boolean = false)

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

/**
 * Otevřený překlad zprávy do jazyka [languageCode]. [body] je `null`, dokud překlad nedorazí;
 * [failed] = server ho nevydal.
 */
data class TranslationSheet(
    val message: ChatMessage,
    val languageCode: String,
    val body: MessageBody? = null,
    val failed: Boolean = false,
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
 * zprávy a ostatní fotky by jinak zůstaly v počtu nepřečtených napořád. Vlastní zprávu jen
 * tehdy, když nese nepřečtené reakce nebo hlasy v anketě — přečte je právě zhlédnutí.
 */
internal fun ConversationState.messagesToView(visibleKeys: Set<Long>): List<Long> {
    val albums = messages
        .filter { keyOf(it) in visibleKeys }
        .map { it.mediaAlbumId }
        .filter { it != 0L }
        .toSet()
    return messages
        .filter {
            (!it.isOutgoing || it.hasUnreadReactions || it.hasUnreadPollVotes) &&
                (keyOf(it) in visibleKeys || it.mediaAlbumId in albums)
        }
        .map { it.id }
}

data class ConversationState(
    val info: ChatInfo? = null,
    /** Vlákno komentářů (kořenová zpráva); `null` u běžné konverzace. */
    val threadRootId: Long? = null,
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
    /** Režim výběru víc zpráv; `null` = vypnutý (běžná konverzace). */
    val selection: MessageSelection? = null,
    /** Přílohy vybrané k odeslání, čekající nad vstupním polem na popisek a odeslání. */
    val attachments: List<OutgoingAttachment> = emptyList(),
    /** Krátké potvrzení akce, která v konverzaci nic viditelného nezmění (snackbar). */
    val notice: UiText? = null,
    /** Právě odepnutá zpráva — obrazovka k ní ukáže snackbar s „Vrátit". */
    val unpinned: ChatMessage? = null,
    /** Zpráva, jejíž text se právě upravuje ve vstupním poli; `null` při běžném psaní. */
    val editing: ChatMessage? = null,
    /** Náhled odkazu v rozepsaném textu, jak ho vrátil server; `null`, když text odkaz nemá. */
    val linkPreview: LinkPreview? = null,
    /** Co uživatel s náhledem udělal (vypnul, velikost, poloha). Patří ke konceptu. */
    val linkPreviewOptions: LinkPreviewOptions = LinkPreviewOptions(),
    /** Zpráva krátce zvýrazněná po skoku na citaci; `null` většinu času. */
    val highlightedMessageId: Long? = null,
    /** Skok na nenačtenou zprávu, která se právě načítá (viz [PendingJump]). */
    val pendingJump: PendingJump? = null,
    /** Hledání v chatu; `null` = zavřené (hlavička ukazuje chat, dole je vstupní pole). */
    val search: InChatSearch? = null,
    /** Jednorázová událost: otevřít chat ze zmínky nebo odkazu, případně se skokem na zprávu. */
    val openChat: ChatTarget? = null,
    /** Jednorázová událost: otevřít profil chatu (odesílatele ze skupiny). */
    val openProfile: Long? = null,
    /** Výběr člena pro hledání podle odesílatele; `null` = zavřený. */
    val memberPicker: MemberPicker? = null,
    /** Náhled chatu z pozvánky; `null` = zavřený. */
    val invite: InviteSheetState? = null,
    /** Vlastní klávesnice bota nebo vynucená odpověď v chatu; `null` = žádná. */
    val replyMarkup: ChatReplyMarkup? = null,
    /** Příkazy botů v chatu — napovídání po „/" a seznam z tlačítka u pole; prázdné = žádní boti. */
    val botCommands: List<BotCommandItem> = emptyList(),
    /** Seznam příkazů otevřený tlačítkem u pole (bez ohledu na to, co je v poli). */
    val commandsOpen: Boolean = false,
    /** Po kolika sekundách se nové zprávy chatu samy mažou; 0 = nemažou (odznak v hlavičce). */
    val autoDeleteTime: Int = 0,
    /** Rozepsaný dotaz na inline bota (`@gif kočka`) a jeho výsledky; `null` = žádný. */
    val inline: InlineQueryState? = null,
    /** Napovídání zmínky (`@ja`): členové k dotazu u kurzoru; `null` = nic se nenapovídá. */
    val mentionSuggestions: MentionSuggestions? = null,
    /** Tlačítko bota chce sdílet telefonní číslo a čeká se na potvrzení. */
    val confirmSharePhone: Boolean = false,
    /** Odkaz z tlačítka bota, který čeká na potvrzení; `null` = žádný. */
    val confirmLink: String? = null,
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
    /** Zpráva s anketou, do které se přidává odpověď (dialog); `null` = zavřený. */
    val addingPollOption: Long? = null,
    /** Anketa, kterou uživatel chce ukončit a čeká se na potvrzení; `null` = žádná. */
    val pendingStopPoll: Long? = null,
    /** Sheet výběru kontaktu ze sponky; `null` = zavřený. */
    val contactPicker: ContactPicker? = null,
    /** Kontakt, který čeká na potvrzení odeslání (jméno a číslo); `null` = žádný. */
    val pendingContact: MessageMedia.Contact? = null,
    /** Jednorázová událost: tlačítko bota chce formulář nové ankety (v tomhle režimu). */
    val openPollComposer: PollComposerMode? = null,
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
    UnsupportedBotButton.WebApp -> Res.string.bot_unsupported_web_app
    UnsupportedBotButton.LoginUrl -> Res.string.bot_unsupported_login_url
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
    UnsupportedKeyboardButton.Location -> Res.string.bot_unsupported_location
    UnsupportedKeyboardButton.Users, UnsupportedKeyboardButton.Chat -> Res.string.bot_unsupported_pick_chat
    UnsupportedKeyboardButton.WebApp -> Res.string.bot_unsupported_web_app
    UnsupportedKeyboardButton.ManagedBot -> Res.string.bot_unsupported_managed_bot
}

/** Jak dlouho se po vynucené odpovědi čeká, než zpráva bota dorazí do seznamu. */
private const val FORCE_REPLY_WAIT = 3_000L

/**
 * Spotřebovala [message] rozepsanou odpověď? Ano, když je to VLASTNÍ anketa, která se právě
 * odesílá (`Sending` — z jiného zařízení přijde už odeslaná) a odpovídá na tutéž zprávu.
 *
 * Formulář ankety je jiná obrazovka s vlastním ViewModelem a výsledek konverzaci nepředává;
 * konverzace si ho pozná sama z příchozí zprávy. Odpověď se nezahazuje hned při otevření
 * formuláře — kdo ho zavře, má ji mít dál.
 */
internal fun ConversationState.pollConsumesReply(message: ChatMessage): Boolean {
    val reply = replyingTo ?: return false
    return message.poll != null &&
        message.isOutgoing &&
        message.sendState == SendState.Sending &&
        message.replyToMessageId == reply.id
}


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
