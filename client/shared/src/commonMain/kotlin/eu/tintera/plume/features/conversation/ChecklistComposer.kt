package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ChecklistDraft
import eu.tintera.plume.core.telegram.ChecklistLimits
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.MessageSchedule
import eu.tintera.plume.core.telegram.ReplyQuote
import eu.tintera.plume.core.telegram.SendOptions
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.confirm
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.core.ui.reject
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.toggle
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.checklist_compose_add_task
import eu.tintera.plume.resources.checklist_compose_name
import eu.tintera.plume.resources.checklist_compose_others_add
import eu.tintera.plume.resources.checklist_compose_others_mark
import eu.tintera.plume.resources.checklist_compose_remove_task
import eu.tintera.plume.resources.checklist_compose_send_failed
import eu.tintera.plume.resources.checklist_compose_task
import eu.tintera.plume.resources.checklist_compose_tasks
import eu.tintera.plume.resources.checklist_compose_title
import eu.tintera.plume.resources.checklist_compose_too_many
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_close
import eu.tintera.plume.resources.conversation_schedule_past
import eu.tintera.plume.resources.conversation_schedule_time
import eu.tintera.plume.resources.conversation_send_schedule
import eu.tintera.plume.resources.conversation_send_silent
import eu.tintera.plume.resources.notifications_off_24dp
import eu.tintera.plume.resources.poll_compose_send
import eu.tintera.plume.resources.poll_compose_settings
import eu.tintera.plume.resources.schedule_send_24dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/*
 * Pravidla formuláře jsou čisté funkce (testy `ChecklistComposerTest`), jako u ankety: co se
 * pošle, kdy je formulář hotový a jak se chovají pole úkolů.
 */

/**
 * Text úkolu bez konců řádku. TDLib úkol s koncem řádku nevezme („without line feeds"), a pole je
 * sice jednořádkové, ale vložený text konce řádku nést může — zahodit je potichu by slepilo dvě
 * slova, mezera je zachová oddělená.
 */
internal fun checklistTaskText(text: String): String = text.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')

/**
 * Pole úkolů po úpravě: **na konci je vždycky jedno prázdné**, dokud se nedojde ke stropu [max] —
 * tatáž úvaha jako u odpovědí ankety ([normalizedAnswers]). Přebytečná prázdná pole na konci
 * zmizí, jedno pole zůstane vždycky; prázdné pole UPROSTŘED se nechává a odebírá se křížkem.
 */
internal fun normalizedTasks(tasks: List<String>, max: Int): List<String> {
    val list = tasks.toMutableList()
    if (list.isEmpty()) list += ""
    while (list.size > 1 && list.last().isBlank() && list[list.lastIndex - 1].isBlank()) {
        list.removeAt(list.lastIndex)
    }
    if (list.last().isNotBlank() && list.size < max) list += ""
    return list
}

/**
 * Hotový seznam z formuláře, nebo `null`, dokud se poslat nedá (Odeslat je pak zašedlé). Prázdná
 * pole se vynechají; text nad strop serveru seznam neodešle — server by ho odmítl až po odeslání.
 * Seznam smí mít jediný úkol (TDLib: 1 až `checklist_task_count_max`).
 */
internal fun checklistDraftOf(
    title: String,
    tasks: List<String>,
    othersCanAddTasks: Boolean,
    othersCanMarkTasksAsDone: Boolean,
    limits: ChecklistLimits,
): ChecklistDraft? {
    val name = title.trim()
    if (name.isEmpty() || name.length > limits.maxTitle) return null
    val filled = tasks.map { checklistTaskText(it).trim() }.filter { it.isNotEmpty() }
    if (filled.size !in 1..limits.maxTasks) return null
    if (filled.any { it.length > limits.maxTaskText }) return null
    return ChecklistDraft(
        title = name,
        tasks = filled,
        othersCanAddTasks = othersCanAddTasks,
        othersCanMarkTasksAsDone = othersCanMarkTasksAsDone,
    )
}

