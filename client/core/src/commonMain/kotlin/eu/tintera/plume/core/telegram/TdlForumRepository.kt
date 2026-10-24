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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
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
                val topics = page.topics.filterNotNull().map { it.toDomain() }
                // Pořadí připnutých nese jen pořadí na stránce od serveru, ne `order` (viz [ForumTopicsPage]).
                val pinnedOrder = topics.filter { it.isPinned }.map { it.id }
                ForumTopicsPage(
                    topics = topics.sortedTopics(pinnedOrder),
                    pinnedOrder = pinnedOrder,
                    // Prázdná stránka je konec; offset z ní by vedl zase na začátek.
                    next = ForumTopicsOffset(page.nextOffsetDate, page.nextOffsetMessageId, page.nextOffsetForumTopicId)
                        .takeIf { topics.isNotEmpty() },
                    general = topics.firstOrNull { it.isGeneral },
                )
            }

    override suspend fun search(chatId: Long, query: String): Result<List<ForumTopic>> =
        client.getForumTopics(
            chatId = chatId,
            query = query,
            offsetDate = 0,
            offsetMessageId = 0L,
            offsetForumTopicId = 0,
            limit = TOPICS_PAGE,
        )
            .toResult()
            .map { page -> page.topics.filterNotNull().map { it.toDomain() }.sortedTopics() }

    override suspend fun rights(chatId: Long): Result<TopicRights> = runCatching {
        val chat = client.getChat(chatId = chatId).toResult().getOrThrow()
        topicRights(client.ownMemberStatus(chat), membersCanCreate = chat.permissions.canCreateTopics)
    }

    override fun rightsUpdates(chatId: Long): Flow<TopicRights> = flow {
        val supergroupId = (client.getChat(chatId = chatId).toResult().getOrNull()?.type as? ChatTypeSupergroup)?.supergroupId
        rights(chatId).onSuccess { emit(it) }
        if (supergroupId == null) return@flow
        emitAll(
            merge(
                holder.updates { chatPermissionsUpdates }.filter { it.chatId == chatId }.map { },
                // Vlastní postavení (povýšení, omezení) nese update supergroupy.
                holder.updates { supergroupUpdates }.filter { it.supergroup.id == supergroupId }.map { },
            ).mapNotNull { rights(chatId).getOrNull() },
        )
    }.distinctUntilChanged()

    override suspend fun create(chatId: Long, name: String, color: Int): Result<Int> =
        client.createForumTopic(
            chatId = chatId,
            name = name.trim().take(TOPIC_NAME_MAX),
            isNameImplicit = false,
            icon = ForumTopicIcon(color = color, customEmojiId = 0L),
        ).toResult().map { it.forumTopicId }

    override suspend fun rename(chatId: Long, topicId: Int, name: String): Result<Unit> =
        client.editForumTopic(
            chatId = chatId,
            forumTopicId = topicId,
            name = name.trim().take(TOPIC_NAME_MAX),
            // Ikonu úprava nemění — `false` ji nechá, jak je.
            editIconCustomEmoji = false,
            iconCustomEmojiId = 0L,
        ).toResult().map { }

    override suspend fun setClosed(chatId: Long, topicId: Int, closed: Boolean): Result<Unit> =
        client.toggleForumTopicIsClosed(chatId = chatId, forumTopicId = topicId, isClosed = closed).toResult().map { }

    override suspend fun setPinned(chatId: Long, topicId: Int, pinned: Boolean): Result<Unit> =
        client.toggleForumTopicIsPinned(chatId = chatId, forumTopicId = topicId, isPinned = pinned).toResult().map { }

    override suspend fun setPinnedOrder(chatId: Long, topicIds: List<Int>): Result<Unit> =
        client.setPinnedForumTopics(chatId = chatId, forumTopicIds = topicIds.toIntArray()).toResult().map { }

    override suspend fun delete(chatId: Long, topicId: Int): Result<Unit> =
        client.deleteForumTopic(chatId = chatId, forumTopicId = topicId).toResult().map { }

    override suspend fun setGeneralHidden(chatId: Long, hidden: Boolean): Result<Unit> =
        client.toggleGeneralForumTopicIsHidden(chatId = chatId, isHidden = hidden).toResult().map { }

    override suspend fun setForum(chatId: Long, forum: Boolean): Result<Unit> = runCatching {
        val chat = client.getChat(chatId = chatId).toResult().getOrThrow()
        val supergroup = (chat.type as? ChatTypeSupergroup) ?: error("Témata umí jen supergroupa.")
        client.toggleSupergroupIsForum(
            supergroupId = supergroup.supergroupId,
            isForum = forum,
            // Témata jako záložky nahoře v chatu — aplikace je ukazuje jako seznam.
            hasForumTabs = false,
        ).toResult().getOrThrow()
    }

    override suspend fun topic(chatId: Long, topicId: Int): Result<ForumTopic> =
        client.getForumTopic(chatId = chatId, forumTopicId = topicId)
            .toResult()
            .map { it.toDomain() }

    /**
     * `UpdateForumTopic` a `UpdateForumTopicInfo` jdou dál, jen když se od posledního pro totéž
     * téma LIŠÍ. `getForumTopic` se ptá serveru a TDLib po odpovědi pošle `UpdateForumTopic`
     * i beze změny; seznam na něj téma čte znovu — a bez tohohle filtru se čtení a update
     * honily dokola (po otevření skupiny zhruba 40 s, stovky dotazů na server).
     */
    override fun topicChanges(chatId: Long): Flow<Int> = merge(
        holder.updates { forumTopicUpdates }.filter { it.chatId == chatId }
            .changedPerTopic { it.forumTopicId }
            .map { it.forumTopicId },
        holder.updates { forumTopicInfoUpdates }.filter { it.info.chatId == chatId }
            .changedPerTopic { it.info.forumTopicId }
            .map { it.info.forumTopicId },
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
        isOwn = info.isOutgoing,
    )

    /**
     * Oznámení se jménem („Jana uzavřel(a) téma") nese jméno ve větě, takže řádek předponu
     * „Jana:" nedostane — byla by tam dvakrát. Bez jména zůstane bezejmenná věta s předponou.
     */
    private suspend fun Message.toTopicPreview(): TopicMessagePreview {
        val named = namedServicePreview(client) { client.getMe().toResult().getOrNull()?.id }
        return TopicMessagePreview(
            text = named ?: previewText(),
            entities = if (named != null) emptyList() else previewEntities(),
            senderName = if (isOutgoing || named != null) null else senderName(senderId, chatId),
            isOutgoing = isOutgoing,
            timestamp = date,
            showsSender = named == null,
        )
    }

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

/**
 * Propustí update, jen když se liší od posledního pro totéž téma (DTO z TDLib mají `equals`).
 * Paměť patří jednomu odběru, takže nový odběr (nová obrazovka) začne první změnou tématu.
 */
internal fun <T> Flow<T>.changedPerTopic(topicId: (T) -> Int): Flow<T> = flow {
    val last = HashMap<Int, T>()
    collect { update ->
        if (last.put(topicId(update), update) != update) emit(update)
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
