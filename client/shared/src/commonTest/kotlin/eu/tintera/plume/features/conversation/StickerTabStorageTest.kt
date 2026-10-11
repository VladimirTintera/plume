package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.GroupStickerSet
import eu.tintera.plume.core.telegram.StickerSetSummary
import kotlin.test.Test
import kotlin.test.assertEquals

class StickerTabStorageTest {

    private fun set(id: Long) = StickerTab.Set(StickerSetSummary(id = id, title = "Sada $id", cover = null))

    private val tabs = listOf(StickerTab.Recent, StickerTab.Favorites, StickerTab.Trending, set(10), set(20))

    @Test
    fun ulozenaSadaSeNajdePodleId() {
        assertEquals(4, tabs.indexOfStored(set(20).storageKey()))
    }

    @Test
    fun sadaPresunutaNaJineMistoSeNajdeTaky() {
        val reordered = listOf(StickerTab.Recent, StickerTab.Favorites, StickerTab.Trending, set(20), set(10))
        assertEquals(3, reordered.indexOfStored(set(20).storageKey()))
    }

    @Test
    fun pevneZalozky() {
        assertEquals(1, tabs.indexOfStored(StickerTab.Favorites.storageKey()))
        assertEquals(2, tabs.indexOfStored(StickerTab.Trending.storageKey()))
    }

    @Test
    fun odebranaSadaNeboNicUlozenehoDaMinusJedna() {
        assertEquals(-1, tabs.indexOfStored(set(30).storageKey()))
        assertEquals(-1, tabs.indexOfStored(null))
    }

    @Test
    fun klicSadySeNepleteSPevnouZalozkou() {
        // Sada s id 0 nesmí vypadat jako uložené Nedávné ani naopak.
        assertEquals(-1, listOf(set(0)).indexOfStored(StickerTab.Recent.storageKey()))
    }

    private fun group(setId: Long) = GroupStickerSet(
        set = StickerSetSummary(id = setId, title = "Sada skupiny", cover = null),
        chatId = -100L,
        chatTitle = "Skupina",
        chatPhotoFileId = null,
    )

    @Test
    fun sadaSkupinyJeZaOblibenymi() {
        val tabs = stickerTabs(listOf(summary(10)), group(99))
        assertEquals(
            listOf("recent", "favorites", "group:99", "trending", "set:10"),
            tabs.map { it.storageKey() },
        )
    }

    @Test
    fun nainstalovanaSadaSkupinyNeniDvakrat() {
        val tabs = stickerTabs(listOf(summary(10), summary(99)), group(99))
        assertEquals(
            listOf("recent", "favorites", "group:99", "trending", "set:10"),
            tabs.map { it.storageKey() },
        )
    }

    @Test
    fun bezSkupinyJenPevneZalozkyASady() {
        assertEquals(
            listOf("recent", "favorites", "trending", "set:10"),
            stickerTabs(listOf(summary(10)), group = null).map { it.storageKey() },
        )
    }

    private fun summary(id: Long) = StickerSetSummary(id = id, title = "Sada $id", cover = null)
}
