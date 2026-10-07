package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.settings.GestureHint
import eu.tintera.plume.core.settings.RecordMode
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.gesture_hint_got_it
import eu.tintera.plume.resources.gesture_hint_record_title
import eu.tintera.plume.resources.gesture_hint_record_video_note
import eu.tintera.plume.resources.gesture_hint_record_voice
import eu.tintera.plume.resources.gesture_hint_reveal_times
import eu.tintera.plume.resources.gesture_hint_reveal_times_title
import eu.tintera.plume.resources.gesture_hint_send_options
import eu.tintera.plume.resources.gesture_hint_send_options_effects
import eu.tintera.plume.resources.gesture_hint_send_options_title
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Co na obrazovce právě je, z pohledu nápověd gest. Čistá data, aby pravidlo šlo otestovat bez
 * Compose ([nextGestureHint]).
 */
internal data class GestureHintContext(
    /**
     * Nic jiného nečeká na uživatele: žádná nabídka nad zprávou, výběr, hledání, úprava, sheet,
     * nahrávání. Nápověda přes rozdělanou věc by ji zakryla — a uživatel by ji zavřel ťuknutím,
     * aniž by ji četl.
     */
    val idle: Boolean,
    /** Tlačítko je odeslání hotové zprávy a podržení nabídne volby. */
    val sendOptions: Boolean,
    /** Tlačítko nahrává (prázdné pole). */
    val recording: Boolean,
    /** V konverzaci je co odkrývat. */
    val hasMessages: Boolean,
)

/** Podmínka, za které nápověda dává smysl — bez ohledu na to, jestli ji uživatel už viděl. */
internal fun GestureHintContext.allows(hint: GestureHint): Boolean = idle && when (hint) {
    GestureHint.SendOptions -> sendOptions
    GestureHint.HoldToRecord -> recording
    GestureHint.RevealTimes -> hasMessages
}

/**
 * Pořadí, ve kterém se nápovědy nabízejí. Volby odeslání první: ukazují se jen při psaní, tedy
 * vzácně, a kdo zrovna píše, podržení hned využije. Odkrývání časů poslední — týká se čtení,
 * a tak počká, až se vysvětlí to, co se týká psaní.
 */
private val HINT_PRIORITY = listOf(GestureHint.SendOptions, GestureHint.HoldToRecord, GestureHint.RevealTimes)

/** Další nápověda k ukázání, nebo `null`. */
internal fun nextGestureHint(seen: Set<GestureHint>, context: GestureHintContext): GestureHint? =
    HINT_PRIORITY.firstOrNull { it !in seen && context.allows(it) }

/**
 * Nápověda, která se v konverzaci ukazuje. Jedna za návštěvu chatu a až po chvíli klidu
 * ([HINT_DELAY_MS]): vyskočit hned po otevření by znamenalo přerušit první pohled do chatu.
 * Viděná je od chvíle, kdy se UKÁZALA — kdo ji zavře, aniž ji dočte, ji znovu vidět nechce.
 *
 * Co je s tlačítkem odeslání, ví jen vstupní pole, a tak ho sem zapisuje samo
 * ([sendOptions], [recording]). Obrazovka nic z toho v kompozici nečte: podmínky se hlídají
 * `snapshotFlow` v [rememberGestureHints], jinak by se kvůli nápovědě skládala celá konverzace.
 */
@Stable
internal class GestureHints(private val onSeen: (GestureHint) -> Unit) {
    var showing by mutableStateOf<GestureHint?>(null)
        internal set

    /** Vstupní pole: tlačítko odesílá hotovou zprávu a podržení nabídne volby. */
    var sendOptions by mutableStateOf(false)

    /** Vstupní pole: tlačítko nahrává. */
    var recording by mutableStateOf(false)

    /** Uživatel nápovědu zavřel (tlačítkem, ťuknutím vedle). */
    fun dismiss() {
        showing = null
    }

    /** Gesto se použilo: nápověda k němu už nemá co vysvětlovat, ani příště. */
    fun used(hint: GestureHint) {
        onSeen(hint)
        if (showing == hint) showing = null
    }
}

