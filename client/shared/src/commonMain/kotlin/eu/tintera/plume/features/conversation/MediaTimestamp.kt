package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage

/**
 * Zpráva s médiem, na které míří čas v textu („1:23") zprávy [message].
 *
 * TDLib: médium je v téže zprávě, nebo ve zprávě, na kterou odpovídá — rozlišuje to
 * `hasTimestampedMedia`. Citovaná zpráva nemusí být v načtené historii, proto i [quotes]
 * (dotažené citace). `null`, když ji nemáme.
 */
internal fun timestampTarget(
    message: ChatMessage,
    messages: List<ChatMessage>,
    quotes: Map<Long, ChatMessage>,
): ChatMessage? = when {
    message.hasTimestampedMedia -> message
    else -> message.replyToMessageId?.let { id -> messages.firstOrNull { it.id == id } ?: quotes[id] }
}
