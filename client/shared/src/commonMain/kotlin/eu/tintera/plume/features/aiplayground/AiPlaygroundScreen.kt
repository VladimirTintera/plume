package eu.tintera.plume.features.aiplayground

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import eu.tintera.plume.core.ui.ExpressiveAssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import eu.tintera.plume.core.ui.ExpressiveSuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.adaptive.ContentWidth
import eu.tintera.ai.core.ModelVariant
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.FormTextField
import eu.tintera.plume.core.ui.GROUP_CORNER
import eu.tintera.plume.core.ui.INNER_CORNER
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.TextClipboard
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.features.settings.SettingsTopBar
import eu.tintera.plume.features.settings.settingsScroll
import eu.tintera.plume.features.settings.settingsScrollBehavior
import eu.tintera.plume.features.settings.title
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.ai_playground_empty_text
import eu.tintera.plume.resources.ai_playground_empty_title
import eu.tintera.plume.resources.ai_playground_first_word
import eu.tintera.plume.resources.ai_playground_forgotten
import eu.tintera.plume.resources.ai_playground_input_hint
import eu.tintera.plume.resources.ai_playground_new
import eu.tintera.plume.resources.ai_playground_regenerate
import eu.tintera.plume.resources.ai_playground_send
import eu.tintera.plume.resources.ai_playground_speed
import eu.tintera.plume.resources.ai_playground_stop
import eu.tintera.plume.resources.ai_playground_stopped
import eu.tintera.plume.resources.ai_playground_suggestion_explain
import eu.tintera.plume.resources.ai_playground_suggestion_joke
import eu.tintera.plume.resources.ai_playground_suggestion_poem
import eu.tintera.plume.resources.ai_playground_suggestion_translate
import eu.tintera.plume.resources.ai_playground_title
import eu.tintera.plume.resources.ai_playground_truncated
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.content_copy_24dp
import eu.tintera.plume.resources.decimal_number
import eu.tintera.plume.resources.delete_sweep_24dp
import eu.tintera.plume.resources.expand_more_24dp
import eu.tintera.plume.resources.media_text_copied
import eu.tintera.plume.resources.menu_copy
import eu.tintera.plume.resources.refresh_24dp
import eu.tintera.plume.resources.send_24dp
import eu.tintera.plume.resources.settings_ai_task_auto
import eu.tintera.plume.resources.settings_ai_task_not_ready
import eu.tintera.plume.resources.smart_toy_24dp
import eu.tintera.plume.resources.stop_24dp
import eu.tintera.plume.resources.summary_details_elapsed
import eu.tintera.plume.resources.summary_model_missing
import kotlin.math.roundToLong
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Náměty v prázdném rozhovoru: vtip, vysvětlení, překlad a tvorba — čtyři různé schopnosti modelu. */
private val SUGGESTIONS = listOf(
    Res.string.ai_playground_suggestion_joke,
    Res.string.ai_playground_suggestion_explain,
    Res.string.ai_playground_suggestion_translate,
    Res.string.ai_playground_suggestion_poem,
)

/**
 * Vyzkoušet model: volný rozhovor s modelem v zařízení a měření pod každou odpovědí.
 *
 * Vlastní jednoduchá obrazovka, ne konverzace: `ConversationScreen` i `MessageInput` stojí na
 * zprávách z TDLib a jejich parametry by se sem jen předávaly prázdné.
 */
