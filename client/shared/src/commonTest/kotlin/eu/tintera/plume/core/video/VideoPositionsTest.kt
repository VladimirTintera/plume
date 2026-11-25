package eu.tintera.plume.core.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VideoPositionsTest {

    private val hour = 60 * 60 * 1000L

    @Test
    fun shortVideoIsNotRemembered() {
        assertNull(resumePosition(positionMs = 60_000, durationMs = MIN_RESUME_DURATION_MS - 1))
    }

    @Test
    fun beginningAndEndAreNotRemembered() {
        assertNull(resumePosition(positionMs = MIN_RESUME_POSITION_MS - 1, durationMs = hour))
        assertNull(resumePosition(positionMs = hour - END_MARGIN_MS + 1, durationMs = hour))
        assertEquals(hour / 2, resumePosition(positionMs = hour / 2, durationMs = hour))
    }

    @Test
    fun newestGoesFirstAndReplacesTheSameKey() {
        val list = listOf(VideoPosition("a", 20_000, hour), VideoPosition("b", 30_000, hour))
            .withPosition("b", 40_000, hour)
        assertEquals(listOf("b", "a"), list.map { it.key })
        assertEquals(40_000, list.first().positionMs)
    }

    @Test
    fun finishedVideoIsForgotten() {
        val list = listOf(VideoPosition("a", 20_000, hour)).withPosition("a", hour, hour)
        assertEquals(emptyList(), list)
    }

    @Test
    fun oldestFallOffTheEnd() {
        val full = (0 until MAX_VIDEO_POSITIONS).map { VideoPosition("k$it", 20_000, hour) }
        val list = full.withPosition("new", 20_000, hour)
        assertEquals(MAX_VIDEO_POSITIONS, list.size)
        assertEquals("new", list.first().key)
        assertEquals("k${MAX_VIDEO_POSITIONS - 2}", list.last().key)
    }

    @Test
    fun sourceIsKeptWhenTheSameFilePlaysWithoutOne() {
        val source = VideoSource(accountId = 1, chatId = 2, messageId = 3)
        val list = listOf(VideoPosition("a", 20_000, hour, source)).withPosition("a", 40_000, hour)
        assertEquals(source, list.single().source)
        val moved = list.withPosition("a", 50_000, hour, source = VideoSource(1, 2, 9))
        assertEquals(9, moved.single().source?.messageId)
    }

    @Test
    fun restoredPositionReturnsToItsPlace() {
        val list = listOf(VideoPosition("a", 20_000, hour), VideoPosition("c", 30_000, hour))
        val restored = list.withRestored(RemovedPosition(VideoPosition("b", 25_000, hour), index = 1))
        assertEquals(listOf("a", "b", "c"), restored.map { it.key })
    }

    @Test
    fun newerPositionWinsOverRestore() {
        val list = listOf(VideoPosition("b", 90_000, hour))
        val restored = list.withRestored(RemovedPosition(VideoPosition("b", 25_000, hour), index = 0))
        assertEquals(90_000, restored.single().positionMs)
    }

    @Test
    fun oldEntriesWithoutSourceStillDecode() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString<List<VideoPosition>>("""[{"key":"a","positionMs":1,"durationMs":2}]""")
        assertNull(decoded.single().source)
    }
}
