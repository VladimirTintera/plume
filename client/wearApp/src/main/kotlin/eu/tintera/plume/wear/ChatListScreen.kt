package eu.tintera.plume.wear

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
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
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_load_failed
import eu.tintera.plume.resources.connection_connecting
import eu.tintera.plume.resources.wear_chats_empty
import eu.tintera.plume.resources.wear_chats_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Hlavní seznam chatů. Repository ho po přihlášení načítá sama a drží živý z updatů TDLib,
 * obrazovka jen kreslí: název, náhled poslední zprávy a počet nepřečtených.
 */
@Composable
fun ChatListScreen() {
    val chats = koinInject<ChatRepository>()
    val listState by chats.chats().collectAsState()

    when (val current = listState) {
        ChatListState.Loading -> LoadingScreen(stringResource(Res.string.connection_connecting))
        is ChatListState.Failed -> MessageScreen(stringResource(Res.string.chats_load_failed))
        is ChatListState.Ready ->
            if (current.chats.isEmpty()) MessageScreen(stringResource(Res.string.wear_chats_empty))
            else ChatList(current.chats)
    }
}

@Composable
private fun ChatList(chats: List<ChatSummary>) {
    val state = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = state) { contentPadding ->
        TransformingLazyColumn(state = state, contentPadding = contentPadding) {
            item { ListHeader { Text(stringResource(Res.string.wear_chats_title)) } }
            // Bez klíče: chaty po přihlášení přibývají postupně a s klíčem by lazy seznam držel
            // první viditelný chat, takže by nové nahoře odjely nad displej (ověřeno na emulátoru).
            items(chats) { chat -> ChatCard(chat) }
        }
    }
}

@Composable
private fun ChatCard(chat: ChatSummary) {
    TitleCard(
        onClick = {},
        title = { Text(chat.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        time = chat.unreadCount.takeIf { it > 0 }?.let { count -> { Text(count.toString()) } },
        modifier = Modifier.fillMaxWidth(),
    ) {
        chat.lastMessagePreview?.let { preview ->
            Text(preview.resolve(), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
