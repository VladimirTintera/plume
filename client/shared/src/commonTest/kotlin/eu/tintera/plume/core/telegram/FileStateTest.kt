package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Dostupný úsek rozestavěného souboru — na něm stojí streamování videa. */
class FileStateTest {

    @Test
    fun usekSePocitaOdOffsetuStahovani() {
        // TDLib plní soubor od `downloadOffset`, ne od začátku: po skoku na konec je k dispozici
        // právě tamten kus, ne prvních N bajtů.
        val state = FileState.Downloading(
            progress = 0.1f,
            path = "/tmp/video.mp4",
            downloadOffset = 1000,
            downloadedPrefixSize = 500,
            totalSize = 10_000,
        )
        assertEquals(1000L..1499L, state.downloaded)
        assertTrue(1000L in state.downloaded)
        assertTrue(1499L in state.downloaded)
        // Začátek souboru dostupný NENÍ, i když je staženo 500 bajtů.
        assertFalse(0L in state.downloaded)
        assertFalse(1500L in state.downloaded)
    }

    @Test
    fun bezStazenychDatJeUsekPrazdny() {
        val state = FileState.Downloading(progress = 0f, path = "/tmp/video.mp4", totalSize = 10_000)
        assertTrue(state.downloaded.isEmpty())
        assertFalse(0L in state.downloaded)
    }
}
