package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.GifItem
import eu.tintera.plume.core.telegram.GifPage
import eu.tintera.plume.core.telegram.MessageMedia
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GifResultsTest {

    private fun gif(id: Int) = GifItem(
        MessageMedia.Animation(fileId = id, thumbnailFileId = null, width = 1, height = 1, sizeBytes = 1),
    )

    @Test
    fun dalsiStrankaSePripojiBezDuplicit() {
        val first = GifResults(query = "kocka").withPage("kocka", GifPage(listOf(gif(1), gif(2)), "20"))
        val second = first.copy(loading = true).withPage("kocka", GifPage(listOf(gif(2), gif(3)), ""))

        assertEquals(listOf(1, 2, 3), second.gifs.map { it.id })
        assertFalse(second.canLoadMore)
    }

    @Test
    fun strankaProJinyDotazSeZahodi() {
        val current = GifResults(query = "kocka")

        assertSame(current, current.withPage("ko", GifPage(listOf(gif(1)), "")))
        assertSame(current, current.withFailure("ko"))
    }

    @Test
    fun prazdnyVysledekJeNicNenalezenoAzPoOdpovedi() {
        val searching = GifResults(query = "xyz")
        assertFalse(searching.nothingFound)

        assertTrue(searching.withPage("xyz", GifPage(emptyList(), "")).nothingFound)
    }

    @Test
    fun selhaniDalsiStrankyNeniChybaAleKonec() {
        val loaded = GifResults(query = "").withPage("", GifPage(listOf(gif(1)), "20"))
        val failed = loaded.copy(loading = true).withFailure("")

        assertFalse(failed.failed)
        assertFalse(failed.canLoadMore)
        assertEquals(1, failed.gifs.size)
    }
}
