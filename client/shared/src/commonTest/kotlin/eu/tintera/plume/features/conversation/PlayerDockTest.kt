package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.audio.VoiceKey
import eu.tintera.plume.core.audio.VoicePlayback
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerDockTest {

    private val chatId = 42L

    @Test
    fun hlasovkaTohotoChatuJdeDoDocku() {
        assertTrue(VoicePlayback(VoiceKey(chatId, 7)).docksIn(chatId))
    }

    @Test
    fun hlasovkaJinehoChatuNe() {
        // Hlasovka odchodem z chatu dohraje, cizí by v docku jen zmátla.
        assertFalse(VoicePlayback(VoiceKey(chatId + 1, 7)).docksIn(chatId))
    }

    @Test
    fun hudbaJinehoChatuAno() {
        assertTrue(VoicePlayback(VoiceKey(chatId + 1, 7), music = true).docksIn(chatId))
    }

    @Test
    fun nahledNahravkyNikdy() {
        // Náhled má vlastní pruh nad polem; v docku by byl podruhé.
        assertFalse(VoicePlayback(voicePreviewKey(chatId)).docksIn(chatId))
    }
}
