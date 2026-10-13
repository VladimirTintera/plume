package eu.tintera.plume.features.settings

import eu.tintera.plume.core.telegram.NetworkUsage
import eu.tintera.plume.core.telegram.StorageCategory
import eu.tintera.plume.core.telegram.StorageRepository
import eu.tintera.plume.core.telegram.StorageUsage
import eu.tintera.plume.core.telegram.TrafficKind
import eu.tintera.plume.core.telegram.TrafficRecord
import eu.tintera.plume.core.telegram.UsageNetwork
import eu.tintera.plume.core.telegram.networkUsageOf
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.network_usage_reset_failed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Obrazovka přenesených dat: která síť se ukazuje a vynulování. */
class NetworkUsageViewModelTest {

    private val main = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val usage = networkUsageOf(
        sinceDate = 100,
        records = listOf(
            TrafficRecord(UsageNetwork.Mobile, TrafficKind.Photos, 1, 2),
            TrafficRecord(UsageNetwork.WiFi, TrafficKind.Videos, 3, 4),
        ),
    )

    @Test
    fun ukazujeVybranouSitAJinakTuSNejviceDaty() = runTest {
        val viewModel = NetworkUsageViewModel(UsageStorage(usage))
        advanceUntilIdle()
        assertEquals(UsageNetwork.WiFi, viewModel.state.value.shown)

        viewModel.select(UsageNetwork.Mobile)
        assertEquals(UsageNetwork.Mobile, viewModel.state.value.shown)

        // Roaming nemá data — ukáže se síť s nejvíc daty, ne prázdno.
        viewModel.select(UsageNetwork.Roaming)
        assertEquals(UsageNetwork.WiFi, viewModel.state.value.shown)
    }

    @Test
    fun poVynulovaniSeStatistikaCteZnovu() = runTest {
        val storage = UsageStorage(usage)
        val viewModel = NetworkUsageViewModel(storage)
        advanceUntilIdle()

        viewModel.requestReset()
        assertEquals(true, viewModel.state.value.confirmingReset)
        viewModel.confirmReset()
        advanceUntilIdle()

        assertEquals(1, storage.resets)
        assertEquals(2, storage.reads)
        assertEquals(networkUsageOf(500, emptyList()), viewModel.state.value.usage)
    }

    @Test
    fun nevynulovanaStatistikaZustaneSHlaskou() = runTest {
        val storage = UsageStorage(usage, resetResult = Result.failure(IllegalStateException("x")))
        val viewModel = NetworkUsageViewModel(storage)
        advanceUntilIdle()

        viewModel.confirmReset()
        advanceUntilIdle()

        assertEquals(usage, viewModel.state.value.usage)
        assertEquals(UiText(Res.string.network_usage_reset_failed), viewModel.state.value.error)
    }
}

private class UsageStorage(
    private val usage: NetworkUsage,
    private val resetResult: Result<Unit> = Result.success(Unit),
) : StorageRepository {
    var reads = 0
    var resets = 0

    override suspend fun networkUsage(): Result<NetworkUsage> {
        reads++
        return Result.success(if (resets > 0) networkUsageOf(500, emptyList()) else usage)
    }

    override suspend fun resetNetworkUsage(): Result<Unit> {
        if (resetResult.isSuccess) resets++
        return resetResult
    }

    override suspend fun databaseBytes(): Result<Long> = Result.success(0)
    override suspend fun usage(): Result<StorageUsage> = Result.failure(UnsupportedOperationException())
    override suspend fun clear(categories: Set<StorageCategory>, chatId: Long?): Result<Long> = Result.success(0)
    override suspend fun clearOlderThan(olderThanSeconds: Int): Result<Long> = Result.success(0)
}
