package eu.tintera.plume.features.gallery

import eu.tintera.plume.core.telegram.GalleryItem
import eu.tintera.plume.core.telegram.GalleryPage
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import eu.tintera.plume.core.telegram.testMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun item(chat: Long, id: Long, timestamp: Int = id.toInt()) = GalleryItem(chat, testMessage(id).copy(timestamp = timestamp))

    /** Stránky podle offsetu; `fail` = offsety, které selžou (jednou). */
    private class FakeRepository(private val pages: Map<String, GalleryPage>, val fail: MutableSet<String> = mutableSetOf()) :
        UnimplementedConversationRepository() {
        val asked = mutableListOf<String>()

        override suspend fun galleryMedia(offset: String): Result<GalleryPage> {
            asked += offset
            if (fail.remove(offset)) return Result.failure(IllegalStateException("network"))
            return Result.success(pages.getValue(offset))
        }
    }

    @Test
    fun strankyNavazujiAzDoKonceBezDuplicit() = runTest(dispatcher) {
        val repository = FakeRepository(
            mapOf(
                "" to GalleryPage(listOf(item(1, 10), item(2, 10)), nextOffset = "a"),
                // Táž zpráva na hranici stránek se neopakuje; stejné id v jiném chatu je jiná fotka.
                "a" to GalleryPage(listOf(item(2, 10), item(1, 9)), nextOffset = null),
            ),
        )
        val model = GalleryViewModel(repository)
        advanceUntilIdle()
        assertEquals(listOf(1L to 10L, 2L to 10L), model.state.value.items.map { it.chatId to it.message.id })

        model.loadMore()
        advanceUntilIdle()
        val state = model.state.value
        assertEquals(listOf(1L to 10L, 2L to 10L, 1L to 9L), state.items.map { it.chatId to it.message.id })
        assertTrue(state.reachedEnd)
        assertFalse(state.loading)

        model.loadMore()
        advanceUntilIdle()
        assertEquals(listOf("", "a"), repository.asked)
    }

    @Test
    fun prazdnaStrankaPoFiltruRovnouDotahneDalsi() = runTest(dispatcher) {
        val repository = FakeRepository(
            mapOf(
                "" to GalleryPage(emptyList(), nextOffset = "a"),
                "a" to GalleryPage(listOf(item(1, 5)), nextOffset = "b"),
            ),
        )
        val model = GalleryViewModel(repository)
        advanceUntilIdle()

        assertEquals(listOf("", "a"), repository.asked)
        assertEquals(1, model.state.value.items.size)
        assertFalse(model.state.value.reachedEnd)
    }

    @Test
    fun zamceneChatyGalerieNeukazuje() = runTest(dispatcher) {
        val locked = MutableStateFlow<Set<Long>?>(null)
        val model = GalleryViewModel(
            FakeRepository(mapOf("" to GalleryPage(listOf(item(1, 3), item(2, 2)), nextOffset = null))),
            lockedChats = locked,
        )
        advanceUntilIdle()
        // Dokud se zamčené čtou, nic: zamčená fotka nesmí probleskout.
        assertTrue(model.state.value.items.isEmpty())

        locked.value = setOf(2L)
        advanceUntilIdle()
        assertEquals(listOf(1L), model.state.value.items.map { it.chatId })
    }

    @Test
    fun chybaPrvniStrankyAZkusitZnovu() = runTest(dispatcher) {
        val repository = FakeRepository(mapOf("" to GalleryPage(listOf(item(1, 1)), nextOffset = null)), fail = mutableSetOf(""))
        val model = GalleryViewModel(repository)
        advanceUntilIdle()
        assertNotNull(model.state.value.failure)

        model.retry()
        advanceUntilIdle()
        assertNull(model.state.value.failure)
        assertEquals(1, model.state.value.items.size)
    }

    @Test
    fun chybaDalsiStrankyNechaFotkyANabidneZnovu() = runTest(dispatcher) {
        val repository = FakeRepository(
            mapOf("" to GalleryPage(listOf(item(1, 2)), nextOffset = "a"), "a" to GalleryPage(listOf(item(1, 1)), nextOffset = null)),
            fail = mutableSetOf("a"),
        )
        val model = GalleryViewModel(repository)
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()
        assertTrue(model.state.value.moreFailed)
        assertEquals(1, model.state.value.items.size)

        model.loadMore()
        advanceUntilIdle()
        assertEquals(2, model.state.value.items.size)
        assertFalse(model.state.value.moreFailed)
    }

    @Test
    fun poMesicichOdNejnovejsiho() {
        fun at(month: Int, day: Int) = LocalDateTime(2026, month, day, 12, 0).toInstant(TimeZone.UTC).epochSeconds.toInt()
        val months = groupByMonth(listOf(item(1, 3, at(11, 20)), item(1, 2, at(11, 1)), item(2, 1, at(9, 5))), TimeZone.UTC)
        assertEquals(listOf(YearMonth(2026, 11) to 2, YearMonth(2026, 9) to 1), months.map { it.first to it.second.size })
    }
}
