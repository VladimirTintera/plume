package eu.tintera.plume.core.ai

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventPromptTest {
    private val text = "Sraz v 15:00 na Černém Mostě u kina"

    @Test
    fun promptNeseTerminOdPravidelAKonciKlicem() {
        val prompt = eventPrompt(text, LocalDateTime(2026, 10, 14, 10, 0), LocalDateTime(2026, 10, 14, 15, 0))
        assertTrue("Wednesday 2026-10-14 at 10:00" in prompt)
        assertTrue("the time would be Wednesday 2026-10-14 at 15:00." in prompt)
        assertTrue(prompt.endsWith("WHEN:"))
    }

    @Test
    fun odpovedSKlici() {
        val details = parseEventDetails("WHEN: FUTURE\nEVENT: YES\nTITLE: Sraz u kina\nPLACE: Černý Most", text)
        assertEquals(EventDetails(isEvent = true, title = "Sraz u kina", place = "Černý Most"), details)
    }

    @Test
    fun prvniRadekBezKliceJeOdpovedNaWhen() {
        // Prompt končí „WHEN:", model pokračuje rovnou odpovědí.
        val details = parseEventDetails(" Future.\nEVENT: yes\nTITLE: \"Kino\"\nPLACE: NONE", text)
        assertEquals(EventDetails(isEvent = true, title = "Kino", place = null), details)
        assertEquals(EventDetails(isEvent = false, title = null, place = null), parseEventDetails("FUTURE\nEVENT: NO", text))
    }

    @Test
    fun minulostRozhodujeSama() {
        // Gemma 4 E2B: „Volal jsem ti v 9:30" za schůzku vydávala.
        assertEquals(false, parseEventDetails("PAST\nEVENT: YES\nTITLE: Volal jsem ti", text)?.isEvent)
        // Bez výslovné budoucnosti se nepotvrzuje.
        assertNull(parseEventDetails("EVENT: YES\nTITLE: Kino", text))
    }

    @Test
    fun vseNaJednomRadku() {
        val details = parseEventDetails("FUTURE / EVENT: YES / TITLE: Sraz u kina / PLACE: kino", text)
        assertEquals(EventDetails(isEvent = true, title = "Sraz u kina", place = "kino"), details)
    }

    @Test
    fun nejistotaJeNull() {
        assertNull(parseEventDetails("FUTURE\nEVENT: YES or NO", text))
        assertNull(parseEventDetails("FUTURE\nEVENT: Not sure", text))
        assertNull(parseEventDetails("", text))
    }

    @Test
    fun mistoMusiBytVeZprave() {
        // Model si místo vymyslel.
        assertNull(parseEventDetails("WHEN: FUTURE\nEVENT: YES\nTITLE: Kino\nPLACE: Václavské náměstí", text)?.place)
        // Skloňované a bez diakritiky platí.
        assertEquals("Cerny Most", parseEventDetails("FUTURE\n**EVENT**: YES\nPLACE: Cerny Most", text)?.place)
    }

    @Test
    fun dlouhyNazevSeNebere() {
        val long = "Setkání s celou rodinou na Černém Mostě u kina a pak společná večeře v restauraci"
        assertNull(parseEventDetails("WHEN: FUTURE\nEVENT: YES\nTITLE: $long", text)?.title)
    }
}
