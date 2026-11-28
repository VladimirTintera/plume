package eu.tintera.plume.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.connection_connecting
import eu.tintera.plume.resources.saved_messages
import eu.tintera.plume.resources.tv_dream_settings_hint
import eu.tintera.plume.resources.tv_dream_settings_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Nastavení spořiče (`android:settingsActivity` v `res/xml/photo_dream.xml`): ze kterého chatu
 * brát fotky. Uložené zprávy nahoře (výchozí), pod nimi chaty hlavního seznamu. Výběr se uloží
 * a obrazovka se zavře, jako systémové výběry.
 */
class DreamSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = DreamSettings(this)
        setContent {
            PrimaryAccountScope {
                TvTheme {
                    DreamSettingsScreen(
                        selected = settings.chatId,
                        onSelect = { chatId ->
                            settings.chatId = chatId
                            finish()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DreamSettingsScreen(selected: Long?, onSelect: (Long?) -> Unit) {
    val stage by koinInject<TelegramSession>().authStage.collectAsState()
    val chats = koinInject<ChatRepository>()
    val savedMessagesId by chats.savedMessagesChatId.collectAsState()
    val list by remember { chats.chats(ChatFolder.Main) }.collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = SAFE_HORIZONTAL, vertical = SAFE_VERTICAL)) {
        Text(stringResource(Res.string.tv_dream_settings_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(Res.string.tv_dream_settings_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        val ready = list as? ChatListState.Ready
        if (stage != AuthStage.Ready || ready == null) {
            LoadingScreen(stringResource(Res.string.connection_connecting))
            return@Column
        }
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        LazyColumn(
            contentPadding = PaddingValues(bottom = SAFE_VERTICAL),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(0.6f),
        ) {
            item(key = "saved") {
                DreamChoice(
                    title = stringResource(Res.string.saved_messages),
                    avatarFileId = null,
                    savedMessages = true,
                    kind = ChatKind.Private,
                    checked = selected == null || selected == savedMessagesId,
                    onClick = { onSelect(null) },
                    modifier = Modifier.focusRequester(first),
                )
            }
            items(ready.chats.filter { it.id != savedMessagesId }, key = { it.id }) { chat ->
                DreamChoice(
                    title = chat.title,
                    avatarFileId = chat.avatarFileId,
                    savedMessages = false,
                    kind = chat.kind,
                    checked = selected == chat.id,
                    onClick = { onSelect(chat.id) },
                )
            }
        }
    }
}

@Composable
private fun DreamChoice(
    title: String,
    avatarFileId: Int?,
    savedMessages: Boolean,
    kind: ChatKind,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        selected = checked,
        onClick = onClick,
        modifier = modifier,
        leadingContent = { ChatAvatar(fileId = avatarFileId, title = title, size = 40.dp, savedMessages = savedMessages, shape = kind.avatarShape()) },
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = if (checked) {
            { Icon(painterResource(Res.drawable.check_24dp), contentDescription = null) }
        } else {
            null
        },
    )
}