@Composable
fun AiPlaygroundScreen(viewModel: AiPlaygroundViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val current by viewModel.current.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = settingsScrollBehavior()
    val clipboard = koinInject<TextClipboard>()
    val scope = rememberCoroutineScope()
    val copy: (String) -> Unit = { text ->
        scope.launch {
            clipboard.copy(text)
            if (!clipboard.confirmsCopy) snackbarHostState.showSnackbar(readString(Res.string.media_text_copied))
        }
    }

    val send = {
        if (draft.isNotBlank() && !state.generating) {
            viewModel.send(draft)
            draft = ""
        }
    }

    Scaffold(
        modifier = Modifier.settingsScroll(scrollBehavior),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SettingsTopBar(
                title = stringResource(Res.string.ai_playground_title),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
                actions = {
                    ModelChip(current = current, models = models, onOpen = viewModel::refreshModels, onChoose = viewModel::choose)
                    IconButton(
                        onClick = viewModel::newConversation,
                        enabled = state.turns.isNotEmpty(),
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(painterResource(Res.drawable.delete_sweep_24dp), contentDescription = stringResource(Res.string.ai_playground_new))
                    }
                },
            )
        },
        bottomBar = {
            PlaygroundInput(
                draft = draft,
                onDraftChange = { draft = it },
                generating = state.generating,
                onSend = send,
                onStop = viewModel::stop,
            )
        },
    ) { padding ->
        if (state.turns.isEmpty()) {
            IntroEmptyState(
                icon = Res.drawable.smart_toy_24dp,
                title = stringResource(Res.string.ai_playground_empty_title),
                text = stringResource(Res.string.ai_playground_empty_text),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxSize().padding(padding),
                action = {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        modifier = Modifier.widthIn(max = ContentWidth.Form),
                    ) {
                        SUGGESTIONS.forEach { suggestion ->
                            val text = stringResource(suggestion)
                            ExpressiveSuggestionChip(onClick = { viewModel.send(text) }, label = { Text(text) })
                        }
                    }
                },
            )
        } else {
            PlaygroundTurns(
                state = state,
                padding = padding,
                onCopy = copy,
                onRegenerate = viewModel::regenerate,
            )
        }
    }
}

