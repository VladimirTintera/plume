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
import eu.tintera.plume.core.telegram.ChatTyping
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.resources.chats_section_pinned
import eu.tintera.plume.resources.chat_bubble_24dp
import eu.tintera.plume.resources.cloud_off_24dp
import eu.tintera.plume.resources.push_pin_filled_24dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import eu.tintera.plume.core.ui.asDate
import eu.tintera.plume.core.ui.asTimeOfDay
import eu.tintera.plume.core.ui.isSameDayAs
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_load_failed
import eu.tintera.plume.resources.connection_connecting
import eu.tintera.plume.resources.wear_chats_empty
import eu.tintera.plume.resources.wear_chats_title
import eu.tintera.plume.resources.wear_chat_actions
import eu.tintera.plume.resources.wear_folders_button
import eu.tintera.plume.resources.wear_search_button
import eu.tintera.plume.resources.search_24dp
import eu.tintera.plume.resources.folder_24dp
import eu.tintera.plume.resources.chats_archive
import eu.tintera.plume.core.telegram.ChatFolder
import androidx.compose.runtime.LaunchedEffect
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Clock

/**
 * Seznam chatů jedné složky. Hlavní seznam repository po přihlášení načítá sama a drží živý
 * z updatů TDLib; archiv a složky Telegramu se začnou načítat až otevřením (`loadMore`).
 * Obrazovka jen kreslí: název, náhled poslední zprávy a počet nepřečtených.
 *
 * Hledání, nastavení a vstup do složek ([onSearch], [onSettings], [onFolders]) má jen hlavní
 * seznam — ve složce by byly navíc a zpět do hlavního seznamu se jde tahem.
 */
@Composable
fun ChatListScreen(
    onOpen: (Long) -> Unit,
    onActions: (Long) -> Unit,
    folder: ChatFolder = ChatFolder.Main,
    onSearch: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onFolders: (() -> Unit)? = null,
) {
    val chats = koinInject<ChatRepository>()
    val listState by remember(folder) { chats.chats(folder) }.collectAsState()
    LaunchedEffect(folder) { if (folder != ChatFolder.Main) chats.loadMore(folder) }
    val folders by chats.chatFolders.collectAsState()
    val title = when (folder) {
        ChatFolder.Main -> stringResource(Res.string.wear_chats_title)
        ChatFolder.Archive -> stringResource(Res.string.chats_archive)
        is ChatFolder.Custom -> folders.folders.firstOrNull { it.id == folder.id }?.title.orEmpty()
    }

    when (val current = listState) {
        ChatListState.Loading -> LoadingScreen(stringResource(Res.string.connection_connecting))
        is ChatListState.Failed -> MessageScreen(stringResource(Res.string.chats_load_failed), Res.drawable.cloud_off_24dp, error = true)
        is ChatListState.Ready ->
            if (current.chats.isEmpty()) MessageScreen(stringResource(Res.string.wear_chats_empty), Res.drawable.chat_bubble_24dp)
            else ChatList(title, current.chats, chats, onOpen, onActions, onSearch, onSettings, onFolders)
    }
}

