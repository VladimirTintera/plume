package eu.tintera.plume.features.webapp

import eu.tintera.plume.core.telegram.NamedWebApp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebAppConsentTest {

    private fun app(skipConfirmation: Boolean = true, requestWriteAccess: Boolean = false) =
        NamedWebApp(title = "Kavárna", requestWriteAccess = requestWriteAccess, skipConfirmation = skipConfirmation)

    @Test
    fun poprveUBotaSePtaVzdycky() {
        assertTrue(needsWebAppConsent(confirmedBot = false, app = null))
        assertTrue(needsWebAppConsent(confirmedBot = false, app = app()))
    }

    @Test
    fun potvrzenyBotBezPojmenovaneAplikaceSeNepta() {
        assertFalse(needsWebAppConsent(confirmedBot = true, app = null))
    }

    @Test
    fun pojmenovanaAplikaceSeServerovymPotvrzenimSeNepta() {
        assertFalse(needsWebAppConsent(confirmedBot = true, app = app(skipConfirmation = true)))
    }

    @Test
    fun pojmenovanaAplikaceBezServerovehoPotvrzeniSePtaPokazde() {
        assertTrue(needsWebAppConsent(confirmedBot = true, app = app(skipConfirmation = false)))
    }

    @Test
    fun zadostOPsaniSeRozhodujePokazde() {
        assertTrue(needsWebAppConsent(confirmedBot = true, app = app(skipConfirmation = true, requestWriteAccess = true)))
    }
}
