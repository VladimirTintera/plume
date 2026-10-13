package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Kanály se zvukem z Telegramu: výchozí zvuk nesmí měnit kanál, jiný zvuk = stabilní jiný kanál. */
class SoundChannelTest {

    @Test
    fun vychoziZvukZustavaVeVychozimKanalu() {
        assertEquals("private", soundChannelKey("private", DEFAULT_SOUND_ID))
        assertEquals("private", soundChannelKey("private", -42))
        assertEquals("groups_silent", soundChannelKey("groups", NO_SOUND_ID))
        assertEquals("mentions_tone_5021", soundChannelKey("mentions", 5021))
    }

    @Test
    fun kanalySeZvukemSePoznajiVychoziNe() {
        assertTrue(isSoundChannel("private_silent"))
        assertTrue(isSoundChannel("channels_tone_9"))
        assertFalse(isSoundChannel("private"))
        assertFalse(isSoundChannel("private_shortcut_42"))
        assertFalse(isSoundChannel("calls"))
    }
}
