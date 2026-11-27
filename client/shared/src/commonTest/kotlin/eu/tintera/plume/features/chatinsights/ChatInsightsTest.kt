package eu.tintera.plume.features.chatinsights

import eu.tintera.plume.core.telegram.MessageStat
import eu.tintera.plume.core.telegram.MessageStatKind
import eu.tintera.plume.core.telegram.ReactionCount
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatInsightsTest {

    private val zone = TimeZone.UTC
    private var nextId = 1L

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Int =
        LocalDateTime(year, month, day, hour, minute).toInstant(zone).epochSeconds.toInt()

    private fun stat(
        time: Int,
        outgoing: Boolean = false,
        sender: Long = if (outgoing) ME else PARTNER,
        kind: MessageStatKind = MessageStatKind.Text,
        characters: Int = 5,
        reactions: List<ReactionCount> = emptyList(),
    ) = MessageStat(nextId++, time, sender, outgoing, kind, characters, reactions)

    private fun compute(messages: List<MessageStat>, today: LocalDate = LocalDate(2026, 11, 27), privateChat: Boolean = true) =
        computeChatInsights(messages, zone, today, privateChat)

    @Test
    fun zebricekOdNejvicPisicihoSeZnaky() {
        val insights = compute(
            listOf(
                stat(at(2026, 11, 1), sender = 1, characters = 10),
                stat(at(2026, 11, 1), sender = 2),
                stat(at(2026, 11, 2), sender = 1, characters = 3),
            ),
        )
        assertEquals(listOf(1L to 2, 2L to 1), insights.senders.map { it.senderId to it.messages })
        assertEquals(13, insights.senders.first().characters)
    }

    @Test
    fun tydenPoHodinachVMistnimCase() {
        // 23. 11. 2026 je pondělí.
        val insights = compute(listOf(stat(at(2026, 11, 23, hour = 21)), stat(at(2026, 11, 23, hour = 21)), stat(at(2026, 11, 29, hour = 8))))
        assertEquals(2, insights.heatmap[0][21])
        assertEquals(1, insights.heatmap[6][8])
        assertEquals(0 to 21, insights.peak)
    }

    @Test
    fun mesiceIsPrazdnymiMeziNimi() {
        val insights = compute(listOf(stat(at(2025, 11, 5)), stat(at(2026, 2, 1)), stat(at(2026, 2, 3))))
        assertEquals(
            listOf(YearMonth(2025, 11) to 1, YearMonth(2025, 12) to 0, YearMonth(2026, 1) to 0, YearMonth(2026, 2) to 2),
            insights.months.map { it.month to it.messages },
        )
    }

    @Test
    fun serieANejrusnejsiDen() {
        val insights = compute(
            listOf(
                stat(at(2026, 11, 1)), stat(at(2026, 11, 2)), stat(at(2026, 11, 3)), stat(at(2026, 11, 3)),
                stat(at(2026, 11, 10)),
                stat(at(2026, 11, 26)), stat(at(2026, 11, 27)),
            ),
        )
        assertEquals(DayStreak(3, LocalDate(2026, 11, 1), LocalDate(2026, 11, 3)), insights.longestStreak)
        assertEquals(2, insights.currentStreak)
        assertEquals(DayCount(LocalDate(2026, 11, 3), 2), insights.busiestDay)
        assertEquals(6, insights.activeDays)
    }

    @Test
    fun soucasnaSerieTrvaIBezDnesniZpravy() {
        val days = setOf(LocalDate(2026, 11, 25), LocalDate(2026, 11, 26))
        assertEquals(2, currentStreak(days, today = LocalDate(2026, 11, 27)))
        assertEquals(0, currentStreak(days, today = LocalDate(2026, 11, 28)))
    }

    @Test
    fun jedenDenNeniSerie() {
        assertNull(longestStreak(setOf(LocalDate(2026, 11, 1), LocalDate(2026, 11, 3))))
    }

    @Test
    fun reakceSeSectouAseradi() {
        val insights = compute(
            listOf(
                stat(at(2026, 11, 1), reactions = listOf(ReactionCount("👍", 2), ReactionCount("❤", 1))),
                stat(at(2026, 11, 2), reactions = listOf(ReactionCount("❤", 3))),
            ),
        )
        assertEquals(listOf(ReactionCount("❤", 4), ReactionCount("👍", 2)), insights.reactions)
    }

    @Test
    fun odpovediAZacatkyVeDvou() {
        val base = at(2026, 11, 1, hour = 10)
        val insights = compute(
            listOf(
                stat(base), // první zpráva úseku: začátek se nepočítá
                stat(base + 120, outgoing = true), // odpovídám za 2 min
                stat(base + 420), // protějšek za 5 min
                stat(base + 600, outgoing = true), // já za 3 min
                stat(base + 10 * 3600, outgoing = true), // po 10 h ticha začínám já
                stat(base + 10 * 3600 + 60), // protějšek za minutu
            ),
        )
        val replies = insights.replies!!
        assertEquals(150L, replies.mine.medianReplySeconds) // medián ze 120 a 180
        assertEquals(180L, replies.theirs.medianReplySeconds) // medián z 300 a 60
        assertEquals(1, replies.mine.starts)
        assertEquals(0, replies.theirs.starts)
    }

    @Test
    fun odpovedPoDlouhePauzeSeNepocita() {
        val base = at(2026, 11, 1)
        // 7 h: delší než okno odpovědi, kratší než ticho pro nový rozhovor.
        val insights = compute(listOf(stat(base), stat(base + 7 * 3600, outgoing = true)))
        assertNull(insights.replies!!.mine.medianReplySeconds)
    }

    @Test
    fun veSkupineSeDvemaPisicimiOdpovediNejsou() {
        val base = at(2026, 11, 1)
        val insights = compute(listOf(stat(base), stat(base + 60, outgoing = true)), privateChat = false)
        assertNull(insights.replies)
    }

    @Test
    fun veSkupineOdpovediNejsou() {
        val insights = compute(listOf(stat(at(2026, 11, 1), sender = 1), stat(at(2026, 11, 1), sender = 2), stat(at(2026, 11, 1), outgoing = true)))
        assertNull(insights.replies)
    }

    @Test
    fun prazdnyUsek() {
        val insights = compute(emptyList())
        assertEquals(0, insights.messageCount)
        assertNull(insights.peak)
        assertEquals(emptyList(), insights.months)
    }

    private companion object {
        const val ME = 100L
        const val PARTNER = 200L
    }
}
