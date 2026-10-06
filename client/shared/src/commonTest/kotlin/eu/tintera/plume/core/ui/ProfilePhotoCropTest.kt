package eu.tintera.plume.core.ui

import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.ProfilePhotoFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ProfilePhotoCropTest {

    private class FakeSquarePhoto : SquarePhoto {
        override suspend fun crop(path: String, region: CropRegion?): String = "$path-square"
    }

    private class FakeProfileVideo : ProfileVideo {
        override suspend fun frame(path: String): String = "$path-frame"
        override suspend fun crop(path: String, region: CropRegion?): String = "$path-square.mp4"
    }

    private val region = CropRegion(0f, 0f, 1f, 1f)

    private fun crop() = ProfilePhotoCrop(FakeSquarePhoto(), FakeProfileVideo())

    @Test
    fun fotkaSeOrizneJakoStatickaFotka() = runTest {
        val crop = crop()
        assertTrue(crop.open(listOf(OutgoingAttachment.Photo("foto.jpg", 100, 100))))
        assertEquals("foto.jpg", crop.image)
        assertFalse(crop.isVideo)

        val prepared = crop.crop(region)
        assertEquals(ProfilePhotoFile.Still("foto.jpg-square"), prepared?.file)
        assertEquals("foto.jpg-square", prepared?.preview)
        assertNull(crop.image)
    }

    @Test
    fun videoSeOrezavaNadSnimkemANahrajeSeJakoAnimace() = runTest {
        val crop = crop()
        val video = OutgoingAttachment.Video("video.mp4", 1280, 720, 20, thumbnail = null, streamable = false)
        assertTrue(crop.open(listOf(video)))
        // V ořezu je snímek, ne video — Coil video neotevře.
        assertEquals("video.mp4-frame", crop.image)
        assertTrue(crop.isVideo)

        val prepared = crop.crop(region)
        assertEquals(ProfilePhotoFile.Animated("video.mp4-square.mp4"), prepared?.file)
        // Náhled je týž výřez ze snímku.
        assertEquals("video.mp4-frame-square", prepared?.preview)
    }

    @Test
    fun souborProfilovkouBytNemuze() = runTest {
        val crop = crop()
        assertFalse(crop.open(listOf(OutgoingAttachment.Document("dokument.pdf"))))
        assertFalse(crop.open(emptyList()))
        assertNull(crop.image)
    }

    @Test
    fun zrusenyOrezNicNevyrobi() = runTest {
        val crop = crop()
        crop.open(listOf(OutgoingAttachment.Photo("foto.jpg", 100, 100)))
        crop.cancel()
        assertNull(crop.image)
        assertNull(crop.crop(region))
    }
}
