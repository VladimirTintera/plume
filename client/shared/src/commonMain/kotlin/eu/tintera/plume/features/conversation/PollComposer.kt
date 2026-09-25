package eu.tintera.plume.features.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.MIN_POLL_ANSWERS
import eu.tintera.plume.core.telegram.POLL_ANSWER_MAX
import eu.tintera.plume.core.telegram.POLL_EXPLANATION_MAX
import eu.tintera.plume.core.telegram.POLL_EXPLANATION_MAX_LINE_FEEDS
import eu.tintera.plume.core.telegram.POLL_QUESTION_MAX
import eu.tintera.plume.core.telegram.PollDraft
import eu.tintera.plume.core.telegram.PollComposerMode
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.GROUP_SPACING
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.confirm
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.toggle
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.check_box_filled_24dp
import eu.tintera.plume.resources.check_box_outline_blank_24dp
import eu.tintera.plume.resources.check_circle_filled_24dp
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_close
import eu.tintera.plume.resources.poll_compose_add_answer
import eu.tintera.plume.resources.poll_compose_answer
import eu.tintera.plume.resources.poll_compose_answers
import eu.tintera.plume.resources.poll_compose_anonymous
import eu.tintera.plume.resources.poll_compose_anonymous_channel
import eu.tintera.plume.resources.poll_compose_counter
import eu.tintera.plume.resources.poll_compose_explanation
import eu.tintera.plume.resources.poll_compose_explanation_hint
import eu.tintera.plume.resources.poll_compose_mark_correct
import eu.tintera.plume.resources.poll_compose_multiple
import eu.tintera.plume.resources.poll_compose_question
import eu.tintera.plume.resources.poll_compose_quiz
import eu.tintera.plume.resources.poll_compose_quiz_hint
import eu.tintera.plume.resources.poll_compose_remove_answer
import eu.tintera.plume.resources.poll_compose_send
import eu.tintera.plume.resources.poll_compose_send_failed
import eu.tintera.plume.resources.poll_compose_settings
import eu.tintera.plume.resources.poll_compose_shuffle
import eu.tintera.plume.resources.poll_compose_title
import eu.tintera.plume.resources.poll_compose_title_quiz
import eu.tintera.plume.resources.poll_compose_too_many
import eu.tintera.plume.resources.radio_button_unchecked_24dp
import androidx.compose.runtime.remember
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ReplyQuote
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.features.conversation.items.QuotedMessage
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.poll_compose_drop_reply
import eu.tintera.plume.resources.poll_compose_duration
import eu.tintera.plume.resources.poll_compose_hide_results
import eu.tintera.plume.resources.poll_duration_none
import eu.tintera.plume.resources.poll_duration_minutes
import eu.tintera.plume.resources.poll_duration_hours
import eu.tintera.plume.resources.poll_duration_days
import eu.tintera.plume.resources.poll_duration_weeks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/*
 * Pravidla formuláře jsou čisté funkce (testy `PollComposerTest`): co se pošle, kdy je formulář
 * hotový a jak se chovají pole odpovědí. Uvnitř `@Composable` by se hraniční případy — prázdná
 * pole uprostřed, správná odpověď, která se vymazala, strop odpovědí — neověřily.
 */

/**
 * Pole odpovědí po úpravě: **na konci je vždycky jedno prázdné**, do kterého se píše další
 * odpověď, dokud se nedojde ke stropu [max]. Přebytečná prázdná pole na konci zmizí, ale
 * nejméně [MIN_POLL_ANSWERS] polí zůstává vždycky.
 *
 * Tak to dělají i oficiální klienti: tlačítko „Přidat odpověď" by bylo ťuknutí navíc u každé
 * odpovědi. Prázdné pole UPROSTŘED se nechává — uživatel ho mohl vymazat jen proto, aby ho
 * přepsal; odebírá se křížkem.
 */
internal fun normalizedAnswers(answers: List<String>, max: Int): List<String> {
    val list = answers.toMutableList()
    while (list.size < MIN_POLL_ANSWERS) list += ""
    while (list.size > MIN_POLL_ANSWERS && list.last().isBlank() && list[list.lastIndex - 1].isBlank()) {
        list.removeAt(list.lastIndex)
    }
    if (list.last().isNotBlank() && list.size < max) list += ""
    return list
}

/**
 * Správné odpovědi kvízu po odebrání pole [removed]: jeho index zmizí a vyšší se posunou —
 * správná odpověď musí zůstat u TÉHOŽ textu, ne na téže pozici.
 */
