package eu.tintera.plume.features.lockedchats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.accounts.AccountTopAppBar
import eu.tintera.plume.core.adaptive.ContentWidth
import eu.tintera.plume.core.lock.LockedChatsSession
import eu.tintera.plume.core.settings.AccountSettings
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.groupRow
import eu.tintera.plume.core.ui.groupedItem
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.lock_24dp
import eu.tintera.plume.resources.lock_open_24dp
import eu.tintera.plume.resources.locked_chats_empty
import eu.tintera.plume.resources.locked_chats_empty_title
import eu.tintera.plume.resources.locked_chats_lock_now
import eu.tintera.plume.resources.locked_chats_title
import eu.tintera.plume.resources.locked_chats_unlock_chat
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Zamčené chaty účtu (menu účtu → Zamčené chaty). Seznam je `ChatFolder.Locked` z repository —
 * jediné místo, kde se zamčené chaty ukazují. Než se odemkne ([LockedChatsSession]), je místo
 * seznamu číselník; po odchodu aplikace do pozadí se zamkne znovu.
 *
 * - **Chat se otevírá v hlavním zásobníku** ([onOpenChat]), jako z historie hovorů. Bránu
 *   (`LockedChatGate`) projde, protože je sezení odemčené.
 * - **Zámek ihned** (ikona v liště) zamkne sezení, ať se nemusí čekat na odchod do pozadí.
 * - **Odemknout chat** ho vrátí do běžných seznamů (a z tohohle zmizí).
 */
@Composable
fun LockedChatsScreen(
    onBack: () -> Unit,
    onOpenChat: (ChatSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = koinInject<LockedChatsSession>()
    val settings = koinInject<AccountSettings>()
    val repository = koinInject<ChatRepository>()
    val unlocked by session.unlocked.collectAsStateWithLifecycle()
    val list by remember(repository) { repository.chats(ChatFolder.Locked) }.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            AccountTopAppBar(
                title = { Text(stringResource(Res.string.locked_chats_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                    }
                },
                actions = {
                    if (unlocked) {
                        IconButton(onClick = session::lock, shapes = IconButtonDefaults.shapes()) {
                            Icon(painterResource(Res.drawable.lock_24dp), contentDescription = stringResource(Res.string.locked_chats_lock_now))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (!unlocked) {
                LockedChatsUnlock(onUnlocked = session::unlock)
                return@Box
            }
            val chats = (list as? ChatListState.Ready)?.chats.orEmpty()
            if (list is ChatListState.Ready && chats.isEmpty()) {
                IntroEmptyState(
                    icon = Res.drawable.lock_24dp,
                    title = stringResource(Res.string.locked_chats_empty_title),
                    text = stringResource(Res.string.locked_chats_empty),
                )
                return@Box
            }
            val rowColor = MaterialTheme.colorScheme.groupRow
            LazyColumn(
                Modifier.fillMaxSize().widthIn(max = ContentWidth.Reading),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                itemsIndexed(chats, key = { _, chat -> chat.id }) { index, chat ->
                    LockedChatRow(
                        chat = chat,
                        modifier = Modifier.groupedItem(index, chats.size, rowColor),
                        onClick = { onOpenChat(chat) },
                        onUnlock = { settings.setChatLocked(chat.id, false) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LockedChatRow(chat: ChatSummary, onClick: () -> Unit, onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            chatId = chat.avatarColorId,
            title = chat.title,
            avatarFileId = chat.avatarFileId,
            size = AVATAR_SIZE,
            shape = chat.kind.avatarShape(),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                chat.title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            chat.lastMessagePreview?.let { preview ->
                Text(
                    preview.resolve(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (chat.hasUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (chat.unreadCount > 0) {
            Badge(
                containerColor = if (chat.isMuted) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primary,
                contentColor = if (chat.isMuted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
            ) {
                Text(chat.unreadCount.toString(), style = MaterialTheme.typography.labelSmall.tabularNumbers())
            }
        }
        IconButton(onClick = onUnlock, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(Res.drawable.lock_open_24dp), contentDescription = stringResource(Res.string.locked_chats_unlock_chat))
        }
    }
}

private val AVATAR_SIZE = 48.dp

