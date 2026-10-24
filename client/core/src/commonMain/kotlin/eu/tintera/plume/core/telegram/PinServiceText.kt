package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessagePinMessage
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.toResult
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.service_message_pinned
import eu.tintera.plume.resources.service_pinned_named
import eu.tintera.plume.resources.service_pinned_named_message
import eu.tintera.plume.resources.service_pinned_quote
import eu.tintera.plume.resources.service_pinned_you
import eu.tintera.plume.resources.service_pinned_you_message

/**
 * Zpráva o připnutí s tím, kdo připnul a co: „Jana připnul(a) „Sraz v 8"", „Připnul(a) jsi
 * zprávu". „Připnuta zpráva" bez obojího nechávala hádat, o kterou zprávu jde.
 *
 * [actor] je odesílatel oznámení, `null` když je to chat (anonymní správce, kanál) — jméno
 * chatu by tu nic neřeklo, je to týž chat. [pinnedText] je text nebo popisek připnuté zprávy;
 * bez něj (fotka bez popisku, zpráva lokálně není) věta říká jen „zprávu".
 */
internal fun pinServiceText(actor: ServicePerson?, pinnedText: String?): UiText {
    val quote = pinnedText?.takeIf { it.isNotBlank() }?.let(::quotedTask)
    return when {
        actor == null && quote == null -> UiText(Res.string.service_message_pinned)
        actor == null -> UiText(Res.string.service_pinned_quote, quote!!)
        actor.isMe && quote != null -> UiText(Res.string.service_pinned_you, quote)
        actor.isMe -> UiText(Res.string.service_pinned_you_message)
        quote != null -> UiText(Res.string.service_pinned_named, actor.name, quote)
        else -> UiText(Res.string.service_pinned_named_message, actor.name)
    }
}

/**
 * [pinServiceText] i s dohledáním jména a textu připnuté zprávy; jiná zpráva než připnutí
 * vrátí `null`. [pinnedText] dostane id připnuté zprávy. Jméno, které nejde zjistit, větu
 * nezahodí — zůstane text bez jména.
 */
internal suspend fun Message.namedPinServiceText(
    myUserId: suspend () -> Long?,
    nameOf: suspend (Long) -> String?,
    pinnedText: suspend (messageId: Long) -> String?,
): UiText? {
    val pin = content as? MessagePinMessage ?: return null
    val actorId = (senderId as? MessageSenderUser)?.userId
    val actor = when {
        actorId == null -> null
        isOutgoing || actorId == myUserId() -> ServicePerson(name = "", isMe = true)
        else -> nameOf(actorId)?.let { ServicePerson(it) }
    }
    return pinServiceText(actor, pin.messageId.takeIf { it != 0L }?.let { pinnedText(it) })
}

/**
 * Text nebo popisek zprávy z lokální databáze (`getMessageLocally`), pro servisní větu
 * o připnutí. Na server se kvůli ní nečeká: věta se skládá při převodu každé zprávy.
 */
internal suspend fun TdlClient.localMessageText(chatId: Long, messageId: Long): String? =
    getMessageLocally(chatId = chatId, messageId = messageId).toResult().getOrNull()
        ?.content?.quotableText()?.text
