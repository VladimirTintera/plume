package eu.tintera.plume.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class NumberFormatTest {
    @Test
    fun tisiceSeOddeluji() {
        assertEquals("10 704 668", 10_704_668.grouped())
        assertEquals("1 000", 1000.grouped())
        assertEquals("999", 999.grouped())
        assertEquals("0", 0.grouped())
        assertEquals("-12 345", (-12_345).grouped())
    }
}
