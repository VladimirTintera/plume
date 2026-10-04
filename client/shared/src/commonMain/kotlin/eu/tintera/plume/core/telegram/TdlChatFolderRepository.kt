package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.ChatFolder
import dev.g000sha256.tdl.dto.ChatFolderIcon
import dev.g000sha256.tdl.dto.ChatFolderName
import dev.g000sha256.tdl.dto.ChatListFolder
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.TextEntity
import dev.g000sha256.tdl.dto.TextEntityTypeCustomEmoji
import dev.g000sha256.tdl.toResult

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

    // Chaty, ke kterým se uživatel přidal přes sdílenou složku, zůstávají — opouštět je by
    // chtělo vlastní otázku (`getChatFolderChatsToLeave`), a smazání složky ji nečeká.
    override suspend fun delete(id: Int): Result<Unit> =
        client.deleteChatFolder(chatFolderId = id, leaveChatIds = longArrayOf()).toResult().map { }

    override suspend fun reorder(ids: List<Int>, mainPosition: Int): Result<Unit> =
        client.reorderChatFolders(chatFolderIds = ids.toIntArray(), mainChatListPosition = mainPosition)
            .toResult().map { }

    override suspend fun recommended(): Result<List<RecommendedChatFolder>> =
        client.getRecommendedChatFolders().toResult().map { result ->
            result.chatFolders.map { RecommendedChatFolder(draft = it.folder.toDraft(), description = it.description) }
        }

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
}

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
