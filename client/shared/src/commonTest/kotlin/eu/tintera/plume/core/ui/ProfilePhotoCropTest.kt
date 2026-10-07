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

    private class FakeProfileVideo(private val duration: Long = 25_000) : ProfileVideo {
        override suspend fun durationMs(path: String): Long = duration
        override suspend fun frame(path: String, atMs: Long, edge: Int): String =
            if (atMs == 0L) "$path-frame" else "$path-frame-$atMs"
        override suspend fun crop(path: String, region: CropRegion?, startMs: Long): String =
            "$path-square-$startMs.mp4"
    }

    private val region = CropRegion(0f, 0f, 1f, 1f)

    private fun crop() = ProfilePhotoCrop(FakeSquarePhoto(), FakeProfileVideo())

    @Test
    fun fotkaSeOrizneJakoStatickaFotka() = runTest {
        val crop = crop()
        assertTrue(crop.open(listOf(OutgoingAttachment.Photo("foto.jpg", 100, 100))))
        assertEquals("foto.jpg", crop.image)
        assertNull(crop.video)

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
        assertEquals(VideoTrim(25_000), crop.video?.trim)

        val prepared = crop.crop(region)
        assertEquals(ProfilePhotoFile.Animated("video.mp4-square-0.mp4"), prepared?.file)
        // Náhled je týž výřez ze snímku.
        assertEquals("video.mp4-frame-square", prepared?.preview)
        assertNull(crop.video)
    }

    @Test
    fun vybranyUsekASnimekJdouDoAnimaceINahledu() = runTest {
        val crop = crop()
        crop.open(listOf(OutgoingAttachment.Video("video.mp4", 1280, 720, 25, thumbnail = null, streamable = false)))
        val control = crop.video!!
        control.moveTo(12_000)
        control.chooseCover(15_500)

        val prepared = crop.crop(region)
        // Animace začíná na 12 s, statická fotka je 3,5 s do ní.
        assertEquals(ProfilePhotoFile.Animated("video.mp4-square-12000.mp4", mainFrameSeconds = 3.5), prepared?.file)
        assertEquals("video.mp4-frame-15500-square", prepared?.preview)
    }

    @Test
    fun bezDelkyZeSouboruSeBereDelkaZVyberu() = runTest {
        val crop = ProfilePhotoCrop(FakeSquarePhoto(), FakeProfileVideo(duration = 0))
        crop.open(listOf(OutgoingAttachment.Video("video.mp4", 1280, 720, 7, thumbnail = null, streamable = false)))
        assertEquals(7_000, crop.video?.trim?.durationMs)
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
