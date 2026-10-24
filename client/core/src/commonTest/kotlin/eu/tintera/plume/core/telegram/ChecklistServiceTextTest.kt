package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.ChecklistTask
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.MessageChecklistTasksAdded
import dev.g000sha256.tdl.dto.MessageChecklistTasksDone
import dev.g000sha256.tdl.dto.MessagePinMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.service_checklist_added_many
import eu.tintera.plume.resources.service_checklist_added_one
import eu.tintera.plume.resources.service_checklist_changed_many
import eu.tintera.plume.resources.service_checklist_done_many
import eu.tintera.plume.resources.service_checklist_done_one
import eu.tintera.plume.resources.service_checklist_undone_one
import eu.tintera.plume.resources.service_checklist_you_done_one
import eu.tintera.plume.resources.service_checklist_you_added_many
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Zprávy o úkolech v seznamu se jménem: kdo odškrtl a co, kdo přidal kolik. Úkol se jmenuje jen
 * u jednoho; bez jeho textu (seznam není lokálně) se úkoly spočítají.
 */
class ChecklistServiceTextTest {

    private val me = ServicePerson(name = "", isMe = true)
    private val jana = ServicePerson("Jana")
    private val texts = mapOf(1 to "Mléko", 2 to "Chleba")

    private fun done(done: IntArray = intArrayOf(), undone: IntArray = intArrayOf()) =
        MessageChecklistTasksDone(checklistMessageId = 5, markedAsDoneTaskIds = done, markedAsNotDoneTaskIds = undone)

    private fun task(id: Int, text: String) =
        ChecklistTask(id = id, text = FormattedText(text = text, entities = emptyArray()), completedBy = null, completionDate = 0)

    @Test
    fun jedenUkolSeJmenuje() {
        assertEquals(UiText(Res.string.service_checklist_done_one, "Jana", "Mléko"), done(done = intArrayOf(1)).checklistServiceText(jana, texts))
        assertEquals(UiText(Res.string.service_checklist_you_done_one, "Mléko"), done(done = intArrayOf(1)).checklistServiceText(me, texts))
        assertEquals(UiText(Res.string.service_checklist_undone_one, "Jana", "Chleba"), done(undone = intArrayOf(2)).checklistServiceText(jana, texts))
    }

    @Test
    fun bezTextuUkolSeSpocita() {
        assertEquals(
            UiText(Res.plurals.service_checklist_done_many, 1, "Jana", 1),
            done(done = intArrayOf(7)).checklistServiceText(jana, texts),
        )
    }

    @Test
    fun vicUkoluSeSpocita() {
        assertEquals(
            UiText(Res.plurals.service_checklist_done_many, 2, "Jana", 2),
            done(done = intArrayOf(1, 2)).checklistServiceText(jana, texts),
        )
        // Smíšené odškrtnutí a zrušení: „změnil(a) stav".
        assertEquals(
            UiText(Res.plurals.service_checklist_changed_many, 2, "Jana", 2),
            done(done = intArrayOf(1), undone = intArrayOf(2)).checklistServiceText(jana, texts),
        )
    }

    @Test
    fun pridaneUkoly() {
        assertEquals(
            UiText(Res.string.service_checklist_added_one, "Jana", "Vejce"),
            MessageChecklistTasksAdded(checklistMessageId = 5, tasks = arrayOf(task(3, "Vejce"))).checklistServiceText(jana, emptyMap()),
        )
        assertEquals(
            UiText(Res.plurals.service_checklist_you_added_many, 2, 2),
            MessageChecklistTasksAdded(checklistMessageId = 5, tasks = arrayOf(task(3, "Vejce"), task(4, "Sýr"))).checklistServiceText(me, emptyMap()),
        )
        assertEquals(
            UiText(Res.plurals.service_checklist_added_many, 2, "Jana", 2),
            MessageChecklistTasksAdded(checklistMessageId = 5, tasks = arrayOf(task(3, "Vejce"), task(4, "Sýr"))).checklistServiceText(jana, emptyMap()),
        )
    }

    @Test
    fun dlouhyUkolSeZkrati() {
        val long = "Chleba a rohlíky na snídani pro celou rodinu z pekárny"
        assertEquals(long.take(40).trimEnd() + "…", quotedTask(long))
        assertEquals("Mléko", quotedTask(" Mléko "))
    }

    @Test
    fun jinaZpravaNeni() {
        assertNull(MessagePinMessage(messageId = 1).checklistServiceText(jana, texts))
    }
}
