package eu.tintera.plume.core.settings

import eu.tintera.plume.core.connectivity.NetworkKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoDownloadTest {

    private val settings = AutoDownloadSettings(
        wifi = AutoDownloadLevel.High,
        mobile = AutoDownloadLevel.Medium,
        roaming = AutoDownloadLevel.Off,
    )

    @Test
    fun urovenSeBerePodleSite() {
        assertEquals(AutoDownloadLevel.High, autoDownloadLevel(settings, NetworkKind.WiFi, saveData = false))
        assertEquals(AutoDownloadLevel.Medium, autoDownloadLevel(settings, NetworkKind.Mobile, saveData = false))
        assertEquals(AutoDownloadLevel.Off, autoDownloadLevel(settings, NetworkKind.MobileRoaming, saveData = false))
    }

    /** Ethernet a VPN nejsou mobilní data; bez sítě platí opatrnější úroveň mobilních dat. */
    @Test
    fun ostatniSiteJakoWifiBezSiteJakoMobil() {
        assertEquals(AutoDownloadLevel.High, autoDownloadLevel(settings, NetworkKind.Other, saveData = false))
        assertEquals(AutoDownloadLevel.Medium, autoDownloadLevel(settings, NetworkKind.None, saveData = false))
    }

    @Test
    fun sporicDatSraziMobilniDataNaUsporne() {
        assertEquals(AutoDownloadLevel.Low, autoDownloadLevel(settings, NetworkKind.Mobile, saveData = true))
        // Níž, než si uživatel vybral, nejde — vypnuté zůstane vypnuté.
        assertEquals(AutoDownloadLevel.Off, autoDownloadLevel(settings, NetworkKind.MobileRoaming, saveData = true))
    }

    /** Spořič dat Android na neměřené síti nevynucuje, takže Wi-Fi nechává být. */
    @Test
    fun sporicDatWifiNechava() {
        assertEquals(AutoDownloadLevel.High, autoDownloadLevel(settings, NetworkKind.WiFi, saveData = true))
    }

    @Test
    fun limityRostouSUrovni() {
        val levels = AutoDownloadLevel.entries.map { it.limits }
        assertFalse(levels.first().photos)
        assertTrue(levels.drop(1).all { it.photos })
        assertEquals(levels.map { it.maxVideoBytes }.sorted(), levels.map { it.maxVideoBytes })
        assertEquals(levels.map { it.maxFileBytes }.sorted(), levels.map { it.maxFileBytes })
        assertEquals(0L, AutoDownloadLevel.Off.limits.maxVideoBytes)
        assertEquals(levels.map { it.maxVideoLines }.sorted(), levels.map { it.maxVideoLines })
        assertEquals(Int.MAX_VALUE, AutoDownloadLevel.High.limits.maxVideoLines)
    }

    /** „Běžně" je to, co aplikace dělala na všech sítích, než šlo vybírat. */
    @Test
    fun bezneJeDosavadniChovani() {
        assertEquals(
            AutoDownloadLimits(photos = true, maxVideoBytes = 5L * 1024 * 1024, maxFileBytes = 5L * 1024 * 1024, maxVideoLines = 720),
            AutoDownloadLevel.Medium.limits,
        )
    }
}
