package eu.tintera.plume.features.conversation

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
import eu.tintera.plume.core.files.FileOpener
import eu.tintera.plume.core.files.MediaSharer
import eu.tintera.plume.core.files.SaveAsLauncher
import eu.tintera.plume.core.share.ShareRequests
import eu.tintera.plume.core.share.SharedContent
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.settings.ExpressionMode
import eu.tintera.plume.core.telegram.ChatActivity
import eu.tintera.plume.core.telegram.ChatInfo
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ConnectionRepository
import eu.tintera.plume.core.telegram.ConnectionState
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.MessageActions
import eu.tintera.plume.core.telegram.MessageInfo
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.MessageReaction
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.Presence
import eu.tintera.plume.core.telegram.ReactionDetail
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
import eu.tintera.plume.resources.conversation_attachments_limit
import eu.tintera.plume.resources.conversation_attachments_send_failed
import eu.tintera.plume.resources.conversation_cancel_send_failed
import eu.tintera.plume.resources.conversation_delete_failed
import eu.tintera.plume.resources.conversation_edit_failed
import eu.tintera.plume.resources.conversation_favorite_added
import eu.tintera.plume.resources.conversation_favorite_failed
import eu.tintera.plume.resources.conversation_favorite_removed
import eu.tintera.plume.resources.conversation_history_failed
import eu.tintera.plume.resources.conversation_pin_failed
import eu.tintera.plume.resources.conversation_quote_deleted
import eu.tintera.plume.resources.conversation_quote_too_deep
import eu.tintera.plume.resources.conversation_reaction_failed
import eu.tintera.plume.resources.conversation_recording_finish_failed
import eu.tintera.plume.resources.conversation_recording_start_failed
import eu.tintera.plume.resources.conversation_recording_too_short
import eu.tintera.plume.resources.conversation_repin_failed
import eu.tintera.plume.resources.conversation_send_failed
import eu.tintera.plume.resources.conversation_sticker_send_failed
import eu.tintera.plume.resources.conversation_unpin_failed
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
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

/**
 * Stav a stránkování jedné konverzace.
 *
 * Zprávy se drží **seřazené od nejnovější po nejstarší**, protože stejně je vykresluje
 * `LazyColumn(reverseLayout = true)` — nejnovější dole, stránkování nahoru. Kdyby se držely
 * opačně, každé přidání staré stránky by znamenalo přepočet celého seznamu.
 */
