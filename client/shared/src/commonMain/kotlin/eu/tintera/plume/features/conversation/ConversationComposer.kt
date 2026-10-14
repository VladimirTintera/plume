package eu.tintera.plume.features.conversation

import androidx.lifecycle.ViewModel
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.share.ShareTargets
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ChatReplyMarkup
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ComposedText
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.LinkPreview
import eu.tintera.plume.core.telegram.LinkPreviewOptions
import eu.tintera.plume.core.telegram.MessageEffect
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.ReplyQuote
import eu.tintera.plume.core.telegram.SendOptions
import eu.tintera.plume.core.telegram.composedFormatsOf
import eu.tintera.plume.core.telegram.diceEmojiFor
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.url.UrlCleaner
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_edit_failed
import eu.tintera.plume.resources.conversation_schedule_failed
import eu.tintera.plume.resources.conversation_scheduled_notice
import eu.tintera.plume.resources.conversation_send_failed
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

/**
 * Vstupní pole z pohledu ViewModelu: koncept (ukládání s pauzou, obnova i s odpovědí),
 * „píše…", odpověď a citace, úprava zprávy, náhled odkazu a jeho volby, efekty a odeslání
 * textu i média s letem bubliny.
 *
 * Část [ConversationViewModel] — ten na ni deleguje.
 */
