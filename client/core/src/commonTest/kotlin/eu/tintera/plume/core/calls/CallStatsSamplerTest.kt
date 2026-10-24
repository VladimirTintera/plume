package eu.tintera.plume.core.calls

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CallStatsSamplerTest {

    private val lines = mutableListOf<String>()
    private val sampler = CallStatsSampler { lines += it }

    private var rx = 0L
    private var played = 0L
    private var at = 0L

    /** Jedna vteřina s [bytes] přijatými a přehráváním, které ([playing]) běží, nebo stojí. */
    private fun second(bytes: Long, playing: Boolean = true, ms: Long = 1_000) {
        rx += bytes
        if (playing) played++
        at += ms
        sampler.sample(CallMediaStats(rxBytes = rx, txBytes = rx, playedSeconds = played, capturedSeconds = played), at)
    }

    @Test
    fun steadyCallWritesOnlySummaries() {
        second(5_000)
        repeat(10) { second(5_000) }
        assertEquals(2, lines.size)
        assertTrue(lines.all { it.startsWith("5 s: rx 5000 B/s (min 5000), tx 5000 B/s, played +5 s") }, lines.toString())
    }

    @Test
    fun stallIsReportedOnceAfterTwoSecondsAndItsEnd() {
        second(5_000)
        second(5_000)
        second(200, playing = false)
        assertTrue(lines.none { it.startsWith("STALL") }, "jedna vteřina ještě není zaseknutí")
        second(0, playing = false)
        second(0, playing = false)
        second(5_000)
        val stall = lines.filter { it.startsWith("STALL") }
        assertEquals(listOf("STALL: receiving 0 B/s, playback stopped"), stall)
        assertTrue(lines.any { it == "Stall over after 3 s, receiving 5000 B/s" }, lines.toString())
    }

    @Test
    fun pausedProcessIsReportedAndDoesNotLookLikeABurst() {
        second(5_000)
        second(15_000, ms = 3_000)
        assertTrue(lines.any { it == "Sampling stalled for 3000 ms (process paused?)" }, lines.toString())
        assertTrue(lines.none { it.startsWith("STALL") })
    }

    @Test
    fun withoutByteCountersStoppedPlaybackIsTheStall() {
        var playedNow = 0L
        var t = 0L
        fun tick(playing: Boolean) {
            if (playing) playedNow++
            t += 1_000
            sampler.sample(CallMediaStats(rxBytes = null, txBytes = null, playedSeconds = playedNow, capturedSeconds = playedNow), t)
        }
        tick(true)
        tick(true)
        tick(false)
        tick(true)
        assertTrue(lines.none { it.startsWith("STALL") }, "jedna nulová vteřina je zaokrouhlení: $lines")
        tick(false)
        tick(false)
        tick(true)
        assertEquals(listOf("STALL: playback stopped", "Stall over after 2 s"), lines.filter { it.startsWith("STALL") || it.startsWith("Stall") })
    }

    @Test
    fun unknownNumbersAreLeftOut() {
        sampler.sample(CallMediaStats(null, null, null, null), 0)
        repeat(5) { i -> sampler.sample(CallMediaStats(null, null, null, null), (i + 1) * 1_000L) }
        assertTrue(lines.isEmpty(), lines.toString())
    }
}
