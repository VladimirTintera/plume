package eu.tintera.plume.core.ui

import eu.tintera.plume.core.telegram.OutgoingAttachment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileVideoTest {

    private fun video(name: String) = OutgoingAttachment.Video(
        path = "/outgoing/a/$name", width = 1920, height = 1080, durationSeconds = 5, thumbnail = null, streamable = true,
    )

    @Test
    fun mp4RodinaJdeJakoVideo() {
        assertTrue(isFileVideo("Svatba 2025.mp4", "video/mp4"))
        assertTrue(isFileVideo("klip.MOV", "video/quicktime"))
        assertTrue(isFileVideo("klip.m4v", "video/x-m4v"))
    }

    @Test
    fun jineKontejneryZustavajiSouborem() {
        assertFalse(isFileVideo("film.mkv", "video/x-matroska"))
        assertFalse(isFileVideo("film.webm", "video/webm"))
        assertFalse(isFileVideo("film.avi", "video/x-msvideo"))
    }

    @Test
    fun typOdSystemuMaPrednostPredPriponou() {
        // Matroska s příponou .mp4 by se jako video jinde nemusela přehrát.
        assertFalse(isFileVideo("film.mp4", "video/x-matroska"))
    }

    @Test
    fun neznamyTypRozhodnePripona() {
        assertTrue(isFileVideo("Svatba.mp4", "application/octet-stream"))
        assertTrue(isFileVideo("Svatba.mp4", null))
        assertFalse(isFileVideo("smlouva.pdf", null))
        assertFalse(isFileVideo("bez-pripony", null))
    }

    @Test
    fun sameVideaZustavajiVidei() {
        val picked = listOf(video("a.mp4"), video("b.mov"))
        assertEquals(picked, videosOnlyWhenAll(picked))
    }

    @Test
    fun smisenyVyberJdeCelyJakoSoubory() {
        val pdf = OutgoingAttachment.Document("/outgoing/b/smlouva.pdf")
        val result = videosOnlyWhenAll(listOf(video("Svatba.mp4"), pdf))
        assertEquals(listOf(OutgoingAttachment.Document("/outgoing/a/Svatba.mp4"), pdf), result, "pořadí i jméno zůstávají")
    }

    @Test
    fun prazdnyVyberSeNemeni() {
        assertEquals(emptyList(), videosOnlyWhenAll(emptyList()))
    }
}
