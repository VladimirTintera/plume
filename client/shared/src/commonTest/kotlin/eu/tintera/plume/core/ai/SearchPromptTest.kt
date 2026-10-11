package eu.tintera.plume.core.ai

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Prompt hledání přirozeným jazykem a čtení polí z výstupu modelu. */
class SearchPromptTest {
    @Test
    fun promptNeseDnesekADotaz() {
        val prompt = searchPrompt("fotky ze skupin loni v létě", LocalDate(2026, 10, 11))
        assertTrue("Sunday 2026-10-11" in prompt)
        assertTrue("fotky ze skupin loni v létě" in prompt)
    }

    @Test
    fun ctePoleIsOmackou() {
        val fields = searchFields(
            """
            Here are the filters:
            - **TEXT:** dovolené
            chats: GROUPS
            CONTENT : PHOTOS
            FROM: 2025-06-01
            TO: 2025-08-31
            """.trimIndent(),
        )
        assertEquals(
            mapOf("TEXT" to "dovolené", "CHATS" to "GROUPS", "CONTENT" to "PHOTOS", "FROM" to "2025-06-01", "TO" to "2025-08-31"),
            fields,
        )
    }

    @Test
    fun prvniVyskytVyhrava() {
        assertEquals("pizza", searchFields("TEXT: pizza\nTEXT: jiné")["TEXT"])
    }
}
