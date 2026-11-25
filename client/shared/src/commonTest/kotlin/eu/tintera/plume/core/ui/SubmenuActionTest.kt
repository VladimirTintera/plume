package eu.tintera.plume.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SubmenuActionTest {
    private fun action(label: String) = MenuAction(label, onClick = {})

    @Test
    fun emptySubmenuIsNoRow() {
        assertNull(submenuAction("Další", null, listOf(emptyList(), emptyList())))
    }

    @Test
    fun singleItemGoesUpInsteadOfSubmenu() {
        val only = action("Zablokovat")
        assertSame(only, submenuAction("Další", null, listOf(emptyList(), listOf(only))))
    }

    @Test
    fun moreItemsMakeSubmenuWithoutEmptyGroups() {
        val link = action("Kopírovat odkaz")
        val qr = action("QR kód")
        val block = action("Zablokovat")
        val row = submenuAction("Další", null, listOf(listOf(link, qr), emptyList(), listOf(block)))!!
        assertEquals("Další", row.label)
        assertEquals(listOf(listOf(link, qr), listOf(block)), row.submenu)
        assertTrue(row.submenu.none { it.isEmpty() })
    }
}
