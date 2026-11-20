package eu.tintera.plume.features.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.drafts.CommentDrafts
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.TopicDrafts
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_action_failed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Rozepsané zprávy v chatech, tématech skupin a vláknech komentářů v jednom seznamu, nejnovější
 * první. Počet ukazuje menu účtu ([ChatsViewModel.drafts]), seznam přehled konceptů
 * ([DraftsViewModel]) — oba ze stejného zdroje, ať se nerozejdou.
 */
internal fun draftItems(
    repository: ChatRepository,
    topicDrafts: TopicDrafts?,
    commentDrafts: CommentDrafts?,
): Flow<List<DraftItem>> = combine(
    repository.drafts,
    topicDrafts?.drafts ?: flowOf(emptyList()),
    commentDrafts?.drafts?.onStart { emit(emptyList()) } ?: flowOf(emptyList()),
) { chats, topics, comments ->
    (chats.mapNotNull { chat -> chat.draft?.let { DraftItem(chat, topic = null, draft = it) } } +
        topics.map { DraftItem(it.chat, DraftTopic(it.topicId, it.topicName), it.draft) } +
        comments.map { DraftItem(it.chat, topic = null, draft = it.draft, thread = DraftThread(it.threadId, it.post)) })
        .sortedByDescending { it.draft.timestamp }
}

/**
 * Přehled konceptů ([eu.tintera.plume.core.navigation.DraftsRoute]). Vlastní ViewModel, ne ten
 * seznamu chatů: sheet je navigační destinace a má vlastní úložiště ViewModelů.
 *
 * **Mazání běží v [workScope] (rozsah aplikace), ne ve `viewModelScope`.** Tenhle ViewModel
 * zaniká se zavřením sheetu, a zavřený přehled nesmí smazání přerušit — „Smazat vše" maže
 * koncepty v tématech a vláknech po jednom a sheet se zavře sám, jakmile zmizí ty v chatech.
 * Dřív to hlídal ViewModel seznamu chatů, který žije pořád.
 */
class DraftsViewModel(
    private val repository: ChatRepository,
    private val topicDrafts: TopicDrafts? = null,
    private val commentDrafts: CommentDrafts? = null,
    private val workScope: CoroutineScope,
) : ViewModel() {
    private val logger = Logger.withTag("Drafts")

    /** `null`, dokud se nenačtou: prázdný seznam znamená „žádné koncepty" a přehled se při něm zavře. */
    val drafts: StateFlow<List<DraftItem>?> = draftItems(repository, topicDrafts, commentDrafts)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _deleted = MutableStateFlow<DraftDeleted?>(null)

    /** Koncept smazaný z přehledu — přehled ukáže snackbar s „Vrátit" a událost vynuluje. */
    val deleted: StateFlow<DraftDeleted?> = _deleted.asStateFlow()

    private val _error = MutableStateFlow<UiText?>(null)

    /** Nepovedené smazání nebo vrácení — hláška ve snackbaru přehledu. */
    val error: StateFlow<UiText?> = _error.asStateFlow()

    /** Otevření přehledu: koncepty ve vláknech se znovu ověří (TDLib je nehlásí). */
    fun refresh() {
        commentDrafts?.refresh()
    }

    /**
     * Smaže koncept z přehledu. Výsledek se ohlásí VŽDY, i bez čeho vracet — přehled podle něj
     * pozná, že smazání doběhlo.
     */
    fun delete(item: DraftItem) {
        val chatId = item.chat.id
        workScope.launch {
            val topicId = item.topic?.id
            val threadId = item.thread?.id
            val deleted = when {
                threadId != null -> commentDrafts?.delete(chatId, threadId) ?: Result.success(null)
                topicId != null -> topicDrafts?.delete(chatId, topicId) ?: Result.success(null)
                else -> repository.deleteDraft(chatId)
            }
            val restore = deleted
                .onFailure {
                    logger.w(it) { "Smazání konceptu v chatu $chatId (téma $topicId) selhalo." }
                    reportFailure(it)
                }
                .getOrNull()
            _deleted.value = DraftDeleted(chatId, restore)
        }
    }

    /**
     * „Smazat vše". Koncepty v tématech se po hromadném smazání dočistí po jednom — TDLib je
     * vede zvlášť (`ForumTopic.draftMessage`) a dokumentace `clearAllDraftMessages` mluví jen
     * o chatech. Přehled se zavře sám, až zmizí poslední koncept.
     */
    fun deleteAll(items: List<DraftItem>) {
        workScope.launch {
            repository.clearAllDrafts().onFailure {
                logger.w(it) { "Hromadné smazání konceptů selhalo." }
                reportFailure(it)
                return@launch
            }
            items.mapNotNull { item -> item.topic?.let { item.chat.id to it.id } }.forEach { (chatId, topicId) ->
                topicDrafts?.delete(chatId, topicId)?.onFailure { logger.w(it) { "Koncept v tématu $topicId se nesmazal." } }
            }
            // Koncepty ve vláknech komentářů taky po jednom, ze stejného důvodu jako témata.
            items.mapNotNull { item -> item.thread?.let { item.chat.id to it.id } }.forEach { (chatId, threadId) ->
                commentDrafts?.delete(chatId, threadId)?.onFailure { logger.w(it) { "Koncept ve vlákně $threadId se nesmazal." } }
            }
        }
    }

    fun consumeDeleted() {
        _deleted.value = null
    }

    /** „Vrátit" ve snackbaru přehledu. */
    fun undo(deleted: DraftDeleted) {
        val restore = deleted.restore ?: return
        workScope.launch {
            restore.restore().onFailure {
                logger.w(it) { "Vrácení konceptu v chatu ${deleted.chatId} selhalo." }
                reportFailure(it)
            }
        }
    }

    fun consumeError() {
        _error.value = null
    }

    private fun reportFailure(failure: Throwable) {
        _error.value = failure.userMessage(Res.string.chats_action_failed)
    }
}
