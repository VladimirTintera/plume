package eu.tintera.plume.features.chatinsights

import androidx.compose.runtime.Immutable
import eu.tintera.plume.core.telegram.MessageStat
import eu.tintera.plume.core.telegram.MessageStatKind
import eu.tintera.plume.core.telegram.ReactionCount
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import kotlin.time.Instant

/**
 * **Statistika chatu** spočtená z úseku historie ([MessageStat]) — věci, které Telegram nepočítá:
 * kdo kolik píše, kdy se píše (týden × hodina), po měsících, série dnů, nejčastější reakce
 * a v soukromém chatu, jak rychle kdo odpovídá a kdo konverzaci začíná.
 *
 * Všechno se počítá jen z toho, co prošlo ([messageCount], od [oldestTimestamp]); co je za
 * celou historii přesně ze serveru (počty fotek, hlasovek…), nese [ChatInsightsState.mediaCounts].
 */
@Immutable
data class ChatInsights(
    val messageCount: Int,
    val oldestTimestamp: Int?,
    val newestTimestamp: Int?,
    /** Od nejvíc píšícího. */
    val senders: List<SenderCount>,
    /** Zprávy podle dne v týdnu (0 = pondělí) a hodiny (0–23), v místním čase; 7 × 24. */
    val heatmap: List<List<Int>>,
    /** Po měsících od nejstaršího k nejnovějšímu, i s prázdnými měsíci mezi nimi. */
    val months: List<MonthCount>,
    val activeDays: Int,
    /** Nejdelší řada dnů po sobě se zprávou; `null`, když žádná nemá aspoň dva dny. */
    val longestStreak: DayStreak?,
    /** Řada, která trvá dodnes (končí dnes, nebo včera — dnešek ještě nemusí mít zprávu); 0 = žádná. */
    val currentStreak: Int,
    val busiestDay: DayCount?,
    /** Nejčastější reakce, od nejčastější. */
    val reactions: List<ReactionCount>,
    /** Kolik zpráv kterého druhu, od nejčastějšího (bez prázdných). */
    val kinds: List<Pair<MessageStatKind, Int>>,
    /** Jen v soukromém chatu: odpovědi a začátky konverzací; jinak `null`. */
    val replies: ReplyHabits?,
) {
    val isEmpty: Boolean get() = messageCount == 0

    /** Nejrušnější chvíle týdne (den 0 = pondělí, hodina); `null` bez zpráv. */
    val peak: Pair<Int, Int>?
        get() {
            var best: Pair<Int, Int>? = null
            var bestCount = 0
            heatmap.forEachIndexed { day, hours ->
                hours.forEachIndexed { hour, count ->
                    if (count > bestCount) {
                        bestCount = count
                        best = day to hour
                    }
                }
            }
            return best
        }
}

data class SenderCount(val senderId: Long, val isOutgoing: Boolean, val messages: Int, val characters: Int)

data class MonthCount(val month: YearMonth, val messages: Int)

data class DayCount(val day: LocalDate, val messages: Int)

data class DayStreak(val days: Int, val start: LocalDate, val end: LocalDate)

/** Jak odpovídá jedna strana rozhovoru dvou. */
data class ReplySide(
    /** Medián doby odpovědi v sekundách; `null`, když nebylo z čeho počítat. */
    val medianReplySeconds: Long?,
    /** Kolikrát tahle strana rozhovor začala (první zpráva po dlouhém tichu). */
    val starts: Int,
)

data class ReplyHabits(val mine: ReplySide, val theirs: ReplySide)

/**
 * Spočte statistiku z [messages] (v libovolném pořadí). [zone] určuje místní den a hodinu,
 * [today] pro řadu, která trvá dodnes. Odpovědi jen v soukromém chatu ([privateChat]): ve
 * skupině, kde zatím psali jen dva, by „kdo začíná" a „jak rychle odpovídá" nedávaly smysl
 * (ověřeno na emulátoru — skupina s botem ukázala odpovědi).
 */
