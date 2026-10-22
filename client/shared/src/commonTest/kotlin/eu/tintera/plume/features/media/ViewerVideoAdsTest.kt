package eu.tintera.plume.features.media

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import eu.tintera.plume.core.telegram.VideoAd
import eu.tintera.plume.core.telegram.VideoAds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reklamy nad videem v prohlížeči: dotaz, čas jen při přehrávání, hlášení zhlédnutí a kliknutí. */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewerVideoAdsTest {

    private class Repository : UnimplementedConversationRepository() {
        val asked = mutableListOf<Long>()
        val views = mutableListOf<Long>()
        val clicks = mutableListOf<Long>()
        var ads: VideoAds? = VideoAds(listOf(ad(1), ad(2)), startDelaySeconds = 5, betweenDelaySeconds = 10)

        override suspend fun videoAds(chatId: Long, messageId: Long): Result<VideoAds?> {
            asked += messageId
            return Result.success(ads)
        }

        override suspend fun reportVideoAdView(uniqueId: Long): Result<Unit> {
            views += uniqueId
            return Result.success(Unit)
        }

        override suspend fun reportVideoAdClick(uniqueId: Long): Result<Unit> {
            clicks += uniqueId
            return Result.success(Unit)
        }
    }

    private fun TestScope.ads(repository: Repository, enabled: Boolean = true): Pair<ViewerVideoAds, CoroutineScope> {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        return ViewerVideoAds(1, enabled, scope, scope, repository, Logger.withTag("test")) to scope
    }

    @Test
    fun reklamaPoStartDelayPrehravaniZhlednutiJednou() = runTest {
        val repository = Repository()
        val (ads, scope) = ads(repository)
        ads.setPlaying(10)
        advanceTimeBy(4_900)
        runCurrent()
        assertNull(ads.shown.value)
        advanceTimeBy(200)
        runCurrent()
        assertEquals(1L, ads.shown.value?.ad?.uniqueId)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(listOf(1L), repository.views)
        scope.cancel()
    }

    @Test
    fun pozastaveniCasZastavi() = runTest {
        val repository = Repository()
        val (ads, scope) = ads(repository)
        ads.setPlaying(10)
        advanceTimeBy(3_000)
        ads.setPlaying(null)
        advanceTimeBy(60_000)
        runCurrent()
        assertNull(ads.shown.value)
        ads.setPlaying(10)
        // Pozastavení zahodí rozběhnutý krok časovače (nejvýš čtvrt vteřiny), proto víc než 2 s.
        advanceTimeBy(2_400)
        runCurrent()
        assertEquals(1L, ads.shown.value?.ad?.uniqueId)
        scope.cancel()
    }

    /** Další video téhož prohlížeče se na reklamy neptá znovu a plán nezačíná od nuly. */
    @Test
    fun dalsiVideoSeNeptaZnovu() = runTest {
        val repository = Repository()
        val (ads, scope) = ads(repository)
        ads.setPlaying(10)
        advanceTimeBy(3_000)
        ads.setPlaying(11)
        advanceTimeBy(2_100)
        runCurrent()
        assertEquals(listOf(10L), repository.asked)
        assertEquals(1L, ads.shown.value?.ad?.uniqueId)
        scope.cancel()
    }

    /** Video bez reklam (nebo bez práva na ně) — zkusí se další video, ale každé jen jednou. */
    @Test
    fun bezReklamZkusiDalsiVideoJednou() = runTest {
        val repository = Repository().apply { ads = null }
        val (ads, scope) = ads(repository)
        ads.setPlaying(10)
        runCurrent()
        ads.setPlaying(null)
        ads.setPlaying(10)
        runCurrent()
        ads.setPlaying(11)
        runCurrent()
        assertEquals(listOf(10L, 11L), repository.asked)
        scope.cancel()
    }

    @Test
    fun mimoMediaChatuSeNepta() = runTest {
        val repository = Repository()
        val (ads, scope) = ads(repository, enabled = false)
        ads.setPlaying(10)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(emptyList(), repository.asked)
        scope.cancel()
    }

    @Test
    fun tuknutiNahlasiKliknutiAVratiOdkaz() = runTest {
        val repository = Repository()
        val (ads, scope) = ads(repository)
        ads.setPlaying(10)
        advanceTimeBy(5_100)
        runCurrent()
        assertEquals("https://t.me/ad1", ads.open())
        runCurrent()
        assertEquals(listOf(1L), repository.clicks)
        scope.cancel()
    }

    /** Otevřená nabídka reklamu drží — čas stojí, i když video hraje. */
    @Test
    fun otevrenaNabidkaReklamuDrzi() = runTest {
        val repository = Repository()
        val (ads, scope) = ads(repository)
        ads.setPlaying(10)
        advanceTimeBy(5_100)
        runCurrent()
        ads.setHeld(true)
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(1L, ads.shown.value?.ad?.uniqueId)
        ads.setHeld(false)
        advanceTimeBy(10_000)
        runCurrent()
        assertNull(ads.shown.value)
        scope.cancel()
    }

    @Test
    fun zavritAzPoMinimu() = runTest {
        val repository = Repository()
        val (ads, scope) = ads(repository)
        ads.setPlaying(10)
        advanceTimeBy(5_100)
        runCurrent()
        ads.close()
        assertEquals(1L, ads.shown.value?.ad?.uniqueId)
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(ads.shown.value?.canClose == true)
        ads.close()
        assertNull(ads.shown.value)
        scope.cancel()
    }

    private companion object {
        fun ad(id: Long) = VideoAd(
            uniqueId = id,
            title = "Reklama $id",
            text = "Text",
            minDisplaySeconds = 5,
            maxDisplaySeconds = 10,
            canBeReported = true,
            url = "https://t.me/ad$id",
            photo = null,
            sponsorInfo = "",
            additionalInfo = "",
        )
    }
}