/** Čip s modelem pro příští odpověď; nabídka zapnutých modelů a „Automaticky". */
@Composable
private fun ModelChip(
    current: CurrentModel?,
    models: List<PlaygroundModel>,
    onOpen: () -> Unit,
    onChoose: (ModelVariant?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val check: @Composable () -> Unit = { Icon(painterResource(Res.drawable.check_24dp), contentDescription = null) }
    Box {
        ExpressiveAssistChip(
            onClick = {
                onOpen()
                expanded = true
            },
            label = { Text(current?.let { it.name ?: stringResource(it.provider.title) }.orEmpty()) },
            trailingIcon = {
                Icon(painterResource(Res.drawable.expand_more_24dp), contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
            },
        )
        ExpressiveMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            groups = listOf(
                listOf(
                    MenuAction(
                        label = stringResource(Res.string.settings_ai_task_auto),
                        trailing = check.takeIf { current?.automatic == true },
                        onClick = { onChoose(null) },
                    ),
                ),
                models.map { model ->
                    val name = model.name ?: stringResource(model.provider.title)
                    MenuAction(
                        // Nepřipravený model jde vybrat taky — odpověď pak řekne, že chybí, a kde ho stáhnout.
                        label = if (!model.ready) stringResource(Res.string.settings_ai_task_not_ready, name) else name,
                        trailing = check.takeIf { current?.automatic == false && current.variant == model.variant },
                        onClick = { onChoose(model.variant) },
                    )
                },
            ),
        )
    }
}

@Composable
private fun PlaygroundTurns(
    state: PlaygroundState,
    padding: PaddingValues,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit,
) {
    // Odspodu (reverseLayout), ať se při psaní odpovědi drží u dna jako konverzace.
    val turns = state.turns.asReversed()
    LazyColumn(
        reverseLayout = true,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        turns.forEachIndexed { index, turn ->
            val last = index == 0
            item(key = "answer-${turn.id}") {
                AnswerBubble(
                    answer = turn.answer,
                    onCopy = { onCopy(turn.answer.text) },
                    onRegenerate = onRegenerate.takeIf { last && !turn.answer.running },
                )
            }
            item(key = "question-${turn.id}") { QuestionBubble(turn.question) }
            // Předěl nad prvním kolem, které model ještě viděl; jen když nad ním nějaké je.
            if (turn.id == state.rememberedFromId && index < turns.lastIndex) {
                item(key = "forgotten") { ForgottenDivider() }
            }
        }
    }
}

@Composable
private fun QuestionBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(GROUP_CORNER, GROUP_CORNER, INNER_CORNER, GROUP_CORNER),
            modifier = Modifier.widthIn(max = ContentWidth.Bubble).padding(start = 48.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AnswerBubble(answer: PlaygroundAnswer, onCopy: () -> Unit, onRegenerate: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(end = 48.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            // Řádek na stránce Nastavení: `surfaceBright` na `surfaceContainer` (viz CLAUDE.md, vrstvení).
            color = MaterialTheme.colorScheme.surfaceBright,
            shape = RoundedCornerShape(GROUP_CORNER, GROUP_CORNER, GROUP_CORNER, INNER_CORNER),
            modifier = Modifier.widthIn(max = ContentWidth.Bubble),
        ) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                when {
                    answer.phase == AnswerPhase.ModelMissing -> Text(
                        stringResource(Res.string.summary_model_missing),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    answer.phase == AnswerPhase.Failed -> Text(
                        answer.error?.resolve().orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    answer.text.isBlank() && answer.running -> LoadingIndicator(Modifier.size(32.dp))
                    else -> Text(answer.text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (!answer.running) AnswerFooter(answer, onCopy, onRegenerate)
    }
}

/** Model a měření drobným písmem — kvůli tomu hřiště je — a akce nad odpovědí. */
@Composable
private fun AnswerFooter(answer: PlaygroundAnswer, onCopy: () -> Unit, onRegenerate: (() -> Unit)?) {
    val parts = buildList {
        val provider = answer.provider
        if (provider != null) add(answer.modelName ?: stringResource(provider.title))
        answer.firstWordMs?.let { add(stringResource(Res.string.ai_playground_first_word, seconds(it))) }
        answer.totalMs?.let { add(stringResource(Res.string.summary_details_elapsed, seconds(it))) }
        answer.tokensPerSecond?.let { add(stringResource(Res.string.ai_playground_speed, oneDecimal(it))) }
        if (answer.truncated) add(stringResource(Res.string.ai_playground_truncated))
        if (answer.phase == AnswerPhase.Stopped) add(stringResource(Res.string.ai_playground_stopped))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            parts.joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false).padding(start = 4.dp),
        )
        if (answer.text.isNotBlank()) {
            FooterAction(Res.string.menu_copy, onCopy) { Icon(painterResource(Res.drawable.content_copy_24dp), contentDescription = it, modifier = Modifier.size(18.dp)) }
        }
        if (onRegenerate != null) {
            FooterAction(Res.string.ai_playground_regenerate, onRegenerate) { Icon(painterResource(Res.drawable.refresh_24dp), contentDescription = it, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun FooterAction(label: StringResource, onClick: () -> Unit, icon: @Composable (String) -> Unit) {
    IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(36.dp)) {
        icon(stringResource(label))
    }
}

@Composable
private fun ForgottenDivider() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        HorizontalDivider(Modifier.weight(1f))
        Text(
            stringResource(Res.string.ai_playground_forgotten),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(Modifier.weight(1f))
    }
}

@Composable
private fun PlaygroundInput(
    draft: String,
    onDraftChange: (String) -> Unit,
    generating: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            // Popis insetů, ne čísla z paddingu (viz Gotchas v CLAUDE.md).
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        FormTextField(
            value = draft,
            onValueChange = onDraftChange,
            placeholder = { Text(stringResource(Res.string.ai_playground_input_hint)) },
            maxLines = 5,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .weight(1f)
                // Enter na klávesnici (desktop, telefon s klávesnicí) odešle, Shift+Enter zalomí řádek.
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Enter && !event.isShiftPressed) {
                        if (event.type == KeyEventType.KeyDown) onSend()
                        true
                    } else {
                        false
                    }
                },
        )
        if (generating) {
            FilledTonalIconButton(onClick = onStop, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(Res.drawable.stop_24dp), contentDescription = stringResource(Res.string.ai_playground_stop))
            }
        } else {
            FilledIconButton(onClick = onSend, enabled = draft.isNotBlank(), shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(Res.drawable.send_24dp), contentDescription = stringResource(Res.string.ai_playground_send))
            }
        }
    }
}

@Composable
private fun seconds(ms: Long): String =
    stringResource(Res.string.decimal_number, (ms / 1000).toString(), ((ms % 1000) / 100).toString())

@Composable
private fun oneDecimal(value: Double): String {
    val tenths = (value * 10).roundToLong()
    return stringResource(Res.string.decimal_number, (tenths / 10).toString(), (tenths % 10).toString())
}
