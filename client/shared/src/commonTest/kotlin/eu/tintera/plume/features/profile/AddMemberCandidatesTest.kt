package eu.tintera.plume.features.profile

import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSummary
import kotlin.test.Test
import kotlin.test.assertEquals

/** Koho nabídne výběr lidí k přidání do skupiny ([addMemberCandidates]). */
class AddMemberCandidatesTest {

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
    private val petr = chat(2, "Petr")
    private val me = chat(9, "Já")
    private val group = chat(-5, "Skupina", ChatKind.Group)

    @Test
    fun bezDotazuKontaktyBezClenuAUlozenychZprav() {
        val (contacts, others) = addMemberCandidates("", listOf(jana, petr, me), listOf(group), savedMessagesChatId = 9, memberIds = setOf(2))
        assertEquals(listOf(jana), contacts)
        assertEquals(emptyList(), others)
    }

    @Test
    fun dotazHledaBezDiakritikyAOstatniJenSoukromeMimoKontakty() {
        val stranger = chat(3, "Jana Cizí")
        val (contacts, others) = addMemberCandidates(
            query = "jana",
            contacts = listOf(jana, petr),
            searchResults = listOf(jana, stranger, group, stranger),
            savedMessagesChatId = 9,
            memberIds = emptySet(),
        )
        assertEquals(listOf(jana), contacts)
        assertEquals(listOf(stranger), others)
    }

    @Test
    fun novakovaSeNajdeBezHacku() {
        val (contacts, _) = addMemberCandidates("novakova", listOf(jana, petr), emptyList(), null, emptySet())
        assertEquals(listOf(jana), contacts)
    }
}
