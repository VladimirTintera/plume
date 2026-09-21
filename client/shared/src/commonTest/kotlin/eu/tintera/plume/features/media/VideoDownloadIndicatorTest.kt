package eu.tintera.plume.features.media

import eu.tintera.plume.core.telegram.FileState
import kotlin.test.Test
import kotlin.test.assertEquals

/** Stažený úsek jako podíl délky souboru — tentýž pro kroužek i čáru. */
class VideoDownloadIndicatorTest {

    @Test
    fun usekOdZacatku() {
        val state = FileState.Downloading(progress = 0.25f, downloadedPrefixSize = 250, totalSize = 1000)
        assertEquals(0f..0.25f, downloadedFractions(state))
    }

    @Test
    fun usekPoPresunuStahovani() {
        // Po skoku pro `moov` atom na konec souboru je k dispozici úsek odtamtud, ne od nuly.
        val state = FileState.Downloading(progress = 0.9f, downloadOffset = 900, downloadedPrefixSize = 50, totalSize = 1000)
        assertEquals(0.9f..0.95f, downloadedFractions(state))
    }

    @Test
    fun neznamaVelikostJePrazdnyUsek() {
        assertEquals(0f..0f, downloadedFractions(FileState.Downloading(progress = 0f)))
    }

    @Test
    fun usekNepresahneKonecSouboru() {
        val state = FileState.Downloading(progress = 1f, downloadOffset = 800, downloadedPrefixSize = 500, totalSize = 1000)
        assertEquals(0.8f..1f, downloadedFractions(state))
    }
}
