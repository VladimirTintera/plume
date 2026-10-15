package eu.tintera.plume.features.media

import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.ui.UiText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BufferedSpanTest {

    @Test
    fun hotovySouborJeCely() {
        assertEquals(0f..1f, bufferedSpan(FileState.Ready("/video.mp4")))
    }

    @Test
    fun stahovaniOdZacatku() {
        val file = FileState.Downloading(progress = 0.25f, downloadOffset = 0, downloadedPrefixSize = 250, totalSize = 1000)
        assertEquals(0f..0.25f, bufferedSpan(file))
    }

    @Test
    fun poSkokuSeBereUsekOdMistaStahovani() {
        // Začátek je na disku taky (`progress`), ale přehrávač teď čte od 600.
        val file = FileState.Downloading(progress = 0.5f, downloadOffset = 600, downloadedPrefixSize = 100, totalSize = 1000)
        assertEquals(0.6f..0.7f, bufferedSpan(file))
    }

    @Test
    fun nicStazenehoNicNekresli() {
        assertNull(bufferedSpan(FileState.Downloading(progress = 0f, downloadOffset = 600, downloadedPrefixSize = 0, totalSize = 1000)))
        assertNull(bufferedSpan(FileState.Downloading(progress = 0f)))
        assertNull(bufferedSpan(FileState.Pending))
        assertNull(bufferedSpan(FileState.Failed(UiText.Raw(""))))
    }
}
