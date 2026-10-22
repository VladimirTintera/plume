package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Duration
import kotlinx.coroutines.flow.StateFlow
import eu.tintera.plume.core.ui.UiText

/**
 * ChatRepository pro testy: každé volání, které test nepřepsal, vyhodí chybu se jménem metody — test tak
 * hned řekne, na co ještě sahá. Vygenerované z rozhraní; po změně rozhraní stačí doplnit chybějící
 * člen (překladač ho vyjmenuje).
 */
open class UnimplementedChatRepository : ChatRepository {
    override fun chats(folder: ChatFolder) : StateFlow<ChatListState> = error("chats")
    override val chatFolders: StateFlow<ChatFolders> get() = error("chatFolders")
    override val folderUnread: StateFlow<Map<Int, ArchiveUnread>> get() = error("folderUnread")
    override fun typing(chatId: Long) : Flow<ChatTyping?> = error("typing")
    override val savedMessagesChatId: StateFlow<Long?> get() = error("savedMessagesChatId")
    override val diceEmojis: StateFlow<List<String>> get() = error("diceEmojis")
    override val availableEffectIds: StateFlow<List<Long>> get() = error("availableEffectIds")
    override val speechTrial: StateFlow<SpeechTrial?> get() = error("speechTrial")
    override val isPremium: StateFlow<Boolean> get() = error("isPremium")
    override suspend fun openSavedMessages() : Result<Long> = error("openSavedMessages")
    override suspend fun resolveLink(url: String) : Result<InternalLink?> = error("resolveLink")
    override suspend fun joinByInvite(link: String) : Result<JoinResult> = error("joinByInvite")
    override suspend fun chatByUsername(username: String) : Result<Long> = error("chatByUsername")
    override suspend fun privateChat(userId: Long) : Result<Long> = error("privateChat")
    override suspend fun contacts() : Result<List<ChatSummary>> = error("contacts")
    override suspend fun groupsForBot(asAdmin: Boolean) : List<ChatSummary> = error("groupsForBot")
    override suspend fun inlineQueryTargets(target: SwitchInlineTarget.Chosen) : List<ChatSummary> = error("inlineQueryTargets")
    override suspend fun addBotToGroup(url: String, groupChatId: Long) : Result<Unit> = error("addBotToGroup")
    override suspend fun searchChats(query: String, onServer: Boolean) : Result<List<ChatSummary>> = error("searchChats")
    override suspend fun searchPublicChats(query: String) : Result<List<ChatSummary>> = error("searchPublicChats")
    override suspend fun searchMessages(query: String, offset: String, filter: MessageSearchFilter) : Result<FoundMessagesPage> = error("searchMessages")
    override suspend fun searchPublicPosts(tag: String, offset: String) : Result<FoundMessagesPage> = error("searchPublicPosts")
    override suspend fun recentlyFoundChats() : Result<List<ChatSummary>> = error("recentlyFoundChats")
    override suspend fun addRecentlyFound(chatId: Long) : Result<Unit> = error("addRecentlyFound")
    override suspend fun clearRecentlyFound() : Result<Unit> = error("clearRecentlyFound")
    override suspend fun removeRecentlyFound(chatId: Long) : Result<Unit> = error("removeRecentlyFound")
    override suspend fun removeTopChat(chatId: Long) : Result<Unit> = error("removeTopChat")
    override suspend fun topChats() : Result<List<ChatSummary>> = error("topChats")
    override val archiveUnread: StateFlow<ArchiveUnread> get() = error("archiveUnread")
    override val mainUnread: StateFlow<ArchiveUnread> get() = error("mainUnread")
    override suspend fun archiveSettings() : Result<ArchiveSettings> = error("archiveSettings")
    override suspend fun setArchiveSettings(settings: ArchiveSettings) : Result<Unit> = error("setArchiveSettings")
    override suspend fun loadMore(folder: ChatFolder): Unit = error("loadMore")
    override suspend fun setPinned(chatId: Long, folder: ChatFolder, pinned: Boolean) : Result<Unit> = error("setPinned")
    override suspend fun readAll(folder: ChatFolder) : Result<Unit> = error("readAll")
    override suspend fun setPinnedOrder(folder: ChatFolder, chatIds: List<Long>) : Result<Unit> = error("setPinnedOrder")
    override suspend fun setArchived(chatId: Long, archived: Boolean) : Result<Unit> = error("setArchived")
    override fun muted(chatId: Long) : Flow<Boolean?> = error("muted")
    override fun appearance(chatId: Long) : Flow<ChatAppearance?> = error("appearance")
    override suspend fun setMuted(chatId: Long, muted: Boolean) : Result<Unit> = error("setMuted")
    override suspend fun muteFor(chatId: Long, duration: Duration) : Result<Unit> = error("muteFor")
    override suspend fun setMarkedAsUnread(chatId: Long, unread: Boolean) : Result<Unit> = error("setMarkedAsUnread")
    override suspend fun readChat(chatId: Long) : Result<Unit> = error("readChat")
    override suspend fun deleteOrLeave(chatId: Long) : Result<Unit> = error("deleteOrLeave")
    override suspend fun spamActions(chatId: Long) : Result<SpamActions> = error("spamActions")
    override suspend fun setBlocked(chatId: Long, blocked: Boolean) : Result<Unit> = error("setBlocked")
    override suspend fun blockedSenders(offset: Int, limit: Int) : Result<BlockedSendersPage> = error("blockedSenders")
    override suspend fun setSenderBlocked(senderId: Long, blocked: Boolean) : Result<Unit> = error("setSenderBlocked")
    override suspend fun report(chatId: Long, request: ReportRequest) : Result<ReportStep> = error("report")
    override fun chatBar(chatId: Long) : Flow<ChatBar?> = error("chatBar")
    override fun videoChat(chatId: Long) : Flow<VideoChatInfo?> = error("videoChat")
    override suspend fun dismissChatBar(chatId: Long) : Result<Unit> = error("dismissChatBar")
    override suspend fun sharePhoneNumber(chatId: Long) : Result<Unit> = error("sharePhoneNumber")
    override suspend fun unarchiveFromSpamBar(chatId: Long) : Result<Unit> = error("unarchiveFromSpamBar")
    override suspend fun addToContacts(chatId: Long) : Result<Unit> = error("addToContacts")
    override suspend fun removeContact(userId: Long) : Result<Unit> = error("removeContact")
}
