package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.ChatFolder
import dev.g000sha256.tdl.dto.ChatFolderIcon
import dev.g000sha256.tdl.dto.ChatFolderInviteLink
import dev.g000sha256.tdl.dto.ChatFolderName
import dev.g000sha256.tdl.dto.ChatListFolder
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.OptionValueInteger
import dev.g000sha256.tdl.dto.TextEntity
import dev.g000sha256.tdl.dto.TextEntityTypeCustomEmoji
import dev.g000sha256.tdl.toResult
import kotlin.time.Clock

/**
 * Složky Telegramu nad TDLib. Po každém zápisu pošle TDLib `UpdateChatFolders` sám, takže se
 * seznam složek nikde lokálně nepřepisuje.
 */
class TdlChatFolderRepository(
    private val holder: TelegramClientHolder,
) : ChatFolderRepository {

    private val client: TdlClient get() = holder.client

    override suspend fun folder(id: Int): Result<ChatFolderDraft> =
        client.getChatFolder(chatFolderId = id).toResult().map { it.toDraft() }

    override suspend fun create(draft: ChatFolderDraft): Result<Int> =
        client.createChatFolder(folder = draft.toTdl()).toResult().map { it.id }

    override suspend fun edit(id: Int, draft: ChatFolderDraft): Result<Unit> =
        client.editChatFolder(chatFolderId = id, folder = draft.toTdl()).toResult().map { }

    override suspend fun chatsToLeave(folderId: Int): Result<List<Long>> =
        client.getChatFolderChatsToLeave(chatFolderId = folderId).toResult().map { it.chatIds.toList() }

    override suspend fun delete(id: Int, leaveChatIds: List<Long>): Result<Unit> =
        client.deleteChatFolder(chatFolderId = id, leaveChatIds = leaveChatIds.toLongArray()).toResult().map { }

    override suspend fun reorder(ids: List<Int>, mainPosition: Int): Result<Unit> =
        client.reorderChatFolders(chatFolderIds = ids.toIntArray(), mainChatListPosition = mainPosition)
            .toResult().map { }

    /**
     * Doporučená složka nese ikonu jen pro zobrazení (`iconName`): v konceptu zůstává prázdná,
     * takže přidaná složka si ikonu dál volí podle obsahu, jako by ji uživatel založil sám.
     */
    override suspend fun recommended(): Result<List<RecommendedChatFolder>> =
        client.getRecommendedChatFolders().toResult().map { result ->
            result.chatFolders.map { recommended ->
                val draft = recommended.folder.toDraft()
                RecommendedChatFolder(
                    draft = draft,
                    description = recommended.description,
                    iconName = draft.iconName ?: defaultIconName(draft).getOrNull(),
                )
            }
        }

    override suspend fun defaultIconName(draft: ChatFolderDraft): Result<String> =
        client.getChatFolderDefaultIconName(folder = draft.toTdl()).toResult().map { it.name }

    /**
     * Ve které složce chat JE, říká `Chat.chatLists` (dokumentace: pozice v seznamu o členství
     * nevypovídá — chat může mít pozici, i když ve složce není, a naopak). Kam ho jde přidat,
     * `getChatListsToAddChat`.
     */
    override suspend fun membership(chatId: Long): Result<ChatFolderMembership> = runCatching {
        val chat = client.getChat(chatId = chatId).toResult().getOrThrow()
        val lists = client.getChatListsToAddChat(chatId = chatId).toResult().getOrThrow()
        ChatFolderMembership(
            member = chat.chatLists.filterIsInstance<ChatListFolder>().mapTo(HashSet()) { it.chatFolderId },
            canAdd = lists.chatLists.filterIsInstance<ChatListFolder>().mapTo(HashSet()) { it.chatFolderId },
        )
    }

    override suspend fun addChat(folderId: Int, chatId: Long): Result<Unit> =
        client.addChatToList(chatId = chatId, chatList = ChatListFolder(chatFolderId = folderId)).toResult().map { }

    override suspend fun removeChat(folderId: Int, chatId: Long): Result<Unit> = runCatching {
        val draft = folder(folderId).getOrThrow()
        edit(folderId, draft.withoutChat(chatId, coveredByKind = draft.includesAnyKind)).getOrThrow()
    }

    override suspend fun links(folderId: Int): Result<List<ChatFolderLink>> =
        client.getChatFolderInviteLinks(chatFolderId = folderId).toResult().map { result ->
            result.inviteLinks.map { it.toLink() }
        }

    override suspend fun createLink(folderId: Int): Result<ChatFolderLink> = runCatching {
        val chats = client.getChatsForChatFolderInviteLink(chatFolderId = folderId).toResult().getOrThrow()
        if (chats.chatIds.isEmpty()) throw NothingToShareException()
        client.createChatFolderInviteLink(chatFolderId = folderId, name = "", chatIds = chats.chatIds)
            .toResult().getOrThrow().toLink()
    }

    override suspend fun shareableChats(folderId: Int): Result<List<Long>> =
        client.getChatsForChatFolderInviteLink(chatFolderId = folderId).toResult().map { it.chatIds.toList() }

    override suspend fun editLink(folderId: Int, url: String, name: String, chatIds: List<Long>): Result<ChatFolderLink> =
        client.editChatFolderInviteLink(
            chatFolderId = folderId,
            inviteLink = url,
            name = name.trim(),
            chatIds = chatIds.toLongArray(),
        ).toResult().map { it.toLink() }

    /**
     * Nové chaty se čtou nejvýš jednou za `chat_folder_new_chats_update_period` (server to tak
     * chce); mezitím se vrací poslední odpověď. Pamatuje se pro instanci klienta — po odhlášení
     * jsou složky jiného účtu.
     */
    private val newChatsCache = mutableMapOf<Int, Pair<Long, List<Long>>>()
    private var newChatsClient: TdlClient? = null

    override suspend fun newChats(folderId: Int): Result<List<Long>> = runCatching {
        val current = client
        if (newChatsClient !== current) {
            newChatsCache.clear()
            newChatsClient = current
        }
        val now = Clock.System.now().epochSeconds
        val period = (current.getOption(name = "chat_folder_new_chats_update_period").toResult().getOrNull()
            as? OptionValueInteger)?.value ?: DEFAULT_NEW_CHATS_PERIOD
        newChatsCache[folderId]?.let { (checkedAt, chats) -> if (now - checkedAt < period) return@runCatching chats }
        val chats = current.getChatFolderNewChats(chatFolderId = folderId).toResult().getOrThrow().chatIds.toList()
        newChatsCache[folderId] = now to chats
        chats
    }

    override suspend fun processNewChats(folderId: Int, chatIds: List<Long>): Result<Unit> =
        client.processChatFolderNewChats(chatFolderId = folderId, addedChatIds = chatIds.toLongArray()).toResult()
            .map { }
            // Vyřízené nové chaty se nesmí vrátit z paměti; server je teď vydá prázdné.
            .onSuccess { newChatsCache[folderId] = Clock.System.now().epochSeconds to emptyList() }

    override suspend fun deleteLink(folderId: Int, url: String): Result<Unit> =
        client.deleteChatFolderInviteLink(chatFolderId = folderId, inviteLink = url).toResult().map { }
}