internal fun correctAfterRemoval(correct: Set<Int>, removed: Int): Set<Int> =
    correct.mapNotNull { index ->
        when {
            index == removed -> null
            index > removed -> index - 1
            else -> index
        }
    }.toSet()

/**
 * Hotová anketa z formuláře, nebo `null`, dokud se poslat nedá (tlačítko Odeslat je pak
 * zašedlé). Prázdná pole odpovědí se vynechají; [correct] jsou indexy POLÍ a přepočítají se na
 * indexy odpovědí, které opravdu odejdou.
 */
internal fun pollDraftOf(
    question: String,
    answers: List<String>,
    anonymous: Boolean,
    multipleAnswers: Boolean,
    shuffle: Boolean,
    quiz: Boolean,
    correct: Set<Int>,
    explanation: String,
    maxAnswers: Int,
    openPeriodSeconds: Int = 0,
    hideResultsUntilClose: Boolean = false,
): PollDraft? {
    val text = question.trim()
    if (text.isEmpty() || text.length > POLL_QUESTION_MAX) return null

    val filled = answers.mapIndexedNotNull { index, answer -> answer.trim().takeIf { it.isNotEmpty() }?.let { index to it } }
    if (filled.size !in MIN_POLL_ANSWERS..maxAnswers) return null
    if (filled.any { (_, answer) -> answer.length > POLL_ANSWER_MAX }) return null

    val correctAnswers = if (quiz) {
        filled.mapIndexedNotNull { position, (field, _) -> position.takeIf { field in correct } }.toSet()
    } else {
        null
    }
    if (correctAnswers != null) {
        if (correctAnswers.isEmpty()) return null
        // Bez víc odpovědí je správná jen jedna — výběr to hlídá, tohle je pojistka.
        if (!multipleAnswers && correctAnswers.size > 1) return null
    }
    val why = if (quiz) explanation.trim() else ""
    if (why.length > POLL_EXPLANATION_MAX || why.count { it == '\n' } > POLL_EXPLANATION_MAX_LINE_FEEDS) return null

    return PollDraft(
        question = text,
        answers = filled.map { it.second },
        anonymous = anonymous,
        multipleAnswers = multipleAnswers,
        shuffle = shuffle,
        correctAnswers = correctAnswers,
        explanation = why,
        openPeriodSeconds = openPeriodSeconds.coerceAtLeast(0),
        // Výsledky až po uzavření jen s dobou trvání — bez ní by je nikdo nikdy neviděl.
        hideResultsUntilClose = hideResultsUntilClose && openPeriodSeconds > 0,
    )
}

/**
 * Nabízené doby trvání ankety v sekundách, jen do stropu serveru ([maxOpenPeriodSeconds]).
 * Bez stropu (`0`) se nenabízí žádná — volba doby se pak nekreslí.
 */
internal fun pollDurations(maxOpenPeriodSeconds: Int): List<Int> =
    POLL_DURATION_PRESETS.filter { it <= maxOpenPeriodSeconds }

private val POLL_DURATION_PRESETS = listOf(
    5 * 60,
    60 * 60,
    24 * 60 * 60,
    3 * 24 * 60 * 60,
    7 * 24 * 60 * 60,
)

/** Stav odesílání formuláře; text polí je Compose stav přímo ve ViewModelu. */
data class PollComposerStatus(
    /** V kanálu je anketa vždycky anonymní — přepínač se pak nekreslí. */
    val isChannel: Boolean = false,
    val maxAnswers: Int = DEFAULT_MAX_ANSWERS,
    /** Nabízené doby trvání; prázdné = volba doby se nekreslí. */
    val durations: List<Int> = emptyList(),
    /** Citovaná zpráva, když se anketou odpovídá; `null`, dokud se nenačte, nebo bez odpovědi. */
    val replyTo: ChatMessage? = null,
    /** Název chatu — citace ho ukáže v soukromém chatu, kde TDLib jméno odesílatele nevyplňuje. */
    val chatTitle: String? = null,
    val sending: Boolean = false,
    val sent: Boolean = false,
    val error: UiText? = null,
)

/**
 * Formulář nové ankety.
 *
 * **Text polí je Compose stav, ne `StateFlow`** — hodnota vrácená o dispatch později by
 * poskakovala kurzorem (viz „Úprava profilu" v `CLAUDE.md`). Ve ViewModelu, ne v obrazovce,
 * aby rozepsaná anketa přežila otočení.
 */
