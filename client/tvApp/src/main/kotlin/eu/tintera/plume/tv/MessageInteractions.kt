package eu.tintera.plume.tv

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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageReaction
import eu.tintera.plume.core.telegram.customEmojiIdOf
import eu.tintera.plume.core.ui.compactCountText
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.tv_action_reply
import eu.tintera.plume.resources.tv_message_you
import eu.tintera.plume.resources.tv_reply_hint
import eu.tintera.plume.resources.tv_reply_send
import eu.tintera.plume.resources.tv_reply_title
import eu.tintera.plume.resources.tv_compose_title
import eu.tintera.plume.resources.edit_24dp
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
            onDismiss = { interactions.actionsFor = null },
        )
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
    onDismiss: () -> Unit,
) {
    val choices by produceState<List<String>?>(null, message.id) {
        value = state.reactionChoices(message.id).filter { customEmojiIdOf(it) == null }.take(MAX_QUICK_REACTIONS)
    }
    Dialog(onDismissRequest = onDismiss) {
        DialogPanel {
            QuoteBar(message, chatTitle, MaterialTheme.colorScheme.onSurface)
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
            if (state.canWrite) {
                val reply = remember { FocusRequester() }
                if (shown != null && shown.isEmpty()) LaunchedEffect(Unit) { runCatching { reply.requestFocus() } }
                Button(onClick = onReply, modifier = Modifier.focusRequester(reply)) {
                    Text(stringResource(Res.string.tv_action_reply))
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
private fun ReplyDialog(state: ConversationState, replyTo: ChatMessage?, chatTitle: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val send = {
        if (text.isNotBlank() && !sending) {
            sending = true
            failed = false
            scope.launch {
                state.send(text.trim(), replyTo?.id)
                    .onSuccess { onDismiss() }
                    .onFailure { failed = true }
                sending = false
            }
        }
    }
    val field = remember { FocusRequester() }
    val sendButton = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        DialogPanel {
            Text(
                stringResource(if (replyTo != null) Res.string.tv_reply_title else Res.string.tv_compose_title),
                style = MaterialTheme.typography.titleLarge,
            )
            if (replyTo != null) {
                Spacer(Modifier.height(12.dp))
                QuoteBar(replyTo, chatTitle, MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(20.dp))
            var focused by remember { mutableStateOf(false) }
            val colors = MaterialTheme.colorScheme
            BasicTextField(
                value = text,
                onValueChange = {
                    text = it
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
                    .fillMaxWidth()
                    .focusRequester(field)
                    .onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            runCatching { sendButton.requestFocus() }
                            true
                        } else {
                            false
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
            if (failed) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.tv_send_failed), style = MaterialTheme.typography.bodyMedium, color = colors.error)
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = send, enabled = text.isNotBlank() && !sending, modifier = Modifier.focusRequester(sendButton)) {
                Text(stringResource(Res.string.tv_reply_send))
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

/** Plocha dialogu: zaoblená, v barvě nad stránkou, šířka do 640 dp. */
@Composable
private fun DialogPanel(content: @Composable () -> Unit) {
    Column(
        Modifier
            .widthIn(min = 420.dp, max = 640.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(28.dp),
    ) { content() }
}

/**
 * Citace zprávy: proužek v barvě motivu, odesílatel a první řádek textu (nebo popis obsahu).
 * V bublině nad textem odpovědi i v nabídce a dialogu odpovědi.
 */
@Composable
fun QuoteBar(message: ChatMessage, chatTitle: String, contentColor: Color, modifier: Modifier = Modifier) {
    val sender = when {
        message.isOutgoing -> stringResource(Res.string.tv_message_you)
        else -> message.senderName ?: chatTitle
    }
    Row(modifier.height(IntrinsicHeightFallback), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(IntrinsicHeightFallback).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(sender, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            Text(
                message.text.ifEmpty { message.description?.resolve() ?: stringResource(Res.string.tv_message_unsupported) }.replace('\n', ' '),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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

/** Výška citace (dva řádky textu); proužek ji kopíruje bez intrinsických měr. */
private val IntrinsicHeightFallback = 44.dp

/** Kolik rychlých reakcí nabídka ukáže; víc se do řady na televizi nevejde. */
private const val MAX_QUICK_REACTIONS = 7
