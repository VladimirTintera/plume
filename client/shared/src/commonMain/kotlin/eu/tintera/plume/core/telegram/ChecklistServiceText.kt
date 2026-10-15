package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageChecklistTasksAdded
import dev.g000sha256.tdl.dto.MessageChecklistTasksDone
import dev.g000sha256.tdl.dto.MessageContent
import dev.g000sha256.tdl.dto.MessageSenderUser
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.service_checklist_added_many
import eu.tintera.plume.resources.service_checklist_added_one
import eu.tintera.plume.resources.service_checklist_changed_many
import eu.tintera.plume.resources.service_checklist_done_many
import eu.tintera.plume.resources.service_checklist_done_one
import eu.tintera.plume.resources.service_checklist_undone_one
import eu.tintera.plume.resources.service_checklist_you_added_many
import eu.tintera.plume.resources.service_checklist_you_added_one
import eu.tintera.plume.resources.service_checklist_you_changed_many
import eu.tintera.plume.resources.service_checklist_you_done_many
import eu.tintera.plume.resources.service_checklist_you_done_one
import eu.tintera.plume.resources.service_checklist_you_undone_one
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource

/**
 * Zpráva o úkolech v seznamu se jménem a úkolem: „Jana odškrtl(a) „Mléko"", „Přidal(a) jsi
 * 3 úkoly do seznamu". Bezejmenná věta z [serviceText] zůstává jako náhrada, když jméno neznáme.
 *
 * [actor] je odesílatel zprávy (kdo odškrtl, kdo přidal). [taskTexts] jsou texty úkolů ze zprávy
 * se seznamem podle id — u jednoho odškrtnutého úkolu se jmenuje on; když text neznáme (zpráva
 * se seznamem není načtená, nebo už je smazaná), věta úkoly jen spočítá.
 */
internal fun MessageContent.checklistServiceText(actor: ServicePerson, taskTexts: Map<Int, String>): UiText? = when (this) {
    is MessageChecklistTasksDone -> {
        val done = markedAsDoneTaskIds.toList()
        val undone = markedAsNotDoneTaskIds.toList()
        val single = (done + undone).singleOrNull()?.let { taskTexts[it] }?.let(::quotedTask)
        when {
            single != null && done.isNotEmpty() -> actor.sentence(Res.string.service_checklist_you_done_one, Res.string.service_checklist_done_one, single)
            single != null -> actor.sentence(Res.string.service_checklist_you_undone_one, Res.string.service_checklist_undone_one, single)
            undone.isEmpty() -> actor.counted(Res.plurals.service_checklist_you_done_many, Res.plurals.service_checklist_done_many, done.size)
            // Smíšené, nebo víc zrušených odškrtnutí naráz: zřídka, a „změnil(a) stav" pokryje obojí.
            else -> actor.counted(Res.plurals.service_checklist_you_changed_many, Res.plurals.service_checklist_changed_many, done.size + undone.size)
        }
    }
    is MessageChecklistTasksAdded -> {
        val single = tasks.singleOrNull()?.text?.text?.takeIf { it.isNotBlank() }?.let(::quotedTask)
        if (single != null) {
            actor.sentence(Res.string.service_checklist_you_added_one, Res.string.service_checklist_added_one, single)
        } else {
            actor.counted(Res.plurals.service_checklist_you_added_many, Res.plurals.service_checklist_added_many, tasks.size)
        }
    }
    else -> null
}

/** Zpráva o úkolech v seznamu — u ní stojí za to dohledávat jméno a texty úkolů. */
internal val MessageContent.isChecklistService: Boolean
    get() = this is MessageChecklistTasksDone || this is MessageChecklistTasksAdded

/**
 * [checklistServiceText] i s dohledáním jména a textů úkolů. [taskTexts] dostane id zprávy se
 * seznamem a vrátí texty úkolů podle id (nebo `null`); volá se jen u odškrtnutí jednoho úkolu,
 * kde se úkol jmenuje — přidaný úkol nese text sám.
 */
internal suspend fun Message.namedChecklistServiceText(
    myUserId: suspend () -> Long?,
    nameOf: suspend (Long) -> String?,
    taskTexts: suspend (checklistMessageId: Long) -> Map<Int, String>?,
): UiText? {
    if (!content.isChecklistService) return null
    val actorId = (senderId as? MessageSenderUser)?.userId ?: return null
    val actor = if (isOutgoing || actorId == myUserId()) {
        ServicePerson(name = "", isMe = true)
    } else {
        ServicePerson(nameOf(actorId) ?: return null)
    }
    val done = content as? MessageChecklistTasksDone
    val texts = if (done != null && done.markedAsDoneTaskIds.size + done.markedAsNotDoneTaskIds.size == 1) {
        taskTexts(done.checklistMessageId).orEmpty()
    } else {
        emptyMap()
    }
    return content.checklistServiceText(actor, texts)
}

/**
 * Úkol v servisní větě. Delší se zkrátí — servisní zpráva je jeden řádek uprostřed konverzace
 * a celý úkol si člověk přečte v seznamu.
 */
internal fun quotedTask(text: String): String {
    val oneLine = text.trim()
    return if (oneLine.length <= MAX_TASK_IN_SERVICE) oneLine else oneLine.take(MAX_TASK_IN_SERVICE).trimEnd() + "…"
}

private fun ServicePerson.sentence(
    mine: StringResource,
    theirs: StringResource,
    task: String,
): UiText = if (isMe) UiText(mine, task) else UiText(theirs, name, task)

private fun ServicePerson.counted(
    mine: PluralStringResource,
    theirs: PluralStringResource,
    count: Int,
): UiText = if (isMe) UiText(mine, count, count) else UiText(theirs, count, name, count)

/** Kolik znaků úkolu se vejde do servisní věty. */
private const val MAX_TASK_IN_SERVICE = 40
