package eu.tintera.plume.core.telegram

import eu.tintera.plume.core.accounts.AccountSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    /** Další účet má kanály se zvukem vlastní; primární beze změny, výchozí kanál společný. */
    @Test
    fun kanalSeZvukemNeseUcet() {
        val second = AccountSlot("fs2gc9")
        assertEquals("private_tone_5@fs2gc9", soundChannelKey("private", 5, second))
        assertEquals("groups_silent@fs2gc9", soundChannelKey("groups", NO_SOUND_ID, second))
        assertEquals("private", soundChannelKey("private", DEFAULT_SOUND_ID, second))
        assertEquals("private_tone_5", soundChannelKey("private", 5, AccountSlot.Primary))
    }

    @Test
    fun ucetSePoznaZId() {
        val second = AccountSlot("fs2gc9")
        assertEquals(AccountSlot.Primary, soundChannelSlot("private_tone_5"))
        assertEquals(AccountSlot.Primary, soundChannelSlot("groups_silent"))
        assertEquals(second, soundChannelSlot("private_tone_5@fs2gc9"))
        assertEquals(second, soundChannelSlot("groups_silent@fs2gc9"))
        // Kanál konverzace pod kanálem se zvukem patří témuž účtu.
        assertEquals(second, soundChannelSlot("private_silent@fs2gc9_chat_fs2gc9_42"))
        assertEquals(AccountSlot.Primary, soundChannelSlot("private_tone_5_chat_42"))
        assertNull(soundChannelSlot("private"))
        assertNull(soundChannelSlot("private_chat_42"))
        assertTrue(isSoundChannel("channels_tone_9@abc"))
    }

    @Test
    fun tonSePoznaZId() {
        assertEquals(5L, soundChannelTone("private_tone_5@fs2gc9"))
        assertEquals(5L, soundChannelTone("private_tone_5"))
        assertNull(soundChannelTone("private_silent"))
        assertNull(soundChannelTone("private"))
    }
}