@Composable
private fun ChatList(
    title: String,
    chats: List<ChatSummary>,
    repository: ChatRepository,
    onOpen: (Long) -> Unit,
    onActions: (Long) -> Unit,
    onSearch: (() -> Unit)?,
    onSettings: (() -> Unit)?,
    onFolders: (() -> Unit)?,
) {
    val state = rememberTransformingLazyColumnState()
    val savedMessagesId = savedMessagesChatId()
    // Položky se u okraje displeje zmenšují (M3 Expressive), viz transformedItem.
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = state) { contentPadding ->
        TransformingLazyColumn(state = state, contentPadding = contentPadding) {
            // Hledat, Archiv a složky a Nastavení jako řada ikon NAD nadpisem: hodinky nemají menu
            // v liště a konec dlouhého seznamu nikoho nenapadne hledat (připomínka uživatele).
            // Pod sebou s popisky zabraly celý první displej a první chat byl vidět až po
            // odrolování; nadpis nad nimi navíc pojmenovával tlačítka, ne chaty.
            val actions = listOfNotNull(
                onSearch?.let { HeaderAction(Res.drawable.search_24dp, Res.string.wear_search_button, it) },
                onFolders?.let { HeaderAction(Res.drawable.folder_24dp, Res.string.wear_folders_button, it) },
                onSettings?.let { HeaderAction(Res.drawable.settings_24dp, Res.string.settings_title, it) },
            )
            if (actions.isNotEmpty()) {
                item { HeaderActions(actions, Modifier.transformedItem(this, spec)) }
            }
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            // Bez klíče: chaty po přihlášení přibývají postupně a s klíčem by lazy seznam držel
            // první viditelný chat, takže by nové nahoře odjely nad displej (ověřeno na emulátoru).
            items(chats) { chat ->
                // „Píše…" odebírá každá viditelná karta sama: lazy seznam drží jen pár položek
                // naráz a odběr skončí s kartou, která odjede z displeje.
                val typing by remember(chat.id) { repository.typing(chat.id) }.collectAsState(null)
                ChatCard(
                    chat,
                    savedMessagesId,
                    typing = typing,
                    onClick = { onOpen(chat.id) },
                    onLongClick = { onActions(chat.id) },
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
    typing: ChatTyping?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    // V rohu odznak nepřečtených, a když nic nečeká, čas poslední zprávy (dnes hodina, jinak datum).
    val time = chat.lastMessageTimestamp?.let { if (it.isSameDayAs(nowSeconds())) it.asTimeOfDay() else it.asDate() }
    val unread = chat.unreadCount > 0 || chat.isMarkedAsUnread
    TitleCard(
        onClick = onClick,
        // Podržení: přečteno, ztlumit, archivovat bez otevírání chatu (ChatActionsScreen).
        onLongClick = onLongClick,
        onLongClickLabel = stringResource(Res.string.wear_chat_actions),
        title = {
            val title = chatTitle(chat.id, chat.title, savedMessagesId)
            ChatAvatar(fileId = chat.avatarFileId, title = title, size = AVATAR_SIZE, savedMessages = chat.id == savedMessagesId)
            Spacer(Modifier.width(6.dp))
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        time = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Připnutý chat špendlíkem před časem / odznakem, jako na telefonu.
                if (chat.isPinned) {
                    Icon(
                        painterResource(Res.drawable.push_pin_filled_24dp),
                        contentDescription = stringResource(Res.string.chats_section_pinned),
                        modifier = Modifier.size(PIN_SIZE),
                    )
                    Spacer(Modifier.width(2.dp))
                }
                when {
                    unread -> UnreadBadge(chat.unreadCount, muted = chat.isMuted)
                    time != null -> Text(time)
                }
            }
        },
        modifier = modifier,
        transformation = transformation,
    ) {
        // „Píše…" místo náhledu, v barvě motivu: protějšek je zrovna u telefonu a odpověď se
        // dá čekat. Ve skupině bez jména — na hodinkách se do řádku nevejde.
        if (typing != null) {
            Text(typing.activity.label(), color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            chat.lastMessagePreview?.let { preview ->
                Text(preview.resolve(), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private val AVATAR_SIZE = 24.dp

private class HeaderAction(val icon: DrawableResource, val label: StringResource, val onClick: () -> Unit)

/**
 * Řada kulatých ikon nad seznamem (Hledat, Archiv a složky, Nastavení). Popisek nese čtečka
 * obrazovky (`contentDescription`); ikony lupy, složky a ozubeného kola jsou na hodinkách
 * srozumitelné i bez textu.
 */
@Composable
private fun HeaderActions(actions: List<HeaderAction>, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        actions.forEach { action ->
            FilledTonalIconButton(onClick = action.onClick, shapes = IconButtonDefaults.animatedShapes()) {
                Icon(painterResource(action.icon), contentDescription = stringResource(action.label))
            }
        }
    }
}

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

private val PIN_SIZE = 12.dp

/** Víc se do rohu karty nevejde a přesné číslo nad sto na hodinkách nikdo nečte. */
private const val MAX_BADGE = 99

internal fun nowSeconds(): Int = Clock.System.now().epochSeconds.toInt()