class PollComposerViewModel(
    private val chatId: Long,
    /** Vynucený druh od tlačítka bota; přepínač kvízu se pak nekreslí. */
    val mode: PollComposerMode,
    /**
     * Odpověď rozepsaná v konverzaci, když se formulář otevřel; anketa pak odejde jako odpověď.
     * Konverzace ji po odeslání ankety zahodí sama (viz `pollConsumesReply`).
     */
    replyToMessageId: Long?,
    private val replyQuote: ReplyQuote?,
    private val repository: ConversationRepository,
) : ViewModel() {

    /** Odpověď, se kterou anketa odejde; `null` = bez odpovědi (nebo ji uživatel zahodil). */
    var replyToMessageId by mutableStateOf(replyToMessageId)
        private set

    var question by mutableStateOf("")
    val answers = mutableStateListOf("", "")
    var anonymous by mutableStateOf(true)
    var multipleAnswers by mutableStateOf(false)
    var shuffle by mutableStateOf(false)
    var quiz by mutableStateOf(mode == PollComposerMode.QuizOnly)

    /** Správné odpovědi kvízu jako indexy POLÍ v [answers]. */
    var correct by mutableStateOf(emptySet<Int>())
    var explanation by mutableStateOf("")

    /** Za kolik sekund se anketa uzavře; `0` = bez omezení. */
    var openPeriod by mutableStateOf(0)
    var hideResults by mutableStateOf(false)

    private val _status = MutableStateFlow(PollComposerStatus())
    val status: StateFlow<PollComposerStatus> = _status.asStateFlow()

    init {
        viewModelScope.launch {
            val info = repository.chatInfo(chatId).getOrNull()
            val limits = repository.pollLimits()
            _status.update {
                it.copy(
                    isChannel = info?.isChannel == true,
                    chatTitle = info?.title,
                    maxAnswers = limits.maxAnswers,
                    durations = pollDurations(limits.maxOpenPeriodSeconds),
                )
            }
        }
        replyToMessageId?.let { id ->
            viewModelScope.launch {
                // Citovaná zpráva jen pro náhled nad otázkou; když ji načíst nejde, anketa
                // odejde jako odpověď i tak — server zprávu zná.
                repository.message(chatId, id).onSuccess { message -> _status.update { it.copy(replyTo = message) } }
            }
        }
    }

    val draft: PollDraft?
        get() = pollDraftOf(
            question = question,
            answers = answers,
            anonymous = anonymous || _status.value.isChannel,
            multipleAnswers = multipleAnswers,
            shuffle = shuffle,
            quiz = quiz,
            correct = correct,
            explanation = explanation,
            maxAnswers = _status.value.maxAnswers,
            openPeriodSeconds = openPeriod,
            hideResultsUntilClose = hideResults,
        )

    fun setAnswer(index: Int, text: String) {
        if (index !in answers.indices) return
        answers[index] = text.take(POLL_ANSWER_MAX + ANSWER_OVERFLOW_ALLOWANCE)
        // Vymazaná odpověď nemůže být správná — v kvízu by jinak zůstala označená prázdná.
        if (text.isBlank()) correct = correct - index
        normalize()
    }

    fun removeAnswer(index: Int) {
        if (index !in answers.indices) return
        answers.removeAt(index)
        correct = correctAfterRemoval(correct, index)
        normalize()
    }

    /** Označí správnou odpověď. Bez víc odpovědí je správná jen jedna, jako u rádia. */
    fun toggleCorrect(index: Int) {
        if (answers.getOrNull(index).isNullOrBlank()) return
        correct = when {
            index in correct -> correct - index
            multipleAnswers -> correct + index
            else -> setOf(index)
        }
    }

    fun changeMultipleAnswers(on: Boolean) {
        multipleAnswers = on
        // Z víc správných odpovědí se při vypnutí nechá ta první — rádio jich víc neukáže.
        if (!on && correct.size > 1) correct = setOf(correct.min())
    }

    /** Citovaný úryvek, když odpověď necituje celou zprávu. */
    val replyQuoteText: String? get() = replyQuote?.text

    /** Anketa odejde bez odpovědi; rozepsaná odpověď v konverzaci zůstane, jak byla. */
    fun dropReply() {
        replyToMessageId = null
        _status.update { it.copy(replyTo = null) }
    }

    fun send() {
        val draft = draft ?: return
        if (_status.value.sending) return
        _status.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            val replyTo = replyToMessageId
            repository.sendPoll(chatId, draft, replyToMessageId = replyTo, quote = replyQuote.takeIf { replyTo != null })
                .onSuccess { _status.update { it.copy(sending = false, sent = true) } }
                .onFailure { failure ->
                    logger.w(failure) { "Anketu se nepodařilo odeslat." }
                    _status.update { it.copy(sending = false, error = failure.userMessage(Res.string.poll_compose_send_failed)) }
                }
        }
    }

    private fun normalize() {
        val normalized = normalizedAnswers(answers, _status.value.maxAnswers)
        if (normalized != answers.toList()) {
            answers.clear()
            answers.addAll(normalized)
        }
    }

    private companion object {
        val logger = Logger.withTag("PollComposer")
    }
}

