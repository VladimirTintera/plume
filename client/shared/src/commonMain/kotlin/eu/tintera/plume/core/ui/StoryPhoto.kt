package eu.tintera.plume.core.ui

/**
 * Fotka pro příběh: plátno přesně [STORY_PHOTO_WIDTH] × [STORY_PHOTO_HEIGHT] jako JPEG.
 *
 * **Rozměr není doporučení, TDLib ho vyžaduje** (`InputStoryContentPhoto`: „The photo size must
 * be 1080x1920"). Vstupem je fotka už připravená výběrem příloh (otočená podle EXIF, JPEG),
 * výstupem nový soubor v cache.
 */
interface StoryPhoto {
    /** [fill] = fotka plátno vyplní a přečnívající kraje se oříznou; jinak se vejde celá na černé. */
    suspend fun render(path: String, fill: Boolean): String
}

/** Kam na plátno příběhu fotku [width] × [height] nakreslit (může přečnívat ven). */
data class StoryPhotoPlacement(val left: Float, val top: Float, val width: Float, val height: Float)

fun storyPhotoPlacement(width: Int, height: Int, fill: Boolean): StoryPhotoPlacement {
    val w = width.coerceAtLeast(1).toFloat()
    val h = height.coerceAtLeast(1).toFloat()
    val scaleX = STORY_PHOTO_WIDTH / w
    val scaleY = STORY_PHOTO_HEIGHT / h
    val scale = if (fill) maxOf(scaleX, scaleY) else minOf(scaleX, scaleY)
    val drawnWidth = w * scale
    val drawnHeight = h * scale
    return StoryPhotoPlacement(
        left = (STORY_PHOTO_WIDTH - drawnWidth) / 2,
        top = (STORY_PHOTO_HEIGHT - drawnHeight) / 2,
        width = drawnWidth,
        height = drawnHeight,
    )
}

const val STORY_PHOTO_WIDTH = 1080
const val STORY_PHOTO_HEIGHT = 1920
