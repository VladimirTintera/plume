package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.ChatTypeSupergroup
import dev.g000sha256.tdl.toResult

/**
 * Osobní kanál podle id chatu; `null`, když ho TDLib nezná nebo to kanál není. Lokální dotazy —
 * chat i supergroup TDLib zná z `UserFullInfo` nebo ze seznamu vhodných kanálů. Počet odběratelů
 * z `Supergroup.memberCount` (0 = neznámý, plné údaje kanálu se kvůli řádku netahají).
 */
internal suspend fun TdlClient.personalChannel(chatId: Long): PersonalChannel? {
    val chat = getChat(chatId = chatId).toResult().getOrNull() ?: return null
    val type = chat.type as? ChatTypeSupergroup ?: return null
    if (!type.isChannel) return null
    val group = getSupergroup(supergroupId = type.supergroupId).toResult().getOrNull()
    return PersonalChannel(
        chatId = chat.id,
        title = chat.title,
        photoFileId = chat.photo?.small?.id,
        subscribers = group?.memberCount?.takeIf { it > 0 },
    )
}
