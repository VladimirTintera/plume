package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.audio.RecordedVoice
import eu.tintera.plume.core.audio.VoiceKey
import eu.tintera.plume.core.audio.encodeWaveform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoicePreviewTest {

    /** Po dohrání náhledu se nesmí řetězit „další neposlechnutá po zprávě −1". */
    @Test
    fun nahledNeniZprava() {
        assertTrue(voicePreviewKey(AccountSlot.Primary, chatId = 7).isVoicePreview)
        assertFalse(VoiceKey(AccountSlot.Primary, chatId = 7, messageId = 42).isVoicePreview)
    }

    @Test
    fun dokoncovanaNahravkaNemaWaveform() {
        assertEquals(emptyList(), VoicePreview(voice = null).bars)
    }

    @Test
    fun waveformMaPevnyPocetSloupcu() {
        val voice = RecordedVoice(path = "x.ogg", durationSeconds = 3, waveform = encodeWaveform(List(60) { it / 60f }))
        assertEquals(VOICE_PREVIEW_BARS, VoicePreview(voice).bars.size)
    }
}
