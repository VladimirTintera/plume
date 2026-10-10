package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.features.conversation.items.describeText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.date_numeric
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Vybrané zprávy jako přepis, řádek na zprávu: `[10. 10. 2026 14:22] Jana: text`. Pro poznámky
 * nebo jazykový model — proto u každé zprávy celé datum, ne jen čas: kus přepisu vytržený
 * z kontextu musí říct, kdy se to stalo.
 *
 * Odchozí zprávy podepisuje [me], příchozí jméno odesílatele, a v soukromém chatu, kde ho TDLib
 * nevyplňuje, [chatTitle] — stejně jako přepis pro AI shrnutí (`transcriptLines`). Příloha má
 * v hranatých závorkách svůj popis („[Fotka] popisek"); kde je popis zároveň textem (kostka,
 * kontakt, animované emoji), závorky nejsou. Zpráva bez čehokoli k zapsání se vynechá.
 *
 * [messages] v libovolném pořadí; přepis jde od nejstarší. [resolve] překládá popisy (v testu
 * se podstrčí, prostředky Compose na JVM nejsou).
 */
internal suspend fun selectionTranscript(
    messages: List<ChatMessage>,
    me: String,
    chatTitle: String,
    zone: TimeZone = TimeZone.currentSystemDefault(),
    resolve: suspend (UiText) -> String = { it.resolveNow() },
): String = messages
    .sortedWith(compareBy({ it.timestamp }, { it.id }))
    .mapNotNull { message ->
        val body = transcriptBody(message, resolve) ?: return@mapNotNull null
        val author = if (message.isOutgoing) me else message.senderName ?: chatTitle
        "[${timestampText(message.timestamp, zone, resolve)}] $author: $body"
    }
    .joinToString("\n")

private suspend fun transcriptBody(message: ChatMessage, resolve: suspend (UiText) -> String): String? {
    val text = message.copyableText().ifBlank { message.description?.let { resolve(it) }.orEmpty() }
    val label = message.media
        ?.takeUnless { it is MessageMedia.AnimatedEmoji || it is MessageMedia.Dice || it is MessageMedia.Contact }
        ?.let { resolve(it.describeText()) }
        ?.takeIf { it.isNotBlank() }
        ?.let { "[$it]" }
    return listOfNotNull(label, text.takeIf { it.isNotBlank() }).joinToString(" ").ifBlank { null }
}

/** Datum v pořadí jazyka aplikace (`date_numeric`), čas 24hodinově jako všude v aplikaci. */
private suspend fun timestampText(timestamp: Int, zone: TimeZone, resolve: suspend (UiText) -> String): String {
    val moment = Instant.fromEpochSeconds(timestamp.toLong()).toLocalDateTime(zone)
    val date = resolve(UiText(Res.string.date_numeric, moment.day, moment.month.number, moment.year))
    return "$date ${moment.hour.toString().padStart(2, '0')}:${moment.minute.toString().padStart(2, '0')}"
}
