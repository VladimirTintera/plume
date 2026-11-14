package eu.tintera.plume.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ListeningTrackerTest {

    private var clock = Duration.ZERO
    private val reports = mutableListOf<Pair<Int, Int>>()
    private val tracker = ListeningTracker<Int>(now = { clock }, report = { id, s -> reports += id to s })

    @Test
    fun poslechSeNahlasiPriPrepnutiNaJinouSkladbu() {
        tracker.update(fileId = 1, playing = true)
        clock += 30.seconds
        tracker.update(fileId = 2, playing = true)

        assertEquals(listOf(1 to 30), reports)
    }

    @Test
    fun pauzaSeNepocita() {
        tracker.update(fileId = 1, playing = true)
        clock += 10.seconds
        tracker.update(fileId = 1, playing = false)
        clock += 60.seconds
        tracker.update(fileId = 1, playing = true)
        clock += 5.seconds
        tracker.update(fileId = null, playing = false)

        assertEquals(listOf(1 to 15), reports)
    }

    @Test
    fun nacitaniPredSpustenimSeNepocita() {
        // Skladba vybraná, ale stahuje se (nehraje).
        tracker.update(fileId = 1, playing = false)
        clock += 20.seconds
        tracker.update(fileId = 1, playing = true)
        clock += 3.seconds
        tracker.update(fileId = null, playing = false)

        assertEquals(listOf(1 to 3), reports)
    }

    @Test
    fun omylemTuknutaSkladbaSeNehlasi() {
        tracker.update(fileId = 1, playing = true)
        clock += Duration.parse("0.4s")
        tracker.update(fileId = 2, playing = true)

        assertEquals(emptyList(), reports)
    }

    @Test
    fun zastaveniNahlasiRozehranouSkladbu() {
        tracker.update(fileId = 7, playing = true)
        clock += 42.seconds
        tracker.update(fileId = null, playing = false)
        // Další zastavení už nic nehlásí.
        tracker.update(fileId = null, playing = false)

        assertEquals(listOf(7 to 42), reports)
    }
}
