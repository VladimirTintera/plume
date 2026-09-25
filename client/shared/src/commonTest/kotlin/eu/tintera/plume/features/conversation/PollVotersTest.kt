package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.PollVotersPage
import eu.tintera.plume.core.telegram.SenderInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Dotahování hlasujících po stránkách. */
class PollVotersTest {

    private fun sender(id: Long) = SenderInfo(id = id, title = "Člověk $id", avatarFileId = null)

    @Test
    fun dalsiStrankaSePripojiBezDuplicit() {
        val first = AnswerVoters().withPage(PollVotersPage(listOf(sender(1), sender(2)), total = 4))
        assertTrue(first.hasMore)

        // Tentýž hlasující na hranici stránek — dva stejné klíče by seznam shodily.
        val second = first.withPage(PollVotersPage(listOf(sender(2), sender(3)), total = 4))
        assertEquals(listOf(1L, 2L, 3L), second.voters.map { it.id })
        assertTrue(second.hasMore)
    }

    @Test
    fun prazdnaStrankaJeKonecIKdyzServerOdhadovalVic() {
        val loaded = AnswerVoters().withPage(PollVotersPage(listOf(sender(1)), total = 5))
        val end = loaded.withPage(PollVotersPage(emptyList(), total = 5))
        assertFalse(end.hasMore)
        assertEquals(1, end.total)
    }

    @Test
    fun behemNacitaniSeDalsiNenabizi() {
        assertFalse(AnswerVoters(voters = listOf(sender(1)), total = 3, loading = true).hasMore)
        assertFalse(AnswerVoters(voters = listOf(sender(1)), total = 3, loading = false, failed = true).hasMore)
    }
}
