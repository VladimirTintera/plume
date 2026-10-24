package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.toResult
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.SavedMessagesTopic
import dev.g000sha256.tdl.dto.SavedMessagesTopicTypeAuthorHidden
import dev.g000sha256.tdl.dto.SavedMessagesTopicTypeMyNotes
import dev.g000sha256.tdl.dto.SavedMessagesTopicTypeSavedFromChat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Témata Uložených zpráv nad `UpdateSavedMessagesTopic`.
 *
 * **Eagerně** (`createdAtStart`): TDLib témata NEVRACÍ dotazem, posílá je jen updaty — po
 * `loadSavedMessagesTopics` i kdykoli se změní (nová uložená zpráva, připnutí). Kdo by se
 * přihlásil k odběru pozdě, část by minul.
 */
class TdlSavedTopicsRepository(
    private val holder: TelegramClientHolder,
    scope: CoroutineScope,
) : SavedTopicsRepository {

    private val logger = Logger.withTag("SavedTopics")
    private val client: TdlClient get() = holder.client

    private val mutex = Mutex()
    private val byId = mutableMapOf<Long, SavedTopic>()
    private val _topics = MutableStateFlow<List<SavedTopic>?>(null)
    override val topics: StateFlow<List<SavedTopic>?> = _topics.asStateFlow()

    init {
        scope.launch {
            holder.updates { savedMessagesTopicUpdates }.collect { update ->
                val topic = update.topic.toDomain()
                mutex.withLock {
                    byId[topic.id] = topic
                    publish()
                }
            }
        }
        // Témata patří účtu: po odhlášení by se pod novým klientem ukázala témata toho starého.
        scope.launch {
            holder.replaced.collect {
                mutex.withLock {
                    byId.clear()
                    _topics.value = null
                }
            }
        }
    }

    private fun publish() {
        _topics.value = byId.values.shownSavedTopics()
    }

    override suspend fun load(): Result<Unit> {
        // `loadSavedMessagesTopics` nevrací data, jen řekne TDLib, ať pošle další kus updaty.
        // 404 = všechno načtené; do té doby se volá znovu (TDLib si pamatuje, kde skončil).
        repeat(MAX_PAGES) {
            when (val result = client.loadSavedMessagesTopics(limit = PAGE_SIZE)) {
                is TdlResult.Failure -> {
                    mutex.withLock { if (_topics.value == null) publish() }
                    return if (result.code == NOT_FOUND) {
                        Result.success(Unit)
                    } else {
                        logger.w { "loadSavedMessagesTopics selhalo: ${result.code} ${result.message}" }
                        Result.failure(IllegalStateException(result.message))
                    }
                }
                is TdlResult.Success -> Unit
            }
        }
        mutex.withLock { if (_topics.value == null) publish() }
        return Result.success(Unit)
    }

    override suspend fun setPinned(topicId: Long, pinned: Boolean): Result<Unit> =
        client.toggleSavedMessagesTopicIsPinned(savedMessagesTopicId = topicId, isPinned = pinned).toResult().map { }

    override suspend fun setPinnedOrder(topicIds: List<Long>): Result<Unit> =
        client.setPinnedSavedMessagesTopics(savedMessagesTopicIds = topicIds.toLongArray()).toResult().map { }

    override suspend fun deleteHistory(topicId: Long): Result<Unit> =
        client.deleteSavedMessagesTopicHistory(savedMessagesTopicId = topicId).toResult().map { }

    private suspend fun SavedMessagesTopic.toDomain(): SavedTopic {
        val kind = when (val type = type) {
            is SavedMessagesTopicTypeMyNotes -> SavedTopicKind.MyNotes
            is SavedMessagesTopicTypeAuthorHidden -> SavedTopicKind.AuthorHidden
            is SavedMessagesTopicTypeSavedFromChat -> SavedTopicKind.FromChat(type.chatId)
        }
        // Zdrojový chat TDLib zná, když zná uloženou zprávu z něj — `getChat` je lokální.
        val chat = (kind as? SavedTopicKind.FromChat)?.let { client.getChat(chatId = it.chatId).toResult().getOrNull() }
        return SavedTopic(
            id = id,
            kind = kind,
            title = chat?.title,
            avatarFileId = chat?.photo?.small?.id,
            chatKind = chat?.type?.chatKind(),
            isPinned = isPinned,
            order = order,
            lastMessage = lastMessage?.toPreview(),
        )
    }

    /** V Uložených zprávách je všechno vlastní, takže náhled jméno odesílatele nenese. */
    private fun Message.toPreview() = TopicMessagePreview(
        text = previewText(),
        entities = previewEntities(),
        senderName = null,
        isOutgoing = false,
        timestamp = date,
    )

    private companion object {
        const val PAGE_SIZE = 100
        /** Pojistka proti věčné smyčce, kdyby TDLib 404 nikdy nevrátil. */
        const val MAX_PAGES = 20
        const val NOT_FOUND = 404
    }
}
