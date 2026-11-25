package eu.tintera.plume.core.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileVideoTest {

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
}
