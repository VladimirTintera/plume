package eu.tintera.plume.core.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Rozřazení chatů do složek: pokyn a čtení odpovědi ([parseFolderCategories]). */
class FolderPromptTest {

    @Test
    fun pokynNeseCislovaneChatySDruhem() {
        val prompt = folderPrompt(
            listOf(
                FolderCandidate("Jana Nováková", FolderChatKind.Private, "Přijedeš v neděli na oběd?"),
                FolderCandidate("Tým Plume", FolderChatKind.Group, null),
            ),
        )
        assertTrue("1. [private chat] Jana Nováková — Přijedeš v neděli na oběd?" in prompt)
        assertTrue("2. [group] Tým Plume\n" in prompt)
        assertTrue("OTHER - anything else, or when you are not sure" in prompt)
    }

    @Test
    fun dlouhyNahledANovyRadekVNazvuSeUpravi() {
        val prompt = folderPrompt(listOf(FolderCandidate("Dva\nřádky", FolderChatKind.Channel, "x".repeat(MAX_FOLDER_PREVIEW_CHARS + 20))))
        assertTrue("1. [channel] Dva řádky — ${"x".repeat(MAX_FOLDER_PREVIEW_CHARS)}\n" in prompt)
    }

    @Test
    fun odpovedTolerantniKObalu() {
        val output = """
            1: WORK
            2. family
            3) **Friends**
            - 4 - NEWS
            5: OTHER
            6: PIZZA
            9: WORK
            1: SCHOOL
        """.trimIndent()
        assertEquals(
            mapOf(0 to FolderCategory.Work, 1 to FolderCategory.Family, 2 to FolderCategory.Friends, 3 to FolderCategory.News),
            parseFolderCategories(output, count = 6),
        )
    }
}
