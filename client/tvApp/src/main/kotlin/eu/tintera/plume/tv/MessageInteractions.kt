package eu.tintera.plume.tv

import eu.tintera.plume.core.telegram.MessageActions
import eu.tintera.plume.resources.delete_message_for_all
import eu.tintera.plume.resources.delete_message_for_self
import eu.tintera.plume.resources.delete_message_title
import eu.tintera.plume.resources.menu_delete
import eu.tintera.plume.resources.menu_edit
import eu.tintera.plume.resources.tv_delete_failed
import eu.tintera.plume.resources.tv_delete_for_all
import eu.tintera.plume.resources.tv_delete_for_me
import eu.tintera.plume.resources.tv_edit_failed
import eu.tintera.plume.resources.tv_edit_save
import eu.tintera.plume.resources.tv_edit_title
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.MessageReaction
import eu.tintera.plume.core.telegram.customEmojiIdOf
import eu.tintera.plume.core.ui.compactCountText
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.media_video
import eu.tintera.plume.resources.media_sticker
import eu.tintera.plume.resources.media_photo
import eu.tintera.plume.resources.conversation_voice_message
import eu.tintera.plume.resources.content_video_note
import eu.tintera.plume.resources.content_gif
import eu.tintera.plume.resources.content_file
import eu.tintera.plume.resources.content_audio
import eu.tintera.plume.resources.menu_forward
import eu.tintera.plume.resources.reactions_title
import eu.tintera.plume.resources.tv_compose_voice
import eu.tintera.plume.resources.tv_compose_voice_prompt
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.tv_action_reply
import eu.tintera.plume.resources.tv_action_show_quote
import eu.tintera.plume.resources.tv_message_you
import eu.tintera.plume.resources.tv_reply_hint
import eu.tintera.plume.resources.tv_reply_send
import eu.tintera.plume.resources.tv_reply_title
import eu.tintera.plume.resources.tv_compose_title
import eu.tintera.plume.resources.edit_24dp
import eu.tintera.plume.resources.format_quote_24dp
import androidx.tv.material3.Icon
import org.jetbrains.compose.resources.painterResource
import eu.tintera.plume.resources.tv_send_failed
import eu.tintera.plume.resources.tv_message_unsupported
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Co je nad zprávou otevřené: nabídka (podržení OK, [actionsFor]), psaní odpovědi ([replyTo])
 * a psaní nové zprávy bez citace ([composing], tlačítko pod seznamem).
 * Jeden držák na obrazovku — konverzace i komentáře pod příspěvkem mají svůj.
 */
@Stable
class MessageInteractions {
    var actionsFor by mutableStateOf<ChatMessage?>(null)
    var replyTo by mutableStateOf<ChatMessage?>(null)
    var composing by mutableStateOf(false)

    /** Vlastní zpráva, jejíž text se upravuje. */
    var editing by mutableStateOf<ChatMessage?>(null)

    /** Zpráva (album celé), kterou se uživatel chystá smazat, a co server dovolí. */
    var deleting by mutableStateOf<Pair<ChatMessage, MessageActions>?>(null)

    /** Zpráva (album celé), pro kterou se vybírá chat, kam ji přeposlat. */
    var forwarding by mutableStateOf<ChatMessage?>(null)

    /** Zpráva, u které se ukazuje, kdo reagoval. */
    var reactors by mutableStateOf<ChatMessage?>(null)
}

/**
 * Nabídka nad zprávou a dialog odpovědi. Obojí je `Dialog` (vlastní okno): fokus z něj šipkou
 * neuteče na zprávy pod ním a Zpět ho zavře, aniž by zavřel konverzaci. Po zavření se fokus
 * vrátí do hlavního okna na zprávu, ze které se nabídka otevřela.
 */
