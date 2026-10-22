package eu.tintera.plume.features.media

import eu.tintera.plume.core.telegram.VideoAd
import eu.tintera.plume.core.telegram.VideoAds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Časování reklam nad videem podle příkladu ze specifikace (start 5, mezi 10, min 5, max 10). */
class VideoAdScheduleTest {

    private fun schedule(count: Int = 2) = VideoAdSchedule(
        VideoAds(ads = (1..count).map { ad(it.toLong()) }, startDelaySeconds = 5, betweenDelaySeconds = 10),
    )

    @Test
    fun prvniReklamaAzPoStartDelayPrehravani() {
        val s = schedule()
        s.advance(4_999)
        assertNull(s.current)
        s.advance(1)
        assertEquals(1L, s.current?.uniqueId)
    }

    /** Čas se skládá jen z přehrávání — pozastavené video sem nic neposílá, tak se dá rozdělit libovolně. */
    @Test
    fun casSeScitaPoUsecich() {
        val s = schedule()
        repeat(20) { s.advance(250) }
        assertEquals(1L, s.current?.uniqueId)
    }

    @Test
    fun zavritJdeAzPoMinimu() {
        val s = schedule()
        s.advance(5_000)
        s.advance(4_000)
        assertFalse(s.canClose)
        assertFalse(s.close())
        assertEquals(1L, s.current?.uniqueId)
        s.advance(1_000)
        assertTrue(s.canClose)
        assertTrue(s.close())
        assertNull(s.current)
    }

    @Test
    fun poMaximuSeSamaSkryjeADalsiPrijdePoBetweenDelay() {
        val s = schedule()
        s.advance(5_000 + 10_000)
        assertNull(s.current)
        s.advance(9_999)
        assertNull(s.current)
        s.advance(1)
        assertEquals(2L, s.current?.uniqueId)
    }

    /** Zavřená reklama spustí mezeru hned, ne až po maximu. */
    @Test
    fun poZavreniBetweenDelayOdZavreni() {
        val s = schedule()
        s.advance(5_000 + 6_000)
        s.close()
        s.advance(10_000)
        assertEquals(2L, s.current?.uniqueId)
    }

    /** Jeden dlouhý úsek (třeba po probuzení) projde fázemi popořadě, nic nepřeskočí na konec. */
    @Test
    fun dlouhyUsekProjdeFazemi() {
        val s = schedule()
        s.advance(5_000 + 10_000 + 10_000 + 3_000)
        assertEquals(2L, s.current?.uniqueId)
        assertFalse(s.canClose)
    }

    @Test
    fun poPosledniUzNic() {
        val s = schedule(count = 1)
        s.advance(5_000 + 10_000)
        assertTrue(s.finished)
        s.advance(60_000)
        assertNull(s.current)
    }

    @Test
    fun nahlasenaZmiziHnedIPredMinimem() {
        val s = schedule()
        s.advance(5_000 + 1_000)
        s.drop(1)
        assertNull(s.current)
        s.advance(10_000)
        assertEquals(2L, s.current?.uniqueId)
    }

    private fun ad(id: Long) = VideoAd(
        uniqueId = id,
        title = "Reklama $id",
        text = "Text",
        minDisplaySeconds = 5,
        maxDisplaySeconds = 10,
        canBeReported = true,
        url = "https://t.me/example",
        photo = null,
        sponsorInfo = "",
        additionalInfo = "",
    )
}
