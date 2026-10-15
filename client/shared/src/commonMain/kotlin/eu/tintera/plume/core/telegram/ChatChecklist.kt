package eu.tintera.plume.core.telegram

/**
 * Seznam úkolů ve zprávě (`MessageChecklist`).
 *
 * Zpráva se seznamem má popis „Seznam úkolů: název" v [ChatMessage.description], jako anketa —
 * citace, připnutý pruh, náhled v seznamu chatů i shrnutí ho berou, když vlastní text chybí.
 * Blok seznamu ho jen nekreslí.
 *
 * **Založit, odškrtnout i doplnit úkol smí jen účet s Premium.** Příznaky [canMarkTasksAsDone]
 * a [canAddTasks] od serveru znamenají „smí, MÁ-LI Premium" (dokumentace TDLib) — o Premium
 * samotném rozhoduje ViewModel, který ho zná.
 */
data class ChatChecklist(
    val title: String,
    val titleEntities: List<MessageEntity> = emptyList(),
    val tasks: List<ChecklistItem>,
    /** Úkoly smí přidávat i jiní než autor. */
    val othersCanAddTasks: Boolean,
    /** Úkoly smí odškrtávat i jiní než autor — Telegram mu pak říká „skupinový seznam". */
    val othersCanMarkTasksAsDone: Boolean,
    val canAddTasks: Boolean,
    val canMarkTasksAsDone: Boolean,
) {
    val doneCount: Int get() = tasks.count { it.isDone }
}

/**
 * Jeden úkol. [doneBy] je ten, kdo ho odškrtl; dohledává ho repository z keše odesílatelů
 * (jako tváře hlasujících v anketě), převod zprávy ho nechává prázdný.
 */
data class ChecklistItem(
    val id: Int,
    val text: String,
    val entities: List<MessageEntity> = emptyList(),
    /** Kdy byl úkol odškrtnutý (unixový čas); `0` = hotový není. */
    val doneAt: Int = 0,
    /** Id odesílatele, který úkol odškrtl; `0` = nikdo, nebo ho server neposlal. */
    val doneById: Long = 0,
    val doneBy: SenderInfo? = null,
) {
    val isDone: Boolean get() = doneAt != 0 || doneById != 0L
}

/**
 * Odškrtnutí, které odešlo a server ho ještě nepotvrdil: úkol [taskId] má být hotový ([done]),
 * nebo zase ne. Bublina ho kreslí hned — čekat na server by u fajfky působilo, jako by ťuknutí
 * nezabralo.
 */
data class PendingChecklistMark(val taskId: Int, val done: Boolean)

/**
 * Úkol, jak ho bublina ukáže: s odškrtnutím, které ještě čeká na server, má přednost před stavem
 * od serveru. Jakmile server stav potvrdí, je výsledek stejný a čekající záznam zmizí.
 */
fun ChecklistItem.shownDone(pending: List<PendingChecklistMark>): Boolean =
    pending.lastOrNull { it.taskId == id }?.done ?: isDone

/**
 * Seznam úkolů k odeslání ([ConversationRepository.sendChecklist]). Platnost (délky, počet)
 * hlídá formulář; tohle jsou hotová data.
 */
data class ChecklistDraft(
    val title: String,
    val tasks: List<String>,
    val othersCanAddTasks: Boolean,
    val othersCanMarkTasksAsDone: Boolean,
    /**
     * Id úkolů při úpravě seznamu, ve stejném pořadí jako [tasks]; `0` = nový úkol. Prázdné
     * u nového seznamu. Stávající úkol si id NESMÍ změnit — podle id TDLib pozná úkol, kterému
     * má zachovat odškrtnutí.
     */
    val taskIds: List<Int> = emptyList(),
)

/**
 * Id úkolů k odeslání: stávající si nechají své, nové dostanou čísla za nejvyšším — kladná
 * a v seznamu jedinečná, jak TDLib chce. U nového seznamu ([ChecklistDraft.taskIds] prázdné)
 * je to prostě 1, 2, 3…
 */
fun ChecklistDraft.resolvedTaskIds(): List<Int> {
    var next = (taskIds.maxOrNull() ?: 0).coerceAtLeast(0)
    return tasks.indices.map { index -> taskIds.getOrNull(index)?.takeIf { it > 0 } ?: ++next }
}

/**
 * Meze seznamu od serveru (`checklist_title_length_max`, `checklist_task_count_max`,
 * `checklist_task_text_length_max`). Kdyby je server nevydal, platí hodnoty, které posílá dnes
 * (ověřeno na emulátoru 15. 10. 2026: 255, 30 a 200).
 */
data class ChecklistLimits(
    val maxTitle: Int = DEFAULT_CHECKLIST_TITLE_MAX,
    val maxTasks: Int = DEFAULT_CHECKLIST_TASK_COUNT_MAX,
    val maxTaskText: Int = DEFAULT_CHECKLIST_TASK_TEXT_MAX,
)

const val DEFAULT_CHECKLIST_TITLE_MAX = 255
const val DEFAULT_CHECKLIST_TASK_COUNT_MAX = 30
const val DEFAULT_CHECKLIST_TASK_TEXT_MAX = 200
