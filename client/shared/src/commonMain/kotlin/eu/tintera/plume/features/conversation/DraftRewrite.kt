package eu.tintera.plume.features.conversation

import org.jetbrains.compose.resources.painterResource
import eu.tintera.plume.resources.tone_rude
import eu.tintera.plume.resources.tone_irritated
import eu.tintera.plume.resources.tone_fine
import eu.tintera.plume.resources.tone_checking
import eu.tintera.plume.resources.mood_24dp
import eu.tintera.plume.resources.info_24dp
import eu.tintera.plume.core.ai.toneVerdictOf
import eu.tintera.plume.core.ai.generateAnswer
import eu.tintera.plume.core.ai.ToneAnswer
import eu.tintera.plume.core.ai.tonePrompt
import eu.tintera.plume.core.ai.parseTone
import eu.tintera.plume.core.ai.ToneVerdict
import eu.tintera.plume.core.ai.ToneIssue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Icon
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import eu.tintera.plume.core.telegram.AiTextRepository
import eu.tintera.plume.core.telegram.DraftChange
import eu.tintera.plume.core.telegram.DraftDiff
import eu.tintera.plume.core.telegram.TextStyleOption
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.telegram.localizedLanguageName
import eu.tintera.plume.resources.rewrite_on_device
import eu.tintera.plume.resources.rewrite_on_server
import eu.tintera.plume.resources.rewrite_server_disclaimer
import eu.tintera.plume.resources.rewrite_server_failed
import eu.tintera.plume.resources.rewrite_server_fix
import eu.tintera.plume.resources.rewrite_server_translate
import eu.tintera.plume.resources.rewrite_server_translate_to
import eu.tintera.plume.resources.rewrite_server_emoji
import org.jetbrains.compose.resources.StringResource
import eu.tintera.plume.core.ai.MAX_REWRITE_CHARS
import eu.tintera.plume.core.ai.OnDeviceAi
import eu.tintera.plume.core.ai.OnDeviceAiException
import eu.tintera.plume.core.ai.OnDeviceAiStatus
import eu.tintera.plume.core.ai.RewriteStyle
import eu.tintera.plume.core.ai.cleanRewrite
import eu.tintera.plume.core.ai.isReportable
import eu.tintera.plume.core.ai.rewritePrompt
import eu.tintera.plume.core.ai.userMessage
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.common_close
import eu.tintera.plume.resources.rewrite_disclaimer
import eu.tintera.plume.resources.rewrite_failed
import eu.tintera.plume.resources.rewrite_fix
import eu.tintera.plume.resources.rewrite_pick
import eu.tintera.plume.resources.rewrite_polite
import eu.tintera.plume.resources.rewrite_shorter
import eu.tintera.plume.resources.rewrite_title
import eu.tintera.plume.resources.rewrite_unchanged
import eu.tintera.plume.resources.rewrite_use
import eu.tintera.plume.resources.summary_model_missing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Je v poli co přepsat? Prázdné ne, dlouhé taky ne — výstup modelu má strop. */
internal fun rewritable(text: String): Boolean = text.isNotBlank() && text.length <= MAX_REWRITE_CHARS

/** Kontrola tónu konceptu ([DraftRewriteViewModel.checkTone]). */
sealed interface ToneState {
    /** Neví se (ještě neběželo, model není, nebo si model nebyl jistý) — nic se neukazuje. */
    data object Unknown : ToneState

    data object Checking : ToneState

    data class Known(val verdict: ToneVerdict) : ToneState
}

/**
 * Čím se přepisuje: modelem v zařízení ([Local]), nebo na serveru Telegramu — opravou chyb
 * ([ServerFix], `fixTextWithAi`), jeho stylem psaní ([ServerStyle]), překladem
 * ([ServerTranslate]) či doplněním emoji ([ServerEmoji]); poslední tři jsou `composeTextWithAi`.
 */
sealed interface RewriteChoice {
    data class Local(val style: RewriteStyle) : RewriteChoice

    data object ServerFix : RewriteChoice

    data class ServerStyle(val style: TextStyleOption) : RewriteChoice

    /** Překlad konceptu do jazyka [languageCode] (kód z `TRANSLATION_LANGUAGES`). */
    data class ServerTranslate(val languageCode: String) : RewriteChoice

    data object ServerEmoji : RewriteChoice

    /** Text odchází na server Telegramu (sheet to pod výsledkem říká). */
    val onServer: Boolean get() = this !is Local
}

