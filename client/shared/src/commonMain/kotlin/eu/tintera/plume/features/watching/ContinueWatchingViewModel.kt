package eu.tintera.plume.features.watching

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.video.ContinueWatching
import eu.tintera.plume.core.video.RemovedPosition
import eu.tintera.plume.core.video.VideoPosition
import eu.tintera.plume.core.video.VideoSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/** Rozkoukané video: pozice a zpráva, ze které hrálo. */
@Immutable
data class WatchingItem(
    val position: VideoPosition,
    val chatId: Long,
    val messageId: Long,
    val chatTitle: String,
    /** Uložené zprávy: název chatu je jméno účtu, řádek ukáže „Uložené zprávy" jako jinde. */
    val isSavedMessages: Boolean,
    val isOutgoing: Boolean,
    val senderName: String?,
    val video: MessageMedia.Video,
) {
    /** Klíč seznamu: soubor je v seznamu nejvýš jednou. */
    val key: String get() = position.key
}

data class ContinueWatchingState(
    /** Nejnověji sledované první; `null`, dokud se poprvé nenačtou zprávy. */
    val items: List<WatchingItem>? = null,
    /** Právě odebrané — obrazovka ukáže snackbar s „Vrátit" a událost spotřebuje. */
    val removal: RemovedPosition? = null,
)

/**
 * Rozkoukaná videa (menu účtu): pamatované pozice účtu ([ContinueWatching]) a k nim zprávy
 * s videem. Zpráva se načte jednou a pamatuje se — pozice se během přehrávání zapisuje každých
 * pár vteřin a seznam by jinak pokaždé znovu četl všechny zprávy.
 *
 * Zpráva, která už není (smazaná, chat opuštěný) nebo v ní není video, se v seznamu tiše
 * nevykreslí; pozice zůstává, dokud ji nevytlačí novější (jako proužek v bublině).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContinueWatchingViewModel(
    private val watching: ContinueWatching,
    private val conversations: ConversationRepository,
    private val chats: ChatRepository,
) : ViewModel() {

    /** Načtené zprávy; `null` = zpráva není nebo v ní video není. Jen na hlavním vlákně. */
    private val loaded = mutableMapOf<VideoSource, WatchingItem?>()
    private val removal = MutableStateFlow<RemovedPosition?>(null)

    val state: StateFlow<ContinueWatchingState> = combine(
        watching.all.mapLatest { positions -> resolve(positions) },
        removal,
    ) { items, removed -> ContinueWatchingState(items = items, removal = removed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ContinueWatchingState())

    fun remove(item: WatchingItem) {
        watching.remove(item.key)?.let { removal.value = it }
    }

    fun consumeRemoval() {
        removal.value = null
    }

    fun undo(removed: RemovedPosition) = watching.restore(removed)

    private suspend fun resolve(positions: List<VideoPosition>): List<WatchingItem> {
        val missing = positions.mapNotNull { it.source }.filterNot { it in loaded }
        if (missing.isNotEmpty()) {
            val titles = chats.summaries(missing.map { it.chatId }.distinct()).associate { it.id to it.title }
            missing.forEach { source ->
                val message = conversations.message(source.chatId, source.messageId).getOrNull()
                val video = message?.media as? MessageMedia.Video
                loaded[source] = if (message != null && video != null) {
                    WatchingItem(
                        position = VideoPosition("", 0, 0),
                        chatId = source.chatId,
                        messageId = source.messageId,
                        chatTitle = titles[source.chatId].orEmpty(),
                        isSavedMessages = source.chatId == chats.savedMessagesChatId.value,
                        isOutgoing = message.isOutgoing,
                        senderName = message.senderName,
                        video = video,
                    )
                } else {
                    null
                }
            }
        }
        return positions.mapNotNull { position -> position.source?.let(loaded::get)?.copy(position = position) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
