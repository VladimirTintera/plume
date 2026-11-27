package eu.tintera.plume.tv

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.forward_empty
import eu.tintera.plume.resources.forward_failed
import eu.tintera.plume.resources.forward_search
import eu.tintera.plume.resources.forward_title
import eu.tintera.plume.resources.saved_messages
import eu.tintera.plume.resources.tv_forward_done
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Přeposlání zprávy: citace toho, co se posílá, a pod ní chaty ze seznamu — Uložené zprávy
 * první („pošlu si to" je nejčastější cíl, jako na telefonu). OK na řádku zprávu rovnou
 * přepošle a dialog zavře; potvrzení je systémový toast, protože konverzace pod dialogem
 * žádnou změnu neukáže (zpráva odešla jinam).
 *
 * Bez hledání a voleb (skrýt odesílatele, komentář): s ovladačem se vybírá z prvních obrazovek
 * seznamu a víc cílů najednou by chtělo zaškrtávání, které televize nepotřebuje. Kam psát
 * nejde (cizí kanál), se nefiltruje — telefon to dělá stejně a chyba řekne proč.
 */
@Composable
internal fun ForwardDialog(state: ConversationState, message: ChatMessage, chatTitle: String, scope: CoroutineScope, onDismiss: () -> Unit) {
    val chats = koinInject<ChatRepository>()
    val savedMessagesId by chats.savedMessagesChatId.collectAsState()
    val list by remember { chats.chats(ChatFolder.Main) }.collectAsState()
    val context = LocalContext.current
    var sending by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val savedTitle = stringResource(Res.string.saved_messages)
    val forward = { targetId: Long?, title: String ->
        if (!sending) {
            sending = true
            failed = false
            // Scope obrazovky: dialog se po úspěchu zavře a s ním by se zrušil i toast.
            scope.launch {
                // Uložené zprávy TDLib nemusí znát, dokud se neotevřou — `null` je založí.
                val target = targetId ?: chats.openSavedMessages().getOrNull()
                val result = if (target == null) Result.failure(IllegalStateException("Uložené zprávy nejsou")) else state.forward(message, target)
                result
                    .onSuccess {
                        Toast.makeText(context, getString(Res.string.tv_forward_done, title), Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                    .onFailure { failed = true }
                sending = false
            }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = WideDialog) {
        DialogPanel {
            Text(stringResource(Res.string.forward_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            QuoteCard(message, chatTitle)
            if (failed) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.forward_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
            // Hledání: pole s mikrofonem nad seznamem. S dotazem se místo seznamu chatů ukážou
            // nalezené (napřed lokálně, pak i ze serveru), Uložené zprávy jen když odpovídají.
            var query by remember { mutableStateOf("") }
            ForwardSearchField(query, onQuery = { query = it })
            Spacer(Modifier.height(12.dp))
            val found by produceState<List<ChatSummary>?>(null, query) {
                value = null
                val text = query.trim()
                if (text.isEmpty()) return@produceState
                delay(SEARCH_DEBOUNCE_MS)
                val local = chats.searchChats(text).getOrNull().orEmpty()
                value = local
                val server = chats.searchChats(text, onServer = true).getOrNull().orEmpty()
                value = (local + server).distinctBy { it.id }
            }
            val searching = query.isNotBlank()
            val showSaved = !searching || savedTitle.contains(query.trim(), ignoreCase = true)
            val targets = if (searching) {
                found.orEmpty().filter { it.id != savedMessagesId }
            } else {
                (list as? ChatListState.Ready)?.chats.orEmpty().filter { it.id != savedMessagesId }
            }
            if (searching && found != null && targets.isEmpty() && !showSaved) {
                Text(stringResource(Res.string.forward_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val listState = rememberLazyListState()
            // Dočítání seznamu, když se fokus blíží konci (jako v seznamu chatů).
            val loaded by rememberUpdatedState(targets.size + 1)
            LaunchedEffect(listState, searching) {
                if (searching) return@LaunchedEffect
                snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                    .collect { last -> if (last != null && last >= loaded - LOAD_AHEAD) chats.loadMore(ChatFolder.Main) }
            }
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                // Strop výšky (tři a půl řádku, vykouknutí dalšího řádku říká, že seznam
                // pokračuje): panel s nadpisem, citací a polem hledání se musí vejít do 484 dp
                // (540 dp bez bezpečných okrajů 28 dp). Se 280 dp měl po přidání pole hledání
                // 511 dp a sahal od stavové lišty ke spodní hraně (změřeno na emulátoru).
                modifier = Modifier.fillMaxWidth().heightIn(max = 248.dp),
            ) {
                if (showSaved) {
                    item(key = "saved") {
                        ForwardTarget(savedTitle, avatarFileId = null, savedMessages = true, Modifier.focusRequester(first)) {
                            forward(savedMessagesId, savedTitle)
                        }
                    }
                }
                items(targets, key = { it.id }) { chat ->
                    ForwardTarget(chat.title, chat.avatarFileId, savedMessages = false) { forward(chat.id, chat.title) }
                }
            }
        }
    }
}

/**
 * Pole hledání s mikrofonem (systémové diktování, [rememberVoiceInput]). Šipka dolů z pole jde
 * do seznamu, šipka vlevo na začátku textu na mikrofon — jednořádkové pole by je jinak
 * spolklo (stejně jako pole v hledání a v psaní zprávy).
 */
@Composable
private fun ForwardSearchField(query: String, onQuery: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    val voice = rememberVoiceInput(stringResource(Res.string.forward_search)) { onQuery(it) }
    var value by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    LaunchedEffect(query) {
        if (value.text != query) value = TextFieldValue(query, TextRange(query.length))
    }
    var focused by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (voice != null) MicButton(voice, stringResource(Res.string.forward_search))
        BasicTextField(
            value = value,
            onValueChange = {
                value = it
                onQuery(it.text)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.moveFocus(FocusDirection.Down) }),
            textStyle = MaterialTheme.typography.titleMedium.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    when {
                        event.type != KeyEventType.KeyDown -> false
                        event.key == Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                        event.key == Key.DirectionLeft && voice != null && value.selection.collapsed && value.selection.start == 0 ->
                            focusManager.moveFocus(FocusDirection.Left)
                        else -> false
                    }
                },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.surface.copy(alpha = if (focused) 1f else 0.6f), RoundedCornerShape(24.dp))
                        .border(if (focused) 2.dp else 0.dp, if (focused) colors.primary else Color.Transparent, RoundedCornerShape(24.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    if (value.text.isEmpty()) {
                        Text(stringResource(Res.string.forward_search), style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun ForwardTarget(
    title: String,
    avatarFileId: Int?,
    savedMessages: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ListItem(
        selected = false,
        // Bez `enabled = false` během odesílání: zakázaný řádek přestane být zaostřitelný
        // a fokus by z dialogu utekl. Druhé OK hlídá `sending`.
        onClick = onClick,
        modifier = modifier,
        leadingContent = { ChatAvatar(fileId = avatarFileId, title = title, size = 40.dp, savedMessages = savedMessages) },
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}

/** Jak dlouho se po posledním písmenu čeká, než se hledá (psaní ovladačem je pomalé). */
private const val SEARCH_DEBOUNCE_MS = 300L

/** Kolik řádků před koncem načtené části se žádá o další. */
private const val LOAD_AHEAD = 5
