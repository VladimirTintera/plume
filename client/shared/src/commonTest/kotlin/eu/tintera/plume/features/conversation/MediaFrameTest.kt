package eu.tintera.plume.features.conversation

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** Rámeček fotky v bublině: zužuje se podle poměru stran, místo aby se ořezávala. */
class MediaFrameTest {

    /** Šířka, kterou bublina nabízí na běžném telefonu. */
    private val available = 260.dp

    private fun assertDp(expected: Float, actual: Dp) =
        assertEquals(expected, actual.value, absoluteTolerance = 0.5f)

    @Test
    fun fotkaNaSirkuZabereCelouSirku() {
        val frame = photoFrame(available, 16f / 9f)

        assertDp(260f, frame.width)
        assertDp(146.25f, frame.height)
    }

    @Test
    fun ctverecZabereCelouSirkuDokudSeVejdeDoStropu() {
        val frame = photoFrame(available, 1f)

        assertDp(260f, frame.width)
        assertDp(260f, frame.height)
    }

    @Test
    fun snimekObrazovkySeZuziMistoOrezu() {
        // 1080 x 2400 — právě ten případ, kdy v bublině byl vidět jen prostřední pruh.
        val frame = photoFrame(available, 1080f / 2400f)

        assertDp(MAX_PHOTO_HEIGHT.value, frame.height)
        assertDp(144f, frame.width)
        // Poměr stran musí sedět, to je celý smysl změny.
        assertEquals(1080f / 2400f, frame.width / frame.height, absoluteTolerance = 0.01f)
    }

    @Test
    fun extremneVysokaFotkaSeZastaviNaPodlazeSirky() {
        val frame = photoFrame(available, 0.1f)

        assertDp(MAX_PHOTO_HEIGHT.value, frame.height)
        assertDp(MIN_PHOTO_WIDTH.value, frame.width)
    }

    @Test
    fun uzkaBublinaPodlahouNeprerusta() {
        val frame = photoFrame(100.dp, 0.1f)

        assertDp(100f, frame.width)
    }

    @Test
    fun panoramaSeVejdeNaSirku() {
        val frame = photoFrame(available, 3f)

        assertDp(260f, frame.width)
        assertDp(86.67f, frame.height)
    }

    @Test
    fun neznameRozmeryBerouCtverec() {
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { ratio ->
            val frame = photoFrame(available, ratio)

            assertDp(260f, frame.width)
            assertDp(260f, frame.height)
        }
    }

    @Test
    fun ctverecRespektujeStropVysky() {
        val frame = photoFrame(400.dp, 1f)

        assertDp(MAX_PHOTO_HEIGHT.value, frame.height)
        assertDp(MAX_PHOTO_HEIGHT.value, frame.width)
    }

    @Test
    fun vysokaFotkaSPopiskemSeRozsiriAOrizne() {
        val available = 400.dp
        val frame = photoFrame(available, 1080f / 2400f, fillWidth = true)
        // Celá nabízená šířka, výška na stropu — prostředek fotky, poměr stran zachovaný ořezem.
        assertEquals(available, frame.width)
        assertEquals(MAX_PHOTO_HEIGHT, frame.height)
    }

    @Test
    fun sirokaFotkaSPopiskemSeNemeni() {
        val available = 400.dp
        assertEquals(photoFrame(available, 16f / 9f), photoFrame(available, 16f / 9f, fillWidth = true))
    }

    @Test
    fun panoramaSPopiskemSeNenataheDoVysky() {
        // Široká fotka, která by se sama zúžila jen kvůli úzké bublině: výška z šířky, ne ze stropu.
        val frame = photoFrame(200.dp, 4f, fillWidth = true)
        assertEquals(200.dp, frame.width)
        assertEquals(50.dp, frame.height)
    }

    @Test
    fun pomerStranZMetadatSNahradouProNulu() {
        assertEquals(2f, aspectRatio(400, 200))
        // Neznámé rozměry nedělí nulou, vrátí náhradu.
        assertEquals(1f, aspectRatio(0, 200))
        assertEquals(16f / 9f, aspectRatio(640, 0, fallback = 16f / 9f))
    }
}
