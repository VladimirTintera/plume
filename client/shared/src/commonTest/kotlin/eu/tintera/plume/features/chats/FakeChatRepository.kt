package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.ChatTyping
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.telegram.ChatFolders
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ArchiveSettings
import eu.tintera.plume.core.telegram.ArchiveUnread
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.SwitchInlineTarget
import eu.tintera.plume.core.telegram.FoundMessagesPage
import eu.tintera.plume.core.telegram.MessageSearchFilter
import eu.tintera.plume.core.telegram.SpamActions
import eu.tintera.plume.core.telegram.ReportRequest
import eu.tintera.plume.core.telegram.ReportStep
import eu.tintera.plume.core.telegram.ChatBar
import eu.tintera.plume.core.telegram.VideoChatInfo
import eu.tintera.plume.core.telegram.BlockedSendersPage
import eu.tintera.plume.core.telegram.InternalLink
import eu.tintera.plume.core.telegram.JoinResult
import kotlinx.coroutines.flow.Flow
import eu.tintera.plume.core.telegram.SpeechTrial
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Repository chatů pro testy ViewModelů: všechno prázdné a úspěšné. Kdo potřebuje jiné chování,
 * podědí a přepíše (`open` členy).
 */
open class FakeChatRepository(
    private val contactsResult: Result<List<ChatSummary>> = Result.success(emptyList()),
) : ChatRepository {
    override val savedMessagesChatId: StateFlow<Long?> = MutableStateFlow(999L)

    override val diceEmojis: StateFlow<List<String>> = MutableStateFlow(emptyList())
    override val availableEffectIds: StateFlow<List<Long>> = MutableStateFlow(emptyList())
    override val speechTrial: StateFlow<SpeechTrial?> = MutableStateFlow(null)
    override val isPremium: StateFlow<Boolean> = MutableStateFlow(false)

    override fun chats(folder: ChatFolder): StateFlow<ChatListState> =
        MutableStateFlow(ChatListState.Ready(emptyList(), canLoadMore = false))

    override fun typing(chatId: Long): Flow<ChatTyping?> = emptyFlow()

    override suspend fun openSavedMessages(): Result<Long> = Result.success(999L)

    override suspend fun resolveLink(url: String): Result<InternalLink?> = Result.success(null)

    override suspend fun joinByInvite(link: String): Result<JoinResult> =
        Result.failure(UnsupportedOperationException())

    override suspend fun chatByUsername(username: String): Result<Long> =
        Result.failure(UnsupportedOperationException())

    override suspend fun privateChat(userId: Long): Result<Long> = Result.success(userId)

    override suspend fun contacts(): Result<List<ChatSummary>> = contactsResult

    override suspend fun groupsForBot(asAdmin: Boolean): List<ChatSummary> = emptyList()
    override suspend fun inlineQueryTargets(target: SwitchInlineTarget.Chosen): List<ChatSummary> = emptyList()

    override suspend fun addBotToGroup(url: String, groupChatId: Long): Result<Unit> = Result.success(Unit)

    override suspend fun searchChats(query: String, onServer: Boolean): Result<List<ChatSummary>> =
        Result.success(emptyList())

    override suspend fun searchPublicChats(query: String): Result<List<ChatSummary>> =
        Result.success(emptyList())

    override suspend fun searchMessages(query: String, offset: String, filter: MessageSearchFilter): Result<FoundMessagesPage> =
        Result.success(FoundMessagesPage(emptyList(), null))

    override suspend fun searchPublicPosts(tag: String, offset: String): Result<FoundMessagesPage> =
        Result.success(FoundMessagesPage(emptyList(), null))

    override suspend fun recentlyFoundChats(): Result<List<ChatSummary>> = Result.success(emptyList())

    override suspend fun addRecentlyFound(chatId: Long): Result<Unit> = Result.success(Unit)

    override suspend fun clearRecentlyFound(): Result<Unit> = Result.success(Unit)

    override suspend fun removeRecentlyFound(chatId: Long): Result<Unit> = Result.success(Unit)

    override suspend fun removeTopChat(chatId: Long): Result<Unit> = Result.success(Unit)

    override suspend fun topChats(): Result<List<ChatSummary>> = Result.success(emptyList())

    override val archiveUnread: StateFlow<ArchiveUnread> = MutableStateFlow(ArchiveUnread())
    override val mainUnread: StateFlow<ArchiveUnread> = MutableStateFlow(ArchiveUnread())

    override val unreadMessages: StateFlow<Int?> = MutableStateFlow(null)
    override val chatFolders: StateFlow<ChatFolders> = MutableStateFlow(ChatFolders())
    override val folderUnread: StateFlow<Map<Int, ArchiveUnread>> = MutableStateFlow(emptyMap())

    override suspend fun archiveSettings(): Result<ArchiveSettings> =
        Result.success(ArchiveSettings(false, false, canArchiveUnknownSenders = false))

    override suspend fun setArchiveSettings(settings: ArchiveSettings): Result<Unit> = Result.success(Unit)

    override suspend fun loadMore(folder: ChatFolder) {}

    override suspend fun setPinned(chatId: Long, folder: ChatFolder, pinned: Boolean): Result<Unit> =
        Result.success(Unit)

    override suspend fun readAll(folder: ChatFolder): Result<Unit> = Result.success(Unit)

    override suspend fun setPinnedOrder(folder: ChatFolder, chatIds: List<Long>): Result<Unit> =
        Result.success(Unit)

    override suspend fun setArchived(chatId: Long, archived: Boolean): Result<Unit> =
        Result.success(Unit)

    override fun muted(chatId: Long): Flow<Boolean?> = emptyFlow()
    override fun appearance(chatId: Long): Flow<eu.tintera.plume.core.telegram.ChatAppearance?> = emptyFlow()

    override suspend fun setMuted(chatId: Long, muted: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun muteFor(chatId: Long, duration: kotlin.time.Duration): Result<Unit> = Result.success(Unit)

    override suspend fun setMarkedAsUnread(chatId: Long, unread: Boolean): Result<Unit> =
        Result.success(Unit)

    override suspend fun readChat(chatId: Long): Result<Unit> = Result.success(Unit)

    override suspend fun deleteOrLeave(chatId: Long): Result<Unit> = Result.success(Unit)

    override suspend fun spamActions(chatId: Long): Result<SpamActions> = Result.success(SpamActions())

    override suspend fun setBlocked(chatId: Long, blocked: Boolean): Result<Unit> = Result.success(Unit)

    override suspend fun report(chatId: Long, request: ReportRequest): Result<ReportStep> = Result.success(ReportStep.Done)
    override fun chatBar(chatId: Long): Flow<ChatBar?> = flowOf(null)
    override fun videoChat(chatId: Long): Flow<VideoChatInfo?> = flowOf(null)
    override suspend fun dismissChatBar(chatId: Long): Result<Unit> = Result.success(Unit)
    override suspend fun sharePhoneNumber(chatId: Long): Result<Unit> = Result.success(Unit)
    override suspend fun unarchiveFromSpamBar(chatId: Long): Result<Unit> = Result.success(Unit)
    override suspend fun addToContacts(chatId: Long): Result<Unit> = Result.success(Unit)
    override suspend fun removeContact(userId: Long): Result<Unit> = Result.success(Unit)
    open override suspend fun blockedSenders(offset: Int, limit: Int): Result<BlockedSendersPage> =
        Result.success(BlockedSendersPage(emptyList(), 0))
    open override suspend fun setSenderBlocked(senderId: Long, blocked: Boolean): Result<Unit> = Result.success(Unit)
}