/**
 * [seen] a [context] se čtou ze snapshot stavu (a ne z hodnot v kompozici), takže změna
 * podmínek nápovědu ukáže i schová bez rekompozice volajícího.
 */
@Composable
internal fun rememberGestureHints(
    chatId: Long,
    seen: () -> Set<GestureHint>,
    context: (GestureHints) -> GestureHintContext,
    onSeen: (GestureHint) -> Unit,
): GestureHints {
    val currentOnSeen by rememberUpdatedState(onSeen)
    val currentSeen by rememberUpdatedState(seen)
    val currentContext by rememberUpdatedState(context)
    val hints = remember(chatId) { GestureHints { currentOnSeen(it) } }
    // Přežije otočení displeje: jinak by se po otočení ukázala další nápověda hned po první.
    var shownThisVisit by rememberSaveable(chatId) { mutableStateOf(false) }
    LaunchedEffect(hints) {
        launch {
            // Podmínka přestala platit (začal psát, otevřel nabídku): bublina by visela nad
            // něčím jiným.
            snapshotFlow { hints.showing?.takeUnless { currentContext(hints).allows(it) } }
                .collect { if (it != null) hints.showing = null }
        }
        snapshotFlow {
            if (shownThisVisit || hints.showing != null) null else nextGestureHint(currentSeen(), currentContext(hints))
        }.collectLatest { next ->
            if (next == null) return@collectLatest
            // Každá změna podmínek čekání zruší a začne znovu: ukáže se až nad obrazovkou,
            // která chvíli stojí.
            delay(HINT_DELAY_MS)
            shownThisVisit = true
            hints.showing = next
            currentOnSeen(next)
        }
    }
    return hints
}

/**
 * Kotva nápovědy: obsah, nad kterým (vedle kterého) se při [hint] ukáže `RichTooltip` se šipkou.
 * `null` = nic. Trvalá nápověda, zavře ji „Rozumím" nebo ťuknutí vedle; nic se nespotřebuje —
 * tooltip není fokusovatelný a dotyky propouští.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GestureHintBox(
    hint: GestureHint?,
    position: TooltipAnchorPosition,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    recordMode: RecordMode = RecordMode.Voice,
    effects: Boolean = false,
    content: @Composable () -> Unit,
) {
    val state = rememberTooltipState(isPersistent = true)
    // Text posledního zobrazení drží bublinu při odjezdu: v tu chvíli je už `hint` prázdný.
    var shown by remember { mutableStateOf(hint) }
    if (hint != null) shown = hint
    LaunchedEffect(hint) {
        if (hint != null) state.show() else state.dismiss()
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(position),
        tooltip = {
            val current = shown ?: return@TooltipBox
            RichTooltip(
                title = { Text(stringResource(current.title)) },
                action = {
                    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.gesture_hint_got_it)) }
                },
                caretShape = TooltipDefaults.caretShape(),
            ) {
                Text(
                    stringResource(current.text(recordMode, effects)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        state = state,
        modifier = modifier,
        onDismissRequest = onDismiss,
        enableUserInput = false,
        hasAction = true,
    ) {
        Box { content() }
    }
}

private val GestureHint.title
    get() = when (this) {
        GestureHint.RevealTimes -> Res.string.gesture_hint_reveal_times_title
        GestureHint.HoldToRecord -> Res.string.gesture_hint_record_title
        GestureHint.SendOptions -> Res.string.gesture_hint_send_options_title
    }

private fun GestureHint.text(recordMode: RecordMode, effects: Boolean) = when (this) {
    GestureHint.RevealTimes -> Res.string.gesture_hint_reveal_times
    GestureHint.HoldToRecord -> when (recordMode) {
        RecordMode.Voice -> Res.string.gesture_hint_record_voice
        RecordMode.VideoNote -> Res.string.gesture_hint_record_video_note
    }
    GestureHint.SendOptions ->
        if (effects) Res.string.gesture_hint_send_options_effects else Res.string.gesture_hint_send_options
}

/** Kolik ms musí obrazovka stát, než se nápověda ukáže. */
internal const val HINT_DELAY_MS = 1_500L

