package eu.tintera.plume.core.settings.sync

import eu.tintera.plume.core.chatlabels.StoredChatLabel
import eu.tintera.plume.core.chatlabels.StoredLabelAssignment
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatLabelSyncTest {

    private val labels = listOf(
        StoredChatLabel(1, "a", "Rodina", "GREEN", order = 0),
        StoredChatLabel(1, "b", "Vyřídit", "RED", order = 1),
        StoredChatLabel(2, "c", "Cizí účet", "BLUE"),
    )
    private val assignments = listOf(
        StoredLabelAssignment(1, 10, "a"),
        StoredLabelAssignment(1, 11, "b"),
        StoredLabelAssignment(2, 12, "c"),
    )

    @Test
    fun polozkyAZpetBezZtraty() {
        val entries = chatLabelEntries(labels, assignments, accountId = 1)
        assertEquals(setOf("label:a", "label:b", "chat:10:a", "chat:11:b"), entries.keys)

        // Cizí účet zůstane, jak byl; účet 1 se složí z položek.
        assertEquals(labels.sortedBy { it.id }, chatLabelsWith(entries, labels, accountId = 1).sortedBy { it.id })
        assertEquals(assignments.toSet(), labelAssignmentsWith(entries, assignments, accountId = 1).toSet())
    }

    @Test
    fun smazaniStitkuNaJednomAPrirazeniNaDruhemSeSlije() {
        val base = chatLabelEntries(labels, assignments, accountId = 1)
        // Telefon štítek „b" smazal i s přiřazením, desktop mezitím dal štítek „a" chatu 13.
        val phone = base - "label:b" - "chat:11:b"
        val desktop = base + ("chat:13:a" to "1")
        val merged = merge3(base, local = phone, remote = desktop) { local, _ -> local }

        assertEquals(listOf("a"), chatLabelsWith(merged, emptyList(), accountId = 1).map { it.id })
        assertEquals(
            setOf(10L to "a", 13L to "a"),
            labelAssignmentsWith(merged, emptyList(), accountId = 1).map { it.chatId to it.labelId }.toSet(),
        )
    }

    @Test
    fun poskozenePolozkySeZahodi() {
        val entries = mapOf("label:x" to "{nesmysl", "label:" to "{}", "chat:abc:a" to "1", "chat:5:" to "1", "jiny:1" to "1")
        assertEquals(emptyList(), chatLabelsWith(entries, emptyList(), accountId = 1))
        assertEquals(emptyList(), labelAssignmentsWith(entries, emptyList(), accountId = 1))
    }
}
