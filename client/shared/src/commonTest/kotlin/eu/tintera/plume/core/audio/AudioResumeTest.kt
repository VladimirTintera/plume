package eu.tintera.plume.core.audio

import eu.tintera.plume.core.video.END_MARGIN_MS
import eu.tintera.plume.core.video.MIN_RESUME_AUDIO_DURATION_MS
import eu.tintera.plume.core.video.MIN_RESUME_DURATION_MS
import eu.tintera.plume.core.video.VideoPosition
import eu.tintera.plume.core.video.resumePosition
import eu.tintera.plume.core.video.withPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioResumeTest {

    private val podcast = 45 * 60 * 1000L

    @Test
    fun pamatovanaPoziceVDlouheNahravcePokracuje() {
        assertEquals(AudioStart(754_000, resumedFromMs = 754_000), audioStart(requestedMs = 0, rememberedMs = 754_000, durationMs = podcast))
    }

    @Test
    fun vyslovnyCasMaPrednostPredPamatovanou() {
        assertEquals(AudioStart(83_000), audioStart(requestedMs = 83_000, rememberedMs = 754_000, durationMs = podcast))
    }

    @Test
    fun casZaKoncemZacneOdZacatku() {
        assertEquals(AudioStart(0), audioStart(requestedMs = podcast + 1, rememberedMs = null, durationMs = podcast))
    }

    @Test
    fun kratkaNahravkaSPamatovanouPoziciNepokracuje() {
        // Seznam pozic je společný s videem; u zvuku pod 10 min se pozice nepoužije.
        val short = MIN_RESUME_AUDIO_DURATION_MS - 1
        assertEquals(AudioStart(0), audioStart(requestedMs = 0, rememberedMs = 60_000, durationMs = short))
    }

    @Test
    fun poziceUKonceSeNepouzije() {
        assertEquals(AudioStart(0), audioStart(requestedMs = 0, rememberedMs = podcast - END_MARGIN_MS + 1, durationMs = podcast))
    }

    @Test
    fun zvukMaVyssiPrahNezVideo() {
        val nineMinutes = 9 * 60 * 1000L
        // Devítiminutové video se pamatuje, devítiminutová hlasovka ne.
        assertEquals(60_000, resumePosition(60_000, nineMinutes))
        assertNull(resumePosition(60_000, nineMinutes, MIN_RESUME_AUDIO_DURATION_MS))
        assertTrue(MIN_RESUME_AUDIO_DURATION_MS > MIN_RESUME_DURATION_MS)
    }

    @Test
    fun kratkaNahravkaSmazePredchoziZaznam() {
        val list = listOf(VideoPosition("a", 60_000, podcast))
            .withPosition("a", 60_000, durationMs = 5 * 60 * 1000L, minDurationMs = MIN_RESUME_AUDIO_DURATION_MS)
        assertEquals(emptyList(), list)
    }

    @Test
    fun dlouhaNahravkaSeZapise() {
        val list = emptyList<VideoPosition>()
            .withPosition("a", 754_000, durationMs = podcast, minDurationMs = MIN_RESUME_AUDIO_DURATION_MS)
        assertEquals(listOf(VideoPosition("a", 754_000, podcast)), list)
    }
}
