package eu.tintera.plume.core.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StoryboardTest {

    private val map = """
        file=mtproto:5432123456789
        frame_width=160
        frame_height=90
        0,0,0
        10.5,160,0
        21,320,0
        broken,line
        31.25,0,90
    """.trimIndent()

    @Test
    fun parsesFramesAndSkipsHeaderAndBrokenLines() {
        assertEquals(
            listOf(
                StoryboardFrame(0, 0, 0),
                StoryboardFrame(10_500, 160, 0),
                StoryboardFrame(21_000, 320, 0),
                StoryboardFrame(31_250, 0, 90),
            ),
            parseStoryboardMap(map),
        )
    }

    @Test
    fun frameIsTheLastOneStartedAtTheTime() {
        val frames = parseStoryboardMap(map)
        assertEquals(StoryboardFrame(0, 0, 0), frames.frameAt(10_499))
        assertEquals(StoryboardFrame(10_500, 160, 0), frames.frameAt(10_500))
        assertEquals(StoryboardFrame(31_250, 0, 90), frames.frameAt(3_600_000))
    }

    @Test
    fun beforeTheFirstFrameIsTheFirstOne() {
        val frames = listOf(StoryboardFrame(1_000, 0, 0), StoryboardFrame(2_000, 160, 0))
        assertEquals(frames.first(), frames.frameAt(0))
        assertNull(emptyList<StoryboardFrame>().frameAt(0))
    }
}
