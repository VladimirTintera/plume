package eu.tintera.plume.features.media

import eu.tintera.plume.core.ai.imageDescriptionPrompt
import eu.tintera.plume.core.ai.variantsFor
import eu.tintera.plume.core.ai.variantFor
import eu.tintera.plume.core.ai.AiTask
import eu.tintera.plume.core.ocr.RecognizedText
import eu.tintera.plume.core.ocr.TextRecognizer
import eu.tintera.plume.core.ui.TextClipboard
import co.touchlab.kermit.Logger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.files.DocumentKind
import eu.tintera.plume.core.files.MediaSaver
import eu.tintera.plume.core.files.autosavesToGallery
import eu.tintera.plume.core.files.copiesAnyFile
import eu.tintera.plume.core.files.MediaSharer
import eu.tintera.plume.core.files.savedFileName
import eu.tintera.plume.core.files.documentKind
import eu.tintera.plume.core.files.opensInViewer
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.MessageActions
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.navigation.NO_STORY
import eu.tintera.plume.core.telegram.NO_ARTICLE_MEDIA
import eu.tintera.plume.core.telegram.NO_POLL_MEDIA
import eu.tintera.ai.core.OnDeviceAi
import eu.tintera.ai.core.OnDeviceAiException
import eu.tintera.plume.core.ai.emptyOutputMessage
import eu.tintera.ai.core.isReportable
import eu.tintera.plume.core.ai.userMessage
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.platformLanguageCode
import eu.tintera.plume.core.telegram.POLL_EXPLANATION_MEDIA
import eu.tintera.plume.core.telegram.POLL_QUESTION_MEDIA
import eu.tintera.plume.core.telegram.PollAttachment
import eu.tintera.plume.core.telegram.attachmentAt
import eu.tintera.plume.core.telegram.MessageReaction
import eu.tintera.plume.core.telegram.PhotoPosition
import eu.tintera.plume.core.telegram.ProfileRepository
import eu.tintera.plume.core.telegram.SelfDestructTimers
import eu.tintera.plume.core.telegram.SelfDestructType
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.features.conversation.PendingDelete
import eu.tintera.plume.features.conversation.toggled
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.sticker_prepare_failed
import eu.tintera.plume.core.stickers.StickerMaker
import eu.tintera.plume.resources.media_attached_stickers_failed
import eu.tintera.plume.resources.media_attached_stickers_none
import eu.tintera.plume.core.telegram.StickerSetSummary
import eu.tintera.plume.core.telegram.StickerRepository
import eu.tintera.plume.resources.media_profile_photo_report_failed
import eu.tintera.plume.resources.media_profile_photo_reported
import eu.tintera.plume.core.telegram.PhotoReportReason
import eu.tintera.plume.resources.conversation_delete_failed
import eu.tintera.plume.resources.conversation_reaction_failed
import eu.tintera.plume.resources.media_describe_failed
import eu.tintera.plume.resources.media_photo_load_failed
import eu.tintera.plume.resources.media_text_copied
import eu.tintera.plume.resources.media_text_failed
import eu.tintera.plume.resources.media_text_none
import eu.tintera.plume.resources.media_profile_photo_failed
import eu.tintera.plume.resources.media_profile_photo_set
import eu.tintera.plume.resources.media_profile_photos_none
import eu.tintera.plume.resources.media_save_denied
import eu.tintera.plume.resources.media_save_failed
import eu.tintera.plume.resources.media_saved_as
import eu.tintera.plume.resources.media_share_failed
import eu.tintera.plume.resources.media_saved
import eu.tintera.plume.resources.media_saved_downloads
import eu.tintera.plume.resources.media_copied
import eu.tintera.plume.resources.media_copy_failed
import eu.tintera.plume.resources.media_unsupported
import eu.tintera.plume.resources.story_unavailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

/**
 * Fotky a videa chatu přes celou obrazovku, s listováním.
 *
 * Otevírá se na jedné zprávě ([messageId]) a kolem ní se dotahují další fotky chatu — nejdřív
 * kus na obě strany, pak po stránkách, jak se uživatel blíží k okraji načteného výřezu.
 *
 * Zprávy se **načítají znovu podle id**, nepředávají se z konverzace. Routa musí být
 * serializovatelná (jinak by nepřežila zabití procesu) a celé zprávy do navigačního zásobníku
 * nepatří. Proti stahování fotek je dotaz navíc zanedbatelný.
 *
 * Nad snímkem jsou jako v Google Zprávách reakce a akce (smazat, přeposlat). Co se u zprávy smí
 * a které reakce chat nabízí, se načítá **až pro stránku, na kterou uživatel doliste** — stejný
 * důvod jako u nabídky v konverzaci: dopředu by to byly dva dotazy na každou fotku ve výřezu.
 */
