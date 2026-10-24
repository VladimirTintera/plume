package eu.tintera.plume.wear

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.di.APP_SCOPE
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.resources.logout_24dp
import eu.tintera.plume.resources.wear_logout
import eu.tintera.plume.resources.wear_logout_text
import eu.tintera.plume.resources.wear_logout_title
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.core.qualifier.named
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.ui.asDate
import eu.tintera.plume.core.ui.asTimeOfDay
import eu.tintera.plume.core.ui.isSameDayAs
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_load_failed
import eu.tintera.plume.resources.connection_connecting
import eu.tintera.plume.resources.wear_chats_empty
import eu.tintera.plume.resources.wear_chats_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Clock

/**
 * Hlavní seznam chatů. Repository ho po přihlášení načítá sama a drží živý z updatů TDLib,
 * obrazovka jen kreslí: název, náhled poslední zprávy a počet nepřečtených.
 */
@Composable
fun ChatListScreen(onOpen: (Long) -> Unit) {
    val chats = koinInject<ChatRepository>()
    val listState by chats.chats().collectAsState()

    when (val current = listState) {
        ChatListState.Loading -> LoadingScreen(stringResource(Res.string.connection_connecting))
        is ChatListState.Failed -> MessageScreen(stringResource(Res.string.chats_load_failed))
        is ChatListState.Ready ->
            if (current.chats.isEmpty()) MessageScreen(stringResource(Res.string.wear_chats_empty))
            else ChatList(current.chats, onOpen)
    }
}

@Composable
private fun ChatList(chats: List<ChatSummary>, onOpen: (Long) -> Unit) {
    val state = rememberTransformingLazyColumnState()
    val savedMessagesId = savedMessagesChatId()
    var confirmLogout by remember { mutableStateOf(false) }
    ScreenScaffold(scrollState = state) { contentPadding ->
        TransformingLazyColumn(state = state, contentPadding = contentPadding) {
            item { ListHeader { Text(stringResource(Res.string.wear_chats_title)) } }
            // Bez klíče: chaty po přihlášení přibývají postupně a s klíčem by lazy seznam držel
            // první viditelný chat, takže by nové nahoře odjely nad displej (ověřeno na emulátoru).
            items(chats) { chat -> ChatCard(chat, savedMessagesId, onClick = { onOpen(chat.id) }) }
            // Odhlášení na konci seznamu: hodinky nemají Nastavení a jinak by šly odhlásit jen
            // z telefonu (Zařízení → ukončit sezení).
            item {
                Button(
                    onClick = { confirmLogout = true },
                    icon = { Icon(painterResource(Res.drawable.logout_24dp), contentDescription = null) },
                    label = { Text(stringResource(Res.string.wear_logout)) },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    LogoutDialog(visible = confirmLogout, onDismiss = { confirmLogout = false })
}

/**
 * Potvrzení odhlášení. Odhlásí jen hodinky (vlastní sezení TDLib); po odhlášení automat přejde
 * k QR kódu sám a o zbytek (notifikace, dlaždice, zrcadlení z telefonu) se starají odběry stavu
 * přihlášení. Na telefonu se nic nemění.
 */
@Composable
private fun LogoutDialog(visible: Boolean, onDismiss: () -> Unit) {
    val session = koinInject<TelegramSession>()
    val appScope = koinInject<CoroutineScope>(named(APP_SCOPE))
    AlertDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(Res.drawable.logout_24dp), contentDescription = null) },
        title = { Text(stringResource(Res.string.wear_logout_title)) },
        text = { Text(stringResource(Res.string.wear_logout_text)) },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(
                onClick = {
                    onDismiss()
                    // APP_SCOPE: obrazovka po odhlášení zmizí a její scope by se zrušil uprostřed.
                    appScope.launch {
                        session.logOut().onFailure { logger.w(it) { "Odhlášení hodinek se nepodařilo." } }
                    }
                },
            )
        },
    )
}

@Composable
private fun ChatCard(chat: ChatSummary, savedMessagesId: Long?, onClick: () -> Unit) {
    // V rohu počet nepřečtených, a když nic nečeká, čas poslední zprávy (dnes hodina, jinak datum).
    val corner = chat.unreadCount.takeIf { it > 0 }?.toString()
        ?: chat.lastMessageTimestamp?.let { if (it.isSameDayAs(nowSeconds())) it.asTimeOfDay() else it.asDate() }
    TitleCard(
        onClick = onClick,
        title = {
            val title = chatTitle(chat.id, chat.title, savedMessagesId)
            ChatAvatar(fileId = chat.avatarFileId, title = title, size = AVATAR_SIZE, savedMessages = chat.id == savedMessagesId)
            Spacer(Modifier.width(6.dp))
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        time = corner?.let { text -> { Text(text) } },
        modifier = Modifier.fillMaxWidth(),
    ) {
        chat.lastMessagePreview?.let { preview ->
            Text(preview.resolve(), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val AVATAR_SIZE = 24.dp

private val logger = Logger.withTag("WearChatList")

internal fun nowSeconds(): Int = Clock.System.now().epochSeconds.toInt()
