package eu.tintera.plume.features.media

import eu.tintera.plume.core.files.savedFileName
import eu.tintera.plume.core.telegram.MessageMedia
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Pod jakým jménem prohlížeč médium uloží a sdílí — video si nese jméno od odesílatele. */
class OriginalFileNameTest {

    private val prague = TimeZone.of("Europe/Prague")

    // 18. 9. 2026 23:30:17 v Praze.
    private val timestamp = 1_789_767_017

    private fun video(fileName: String?) = MessageMedia.Video(
        fileId = 1,
        thumbnailFileId = null,
        width = 1920,
        height = 1080,
        durationSeconds = 10,
        fileName = fileName,
    )

    private fun document(fileName: String, mimeType: String) = MessageMedia.Document(
        fileId = 2,
        fileName = fileName,
        mimeType = mimeType,
        sizeBytes = 1_000,
        thumbnailFileId = null,
    )

    @Test
    fun videoKeepsSendersName() {
        val media = video("PXL_20261125_101500.mp4")
        assertEquals("PXL_20261125_101500.mp4", media.originalFileName)
        assertEquals(
            "PXL_20261125_101500.mp4",
            savedFileName(media.originalFileName, "/tdlib/videos/5868_121.mp4", timestamp, prague),
        )
    }

    @Test
    fun videoWithoutNameGetsTimestamp() {
        val media = video(null)
        assertNull(media.originalFileName)
        assertEquals(
            "Plume_20260918_233017.mp4",
            savedFileName(media.originalFileName, "/tdlib/videos/5868_121.mp4", timestamp, prague),
        )
    }

    @Test
    fun photoNeverHasName() {
        val photo = MessageMedia.Photo(fileId = 3, thumbnailFileId = null, fullFileId = 3, width = 10, height = 10)
        assertNull(photo.originalFileName)
    }

    @Test
    fun videoSentAsFileKeepsNameInViewer() {
        val media = document("dovolena.mp4", "video/mp4").asViewerMedia()
        assertEquals("dovolena.mp4", media?.originalFileName)
    }
}
