package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContactOfferTest {

    @Test
    fun clovekMimoKontaktyDostaneNabidku() {
        assertTrue(contactOffer(isContact = false, isBot = false, isSelf = false))
    }

    @Test
    fun kontaktSeOtevreChatem() {
        assertFalse(contactOffer(isContact = true, isBot = false, isSelf = false))
    }

    @Test
    fun vlastniOdkazABotNabidkuNemaji() {
        // Vlastní odkaz vede do Uložených zpráv (profil nemají), bota do kontaktů nikdo nepřidává.
        assertFalse(contactOffer(isContact = false, isBot = false, isSelf = true))
        assertFalse(contactOffer(isContact = false, isBot = true, isSelf = false))
    }
}