class MediaViewerViewModel(
    private val chatId: Long,
    private val messageId: Long,
    /**
     * Příloha ankety (`POLL_QUESTION_MEDIA`, nebo PŮVODNÍ index odpovědi) místo přílohy zprávy;
     * `NO_POLL_MEDIA` = běžná zpráva. Viz [loadPollMedia].
     */
    private val pollMedia: Int = NO_POLL_MEDIA,
    /** Profilové fotky chatu místo médií ze zpráv — viz [loadProfilePhotos]; [messageId] je pak 0. */
    private val profilePhotos: Boolean = false,
    /** Médium článku (`ArticleMedia.index`) místo přílohy zprávy — viz [loadArticleMedia]. */
    private val articleMedia: Int = NO_ARTICLE_MEDIA,
    /** Příběh podle autora ([chatId]) a id místo přílohy zprávy — viz [loadStory]. */
    private val storyId: Int = NO_STORY,
    /** Médium sponzorované zprávy [messageId] (video reklamy) — viz [loadSponsored]. */
    private val sponsored: Boolean = false,
    private val repository: ConversationRepository,
    private val profileRepository: ProfileRepository,
    private val files: FileRepository,
    private val saver: MediaSaver,
    private val sharer: MediaSharer,
    private val timers: SelfDestructTimers,
    /** Nahlášení zhlédnutého jednorázového média musí doběhnout i po zrušení [viewModelScope]. */
    private val appScope: CoroutineScope,
    /** Text z fotky; bez něj se tlačítko nenabízí. */
    private val textRecognizer: TextRecognizer? = null,
    /** Samolepka z fotky; bez ní (iOS, desktop) se „Vytvořit samolepku" nenabízí. */
    private val stickerMaker: StickerMaker? = null,
    /** Text z fotky do schránky. */
    private val clipboard: TextClipboard? = null,
    /** Popis fotky modelem v telefonu; bez něj (nebo bez [settings]) se nenabízí. */
    private val onDeviceAi: OnDeviceAi? = null,
    private val settings: AppSettings? = null,
    /** Hotové popisy fotek: uložit, aby je měla bublina pro čtečku, a příště ukázat bez modelu. */
    private val photoDescriptions: PhotoDescriptions? = null,
    /** Sady samolepek nalepených na fotku nebo video; bez něj se položka nenabízí. */
    private val stickers: StickerRepository? = null,
) : ViewModel() {

    private val logger = Logger.withTag("MediaViewer")

    private val _state = MutableStateFlow(MediaViewerState(initialMessageId = messageId))
    val state: StateFlow<MediaViewerState> = _state.asStateFlow()

    /** Probíhající dotahování. Stránkuje se z `onPageChanged`, které chodí při každém posunu. */
    private var loadingOlder = false
    private var loadingNewer = false

    /**
     * Reklamy nad videem z kanálu na celé obrazovce ([ViewerVideoAds]). Jen u médií chatu —
     * profilové fotky, příběh, anketa, článek ani reklama samotná je nemají.
     */
    private val videoAds = ViewerVideoAds(
        chatId = chatId,
        enabled = pollMedia == NO_POLL_MEDIA && !profilePhotos && articleMedia == NO_ARTICLE_MEDIA &&
            storyId == NO_STORY && !sponsored,
        scope = viewModelScope,
        appScope = appScope,
        repository = repository,
        logger = logger,
    )

    /** Reklama nad hrajícím videem; `null` = žádná. */
    val videoAd: StateFlow<ViewerVideoAd?> = videoAds.shown

    /** Průvodce nahlášením reklamy nad videem; kreslí ho obrazovka. */
    val videoAdReportFlow get() = videoAds.reportFlow

    /** Které video hraje na celé obrazovce (`null` = žádné) — čas reklam běží jen při přehrávání. */
    fun onVideoPlaying(messageId: Long?) = videoAds.setPlaying(messageId)

    fun closeVideoAd() = videoAds.close()

    /** Ťuknutí na reklamu nad videem: nahlásí kliknutí a vrátí odkaz inzerenta. */
    fun openVideoAd(): String? = videoAds.open()

    fun reportVideoAd() = videoAds.report()

    /** U reklamy je otevřená nabídka nebo informace o sponzorovi — nesmí se zatím skrýt. */
    fun holdVideoAd(held: Boolean) = videoAds.setHeld(held)

    /**
     * Otevřený je soubor (obrázek nebo video poslané jako dokument). Listuje se jen fotkami
     * a videi, mezi které nepatří, takže se ukazuje sám a bez pořadí „N z M" — TDLib by ho
     * počítala mezi fotkami.
     */
    private var single = false

    /** Otevřené mizející médium poslal sám uživatel: žádný odpočet ani spotřebování. */
    private var ownSelfDestruct = false

    /**
     * Jednorázové médium se stáhlo a uživatel ho viděl — TDLib se to dozví až při odchodu
     * (`onCleared`), viz [awaitSelfDestructMedia].
     */
    private var viewOnceShown = false

    /** Zprávy, u kterých se podrobnosti (reakce, práva) už načítají nebo načetly. */
    private val detailsRequested = mutableSetOf<Long>()

    private val isPollMedia: Boolean get() = pollMedia != NO_POLL_MEDIA

    private val isArticleMedia: Boolean get() = articleMedia != NO_ARTICLE_MEDIA

    /**
     * Stránky nejsou zprávy chatu (příloha ankety, profilovky): žádné pořadí fotek chatu,
     * reakce, úpravy ani mazání se jich netýkají.
     */
    private val isStory: Boolean get() = storyId != NO_STORY

    private val notMessages: Boolean get() = isPollMedia || isArticleMedia || profilePhotos || isStory || sponsored

    /** Běžící popis fotky ([describePhoto]); zavření panelu nebo odlistování ho zruší. */
    private var describeJob: Job? = null

    init {
        watchPhotoDescription()
        viewModelScope.launch {
            when {
                profilePhotos -> loadProfilePhotos()
                isPollMedia -> loadPollMedia()
                isArticleMedia -> loadArticleMedia()
                isStory -> loadStory()
                sponsored -> loadSponsored()
                else -> loadInitial()
            }
        }
        if (!notMessages) viewModelScope.launch {
            repository.visualMediaPosition(chatId, messageId)
                .onSuccess { position -> if (!single) _state.update { it.copy(initialPosition = position) } }
        }
        viewModelScope.launch {
            // Název chatu je titulek u příchozích fotek v soukromém chatu, kde TDLib jméno
            // odesílatele nevyplňuje.
            repository.chatInfo(chatId).onSuccess { info -> _state.update { it.copy(chatTitle = info.title) } }
        }
        // Příběh není zpráva chatu — úpravy ani mazání zpráv autora se ho netýkají.
        if (!profilePhotos && !isStory && !sponsored) observeMessages()
    }

    /** Změny zpráv, na kterých prohlížeč stojí: reakce, úprava, vypršení a smazání. */
    private fun observeMessages() {
        viewModelScope.launch {
            repository.interactionUpdates(chatId).collect { update ->
                updateItem(update.messageId) { it.copy(reactions = update.reactions) }
            }
        }
        viewModelScope.launch {
            repository.editedMessages(chatId).collect { message ->
                if (message.id == messageId && message.media == null && _state.value.isSelfDestruct) {
                    // Vypršela platnost v TDLib (MessageExpiredPhoto)
                    _state.update { it.copy(closed = true) }
                } else if (!isPollMedia && !isArticleMedia) {
                    // U ankety je text zprávy „Anketa: otázka"; popiskem přílohy je otázka
                    // nebo odpověď. U článku je text celý článek, popiskem popisek média.
                    // Příběh text zprávy nemá, popiskem je text příběhu.
                    val caption = (message.media as? MessageMedia.Story)?.caption ?: message.text
                    updateItem(message.id) { it.copy(caption = caption) }
                }
            }
        }
        viewModelScope.launch {
            // Smazání odjinud i odsud: fotka z listování zmizí, pager se posune na sousední.
            repository.deletedMessages(chatId).collect { ids ->
                val removed = ids.toSet()
                _state.update { state ->
                    val left = state.items.filterNot { it.messageId in removed }
                    // Smazaná poslední fotka: prohlížeč nemá co ukázat a zavře se.
                    state.copy(items = left, closed = state.items.isNotEmpty() && left.isEmpty())
                }
            }
        }
    }

    private suspend fun loadInitial() {
        val initialMessage = repository.message(chatId, messageId).getOrNull()
        val isSecret = initialMessage != null && (
            (initialMessage.media as? MessageMedia.Photo)?.isSecret == true ||
                (initialMessage.media as? MessageMedia.Video)?.isSecret == true ||
                initialMessage.selfDestructType != null
        )

        if (initialMessage != null && isSecret) {
            single = true
            ownSelfDestruct = initialMessage.isOutgoing
            // Vlastní odeslané médium se u odesílatele nespotřebovává — odpočet patří příjemci.
            if ((!ownSelfDestruct && timers.isConsumed(messageId)) || initialMessage.media == null) {
                _state.update { it.copy(closed = true, loading = false) }
                return
            }
            val item = initialMessage.toViewerItem()
            val selfDestructType = initialMessage.selfDestructType
                ?: (initialMessage.media as? MessageMedia.Photo)?.selfDestructType
                ?: (initialMessage.media as? MessageMedia.Video)?.selfDestructType

            _state.update {
                it.copy(
                    items = listOfNotNull(item),
                    initialPosition = null,
                    reachedOldest = true,
                    reachedNewest = true,
                    loading = false,
                    isSelfDestruct = true,
                    selfDestructType = selfDestructType,
                )
            }

            // Otevření vlastní zprávy nic nespouští: odpočet patří příjemci, a lokální odpočet by
            // odesílateli ukázal „vypršelo", i když příjemce médium ještě neviděl.
            if (ownSelfDestruct) return

            if (!awaitSelfDestructMedia(initialMessage.media ?: return)) return

            if (selfDestructType !is SelfDestructType.Timer) {
                // Jednorázové zhlédnutí: TDLib po `openMessageContent` obsah hned zahodí i se
                // soubory, takže se hlásí až při odchodu z prohlížeče.
                viewOnceShown = true
                return
            }

            // Oznámit TDLib otevření obsahu — spustí odpočet. Až teď, když je médium stažené:
            // odpočet běží od otevření a čekání na síť by ukrojilo z doby, kterou odesílatel dal.
            repository.openContent(chatId, messageId)

            // Zbývající čas je od TDLib, ne délka časovače: odpočet mohl začít dřív (jiné
            // zařízení, otevření před restartem). Po `openContent` ho TDLib už zná; dokud ne,
            // platí hodnota z doby načtení a teprve pak plná délka.
            val remaining = repository.message(chatId, messageId).getOrNull()?.selfDestructIn
                ?: initialMessage.selfDestructIn
                ?: selfDestructType.seconds
            viewModelScope.launch {
                timers.observeRemainingSeconds(messageId, remaining)
                    .collect { remaining ->
                        _state.update { it.copy(remainingSeconds = remaining) }
                        if (remaining <= 0) {
                            _state.update { it.copy(closed = true) }
                        }
                    }
            }
            return
        }

        /*
         * Otevřená fotka se ukáže HNED, sousedé pro listování se dotáhnou pod ní. Výřez jde
         * přes `searchChatMessages`, a to trvá (v chatu se čtyřiceti fotkami stovky ms); dokud
         * se čekalo na něj, neměla fotka letící z bubliny kam dosednout — zmizela, bliklo
         * načítání a pak se objevila. Pager drží stránku podle klíče, takže když se výřez kolem
         * ní rozšíří, zůstane na ní. Okraje jsou do té doby „dosažené", aby listování nezačalo
         * dotahovat od jediné fotky souběžně s výřezem.
         */
        val initialItem = initialMessage?.toViewerItem()
        /*
         * Sdílený příběh mezi fotkami chatu není (`SearchMessagesFilterPhotoAndVideo` ho nenajde)
         * a „N z M" by se počítalo mezi nimi — ukáže se sám, jako soubor nebo příloha ankety.
         */
        if (initialMessage?.media is MessageMedia.Story) {
            single = true
            _state.update {
                it.copy(
                    items = listOfNotNull(initialItem),
                    initialPosition = null,
                    reachedOldest = true,
                    reachedNewest = true,
                    loading = false,
                    error = if (initialItem == null) UiText(Res.string.media_unsupported) else null,
                )
            }
            if (initialItem != null) loadDetails(messageId)
            return
        }
        if (initialItem != null) {
            _state.update {
                it.copy(items = listOf(initialItem), reachedOldest = true, reachedNewest = true, loading = false)
            }
            loadDetails(messageId)
        }

        repository.visualMedia(chatId, messageId, older = PAGE_SIZE, newer = PAGE_SIZE)
            .onSuccess { messages ->
                val items = messages.mapNotNull { it.toViewerItem() }
                if (items.none { it.messageId == messageId }) {
                    // Otevřená zpráva mezi fotkami není — jiný typ média, nebo ji vyhledávání
                    // ještě nezná. Pak se aspoň zkusí ukázat samotná.
                    loadSingle()
                    return
                }
                _state.update {
                    it.copy(items = items, reachedOldest = false, reachedNewest = false, loading = false)
                }
                loadDetails(messageId)
            }
            .onFailure { loadSingle() }
    }

    /**
     * Příloha ankety: jediná stránka, bez listování a bez akcí. Fotky v anketě mezi fotkami chatu
     * nejsou (`SearchMessagesFilterPhotoAndVideo` je nenajde), a pořadí „N z M" by se počítalo
     * mezi fotkami, kam nepatří — stejně jako u souboru.
     */
    private suspend fun loadPollMedia() {
        single = true
        repository.message(chatId, messageId)
            .onSuccess { message ->
                val poll = message.poll
                val media = poll?.attachmentAt(pollMedia)?.let { (it as? PollAttachment.Media)?.media }
                val shown = when (media) {
                    is MessageMedia.Photo, is MessageMedia.Video -> media
                    is MessageMedia.Document -> media.asViewerMedia()
                    else -> null
                }
                val caption = when {
                    poll == null -> ""
                    pollMedia == POLL_QUESTION_MEDIA -> poll.question
                    pollMedia == POLL_EXPLANATION_MEDIA -> poll.quiz?.explanation.orEmpty()
                    else -> poll.options.firstOrNull { it.index == pollMedia }?.text.orEmpty()
                }
                val item = shown?.let {
                    ViewerItem(
                        messageId = message.id,
                        media = it,
                        caption = caption,
                        isOutgoing = message.isOutgoing,
                        senderName = message.senderName,
                        timestamp = message.timestamp,
                        fileName = media?.originalFileName,
                    )
                }
                _state.update {
                    it.copy(
                        items = listOfNotNull(item),
                        initialPosition = null,
                        reachedOldest = true,
                        reachedNewest = true,
                        loading = false,
                        error = if (item == null) UiText(Res.string.media_unsupported) else null,
                    )
                }
            }
            .onFailure { failure ->
                logger.w(failure) { "Příloha ankety $messageId/$pollMedia se nepodařila načíst." }
                _state.update {
                    it.copy(loading = false, error = failure.userMessage(Res.string.media_photo_load_failed))
                }
            }
    }

    /**
     * Médium článku: jediná stránka bez listování a bez akcí, ze stejného důvodu jako příloha
     * ankety. Napřed z uložené zprávy (lokálně, hned — let z bubliny má kam dosednout); celý
     * článek (`fullArticle`, dotaz na server) jen tehdy, když médium leží až v části, kterou TDLib
     * u dlouhého článku neposlal.
     */
    private suspend fun loadArticleMedia() {
        single = true
        val local = repository.message(chatId, messageId).getOrNull()
            ?.takeIf { message -> message.article?.media.orEmpty().any { it.index == articleMedia } }
        (local?.let { Result.success(it) } ?: repository.fullArticle(chatId, messageId))
            .onSuccess { message ->
                val anchor = message.article?.media?.firstOrNull { it.index == articleMedia }
                val shown = when (val media = anchor?.media) {
                    is MessageMedia.Photo, is MessageMedia.Video -> media
                    else -> null
                }
                val item = shown?.let {
                    ViewerItem(
                        messageId = message.id,
                        media = it,
                        caption = anchor?.caption.orEmpty(),
                        isOutgoing = message.isOutgoing,
                        senderName = message.senderName,
                        timestamp = message.timestamp,
                        fileName = null,
                    )
                }
                _state.update {
                    it.copy(
                        items = listOfNotNull(item),
                        initialPosition = null,
                        reachedOldest = true,
                        reachedNewest = true,
                        loading = false,
                        error = if (item == null) UiText(Res.string.media_unsupported) else null,
                    )
                }
            }
            .onFailure { failure ->
                logger.w(failure) { "Médium článku $messageId/$articleMedia se nepodařilo načíst." }
                _state.update {
                    it.copy(loading = false, error = failure.userMessage(Res.string.media_photo_load_failed))
                }
            }
    }

    /**
     * Příběh podle autora a id (odpověď na příběh, odkaz na příběh): jediná stránka bez
     * listování a bez akcí, jako příloha ankety. Zpráva, ke které by patřil, není — reagovat,
     * přeposlat ani smazat se odsud nedá. Konverzace sem posílá jen příběh s obsahem; kdyby
     * mezitím vypršel, prohlížeč to řekne místo prázdné stránky.
     */
    private suspend fun loadStory() {
        single = true
        val story = repository.story(chatId, storyId)
        val item = story.content
            ?.takeIf { it is MessageMedia.Photo || it is MessageMedia.Video }
            ?.let {
                ViewerItem(
                    // Stránka není zpráva; klíč je id z routy (0), na kterém pager startuje.
                    messageId = messageId,
                    media = it,
                    caption = story.caption,
                    isOutgoing = false,
                    senderName = story.posterName,
                    timestamp = story.postedAt,
                )
            }
        _state.update {
            it.copy(
                items = listOfNotNull(item),
                initialPosition = null,
                reachedOldest = true,
                reachedNewest = true,
                loading = false,
                error = if (item == null) UiText(Res.string.story_unavailable) else null,
            )
        }
    }

    /**
     * Médium reklamy (video na celé obrazovce): jediná stránka bez listování a bez akcí, jako
     * příloha ankety. Reklama v historii chatu není; seznam reklam drží TDLib pět minut v keši,
     * takže druhý dotaz vrátí tutéž reklamu se stejným id, aniž by šel na server. Pod snímkem
     * je tlačítko reklamy ([MediaViewerState.sponsored], [openSponsoredLink]).
     */
    private suspend fun loadSponsored() {
        single = true
        repository.sponsoredMessages(chatId)
            .onSuccess { ads ->
                val ad = ads.firstOrNull { it.messageId == messageId }
                val item = ad?.media
                    ?.takeIf { it is MessageMedia.Photo || it is MessageMedia.Video }
                    ?.let {
                        ViewerItem(
                            messageId = messageId,
                            media = it,
                            caption = ad.text,
                            isOutgoing = false,
                            senderName = ad.title,
                            timestamp = 0,
                        )
                    }
                _state.update {
                    it.copy(
                        items = listOfNotNull(item),
                        initialPosition = null,
                        reachedOldest = true,
                        reachedNewest = true,
                        loading = false,
                        sponsored = ad?.takeIf { item != null }?.let { ViewerSponsored(it.buttonText, it.url, it.isRecommended) },
                        error = if (item == null) UiText(Res.string.media_unsupported) else null,
                    )
                }
            }
            .onFailure { failure ->
                logger.w(failure) { "Reklama $messageId se nepodařila načíst." }
                _state.update {
                    it.copy(loading = false, error = failure.userMessage(Res.string.media_photo_load_failed))
                }
            }
    }

    /**
     * Tlačítko reklamy na celé obrazovce: kliknutí se hlásí s `fromFullscreen` a vrátí se odkaz,
     * který otevře konverzace pod prohlížečem. Hlášení jede v [appScope] — prohlížeč se hned
     * zavírá a [viewModelScope] by ho zrušil.
     */
    fun openSponsoredLink(): String? {
        val sponsored = _state.value.sponsored ?: return null
        appScope.launch {
            repository.reportSponsoredClick(chatId, messageId, fromFullscreen = true)
                .onFailure { logger.w(it) { "Kliknutí na reklamu $messageId se nepodařilo nahlásit." } }
        }
        return sponsored.url
    }

    /**
     * Historie profilových fotek chatu (ťuknutí na fotku v profilu). Pager jde od nejstarší
     * k nejnovější jako u fotek chatu, takže se otevírá vpravo na té, kterou chat má teď.
     *
     * Stránka je fotka, ne zpráva: klíčem je id fotky, popisek žádný a z akcí jen uložení
     * a sdílení — reagovat, přeposlat ani smazat se profilovka odsud nedá.
     */
    private suspend fun loadProfilePhotos() {
        val own = profileRepository.isOwnAccount(chatId)
        // Vlastní fotky se nehlásí; cizí jen tam, kde to TDLib dovolí (`canBeReported`).
        val reportable = !own && profileRepository.canReportPhotos(chatId)
        profileRepository.profilePhotos(chatId)
            .onSuccess { photos ->
                val items = photos.asReversed().map { photo ->
                    ViewerItem(
                        messageId = photo.id,
                        media = photo.media,
                        caption = "",
                        isOutgoing = false,
                        senderName = null,
                        timestamp = photo.addedDate,
                    )
                }
                _state.update {
                    it.copy(
                        items = items,
                        initialMessageId = items.lastOrNull()?.messageId ?: it.initialMessageId,
                        initialPosition = items.size.takeIf { size -> size > 0 }?.let { size -> PhotoPosition(size, size) },
                        actions = items.associate { item -> item.messageId to PROFILE_PHOTO_ACTIONS },
                        ownProfilePhotos = own,
                        reportablePhotos = if (reportable) {
                            photos.mapNotNull { photo -> photo.reportFileId?.let { photo.id to it } }.toMap()
                        } else {
                            emptyMap()
                        },
                        reachedOldest = true,
                        reachedNewest = true,
                        loading = false,
                        error = if (items.isEmpty()) UiText(Res.string.media_profile_photos_none) else null,
                    )
                }
            }
            .onFailure { failure ->
                logger.w(failure) { "Profilové fotky chatu $chatId se nepodařilo načíst." }
                _state.update {
                    it.copy(loading = false, error = failure.userMessage(Res.string.media_photo_load_failed))
                }
            }
    }

    /** Záloha bez listování: jen otevřená zpráva, stejně jako před zavedením listování. */
    private suspend fun loadSingle() {
        repository.message(chatId, messageId)
            .onSuccess { message ->
                val photo = message.toViewerItem()
                val document = message.media is MessageMedia.Document
                if (document) single = true
                _state.update {
                    it.copy(
                        initialPosition = if (document) null else it.initialPosition,
                        items = listOfNotNull(photo),
                        reachedOldest = true,
                        reachedNewest = true,
                        loading = false,
                        // Jiný typ média zatím neumíme; prázdný stav by vypadal jako chyba
                        // načítání, tak se to řekne rovnou.
                        error = if (photo == null) UiText(Res.string.media_unsupported) else null,
                    )
                }
                if (photo != null) loadDetails(messageId)
            }
            .onFailure { failure ->
                logger.w(failure) { "Médium $messageId se nepodařilo načíst." }
                _state.update {
                    it.copy(loading = false, error = failure.userMessage(Res.string.media_photo_load_failed))
                }
            }
    }

    /** Pager se posunul. U okraje načteného výřezu se dotáhne další stránka. */
    fun onPageChanged(index: Int) {
        val items = _state.value.items
        if (items.isEmpty()) return
        items.getOrNull(index)?.let { loadDetails(it.messageId) }
        // Zvýrazněný text patří fotce, ze které se odlistovalo; po návratu by překvapil.
        if (_state.value.recognizedText?.messageId != items.getOrNull(index)?.messageId) closeRecognizedText()
        if (_state.value.photoDescription?.messageId != items.getOrNull(index)?.messageId) closePhotoDescription()
        if (index <= PRELOAD_DISTANCE) loadOlder()
        if (index >= items.lastIndex - PRELOAD_DISTANCE) loadNewer()
    }

    /**
     * Pruh náhledů ([Filmstrip]) ukazuje stránky [first]..[last]. U okraje načteného výřezu se
     * dotáhne další, stejně jako při listování — jinak by pruh skončil po dvaceti fotkách.
     */
    fun onStripVisible(first: Int, last: Int) {
        val items = _state.value.items
        if (items.isEmpty()) return
        if (first <= PRELOAD_DISTANCE) loadOlder()
        if (last >= items.lastIndex - PRELOAD_DISTANCE) loadNewer()
    }

    /**
     * Nabízené reakce a práva ke zprávě, na které uživatel stojí. Obojí **současně** a jen
     * jednou za zprávu; selhání jednoho druhé nezahodí — bez reakcí jde pořád smazat a naopak.
     */
    private fun loadDetails(messageId: Long) {
        // Reakce, sdílení, přeposlání i smazání by šly na CELOU anketu, ne na obrázek v ní —
        // přeposlat fotku z odpovědi by odeslalo anketu a smazat by smazalo anketu. Profilovka
        // zprávou není vůbec; co s ní jde, určuje [PROFILE_PHOTO_ACTIONS].
        if (notMessages) return
        if (!detailsRequested.add(messageId)) return
        viewModelScope.launch {
            val reactions = async { repository.availableReactions(chatId, messageId) }
            val actions = async { repository.messageActions(chatId, messageId) }
            val available = reactions.await().getOrNull()?.emoji
            val allowed = actions.await().getOrNull()
            _state.update {
                it.copy(
                    availableReactions = if (available != null) it.availableReactions + (messageId to available) else it.availableReactions,
                    actions = if (allowed != null) it.actions + (messageId to allowed) else it.actions,
                )
            }
        }
    }

    /**
     * Přepne reakci. Jako v konverzaci se stav **mění lokálně hned** a při selhání vrátí;
     * autoritativní stav stejně dorazí `interactionUpdates`.
     */
    fun toggleReaction(messageId: Long, emoji: String) {
        val item = _state.value.items.firstOrNull { it.messageId == messageId } ?: return
        val add = item.reactions.firstOrNull { it.emoji == emoji }?.isChosen != true
        val before = item.reactions
        updateItem(messageId) { it.copy(reactions = before.toggled(emoji, add)) }

        viewModelScope.launch {
            repository.setReaction(chatId, messageId, emoji, add).onFailure { failure ->
                updateItem(messageId) { it.copy(reactions = before) }
                _state.update { it.copy(notice = failure.reported(Res.string.conversation_reaction_failed)) }
            }
        }
    }

    /** Koš v horní liště: potvrzovací dialog, pokud server mazání vůbec dovolí. */
    fun requestDelete(messageId: Long) {
        val actions = _state.value.actions[messageId] ?: return
        if (!actions.canDeleteForSelf && !actions.canDeleteForAll) return
        _state.update {
            it.copy(
                pendingDelete = PendingDelete(
                    messageId = messageId,
                    canDeleteForSelf = actions.canDeleteForSelf,
                    canDeleteForAll = actions.canDeleteForAll,
                ),
            )
        }
    }

    fun dismissDelete() {
        _state.update { it.copy(pendingDelete = null) }
    }

    /**
     * Smaže fotku, na které uživatel stojí. **Jen tu jednu**, i když je z alba: v prohlížeči je
     * každá fotka samostatná stránka. Ze seznamu ji odebere až `deletedMessages` — předběhnout
     * server by znamenalo riskovat, že zmizí z obrazovky a na serveru zůstane.
     */
    fun confirmDelete(revoke: Boolean) {
        val pending = _state.value.pendingDelete ?: return
        _state.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            repository.deleteMessages(chatId, pending.messageIds, revoke).onFailure { failure ->
                _state.update { it.copy(notice = failure.reported(Res.string.conversation_delete_failed)) }
            }
        }
    }

    /**
     * Uloží snímek nebo video, na kterém uživatel stojí, do galerie.
     *
     * Běží ve `viewModelScope`: zavření prohlížeče ukládání zruší, dřív než uživatel uvidí,
     * jestli se povedlo.
     */
    fun save(messageId: Long) {
        viewModelScope.launch {
            val file = prepare(messageId, MediaTask.Gallery) ?: return@launch
            val saved = saver.save(file.path, file.fileName, file.isVideo)
            val done = if (autosavesToGallery) Res.string.media_saved else Res.string.media_saved_downloads
            // Soubor ve Stažených (desktop) jde z hlášky ukázat ve složce; galerie telefonu ne.
            _state.update { it.copy(savedFile = saved?.file) }
            finish(messageId, if (saved != null) done else Res.string.media_save_failed)
        }
    }

    /**
     * Jde médium, na kterém uživatel stojí, zkopírovat do schránky? Fotka všude, video jen tam,
     * kde schránka nese soubory ([copiesAnyFile], desktop).
     */
    fun canCopy(item: ViewerItem): Boolean = when (item.media) {
        is MessageMedia.Photo -> true
        is MessageMedia.Video -> copiesAnyFile
        else -> false
    }

    /**
     * Zkopíruje ostrou verzi média do schránky ([MediaSharer.copy]). Hláška jen tam, kde
     * zkopírování nepotvrdí systém sám (`TextClipboard.confirmsCopy`, Android 13+ ukáže náhled).
     */
    fun copy(messageId: Long) {
        viewModelScope.launch {
            val file = prepare(messageId, MediaTask.Copy, failure = Res.string.media_copy_failed) ?: return@launch
            val copied = sharer.copy(file.path, file.fileName, isImage = !file.isVideo)
            finish(
                messageId,
                when {
                    !copied -> Res.string.media_copy_failed
                    clipboard?.confirmsCopy == true -> null
                    else -> Res.string.media_copied
                },
            )
        }
    }

    /** Systémový dialog sdílení. Hláška jen při selhání — úspěch je vidět na dialogu samém. */
    fun share(messageId: Long) {
        viewModelScope.launch {
            val file = prepare(messageId, MediaTask.Share) ?: return@launch
            val shared = sharer.share(file.path, file.fileName)
            finish(messageId, if (shared) null else Res.string.media_share_failed)
        }
    }

    /**
     * Soubor pro „Uložit jako…". Výběr umístění je aktivita systému, takže ho spouští obrazovka;
     * odsud jen dostane stažený soubor a jméno. Výsledek hlásí zpátky [onSavedAs].
     */
    suspend fun prepareSaveAs(messageId: Long): PreparedMedia? = prepare(messageId, MediaTask.SaveAs)

    /** `null` = uživatel výběr umístění zavřel; to není chyba a hláška by jen rušila. */
    fun onSavedAs(messageId: Long, saved: Boolean?) {
        finish(
            messageId,
            when (saved) {
                true -> Res.string.media_saved_as
                false -> Res.string.media_save_failed
                null -> null
            },
        )
    }

    /**
     * Stažená ostrá verze média, na kterém uživatel stojí, se jménem pro galerii a sdílení.
     *
     * U fotky `fullFileId`, u souboru soubor sám — ne zmenšenina z bubliny. Když ještě není
     * stažená, počká se na ni; tlačítko akce po tu dobu točí ([MediaViewerState.busy]), takže
     * druhé ťuknutí nic nespustí. `null`, když se soubor stáhnout nepodařilo (hláška už je ve
     * stavu) nebo když s tou zprávou už něco běží.
     */
    private suspend fun prepare(
        messageId: Long,
        task: MediaTask,
        failure: StringResource = Res.string.media_save_failed,
    ): PreparedMedia? {
        val item = _state.value.items.firstOrNull { it.messageId == messageId } ?: return null
        if (messageId in _state.value.busy) return null
        val (fileId, isVideo) = when (val media = item.media) {
            is MessageMedia.Photo -> media.fullFileId to false
            is MessageMedia.Video -> media.fileId to true
            else -> return null
        }
        _state.update { it.copy(busy = it.busy + (messageId to task)) }

        val file = files.file(fileId).first { it is FileState.Ready || it is FileState.Failed }
        if (file !is FileState.Ready) {
            logger.w { "Soubor $fileId se nepodařilo stáhnout." }
            finish(messageId, failure)
            return null
        }
        return PreparedMedia(
            path = file.path,
            fileName = savedFileName(item.fileName, file.path, item.timestamp, TimeZone.currentSystemDefault()),
            isVideo = isVideo,
        )
    }

    /** Umí platforma text z fotky — jinak tlačítko v liště nebude. */
    val canRecognizeText: Boolean get() = textRecognizer != null

    val canMakeSticker: Boolean get() = stickerMaker != null

    /** Fotku stáhne a předá tvorbě samolepky ([MediaViewerState.stickerSource]). */
    fun makeSticker(messageId: Long) {
        val photo = _state.value.items.firstOrNull { it.messageId == messageId }?.media as? MessageMedia.Photo ?: return
        viewModelScope.launch {
            val file = prepare(messageId, MediaTask.Sticker, failure = Res.string.sticker_prepare_failed) ?: return@launch
            finish(messageId, null)
            _state.update { it.copy(stickerSource = StickerSource(file.path, photo.width, photo.height)) }
        }
    }

    fun stickerSourceHandled() = _state.update { it.copy(stickerSource = null) }

    /**
     * Text z fotky [messageId]: rozpozná ho v ostré verzi a zvýrazní na fotce
     * ([MediaViewerState.recognizedText]). Druhé ťuknutí zvýraznění schová.
     *
     * Ostrá verze proto, že ve zmenšenině z bubliny drobné písmo nepřečte ani model. Stahování
     * točí na místě tlačítka jako u uložení ([MediaTask.Text]).
     */
    fun recognizeText(messageId: Long) {
        val recognizer = textRecognizer ?: return
        if (_state.value.recognizedText?.messageId == messageId) {
            closeRecognizedText()
            return
        }
        viewModelScope.launch {
            val file = prepare(messageId, MediaTask.Text, failure = Res.string.media_text_failed) ?: return@launch
            val text = recognizer.recognize(file.path)
            when {
                text == null -> finish(messageId, Res.string.media_text_failed)
                text.isEmpty -> finish(messageId, Res.string.media_text_none)
                else -> {
                    finish(messageId, null)
                    // Oba panely leží na témže místě dole; nový nahradí starý.
                    closePhotoDescription()
                    _state.update { it.copy(recognizedText = PhotoText(messageId, text)) }
                }
            }
        }
    }

    fun closeRecognizedText() {
        _state.update { it.copy(recognizedText = null) }
    }

    /** Vybere řádek [index] rozpoznaného textu, nebo výběr zruší, když už vybraný byl. */
    fun toggleTextLine(index: Int) {
        _state.update { state ->
            val photoText = state.recognizedText ?: return@update state
            val selected = if (index in photoText.selected) photoText.selected - index else photoText.selected + index
            state.copy(recognizedText = photoText.copy(selected = selected))
        }
    }

    fun clearTextSelection() {
        _state.update { state -> state.copy(recognizedText = state.recognizedText?.copy(selected = emptySet())) }
    }

    /** Vybrané řádky, nebo celý rozpoznaný text, do schránky; potvrzení snackbarem, kde ho nedá systém. */
    fun copyRecognizedText() {
        val text = _state.value.recognizedText?.shownText ?: return
        val clipboard = clipboard ?: return
        viewModelScope.launch { clipboard.copy(text) }
        if (!clipboard.confirmsCopy) _state.update { it.copy(notice = UiText(Res.string.media_text_copied)) }
    }

    /**
     * Nabízet popis fotky? Jen se zapnutou AI v zařízení a modelem, který obraz bere (dnes Gemma 4,
     * `OnDeviceAi.canDescribeImage`). Sleduje se, protože model se může dostahovat, zatímco je
     * prohlížeč otevřený.
     */
    private fun watchPhotoDescription() {
        val ai = onDeviceAi ?: return
        val settings = settings ?: return
        viewModelScope.launch {
            combine(settings.onDeviceAiEnabled, ai.variantsFor(AiTask.PhotoDescription, settings)) { enabled, variant ->
                enabled && runCatching { ai.canDescribeImage(variant) }.getOrDefault(false)
            }.collect { can -> _state.update { it.copy(canDescribePhoto = can) } }
        }
    }

    /**
     * Popis fotky [messageId] modelem v telefonu ([MediaViewerState.photoDescription]) — pro
     * toho, kdo fotku nevidí, nebo kdo chce vědět, co na ní je. Druhé ťuknutí panel zavře.
     *
     * Ostrá verze jako u textu z fotky; stahování točí na místě ⋮ ([MediaTask.Describe]), psaní
     * modelu už ukazuje panel. Fotka telefon neopouští.
     */
    fun describePhoto(messageId: Long) {
        val ai = onDeviceAi ?: return
        val settings = settings ?: return
        if (_state.value.photoDescription?.messageId == messageId) {
            closePhotoDescription()
            return
        }
        describeJob?.cancel()
        // Fotka zprávy, ne profilovka, příběh ani médium ankety či článku — jen ta má id zprávy.
        val ofMessage = !notMessages
        // Jednou popsaná fotka se ukáže hned: model by psal tentýž popis znovu a pálil baterii.
        photoDescriptions?.takeIf { ofMessage }?.get(chatId, messageId)?.let { stored ->
            closeRecognizedText()
            _state.update { it.copy(photoDescription = PhotoDescription(messageId, text = stored, done = true)) }
            return
        }
        describeJob = viewModelScope.launch {
            val file = prepare(messageId, MediaTask.Describe, failure = Res.string.media_describe_failed) ?: return@launch
            finish(messageId, null)
            closeRecognizedText()
            _state.update { it.copy(photoDescription = PhotoDescription(messageId)) }
            try {
                var text = ""
                ai.describeImage(ai.variantFor(AiTask.PhotoDescription, settings), file.path, imageDescriptionPrompt(platformLanguageCode)).collect { generated ->
                    text = generated.text
                    updateDescription(messageId) { it.copy(text = text) }
                }
                updateDescription(messageId) {
                    if (text.isBlank()) it.copy(error = emptyOutputMessage) else it.copy(text = text.trim(), done = true)
                }
                if (text.isNotBlank() && ofMessage) photoDescriptions?.set(chatId, messageId, text.trim())
            } catch (e: OnDeviceAiException) {
                if (e.isReportable) logger.e(e) { "Popis fotky se nepovedl" } else logger.w(e) { "Popis fotky se nepovedl" }
                updateDescription(messageId) { it.copy(error = e.userMessage()) }
            }
        }
    }

    private inline fun updateDescription(messageId: Long, crossinline change: (PhotoDescription) -> PhotoDescription) {
        _state.update { state ->
            val description = state.photoDescription?.takeIf { it.messageId == messageId } ?: return@update state
            state.copy(photoDescription = change(description))
        }
    }

    fun closePhotoDescription() {
        describeJob?.cancel()
        describeJob = null
        _state.update { state ->
            // Zrušené stahování by jinak nechalo ⋮ točit navždy (`prepare` busy nesundá).
            val stuck = state.busy.filterValues { it == MediaTask.Describe }.keys
            state.copy(photoDescription = null, busy = state.busy - stuck)
        }
    }

    /** Hotový popis do schránky; potvrzení snackbarem, kde ho nedá systém. */
    fun copyPhotoDescription() {
        val text = _state.value.photoDescription?.takeIf { it.done }?.text ?: return
        val clipboard = clipboard ?: return
        viewModelScope.launch { clipboard.copy(text) }
        if (!clipboard.confirmsCopy) _state.update { it.copy(notice = UiText(Res.string.media_text_copied)) }
    }

    private fun finish(messageId: Long, notice: StringResource?) {
        _state.update {
            it.copy(
                busy = it.busy - messageId,
                notice = notice?.let { resource -> UiText(resource) } ?: it.notice,
            )
        }
    }

    /** Systém zápis do galerie nepovolil. Bez hlášky by ťuknutí vypadalo, že nic neudělalo. */
    fun onSaveDenied() {
        _state.update { it.copy(notice = UiText(Res.string.media_save_denied)) }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null, savedFile = null) }
    }

    private fun loadOlder() {
        val current = _state.value
        if (loadingOlder || current.reachedOldest) return
        val oldest = current.items.firstOrNull() ?: return
        loadingOlder = true

        viewModelScope.launch {
            repository.visualMedia(chatId, oldest.messageId, older = PAGE_SIZE, newer = 0)
                .onSuccess { messages ->
                    // Výsledek obsahuje i výchozí zprávu, ta se zahodí.
                    val older = messages.filter { it.id < oldest.messageId }.mapNotNull { it.toViewerItem() }
                    _state.update {
                        it.copy(
                            items = older + it.items,
                            // Konec se pozná jen podle prázdné odpovědi, příznak TDLib nedává.
                            reachedOldest = older.isEmpty(),
                        )
                    }
                }
            loadingOlder = false
        }
    }

    private fun loadNewer() {
        val current = _state.value
        if (loadingNewer || current.reachedNewest) return
        val newest = current.items.lastOrNull() ?: return
        loadingNewer = true

        viewModelScope.launch {
            repository.visualMedia(chatId, newest.messageId, older = 0, newer = PAGE_SIZE)
                .onSuccess { messages ->
                    val newer = messages.filter { it.id > newest.messageId }.mapNotNull { it.toViewerItem() }
                    _state.update {
                        it.copy(
                            items = it.items + newer,
                            reachedNewest = newer.isEmpty(),
                        )
                    }
                }
            loadingNewer = false
        }
    }

    private fun updateItem(messageId: Long, transform: (ViewerItem) -> ViewerItem) {
        _state.update { state ->
            if (state.items.none { it.messageId == messageId }) return@update state
            state.copy(items = state.items.map { if (it.messageId == messageId) transform(it) else it })
        }
    }

    /** Surovou příčinu do logu, uživateli naše věta. Server řekl ne — očekávané selhání, proto `w`. */
    private fun Throwable.reported(message: StringResource): UiText {
        logger.w(this) { message.key }
        return userMessage(message)
    }

    private fun ChatMessage.toViewerItem(): ViewerItem? {
        val story = this.media as? MessageMedia.Story
        val media = when (val media = media) {
            is MessageMedia.Photo, is MessageMedia.Video -> media
            is MessageMedia.Document -> media.asViewerMedia() ?: return null
            // Sdílený příběh je fotka nebo video; prohlížeč ukáže to, popiskem je text příběhu.
            is MessageMedia.Story -> media.content?.takeIf { it is MessageMedia.Photo || it is MessageMedia.Video }
                ?: return null
            else -> return null
        }
        return ViewerItem(
            messageId = id,
            media = media,
            caption = story?.caption ?: text,
            isOutgoing = isOutgoing,
            senderName = senderName,
            timestamp = timestamp,
            reactions = reactions,
            fileName = this.media?.originalFileName,
        )
    }

    /**
     * Počká, až je mizející médium celé na disku — teprve pak smí začít odpočet.
     *
     * TDLib počítá odpočet od `openMessageContent`. Kdyby se volal hned při otevření prohlížeče,
     * ukrojilo by stahování z doby, kterou dal odesílatel: u videa, kterému TDLib časovač
     * prodlouží jen na délku + 1 s, se pak prohlížeč zavřel dřív, než video dohrálo. Oficiální
     * klient mizející médium taky otevírá až stažené. Video se proto u mizejícího média ani
     * nestreamuje (`VideoPage(streaming = false)`) — přehrávání a odpočet začnou naráz.
     */
    private suspend fun awaitSelfDestructMedia(media: MessageMedia): Boolean {
        val fileId = when (media) {
            is MessageMedia.Photo -> media.fullFileId
            is MessageMedia.Video -> media.fileId
            else -> return true
        }
        val file = files.file(fileId).first { it is FileState.Ready || it is FileState.Failed }
        if (file is FileState.Failed) {
            logger.w { "Mizející médium se nepodařilo stáhnout, odpočet se nespouští." }
            return false
        }
        return true
    }

    /** Uživatel zavírá prohlížeč (gestem, křížkem, tlačítkem Zpět). */
    fun onDismiss() {
        if (_state.value.isSelfDestruct && !ownSelfDestruct) {
            val type = _state.value.selfDestructType
            val remaining = _state.value.remainingSeconds ?: timers.remainingSeconds(messageId)
            // Jednorázové médium, které se ani nestáhlo, uživatel neviděl — nespotřebuje se.
            if ((type !is SelfDestructType.Timer && viewOnceShown) || (remaining != null && remaining <= 0)) {
                timers.markConsumed(messageId)
            }
        }
    }

    override fun onCleared() {
        // Jednorázové zhlédnutí se TDLib hlásí až teď: `openMessageContent` ho vyřídí okamžitě,
        // smaže soubory a pošle vypršelý obsah — nahlášené při otevření by zmizelo uživateli
        // pod rukama. `appScope`, protože `viewModelScope` je v tuhle chvíli zrušený.
        if (viewOnceShown) {
            timers.markConsumed(messageId)
            appScope.launch { repository.openContent(chatId, messageId) }
        }
    }

    /**
     * Starší vlastní profilovka bude zase hlavní. Bez potvrzení — jde to vrátit stejnou cestou.
     * Seznam se pak čte znovu: server fotku přesune na začátek historie.
     */
    fun setMainProfilePhoto(photoId: Long) {
        if (!_state.value.ownProfilePhotos) return
        viewModelScope.launch {
            profileRepository.setMainProfilePhoto(photoId)
                .onSuccess {
                    loadProfilePhotos()
                    // Fotka nastavená znovu dostane NOVÉ id a je teď aktuální, tedy poslední.
                    _state.update {
                        it.copy(notice = UiText(Res.string.media_profile_photo_set), focusMessageId = it.items.lastOrNull()?.messageId)
                    }
                }
                .onFailure { failure ->
                    _state.update { it.copy(notice = failure.reported(Res.string.media_profile_photo_failed)) }
                }
        }
    }

    fun focusShown() {
        _state.update { it.copy(focusMessageId = null) }
    }

    /** „Nahlásit fotku" v ⋮: napřed výběr důvodu. */
    fun requestReportProfilePhoto(photoId: Long) {
        if (photoId in _state.value.reportablePhotos) _state.update { it.copy(reportingPhoto = photoId) }
    }

    fun dismissReportProfilePhoto() {
        _state.update { it.copy(reportingPhoto = null) }
    }

    /** Nabídnout „Samolepky na fotce" u stránky [item]: jen fotka nebo video, které je mají. */
    fun hasAttachedStickers(item: ViewerItem): Boolean = stickers != null && when (val media = item.media) {
        is MessageMedia.Photo -> media.hasStickers
        is MessageMedia.Video -> media.hasStickers
        else -> false
    }

    /**
     * Sady samolepek nalepených na médium zprávy [messageId] (`getAttachedStickerSets`). Jediná
     * sada se otevře rovnou, víc se nabídne seznamem.
     */
    fun showAttachedStickers(messageId: Long) {
        val repository = stickers ?: return
        val media = _state.value.items.firstOrNull { it.messageId == messageId }?.media ?: return
        val fileId = when (media) {
            is MessageMedia.Photo -> media.fullFileId
            is MessageMedia.Video -> media.fileId
            else -> return
        }
        viewModelScope.launch {
            repository.attachedStickerSets(fileId)
                .onSuccess { sets ->
                    _state.update {
                        when (sets.size) {
                            0 -> it.copy(notice = UiText(Res.string.media_attached_stickers_none))
                            1 -> it.copy(openStickerSetId = sets.single().id)
                            else -> it.copy(attachedStickerSets = sets)
                        }
                    }
                }
                .onFailure { failure ->
                    logger.w(failure) { "Sady samolepek na fotce ($fileId) se nepodařilo načíst." }
                    _state.update { it.copy(notice = failure.userMessage(Res.string.media_attached_stickers_failed)) }
                }
        }
    }

    fun openStickerSet(setId: Long) = _state.update { it.copy(attachedStickerSets = null, openStickerSetId = setId) }

    fun dismissAttachedStickers() = _state.update { it.copy(attachedStickerSets = null, openStickerSetId = null) }

    fun reportProfilePhoto(reason: PhotoReportReason, text: String) {
        val photoId = _state.value.reportingPhoto ?: return
        val fileId = _state.value.reportablePhotos[photoId] ?: return
        _state.update { it.copy(reportingPhoto = null) }
        viewModelScope.launch {
            profileRepository.reportPhoto(chatId, fileId, reason, text.trim())
                .onSuccess { _state.update { it.copy(notice = UiText(Res.string.media_profile_photo_reported)) } }
                .onFailure { failure ->
                    _state.update { it.copy(notice = failure.reported(Res.string.media_profile_photo_report_failed)) }
                }
        }
    }

    fun requestDeleteProfilePhoto(photoId: Long) {
        if (_state.value.ownProfilePhotos) _state.update { it.copy(pendingProfilePhotoDelete = photoId) }
    }

    fun dismissDeleteProfilePhoto() {
        _state.update { it.copy(pendingProfilePhotoDelete = null) }
    }

    /** Smaže vlastní profilovku; po poslední se prohlížeč zavře, jinak se seznam čte znovu. */
    fun confirmDeleteProfilePhoto() {
        val photoId = _state.value.pendingProfilePhotoDelete ?: return
        _state.update { it.copy(pendingProfilePhotoDelete = null) }
        viewModelScope.launch {
            profileRepository.deleteProfilePhoto(photoId)
                .onSuccess {
                    if (_state.value.items.size <= 1) {
                        _state.update { it.copy(closed = true) }
                    } else {
                        loadProfilePhotos()
                    }
                }
                .onFailure { failure ->
                    _state.update { it.copy(notice = failure.reported(Res.string.media_profile_photo_failed)) }
                }
        }
    }

    private companion object {
        /**
         * Profilovka jde uložit a sdílet, jako v oficiálních klientech; ochranu obsahu jako
         * zprávy v kanálu nemá.
         */
        val PROFILE_PHOTO_ACTIONS = MessageActions(
            canReply = false,
            canCopy = false,
            canDeleteForSelf = false,
            canDeleteForAll = false,
            canEdit = false,
            canPin = false,
            canForward = false,
            canSave = true,
        )

        /** Kolik fotek se dotahuje najednou na každou stranu. */
        const val PAGE_SIZE = 20

        /** Jak blízko okraje načteného výřezu se začne dotahovat další stránka. */
        const val PRELOAD_DISTANCE = 3
    }
}