/**
 * Obrazovka nové ankety: otázka, blok odpovědí, blok voleb. Odeslat je v liště (je to hlavní
 * akce obrazovky a s klávesnicí nahoře musí zůstat vidět); po odeslání obrazovka odejde a pod
 * ní je konverzace s novou anketou.
 *
 * Tentýž jazyk jako Nastavení a profil: bloky zaoblených řádků (`groupItemShape`), nadpis nad
 * blokem, přepínače, které nese celý řádek.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PollComposerScreen(
    viewModel: PollComposerViewModel,
    onClose: () -> Unit,
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    if (status.sent) {
        // Jednorázově: obrazovka odchází, stav s ní.
        LaunchedEffect(Unit) { onClose() }
    }
    val canSend = viewModel.draft != null && !status.sending

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (viewModel.quiz) Res.string.poll_compose_title_quiz else Res.string.poll_compose_title))
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.common_close))
                    }
                },
                actions = {
                    if (status.sending) {
                        LoadingIndicator(Modifier.padding(end = 16.dp).size(32.dp))
                    } else {
                        Button(
                            onClick = {
                                haptics.confirm()
                                viewModel.send()
                            },
                            enabled = canSend,
                            modifier = Modifier.padding(end = 12.dp),
                        ) { Text(stringResource(Res.string.poll_compose_send)) }
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

            item(key = "question") {
                BlockField(
                    value = viewModel.question,
                    onValueChange = { viewModel.question = it.take(POLL_QUESTION_MAX + ANSWER_OVERFLOW_ALLOWANCE) },
                    label = stringResource(Res.string.poll_compose_question),
                    max = POLL_QUESTION_MAX,
                    shape = groupItemShape(0, 1),
                    singleLine = false,
                )
            }

            item(key = "answers-title") {
                SectionTitle(stringResource(Res.string.poll_compose_answers))
            }
            itemsIndexed(viewModel.answers, key = { index, _ -> "answer-$index" }) { index, answer ->
                AnswerField(
                    index = index,
                    value = answer,
                    count = viewModel.answers.size,
                    quiz = viewModel.quiz,
                    multiple = viewModel.multipleAnswers,
                    correct = index in viewModel.correct,
                    removable = viewModel.answers.size > MIN_POLL_ANSWERS && answer.isNotBlank(),
                    onValueChange = { viewModel.setAnswer(index, it) },
                    onToggleCorrect = {
                        haptics.toggle(index !in viewModel.correct)
                        viewModel.toggleCorrect(index)
                    },
                    onRemove = { viewModel.removeAnswer(index) },
                )
            }
            item(key = "answers-hint") {
                val filled = viewModel.answers.count { it.isNotBlank() }
                val hint = when {
                    viewModel.quiz && viewModel.correct.isEmpty() -> stringResource(Res.string.poll_compose_quiz_hint)
                    filled >= status.maxAnswers -> stringResource(Res.string.poll_compose_too_many, status.maxAnswers)
                    else -> (status.maxAnswers - filled).let { left ->
                        pluralStringResource(Res.plurals.poll_compose_add_answer, left, left)
                    }
                }
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (viewModel.quiz && viewModel.correct.isEmpty()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            item(key = "settings-title") {
                SectionTitle(stringResource(Res.string.poll_compose_settings))
            }
            val settings = buildList {
                if (!status.isChannel) add(SettingRow.Anonymous)
                add(SettingRow.Multiple)
                if (viewModel.mode == PollComposerMode.Any) add(SettingRow.Quiz)
                add(SettingRow.Shuffle)
                if (status.durations.isNotEmpty()) add(SettingRow.Duration)
                // Výsledky až po skončení mají smysl jen s dobou trvání.
                if (viewModel.openPeriod > 0) add(SettingRow.HideResults)
            }
            itemsIndexed(settings, key = { _, row -> "setting-$row" }) { index, row ->
                val shape = groupItemShape(index, settings.size)
                when (row) {
                    SettingRow.Anonymous -> SwitchRow(stringResource(Res.string.poll_compose_anonymous), viewModel.anonymous, shape) {
                        haptics.toggle(it)
                        viewModel.anonymous = it
                    }
                    SettingRow.Multiple -> SwitchRow(stringResource(Res.string.poll_compose_multiple), viewModel.multipleAnswers, shape) {
                        haptics.toggle(it)
                        viewModel.changeMultipleAnswers(it)
                    }
                    SettingRow.Quiz -> SwitchRow(stringResource(Res.string.poll_compose_quiz), viewModel.quiz, shape) {
                        haptics.toggle(it)
                        viewModel.quiz = it
                    }
                    SettingRow.Shuffle -> SwitchRow(stringResource(Res.string.poll_compose_shuffle), viewModel.shuffle, shape) {
                        haptics.toggle(it)
                        viewModel.shuffle = it
                    }
                    SettingRow.Duration -> DurationRow(
                        selected = viewModel.openPeriod,
                        options = status.durations,
                        shape = shape,
                        onSelect = { seconds ->
                            viewModel.openPeriod = seconds
                            if (seconds == 0) viewModel.hideResults = false
                        },
                    )
                    SettingRow.HideResults -> SwitchRow(stringResource(Res.string.poll_compose_hide_results), viewModel.hideResults, shape) {
                        haptics.toggle(it)
                        viewModel.hideResults = it
                    }
                }
            }
            if (status.isChannel) {
                item(key = "channel-anonymous") {
                    Text(
                        text = stringResource(Res.string.poll_compose_anonymous_channel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            item(key = "explanation") {
                AnimatedVisibility(visible = viewModel.quiz) {
                    Column(Modifier.animateContentSize()) {
                        SectionTitle(stringResource(Res.string.poll_compose_explanation))
                        BlockField(
                            value = viewModel.explanation,
                            onValueChange = { viewModel.explanation = it.take(POLL_EXPLANATION_MAX + ANSWER_OVERFLOW_ALLOWANCE) },
                            label = stringResource(Res.string.poll_compose_explanation_hint),
                            max = POLL_EXPLANATION_MAX,
                            shape = groupItemShape(0, 1),
                            singleLine = false,
                        )
                    }
                }
            }
        }
    }
}

private enum class SettingRow { Anonymous, Multiple, Quiz, Shuffle, Duration, HideResults }

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = GROUP_SPACING + 4.dp, bottom = 6.dp),
    )
}

/**
 * Pole v bloku: tónovaná plocha řádku, bez podtržení — podtržení `TextField`u je jazyk, kterého
 * se aplikace vzdala (viz přihlášení a „Jeden prvek pro hledání"). Počítadlo se ukáže až
 * v posledních dvaceti znacích a přes strop zčervená; text se nad strop kousek pustí, aby šlo
 * vložit delší text a zkrátit ho, a odeslání je do té doby zašedlé.
 */