@Composable
fun MessageInteractionDialogs(state: ConversationState, interactions: MessageInteractions, chatTitle: String) {
    // Scope obrazovky, ne nabídky: nabídka se po výběru reakce hned zavře a s ní by skončila
    // i rozběhnutá reakce — požadavek by se zrušil a vrácení při chybě by neproběhlo.
    val scope = rememberCoroutineScope()
    interactions.actionsFor?.let { message ->
        MessageActionsDialog(
            state = state,
            message = message,
            scope = scope,
            chatTitle = chatTitle,
            onReply = {
                interactions.actionsFor = null
                interactions.replyTo = message
            },
            onJumpToQuote = message.replyToMessageId?.let { quoted ->
                {
                    interactions.actionsFor = null
                    scope.launch { state.jumpTo(quoted) }
                }
            },
            onEdit = {
                interactions.actionsFor = null
                interactions.editing = message
            },
            onDelete = { actions ->
                interactions.actionsFor = null
                interactions.deleting = message to actions
            },
            onForward = {
                interactions.actionsFor = null
                interactions.forwarding = message
            },
            onReactors = {
                interactions.actionsFor = null
                interactions.reactors = message
            },
            onDismiss = { interactions.actionsFor = null },
        )
    }
    interactions.editing?.let { message ->
        ReplyDialog(state, replyTo = null, chatTitle, editing = message, onDismiss = { interactions.editing = null })
    }
    interactions.deleting?.let { (message, actions) ->
        DeleteDialog(state, message, actions, scope, onDismiss = { interactions.deleting = null })
    }
    interactions.forwarding?.let { message ->
        ForwardDialog(state, message, chatTitle, scope, onDismiss = { interactions.forwarding = null })
    }
    interactions.reactors?.let { message ->
        ReactorsDialog(state, message, chatTitle, onDismiss = { interactions.reactors = null })
    }
    interactions.replyTo?.let { message ->
        ReplyDialog(state, message, chatTitle, onDismiss = { interactions.replyTo = null })
    }
    if (interactions.composing) {
        ReplyDialog(state, replyTo = null, chatTitle, onDismiss = { interactions.composing = false })
    }
}

/**
 * Rychlé reakce (sada chatu od TDLib, dokud se nenačte, nic) a „Odpovědět", když se smí psát.
 * Vybraná reakce se přepne a nabídka zavře; moje reakce je zvýrazněná a dá se jí odebrat.
 */
@Composable
private fun MessageActionsDialog(
    state: ConversationState,
    message: ChatMessage,
    scope: CoroutineScope,
    chatTitle: String,
    onReply: () -> Unit,
    /** Skok na zprávu, na kterou tahle odpovídá; `null` = neodpovídá na nic. */
    onJumpToQuote: (() -> Unit)?,
    onEdit: () -> Unit,
    onDelete: (MessageActions) -> Unit,
    onForward: () -> Unit,
    onReactors: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Upravit a smazat jen tam, kde to server dovolí (`messageActions`): vlastní zprávu jde
    // upravit omezenou dobu, smazat cizí jen správce.
    val actions by produceState<MessageActions?>(null, message.id) { value = state.actions(message.id) }
    val choices by produceState<List<String>?>(null, message.id) {
        value = state.reactionChoices(message.id).filter { customEmojiIdOf(it) == null }.take(MAX_QUICK_REACTIONS)
    }
    Dialog(onDismissRequest = onDismiss, properties = WideDialog) {
        DialogPanel {
            QuoteCard(message, chatTitle)
            Spacer(Modifier.height(20.dp))
            val first = remember { FocusRequester() }
            val shown = choices
            if (!shown.isNullOrEmpty()) {
                LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    shown.forEachIndexed { index, emoji ->
                        val chosen = message.reactions.any { it.emoji == emoji && it.isChosen }
                        Surface(
                            onClick = {
                                scope.launch { state.toggleReaction(message, emoji) }
                                onDismiss()
                            },
                            modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                            shape = ClickableSurfaceDefaults.shape(CircleShape),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (chosen) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            ),
                        ) {
                            Text(emoji, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(10.dp))
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.canWrite) {
                    val reply = remember { FocusRequester() }
                    if (shown != null && shown.isEmpty()) LaunchedEffect(Unit) { runCatching { reply.requestFocus() } }
                    Button(onClick = onReply, modifier = Modifier.focusRequester(reply)) {
                        Text(stringResource(Res.string.tv_action_reply))
                    }
                }
                val allowed = actions
                if (allowed?.canEdit == true && message.text.isNotBlank() && state.canEditKeepingFormat(message)) {
                    Button(onClick = onEdit) { Text(stringResource(Res.string.menu_edit)) }
                }
                // Kdo reagoval: jen u zprávy s reakcemi. V kanálu to server neprozradí (dialog
                // to pak řekne), ve skupině a v soukromém chatu ano.
                if (message.reactions.isNotEmpty()) {
                    Button(onClick = onReactors) { Text(stringResource(Res.string.reactions_title)) }
                }
                if (allowed?.canForward == true) {
                    Button(onClick = onForward) { Text(stringResource(Res.string.menu_forward)) }
                }
                if (allowed != null && (allowed.canDeleteForAll || allowed.canDeleteForSelf)) {
                    Button(onClick = { onDelete(allowed) }) { Text(stringResource(Res.string.menu_delete)) }
                }
                if (onJumpToQuote != null) {
                    // Bez reakcí i odpovědi (kanál bez reakcí) by fokus v nabídce nebyl nikde.
                    val showQuote = remember { FocusRequester() }
                    if (shown != null && shown.isEmpty() && !state.canWrite) LaunchedEffect(Unit) { runCatching { showQuote.requestFocus() } }
                    Button(onClick = onJumpToQuote, modifier = Modifier.focusRequester(showQuote)) {
                        Text(stringResource(Res.string.tv_action_show_quote))
                    }
                }
            }
        }
    }
}

