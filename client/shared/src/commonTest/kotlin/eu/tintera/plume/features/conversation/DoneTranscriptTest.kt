package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.SpeechTranscript
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Hotový přepis záznamu: ze serveru ve zprávě, jinak z telefonu ([doneTranscript]). */
class DoneTranscriptTest {

    private fun voice(transcript: SpeechTranscript? = null) = ChatMessage(
        id = 1L,
        isOutgoing = false,
        senderName = "Jana",
        senderAvatarFileId = null,
        senderId = 1L,
        text = "",
        entities = emptyList(),
        media = MessageMedia.VoiceNote(fileId = 1, durationSeconds = 90, waveform = emptyList(), isListened = true, transcript = transcript),
        linkPreview = null,
        reactions = emptyList(),
        sendState = null,
        replyToMessageId = null,
        isService = false,
        timestamp = 0,
        isEdited = false,
    )

    @Test
    fun serverMaPrednost() {
        assertEquals("ze serveru", voice(SpeechTranscript.Done("ze serveru")).doneTranscript(SpeechTranscript.Done("z telefonu")))
    }

    @Test
    fun bezServeruZTelefonu() {
        assertEquals("z telefonu", voice().doneTranscript(SpeechTranscript.Done("z telefonu")))
        assertEquals("z telefonu", voice(SpeechTranscript.Pending("ze ser")).doneTranscript(SpeechTranscript.Done("z telefonu")))
    }

    /** Rozpracovaný, prázdný nebo žádný přepis = není co shrnout; text zprávy přepis není. */
    @Test
    fun nehotovyNeboJinaZpravaNic() {
        assertNull(voice().doneTranscript(SpeechTranscript.Pending("ta")))
        assertNull(voice().doneTranscript(SpeechTranscript.Done(" ")))
        assertNull(voice().copy(media = null, text = "dlouhý text").doneTranscript(SpeechTranscript.Done("z telefonu")))
    }
}
