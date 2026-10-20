package eu.tintera.plume.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class StoryPhotoTest {

    @Test
    fun landscapeFillsByHeightAndOverflowsSideways() {
        val place = storyPhotoPlacement(width = 4000, height = 3000, fill = true)
        assertEquals(STORY_PHOTO_HEIGHT.toFloat(), place.height)
        assertEquals(2560f, place.width)
        assertEquals(-740f, place.left, "přečnívá stejně na obě strany")
        assertEquals(0f, place.top)
    }

    @Test
    fun landscapeFitsByWidthWithBarsAboveAndBelow() {
        val place = storyPhotoPlacement(width = 4000, height = 3000, fill = false)
        assertEquals(STORY_PHOTO_WIDTH.toFloat(), place.width, TOLERANCE)
        assertEquals(810f, place.height, TOLERANCE)
        assertEquals(555f, place.top, TOLERANCE)
    }

    @Test
    fun exactPortraitCoversTheCanvas() {
        val place = storyPhotoPlacement(width = 540, height = 960, fill = false)
        assertEquals(StoryPhotoPlacement(0f, 0f, 1080f, 1920f), place)
    }
}

private const val TOLERANCE = 0.01f
