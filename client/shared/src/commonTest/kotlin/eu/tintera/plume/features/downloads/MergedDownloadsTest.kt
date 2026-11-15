package eu.tintera.plume.features.downloads

import eu.tintera.plume.core.telegram.testDownload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MergedDownloadsTest {

    private fun MergedDownloads.active() = active.map { it.source to it.item.fileId }
    private fun MergedDownloads.completed() = completed.map { it.source to it.item.fileId }

    @Test
    fun dokudSeNenacteKazdyUcetNeniCoUkazat() {
        assertNull(mergeDownloads(listOf(DownloadsState(items = emptyList()), DownloadsState())))
    }

    @Test
    fun jedenUcetZustavaVPoradiOdTdlib() {
        val items = listOf(testDownload(1).copy(addDate = 10), testDownload(2).copy(addDate = 20))
        assertEquals(listOf(0 to 1, 0 to 2), mergeDownloads(listOf(DownloadsState(items = items)))!!.active())
    }

    @Test
    fun vicUctuSeRadiPodleData() {
        val merged = mergeDownloads(
            listOf(
                DownloadsState(items = listOf(testDownload(1).copy(addDate = 10), testDownload(2, completed = true).copy(completeDate = 300))),
                // Totéž id souboru v druhém účtu: jiný soubor, jiný klíč.
                DownloadsState(items = listOf(testDownload(1).copy(addDate = 20), testDownload(3, completed = true).copy(completeDate = 400))),
            ),
        )!!
        assertEquals(listOf(1 to 1, 0 to 1), merged.active())
        assertEquals(listOf(1 to 3, 0 to 2), merged.completed())
        assertEquals(2, merged.active.map { it.key }.distinct().size)
    }
}
