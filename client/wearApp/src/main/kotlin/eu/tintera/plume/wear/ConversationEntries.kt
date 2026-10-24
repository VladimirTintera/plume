package eu.tintera.plume.wear

import eu.tintera.plume.core.telegram.ChatMessage
import java.time.Instant
import java.time.ZoneId

/** Pauza, po které další zpráva téhož odesílatele začne nový blok (jako časový předěl na telefonu). */
private const val GROUP_GAP_SECONDS = 10 * 60

/** Položka konverzace na hodinkách: předěl dne, nebo zpráva se svým místem v bloku. */
sealed interface ConversationEntry {
    val key: Any

    /**
     * Předěl nad první zprávou dne. Klíč je číslo dne, ne id zprávy: dočtením starší historie
     * téhož dne by se první zpráva změnila, předěl by dostal nový klíč a seznam by ztratil kotvu.
     */
    data class Day(val timestamp: Int, val epochDay: Long) : ConversationEntry {
        override val key: Any get() = "day-$epochDay"
    }

    /**
     * Zpráva. [joinsPrevious] / [joinsNext]: navazuje na starší / novější zprávu ve stejném bloku
     * (stejný odesílatel a strana, stejný den, bez dlouhé pauzy) — podle toho se kreslí těsněji,
     * se staženým rohem a bez opakovaného jména.
     */
    data class Message(val message: ChatMessage, val joinsPrevious: Boolean, val joinsNext: Boolean) : ConversationEntry {
        override val key: Any get() = message.id
    }
}

/** Zprávy (od nejstarší) proložené předěly dní a s informací o blocích. */
fun conversationEntries(messages: List<ChatMessage>, zone: ZoneId = ZoneId.systemDefault()): List<ConversationEntry> {
    val days = messages.map { Instant.ofEpochSecond(it.timestamp.toLong()).atZone(zone).toLocalDate().toEpochDay() }
    fun joins(newer: Int, older: Int): Boolean {
        val a = messages[newer]
        val b = messages[older]
        return !a.isService && !b.isService &&
            a.isOutgoing == b.isOutgoing &&
            a.senderId == b.senderId &&
            days[newer] == days[older] &&
            a.timestamp - b.timestamp < GROUP_GAP_SECONDS
    }
    return buildList {
        messages.forEachIndexed { index, message ->
            if (index == 0 || days[index] != days[index - 1]) add(ConversationEntry.Day(message.timestamp, days[index]))
            add(
                ConversationEntry.Message(
                    message,
                    joinsPrevious = index > 0 && joins(index, index - 1),
                    joinsNext = index < messages.lastIndex && joins(index + 1, index),
                ),
            )
        }
    }
}