/**
 * Nápovědy pro konverzaci: co uživatel už viděl, bere z nastavení, podmínky ze stavu konverzace
 * a z [screenBusy] (sheety a nahrávání, které drží obrazovka sama).
 */
@Composable
internal fun rememberConversationGestureHints(
    viewModel: ConversationViewModel,
    state: State<ConversationState>,
    screenBusy: () -> Boolean,
): GestureHints {
    val seen = viewModel.seenGestureHints.collectAsStateWithLifecycle()
    return rememberGestureHints(
        chatId = viewModel.chatId,
        seen = { seen.value },
        context = { hints -> conversationHintContext(state.value, hints, screenBusy()) },
        onSeen = viewModel::markGestureHintSeen,
    )
}

internal fun conversationHintContext(state: ConversationState, hints: GestureHints, busy: Boolean) = GestureHintContext(
    // Až nad načteným chatem: nad prázdnou obrazovkou při načítání nemá bublina k čemu ukazovat.
    idle = !busy &&
        state.info != null &&
        state.info.restriction == null &&
        state.messageMenu == null &&
        state.selection == null &&
        state.search == null &&
        state.editing == null &&
        state.selectingText == null &&
        state.reactionDetails == null &&
        state.messageInfo == null &&
        state.translation == null &&
        state.pendingDelete == null &&
        state.pendingPin == null &&
        state.scheduledMessages == null &&
        state.contactPicker == null &&
        state.inline == null &&
        state.mentionSuggestions == null &&
        !state.commandsOpen,
    sendOptions = hints.sendOptions,
    recording = hints.recording,
    hasMessages = state.messages.isNotEmpty(),
)

/**
 * Kotva nápovědy k odkrývání časů: neviditelný bod u pravého okraje seznamu, bublina vyjede
 * vlevo od něj — tam, odkud časy tahem přijedou. Vlastní funkce, aby změnu nápovědy četla jen
 * ona, ne celá obrazovka.
 */
@Composable
internal fun RevealTimesHintAnchor(hints: GestureHints, modifier: Modifier = Modifier) {
    // Zarovnání nese vlastní `Box`: `TooltipBox` ho rodiči nepředá a kotva skončila v levém
    // horním rohu seznamu (změřeno: bounds 0,310 místo pravého okraje).
    // Kousek od kraje, jinak by šipka bubliny ležela za okrajem obrazovky.
    Box(modifier.padding(end = 16.dp)) {
        GestureHintBox(
            hint = hints.showing?.takeIf { it == GestureHint.RevealTimes },
            position = TooltipAnchorPosition.Start,
            onDismiss = hints::dismiss,
        ) {
            Spacer(Modifier.size(1.dp))
        }
    }
}

/**
 * Nápověda u tlačítka odeslání (podržení pro volby, podržení pro nahrávání). Pole sem hlásí, co
 * tlačítko právě dělá; po odchodu pole (výběr zpráv, bot bez spuštění) se hlášení vynuluje,
 * jinak by v [GestureHints] zůstalo viset.
 */
@Composable
internal fun ButtonGestureHint(
    hints: GestureHints?,
    sendOptions: Boolean,
    recording: Boolean,
    recordMode: RecordMode,
    effects: Boolean,
    content: @Composable () -> Unit,
) {
    if (hints == null) {
        content()
        return
    }
    SideEffect {
        hints.sendOptions = sendOptions
        hints.recording = recording
    }
    DisposableEffect(hints) {
        onDispose {
            hints.sendOptions = false
            hints.recording = false
        }
    }
    GestureHintBox(
        hint = hints.showing?.takeIf { it != GestureHint.RevealTimes },
        position = TooltipAnchorPosition.Above,
        onDismiss = hints::dismiss,
        recordMode = recordMode,
        effects = effects,
        content = content,
    )
}
