package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChecklistItem
import eu.tintera.plume.core.telegram.ChecklistLimits
import eu.tintera.plume.core.telegram.PendingChecklistMark
import eu.tintera.plume.core.telegram.PollChatKind
import eu.tintera.plume.core.telegram.checklistsAllowedIn
import eu.tintera.plume.core.telegram.shownDone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Seznam úkolů: formulář, čekající odškrtnutí a kam seznam smí. */
class ChecklistComposerTest {

    private val limits = ChecklistLimits(maxTitle = 20, maxTasks = 3, maxTaskText = 10)

    private fun draft(title: String = "Nákup", tasks: List<String> = listOf("Mléko", "Chleba", "")) =
        checklistDraftOf(title, tasks, othersCanAddTasks = true, othersCanMarkTasksAsDone = false, limits = limits)

    // --- Pole úkolů ---

    @Test
    fun naKonciJeVzdyckyJednoPrazdnePole() {
        assertEquals(listOf(""), normalizedTasks(emptyList(), max = 3))
        assertEquals(listOf("Mléko", ""), normalizedTasks(listOf("Mléko"), max = 3))
        // Dvě prázdná na konci se sloučí do jednoho.
        assertEquals(listOf("Mléko", ""), normalizedTasks(listOf("Mléko", "", ""), max = 3))
    }

    @Test
    fun naStropuSePrazdnePoleNepridava() {
        assertEquals(listOf("A", "B", "C"), normalizedTasks(listOf("A", "B", "C"), max = 3))
    }

    @Test
    fun prazdnePoleUprostredZustava() {
        // Uživatel ho mohl vymazat, aby ho přepsal; odebírá se křížkem.
        assertEquals(listOf("A", "", "B", ""), normalizedTasks(listOf("A", "", "B"), max = 5))
    }

    @Test
    fun konecRadkuVUkoluJeMezera() {
        // TDLib úkol s koncem řádku nevezme; slova se ale nesmí slepit.
        assertEquals("Mléko a chleba", checklistTaskText("Mléko\na chleba"))
        assertEquals("Mléko a chleba", checklistTaskText("Mléko\r\na chleba"))
    }

    // --- Co se pošle ---

    @Test
    fun prazdnaPoleSeVynechaji() {
        val draft = assertNotNull(draft(tasks = listOf("Mléko", "", " Chleba ", "")))
        assertEquals(listOf("Mléko", "Chleba"), draft.tasks)
        assertEquals("Nákup", draft.title)
        assertTrue(draft.othersCanAddTasks)
        assertFalse(draft.othersCanMarkTasksAsDone)
    }

    @Test
    fun staciJedinyUkol() {
        assertNotNull(draft(tasks = listOf("Mléko", "")))
    }

    @Test
    fun bezNazvuNeboUkoluSeNeposila() {
        assertNull(draft(title = "  "))
        assertNull(draft(tasks = listOf("", " ")))
    }

    @Test
    fun nadStropemSeNeposila() {
        assertNull(draft(title = "x".repeat(21)))
        assertNull(draft(tasks = listOf("x".repeat(11))))
        assertNull(draft(tasks = listOf("A", "B", "C", "D")))
        assertNotNull(draft(title = "x".repeat(20), tasks = listOf("x".repeat(10), "B", "C")))
    }

    // --- Odškrtnutí, které čeká na server ---

    private val task = ChecklistItem(id = 7, text = "Mléko")

    @Test
    fun cekajiciOdskrtnutiMaPrednost() {
        assertFalse(task.shownDone(emptyList()))
        assertTrue(task.shownDone(listOf(PendingChecklistMark(7, done = true))))
        // Cizí úkol se ho netýká.
        assertFalse(task.shownDone(listOf(PendingChecklistMark(8, done = true))))
    }

    @Test
    fun novejsiTuknutiVyhrava() {
        val marks = listOf(PendingChecklistMark(7, done = true), PendingChecklistMark(7, done = false))
        assertFalse(task.shownDone(marks))
    }

    @Test
    fun hotovyUkolPodleServeru() {
        assertTrue(task.copy(doneAt = 1_700_000_000).isDone)
        assertTrue(task.copy(doneById = 42).isDone)
        assertFalse(task.copy(doneAt = 1_700_000_000).shownDone(listOf(PendingChecklistMark(7, done = false))))
    }

    @Test
    fun vyrizeneOdskrtnutiOdejdeSamo() {
        val first = PendingChecklistMark(7, done = true)
        val second = PendingChecklistMark(7, done = false)
        val pending = emptyMap<Long, List<PendingChecklistMark>>().plusMark(1, first).plusMark(1, second)
        // Odpověď na první ťuknutí nesmí smazat druhé, které ještě čeká.
        assertEquals(mapOf(1L to listOf(second)), pending.minusMark(1, first))
        assertEquals(emptyMap(), pending.minusMark(1, first).minusMark(1, second))
        // Neznámý záznam nic nemění.
        assertEquals(pending, pending.minusMark(2, first))
    }

    // --- Kam seznam smí ---

    @Test
    fun seznamJdeDoSoukromehoChatuAleNeDoKanalu() {
        assertTrue(checklistsAllowedIn(PollChatKind.Private))
        assertTrue(checklistsAllowedIn(PollChatKind.Group))
        assertTrue(checklistsAllowedIn(PollChatKind.Bot))
        assertTrue(checklistsAllowedIn(PollChatKind.SavedMessages))
        assertFalse(checklistsAllowedIn(PollChatKind.Channel))
        assertFalse(checklistsAllowedIn(PollChatKind.Secret))
    }
}
