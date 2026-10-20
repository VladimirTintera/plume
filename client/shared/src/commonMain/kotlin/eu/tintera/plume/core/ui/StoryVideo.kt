package eu.tintera.plume.core.ui

/**
 * Video pro příběh: [STORY_VIDEO_WIDTH] × [STORY_VIDEO_HEIGHT], nejvýš [STORY_VIDEO_MAX_SECONDS] s.
 *
 * **TDLib chce víc než rozměr** (`InputStoryContentVideo`): MP4 s H.265 a klíčovým snímkem
 * každou sekundu. Video z galerie tomu nevyhoví skoro nikdy, takže se převádí vždycky.
 * Desktop kodér nemá ([supported] = `false`).
 */
interface StoryVideo {
    val supported: Boolean

    /** Délka zdroje v milisekundách. */
    suspend fun durationMs(path: String): Long

    /** První snímek jako JPEG pro náhled na obrazovce nového příběhu. */
    suspend fun preview(path: String): String

    /**
     * Převede video na plátno 9 : 16 ([fill] = vyplnit s ořezem krajů, jinak celé na černé),
     * vezme nejvýš [STORY_VIDEO_MAX_SECONDS] s od začátku a vrátí cestu k MP4.
     */
    suspend fun render(path: String, fill: Boolean): RenderedStoryVideo
}

data class RenderedStoryVideo(
    val path: String,
    val durationSeconds: Double,
    /** Video bez zvuku Telegram vede jako animaci (`isAnimation`). */
    val hasAudio: Boolean,
)

/** Desktop video nepřevede — příběh tam jde jen z fotky. */
object UnsupportedStoryVideo : StoryVideo {
    override val supported: Boolean = false
    override suspend fun durationMs(path: String): Long = 0
    override suspend fun preview(path: String): String = error("Video příběh tady nejde.")
    override suspend fun render(path: String, fill: Boolean): RenderedStoryVideo = error("Video příběh tady nejde.")
}

const val STORY_VIDEO_WIDTH = 720
const val STORY_VIDEO_HEIGHT = 1280
const val STORY_VIDEO_MAX_SECONDS = 60

/**
 * 2,5 Mbit/s: minuta je pak kolem 19 MB. H.265 na 720p vypadá s tímhle tokem čistě a nahrávání
 * na mobilních datech netrvá minuty.
 */
const val STORY_VIDEO_BITRATE = 2_500_000
