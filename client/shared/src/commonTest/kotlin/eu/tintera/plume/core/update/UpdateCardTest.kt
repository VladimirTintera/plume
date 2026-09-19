package eu.tintera.plume.core.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UpdateCardTest {

    @Test
    fun bezAktualizaceNicNeni() {
        assertNull(updateCard(AppUpdateState.None, dismissedVersionCode = null))
    }

    @Test
    fun nabidkaSeUkaze() {
        val state = AppUpdateState.Available(versionCode = 42)
        assertEquals(state, updateCard(state, dismissedVersionCode = null))
    }

    @Test
    fun zavrenaVerzeSeZnovuNenabizi() {
        assertNull(updateCard(AppUpdateState.Available(versionCode = 42), dismissedVersionCode = 42))
    }

    @Test
    fun dalsiVerzePoZavreneSeNabidne() {
        val state = AppUpdateState.Available(versionCode = 43)
        assertEquals(state, updateCard(state, dismissedVersionCode = 42))
    }

    /** Stahování a staženou aktualizaci si uživatel vyžádal sám — zavření nabídky je neschová. */
    @Test
    fun stahovaniAStazenaSeUkazujiVzdycky() {
        val downloading = AppUpdateState.Downloading(versionCode = 42, progress = 0.5f)
        val downloaded = AppUpdateState.Downloaded(versionCode = 42)
        assertEquals(downloading, updateCard(downloading, dismissedVersionCode = 42))
        assertEquals(downloaded, updateCard(downloaded, dismissedVersionCode = 42))
    }
}
