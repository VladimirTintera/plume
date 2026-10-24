package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.BotCommands
import dev.g000sha256.tdl.dto.EphemeralMessageContent
import dev.g000sha256.tdl.dto.Message

/**
 * Zpráva s obsahem, který TDLib předepisuje ukázat MÍSTO běžného (`Message.ephemeralContent`:
 * „must be shown instead of the regular content"). Bot ho nastaví po ťuknutí na tlačítko jen
 * pro toho, kdo ťukl; zbytek převodu ([TdlConversationRepository]) tak nemusí o mizejícím
 * obsahu nic vědět a kreslí zprávu jako jakoukoli jinou.
 *
 * Pojmenované argumenty bez výchozích hodnot: pole, které novější TDLib přidá, rozbije překlad
 * tady, místo aby se tiše ztratilo.
 */
internal fun Message.withEphemeralContent(ephemeral: EphemeralMessageContent): Message = Message(
    id = id,
    senderId = senderId,
    receiverId = receiverId,
    chatId = chatId,
    sendingState = sendingState,
    schedulingState = schedulingState,
    isOutgoing = isOutgoing,
    isPinned = isPinned,
    isFromOffline = isFromOffline,
    canBeSaved = ephemeral.canBeSaved,
    hasTimestampedMedia = ephemeral.hasTimestampedMedia,
    isChannelPost = isChannelPost,
    isPaidStarSuggestedPost = isPaidStarSuggestedPost,
    isPaidGramSuggestedPost = isPaidGramSuggestedPost,
    containsUnreadMention = containsUnreadMention,
    containsUnreadPollVotes = containsUnreadPollVotes,
    date = date,
    editDate = editDate,
    forwardInfo = forwardInfo,
    importInfo = importInfo,
    interactionInfo = interactionInfo,
    unreadReactions = unreadReactions,
    factCheck = factCheck,
    suggestedPostInfo = suggestedPostInfo,
    replyTo = replyTo,
    topicId = topicId,
    selfDestructType = selfDestructType,
    selfDestructIn = selfDestructIn,
    autoDeleteIn = autoDeleteIn,
    viaBotUserId = viaBotUserId,
    guestBotCallerId = guestBotCallerId,
    senderBusinessBotUserId = senderBusinessBotUserId,
    senderBoostCount = senderBoostCount,
    senderTag = senderTag,
    paidMessageStarCount = paidMessageStarCount,
    authorSignature = authorSignature,
    mediaAlbumId = mediaAlbumId,
    effectId = effectId,
    restrictionInfo = restrictionInfo,
    summaryLanguageCode = summaryLanguageCode,
    content = ephemeral.content,
    ephemeralContent = null,
    replyMarkup = ephemeral.replyMarkup,
    ephemeralMessageId = ephemeralMessageId,
    chatInstance = chatInstance,
)

/**
 * Bot, kterému má jít [text] jako mizející příkaz (`BotCommand.isEphemeral`), nebo `null`.
 *
 * Příkaz je první slovo zprávy (`/cmd` nebo `/cmd@bot`). Se jménem bota rozhoduje jen ten bot;
 * bez něj se mizející posílá, jen když příkaz zná jediný bot ve skupině — u dvou by nebylo
 * jasné, komu ho dát, a běžná zpráva dojde oběma.
 */
internal fun ephemeralCommandBot(
    text: String,
    bots: List<BotCommands>,
    username: (Long) -> String?,
): Long? {
    val token = text.trimStart().takeWhile { !it.isWhitespace() }
    if (!token.startsWith("/") || token.length < 2) return null
    val command = token.substring(1).substringBefore('@')
    val target = token.substringAfter('@', missingDelimiterValue = "").ifEmpty { null }
    val candidates = bots.filter { bot ->
        bot.commands.any { it.command.equals(command, ignoreCase = true) } &&
            (target == null || username(bot.botUserId).equals(target, ignoreCase = true))
    }
    val bot = candidates.singleOrNull() ?: return null
    val isEphemeral = bot.commands.any { it.command.equals(command, ignoreCase = true) && it.isEphemeral }
    return bot.botUserId.takeIf { isEphemeral }
}
