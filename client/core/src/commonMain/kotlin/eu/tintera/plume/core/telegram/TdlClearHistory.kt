package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.Chat
import dev.g000sha256.tdl.dto.ChatTypeBasicGroup
import dev.g000sha256.tdl.dto.ChatTypePrivate

/**
 * Práva vymazat historii podle příznaků chatu. Mazání po dnech (`deleteChatMessagesByDate`)
 * TDLib bere jen v soukromém chatu a základní skupině — tajný chat ani supergroupa ho nemají.
 */
internal fun Chat.clearHistoryRights(): ClearHistoryRights? {
    if (!canBeDeletedOnlyForSelf && !canBeDeletedForAllUsers) return null
    return ClearHistoryRights(
        forAll = canBeDeletedForAllUsers,
        byDate = type is ChatTypePrivate || type is ChatTypeBasicGroup,
        forAllByDate = canBeDeletedForAllUsers && type is ChatTypePrivate,
    )
}