/**
 * Obrázek nebo video poslané jako soubor, převedené na stránku prohlížeče.
 *
 * Ostrá verze je soubor sám; náhled od serveru poslouží jako okamžitý obrázek, než se dotáhne
 * (u fotky je to role `fileId`). Rozměry jsou z náhledu — jiné TDLib u dokumentu nedává — a bez
 * náhledu zůstanou nulové; karta pak dostane obrázek vepsaný, ne oříznutý.
 */
internal fun MessageMedia.Document.asViewerMedia(): MessageMedia? {
    if (!opensInViewer(fileName, mimeType)) return null
    return when (documentKind(fileName, mimeType)) {
        DocumentKind.Image -> MessageMedia.Photo(
            fileId = thumbnailFileId ?: fileId,
            thumbnailFileId = null,
            fullFileId = fileId,
            width = thumbnailWidth,
            height = thumbnailHeight,
        )
        DocumentKind.Video -> MessageMedia.Video(
            fileId = fileId,
            thumbnailFileId = thumbnailFileId,
            width = thumbnailWidth,
            height = thumbnailHeight,
            // Délku dokument nenese; přehrávač ji zjistí sám, jakmile video otevře.
            durationSeconds = 0,
            // MP4 poslané jako soubor (desktop tak posílá každé video) se streamuje taky.
            mimeType = mimeType,
            fileName = fileName.ifBlank { null },
        )
        DocumentKind.Other -> null
    }
}

