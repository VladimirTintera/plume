package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.toResult
import dev.g000sha256.tdl.dto.Chat
import dev.g000sha256.tdl.dto.ChatNotificationSettings
import dev.g000sha256.tdl.dto.ChatTypeSupergroup
import dev.g000sha256.tdl.dto.ForumTopic as TdlForumTopic
import dev.g000sha256.tdl.dto.ForumTopicIcon
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageSender
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.MessageTopicForum
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge

/**
 * Témata přes `getForumTopics` a `getForumTopic`.
 *
 * Změny se hlásí jen jako id tématu a téma se čte znovu: `UpdateForumTopic` nese počty
 * a koncept, `UpdateForumTopicInfo` název a stav, nová zpráva poslední zprávu a pořadí — skládat
 * je dohromady by znamenalo držet stav, který TDLib drží sám (stejně jako u nepřečtených značek).
 */
class TdlForumRepository(private val holder: TelegramClientHolder) : ForumRepository {

    private val client: TdlClient get() = holder.client

    override suspend fun topics(chatId: Long, offset: ForumTopicsOffset?): Result<ForumTopicsPage> =
        client.getForumTopics(
            chatId = chatId,
            query = "",
            offsetDate = offset?.date ?: 0,
            offsetMessageId = offset?.messageId ?: 0L,
            offsetForumTopicId = offset?.topicId ?: 0,
            limit = TOPICS_PAGE,
        )
            .toResult()
            .map { page ->
                val topics = page.topics.filterNotNull()
                ForumTopicsPage(
                    topics = topics.map { it.toDomain() }.sortedTopics(),
                    // Prázdná stránka je konec; offset z ní by vedl zase na začátek.
                    next = ForumTopicsOffset(page.nextOffsetDate, page.nextOffsetMessageId, page.nextOffsetForumTopicId)
                        .takeIf { topics.isNotEmpty() },
                )
            }

    override suspend fun topic(chatId: Long, topicId: Int): Result<ForumTopic> =
        client.getForumTopic(chatId = chatId, forumTopicId = topicId)
            .toResult()
            .map { it.toDomain() }

    override fun topicChanges(chatId: Long): Flow<Int> = merge(
        holder.updates { forumTopicUpdates }.filter { it.chatId == chatId }.map { it.forumTopicId },
        holder.updates { forumTopicInfoUpdates }.filter { it.info.chatId == chatId }.map { it.info.forumTopicId },
        holder.updates { newMessageUpdates }
            .filter { it.message.chatId == chatId && it.message.schedulingState == null }
            .mapNotNull { (it.message.topicId as? MessageTopicForum)?.forumTopicId },
        // Vlastní zpráva dostane po potvrzení nové id; poslední zpráva tématu se tím mění taky.
        holder.updates { messageSendSucceededUpdates }
            .filter { it.message.chatId == chatId }
            .mapNotNull { (it.message.topicId as? MessageTopicForum)?.forumTopicId },
    )

    private suspend fun TdlForumTopic.toDomain(): ForumTopic = ForumTopic(
        chatId = info.chatId,
        id = info.forumTopicId,
        name = info.name,
        icon = info.icon.toDomain(),
        isGeneral = info.isGeneral,
        isClosed = info.isClosed,
        isPinned = isPinned,
        order = order,
        unreadCount = unreadCount,
        unreadMentionCount = unreadMentionCount,
        unreadReactionCount = unreadReactionCount,
        isMuted = notificationSettings.isTopicMuted(),
        lastMessage = lastMessage?.toTopicPreview(),
        draft = draftMessage?.toChatDraft(),
        isHidden = info.isHidden,
    )

    private suspend fun Message.toTopicPreview() = TopicMessagePreview(
        text = previewText(),
        entities = previewEntities(),
        senderName = if (isOutgoing) null else senderName(senderId, chatId),
        isOutgoing = isOutgoing,
        timestamp = date,
    )

    /**
     * Jméno odesílatele, jen křestní — řádek je krátký a jméno je tu jen proto, aby bylo poznat,
     * kdo v tématu naposledy psal. Anonymní správce (odesílatel je skupina sama) jméno nemá.
     */
    private suspend fun senderName(sender: MessageSender, chatId: Long): String? = when (sender) {
        is MessageSenderUser -> client.getUser(userId = sender.userId).toResult().getOrNull()
            ?.let { user -> user.firstName.ifBlank { user.lastName } }
            ?.takeIf { it.isNotBlank() }
        is MessageSenderChat -> sender.chatId.takeIf { it != chatId }
            ?.let { client.getChat(chatId = it).toResult().getOrNull()?.title }
    }
}

internal fun ForumTopicIcon.toDomain() = TopicIcon(color = color, customEmojiId = customEmojiId.takeIf { it != 0L })

/** Ztlumené téma; výchozí nastavení tématu se řídí skupinou, a to seznam témat neukazuje. */
private fun ChatNotificationSettings.isTopicMuted(): Boolean = !useDefaultMuteFor && muteFor > 0

private const val TOPICS_PAGE = 50

/** Je chat skupina s tématy? Nese to supergroupa; lokální dotaz, TDLib ji zná, když zná chat. */
internal suspend fun TdlClient.isForum(chat: Chat): Boolean = when (val type = chat.type) {
    is ChatTypeSupergroup -> getSupergroup(supergroupId = type.supergroupId).toResult().getOrNull()?.isForum == true
    else -> false
}
