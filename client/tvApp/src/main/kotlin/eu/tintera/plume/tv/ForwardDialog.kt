package eu.tintera.plume.tv

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
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
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.forward_failed
import eu.tintera.plume.resources.forward_title
import eu.tintera.plume.resources.saved_messages
import eu.tintera.plume.resources.tv_forward_done
import kotlinx.coroutines.CoroutineScope
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
            val targets = (list as? ChatListState.Ready)?.chats.orEmpty().filter { it.id != savedMessagesId }
            val listState = rememberLazyListState()
            // Dočítání seznamu, když se fokus blíží konci (jako v seznamu chatů).
            val loaded by rememberUpdatedState(targets.size + 1)
            LaunchedEffect(listState) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                    .collect { last -> if (last != null && last >= loaded - LOAD_AHEAD) chats.loadMore(ChatFolder.Main) }
            }
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                // Strop výšky (asi čtyři řádky): s víc řádky panel s nadpisem a citací přerostl
                // bezpečné okraje obrazovky (ověřeno na emulátoru, 360 dp sahalo ke spodní hraně).
                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
            ) {
                item(key = "saved") {
                    ForwardTarget(savedTitle, avatarFileId = null, savedMessages = true, Modifier.focusRequester(first)) {
                        forward(savedMessagesId, savedTitle)
                    }
                }
                items(targets, key = { it.id }) { chat ->
                    ForwardTarget(chat.title, chat.avatarFileId, savedMessages = false) { forward(chat.id, chat.title) }
                }
            }
        }
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

/** Kolik řádků před koncem načtené části se žádá o další. */
private const val LOAD_AHEAD = 5