class ConversationViewModel(
    private val chatId: Long,
    private val repository: ConversationRepository,
    /**
     * Jen kvůli „píše…". Činnost v chatu už sleduje seznam chatů — i s odpočtem, který ji zhasne,
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
    audioRecorder: AudioRecorder,
    connection: ConnectionRepository,
) : ViewModel() {

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

    private val _state = MutableStateFlow(ConversationState())
    val state: StateFlow<ConversationState> = _state.asStateFlow()

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

    /** Běžící otevírání souborů podle `fileId`; druhé ťuknutí to čekání zruší. */
    private val documentJobs = mutableMapOf<Int, Job>()

    /** Už nahlášená zhlédnutí reklam — každé se hlásí jen jednou. */
    private val reportedSponsoredViews = mutableSetOf<Long>()

    init {
        viewModelScope.launch {
            voicePlayer.errors.collect { message -> _state.update { it.copy(error = message) } }
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
                    )
                }
                info.draftText?.let { draftText = it }
                info.draftReplyToMessageId?.let(::restoreDraftReply)
                loadUntilUnread()
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
                        state.copy(
                            messages = merge(state.messages, page),
                            loading = false,
                            reachedStart = page.isEmpty(),
                            error = null,
                        )
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

    /**
     * Dotáhne historii tak daleko, aby v ní byla hranice nepřečtených, a pak na ni skočí.
     *
     * Načítá se **od nejnovější dozadu**, ne od hranice dopředu. TDLib by uměl i to (záporný
     * `offset` v `getChatHistory`), ale stránkování by pak muselo umět oba směry: kotva uprostřed
     * znamená, že se dá dojít i na konec toho, co je načtené, směrem k novějším. Za tu složitost
     * to nestojí — pár stránek zpátky je z lokální databáze rychlých.
     *
     * Proto je tu [MAX_UNREAD_PAGES]. Kdo má v chatu stovky nepřečtených, skok nedostane
     * a chová se to jako dosud; načítat kvůli tomu půlku historie by bylo horší než nepomoct.
     */
    private suspend fun loadUntilUnread() {
        val boundary = _state.value.unreadBoundaryId ?: return

        // Hranice je načtená, jakmile máme aspoň jednu zprávu z přečtené části. Bez ní se předěl
        // stejně nevykreslí, takže skok nemá kam mířit.
        if (!loadUntil(MAX_UNREAD_PAGES) { messages -> messages.any { it.id <= boundary } }) return

        // Seznam jde od nejnovější, takže první nepřečtená je ta POSLEDNÍ nad hranicí.
        val firstUnread = _state.value.messages.lastOrNull { it.id > boundary } ?: return
        _state.update { it.copy(scrollTarget = ScrollTarget(firstUnread.id, ScrollAnchor.Top)) }
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
     * Skočí na citovanou zprávu a krátce ji zvýrazní.
     *
     * Citace míří často na zprávu, která už je načtená — pak je skok okamžitý. Jinak se historie
     * dotahuje, stejně jako u skoku na nepřečtené, a stejně tak se stropem: odpověď na zprávu
     * stará tisíce zpráv nestojí za to, aby se kvůli ní načítala půlka chatu.
     *
     * Zvýraznění je nutné. Po skoku uprostřed historie by jinak nebylo poznat, na kterou
     * z bublin na obrazovce citace mířila.
     */
    fun jumpToMessage(messageId: Long) {
        viewModelScope.launch {
            if (!loadUntil(MAX_JUMP_PAGES) { messages -> messages.any { it.id == messageId } }) {
                _state.update {
                    it.copy(
                        error = if (it.reachedStart) {
                            // Celá historie je načtená a zpráva v ní není — byla smazaná.
                            UiText(Res.string.conversation_quote_deleted)
                        } else {
                            UiText(Res.string.conversation_quote_too_deep)
                        },
                    )
                }
                return@launch
            }

            _state.update {
                it.copy(
                    scrollTarget = ScrollTarget(messageId, ScrollAnchor.Center),
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
        _state.update { it.copy(scrollTarget = null) }
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
                resolveMissingQuotes()
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

        viewModelScope.launch {
            repository.reactionUpdates(chatId).collect { update ->
                _state.update { state ->
                    state.copy(
                        messages = state.messages.map { message ->
                            if (message.id == update.messageId) {
                                message.copy(reactions = update.reactions)
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
            chatRepository.chats
                .map { list ->
                    (list as? ChatListState.Ready)?.chats?.firstOrNull { it.id == chatId }?.activity
                }
                .distinctUntilChanged()
                .collect { activity -> _state.update { it.copy(activity = activity) } }
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
        sendMedia(Res.string.conversation_sticker_send_failed) { replyTo ->
            repository.sendSticker(chatId, sticker, replyTo)
        }
    }

    /**
     * Přílohy z výběru se **neposílají hned**, ale čekají nad vstupním polem: uživatel k nim napíše
     * popisek, některou odebere nebo přidá další, a teprve odeslání je pošle (jednu jako zprávu,
     * víc jako album). Pravidla skládání viz [stageAttachments].
     */
    fun addAttachments(picked: List<OutgoingAttachment>) {
        val staged = stageAttachments(_state.value.attachments, picked)
        _state.update {
            it.copy(
                attachments = staged.attachments,
                notice = if (staged.overflow) UiText(Res.string.conversation_attachments_limit, MAX_ATTACHMENTS) else it.notice,
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
     * Odešle připravené přílohy s popiskem.
     *
     * Jako odeslání textu: koncept se zahodí, citace se spotřebuje, „píšu" se zapomene. Na rozdíl od
     * textové zprávy TDLib u příloh koncept sám nesmaže, takže se maže výslovně. Bez letu ze vstupního
     * pole — přílohy z pole nevyletěly. Když odeslání selže, přílohy se vrátí nad pole, ať se nemusí
     * vybírat znovu.
     */
    private fun sendAttachments(caption: String, attachments: List<OutgoingAttachment>) {
        typingSentAt = null
        draftSave?.cancel()
        draftSave = null
        draftText = ""

        val replyTo = _state.value.replyingTo?.id
        clearReply()
        _state.update { it.copy(attachments = emptyList()) }

        viewModelScope.launch {
            repository.sendAttachments(chatId, attachments, caption, replyTo)
                .onSuccess { pending ->
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

    /** Hláška z platformy (výběr souboru selhal) do snackbaru. */
    fun reportError(message: UiText) {
        _state.update { it.copy(error = message) }
    }

    private fun sendMedia(failureMessage: StringResource, send: suspend (replyTo: Long?) -> Result<ChatMessage>) {
        val replyTo = _state.value.replyingTo?.id
        clearReply()

        viewModelScope.launch {
            send(replyTo)
                .onSuccess { pending ->
                    // Potvrzení mohlo dorazit dřív než výsledek; `withMessage` to ošetří.
                    _state.update { it.withMessage(pending) }
                }
                .onFailure { failure ->
                    _state.update { it.copy(error = failure.reported(failureMessage)) }
                }
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()

        // S přílohami je text popisek a smí být prázdný. Úprava zprávy přílohy nemá.
        val attachments = _state.value.attachments
        if (attachments.isNotEmpty() && _state.value.editing == null) {
            sendAttachments(trimmed, attachments)
            return
        }

        if (trimmed.isEmpty()) return

        _state.value.editing?.let { target ->
            sendEdit(target, trimmed)
            return
        }

        // Odeslaná zpráva psaní ukončí sama; další úhoz má „píšu" ohlásit hned, ne až za pět vteřin.
        typingSentAt = null

        // Koncept smaže TDLib sám (`clearDraft` při odeslání). Čekající uložení by ho vrátilo zpátky
        // se starým textem, takže se musí zahodit; obrazovka pole vyprázdní bez `onDraftChanged`.
        draftSave?.cancel()
        draftSave = null
        draftText = ""

        val replyTo = _state.value.replyingTo?.id
        // Rozepsaná odpověď se zruší hned, ne až po potvrzení — jinak by při pomalé síti šlo
        // odeslat druhou zprávu se stejnou citací.
        clearReply()

        // Text „vyletí" ze vstupního pole do bubliny. Musí se zapamatovat hned při odeslání,
        // protože pole se vyprázdní dřív, než dorazí zpráva — a bez toho by nebylo co animovat.
        val flight = MessageFlight(id = ++flightCounter, text = trimmed, itemKey = null)
        _state.update { it.copy(flight = flight) }

        viewModelScope.launch {
            repository.send(chatId, trimmed, replyTo)
                // TDLib vrací rozepsanou zprávu okamžitě, takže bublina naskočí bez čekání
                // na server. Skutečné id dorazí potvrzením a zprávu v seznamu nahradí.
                .onSuccess { pending ->
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

        draftText = text
        scheduleDraftSave()

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

        draftSave?.cancel()
        draftSave = appScope.launch {
            delay(DRAFT_SAVE_DELAY_MS)
            saveDraft(text, replyTo)
        }
    }

    private suspend fun saveDraft(text: String, replyTo: Long?) {
        repository.saveDraft(chatId, text, replyTo).onFailure { failure ->
            logger.w(failure) { "Koncept se nepodařilo uložit." }
        }
    }

    /**
     * Doplní citaci konceptu. Zprávu hledá v načtené historii, jinak si ji vyžádá — koncept
     * může odpovídat na cokoli.
     */
    private fun restoreDraftReply(messageId: Long) {
        viewModelScope.launch {
            val message = _state.value.messages.firstOrNull { it.id == messageId }
                ?: repository.message(chatId, messageId).getOrNull()
                ?: return@launch
            // Mezitím mohl uživatel začít odpovídat na něco jiného; to má přednost.
            _state.update { if (it.replyingTo == null) it.copy(replyingTo = message) else it }
        }
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
     */
    private fun sendEdit(target: ChatMessage, text: String) {
        leaveEdit()
        // Beze změny není co posílat; server by vrátil chybu „zpráva se nezměnila".
        if (text == target.text.trim()) return

        viewModelScope.launch {
            repository.editText(chatId, target.id, text).onFailure { failure ->
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
        // Hlasovka z chatu, který se zavřel, nemá kde ukazovat průběh ani jak se zastavit.
        voicePlayer.stop(chatId)
        // Rozpracovaná nahrávka se při odchodu zahazuje — neposílat nic, co uživatel neodeslal.
        voiceRecorder.cancel()

        val pending = draftSave ?: return
        if (!pending.isActive) return
        pending.cancel()

        val text = draftText
        val replyTo = _state.value.replyingTo?.id
        appScope.launch { saveDraft(text, replyTo) }
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
     * Otevře odkaz ze zprávy.
     *
     * Vede přes ViewModel, ne rovnou z bubliny do `UrlOpener`: až budeme rozlišovat interní
     * `t.me` odkazy (pozvánka do skupiny, odkaz na zprávu), rozhodne se to tady a obrazovka
     * se měnit nebude.
     */
    private fun openLink(url: String) {
        urlOpener.open(url)
    }

    /** Nastaví zprávu, na kterou se odpovídá. Zobrazí se nad vstupním polem. */
    private fun replyTo(messageId: Long) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        _state.update { it.copy(replyingTo = message, messageMenu = null) }
        // Citace je součást konceptu — jen když už se něco píše, jinak není co ukládat.
        if (draftText.isNotBlank()) scheduleDraftSave()
    }

    fun clearReply() {
        _state.update { it.copy(replyingTo = null) }
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
        val visible = messageIds.toSet()
        val state = _state.value
        val shown = state.messages.filter { state.keyOf(it) in visible }
        // Album je jedna položka s klíčem nejstarší zprávy; za přečtené se hlásí celé, jinak by
        // ostatní fotky alba zůstaly v počtu nepřečtených napořád.
        val albums = shown.map { it.mediaAlbumId }.filter { it != 0L }.toSet()
        val incoming = state.messages
            .filter { !it.isOutgoing && (state.keyOf(it) in visible || it.mediaAlbumId in albums) }
            .map { it.id }

        if (incoming.isEmpty()) return
        viewModelScope.launch { repository.markRead(chatId, incoming) }
    }

    /**
     * Přepne reakci.
     *
     * Stav se **mění lokálně hned**, ještě než dorazí potvrzení ze serveru — jinak by mezi ťuknutím
     * a odpovědí byla viditelná prodleva a gesto by působilo rozbitě. Když volání selže, změna se
     * vrátí zpět. Server pak stejně pošle autoritativní stav přes `reactionUpdates`.
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
        _state.update { it.copy(reactionDetails = ReactionDetails(messageId = messageId)) }

        viewModelScope.launch {
            repository.reactionDetails(chatId, messageId)
                .onSuccess { details ->
                    _state.update { state ->
                        // Mezitím mohl uživatel sheet zavřít nebo otevřít u jiné zprávy.
                        val open = state.reactionDetails?.takeIf { it.messageId == messageId }
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
            is MessageAction.OpenLink -> openLink(action.url)
            MessageAction.OpenDocument -> openDocument(messageId)
            MessageAction.CancelUpload -> cancelUpload(messageId)
            MessageAction.ToggleVoice -> toggleVoice(messageId)
            is MessageAction.SeekVoice -> seekVoice(messageId, action.fraction)
            MessageMenuAction.Edit -> startEdit(messageId)
            MessageMenuAction.Copy -> copyMessage(messageId)
            MessageMenuAction.ToggleFavoriteSticker -> toggleFavoriteSticker(messageId)
            MessageMenuAction.Forward -> requestForward(messageId)
            MessageMenuAction.Info -> showMessageInfo(messageId)
            MessageMenuAction.Pin -> requestPin(messageId)
            MessageMenuAction.Unpin -> unpinFromMenu(messageId)
            MessageMenuAction.Delete -> requestDelete(messageId)
            MessageMenuAction.ShareFile -> shareDocument(messageId)
            MessageAction.OpenMedia, is MessageAction.OpenStickerSet, MessageMenuAction.SaveFileAs ->
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
        viewModelScope.launch { clipboard.copy(message.copyableText()) }
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
     * „Přeposlat" z nabídky. Obrazovka si o cíl řekne sama (otevře výběr chatu), takže se tu jen
     * zapamatuje, co se přeposílá — u alba celé album, jako u mazání.
     */
    private fun requestForward(messageId: Long) {
        _state.update { it.copy(messageMenu = null, forwardRequest = it.albumMemberIds(messageId)) }
    }

    fun consumeForwardRequest() {
        _state.update { it.copy(forwardRequest = null) }
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
        val document = _state.value.messages.firstOrNull { it.id == messageId }?.media as? MessageMedia.Document ?: return

        // Druhé ťuknutí během čekání ho zruší. Bez toho by se z rozbitého stahování (mrtvé spojení,
        // odpověď, která nedorazí) nedalo dostat ven — kolečko by se točilo napořád.
        documentJobs.remove(document.fileId)?.let { running ->
            running.cancel()
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
        val voice = message.media as? MessageMedia.VoiceNote ?: return

        voicePlayer.toggle(VoiceKey(chatId, messageId)) {
            when (val file = fileRepository.file(voice.fileId).first { it is FileState.Ready || it is FileState.Failed }) {
                is FileState.Ready -> file.path
                is FileState.Failed -> error(file.message)
                else -> error("Nedosažitelné")
            }
        }

        if (!message.isOutgoing && !voice.isListened) {
            markListened(messageId)
            viewModelScope.launch {
                repository.openContent(chatId, messageId).onFailure { failure ->
                    logger.w(failure) { "Poslechnutí hlasovky se nepodařilo nahlásit." }
                }
            }
        }
    }

    /**
     * Začne nahrávat hlasovku. Oprávnění k mikrofonu řeší obrazovka dřív, než sem dojde.
     * Hrající hlasovka se zastaví — nahrával by se i její zvuk z reproduktoru.
     */
    fun startRecording() {
        voicePlayer.stop()
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
            sendMedia(Res.string.conversation_voice_send_failed) { replyTo ->
                repository.sendVoiceNote(chatId, recorded.path, recorded.durationSeconds, recorded.waveform, replyTo)
            }
        }
    }

    private fun seekVoice(messageId: Long, fraction: Float) {
        voicePlayer.seek(VoiceKey(chatId, messageId), fraction)
    }

    private fun markListened(messageId: Long) {
        _state.update { state ->
            state.copy(
                messages = state.messages.map { message ->
                    val voice = message.media as? MessageMedia.VoiceNote
                    if (message.id == messageId && voice != null && !voice.isListened) {
                        message.copy(media = voice.copy(isListened = true))
                    } else {
                        message
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
 * Odstup ukládání konceptu od poslední úpravy.
 *
 * Koncept se propisuje na server a do ostatních zařízení, takže ne na každý úhoz. Při odchodu
 * z chatu se čekání přeskočí (`onCleared`), takže delší odstup nic neriskuje.
 */
private const val DRAFT_SAVE_DELAY_MS = 1500L

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
 * Kolik stránek historie se dotáhne kvůli skoku na citovanou zprávu.
 *
 * Víc než u nepřečtených: tam jde o pohodlí při otevření chatu, tady o výslovné ťuknutí, na jehož
 * výsledek uživatel čeká. Tři sta zpráv zpátky je pořád otázka chvilky z lokální databáze.
 */
private const val MAX_JUMP_PAGES = 10

/** Jak dlouho zůstane zpráva po skoku zvýrazněná. */
private const val HIGHLIGHT_DURATION_MS = 1500L

/** Kam na obrazovce má zpráva po skoku dosednout. */
enum class ScrollAnchor {
    /** K horní hraně — čte se od ní dolů. Skok na první nepřečtenou. */
    Top,

    /** Na střed — kolem je vidět, na co zpráva navazuje. Skok na citaci. */
    Center,
}

/** Požadavek na odrolování ke zprávě. Obrazovka ho po provedení vynuluje. */
data class ScrollTarget(
    val messageId: Long,
    val anchor: ScrollAnchor,
)

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
)

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

data class ConversationState(
    val info: ChatInfo? = null,
    /** Dostupnost protějšku pro podtitul hlavičky. `null` u skupin a kanálů. */
    val presence: Presence? = null,
    /** Co protějšek (ve skupině kdokoli) právě dělá. Má v hlavičce přednost před dostupností. */
    val activity: ChatActivity? = null,
    /** Otevřená nabídka nad zprávou (rychlé reakce + akce). */
    val messageMenu: MessageMenu? = null,
    /** Zpráva čekající na potvrzení smazání. */
    val pendingDelete: PendingDelete? = null,
    /** Zpráva čekající na potvrzení připnutí. */
    val pendingPin: PendingPin? = null,
    /** Zpráva, na kterou se právě chystá odpověď. Zobrazuje se nad vstupním polem. */
    val replyingTo: ChatMessage? = null,
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
    /** Přílohy vybrané k odeslání, čekající nad vstupním polem na popisek a odeslání. */
    val attachments: List<OutgoingAttachment> = emptyList(),
    /** Krátké potvrzení akce, která v konverzaci nic viditelného nezmění (snackbar). */
    val notice: UiText? = null,
    /** Právě odepnutá zpráva — obrazovka k ní ukáže snackbar s „Vrátit". */
    val unpinned: ChatMessage? = null,
    /** Zpráva, jejíž text se právě upravuje ve vstupním poli; `null` při běžném psaní. */
    val editing: ChatMessage? = null,
    /** Zpráva krátce zvýrazněná po skoku na citaci; `null` většinu času. */
    val highlightedMessageId: Long? = null,
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
    val error: UiText? = null,
) {
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
    fun withMessage(message: ChatMessage): ConversationState =
        if (message.id in itemKeys.values) this else copy(messages = merge(messages, listOf(message)))

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
