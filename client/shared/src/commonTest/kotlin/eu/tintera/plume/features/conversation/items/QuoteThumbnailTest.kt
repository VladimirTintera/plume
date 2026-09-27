package eu.tintera.plume.features.conversation.items

import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.Minithumbnail
import eu.tintera.plume.core.telegram.SelfDestructType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuoteThumbnailTest {

    private val mini = Minithumbnail(byteArrayOf(1, 2, 3), 40, 30)

    private fun photo(
        hasSpoiler: Boolean = false,
        isSensitive: Boolean = false,
        isSecret: Boolean = false,
        selfDestructType: SelfDestructType? = null,
        minithumbnail: Minithumbnail? = mini,
    ) = MessageMedia.Photo(
        fileId = 10,
        thumbnailFileId = 11,
        fullFileId = 12,
        width = 800,
        height = 600,
        hasSpoiler = hasSpoiler,
        isSensitive = isSensitive,
        isSecret = isSecret,
        selfDestructType = selfDestructType,
        minithumbnail = minithumbnail,
    )

    @Test
    fun fotkaBereNejmensiVariantu() {
        assertEquals(QuoteThumbnail(fileId = 11, placeholder = mini), photo().quoteThumbnail())
    }

    @Test
    fun spoilerJenRozmazaneANicSeNestahuje() {
        assertEquals(QuoteThumbnail(fileId = null, placeholder = mini, hidden = true), photo(hasSpoiler = true).quoteThumbnail())
        assertEquals(QuoteThumbnail(fileId = null, placeholder = mini, hidden = true), photo(isSensitive = true).quoteThumbnail())
    }

    @Test
    fun skrytaBezRozmazanehoNahleduNemaNic() {
        assertNull(photo(hasSpoiler = true, minithumbnail = null).quoteThumbnail())
    }

    @Test
    fun mizejiciATajneNahledNemaji() {
        assertNull(photo(selfDestructType = SelfDestructType.Timer(10)).quoteThumbnail())
        assertNull(photo(isSecret = true).quoteThumbnail())
    }

    @Test
    fun koleckoJeKulate() {
        val note = MessageMedia.VideoNote(fileId = 1, thumbnailFileId = 2, durationSeconds = 5, sizeBytes = 10, isViewed = false)
        assertEquals(QuoteThumbnail(fileId = 2, placeholder = null, round = true), note.quoteThumbnail())
    }

    @Test
    fun souborBezNahleduNicNeukaze() {
        val document = MessageMedia.Document(fileId = 1, fileName = "a.pdf", mimeType = "application/pdf", sizeBytes = 5, thumbnailFileId = null)
        assertNull(document.quoteThumbnail())
        assertEquals(QuoteThumbnail(fileId = 7, placeholder = null), document.copy(thumbnailFileId = 7).quoteThumbnail())
    }

    @Test
    fun hlasovkaATextNahledNemaji() {
        assertNull(MessageMedia.VoiceNote(fileId = 1, durationSeconds = 3, waveform = emptyList(), isListened = false).quoteThumbnail())
        assertNull(null.quoteThumbnail())
    }

    @Test
    fun citlivySouborASamolepkaNahledNemaji() {
        val document = MessageMedia.Document(
            fileId = 1, fileName = "a.jpg", mimeType = "image/jpeg", sizeBytes = 5, thumbnailFileId = 7, isSensitive = true,
        )
        assertNull(document.quoteThumbnail())
        val sticker = MessageMedia.Sticker(
            stickerFileId = 1, setId = 0, fileId = 2, animationFileId = null, thumbnailFileId = 3,
            width = 512, height = 512, emoji = "🙂", isSensitive = true,
        )
        assertNull(sticker.quoteThumbnail())
        assertEquals(QuoteThumbnail(fileId = 3, placeholder = null, transparent = true), sticker.copy(isSensitive = false).quoteThumbnail())
    }
}