/** Stav přepisu rozepsané zprávy. */
sealed interface RewriteState {
    /** Nic neběží; sheet čeká na výběr stylu. */
    data object Idle : RewriteState

    /** Model píše; [text] je zatím napsaný kus (může být prázdný). */
    data class Working(val choice: RewriteChoice, val text: String) : RewriteState

    /**
     * Hotovo. [text] je očištěný výstup ([cleanRewrite]); u opravy na serveru navíc [diff] —
     * tentýž text i se smazanými kusy a vyznačenými změnami.
     */
    data class Done(val choice: RewriteChoice, val text: String, val diff: DraftDiff? = null) : RewriteState

    data object ModelMissing : RewriteState

    data class Failed(val choice: RewriteChoice, val reason: UiText) : RewriteState
}

/**
 * Přepis rozepsané zprávy modelem v zařízení („kratší", „zdvořileji", „opravit překlepy").
 *
 * Samostatný ViewModel jako [ChatSummaryViewModel]: experimentální doplněk, který se dá celý
 * vyjmout. **Nic neodesílá a pole nemění sám** — výsledek jen ukáže a do pole ho vloží až
 * ťuknutí na „Použít" (viz [DraftRewriteSheet]).
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DraftRewriteViewModel(
    private val ai: OnDeviceAi,
    private val settings: AppSettings,
    private val aiText: AiTextRepository,
) : ViewModel() {
    private val logger = Logger.withTag("DraftRewrite")

    /** Styly psaní od serveru Telegramu; prázdné = nabízí se jen oprava chyb. */
    val serverStyles: StateFlow<List<TextStyleOption>> = aiText.styles

    /** Přepis modelem v zařízení: jen se zapnutou AI a na zařízení, které model má nebo mít může. */
    val available: StateFlow<Boolean> = combine(
        settings.onDeviceAiEnabled,
        settings.onDeviceAiVariant.flatMapLatest { variant ->
            // Pojistka jako u shrnutí: nepodařilo se zjistit = nenabízet.
            flow { emit(ai.status(variant) != OnDeviceAiStatus.Unsupported) }.catch { emit(false) }
        },
    ) { enabled, supported -> enabled && supported }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _state = MutableStateFlow<RewriteState>(RewriteState.Idle)
    val state: StateFlow<RewriteState> = _state.asStateFlow()

    private var job: Job? = null

    private val _tone = MutableStateFlow<ToneState>(ToneState.Unknown)

    /** Jak koncept vyzní ([checkTone]); běží sám po otevření sheetu. */
    val tone: StateFlow<ToneState> = _tone.asStateFlow()

    private var toneJob: Job? = null

    /**
     * Kontrola tónu konceptu („nezní to podrážděně?"). Jen klasifikace — nic nepřepisuje.
     * Model, který není připravený, se neptá a chyba znamená mlčet: kontrola je doplněk sheetu,
     * ne jeho účel. Přepis ji ruší ([rewrite]), aby se dva dotazy na model nepředbíhaly.
     */
    fun checkTone(text: String) {
        toneJob?.cancel()
        _tone.value = ToneState.Checking
        toneJob = viewModelScope.launch {
            val variant = settings.onDeviceAiVariant.value
            if (ai.status(variant) != OnDeviceAiStatus.Ready) {
                _tone.value = ToneState.Unknown
                return@launch
            }
            val verdict = try {
                ai.generateAnswer(
                    variant,
                    ToneAnswer::class,
                    prompt = { structured -> tonePrompt(text, structured) },
                    fromStructured = ::toneVerdictOf,
                    fromText = ::parseTone,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.i(e) { "Tón se nepodařilo posoudit" }
                null
            }
            _tone.value = verdict?.let { ToneState.Known(it) } ?: ToneState.Unknown
        }
    }

    /** Přepíše [text] volbou [choice]. Vždy z PŮVODNÍHO textu, ne z předchozího přepisu. */
    fun rewrite(text: String, choice: RewriteChoice) {
        job?.cancel()
        if (_tone.value == ToneState.Checking) {
            toneJob?.cancel()
            _tone.value = ToneState.Unknown
        }
        _state.value = RewriteState.Working(choice, "")
        job = viewModelScope.launch {
            when (choice) {
                is RewriteChoice.Local -> rewriteLocally(text, choice)
                RewriteChoice.ServerFix -> aiText.fix(text).fold(
                    onSuccess = { fixed -> _state.value = RewriteState.Done(choice, fixed.text.trim(), fixed.diff) },
                    onFailure = { failure -> serverFailed(choice, failure) },
                )
                is RewriteChoice.ServerStyle -> composeOnServer(choice) { aiText.compose(text, styleName = choice.style.name) }
                is RewriteChoice.ServerTranslate ->
                    composeOnServer(choice) { aiText.compose(text, translateTo = choice.languageCode) }
                RewriteChoice.ServerEmoji -> composeOnServer(choice) { aiText.compose(text, addEmojis = true) }
            }
        }
    }

    /** Přepis na serveru (`composeTextWithAi`): prázdný výsledek je selhání, ne „nic se nezměnilo". */
    private suspend fun composeOnServer(choice: RewriteChoice, request: suspend () -> Result<String>) {
        request().fold(
            onSuccess = { result ->
                _state.value = if (result.isBlank()) {
                    RewriteState.Failed(choice, UiText(Res.string.rewrite_failed))
                } else {
                    RewriteState.Done(choice, result.trim())
                }
            },
            onFailure = { failure -> serverFailed(choice, failure) },
        )
    }

    /** Selhání serveru: věta pro uživatele (`AICOMPOSE_FLOOD_PREMIUM` = došly bezplatné úpravy). */
    private fun serverFailed(choice: RewriteChoice, failure: Throwable) {
        logger.w(failure) { "Úprava textu na serveru se nepovedla" }
        _state.value = RewriteState.Failed(choice, failure.userMessage(Res.string.rewrite_server_failed))
    }

    private suspend fun rewriteLocally(text: String, choice: RewriteChoice.Local) {
        val style = choice.style
        val variant = settings.onDeviceAiVariant.value
        if (ai.status(variant) != OnDeviceAiStatus.Ready) {
            _state.value = RewriteState.ModelMissing
            return
        }
        try {
            var last = ""
            ai.generate(variant, rewritePrompt(text, style)).collect { chunk ->
                last = chunk.text
                _state.value = RewriteState.Working(choice, cleanRewrite(chunk.text))
            }
            val result = cleanRewrite(last)
            _state.value = if (result.isBlank()) {
                logger.w { "Model přepisu nenapsal žádný text" }
                RewriteState.Failed(choice, UiText(Res.string.rewrite_failed))
            } else {
                RewriteState.Done(choice, result)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: OnDeviceAiException) {
            if (e.isReportable) logger.e(e) { "Přepis se nepovedl" } else logger.w(e) { "Přepis se nepovedl" }
            _state.value = RewriteState.Failed(choice, e.userMessage())
        } catch (e: Exception) {
            logger.e(e) { "Přepis se nepovedl" }
            _state.value = RewriteState.Failed(choice, UiText(Res.string.rewrite_failed))
        }
    }

    /** Zavření sheetu: rozpracovaný přepis se zahodí. */
    fun dismiss() {
        job?.cancel()
        job = null
        toneJob?.cancel()
        toneJob = null
        _state.value = RewriteState.Idle
        _tone.value = ToneState.Unknown
    }
}

/**
 * Přepis konceptu pro obrazovku konverzace: jestli se nabízí a jestli je sheet otevřený.
 *
 * Drží ho vstupní pole (`MessageInput`), které má koncept v ruce — ne `ConversationScreen`,
 * který je v dexu na hraně (viz „Gotchas" v CLAUDE.md).
 */
@Stable
internal class DraftRewrite(
    val viewModel: DraftRewriteViewModel,
    private val available: State<Boolean>,
) {
    /** Text, který se přepisuje; `null` = sheet zavřený. */
    var source by mutableStateOf<String?>(null)
        private set

    /** Smí text na server Telegramu? Ne v tajném chatu; nastavuje vstupní pole. */
    var serverAllowed by mutableStateOf(true)
        internal set

    /** Přepis modelem v zařízení se nabízí (zapnutá AI, podporované zařízení). */
    val localAvailable: Boolean get() = available.value

    /**
     * Nabídnout „Zkontrolovat a přepsat…"? Model v zařízení, nebo server — úpravy od serveru
     * tak má i telefon bez AI v zařízení. Jestli je v poli co přepsat ([rewritable]), rozhoduje
     * pole samo.
     */
    val offered: Boolean get() = available.value || serverAllowed

    fun open(text: String) {
        source = text
    }

    fun close() {
        source = null
        viewModel.dismiss()
    }
}

/**
 * Bez id chatu v klíči: ViewModel patří položce navigace konverzace (vlastní úložiště, viz
 * „Zásobník pro každou sekci"), a stav nese jen otevřený sheet, ne nic z chatu.
 */
@Composable
internal fun rememberDraftRewrite(serverAllowed: Boolean): DraftRewrite {
    val viewModel: DraftRewriteViewModel = koinViewModel(key = "draft-rewrite")
    val available = viewModel.available.collectAsStateWithLifecycle()
    return remember(viewModel) { DraftRewrite(viewModel, available) }.also { it.serverAllowed = serverAllowed }
}

/**
 * Sheet přepisu: výběr stylu, výsledek a „Použít". Styl se volí ťuknutím a přepis běží hned;
 * jiný styl přepíše znovu PŮVODNÍ text. Původní text je nahoře kvůli porovnání.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DraftRewriteSheet(rewrite: DraftRewrite, onUse: (String) -> Unit) {
    val source = rewrite.source ?: return
    val state by rewrite.viewModel.state.collectAsStateWithLifecycle()
    val tone by rewrite.viewModel.tone.collectAsStateWithLifecycle()
    // Tón se zjišťuje sám po otevření: je to dotaz „jak to zní", který si uživatel otevřením
    // sheetu položil. Jednou na text, ne při každém složení.
    LaunchedEffect(source) { rewrite.viewModel.checkTone(source) }
    val serverStyles by rewrite.viewModel.serverStyles.collectAsStateWithLifecycle()
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    // Výběr jazyka překladu je sheet NAD tímhle, jako u překladu zprávy.
    var pickingLanguage by remember { mutableStateOf(false) }
    val choice = when (val s = state) {
        is RewriteState.Working -> s.choice
        is RewriteState.Done -> s.choice
        is RewriteState.Failed -> s.choice
        else -> null
    }

    ModalBottomSheet(onDismissRequest = rewrite::close, sheetState = sheetState) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        ) {
            Text(stringResource(Res.string.rewrite_title), style = MaterialTheme.typography.titleMedium)
            Text(
                source,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 6,
            )
            ToneLine(tone)
            /*
             * Dvě řady: model v zařízení a server Telegramu. Odděleně a s nadpisem, protože se
             * liší tím podstatným — kam text odchází. Kdo AI v zařízení nemá, vidí jen server;
             * v tajném chatu jen zařízení.
             */
            val local = rewrite.localAvailable
            if (local) {
                ChoiceRow(title = Res.string.rewrite_on_device.takeIf { rewrite.serverAllowed }) {
                    RewriteStyle.entries.forEach { option ->
                        val option = RewriteChoice.Local(option)
                        FilterChip(
                            selected = option == choice,
                            onClick = { rewrite.viewModel.rewrite(source, option) },
                            label = { Text(stringResource(option.style.label)) },
                        )
                    }
                }
            }
            if (rewrite.serverAllowed) {
                ChoiceRow(title = Res.string.rewrite_on_server.takeIf { local }) {
                    FilterChip(
                        selected = choice == RewriteChoice.ServerFix,
                        onClick = { rewrite.viewModel.rewrite(source, RewriteChoice.ServerFix) },
                        label = { Text(stringResource(Res.string.rewrite_server_fix)) },
                    )
                    // Překlad: čip nese vybraný jazyk; ťuknutí vždy otevře výběr, i k překladu
                    // do jiného jazyka.
                    val translate = choice as? RewriteChoice.ServerTranslate
                    FilterChip(
                        selected = translate != null,
                        onClick = { pickingLanguage = true },
                        label = {
                            Text(
                                translate?.let { stringResource(Res.string.rewrite_server_translate_to, languageName(it.languageCode)) }
                                    ?: stringResource(Res.string.rewrite_server_translate),
                            )
                        },
                    )
                    FilterChip(
                        selected = choice == RewriteChoice.ServerEmoji,
                        onClick = { rewrite.viewModel.rewrite(source, RewriteChoice.ServerEmoji) },
                        label = { Text(stringResource(Res.string.rewrite_server_emoji)) },
                    )
                    serverStyles.forEach { style ->
                        val option = RewriteChoice.ServerStyle(style)
                        FilterChip(
                            selected = option == choice,
                            onClick = { rewrite.viewModel.rewrite(source, option) },
                            label = { Text(style.title) },
                        )
                    }
                }
            }

            when (val s = state) {
                RewriteState.Idle -> Text(
                    stringResource(Res.string.rewrite_pick),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is RewriteState.Working -> RewriteResult(s.text, working = true)
                is RewriteState.Done -> {
                    RewriteResult(s.text, working = false, diff = s.diff)
                    if (s.text == source.trim()) {
                        Text(
                            stringResource(Res.string.rewrite_unchanged),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                RewriteState.ModelMissing -> Text(
                    stringResource(Res.string.summary_model_missing),
                    style = MaterialTheme.typography.bodyLarge,
                )
                is RewriteState.Failed -> {
                    Text(stringResource(Res.string.rewrite_failed), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        s.reason.resolve(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = rewrite::close) { Text(stringResource(Res.string.common_close)) }
                val done = state as? RewriteState.Done
                Button(
                    onClick = {
                        done?.let { onUse(it.text) }
                        rewrite.close()
                    },
                    enabled = done != null && done.text != source.trim(),
                ) { Text(stringResource(Res.string.rewrite_use)) }
            }

            // Kam text šel, podle toho, čím se přepisovalo; před první volbou podle toho, co
            // sheet nabízí (bez AI v zařízení jen server).
            val onServer = choice?.onServer ?: !local
            Text(
                stringResource(if (onServer) Res.string.rewrite_server_disclaimer else Res.string.rewrite_disclaimer),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (pickingLanguage) {
        TranslationLanguageSheet(
            selected = (choice as? RewriteChoice.ServerTranslate)?.languageCode.orEmpty(),
            onPick = { code ->
                pickingLanguage = false
                rewrite.viewModel.rewrite(source, RewriteChoice.ServerTranslate(code))
            },
            onDismiss = { pickingLanguage = false },
        )
    }
}

/** Název jazyka v jazyce aplikace (od systému); co systém nezná, ukáže se kódem. */
private fun languageName(code: String): String = localizedLanguageName(code) ?: code

/** Řada voleb přepisu, nad ní případně nadpis (jen když jsou řady dvě). */
@Composable
private fun ChoiceRow(title: StringResource?, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        title?.let {
            Text(
                stringResource(it),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}

/**
 * Změny z opravy na serveru přímo v textu: vložené a nahrazené na podkladu `primaryContainer`,
 * smazané přeškrtnuté chybovou barvou. Rozsahy jsou v UTF-16 jako u entit; mimo text se oříznou.
 */
@Composable
private fun diffText(diff: DraftDiff): AnnotatedString {
    val added = SpanStyle(
        background = MaterialTheme.colorScheme.primaryContainer,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    val removed = SpanStyle(
        color = MaterialTheme.colorScheme.error,
        textDecoration = TextDecoration.LineThrough,
    )
    return buildAnnotatedString {
        append(diff.text)
        diff.changes.forEach { change ->
            val start = change.offset.coerceIn(0, diff.text.length)
            val end = (change.offset + change.length).coerceIn(start, diff.text.length)
            addStyle(if (change.kind == DraftChange.Kind.Delete) removed else added, start, end)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RewriteResult(text: String, working: Boolean, diff: DraftDiff? = null) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            if (text.isNotEmpty()) {
                // Barva výslovně: zděděná vyšla v sheetu šedá jako původní text nad kartou
                // (ověřeno na emulátoru) a výsledek, o kterém se rozhoduje, nesmí vypadat potlačeně.
                SelectionContainer {
                    if (diff != null && diff.changes.isNotEmpty()) {
                        Text(diffText(diff), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    } else {
                        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            if (working) LoadingIndicator(Modifier.size(32.dp))
        }
    }
}

/**
 * Jak koncept vyzní. U problému chybovou barvou (je to varování, které jde přejít), jinak
 * klidně. Neznámý tón (model nejistý, chyba) se neukazuje vůbec — radši mlčet než hádat.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ToneLine(tone: ToneState) {
    val verdict = (tone as? ToneState.Known)?.verdict
    if (tone == ToneState.Unknown) return
    val issue = (verdict as? ToneVerdict.Issue)?.issue
    val color = if (issue != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (tone == ToneState.Checking) {
            LoadingIndicator(Modifier.size(24.dp))
        } else {
            Icon(
                painter = painterResource(if (issue != null) Res.drawable.info_24dp else Res.drawable.mood_24dp),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            stringResource(
                when (issue) {
                    null -> if (tone == ToneState.Checking) Res.string.tone_checking else Res.string.tone_fine
                    ToneIssue.Irritated -> Res.string.tone_irritated
                    ToneIssue.Rude -> Res.string.tone_rude
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = color,
        )
    }
}

private val RewriteStyle.label
    get() = when (this) {
        RewriteStyle.Shorter -> Res.string.rewrite_shorter
        RewriteStyle.Polite -> Res.string.rewrite_polite
        RewriteStyle.Fix -> Res.string.rewrite_fix
    }