internal class ConversationComposer(
    private val chatId: Long,
    private val state: MutableStateFlow<ConversationState>,
    private val scope: CoroutineScope,
    /** Koncept se ukládá i po odchodu z chatu — poslední úprava je nejdůležitější. */
    private val appScope: CoroutineScope,
    private val repository: ConversationRepository,
    private val chatRepository: ChatRepository,
    private val settings: AppSettings,
    private val shareTargets: ShareTargets,
    /** Zmínky jménem čekající na odeslání. */
    private val suggestions: ConversationSuggestions,
    /** Vlastní zpráva přečte konverzaci až k sobě. */
    private val readAfterOwnMessage: () -> Unit,
    /** Text s přílohami odchází jako jejich popisek. */
    private val sendAttachments: (caption: ComposedText, attachments: List<OutgoingAttachment>, options: SendOptions) -> Unit,
    private val logger: Logger,
) {
    /** Rozlišuje jednotlivé lety textu do bubliny, když uživatel odešle dvě zprávy rychle po sobě. */
    private var flightCounter = 0L

    /**
     * Kdy naposledy odešlo „píšu". `null`, když protějšek o psaní neví (nic neodešlo, nebo se
     * mezitím zrušilo či odeslalo).
     */
    var typingSentAt: TimeMark? = null

    /**
     * Poslední text z pole i s ručním formátováním, jak ho hlásí obrazovka. Kvůli ukládání
     * konceptu.
     */
    var draft = ComposedText("")

    /**
     * Koncept z TDLib je vyřízený (vrácený do pole, nebo žádný není). Sdílený text se vkládá až
     * po něm: koncept jde jen do prázdného pole, takže sdílení před ním by ho z pole vytlačilo.
     */
    val draftRestored = CompletableDeferred<Unit>()

    /** Koncept odložený po dobu úpravy zprávy; po ní se vrátí do pole i s formátováním. */
    private var draftBeforeEdit = ComposedText("")

    /**
     * Náhled odkazu konceptu, odložený s ním. Úprava má vlastní náhled a volbu (z upravované
     * zprávy), a kdyby je nepřepsala, odešla by upravená zpráva s volbou rozepsaného konceptu.
     */
    private var linkPreviewBeforeEdit = LinkPreviewStash()

    /** Odložené uložení konceptu; každá další úprava ho posune. */
    var draftSave: Job? = null

    /** Odložené zjištění náhledu odkazu v rozepsaném textu; každý úhoz ho posune. */
    private var linkPreviewJob: Job? = null

    /**
     * Text, ke kterému se náhled naposledy zjišťoval. Nemusí být [draft]: pole vyplněné
     * sdílením nebo konceptem `onDraftChanged` nevolá, a výběr odkazu se ptá právě k tomuhle.
     */
    private var linkPreviewText = ""

    fun sendMedia(
        failureMessage: StringResource,
        scheduled: Boolean = false,
        send: suspend (replyTo: Long?, quote: ReplyQuote?) -> Result<ChatMessage>,
    ) {
        val replyTo = state.value.replyingTo?.id
        val quote = state.value.replyQuote
        clearReply()

        scope.launch {
            send(replyTo, quote)
                .onSuccess { pending ->
                    shareTargets.reportUsed(chatId)
                    // Naplánovaná do konverzace nepatří, jen se ohlásí — jako naplánovaný text.
                    if (scheduled) {
                        state.update { it.copy(notice = UiText(Res.string.conversation_scheduled_notice)) }
                        return@onSuccess
                    }
                    // Potvrzení mohlo dorazit dřív než výsledek; `withMessage` to ošetří.
                    state.update { it.withMessage(pending) }
                }
                .onFailure { failure ->
                    state.update {
                        it.copy(error = failure.reported(logger, if (scheduled) Res.string.conversation_schedule_failed else failureMessage))
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
        scope.launch {
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
        val attachments = state.value.attachments
        if (attachments.isNotEmpty() && state.value.editing == null) {
            // Dokud se úprava fotky vykresluje, příloha má ještě cestu k NEUPRAVENÉ fotce —
            // odeslala by se i s tím, co měl uživatel začerněné. Tlačítko je mezitím vypnuté;
            // tohle je pojistka pro Enter z klávesnice.
            if (state.value.markupBusy) return
            readAfterOwnMessage()
            sendAttachments(composed, attachments, options)
            return
        }

        // Úprava se řeší dřív než prázdné pole: prázdný POPISEK je platná úprava (odebere ho),
        // takže o tom rozhoduje `sendEdit` podle toho, co se vlastně upravuje.
        state.value.editing?.let { target ->
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
        if (dice != null && state.value.content.stickers) {
            resetLinkPreview()
            sendMedia(Res.string.conversation_send_failed) { replyTo, quote ->
                repository.sendDice(chatId, dice, replyTo, quote)
            }
            return
        }

        val linkPreview = state.value.linkPreviewOptions.forSending(shown = state.value.linkPreview)
        resetLinkPreview()

        val replyTo = state.value.replyingTo?.id
        val quote = state.value.replyQuote
        // Rozepsaná odpověď se zruší hned, ne až po potvrzení — jinak by při pomalé síti šlo
        // odeslat druhou zprávu se stejnou citací.
        clearReply()
        val mentions = suggestions.takeNameMentions()

        // Naplánovaná zpráva do konverzace nepatří: neletí, nepřidává se, jen se ohlásí.
        if (options.schedule != null) {
            scope.launch {
                repository.send(chatId, trimmed, replyTo, linkPreview, quote, options, mentions, composed.formats)
                    .onSuccess { state.update { it.copy(notice = UiText(Res.string.conversation_scheduled_notice)) } }
                    .onFailure { failure ->
                        state.update { it.copy(error = failure.reported(logger, Res.string.conversation_schedule_failed)) }
                    }
            }
            return
        }

        // Text „vyletí" ze vstupního pole do bubliny. Musí se zapamatovat hned při odeslání,
        // protože pole se vyprázdní dřív, než dorazí zpráva — a bez toho by nebylo co animovat.
        val flight = MessageFlight(id = ++flightCounter, text = trimmed, itemKey = null)
        state.update { it.copy(flight = flight) }

        scope.launch {
            repository.send(chatId, trimmed, replyTo, linkPreview, quote, options, mentions, composed.formats)
                // TDLib vrací rozepsanou zprávu okamžitě, takže bublina naskočí bez čekání
                // na server. Skutečné id dorazí potvrzením a zprávu v seznamu nahradí.
                .onSuccess { pending ->
                    shareTargets.reportUsed(chatId)
                    // Na vynucenou odpověď se odpovědělo; TDLib ji po klientovi chce odebrat.
                    val force = state.value.replyMarkup as? ChatReplyMarkup.ForceReply
                    if (force != null && force.messageId == replyTo) {
                        repository.dismissReplyMarkup(chatId, force.messageId)
                    }
                    state.update {
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
                    state.update { current ->
                        current.copy(flight = current.flight?.takeIf { it.id != flight.id })
                    }
                }
                .onFailure { failure ->
                    state.update {
                        it.copy(
                            error = failure.reported(logger, Res.string.conversation_send_failed),
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
        state.value.editing?.let { editing ->
            // Náhled odkazu ale patří i k úpravě textu: upravená zpráva odejde s ním.
            if (editing.composerEdit() == ComposerEdit.Text) refreshLinkPreview(text.text)
            return
        }
        // Psaní zavře seznam příkazů otevřený tlačítkem; napovídání „/…" se řídí textem samo.
        if (state.value.commandsOpen) state.update { it.copy(commandsOpen = false) }

        val textChanged = text.text != draft.text
        draft = text
        // Vyprázdněné pole = zpráva, ke které zmínky patřily, je pryč.
        if (text.text.isBlank()) suggestions.nameMentions.clear()
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
    fun scheduleDraftSave() {
        val text = draft
        val replyTo = state.value.replyingTo?.id
        val quote = state.value.replyQuote
        val linkPreview = state.value.linkPreviewOptions

        draftSave?.cancel()
        draftSave = appScope.launch {
            delay(DRAFT_SAVE_DELAY_MS)
            saveDraft(text, replyTo, linkPreview, quote)
        }
    }

    suspend fun saveDraft(
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
    fun restoreDraftReply(messageId: Long, quote: ReplyQuote?) {
        scope.launch {
            val message = state.value.messages.firstOrNull { it.id == messageId }
                ?: repository.message(chatId, messageId).getOrNull()
                ?: return@launch
            // Mezitím mohl uživatel začít odpovídat na něco jiného; to má přednost.
            state.update { if (it.replyingTo == null) it.copy(replyingTo = message, replyQuote = quote) else it }
        }
    }

    /**
     * Zjistí náhled odkazu v rozepsaném textu, s pauzou po posledním úhozu
     * ([LINK_PREVIEW_DELAY_MS]) — `getLinkPreview` jde na síť a TDLib ho žádá nevolat často.
     *
     * Prázdné pole zahodí náhled i volbu (i vypnutí): je to nová zpráva. Vypnutý náhled se
     * nezjišťuje vůbec, dokud se pole nevyprázdní.
     */
    fun refreshLinkPreview(text: String, delayMs: Long = LINK_PREVIEW_DELAY_MS) {
        linkPreviewJob?.cancel()
        linkPreviewText = text
        if (text.isBlank()) {
            // Vymazaný text úpravy není nová zpráva: volba upravované zprávy platí dál.
            if (state.value.editing != null) {
                state.update { it.copy(linkPreview = null, linkPreviewLinks = emptyList()) }
            } else {
                resetLinkPreview()
            }
            return
        }
        if (state.value.linkPreviewOptions.disabled) return
        linkPreviewJob = scope.launch {
            delay(delayMs)
            val result = repository.linkPreview(text, state.value.linkPreviewOptions.url).getOrElse { failure ->
                // Bez náhledu se dá psát i poslat dál; server si ho k odeslané zprávě vyrobí sám.
                logger.w(failure) { "Náhled odkazu se nepodařilo zjistit." }
                return@launch
            }
            state.update {
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

    fun resetLinkPreview() {
        linkPreviewJob?.cancel()
        linkPreviewJob = null
        state.update {
            it.copy(linkPreview = null, linkPreviewLinks = emptyList(), linkPreviewOptions = LinkPreviewOptions())
        }
    }

    /**
     * Uživatel změnil volbu náhledu (odkaz, velikost, poloha, ✕). Patří ke konceptu, ukládá se
     * s ním. Jiný odkaz chce nový náhled hned — ťuknutí není psaní, na pauzu se nečeká.
     */
    fun setLinkPreviewOptions(options: LinkPreviewOptions) {
        val linkChanged = options.url != state.value.linkPreviewOptions.url
        state.update { it.copy(linkPreviewOptions = options) }
        // Při úpravě volba patří upravované zprávě, ne konceptu.
        if (state.value.editing == null) scheduleDraftSave()
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
        state.update { if (it.inputFill === fill) it.copy(inputFill = null) else it }
    }

    /**
     * Přepne vstupní pole do úpravy zprávy.
     *
     * Rozepsaný text se **odloží a po skončení úpravy vrátí**. Úprava je vsuvka — kdo si
     * uprostřed psaní všimne překlepu v minulé zprávě, nechce kvůli tomu přijít o rozepsanou.
     */
    fun startEdit(messageId: Long) {
        val message = state.value.messages.firstOrNull { it.id == messageId } ?: return
        val formats = composedFormatsOf(message.text, message.entities) ?: return
        enterEdit(message, ComposedText(message.text, formats), message.linkPreviewOptions ?: LinkPreviewOptions())
    }

    /**
     * Pole v úpravě [target] s textem [text] a volbou náhledu [linkOptions]. Náhled se zjistí
     * hned — text se do pole vkládá bez `onDraftChanged` a na pauzu v psaní není proč čekat.
     */
    private fun enterEdit(target: ChatMessage, text: ComposedText, linkOptions: LinkPreviewOptions) {
        // Přepnutí z jedné úpravy do druhé nesmí přepsat odložený koncept textem první úpravy.
        if (state.value.editing == null) {
            draftBeforeEdit = draft
            linkPreviewBeforeEdit = state.value.let {
                LinkPreviewStash(it.linkPreview, it.linkPreviewLinks, it.linkPreviewOptions)
            }
        }
        linkPreviewJob?.cancel()
        state.update {
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
        if (state.value.editing == null) return
        leaveEdit()
    }

    private fun leaveEdit() {
        draft = draftBeforeEdit
        linkPreviewJob?.cancel()
        linkPreviewText = draftBeforeEdit.text
        val stash = linkPreviewBeforeEdit
        state.update {
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

        val linkOptions = state.value.linkPreviewOptions
        val linkPreview = linkOptions.forEdit(sanitized.text, shown = state.value.linkPreview)
        leaveEdit()
        // Beze změny není co posílat; server by vrátil chybu „zpráva se nezměnila". Změna jen
        // formátování (tučné slovo) je ale změna.
        val optionsChanged = kind == ComposerEdit.Text &&
            linkOptions != (target.linkPreviewOptions ?: LinkPreviewOptions())
        val original = ComposedText(target.text, composedFormatsOf(target.text, target.entities).orEmpty()).trimmed()
        if (sanitized == original && !optionsChanged) return

        scope.launch {
            val edited = when (kind) {
                ComposerEdit.Text -> repository.editText(chatId, target.id, sanitized.text, linkPreview, sanitized.formats)
                ComposerEdit.Caption -> repository.editCaption(chatId, target.id, sanitized.text, sanitized.formats)
            }
            edited.onFailure { failure ->
                state.update { it.copy(error = failure.reported(logger, Res.string.conversation_edit_failed)) }
                enterEdit(target, text, linkOptions)
            }
        }
    }

    private fun reportTyping(typing: Boolean) {
        // Diskrétní čtení skrývá i psaní. Ukončení psaní projde — nic neprozradí a zhasne
        // „píše…" ohlášené ještě před zapnutím volby.
        if (typing && settings.discreetReading.value) {
            logger.d { "Diskrétní čtení: psaní se nehlásí" }
            return
        }
        scope.launch {
            repository.sendTyping(chatId, typing).onFailure { failure ->
                // Třeba kanál, kam uživatel psát nesmí. Chyba kvůli „píše…" by jen obtěžovala.
                logger.d(failure) { "Činnost v chatu se nepodařilo ohlásit." }
            }
        }
    }

    /**
     * Nastaví zprávu, na kterou se odpovídá, případně jen její úryvek ([quote]). Zobrazí se nad
     * vstupním polem.
     */
    fun replyTo(messageId: Long, quote: ReplyQuote? = null) {
        // Swipe na odpověď se nabízí u každé zprávy; kde se psát nesmí, není kam odpovědět.
        if (!state.value.canWrite) return
        val message = state.value.messages.firstOrNull { it.id == messageId } ?: return
        state.update { it.copy(replyingTo = message, replyQuote = quote, messageMenu = null) }
        // Citace je součást konceptu — jen když už se něco píše, jinak není co ukládat.
        if (draft.text.isNotBlank()) scheduleDraftSave()
    }

    /**
     * Odpověď z prohlížeče médií. Prohlížeč listuje fotkami celého chatu, takže zpráva nemusí být
     * v načtené historii konverzace — pak se dočte (lokálně), citace ji nepotřebuje mít v seznamu.
     */
    fun replyFromViewer(messageId: Long) {
        if (!state.value.canWrite) return
        if (state.value.messages.any { it.id == messageId }) {
            replyTo(messageId)
            return
        }
        scope.launch {
            repository.message(chatId, messageId).onSuccess { message ->
                state.update { it.copy(replyingTo = message, replyQuote = null, messageMenu = null) }
                if (draft.text.isNotBlank()) scheduleDraftSave()
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
    fun quoteReply(messageId: Long, text: String) {
        val message = state.value.messages.firstOrNull { it.id == messageId } ?: return
        val position = message.text.indexOf(text)
        replyTo(messageId, quote = ReplyQuote(text, position).takeIf { position >= 0 })
    }

    fun clearReply() {
        state.update { it.copy(replyingTo = null, replyQuote = null) }
        if (draft.text.isNotBlank()) scheduleDraftSave()
    }

    /**
     * Odchod z chatu: čekající uložení konceptu odejde hned, v [appScope] — rozsah konverzace se
     * ruší dřív, než by poslední úprava stihla odejít, a právě ta je nejdůležitější.
     */
    fun saveOnLeave() {
        val pending = draftSave ?: return
        if (!pending.isActive) return
        pending.cancel()

        val text = draft
        val replyTo = state.value.replyingTo?.id
        val quote = state.value.replyQuote
        val linkPreview = state.value.linkPreviewOptions
        appScope.launch { saveDraft(text, replyTo, linkPreview, quote) }
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

/** Náhled odkazu konceptu, odložený po dobu úpravy zprávy ([ConversationViewModel]). */
private data class LinkPreviewStash(
    val preview: LinkPreview? = null,
    val links: List<String> = emptyList(),
    val options: LinkPreviewOptions = LinkPreviewOptions(),
)