/**
 * Odpověď ([replyTo]) nebo nová zpráva (`null`, bez citace; ve vlákně komentářů jde kořeni):
 * citace, pole a Odeslat. Pole dostane fokus hned, takže naskočí systémová klávesnice
 * televize (na Google TV i s diktováním z ovladače); OK na klávesnici rovnou odešle.
 */
@Composable
private fun ReplyDialog(
    state: ConversationState,
    replyTo: ChatMessage?,
    chatTitle: String,
    /** Úprava vlastní zprávy: pole začíná jejím textem a Odeslat ho uloží. */
    editing: ChatMessage? = null,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // Kurzor na konec předvyplněného textu: s holým řetězcem stál na začátku a dopsat
    // k upravované zprávě slovo chtělo napřed dojet šipkami na konec (ověřeno na emulátoru).
    var value by remember { editing?.text.orEmpty().let { mutableStateOf(TextFieldValue(it, TextRange(it.length))) } }
    val text = value.text
    var sending by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val send = {
        if (text.isNotBlank() && !sending) {
            sending = true
            failed = false
            scope.launch {
                val result = if (editing != null) {
                    if (text.trim() == editing.text) Result.success(Unit) else state.edit(editing, text.trim())
                } else {
                    state.send(text.trim(), replyTo?.id)
                }
                result.onSuccess { onDismiss() }.onFailure { failed = true }
                sending = false
            }
        }
    }
    val field = remember { FocusRequester() }
    val sendButton = remember { FocusRequester() }
    val mic = remember { FocusRequester() }
    // Nadiktovaný text se PŘIPOJÍ za napsaný (u úpravy za text zprávy), kurzor na konec; fokus
    // zpátky do pole, ať jde rovnou odeslat klávesnicí nebo dopsat.
    val voice = rememberVoiceInput(stringResource(Res.string.tv_compose_voice_prompt)) { spoken ->
        val joined = if (value.text.isBlank()) spoken else value.text.trimEnd() + " " + spoken
        value = TextFieldValue(joined, TextRange(joined.length))
        failed = false
        runCatching { field.requestFocus() }
    }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    Dialog(onDismissRequest = onDismiss, properties = WideDialog) {
        DialogPanel {
            Text(
                stringResource(
                    when {
                        editing != null -> Res.string.tv_edit_title
                        replyTo != null -> Res.string.tv_reply_title
                        else -> Res.string.tv_compose_title
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            if (replyTo != null) {
                Spacer(Modifier.height(12.dp))
                QuoteCard(replyTo, chatTitle)
            }
            Spacer(Modifier.height(20.dp))
            var focused by remember { mutableStateOf(false) }
            val colors = MaterialTheme.colorScheme
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (voice != null) {
                    MicButton(
                        voice,
                        stringResource(Res.string.tv_compose_voice),
                        Modifier.focusRequester(mic).onPreviewKeyEvent { event ->
                            // Z mikrofonu dolů na Odeslat, ne do prázdna vedle pole.
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                runCatching { sendButton.requestFocus() }
                                true
                            } else {
                                false
                            }
                        },
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        failed = false
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    textStyle = MaterialTheme.typography.titleMedium.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    // Jednořádkové: víceřádkové pole si šipku dolů bere na pohyb kurzoru a z pole se
                    // ovladačem nedalo odejít na Odeslat (ověřeno na emulátoru). I tak se šipka dolů
                    // posílá výslovně — v jednořádkovém poli ji Compose na všech verzích nepustí.
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(field)
                        .onFocusChanged { focused = it.isFocused }
                        .onPreviewKeyEvent { event ->
                            when {
                                event.type != KeyEventType.KeyDown -> false
                                event.key == Key.DirectionDown -> {
                                    runCatching { sendButton.requestFocus() }
                                    true
                                }
                                // Šipku vlevo pole bere na posun kurzoru; na začátku textu z něj
                                // odejde na mikrofon (jako v hledání).
                                event.key == Key.DirectionLeft && voice != null && value.selection.collapsed && value.selection.start == 0 -> {
                                    runCatching { mic.requestFocus() }
                                    true
                                }
                                else -> false
                            }
                        },
                    decorationBox = { inner ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .background(colors.surfaceVariant, RoundedCornerShape(16.dp))
                                .border(if (focused) 3.dp else 1.dp, if (focused) colors.primary else colors.border, RoundedCornerShape(16.dp))
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                        ) {
                            if (text.isEmpty()) {
                                Text(stringResource(Res.string.tv_reply_hint), style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
                            }
                            inner()
                        }
                    },
                )
            }
            if (failed) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(if (editing != null) Res.string.tv_edit_failed else Res.string.tv_send_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.error,
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = send, enabled = text.isNotBlank() && !sending, modifier = Modifier.focusRequester(sendButton)) {
                Text(stringResource(if (editing != null) Res.string.tv_edit_save else Res.string.tv_reply_send))
            }
        }
    }
}

/**
 * Potvrzení smazání: otázka, věta s následkem a tlačítka podle toho, co server dovolí —
 * „Smazat pro všechny" (ve skupině a kanálu jediná možnost) a „Smazat jen pro mě". Album
 * se maže celé (zprávy se stejným `mediaAlbumId` z načtené historie).
 */
@Composable
private fun DeleteDialog(state: ConversationState, message: ChatMessage, actions: MessageActions, scope: CoroutineScope, onDismiss: () -> Unit) {
    val ids = state.albumIds(message)
    var failed by remember { mutableStateOf(false) }
    val delete = { revoke: Boolean ->
        scope.launch {
            state.delete(ids, revoke).onSuccess { onDismiss() }.onFailure { failed = true }
        }
    }
    val cancel = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { cancel.requestFocus() } }
    Dialog(onDismissRequest = onDismiss, properties = WideDialog) {
        DialogPanel {
            Text(stringResource(Res.string.delete_message_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(if (actions.canDeleteForAll) Res.string.delete_message_for_all else Res.string.delete_message_for_self),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (failed) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.tv_delete_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Výchozí fokus na Zrušit, ne na smazání: OK zmáčknuté o jednou víc (dialog
                // naskočí pod prstem hned po výběru v nabídce) smazalo zprávu pro všechny —
                // stalo se při ověřování na emulátoru.
                Button(onClick = onDismiss, modifier = Modifier.focusRequester(cancel)) {
                    Text(stringResource(Res.string.common_cancel))
                }
                if (actions.canDeleteForAll) {
                    Button(onClick = { delete(true) }) {
                        Text(stringResource(Res.string.tv_delete_for_all))
                    }
                }
                if (actions.canDeleteForSelf) {
                    Button(onClick = { delete(false) }) {
                        Text(stringResource(Res.string.tv_delete_for_me))
                    }
                }
            }
        }
    }
}

/**
 * Tlačítko pod seznamem zpráv, které otevře psaní nové zprávy. Pod seznamem, ne v něm: je vidět
 * pořád a šipka dolů z nejnovější zprávy na něj skočí. Pole se rovnou nekreslí — na televizi by
 * klávesnice naskočila při každém průchodu fokusu.
 */
@Composable
fun ComposeButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier) {
        Icon(painterResource(Res.drawable.edit_24dp), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label)
    }
}

/**
 * Dialog bez výchozí šířky platformy: ta je kolem 440 dp a strop panelu (640 dp) by se neuplatnil
 * — řada rychlých reakcí v nabídce se pak uřízla (ověřeno na emulátoru).
 */
internal val WideDialog = DialogProperties(usePlatformDefaultWidth = false)

/**
 * Plocha dialogu: zaoblená, šířka do 640 dp, v barvě `surfaceContainerHigh` jako `AlertDialog`
 * na telefonu ([TvSurfaces]).
 *
 * **Ne `surfaceVariant`:** na té ploše stojí i nezaostřené `Button` z tv-material (výchozí
 * `surfaceVariant` s průhledností 0,8), takže tlačítka s dialogem splývala a jako tlačítko bylo
 * vidět jen to zaostřené — ostatní vypadala jako holý text na šedé ploše.
 */
@Composable
internal fun DialogPanel(content: @Composable () -> Unit) {
    Column(
        Modifier
            .widthIn(min = 420.dp, max = 640.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(TvColors.surfaces.containerHigh)
            .padding(28.dp),
    ) { content() }
}

/**
 * Obsah citace: ikona uvozovek a jméno citovaného, pod nimi první řádek textu (nebo popis obsahu).
 * Bez svislého proužku — ten je jazyk M2 a telefon ho dávno nahradil tónovanou plochou
 * (docs/poznatky/konverzace-zobrazeni.md, „Odpověď je karta ze dvou částí"). Plochu si dává
 * volající: v bublině horní díl karty odpovědi ([QuotedCard]), v dialogu samostatná kartička
 * ([QuoteCard]). Barvy jsou `onTertiaryContainer` na `tertiaryContainer` — dvojice, kterou M3
 * garantuje; `tertiary` na `tertiaryContainer` kontrast nezaručuje (CLAUDE.md, „Akcent na
 * tónované ploše").
 */
@Composable
fun QuoteContent(message: ChatMessage, chatTitle: String) {
    val sender = when {
        message.isOutgoing -> stringResource(Res.string.tv_message_you)
        else -> message.senderName ?: chatTitle
    }
    val color = MaterialTheme.colorScheme.onTertiaryContainer
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Ikona vždy před jménem, jako na telefonu: říká, k čemu se obsah vztahuje.
            Icon(painterResource(Res.drawable.format_quote_24dp), contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(sender, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            message.text.ifEmpty { message.description?.resolve() ?: mediaLabel(message) }.replace('\n', ' '),
            style = MaterialTheme.typography.bodyMedium,
            color = color.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Citace jako samostatná tónovaná kartička — v nabídce nad zprávou a v dialogu odpovědi. */
@Composable
fun QuoteCard(message: ChatMessage, chatTitle: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) { QuoteContent(message, chatTitle) }
}

/**
 * Reakce pod zprávou jako čipy (emoji a počet), moje zvýrazněná. Vlastní emoji se vynechávají,
 * televize je kreslit neumí. Řádky se zalomí, když se nevejdou.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReactionChips(reactions: List<MessageReaction>, contentColor: Color) {
    val shown = reactions.filter { customEmojiIdOf(it.emoji) == null }
    if (shown.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        shown.forEach { reaction ->
            val container = if (reaction.isChosen) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.12f)
            val content = if (reaction.isChosen) MaterialTheme.colorScheme.onPrimary else contentColor
            Text(
                "${reaction.emoji} ${compactCountText(reaction.count)}",
                style = MaterialTheme.typography.labelLarge.tabular(),
                color = content,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(container).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/** Kolik rychlých reakcí nabídka ukáže; víc se do řady na televizi nevejde. */
private const val MAX_QUICK_REACTIONS = 7

/**
 * Co je zpráva bez textu a bez popisu (`description` mají jen popisné a servisní zprávy):
 * „Fotka", „Video zpráva"… jako náhled v seznamu chatů. Dřív citace fotky nebo alba říkala
 * „Tuhle zprávu zatím ukáže jen telefon", přestože ji televize ukáže.
 */
@Composable
private fun mediaLabel(message: ChatMessage): String = when (val media = message.media) {
    is MessageMedia.Photo -> stringResource(Res.string.media_photo)
    is MessageMedia.Video -> stringResource(Res.string.media_video)
    is MessageMedia.VideoNote -> stringResource(Res.string.content_video_note)
    is MessageMedia.VoiceNote -> stringResource(Res.string.conversation_voice_message)
    is MessageMedia.Audio -> listOfNotNull(media.performer, media.title).filter { it.isNotBlank() }.joinToString(" – ")
        .ifBlank { stringResource(Res.string.content_audio) }
    is MessageMedia.Animation -> stringResource(Res.string.content_gif)
    is MessageMedia.Document -> stringResource(Res.string.content_file)
    is MessageMedia.Sticker -> stringResource(Res.string.media_sticker)
    else -> stringResource(Res.string.tv_message_unsupported)
}
