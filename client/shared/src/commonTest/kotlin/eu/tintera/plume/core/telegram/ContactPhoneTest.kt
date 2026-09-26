package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals

/** Číslo sdíleného kontaktu, jak se ukazuje na kartě. */
class ContactPhoneTest {

    @Test
    fun mezinarodniBezPlusuDostanePlus() {
        assertEquals("+420777123456", contactPhone("420777123456"))
    }

    @Test
    fun sPlusemZustane() {
        assertEquals("+420 777 123 456", contactPhone("+420 777 123 456"))
    }

    @Test
    fun mistniSMezeramiZustaneJakPrislo() {
        // Nevíme, jak ho číst — plus by z místního čísla udělal cizí.
        assertEquals("777 123 456", contactPhone(" 777 123 456 "))
    }

    @Test
    fun prazdneZustanePrazdne() {
        assertEquals("", contactPhone(""))
    }
}
