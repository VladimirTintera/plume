package eu.tintera.plume.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** Mazání pozpátku po celých znacích, jak je vidí uživatel. */
class PreviousClusterStartTest {

    private fun deleteLast(text: String): String = text.substring(0, text.previousClusterStart(text.length))

    @Test
    fun obycejnyZnak() = assertEquals("ahoj", deleteLast("ahoj!"))

    @Test
    fun prazdnyText() = assertEquals(0, "".previousClusterStart(0))

    @Test
    fun emojiMimoBmpNenechaPulkuSurrogatu() = assertEquals("a", deleteLast("a😀"))

    @Test
    fun odstinPleti() = assertEquals("a", deleteLast("a👍🏽"))

    @Test
    fun rodinaPresZwj() = assertEquals("a", deleteLast("a👨‍👩‍👧"))

    @Test
    fun vlajkaJeDvojiceIndikatoru() = assertEquals("🇨🇿", deleteLast("🇨🇿🇩🇪"))

    @Test
    fun klavesaSRameckem() = assertEquals("a", deleteLast("a#️⃣"))

    @Test
    fun srdceSVyberemVarianty() = assertEquals("a", deleteLast("a❤️"))

    @Test
    fun vlajkaRegionuSTagy() = assertEquals("a", deleteLast("a🏴󠁧󠁢󠁳󠁣󠁴󠁿"))

    @Test
    fun uprostredTextu() {
        val text = "a😀b"
        // Kurzor za emoji (před „b").
        assertEquals(1, text.previousClusterStart(3))
    }
}
