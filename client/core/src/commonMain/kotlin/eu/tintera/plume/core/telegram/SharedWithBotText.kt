package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.MessageContent
import dev.g000sha256.tdl.dto.MessageChatShared
import dev.g000sha256.tdl.dto.MessageUsersShared
import dev.g000sha256.tdl.dto.SharedUser
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.content_shared_chat
import eu.tintera.plume.resources.content_shared_users
import eu.tintera.plume.resources.service_names_more
import eu.tintera.plume.resources.service_names_two
import eu.tintera.plume.resources.service_shared_chat
import eu.tintera.plume.resources.service_shared_users

/** „Sdílel(a) jsi s botem „Výlet"", bez názvu obecná věta. */
internal fun sharedChatText(title: String?): UiText =
    title?.takeIf { it.isNotBlank() }?.let { UiText(Res.string.service_shared_chat, it) }
        ?: UiText(Res.string.content_shared_chat)

/**
 * „Sdílel(a) jsi s botem Alici", „… Alici a Boba", „… Alici a 2 další". [names] má jméno pro
 * každého sdíleného člověka, `null` = neznámé. Věta potřebuje jen první dvě; chybí-li některé
 * z nich, zůstane obecná věta — napůl prázdná by byla horší.
 */
internal fun sharedUsersText(names: List<String?>): UiText {
    val first = names.getOrNull(0) ?: return UiText(Res.string.content_shared_users)
    val list = when (names.size) {
        1 -> UiText.Raw(first)
        2 -> UiText(
            Res.string.service_names_two,
            UiText.Raw(first),
            UiText.Raw(names[1] ?: return UiText(Res.string.content_shared_users)),
        )
        else -> UiText(Res.plurals.service_names_more, names.size - 1, UiText.Raw(first), names.size - 1)
    }
    return UiText(Res.string.service_shared_users, list)
}

/**
 * Jméno, které nese zpráva sama — vyplněné jen pro boty (`SharedUser` „for bots only"), takže
 * u uživatelského účtu prázdné. Uživatelské jméno se zavináčem, když jiné není.
 */
internal fun SharedUser.ownName(): String? =
    listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
        ?: username.takeIf { it.isNotBlank() }?.let { "@$it" }

/**
 * Věta o sdílení s botem i s dohledáním jmen podle id. Jména a název ve zprávě TDLib vyplňuje
 * jen botům — uživatelský účet dostane jen id, a bez dohledání stálo „Sdílení uživatelé"
 * (ověřeno na emulátoru). [nameOf] vrátí jméno uživatele nebo název chatu podle id.
 * Dohledávají se jen jména, která věta použije (první dvě).
 */
internal suspend fun MessageContent.namedSharedWithBotText(nameOf: suspend (Long) -> String?): UiText? =
    when (val shared = this) {
        is MessageChatShared -> sharedChatText(shared.chat.title.ifBlank { null } ?: nameOf(shared.chat.chatId))
        is MessageUsersShared -> {
            val users = shared.users.toList()
            sharedUsersText(users.mapIndexed { index, user -> if (index < 2) user.ownName() ?: nameOf(user.userId) else null })
        }
        else -> null
    }
