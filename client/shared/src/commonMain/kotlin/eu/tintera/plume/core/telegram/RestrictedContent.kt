package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.Message

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
