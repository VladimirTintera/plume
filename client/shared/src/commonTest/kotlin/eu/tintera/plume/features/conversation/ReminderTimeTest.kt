package eu.tintera.plume.features.conversation

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Termín ze zprávy pro připomínku. Zpráva odeslaná ve středu 14. 10. 2026 v 10:00. */
class ReminderTimeTest {
    private val wednesday = LocalDateTime(2026, 10, 14, 10, 0)

    private fun at(text: String, sentAt: LocalDateTime = wednesday) = reminderTimeFromText(text, sentAt)

    @Test
    fun vztazneDnyACas() {
        assertEquals(LocalDateTime(2026, 10, 15, 15, 0), at("Sraz zítra v 15:00 na Černém Mostě"))
        assertEquals(LocalDateTime(2026, 10, 15, 15, 0), at("sraz zitra v 15:00"))
        assertEquals(LocalDateTime(2026, 10, 16, 8, 0), at("pozítří ráno"))
        assertEquals(LocalDateTime(2026, 10, 14, 19, 0), at("dneska večer?"))
        assertEquals(LocalDateTime(2026, 10, 15, 15, 30), at("Let's meet tomorrow at 3:30pm"))
    }

    @Test
    fun denVTydnu() {
        assertEquals(LocalDateTime(2026, 10, 16, 15, 0), at("v pátek v 15 hodin"))
        assertEquals(LocalDateTime(2026, 10, 19, 8, 0), at("ozvu se v pondělí"))
        assertEquals(LocalDateTime(2026, 10, 16, 19, 0), at("on Friday evening"))
        // Týž den s časem, který ještě nebyl, je dnes; bez času příští týden.
        assertEquals(LocalDateTime(2026, 10, 14, 18, 0), at("ve středu v 18:00"))
        assertEquals(LocalDateTime(2026, 10, 21, 8, 0), at("ve středu"))
    }

    @Test
    fun datum() {
        assertEquals(LocalDateTime(2026, 10, 20, 9, 30), at("Termín 20. 10. v 9:30"))
        assertEquals(LocalDateTime(2026, 11, 3, 8, 0), at("odevzdat do 3.11."))
        // Datum, které letos už bylo, je příští rok.
        assertEquals(LocalDateTime(2027, 1, 5, 8, 0), at("5. 1."))
        assertEquals(LocalDateTime(2027, 2, 1, 8, 0), at("1.2.2027"))
    }

    /** Čas bez dne je den odeslání, když už tehdy minul, tak další den. */
    @Test
    fun casBezDne() {
        assertEquals(LocalDateTime(2026, 10, 14, 15, 0), at("v 15:00 u kina"))
        assertEquals(LocalDateTime(2026, 10, 15, 8, 0), at("v 8 u brány"))
        assertEquals(LocalDateTime(2026, 10, 14, 20, 0), at("v 8 večer"))
        assertEquals(LocalDateTime(2026, 10, 14, 21, 30), at("sejdeme se v 21.30"))
    }

    /** Radši nic než nesmysl. */
    @Test
    fun bezTerminu() {
        assertNull(at("Přijde nás 5 lidí"))
        assertNull(at("Verze 5.10 je venku"))
        assertNull(at("To je ráno super"))
        assertNull(at("Dopoledne bylo krásně"))
        assertNull(at("Díky!"))
        assertNull(at("Skóre 3:1"))
    }

    @Test
    fun svatkySPevnymDatem() {
        // Model tohle počítal špatně („den před Silvestrem" → 24. 12.).
        assertEquals(LocalDateTime(2026, 12, 30, 8, 0), at("Připomeň mi to den před Silvestrem"))
        assertEquals(LocalDateTime(2026, 12, 31, 19, 0), at("Na Silvestra večer u nás"))
        assertEquals(LocalDateTime(2026, 12, 24, 18, 0), at("Na Štědrý den v 18:00 večeře"))
        assertEquals(LocalDateTime(2026, 12, 23, 8, 0), at("den před Štědrým dnem nakoupit"))
        assertEquals(LocalDateTime(2026, 12, 25, 8, 0), at("o Božím hodu"))
        assertEquals(LocalDateTime(2027, 1, 1, 8, 0), at("den po Silvestru"))
        assertEquals(LocalDateTime(2027, 1, 1, 8, 0), at("na Nový rok"))
        assertEquals(LocalDateTime(2026, 10, 31, 20, 0), at("Halloween party at 8pm"))
        assertEquals(LocalDateTime(2026, 12, 24, 8, 0), at("on Christmas Eve"))
        assertEquals(LocalDateTime(2026, 12, 31, 8, 0), at("New Year’s Eve"))
        // Svátek, který letos už byl, je příští rok.
        assertEquals(LocalDateTime(2027, 2, 14, 8, 0), at("na svatého Valentýna"))
        // „Valentýn" je i jméno a „zítra" řekne den jistěji než svátek.
        assertEquals(LocalDateTime(2026, 10, 15, 8, 0), at("Valentýn přijde zítra"))
        assertEquals(LocalDateTime(2026, 10, 15, 8, 0), at("Silvestr se staví zítra"))
        // Datum psané číslem má přednost před svátkem.
        assertEquals(LocalDateTime(2026, 12, 28, 8, 0), at("28. 12. před Silvestrem"))
    }
}
