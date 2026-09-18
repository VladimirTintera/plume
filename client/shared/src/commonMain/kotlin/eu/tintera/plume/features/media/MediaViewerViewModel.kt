package eu.tintera.plume.features.media

import co.touchlab.kermit.Logger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.files.DocumentKind
import eu.tintera.plume.core.files.documentKind
import eu.tintera.plume.core.files.opensInViewer
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.MessageActions
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.MessageReaction
import eu.tintera.plume.core.telegram.PhotoPosition
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.features.conversation.PendingDelete
import eu.tintera.plume.features.conversation.toggled
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_delete_failed
import eu.tintera.plume.resources.conversation_reaction_failed
import eu.tintera.plume.resources.media_photo_load_failed
import eu.tintera.plume.resources.media_unsupported
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val repository: ConversationRepository,
) : ViewModel() {

    private val logger = Logger.withTag("MediaViewer")

    private val _state = MutableStateFlow(MediaViewerState(initialMessageId = messageId))
    val state: StateFlow<MediaViewerState> = _state.asStateFlow()

    /** Probíhající dotahování. Stránkuje se z `onPageChanged`, které chodí při každém posunu. */
    private var loadingOlder = false
    private var loadingNewer = false

    /**
     * Otevřený je soubor (obrázek nebo video poslané jako dokument). Listuje se jen fotkami
     * a videi, mezi které nepatří, takže se ukazuje sám a bez pořadí „N z M" — TDLib by ho
     * počítala mezi fotkami.
     */
    private var single = false

    /** Zprávy, u kterých se podrobnosti (reakce, práva) už načítají nebo načetly. */
    private val detailsRequested = mutableSetOf<Long>()

    init {
        viewModelScope.launch { loadInitial() }
        viewModelScope.launch {
            repository.visualMediaPosition(chatId, messageId)
                .onSuccess { position -> if (!single) _state.update { it.copy(initialPosition = position) } }
        }
        viewModelScope.launch {
            // Název chatu je titulek u příchozích fotek v soukromém chatu, kde TDLib jméno
            // odesílatele nevyplňuje.
            repository.chatInfo(chatId).onSuccess { info -> _state.update { it.copy(chatTitle = info.title) } }
        }
        viewModelScope.launch {
            repository.reactionUpdates(chatId).collect { update ->
                updateItem(update.messageId) { it.copy(reactions = update.reactions) }
            }
        }
        viewModelScope.launch {
            repository.editedMessages(chatId).collect { message ->
                updateItem(message.id) { it.copy(caption = message.text) }
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
        repository.visualMedia(chatId, messageId, older = PAGE_SIZE, newer = PAGE_SIZE)
            .onSuccess { messages ->
                val items = messages.mapNotNull { it.toViewerItem() }
                if (items.none { it.messageId == messageId }) {
                    // Otevřená zpráva mezi fotkami není — jiný typ média, nebo ji vyhledávání
                    // ještě nezná. Pak se aspoň zkusí ukázat samotná.
                    loadSingle()
                    return
                }
                _state.update { it.copy(items = items, loading = false) }
                loadDetails(messageId)
            }
            .onFailure { loadSingle() }
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
        if (index <= PRELOAD_DISTANCE) loadOlder()
        if (index >= items.lastIndex - PRELOAD_DISTANCE) loadNewer()
    }

    /**
     * Nabízené reakce a práva ke zprávě, na které uživatel stojí. Obojí **současně** a jen
     * jednou za zprávu; selhání jednoho druhé nezahodí — bez reakcí jde pořád smazat a naopak.
     */
    private fun loadDetails(messageId: Long) {
        if (!detailsRequested.add(messageId)) return
        viewModelScope.launch {
            val reactions = async { repository.availableReactions(chatId, messageId) }
            val actions = async { repository.messageActions(chatId, messageId) }
            val available = reactions.await().getOrNull()
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
     * autoritativní stav stejně dorazí `reactionUpdates`.
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

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
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
        val media = when (val media = media) {
            is MessageMedia.Photo, is MessageMedia.Video -> media
            is MessageMedia.Document -> media.asViewerMedia() ?: return null
            else -> return null
        }
        return ViewerItem(
            messageId = id,
            media = media,
            caption = text,
            isOutgoing = isOutgoing,
            senderName = senderName,
            timestamp = timestamp,
            reactions = reactions,
        )
    }

    private companion object {
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
        )
        DocumentKind.Other -> null
    }
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
)

data class MediaViewerState(
    /** Zpráva, na které se prohlížeč otevřel. Pager na ní startuje. */
    val initialMessageId: Long,
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
    /** Jednorázová hláška do snackbaru (nepovedená reakce, mazání). */
    val notice: UiText? = null,
    /** Zmizela poslední fotka (smazaná odsud nebo odjinud) — prohlížeč se zavře. */
    val closed: Boolean = false,
    val reachedOldest: Boolean = false,
    val reachedNewest: Boolean = false,
    val loading: Boolean = true,
    val error: UiText? = null,
)
