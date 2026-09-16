package eu.tintera.plume.features.conversation

import co.touchlab.kermit.Logger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.settings.ExpressionMode
import eu.tintera.plume.core.telegram.ChatActivity
import eu.tintera.plume.core.telegram.ChatInfo
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.MessageActions
import eu.tintera.plume.core.telegram.MessageEntityType
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.MessageReaction
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.Presence
import eu.tintera.plume.core.telegram.SendState
import eu.tintera.plume.core.telegram.SentMessageUpdate
import eu.tintera.plume.core.telegram.SponsoredMessage
import eu.tintera.plume.core.telegram.StickerItem
import eu.tintera.plume.core.telegram.StickerRepository
import eu.tintera.plume.core.ui.TextClipboard
import eu.tintera.plume.core.ui.UrlOpener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

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
) : ViewModel() {

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

    /** Koncept odložený po dobu úpravy zprávy; po ní se vrátí do pole. */
    private var draftBeforeEdit = ""

    /** Odložené uložení konceptu; každá další úprava ho posune. */
    private var draftSave: Job? = null

    /** Už nahlášená zhlédnutí reklam — každé se hlásí jen jednou. */
    private val reportedSponsoredViews = mutableSetOf<Long>()

    init {
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
                    _state.update { it.copy(error = failure.message ?: "Zprávu se nepodařilo odepnout.") }
                    refreshPinned()
                }
        }
    }

    /** „Vrátit" ze snackbaru. */
    fun undoUnpin(message: ChatMessage) {
        viewModelScope.launch {
            repository.pin(chatId, message.id).onFailure { failure ->
                _state.update { it.copy(error = failure.message ?: "Zprávu se nepodařilo znovu připnout.") }
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
                        it.copy(loading = false, error = failure.message ?: "Historii se nepodařilo načíst.")
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
                            "Citovaná zpráva už není k dispozici."
                        } else {
                            "Citovaná zpráva je příliš hluboko v historii."
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
                    it.copy(
                        messages = merge(it.messages, listOf(message)),
                        // Rozepsaná zpráva chodí tudy DŘÍV, než `send` vrátí výsledek. Let se proto
                        // musí navázat už tady, ve stejné změně stavu, ve které bublina vzniká —
                        // jinak se na snímek vykreslí na místě, zmizí a teprve pak přiletí.
                        flight = it.flight.landingOn(message),
                    )
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

                /*
                 * Klíč položky ale zůstává PŮVODNÍ. Změna klíče znamená pro `LazyColumn` jinou
                 * položku: stará by odprchla, nová naskočila a z potvrzení doručení by byl problik
                 * celé bubliny — navíc uprostřed letu ze vstupního pole, jehož cíl by zmizel.
                 */
                _state.update { state ->
                    val without = state.messages.filterNot { it.id == oldId }
                    val key = state.itemKeys[oldId] ?: oldId
                    state.copy(
                        messages = merge(without, listOf(message)),
                        itemKeys = state.itemKeys - oldId + (message.id to key),
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
        sendMedia("Samolepku se nepodařilo odeslat.") { replyTo ->
            repository.sendSticker(chatId, sticker, replyTo)
        }
    }

    /**
     * Odešle fotku nebo soubor z výběru.
     *
     * Stejně jako samolepka: bez letu ze vstupního pole, rozepsaný text zůstává, citace se spotřebuje.
     * Bublina se objeví hned s průběhem nahrávání; nahrávání samo běží v TDLib.
     */
    fun sendAttachment(attachment: OutgoingAttachment) {
        sendMedia("Přílohu se nepodařilo odeslat.") { replyTo ->
            repository.sendAttachment(chatId, attachment, replyTo)
        }
    }

    /** Hláška z platformy (výběr souboru selhal) do snackbaru. */
    fun reportError(message: String) {
        _state.update { it.copy(error = message) }
    }

    private fun sendMedia(failureMessage: String, send: suspend (replyTo: Long?) -> Result<ChatMessage>) {
        val replyTo = _state.value.replyingTo?.id
        clearReply()

        viewModelScope.launch {
            send(replyTo)
                .onSuccess { pending ->
                    _state.update {
                        it.copy(
                            // Potvrzení mohlo dorazit dřív než výsledek — viz `send`.
                            messages = if (pending.id in it.itemKeys.values) {
                                it.messages
                            } else {
                                merge(it.messages, listOf(pending))
                            },
                        )
                    }
                }
                .onFailure { failure ->
                    _state.update { it.copy(error = failure.message ?: failureMessage) }
                }
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
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
                        it.copy(
                            // Mezitím ji už mohlo dorazit i potvrzení. Vrátit do seznamu dočasnou
                            // verzi by znamenalo mít tam zprávu dvakrát.
                            messages = if (pending.id in it.itemKeys.values) {
                                it.messages
                            } else {
                                merge(it.messages, listOf(pending))
                            },
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
                            error = failure.message ?: "Zprávu se nepodařilo odeslat.",
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
    fun startEdit(messageId: Long) {
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
                        error = failure.message ?: "Zprávu se nepodařilo upravit.",
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
    fun openLink(url: String) {
        urlOpener.open(url)
    }

    /** Nastaví zprávu, na kterou se odpovídá. Zobrazí se nad vstupním polem. */
    fun replyTo(messageId: Long) {
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
    fun toggleReaction(messageId: Long, emoji: String) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        val existing = message.reactions.firstOrNull { it.emoji == emoji }
        val add = existing?.isChosen != true

        val before = message.reactions
        applyReactions(messageId, before.toggled(emoji, add))

        viewModelScope.launch {
            repository.setReaction(chatId, messageId, emoji, add).onFailure { failure ->
                applyReactions(messageId, before)
                _state.update { it.copy(error = failure.message ?: "Reakci se nepodařilo uložit.") }
            }
        }
        closeMenus()
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
    fun showMessageMenu(messageId: Long) {
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
                        isOutgoing = message.isOutgoing,
                        emoji = emoji,
                        chosenEmoji = message.reactions.filter { r -> r.isChosen }.map { r -> r.emoji }.toSet(),
                        actions = actions,
                        hasText = message.copyableText().isNotBlank(),
                        canEdit = actions.canEdit && message.isEditableAsPlainText(),
                        isPinned = _state.value.pinned.any { p -> p.id == messageId },
                        favoriteSticker = favorite,
                    ),
                )
            }
        }
    }

    /** Přepne z nabídky na plný seznam emoji. Ten se listuje, takže zůstává bottom sheetem. */
    fun showAllReactions() {
        val menu = _state.value.messageMenu ?: return
        _state.update {
            it.copy(
                messageMenu = null,
                reactionSheet = ReactionPicker(menu.messageId, menu.emoji),
            )
        }
    }

    /**
     * Přidá samolepku ze zprávy do oblíbených, nebo ji z nich odebere.
     *
     * V konverzaci se tím nic nezmění, takže výsledek hlásí snackbar — jinak by po zavření nabídky
     * nebylo poznat, že se něco stalo.
     */
    fun toggleFavoriteSticker(messageId: Long) {
        val menu = _state.value.messageMenu?.takeIf { it.messageId == messageId } ?: return
        val favorite = menu.favoriteSticker ?: return
        val sticker = _state.value.messages.firstOrNull { it.id == messageId }?.media as? MessageMedia.Sticker
            ?: return
        closeMenus()

        viewModelScope.launch {
            stickerRepository.setFavorite(sticker.stickerFileId, !favorite)
                .onSuccess {
                    _state.update {
                        it.copy(notice = if (favorite) "Odebráno z oblíbených" else "Přidáno do oblíbených")
                    }
                }
                .onFailure { failure ->
                    _state.update { it.copy(error = failure.message ?: "Oblíbené se nepodařilo změnit.") }
                }
        }
    }

    /** Obrazovka oznámení ukázala. */
    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun closeMenus() {
        _state.update { it.copy(messageMenu = null, reactionSheet = null) }
    }

    /**
     * Zkopíruje text zprávy do schránky.
     *
     * Kopíruje se text, jak ho vidí uživatel — bez entit. Formátování má smysl v Telegramu,
     * ale do cizí aplikace se stejně přenese jen holý řetězec.
     */
    fun copyMessage(messageId: Long) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        clipboard.copy(message.copyableText())
        closeMenus()
    }

    /**
     * Připnutí z nabídky.
     *
     * Ve skupině, kanálu i soukromém chatu se nejdřív zeptá — každý z nich má jednu volbu, která
     * mění, koho se to dotkne (upozornit členy / připnout i protějšku). V Uložených zprávách
     * žádná taková volba není, takže se připíná rovnou.
     */
    fun requestPin(messageId: Long) {
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
                    _state.update { it.copy(error = failure.message ?: "Zprávu se nepodařilo připnout.") }
                }
        }
    }

    /** Odepnutí z nabídky. Chová se stejně jako křížek v pruhu, včetně „Vrátit". */
    fun unpinFromMenu(messageId: Long) {
        val message = _state.value.pinned.firstOrNull { it.id == messageId }
            ?: _state.value.messages.firstOrNull { it.id == messageId }
        closeMenus()
        if (message != null) unpin(message)
    }

    /** Otevře potvrzení smazání. Nevratná akce se nedělá na jedno ťuknutí. */
    fun requestDelete(messageId: Long) {
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
                        it.copy(error = failure.message ?: "Zprávu se nepodařilo smazat.")
                    }
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
private fun List<MessageReaction>.toggled(emoji: String, add: Boolean): List<MessageReaction> {
    val existing = firstOrNull { it.emoji == emoji }

    return when {
        // Vlastní avatar u nově přidané reakce zatím neznáme — dorazí se serverovým updatem
        // o chvíli později. Optimisticky se ukáže jen počet, což je lepší než čekat na data.
        existing == null && add ->
            this + MessageReaction(emoji, count = 1, isChosen = true, recentSenders = emptyList())

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
 * Dá se zpráva upravit ve vstupním poli, aniž by přišla o formátování?
 *
 * Pole umí jen holý text. Odkazy, zmínky a spol. se při odeslání úpravy dohledají znovu, ale
 * **ruční formátování** (tučné, kurzíva, spoiler, odkaz s vlastním popiskem) by úprava tiše smazala.
 * Takovou zprávu radši upravit nenabídneme — položka, která rozbije obsah, je horší než žádná.
 *
 * Odkaz se pozná jako automatický, když jeho text v adrese opravdu stojí (`example.com` →
 * `https://example.com`). U odkazu s popiskem („klikni sem") to neplatí.
 */
private fun ChatMessage.isEditableAsPlainText(): Boolean {
    if (media != null || isService || text.isBlank()) return false

    return entities.all { entity ->
        val type = entity.type as? MessageEntityType.Link ?: return@all false
        val start = entity.offset.coerceIn(0, text.length)
        val end = (entity.offset + entity.length).coerceIn(start, text.length)
        type.url.endsWith(text.substring(start, end), ignoreCase = true)
    }
}

/**
 * Text zprávy pro schránku.
 *
 * Animované emoji má prázdný text (kreslí se jako příloha), ale zkopírovat se má pořád jeho znak —
 * dřív, když bylo textovou zprávou, to šlo.
 */
private fun ChatMessage.copyableText(): String =
    text.ifBlank { (media as? MessageMedia.AnimatedEmoji)?.emoji.orEmpty() }

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

/** Otevřený plný seznam reakcí (bottom sheet za tlačítkem „+"). */
data class ReactionPicker(
    val messageId: Long,
    val emoji: List<String>,
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
    /** Podle strany bubliny se nabídka zarovná, aby vyrůstala od zprávy, ne od kraje obrazovky. */
    val isOutgoing: Boolean,
    /** Nabídnutá emoji. Prázdné je běžný stav (Uložené zprávy), ne chyba. */
    val emoji: List<String>,
    /** Emoji, která uživatel u téhle zprávy už dal — v rychlém řádku se zvýrazní. */
    val chosenEmoji: Set<String>,
    val actions: MessageActions,
    /** Bez textu není co kopírovat, i kdyby to server dovolil. */
    val hasText: Boolean,
    /** Server úpravu dovolí A zpráva se dá upravit bez ztráty formátování. */
    val canEdit: Boolean,
    /** Podle seznamu připnutých — rozhoduje mezi „Připnout" a „Odepnout". */
    val isPinned: Boolean,
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
    /** Plný seznam reakcí za tlačítkem „+". */
    val reactionSheet: ReactionPicker? = null,
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
    /** Krátké potvrzení akce, která v konverzaci nic viditelného nezmění (snackbar). */
    val notice: String? = null,
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
    val error: String? = null,
) {
    /** Id všech načtených zpráv alba, do kterého zpráva patří; u samostatné zprávy jen ona. */
    fun albumMemberIds(messageId: Long): List<Long> {
        val album = messages.firstOrNull { it.id == messageId }?.mediaAlbumId ?: return listOf(messageId)
        if (album == 0L) return listOf(messageId)
        return messages.filter { it.mediaAlbumId == album }.map { it.id }
    }

    /** Stabilní klíč položky zprávy; přežije změnu id po odeslání. */
    fun keyOf(message: ChatMessage): Long = itemKeys[message.id] ?: message.id
}