fun computeChatInsights(messages: List<MessageStat>, zone: TimeZone, today: LocalDate, privateChat: Boolean): ChatInsights {
    val sorted = messages.sortedBy { it.id }
    val heatmap = List(DAYS_IN_WEEK) { IntArray(HOURS_IN_DAY) }
    val perDay = HashMap<LocalDate, Int>()
    val perMonth = HashMap<YearMonth, Int>()
    val senders = LinkedHashMap<Long, SenderCount>()
    val reactions = HashMap<String, Int>()
    val kinds = HashMap<MessageStatKind, Int>()

    for (message in sorted) {
        val moment = Instant.fromEpochSeconds(message.timestamp.toLong()).toLocalDateTime(zone)
        heatmap[moment.dayOfWeek.ordinal][moment.hour]++
        perDay[moment.date] = (perDay[moment.date] ?: 0) + 1
        perMonth[moment.date.yearMonth] = (perMonth[moment.date.yearMonth] ?: 0) + 1
        val sender = senders[message.senderId]
        senders[message.senderId] = SenderCount(
            senderId = message.senderId,
            isOutgoing = message.isOutgoing,
            messages = (sender?.messages ?: 0) + 1,
            characters = (sender?.characters ?: 0) + message.characters,
        )
        message.reactions.forEach { reactions[it.key] = (reactions[it.key] ?: 0) + it.count }
        kinds[message.kind] = (kinds[message.kind] ?: 0) + 1
    }

    return ChatInsights(
        messageCount = sorted.size,
        oldestTimestamp = sorted.firstOrNull()?.timestamp,
        newestTimestamp = sorted.lastOrNull()?.timestamp,
        senders = senders.values.sortedWith(compareByDescending<SenderCount> { it.messages }.thenBy { it.senderId }),
        heatmap = heatmap.map { it.toList() },
        months = monthsWithGaps(perMonth),
        activeDays = perDay.size,
        longestStreak = longestStreak(perDay.keys),
        currentStreak = currentStreak(perDay.keys, today),
        busiestDay = perDay.entries
            .maxWithOrNull(compareBy<Map.Entry<LocalDate, Int>> { it.value }.thenByDescending { it.key })
            ?.let { DayCount(it.key, it.value) },
        reactions = reactions.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(MAX_REACTIONS)
            .map { ReactionCount(it.key, it.value) },
        kinds = kinds.entries.sortedByDescending { it.value }.map { it.key to it.value },
        replies = if (privateChat) replyHabits(sorted) else null,
    )
}

private fun monthsWithGaps(perMonth: Map<YearMonth, Int>): List<MonthCount> {
    val first = perMonth.keys.minOrNull() ?: return emptyList()
    val last = perMonth.keys.max()
    val months = mutableListOf<MonthCount>()
    var month = first
    while (month <= last) {
        months += MonthCount(month, perMonth[month] ?: 0)
        month = if (month.month.number == 12) YearMonth(month.year + 1, 1) else YearMonth(month.year, month.month.number + 1)
    }
    return months
}

internal fun longestStreak(days: Set<LocalDate>): DayStreak? {
    var best: DayStreak? = null
    var start: LocalDate? = null
    var previous: LocalDate? = null
    for (day in days.sorted()) {
        if (previous == null || previous.plus(DatePeriod(days = 1)) != day) start = day
        val length = (day.toEpochDays() - start!!.toEpochDays()).toInt() + 1
        if (length >= 2 && (best == null || length > best.days)) best = DayStreak(length, start, day)
        previous = day
    }
    return best
}

internal fun currentStreak(days: Set<LocalDate>, today: LocalDate): Int {
    var day = if (today in days) today else today.minus(DatePeriod(days = 1))
    var length = 0
    while (day in days) {
        length++
        day = day.minus(DatePeriod(days = 1))
    }
    return length
}

/**
 * Odpovědi a začátky v rozhovoru dvou (jen vlastní a jedna cizí strana). Odpověď = zpráva druhé
 * strany po zprávě té první, nejvýš [REPLY_WINDOW_SECONDS] po ní — po delší pauze to není
 * odpověď, ale nový rozhovor. Začátek = první zpráva po tichu aspoň [CONVERSATION_GAP_SECONDS].
 * Úplně první zpráva úseku se za začátek nepočítá: co bylo před ní, se neví.
 */
internal fun replyHabits(sorted: List<MessageStat>): ReplyHabits? {
    val others = sorted.filterNot { it.isOutgoing }.map { it.senderId }.toSet()
    if (others.size != 1 || sorted.none { it.isOutgoing }) return null
    val mineReplies = mutableListOf<Long>()
    val theirReplies = mutableListOf<Long>()
    var mineStarts = 0
    var theirStarts = 0
    sorted.zipWithNext().forEach { (previous, current) ->
        val gap = (current.timestamp - previous.timestamp).toLong()
        if (gap >= CONVERSATION_GAP_SECONDS) {
            if (current.isOutgoing) mineStarts++ else theirStarts++
        } else if (current.isOutgoing != previous.isOutgoing && gap <= REPLY_WINDOW_SECONDS) {
            (if (current.isOutgoing) mineReplies else theirReplies) += gap
        }
    }
    return ReplyHabits(
        mine = ReplySide(median(mineReplies), mineStarts),
        theirs = ReplySide(median(theirReplies), theirStarts),
    )
}

private fun median(values: List<Long>): Long? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
}

private const val DAYS_IN_WEEK = 7
private const val HOURS_IN_DAY = 24
private const val MAX_REACTIONS = 8

/** Po tak dlouhém tichu je další zpráva začátek nového rozhovoru (8 h). */
internal const val CONVERSATION_GAP_SECONDS = 8 * 60 * 60L

/** Odpověď po delší době se do doby odpovědi nepočítá (6 h) — je to spíš nový rozhovor. */
internal const val REPLY_WINDOW_SECONDS = 6 * 60 * 60L
