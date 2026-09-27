package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.Chat
import dev.g000sha256.tdl.dto.ChatTypeBasicGroup
import dev.g000sha256.tdl.dto.ChatTypePrivate
import dev.g000sha256.tdl.dto.ChatTypeSecret
import dev.g000sha256.tdl.dto.ChatTypeSupergroup
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.toResult

/**
 * Proč server zprávu tady nedovolí ukázat (omezení podle země nebo platformy), nebo `null`.
 *
 * Je to věta pro čtenáře a patří na místo obsahu — tak to dělají i oficiální klienti. **Není to
 * chybová hláška od TDLib** (ty se uživateli nevypisují, viz `userMessage`): TDLib ji jen předá
 * ze serveru a dokumentace ji popisuje jako „human-readable description".
 *
 * **Citlivý obsah sem nepatří.** `get_restriction_info_object` (`RestrictionReason.cpp`) hledá
 * důvod s `is_sensitive() == false`; citlivost hlásí zvlášť `hasSensitiveContent` a ten se
 * odkrývá ťuknutím (viz [SensitiveContent]). Omezený obsah odkrýt nejde.
 */
internal val Message.restrictionText: String?
    get() = restrictionInfo?.restrictionReason?.takeIf { it.isNotBlank() }

/**
 * Zpráva, jejíž obsah server nedovolí ukázat: místo textu důvod, bez přílohy, náhledu odkazu,
 * ankety, tlačítek a kontroly faktů — cokoli z toho by obsah prozradilo nebo nabízelo akci nad
 * ním. Reakce, čas a odesílatel zůstávají.
 */
internal fun ChatMessage.restricted(reason: String): ChatMessage = copy(
    text = reason,
    entities = emptyList(),
    media = null,
    linkPreview = null,
    poll = null,
    keyboard = null,
    factCheck = null,
    isRestricted = true,
)

/**
 * Proč server celý chat tady nedovolí ukázat (kanál nebo účet omezený v zemi či na platformě),
 * nebo `null`. `Chat` omezení nenese — je na `User` (soukromý a tajný chat) a `Supergroup`
 * (skupina i kanál), stejně jako ověření (viz [verificationOf]); základní skupina ho nemá.
 * Dotazy jsou lokální.
 */
internal suspend fun TdlClient.restrictionOf(chat: Chat): String? = when (val type = chat.type) {
    is ChatTypePrivate -> getUser(userId = type.userId).toResult().getOrNull()?.restrictionInfo
    is ChatTypeSecret -> getUser(userId = type.userId).toResult().getOrNull()?.restrictionInfo
    is ChatTypeSupergroup -> getSupergroup(supergroupId = type.supergroupId).toResult().getOrNull()?.restrictionInfo
    is ChatTypeBasicGroup -> null
}?.restrictionReason?.takeIf { it.isNotBlank() }
