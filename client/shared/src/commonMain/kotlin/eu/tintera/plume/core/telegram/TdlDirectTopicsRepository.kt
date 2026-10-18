package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.toResult
import dev.g000sha256.tdl.dto.DirectMessagesChatTopic
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Vlákna přímých zpráv nad `UpdateDirectMessagesChatTopic`.
 *
 * **Eagerně** (`createdAtStart`), ze stejného důvodu jako [TdlSavedTopicsRepository]: TDLib
 * vlákna posílá jen updaty — po `loadDirectMessagesChatTopics` i kdykoli někdo kanálu napíše.
 * Kdo by se k odběru přihlásil pozdě, část by minul.
 */
class TdlDirectTopicsRepository(
    private val holder: TelegramClientHolder,
    scope: CoroutineScope,
) : DirectTopicsRepository {

    private val logger = Logger.withTag("DirectTopics")
    private val client: TdlClient get() = holder.client

    private val mutex = Mutex()
    private val byChat = mutableMapOf<Long, MutableMap<Long, DirectTopic>>()

    /** Chaty, jejichž vlákna se už načetla — do té doby je seznam `null` (načítá se). */
    private val loaded = mutableSetOf<Long>()
    private val state = MutableStateFlow<Map<Long, List<DirectTopic>>>(emptyMap())

    init {
        scope.launch {
            holder.updates { directMessagesChatTopicUpdates }.collect { update ->
                val topic = update.topic.toDomain()
                mutex.withLock {
                    byChat.getOrPut(topic.chatId) { mutableMapOf() }[topic.id] = topic
                    publish()
                }
            }
        }
        // Vlákna patří účtu: po odhlášení by se pod novým klientem ukázala vlákna toho starého.
        scope.launch {
            holder.replaced.collect {
                mutex.withLock {
                    byChat.clear()
                    loaded.clear()
                    state.value = emptyMap()
                }
            }
        }
    }

    private fun publish() {
        state.value = byChat.filterKeys { it in loaded }.mapValues { (_, topics) -> topics.values.shownDirectTopics() }
    }

    override fun topics(chatId: Long): Flow<List<DirectTopic>?> =
        state.map { it[chatId] }.distinctUntilChanged()

    override suspend fun load(chatId: Long): Result<Unit> {
        // Jako u Uložených zpráv: `loadDirectMessagesChatTopics` data nevrací, jen řekne TDLib,
        // ať pošle další kus updaty. 404 = všechno načtené.
        var result = Result.success(Unit)
        var pages = 0
        while (pages++ < MAX_PAGES) {
            val response = client.loadDirectMessagesChatTopics(chatId = chatId, limit = PAGE_SIZE)
            if (response !is TdlResult.Failure) continue
            if (response.code != NOT_FOUND) {
                logger.w { "loadDirectMessagesChatTopics selhalo: ${response.code} ${response.message}" }
                result = Result.failure(IllegalStateException(response.message))
            }
            break
        }
        mutex.withLock {
            loaded += chatId
            publish()
        }
        return result
    }

    override suspend fun topic(chatId: Long, topicId: Long): DirectTopic? =
        client.getDirectMessagesChatTopic(chatId = chatId, topicId = topicId).toResult().getOrNull()?.toDomain()

    override suspend fun deleteHistory(chatId: Long, topicId: Long): Result<Unit> =
        client.deleteDirectMessagesChatTopicHistory(chatId = chatId, topicId = topicId).toResult().map { }

    override suspend fun setMarkedAsUnread(chatId: Long, topicId: Long, unread: Boolean): Result<Unit> =
        client.setDirectMessagesChatTopicIsMarkedAsUnread(chatId = chatId, topicId = topicId, isMarkedAsUnread = unread)
            .toResult().map { }

    private suspend fun DirectMessagesChatTopic.toDomain(): DirectTopic {
        // Odesílatele TDLib zná, když zná jeho zprávu — `getUser` i `getChat` jsou lokální.
        val (sender, title, avatar) = when (val sender = senderId) {
            is MessageSenderUser -> {
                val user = client.getUser(userId = sender.userId).toResult().getOrNull()
                Triple(sender.userId, user?.fullName().orEmpty(), user?.profilePhoto?.small?.id)
            }
            is MessageSenderChat -> {
                val chat = client.getChat(chatId = sender.chatId).toResult().getOrNull()
                Triple(sender.chatId, chat?.title.orEmpty(), chat?.photo?.small?.id)
            }
        }
        return DirectTopic(
            chatId = chatId,
            id = id,
            senderId = sender,
            title = title,
            avatarFileId = avatar,
            order = order,
            unreadCount = unreadCount.toInt(),
            isMarkedAsUnread = isMarkedAsUnread,
            lastMessage = lastMessage?.toPreview(),
        )
    }

    /** Ve vlákně píšou jen dva — ten, kdo kanálu napsal, a kanál; jméno náhled nenese. */
    private fun Message.toPreview() = TopicMessagePreview(
        text = previewText(),
        entities = previewEntities(),
        senderName = null,
        isOutgoing = isOutgoing,
        timestamp = date,
    )

    private companion object {
        const val PAGE_SIZE = 100
        /** Pojistka proti věčné smyčce, kdyby TDLib 404 nikdy nevrátil. */
        const val MAX_PAGES = 20
        const val NOT_FOUND = 404
    }
}