/** Stav odesílání formuláře; text polí je Compose stav přímo ve ViewModelu. */
data class ChecklistComposerStatus(
    val limits: ChecklistLimits = ChecklistLimits(),
    /** V Uložených zprávách nejsou „ostatní" — volby o nich se nekreslí, a „bez zvuku" taky ne. */
    val isSavedMessages: Boolean = false,
    /** Citovaná zpráva, když se seznamem odpovídá; `null`, dokud se nenačte, nebo bez odpovědi. */
    val replyTo: ChatMessage? = null,
    /** Název chatu — citace ho ukáže v soukromém chatu, kde TDLib jméno odesílatele nevyplňuje. */
    val chatTitle: String? = null,
    val sending: Boolean = false,
    val sent: Boolean = false,
    val error: UiText? = null,
)

/**
 * Formulář nového seznamu úkolů. Nabízí se jen s Premium (sponka bez něj položku nemá), takže
 * Premium tu už neřeší.
 *
 * **Text polí je Compose stav, ne `StateFlow`**, a ve ViewModelu, ne v obrazovce — z týchž
 * důvodů jako u formuláře ankety ([PollComposerViewModel]).
 */
class ChecklistComposerViewModel(
    val chatId: Long,
    /** Odpověď rozepsaná v konverzaci; po odeslání ji formulář ohlásí jako spotřebovanou. */
    replyToMessageId: Long?,
    private val replyQuote: ReplyQuote?,
    private val repository: ConversationRepository,
    private val replyRequests: ConversationReplyRequests,
) : ViewModel() {

    /** Odpověď, se kterou seznam odejde; `null` = bez odpovědi (nebo ji uživatel zahodil). */
    var replyToMessageId by mutableStateOf(replyToMessageId)
        private set

    var title by mutableStateOf("")
    val tasks = mutableStateListOf("")

    /*
     * Výchozí je seznam, do kterého smí psát a odškrtávat všichni: nákup ve dvou nebo úkoly ve
     * skupině jsou ten častý případ, seznam jen pro sebe se pozná tím, že je v Uložených zprávách.
     */
    var othersCanAddTasks by mutableStateOf(true)
    var othersCanMarkTasksAsDone by mutableStateOf(true)

    private val _status = MutableStateFlow(ChecklistComposerStatus())
    val status: StateFlow<ChecklistComposerStatus> = _status.asStateFlow()

    init {
        viewModelScope.launch {
            val info = repository.chatInfo(chatId).getOrNull()
            val limits = repository.checklistLimits()
            _status.update {
                it.copy(limits = limits, isSavedMessages = info?.isSavedMessages == true, chatTitle = info?.title)
            }
            normalize()
        }
        replyToMessageId?.let { id ->
            viewModelScope.launch {
                // Citovaná zpráva jen pro náhled; seznam odejde jako odpověď i bez ní.
                repository.message(chatId, id).onSuccess { message -> _status.update { it.copy(replyTo = message) } }
            }
        }
    }

    val draft: ChecklistDraft?
        get() {
            val alone = _status.value.isSavedMessages
            return checklistDraftOf(
                title = title,
                tasks = tasks,
                // V Uložených zprávách nikdo jiný není; volby se nekreslí a posílají se vypnuté.
                othersCanAddTasks = othersCanAddTasks && !alone,
                othersCanMarkTasksAsDone = othersCanMarkTasksAsDone && !alone,
                limits = _status.value.limits,
            )
        }

    fun setTask(index: Int, text: String) {
        if (index !in tasks.indices) return
        tasks[index] = checklistTaskText(text).take(_status.value.limits.maxTaskText + ANSWER_OVERFLOW_ALLOWANCE)
        normalize()
    }

    fun removeTask(index: Int) {
        if (index !in tasks.indices) return
        tasks.removeAt(index)
        normalize()
    }

    fun reportError(message: UiText) {
        _status.update { it.copy(error = message) }
    }

    /** Citovaný úryvek, když odpověď necituje celou zprávu. */
    val replyQuoteText: String? get() = replyQuote?.text

    /** Seznam odejde bez odpovědi; rozepsaná odpověď v konverzaci zůstane, jak byla. */
    fun dropReply() {
        replyToMessageId = null
        _status.update { it.copy(replyTo = null) }
    }

    /** [options] z nabídky nad Odeslat (bez zvuku, naplánovat); ťuknutí posílá výchozí. */
    fun send(options: SendOptions = SendOptions()) {
        val draft = draft ?: return
        if (_status.value.sending) return
        _status.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            val replyTo = replyToMessageId
            repository.sendChecklist(chatId, draft, replyToMessageId = replyTo, quote = replyQuote.takeIf { replyTo != null }, options = options)
                .onSuccess {
                    // I naplánovaný: ten do historie nepřijde a odpověď by nad polem visela.
                    replyTo?.let { replyRequests.replySent(chatId, it) }
                    _status.update { it.copy(sending = false, sent = true) }
                }
                .onFailure { failure ->
                    logger.w(failure) { "Seznam úkolů se nepodařilo odeslat." }
                    _status.update { it.copy(sending = false, error = failure.userMessage(Res.string.checklist_compose_send_failed)) }
                }
        }
    }

    private fun normalize() {
        val normalized = normalizedTasks(tasks, _status.value.limits.maxTasks)
        if (normalized != tasks.toList()) {
            tasks.clear()
            tasks.addAll(normalized)
        }
    }

    private companion object {
        val logger = Logger.withTag("ChecklistComposer")
    }
}