@Composable
private fun BlockField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    max: Int,
    shape: Shape,
    singleLine: Boolean,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(label) },
            singleLine = singleLine,
            leadingIcon = leading,
            trailingIcon = trailing,
            supportingText = counterText(value.length, max)?.let { { it() } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Počítadlo znaků — až v posledních [COUNTER_FROM] znacích, přes strop chybovou barvou. */
private fun counterText(length: Int, max: Int): (@Composable () -> Unit)? {
    if (length < max - COUNTER_FROM) return null
    return {
        Text(
            text = stringResource(Res.string.poll_compose_counter, length, max),
            color = if (length > max) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Pole odpovědi. V kvízu vlevo kolečko (jedna správná) nebo zaškrtávátko (víc správných), kterým
 * se označuje správná odpověď; vpravo křížek na odebrání.
 */
@Composable
private fun AnswerField(
    index: Int,
    value: String,
    count: Int,
    quiz: Boolean,
    multiple: Boolean,
    correct: Boolean,
    removable: Boolean,
    onValueChange: (String) -> Unit,
    onToggleCorrect: () -> Unit,
    onRemove: () -> Unit,
) {
    val markLabel = stringResource(Res.string.poll_compose_mark_correct)
    BlockField(
        value = value,
        onValueChange = onValueChange,
        label = stringResource(Res.string.poll_compose_answer, index + 1),
        max = POLL_ANSWER_MAX,
        shape = groupItemShape(index, count),
        singleLine = true,
        leading = if (quiz) {
            {
                IconButton(onClick = onToggleCorrect, enabled = value.isNotBlank()) {
                    Icon(
                        painter = painterResource(
                            when {
                                correct && multiple -> Res.drawable.check_box_filled_24dp
                                correct -> Res.drawable.check_circle_filled_24dp
                                multiple -> Res.drawable.check_box_outline_blank_24dp
                                else -> Res.drawable.radio_button_unchecked_24dp
                            },
                        ),
                        contentDescription = markLabel,
                        tint = if (correct) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            null
        },
        trailing = if (removable) {
            {
                IconButton(onClick = onRemove) {
                    Icon(
                        painterResource(Res.drawable.close_24dp),
                        contentDescription = stringResource(Res.string.poll_compose_remove_answer),
                    )
                }
            }
        } else {
            null
        },
    )
}

/** Přepínač, který nese celý řádek (viz „Obrazovka nastavení"). */
@Composable
private fun SwitchRow(label: String, checked: Boolean, shape: Shape, onChange: (Boolean) -> Unit) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

/** Kdyby server strop nevydal. */
private const val DEFAULT_MAX_ANSWERS = 10

/** O kolik se text smí dostat přes strop, než ho pole utne — aby šlo vložit a zkrátit. */
private const val ANSWER_OVERFLOW_ALLOWANCE = 50

/** Počítadlo se ukáže, až zbývá tolik znaků. */
private const val COUNTER_FROM = 20

/**
 * Citovaná zpráva nad otázkou, když se anketou odpovídá — tentýž prvek jako nad vstupním polem,
 * v terciární ploše (cizí obsah). Křížek odpověď zahodí; anketa pak odejde samostatně.
 */
@Composable
private fun ReplyHeader(message: ChatMessage, partnerName: String?, quoteText: String?, onDrop: () -> Unit) {
    Surface(
        shape = groupItemShape(0, 1),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = GROUP_SPACING),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QuotedMessage(
                message = message,
                partnerName = partnerName,
                accent = MaterialTheme.colorScheme.onTertiaryContainer,
                contained = false,
                quoteText = quoteText,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDrop) {
                Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.poll_compose_drop_reply))
            }
        }
    }
}

/**
 * Doba trvání: řádek s vybranou hodnotou, ťuknutí otevře nabídku (Expressive, jako výběr
 * období v hledání). Vybraná doba má v nabídce fajfku.
 */
@Composable
private fun DurationRow(selected: Int, options: List<Int>, shape: Shape, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { expanded = true }
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(stringResource(Res.string.poll_compose_duration), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    text = durationLabel(selected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        ExpressiveMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            groups = listOf(
                (listOf(0) + options).map { seconds ->
                    MenuAction(
                        label = durationLabel(seconds),
                        trailing = if (seconds == selected) {
                            { Icon(painterResource(Res.drawable.check_24dp), contentDescription = null) }
                        } else {
                            null
                        },
                    ) { onSelect(seconds) }
                },
            ),
        )
    }
}

/** „Bez omezení", „5 minut", „1 hodina", „3 dny", „1 týden" — podle největší celé jednotky. */
@Composable
private fun durationLabel(seconds: Int): String = when {
    seconds <= 0 -> stringResource(Res.string.poll_duration_none)
    seconds % WEEK == 0 -> (seconds / WEEK).let { pluralStringResource(Res.plurals.poll_duration_weeks, it, it) }
    seconds % DAY == 0 -> (seconds / DAY).let { pluralStringResource(Res.plurals.poll_duration_days, it, it) }
    seconds % HOUR == 0 -> (seconds / HOUR).let { pluralStringResource(Res.plurals.poll_duration_hours, it, it) }
    else -> (seconds / MINUTE).let { pluralStringResource(Res.plurals.poll_duration_minutes, it, it) }
}

private const val MINUTE = 60
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR
private const val WEEK = 7 * DAY

/**
 * Co konverzace předá formuláři: vynucený druh ankety (tlačítko bota) a rozepsanou odpověď.
 * Odpověď se PŘEDÁVÁ, neodebírá: když uživatel formulář zavře, zůstane v konverzaci, jak byla.
 */
data class PollComposerRequest(
    val mode: PollComposerMode,
    val replyToMessageId: Long?,
    val quote: ReplyQuote?,
)

