package eu.tintera.plume.wear

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.wear.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import eu.tintera.plume.resources.settings_24dp
import eu.tintera.plume.resources.settings_title
import androidx.wear.compose.material3.Icon
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
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
fun ChatListScreen(onOpen: (Long) -> Unit, onSettings: () -> Unit) {
    val chats = koinInject<ChatRepository>()
    val listState by chats.chats().collectAsState()

    when (val current = listState) {
        ChatListState.Loading -> LoadingScreen(stringResource(Res.string.connection_connecting))
        is ChatListState.Failed -> MessageScreen(stringResource(Res.string.chats_load_failed))
        is ChatListState.Ready ->
            if (current.chats.isEmpty()) MessageScreen(stringResource(Res.string.wear_chats_empty))
            else ChatList(current.chats, onOpen, onSettings)
    }
}

@Composable
private fun ChatList(chats: List<ChatSummary>, onOpen: (Long) -> Unit, onSettings: () -> Unit) {
    val state = rememberTransformingLazyColumnState()
    val savedMessagesId = savedMessagesChatId()
    // Položky se u okraje displeje zmenšují (M3 Expressive), viz transformedItem.
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = state) { contentPadding ->
        TransformingLazyColumn(state = state, contentPadding = contentPadding) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text(stringResource(Res.string.wear_chats_title)) }
            }
            // Nastavení (odhlášení, později další) hned pod nadpisem: hodinky nemají menu v liště
            // a konec dlouhého seznamu chatů nikoho nenapadne hledat (připomínka uživatele).
            item {
                Box(Modifier.fillMaxWidth().transformedItem(this, spec), contentAlignment = Alignment.Center) {
                    CompactButton(
                        onClick = onSettings,
                        icon = { Icon(painterResource(Res.drawable.settings_24dp), contentDescription = null) },
                        label = { Text(stringResource(Res.string.settings_title)) },
                        colors = ButtonDefaults.filledTonalButtonColors(),
                    )
                }
            }
            // Bez klíče: chaty po přihlášení přibývají postupně a s klíčem by lazy seznam držel
            // první viditelný chat, takže by nové nahoře odjely nad displej (ověřeno na emulátoru).
            items(chats) { chat ->
                ChatCard(
                    chat,
                    savedMessagesId,
                    onClick = { onOpen(chat.id) },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                )
            }
        }
    }
}

@Composable
private fun ChatCard(
    chat: ChatSummary,
    savedMessagesId: Long?,
    onClick: () -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    // V rohu odznak nepřečtených, a když nic nečeká, čas poslední zprávy (dnes hodina, jinak datum).
    val time = chat.lastMessageTimestamp?.let { if (it.isSameDayAs(nowSeconds())) it.asTimeOfDay() else it.asDate() }
    val unread = chat.unreadCount > 0 || chat.isMarkedAsUnread
    TitleCard(
        onClick = onClick,
        title = {
            val title = chatTitle(chat.id, chat.title, savedMessagesId)
            ChatAvatar(fileId = chat.avatarFileId, title = title, size = AVATAR_SIZE, savedMessages = chat.id == savedMessagesId)
            Spacer(Modifier.width(6.dp))
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        time = when {
            unread -> { { UnreadBadge(chat.unreadCount, muted = chat.isMuted) } }
            time != null -> { { Text(time) } }
            else -> null
        },
        modifier = modifier,
        transformation = transformation,
    ) {
        chat.lastMessagePreview?.let { preview ->
            Text(preview.resolve(), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val AVATAR_SIZE = 24.dp

/**
 * Odznak nepřečtených: vyplněná pilulka s počtem, jako na telefonu. Holé číslo v rohu karty
 * splývalo s časem u ostatních chatů a nepřečtený chat nebyl na první pohled vidět. Ztlumený
 * chat má odznak tlumený (notifikace nevydává, nemá ani křičet v seznamu). Ručně označený
 * jako nepřečtený bez počtu je jen tečka.
 */
@Composable
private fun UnreadBadge(count: Int, muted: Boolean) {
    val container = if (muted) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primary
    val content = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
    Box(
        Modifier
            .heightIn(min = BADGE_SIZE)
            .widthIn(min = BADGE_SIZE)
            .background(container, CircleShape)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (count > 0) {
            Text(
                if (count > MAX_BADGE) "$MAX_BADGE+" else count.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = content,
                maxLines = 1,
            )
        }
    }
}

private val BADGE_SIZE = 18.dp

/** Víc se do rohu karty nevejde a přesné číslo nad sto na hodinkách nikdo nečte. */
private const val MAX_BADGE = 99

internal fun nowSeconds(): Int = Clock.System.now().epochSeconds.toInt()
