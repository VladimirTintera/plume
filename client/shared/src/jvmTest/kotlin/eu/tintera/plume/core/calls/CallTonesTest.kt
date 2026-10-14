package eu.tintera.plume.core.calls

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CallTonesTest {

    @Test
    fun ringtoneLoopsEveryThreeSecondsWithoutClipping() {
        val pcm = CallTones.ringtone()
        assertEquals(3 * CallTones.SAMPLE_RATE, pcm.size)
        val peak = pcm.maxOf { abs(it.toInt()) }
        assertTrue(peak in 1..Short.MAX_VALUE * 6 / 10, "špička $peak")
        // Konec periody je ticho, jinak by smyčka na švu lupla.
        assertTrue(pcm.takeLast(CallTones.SAMPLE_RATE / 2).all { it.toInt() == 0 })
    }

    @Test
    fun ringbackIsOneSecondOnFourOff() {
        val pcm = CallTones.ringback()
        assertEquals(5 * CallTones.SAMPLE_RATE, pcm.size)
        val sounding = pcm.indexOfLast { it.toInt() != 0 }
        assertTrue(sounding < CallTones.SAMPLE_RATE, "zní do vzorku $sounding")
        // Náběh bez skoku: první vzorek je ticho.
        assertEquals(0, pcm.first().toInt())
    }
}
