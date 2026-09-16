package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.OutgoingAttachment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StagedAttachmentsTest {

    private fun photo(n: Int) = OutgoingAttachment.Photo(path = "/p$n.jpg", width = 1, height = 1)
    private fun document(n: Int) = OutgoingAttachment.Document(path = "/d$n.pdf")

    @Test
    fun sameKindAppends() {
        val staged = stageAttachments(listOf(photo(1)), listOf(photo(2), photo(3)))
        assertEquals(listOf(photo(1), photo(2), photo(3)), staged.attachments)
        assertFalse(staged.overflow)
    }

    private fun video(n: Int) = OutgoingAttachment.Video(
        path = "/v$n.mp4", width = 1, height = 1, durationSeconds = 1, thumbnail = null, streamable = false,
    )

    @Test
    fun photosAndVideosMix() {
        val staged = stageAttachments(listOf(photo(1)), listOf(video(1)))
        assertEquals(listOf(photo(1), video(1)), staged.attachments)
    }

    @Test
    fun documentReplacesVideos() {
        val staged = stageAttachments(listOf(video(1)), listOf(document(1)))
        assertEquals(listOf(document(1)), staged.attachments)
    }

    @Test
    fun otherKindReplaces() {
        val staged = stageAttachments(listOf(photo(1), photo(2)), listOf(document(1)))
        assertEquals(listOf(document(1)), staged.attachments)
        assertFalse(staged.overflow)
    }

    @Test
    fun overLimitIsTrimmedAndReported() {
        val current = (1..8).map(::photo)
        val staged = stageAttachments(current, (9..12).map(::photo))
        assertEquals((1..MAX_ATTACHMENTS).map(::photo), staged.attachments)
        assertTrue(staged.overflow)
    }

    @Test
    fun mixedPickKeepsOnlyFirstKind() {
        val staged = stageAttachments(emptyList(), listOf(document(1), photo(1), document(2)))
        assertEquals(listOf(document(1), document(2)), staged.attachments)
    }

    @Test
    fun emptyPickKeepsCurrent() {
        val staged = stageAttachments(listOf(photo(1)), emptyList())
        assertEquals(listOf(photo(1)), staged.attachments)
    }
}

class FormatDurationTest {
    @Test
    fun formats() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:42", formatDuration(42))
        assertEquals("12:05", formatDuration(725))
        assertEquals("1:02:03", formatDuration(3723))
    }
}
