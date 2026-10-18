package eu.tintera.plume.features.conversation.items

import kotlin.test.Test
import kotlin.test.assertEquals

class SuggestedPostTest {
    @Test
    fun cenaVTonBezZbytecnychNul() {
        assertEquals("12", formatTon(1200))
        assertEquals("12,5", formatTon(1250))
        assertEquals("12,05", formatTon(1205))
        assertEquals("0,99", formatTon(99))
    }
}
