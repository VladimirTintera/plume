package eu.tintera.plume.features.conversation

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.PendingChecklistMark
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.checklist_add_premium
import eu.tintera.plume.resources.checklist_add_task_failed
import eu.tintera.plume.resources.checklist_edit_premium
import eu.tintera.plume.resources.checklist_mark_failed
import eu.tintera.plume.resources.checklist_mark_premium
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Seznamy úkolů v konverzaci: odškrtnutí úkolu a doplnění nového.
 *
 * **Obojí smí jen účet s Premium**, i když server u seznamu `canMarkTasksAsDone` / `canAddTasks`
 * pošle — ty znamenají „smí, MÁ-LI Premium". Bez něj se požadavek neposílá (server by ho
 * odmítl) a snackbar řekne proč; tiché nic by vypadalo jako nefunkční ťuknutí.
 */
internal class ConversationChecklists(
    private val chatId: Long,
    private val state: MutableStateFlow<ConversationState>,
    private val scope: CoroutineScope,
    private val repository: ConversationRepository,
    /** Má účet Premium — čte se v okamžiku ťuknutí, ne při založení. */
    private val isPremium: () -> Boolean,
    private val logger: Logger,
) {
    /**
     * Odškrtne úkol, nebo odškrtnutí zruší. Bublina změnu ukáže hned
     * ([ConversationState.pendingChecklistMarks]); nový stav seznamu přinese úprava obsahu zprávy
     * (`editedMessages`), jako hlas v anketě. Po odpovědi serveru — i po chybě — čekající záznam
     * zmizí a platí stav ze zprávy.
     */
    fun mark(messageId: Long, taskId: Int, done: Boolean) {
        val checklist = state.value.messages.firstOrNull { it.id == messageId }?.checklist ?: return
        if (!checklist.canMarkTasksAsDone) return
        if (!isPremium()) {
            state.update { it.copy(notice = UiText(Res.string.checklist_mark_premium)) }
            return
        }
        val mark = PendingChecklistMark(taskId, done)
        state.update { it.copy(pendingChecklistMarks = it.pendingChecklistMarks.plusMark(messageId, mark)) }
        scope.launch {
            val result = repository.markChecklistTasks(
                chatId = chatId,
                messageId = messageId,
                done = if (done) listOf(taskId) else emptyList(),
                notDone = if (done) emptyList() else listOf(taskId),
            )
            state.update { current ->
                current.copy(
                    pendingChecklistMarks = current.pendingChecklistMarks.minusMark(messageId, mark),
                    error = result.exceptionOrNull()?.reported(logger, Res.string.checklist_mark_failed) ?: current.error,
                )
            }
        }
    }

    /** „Přidat úkol" pod seznamem: bez Premium vysvětlení, jinak dialog s polem. */
    fun openAddTask(messageId: Long) {
        val checklist = state.value.messages.firstOrNull { it.id == messageId }?.checklist ?: return
        if (!checklist.canAddTasks) return
        if (!isPremium()) {
            state.update { it.copy(notice = UiText(Res.string.checklist_add_premium)) }
            return
        }
        scope.launch {
            // Strop délky úkolu posílá server; dialog ho potřebuje hned, takže se čte před ním.
            val limits = repository.checklistLimits()
            state.update { it.copy(dialog = ConversationDialog.AddChecklistTask(messageId, maxLength = limits.maxTaskText)) }
        }
    }

    /** Doplní úkol z dialogu. Nový úkol přijde úpravou obsahu zprávy. */
    fun addTask(text: String) {
        val messageId = state.value.dialog<ConversationDialog.AddChecklistTask>()?.messageId ?: return
        val trimmed = text.trim()
        state.update { it.closing<ConversationDialog.AddChecklistTask>() }
        if (trimmed.isEmpty()) return
        scope.launch {
            repository.addChecklistTasks(chatId, messageId, listOf(trimmed)).onFailure { failure ->
                state.update { it.copy(error = failure.reported(logger, Res.string.checklist_add_task_failed)) }
            }
        }
    }

    /** „Upravit" nad vlastním seznamem: bez Premium vysvětlení, jinak formulář s ním. */
    fun openEditor(messageId: Long) {
        if (state.value.messages.none { it.id == messageId && it.checklist != null }) return
        if (!isPremium()) {
            state.update { it.copy(notice = UiText(Res.string.checklist_edit_premium)) }
            return
        }
        state.update { it.copy(openChecklistEditor = messageId) }
    }

    fun consumeOpenEditor() {
        state.update { it.copy(openChecklistEditor = null) }
    }

    fun dismissAddTask() {
        state.update { it.closing<ConversationDialog.AddChecklistTask>() }
    }
}

/** Přidá čekající odškrtnutí; novější ťuknutí na týž úkol má přednost (viz `shownDone`). */
internal fun Map<Long, List<PendingChecklistMark>>.plusMark(messageId: Long, mark: PendingChecklistMark) =
    this + (messageId to (this[messageId].orEmpty() + mark))

/**
 * Odebere vyřízené odškrtnutí — jen TEN záznam, ne všechny k úkolu: když uživatel mezitím ťukl
 * znovu, novější ťuknutí dál čeká na svou odpověď.
 */
internal fun Map<Long, List<PendingChecklistMark>>.minusMark(messageId: Long, mark: PendingChecklistMark): Map<Long, List<PendingChecklistMark>> {
    val list = this[messageId] ?: return this
    val index = list.indexOf(mark).takeIf { it >= 0 } ?: return this
    val rest = list.toMutableList().apply { removeAt(index) }
    return if (rest.isEmpty()) this - messageId else this + (messageId to rest)
}
