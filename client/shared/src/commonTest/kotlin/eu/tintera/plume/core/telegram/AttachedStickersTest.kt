package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.File
import dev.g000sha256.tdl.dto.LocalFile
import dev.g000sha256.tdl.dto.Photo
import dev.g000sha256.tdl.dto.PhotoSize
import dev.g000sha256.tdl.dto.RemoteFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Příznak „na fotce jsou samolepky" musí převodem projít — podle něj se nabízí jejich sady. */
class AttachedStickersTest {

    private fun file(id: Int) = File(
        id = id,
        size = 0,
        expectedSize = 0,
        local = LocalFile("", false, false, false, false, 0, 0, 0),
        remote = RemoteFile("", "", false, false, 0),
    )

    private fun photo(hasStickers: Boolean) = Photo(
        hasStickers = hasStickers,
        minithumbnail = null,
        sizes = arrayOf(PhotoSize("m", file(1), 320, 240, intArrayOf()), PhotoSize("y", file(2), 2560, 1920, intArrayOf())),
    )

    @Test
    fun fotkaSeSamolepkamiToNese() {
        val media = photo(hasStickers = true).toMedia()!!
        assertTrue(media.hasStickers)
        // Sady se hledají podle nejlepší varianty — té, kterou ukazuje prohlížeč.
        assertEquals(2, media.fullFileId)
    }

    @Test
    fun obycejnaFotkaNe() {
        assertFalse(photo(hasStickers = false).toMedia()!!.hasStickers)
    }
}