/** Co s médiem právě běží; podle toho se točí to tlačítko, na které uživatel ťukl. */
enum class MediaTask { Gallery, Share, Copy, SaveAs, Text, Describe, Sticker }

/** Stažená fotka, ze které se má udělat samolepka — obrazovka ji předá tvorbě samolepky. */
data class StickerSource(val path: String, val width: Int, val height: Int)

/**
 * Text rozpoznaný na fotce [messageId] — zvýrazní se jen na ní, jinde ne. [selected] jsou řádky
 * vybrané ťuknutím na fotce (indexy do [RecognizedText.lines]); prázdný = platí celý text.
 */
data class PhotoText(val messageId: Long, val text: RecognizedText, val selected: Set<Int> = emptySet()) {
    /** Co ukáže panel a co se zkopíruje: vybrané řádky, jinak všechno. */
    val shownText: String get() = text.text(selected)
}

/**
 * Popis fotky [messageId] od modelu v telefonu. [text] roste, jak model píše; [done] = dopsáno,
 * [error] = nepovedlo se (text pak nic neznamená).
 */
data class PhotoDescription(
    val messageId: Long,
    val text: String = "",
    val done: Boolean = false,
    val error: UiText? = null,
)

/** Stažený soubor připravený k uložení nebo sdílení, se jménem, pod kterým se má objevit. */
data class PreparedMedia(val path: String, val fileName: String, val isVideo: Boolean)

