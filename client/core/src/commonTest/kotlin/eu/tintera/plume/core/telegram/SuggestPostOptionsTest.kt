package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SuggestPostOptionsTest {
    /** Návrh příspěvku jde zdarma a bez termínu; běžná zpráva volby nenese vůbec. */
    @Test
    fun navrhPrispevkuNeseInfoBezCenyATerminu() {
        val info = assertNotNull(SendOptions(suggestPost = true).toTdl()?.suggestedPostInfo)
        assertNull(info.price)
        assertEquals(0, info.sendDate)
        assertNull(SendOptions().toTdl())
    }
}