/**
 * Obrazovka nového seznamu úkolů: název, blok úkolů, blok voleb. Tentýž jazyk i stavebnice jako
 * formulář ankety ([PollComposerScreen]) — Odeslat v liště s podržením pro „bez zvuku"
 * a naplánování, bloky zaoblených řádků, přepínače nesené celým řádkem.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChecklistComposerScreen(
    viewModel: ChecklistComposerViewModel,
    onClose: () -> Unit,
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    if (status.sent) {
        LaunchedEffect(Unit) { onClose() }
    }
    val canSend = viewModel.draft != null && !status.sending
    var sendMenuOpen by remember { mutableStateOf(false) }
    var pickingScheduleDay by remember { mutableStateOf(false) }
    var pickedScheduleDay by remember { mutableStateOf<Long?>(null) }
    val limits = status.limits

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.checklist_compose_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.common_close))
                    }
                },
                actions = {
                    if (status.sending) {
                        LoadingIndicator(Modifier.padding(end = 16.dp).size(32.dp))
                    } else {
                        Box {
                            Button(
                                onClick = {
                                    haptics.confirm()
                                    viewModel.send()
                                },
                                enabled = canSend,
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .longPressInstead(enabled = canSend) { sendMenuOpen = true },
                            ) { Text(stringResource(Res.string.poll_compose_send)) }
                            ExpressiveMenu(
                                expanded = sendMenuOpen,
                                onDismiss = { sendMenuOpen = false },
                                groups = listOf(
                                    listOfNotNull(
                                        MenuAction(stringResource(Res.string.conversation_send_silent), Res.drawable.notifications_off_24dp) {
                                            sendMenuOpen = false
                                            haptics.confirm()
                                            viewModel.send(SendOptions(silent = true))
                                        }.takeIf { !status.isSavedMessages },
                                        MenuAction(stringResource(Res.string.conversation_send_schedule), Res.drawable.schedule_send_24dp) {
                                            sendMenuOpen = false
                                            pickingScheduleDay = true
                                        },
                                    ),
                                ),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding(),
        ) {
            status.error?.let { error ->
                item(key = "error") {
                    Text(
                        text = error.resolve(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            }

            status.replyTo?.takeIf { viewModel.replyToMessageId != null }?.let { quoted ->
                item(key = "reply") {
                    ReplyHeader(
                        message = quoted,
                        partnerName = status.chatTitle,
                        quoteText = viewModel.replyQuoteText,
                        onDrop = viewModel::dropReply,
                    )
                }
            }

            item(key = "title") {
                BlockField(
                    value = viewModel.title,
                    onValueChange = { viewModel.title = it.take(limits.maxTitle + ANSWER_OVERFLOW_ALLOWANCE) },
                    label = stringResource(Res.string.checklist_compose_name),
                    max = limits.maxTitle,
                    shape = groupItemShape(0, 1),
                    singleLine = false,
                )
            }

            item(key = "tasks-title") {
                SectionTitle(stringResource(Res.string.checklist_compose_tasks))
            }
            itemsIndexed(viewModel.tasks, key = { index, _ -> "task-$index" }) { index, task ->
                BlockField(
                    value = task,
                    onValueChange = { viewModel.setTask(index, it) },
                    label = stringResource(Res.string.checklist_compose_task, index + 1),
                    max = limits.maxTaskText,
                    shape = groupItemShape(index, viewModel.tasks.size),
                    singleLine = true,
                    trailing = if (viewModel.tasks.size > 1 && task.isNotBlank()) {
                        {
                            IconButton(onClick = { viewModel.removeTask(index) }, shapes = IconButtonDefaults.shapes()) {
                                Icon(
                                    painterResource(Res.drawable.close_24dp),
                                    contentDescription = stringResource(Res.string.checklist_compose_remove_task),
                                )
                            }
                        }
                    } else {
                        null
                    },
                )
            }
            item(key = "tasks-hint") {
                val filled = viewModel.tasks.count { it.isNotBlank() }
                Text(
                    text = if (filled >= limits.maxTasks) {
                        stringResource(Res.string.checklist_compose_too_many, limits.maxTasks)
                    } else {
                        (limits.maxTasks - filled).let { left -> pluralStringResource(Res.plurals.checklist_compose_add_task, left, left) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            // V Uložených zprávách nejsou „ostatní", volby o nich by nic neznamenaly.
            if (!status.isSavedMessages) {
                item(key = "settings-title") {
                    SectionTitle(stringResource(Res.string.poll_compose_settings))
                }
                item(key = "others-add") {
                    SwitchRow(stringResource(Res.string.checklist_compose_others_add), viewModel.othersCanAddTasks, groupItemShape(0, 2)) {
                        haptics.toggle(it)
                        viewModel.othersCanAddTasks = it
                    }
                }
                item(key = "others-mark") {
                    SwitchRow(stringResource(Res.string.checklist_compose_others_mark), viewModel.othersCanMarkTasksAsDone, groupItemShape(1, 2)) {
                        haptics.toggle(it)
                        viewModel.othersCanMarkTasksAsDone = it
                    }
                }
            }
        }
    }

    if (pickingScheduleDay) {
        FutureDayDialog(
            now = Clock.System.now().epochSeconds,
            maxAheadSeconds = MAX_SCHEDULE_AHEAD_SECONDS,
            onPick = { day ->
                pickingScheduleDay = false
                pickedScheduleDay = day
            },
            onDismiss = { pickingScheduleDay = false },
        )
    }
    pickedScheduleDay?.let { day ->
        TimeOfDayDialog(
            title = stringResource(Res.string.conversation_schedule_time),
            initial = Clock.System.now().epochSeconds + 60 * 60,
            onPick = { hour, minute ->
                pickedScheduleDay = null
                val at = closeDateOf(day, hour, minute, TimeZone.currentSystemDefault())
                if (!scheduleValid(at, Clock.System.now().epochSeconds)) {
                    haptics.reject()
                    viewModel.reportError(UiText(Res.string.conversation_schedule_past))
                    return@TimeOfDayDialog
                }
                haptics.confirm()
                viewModel.send(SendOptions(schedule = MessageSchedule.At(at)))
            },
            onDismiss = { pickedScheduleDay = null },
        )
    }
}

/**
 * Co konverzace předá formuláři: rozepsanou odpověď. Odpověď se PŘEDÁVÁ, neodebírá — jako
 * u ankety ([PollComposerRequest]).
 */
data class ChecklistComposerRequest(
    val replyToMessageId: Long?,
    val quote: ReplyQuote?,
)