/**
 * Jméno souboru od odesílatele, pod kterým se médium uloží a sdílí. Soubor a video ho nesou,
 * fotka nikdy (server ji překóduje do vlastních velikostí a jméno zahodí).
 */
internal val MessageMedia.originalFileName: String?
    get() = when (this) {
        is MessageMedia.Document -> fileName.ifBlank { null }
        is MessageMedia.Video -> fileName
        else -> null
    }

data class ViewerItem(
    val messageId: Long,
    /** [MessageMedia.Photo], nebo [MessageMedia.Video] — i soubor se na jedno z nich převede. */
    val media: MessageMedia,
    /** Popisek pod fotkou. Prázdný u fotky bez textu. */
    val caption: String,
    val isOutgoing: Boolean,
    /** Vyplněné jen ve skupinách; v soukromém chatu je odesílatelem chat sám ([MediaViewerState.chatTitle]). */
    val senderName: String?,
    val timestamp: Int,
    val reactions: List<MessageReaction> = emptyList(),
    /** Původní jméno souboru od odesílatele ([originalFileName]); fotka ho nemá nikdy, video jen někdy. */
    val fileName: String? = null,
)

data class MediaViewerState(
    /** Zpráva, na které se prohlížeč otevřel. Pager na ní startuje. */
    val initialMessageId: Long,
    /** Fotka připravená pro tvorbu samolepky; obrazovka ji vyzvedne a otevře tvorbu. */
    val stickerSource: StickerSource? = null,
    /** Načtený výřez fotek chatu, od nejstarší. Pager jde zleva doprava k novějším. */
    val items: List<ViewerItem> = emptyList(),
    /**
     * Pořadí otevřené fotky v chatu. Počítadlo u ostatních stránek se od něj odvozuje posunem
     * indexu — výřez je souvislý, takže to sedí.
     */
    val initialPosition: PhotoPosition? = null,
    val chatTitle: String? = null,
    /** Reakce, které chat u zprávy nabízí, podle id. Chybí, dokud se nenačtou. */
    val availableReactions: Map<Long, List<String>> = emptyMap(),
    /** Co se se zprávou smí, podle id. Chybí, dokud se nenačte — akce se do té doby nenabízejí. */
    val actions: Map<Long, MessageActions> = emptyMap(),
    val pendingDelete: PendingDelete? = null,
    /**
     * Profilovky vlastního účtu — jde je nastavit jako hlavní a mazat (`setMainProfilePhoto`,
     * `deleteProfilePhoto`). Cizí profilovky a fotky skupin ne: TDLib to umí jen u vlastního účtu.
     */
    val ownProfilePhotos: Boolean = false,
    /** Cizí profilovky, které jde nahlásit: id fotky → plná velikost (`reportChatPhoto`). */
    val reportablePhotos: Map<Long, Int> = emptyMap(),
    /** Profilovka, u které se vybírá důvod nahlášení. */
    val reportingPhoto: Long? = null,
    /** Víc sad samolepek na fotce — vybírá se, kterou otevřít. */
    val attachedStickerSets: List<StickerSetSummary>? = null,
    /** Otevřená sada samolepek (z fotky). */
    val openStickerSetId: Long? = null,
    /** Vlastní profilovka čekající na potvrzení smazání. */
    val pendingProfilePhotoDelete: Long? = null,
    /**
     * Stránka, na kterou má pager dojet — jednorázově, obrazovka ji po posunu vynuluje
     * ([MediaViewerViewModel.focusShown]). Fotka nastavená jako hlavní se přesune na konec
     * (a dostane nové id) a pager by jinak zůstal na indexu, tedy na jiné fotce.
     */
    val focusMessageId: Long? = null,
    /** Zprávy, se kterými právě běží uložení nebo sdílení, a co to je — točí se místo tlačítka. */
    val busy: Map<Long, MediaTask> = emptyMap(),
    /** Jednorázová hláška do snackbaru (nepovedená reakce, mazání, uložení). */
    val notice: UiText? = null,
    /**
     * Soubor, který hláška [notice] právě uložila na disk (desktop: Stažené) — snackbar k ní
     * nabídne „Zobrazit ve složce". `null` = hláška bez akce.
     */
    val savedFile: String? = null,
    /** Text z fotky, zvýrazněný na ní; `null` = žádný. */
    val recognizedText: PhotoText? = null,
    /** Nabídnout popis fotky modelem v telefonu ([MediaViewerViewModel.describePhoto]). */
    val canDescribePhoto: Boolean = false,
    /** Popis fotky od modelu v telefonu; `null` = žádný. */
    val photoDescription: PhotoDescription? = null,
    /** Zmizela poslední fotka (smazaná odsud nebo odjinud) — prohlížeč se zavře. */
    val closed: Boolean = false,
    val reachedOldest: Boolean = false,
    val reachedNewest: Boolean = false,
    val loading: Boolean = true,
    val error: UiText? = null,
    val isSelfDestruct: Boolean = false,
    val selfDestructType: SelfDestructType? = null,
    val remainingSeconds: Int? = null,
    /** Médium reklamy: štítek místo data a tlačítko pod snímkem; `null` mimo reklamu. */
    val sponsored: ViewerSponsored? = null,
)

/** Reklama v prohlížeči: popisek tlačítka od Telegramu (nepřekládá se), cíl a druh štítku. */
data class ViewerSponsored(val buttonText: String, val url: String, val isRecommended: Boolean)
