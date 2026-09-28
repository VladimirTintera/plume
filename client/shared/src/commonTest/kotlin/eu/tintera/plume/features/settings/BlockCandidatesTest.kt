package eu.tintera.plume.features.settings

import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSummary
import kotlin.test.Test
import kotlin.test.assertEquals

/** Koho výběr „Zablokovat uživatele" nabídne. */
class BlockCandidatesTest {

    private fun chat(id: Long, title: String, kind: ChatKind = ChatKind.Private) = ChatSummary(
        id = id,
        title = title,
        unreadCount = 0,
        lastMessagePreview = null,
        isPinned = false,
        order = id,
        avatarFileId = null,
        isMuted = false,
        isMarkedAsUnread = false,
        kind = kind,
        lastMessageTimestamp = null,
        presence = null,
        activity = null,
        draft = null,
    )

    private val jana = chat(1, "Jana Nováková")
    private val petr = chat(2, "Petr Šťastný")
    private val me = chat(9, "Já")

    @Test
    fun bezDotazuJenKontaktyBezBlokovanychAUlozenychZprav() {
        val (contacts, others) = blockCandidates("", listOf(jana, petr, me), listOf(chat(5, "Bot")), 9, setOf(2))
        assertEquals(listOf(jana), contacts)
        assertEquals(emptyList(), others)
    }

    @Test
    fun hledaSeBezDiakritikyAOstatniJenSoukrome() {
        val results = listOf(petr, chat(5, "Stastny bot"), chat(6, "Stastna skupina", ChatKind.Group), chat(7, "Kanal", ChatKind.Channel))
        val (contacts, others) = blockCandidates("stastn", listOf(jana, petr), results, 9, emptySet())
        assertEquals(listOf(petr), contacts)
        // Kontakt podruhé mezi ostatními není, skupina a kanál vůbec.
        assertEquals(listOf(5L), others.map { it.id })
    }
}