/** Kdyby server interval nevydal: oficiální klienti se ptají nejvýš jednou za hodinu. */
private const val DEFAULT_NEW_CHATS_PERIOD = 3_600L

private fun ChatFolderInviteLink.toLink() = ChatFolderLink(url = inviteLink, name = name, chatIds = chatIds.toList())

private fun ChatFolder.toDraft(): ChatFolderDraft {
    val title = name.text.toBody()
    return ChatFolderDraft(
        title = title.text,
        titleEntities = title.entities.filter { it.type is MessageEntityType.CustomEmoji },
        iconName = icon?.name,
        colorId = colorId,
        isShareable = isShareable,
        pinnedChatIds = pinnedChatIds.toList(),
        includedChatIds = includedChatIds.toList(),
        excludedChatIds = excludedChatIds.toList(),
        includeContacts = includeContacts,
        includeNonContacts = includeNonContacts,
        includeGroups = includeGroups,
        includeChannels = includeChannels,
        includeBots = includeBots,
        excludeMuted = excludeMuted,
        excludeRead = excludeRead,
        excludeArchived = excludeArchived,
    )
}

private fun ChatFolderDraft.toTdl(): ChatFolder {
    val text = title.trim()
    // Vlastní emoji jen nad nezměněným textem — ořez by posunul jejich offsety.
    val entities = if (text == title) {
        titleEntities.mapNotNull { entity ->
            val emoji = entity.type as? MessageEntityType.CustomEmoji ?: return@mapNotNull null
            TextEntity(offset = entity.offset, length = entity.length, type = TextEntityTypeCustomEmoji(customEmojiId = emoji.id))
        }
    } else {
        emptyList()
    }
    return ChatFolder(
        name = ChatFolderName(
            text = FormattedText(text = text, entities = entities.toTypedArray()),
            animateCustomEmoji = true,
        ),
        // Bez ikony si ji TDLib vybere podle obsahu sama (`getChatFolderDefaultIconName`).
        icon = iconName?.let { ChatFolderIcon(name = it) },
        colorId = colorId,
        isShareable = isShareable,
        pinnedChatIds = pinnedChatIds.toLongArray(),
        includedChatIds = includedChatIds.toLongArray(),
        excludedChatIds = excludedChatIds.toLongArray(),
        excludeMuted = excludeMuted,
        excludeRead = excludeRead,
        excludeArchived = excludeArchived,
        includeContacts = includeContacts,
        includeNonContacts = includeNonContacts,
        includeBots = includeBots,
        includeGroups = includeGroups,
        includeChannels = includeChannels,
    )
}
