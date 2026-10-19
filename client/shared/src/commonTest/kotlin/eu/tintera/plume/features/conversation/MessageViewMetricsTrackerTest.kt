package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.MessageViewMetrics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageViewMetricsTrackerTest {

    private val tracker = MessageViewMetricsTracker()

    /** Viewport 0–1000 px; zpráva [key] leží v `[start, end)`. */
    private fun frame(now: Long, vararg messages: VisibleMessage) =
        tracker.frame(now, messages.toList(), viewportStart = 0, viewportEnd = 1000)

    @Test
    fun zmizeniNaViceNez300msZobrazeniNahlasi() {
        frame(0, VisibleMessage(1, 100, 300))
        frame(2_000)

        // Zmizení samo nestačí — teprve po 300 ms.
        assertTrue(tracker.tick(2_300).isEmpty())
        val reported = tracker.tick(2_301)

        assertEquals(listOf(1L), reported.map { it.messageId })
        assertEquals(2_000, reported.single().timeInViewMs)
    }

    @Test
    fun kratkeZmizeniZobrazeniNepreurusi() {
        frame(0, VisibleMessage(1, 100, 300))
        frame(1_000)
        assertTrue(tracker.tick(1_200).isEmpty())
        frame(1_250, VisibleMessage(1, 100, 300))
        assertTrue(tracker.tick(5_000).isEmpty())

        val reported = tracker.flush(6_000)
        // Mimo obrazovku se čas nepočítá: 1000 ms + 4750 ms.
        assertEquals(5_750, reported.single().timeInViewMs)
    }

    @Test
    fun petMinutZobrazeniSeNahlasiANovePocitaOdZnovu() {
        frame(0, VisibleMessage(1, 100, 300))
        assertTrue(tracker.tick(MessageViewMetricsTracker.MAX_VIEW_MS - 1).isEmpty())

        val first = tracker.tick(MessageViewMetricsTracker.MAX_VIEW_MS)
        assertEquals(MessageViewMetricsTracker.MAX_VIEW_MS.toInt(), first.single().timeInViewMs)

        val second = tracker.flush(MessageViewMetricsTracker.MAX_VIEW_MS + 10_000)
        assertEquals(10_000, second.single().timeInViewMs)
    }

    @Test
    fun zanikViewportuPosleVseRozpracovane() {
        frame(0, VisibleMessage(1, 0, 200), VisibleMessage(2, 200, 400))

        val reported = tracker.flush(500)

        assertEquals(setOf(1L, 2L), reported.map { it.messageId }.toSet())
        assertTrue(tracker.flush(600).isEmpty())
    }

    @Test
    fun aktivniCasJenDo15sOdAkceUzivatele() {
        frame(0, VisibleMessage(1, 100, 300))
        tracker.userAction(1_000)

        val reported = tracker.flush(30_000)

        assertEquals(30_000, reported.single().timeInViewMs)
        assertEquals(15_000, reported.single().activeTimeInViewMs)
    }

    @Test
    fun bezAkceUzivateleNeniCasAktivni() {
        frame(0, VisibleMessage(1, 100, 300))
        assertEquals(0, tracker.flush(5_000).single().activeTimeInViewMs)
    }

    @Test
    fun vysePomeruAVidenyRozsah() {
        // Zpráva vysoká 2000 px (dvakrát viewport), postupně vidět horní a dolní kus.
        frame(0, VisibleMessage(1, -500, 1_500))
        frame(1_000, VisibleMessage(1, -1_200, 800))

        val metrics: MessageViewMetrics = tracker.flush(2_000).single()

        assertEquals(2_000, metrics.heightToViewportRatioPerMille)
        // Viděno 500–1500 a 1200–2000 → obal 500–2000 = 1500 z 2000.
        assertEquals(750, metrics.seenRangeRatioPerMille)
    }

    @Test
    fun zpravaPodListouSeNepocita() {
        // Celá pod odsazením (mimo viditelnou část 0–1000).
        frame(0, VisibleMessage(1, 1_000, 1_200))

        assertTrue(tracker.flush(5_000).isEmpty())
    }
}
