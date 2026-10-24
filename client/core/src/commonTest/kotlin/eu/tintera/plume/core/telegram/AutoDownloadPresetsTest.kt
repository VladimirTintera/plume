package eu.tintera.plume.core.telegram

import eu.tintera.plume.core.settings.AutoDownloadLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AutoDownloadPresetsTest {

    private val mb = 1024L * 1024

    /** Co Plume zapíše na server, musí se při dalším přihlášení vrátit jako tatáž úroveň. */
    @Test
    fun urovenProjdeServeremBezZmeny() {
        for (level in AutoDownloadLevel.entries) {
            val limits = serverLimits(level, serverPhotoBytes = 1 * mb)
            val back = if (limits == null) {
                autoDownloadLevelOf(enabled = false, maxPhotoBytes = mb, maxVideoBytes = 10 * mb)
            } else {
                autoDownloadLevelOf(enabled = true, maxPhotoBytes = limits.photoBytes, maxVideoBytes = limits.videoBytes)
            }
            assertEquals(level, back)
        }
    }

    @Test
    fun vypnutePredvolbyJsouVypnuto() {
        assertEquals(AutoDownloadLevel.Off, autoDownloadLevelOf(enabled = false, maxPhotoBytes = mb, maxVideoBytes = 20 * mb))
        assertEquals(AutoDownloadLevel.Off, autoDownloadLevelOf(enabled = true, maxPhotoBytes = 0, maxVideoBytes = 0))
    }

    /** Předvolby oficiálních klientů na stropy Plume nesedí; berou se podle nejbližšího v poměru. */
    @Test
    fun predvolbaServeruPadneNaNejblizsiUroven() {
        assertEquals(AutoDownloadLevel.Low, autoDownloadLevelOf(enabled = true, maxPhotoBytes = mb, maxVideoBytes = 0))
        assertEquals(AutoDownloadLevel.Low, autoDownloadLevelOf(enabled = true, maxPhotoBytes = mb, maxVideoBytes = 2 * mb))
        assertEquals(AutoDownloadLevel.Medium, autoDownloadLevelOf(enabled = true, maxPhotoBytes = mb, maxVideoBytes = 3 * mb))
        assertEquals(AutoDownloadLevel.Medium, autoDownloadLevelOf(enabled = true, maxPhotoBytes = mb, maxVideoBytes = 9 * mb))
        assertEquals(AutoDownloadLevel.High, autoDownloadLevelOf(enabled = true, maxPhotoBytes = mb, maxVideoBytes = 15 * mb))
    }

    /** Výchozí předvolby Telegramu (naměřené) dají výchozí úrovně Plume. */
    @Test
    fun vychoziPredvolbyTelegramuJsouVychoziPlume() {
        assertEquals(AutoDownloadLevel.Low, autoDownloadLevelOf(enabled = true, maxPhotoBytes = mb, maxVideoBytes = 0))
        assertEquals(AutoDownloadLevel.Medium, autoDownloadLevelOf(enabled = true, maxPhotoBytes = mb, maxVideoBytes = 10 * mb))
        assertEquals(AutoDownloadLevel.High, autoDownloadLevelOf(enabled = true, maxPhotoBytes = mb, maxVideoBytes = 15 * mb))
    }

    @Test
    fun vypnutiNechaStropy() {
        assertNull(serverLimits(AutoDownloadLevel.Off, serverPhotoBytes = mb))
    }

    @Test
    fun fotkyVypnuteNaServeruDostanouStrop() {
        assertEquals(MAX_PHOTO_BYTES, serverLimits(AutoDownloadLevel.Low, serverPhotoBytes = 0)?.photoBytes)
        assertEquals(mb, serverLimits(AutoDownloadLevel.Low, serverPhotoBytes = mb)?.photoBytes)
    }
}
