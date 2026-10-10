package eu.tintera.plume.core.ai

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Prompt a čtení výstupu modelu pro termín připomínky. */
class ReminderPromptTest {
    private val sentAt = LocalDateTime(2026, 10, 14, 10, 5)

    @Test
    fun promptNeseDenAZpravu() {
        val prompt = reminderPrompt("sejdem se za týden po obědě", sentAt)
        assertTrue("Wednesday 2026-10-14 at 10:05" in prompt)
        assertTrue("sejdem se za týden po obědě" in prompt)
    }

    @Test
    fun ctePresnyFormatIVetu() {
        assertEquals(LocalDateTime(2026, 10, 21, 13, 0), parseReminderTime("2026-10-21 13:00", sentAt))
        assertEquals(LocalDateTime(2026, 10, 21, 13, 0), parseReminderTime("The moment is \"2026-10-21T13:00\".", sentAt))
        assertEquals(LocalDateTime(2026, 10, 21, 9, 30), parseReminderTime("2026-10-21 9:30", sentAt))
    }

    @Test
    fun nicNeboNesmysl() {
        assertNull(parseReminderTime("NONE", sentAt))
        assertNull(parseReminderTime("2026-02-30 10:00", sentAt))
        assertNull(parseReminderTime("2026-10-21 25:00", sentAt))
        // Před odesláním zprávy, nebo víc než rok po něm.
        assertNull(parseReminderTime("2026-10-13 10:00", sentAt))
        assertNull(parseReminderTime("1970-01-01 00:00", sentAt))
        assertNull(parseReminderTime("2028-01-01 08:00", sentAt))
    }
}
