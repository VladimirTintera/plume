package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageChatAddMembers
import dev.g000sha256.tdl.dto.MessageChatDeleteMember
import dev.g000sha256.tdl.dto.MessageChatJoinByLink
import dev.g000sha256.tdl.dto.MessageChatJoinByRequest
import dev.g000sha256.tdl.dto.MessageContent
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.User
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.service_added_you
import eu.tintera.plume.resources.service_member_accepted
import eu.tintera.plume.resources.service_member_added
import eu.tintera.plume.resources.service_member_joined
import eu.tintera.plume.resources.service_member_joined_by_link
import eu.tintera.plume.resources.service_member_left_named
import eu.tintera.plume.resources.service_member_removed
import eu.tintera.plume.resources.service_names_more
import eu.tintera.plume.resources.service_names_two
import eu.tintera.plume.resources.service_removed_you
import eu.tintera.plume.resources.service_you_accepted
import eu.tintera.plume.resources.service_you_added
import eu.tintera.plume.resources.service_you_joined
import eu.tintera.plume.resources.service_you_joined_by_link
import eu.tintera.plume.resources.service_you_left
import eu.tintera.plume.resources.service_you_object
import eu.tintera.plume.resources.service_you_removed

/**
 * Člověk ve zprávě o členech skupiny: jméno, nebo „ty" ([isMe] — pak se [name] nečte).
 */
internal data class ServicePerson(val name: String, val isMe: Boolean = false)

/**
 * Uživatelé, o kterých zpráva o členech mluví KROMĚ odesílatele (ten je aktér: kdo přidal,
 * odešel, připojil se). `null` = nejde o zprávu o členech.
 */
internal fun MessageContent.memberServiceUserIds(): List<Long>? = when (this) {
    is MessageChatAddMembers -> memberUserIds.toList()
    is MessageChatDeleteMember -> listOf(userId)
    is MessageChatJoinByLink, is MessageChatJoinByRequest -> emptyList()
    else -> null
}

/**
 * Zpráva o členech se jmény: „Pixel 5 opustil(a) skupinu", „Přidal(a) jsi Alici a Boba".
 *
 * [people] nese jména aktéra ([actorId], odesílatel zprávy) a lidí z [memberServiceUserIds];
 * u víc než dvou přidaných stačí první, zbytek se jen spočítá. Když některé jméno chybí, vrací
 * `null` a volající zůstane u bezejmenné věty z [serviceText] — ta je pravdivá vždycky.
 */
internal fun MessageContent.memberServiceText(actorId: Long, people: Map<Long, ServicePerson>): UiText? {
    val actor = people[actorId] ?: return null
    return when (this) {
        is MessageChatAddMembers -> {
            val members = memberUserIds.toList()
            when {
                // Do veřejné skupiny se člověk „přidá" sám — tak to server zapisuje.
                members == listOf(actorId) -> if (actor.isMe) {
                    UiText(Res.string.service_you_joined)
                } else {
                    UiText(Res.string.service_member_joined, actor.name)
                }
                members.size == 1 && people[members[0]]?.isMe == true && !actor.isMe ->
                    UiText(Res.string.service_added_you, actor.name)
                else -> {
                    val names = nameList(members, people) ?: return null
                    if (actor.isMe) {
                        UiText(Res.string.service_you_added, names)
                    } else {
                        UiText(Res.string.service_member_added, actor.name, names)
                    }
                }
            }
        }
        is MessageChatJoinByLink -> if (actor.isMe) {
            UiText(Res.string.service_you_joined_by_link)
        } else {
            UiText(Res.string.service_member_joined_by_link, actor.name)
        }
        is MessageChatJoinByRequest -> if (actor.isMe) {
            UiText(Res.string.service_you_accepted)
        } else {
            UiText(Res.string.service_member_accepted, actor.name)
        }
        is MessageChatDeleteMember -> {
            val member = people[userId] ?: return null
            when {
                userId == actorId -> if (actor.isMe) {
                    UiText(Res.string.service_you_left)
                } else {
                    UiText(Res.string.service_member_left_named, actor.name)
                }
                actor.isMe -> UiText(Res.string.service_you_removed, member.asObject())
                member.isMe -> UiText(Res.string.service_removed_you, actor.name)
                else -> UiText(Res.string.service_member_removed, actor.name, member.name)
            }
        }
        else -> null
    }
}

/** „Alice", „Alice a Bob", „Alice a 4 další" — víc jmen by se do řádku stejně nevešlo. */
private fun nameList(ids: List<Long>, people: Map<Long, ServicePerson>): UiText? {
    val first = people[ids.firstOrNull() ?: return null]?.asObject() ?: return null
    return when (ids.size) {
        1 -> first
        2 -> UiText(Res.string.service_names_two, first, people[ids[1]]?.asObject() ?: return null)
        else -> UiText(Res.plurals.service_names_more, ids.size - 1, first, ids.size - 1)
    }
}

/** Člověk jako předmět věty: jméno, nebo „tebe". */
private fun ServicePerson.asObject(): UiText =
    if (isMe) UiText(Res.string.service_you_object) else UiText.Raw(name)

/**
 * [memberServiceText] i s dohledáním jmen. [myUserId] rozhodne, kdo je „ty" (u aktéra stačí
 * i `isOutgoing`; volá se jen u zpráv o členech); [nameOf] vrátí jméno uživatele, nebo `null`, když ho neznáme.
 *
 * Dohledávají se jen jména, která věta opravdu použije — u hromadného přidání první dvě.
 * Pro ostatní zprávy nestojí nic: `memberServiceUserIds` je vrátí hned jako `null`.
 */
internal suspend fun Message.namedMemberServiceText(
    myUserId: suspend () -> Long?,
    nameOf: suspend (Long) -> String?,
): UiText? {
    val members = content.memberServiceUserIds() ?: return null
    val actorId = (senderId as? MessageSenderUser)?.userId ?: return null
    val me = myUserId()
    val people = mutableMapOf<Long, ServicePerson>()
    for (id in (listOf(actorId) + members.take(2)).distinct()) {
        val isMe = id == me || (id == actorId && isOutgoing)
        people[id] = if (isMe) ServicePerson(name = "", isMe = true) else ServicePerson(nameOf(id) ?: return null)
    }
    return content.memberServiceText(actorId, people)
}

/** Jméno a příjmení, jak je má uživatel (nebo jak ho máme v kontaktech). */
internal fun User.fullName(): String = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
