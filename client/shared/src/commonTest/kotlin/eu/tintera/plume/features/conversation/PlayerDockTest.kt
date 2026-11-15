package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.audio.VoiceKey
import eu.tintera.plume.core.audio.VoicePlayback
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerDockTest {

    private val slot = AccountSlot.Primary
    private val other = AccountSlot("bbbbbb")
    private val chatId = 42L

    @Test
    fun hlasovkaTohotoChatuJdeDoDocku() {
        assertTrue(VoicePlayback(VoiceKey(slot, chatId, 7)).docksIn(slot, chatId))
    }

    @Test
    fun hlasovkaJinehoChatuNe() {
        // Hlasovka odchodem z chatu dohraje, cizí by v docku jen zmátla.
        assertFalse(VoicePlayback(VoiceKey(slot, chatId + 1, 7)).docksIn(slot, chatId))
    }

    /** Chat s týmž id v jiném účtu (společný kanál, chat s týmž člověkem) je jiný chat. */
    @Test
    fun hlasovkaStejnehoIdVJinemUctuNe() {
        assertFalse(VoicePlayback(VoiceKey(other, chatId, 7)).docksIn(slot, chatId))
    }

    @Test
    fun hudbaJinehoChatuAno() {
        assertTrue(VoicePlayback(VoiceKey(slot, chatId + 1, 7), music = true).docksIn(slot, chatId))
    }

    /** Skladbu z jiného účtu ovládá dock nad seznamem chatů; otevřít ji jde jen v jejím účtu. */
    @Test
    fun hudbaJinehoUctuNe() {
        assertFalse(VoicePlayback(VoiceKey(other, chatId, 7), music = true).docksIn(slot, chatId))
    }

    @Test
    fun nahledNahravkyNikdy() {
        // Náhled má vlastní pruh nad polem; v docku by byl podruhé.
        assertFalse(VoicePlayback(voicePreviewKey(slot, chatId)).docksIn(slot, chatId))
    }
}
