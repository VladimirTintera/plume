package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals

/** S jakou dobou mazání vznikne nová skupina. */
class NewChatAutoDeleteTest {

    @Test
    fun vychoziDobaMazaniProjdeJenVCelychDnechDoRoku() {
        assertEquals(0, newChatAutoDelete(null))
        assertEquals(0, newChatAutoDelete(0))
        assertEquals(86_400, newChatAutoDelete(86_400))
        assertEquals(31 * 86_400, newChatAutoDelete(31 * 86_400))
        // Hodina by u supergroupy neprošla a s ní ani celé založení.
        assertEquals(0, newChatAutoDelete(3_600))
        assertEquals(0, newChatAutoDelete(366 * 86_400))
        assertEquals(0, newChatAutoDelete(-86_400))
    }
}
