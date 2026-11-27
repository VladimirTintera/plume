package eu.tintera.plume.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.telegram.ChatRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import eu.tintera.plume.core.telegram.ChatInfo
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.connection_connecting
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Dvě úrovně: seznam chatů a otevřený chat; zpět tlačítkem ovladače. Na dvě obrazovky stačí
 * stav — knihovna navigace by tu jen přidala závislost.
 *
 * Seznam drží pozici a poslední otevřený chat tady, ne v sobě: s konverzací odchází z kompozice
 * a po návratu musí stát tam, kde byl, s fokusem na chatu, ze kterého uživatel odešel. Pozice
 * je zvlášť pro každou složku, ať přepnutí složky nezačne uprostřed jiného seznamu.
 *
 * Nad otevřeným chatem leží prohlížeč fotek a nad ním přehrávač videa. Zpět zavírá od horní
 * vrstvy (`BackHandler` složený později má přednost) a fokus si bere vrstva, která zbyla.
 * Přehrávač zvuku ([TvAudioPlayer]) žije tady, aby hrál dál při přechodu mezi chaty, a jeho
 * dock leží nad vším kromě videa.
 */
@Composable
fun TvNavigation(openChat: Long? = null, onChatOpened: () -> Unit = {}) {
    var folder by rememberSaveable(stateSaver = FolderSaver) { mutableStateOf<ChatFolder>(ChatFolder.Main) }
    var opened by rememberSaveable { mutableStateOf<Long?>(null) }
    var lastOpened by rememberSaveable { mutableStateOf<Long?>(null) }
    val listStates = remember { mutableMapOf<ChatFolder, LazyListState>() }
    var playing by remember { mutableStateOf<Pair<MessageMedia.Video, String>?>(null) }
    var viewer by remember { mutableStateOf<Pair<List<ViewerItem>, Int>?>(null) }
    val audio = rememberTvAudioPlayer()
    val actions = remember(audio) {
        MediaActions(
            playVideo = { video, caption -> playing = video to caption },
            openViewer = { items, index -> viewer = items to index },
            playAudio = audio::play,
        )
    }
    // Složka obnovená z uloženého stavu už nemusí existovat (smazaná v telefonu, než se proces
    // obnovil). Záložky by pak zvýraznily „Všechny chaty" a seznam zůstal prázdný; když se složka
    // do pár vteřin mezi složkami účtu neobjeví (seznam složek chodí od TDLib až po startu),
    // vrátí se na hlavní seznam.
    val chats = koinInject<ChatRepository>()
    LaunchedEffect(folder) {
        val custom = folder as? ChatFolder.Custom ?: return@LaunchedEffect
        val exists = withTimeoutOrNull(FOLDER_RESTORE_TIMEOUT_MS) {
            chats.chatFolders.first { folders -> folders.folders.any { it.id == custom.id } }
        }
        if (exists == null) folder = ChatFolder.Main
    }
    LaunchedEffect(openChat) {
        if (openChat == null) return@LaunchedEffect
        lastOpened = openChat
        opened = openChat
        onChatOpened()
    }

    CompositionLocalProvider(LocalAudioPlayer provides audio) {
        Box(Modifier.fillMaxSize()) {
            val current = opened
            if (current != null) {
                BackHandler {
                    opened = null
                    viewer = null
                    playing = null
                }
                key(current) { ChatScreen(chatId = current, overlayOpen = playing != null || viewer != null, actions = actions) }
                viewer?.let { (items, index) ->
                    MediaViewerScreen(
                        items = items,
                        startIndex = index,
                        playerOpen = playing != null,
                        onPlayVideo = actions.playVideo,
                        onClose = { viewer = null },
                    )
                }
            } else {
                // Záložky složek jsou součástí obrazovky a přežijí přepnutí složky (fokus v nich
                // zůstane); pozice seznamu je zvlášť pro každou složku.
                ChatListScreen(
                    folder = folder,
                    onFolder = {
                        folder = it
                        lastOpened = null
                    },
                    listStateFor = { listStates.getOrPut(it) { LazyListState() } },
                    lastOpenedChat = lastOpened,
                    onOpen = { chatId ->
                        lastOpened = chatId
                        opened = chatId
                    },
                )
            }
            if (playing == null) AudioDock(audio, Modifier.align(Alignment.BottomCenter))
            playing?.let { (video, caption) ->
                key(video.fileId) { VideoPlayerScreen(video = video, caption = caption, onClose = { playing = null }) }
            }
        }
    }
}

/**
 * Otevřený chat: kanál jako feed příspěvků ([ChannelFeedScreen]), ostatní jako konverzace.
 * Druh chatu se zjistí z `chatInfo`, než se cokoli nakreslí.
 */
@Composable
private fun ChatScreen(chatId: Long, overlayOpen: Boolean, actions: MediaActions) {
    val conversations = koinInject<ConversationRepository>()
    val info by produceState<Result<ChatInfo>?>(null, chatId) { value = conversations.chatInfo(chatId) }
    val loaded = info?.getOrNull()
    when {
        info == null -> LoadingScreen(stringResource(Res.string.connection_connecting))
        loaded?.isChannel == true -> ChannelFeedScreen(chatId, loaded, overlayOpen, actions)
        else -> ConversationScreen(chatId = chatId, playerOpen = overlayOpen, actions = actions)
    }
}

/** Jak dlouho se čeká, než se obnovená složka objeví mezi složkami účtu. */
private const val FOLDER_RESTORE_TIMEOUT_MS = 5_000L

/** Složka do uloženého stavu: Main a Archive jménem, složka Telegramu svým id. */
private val FolderSaver = Saver<ChatFolder, String>(
    save = { folder ->
        when (folder) {
            ChatFolder.Archive -> "archive"
            is ChatFolder.Custom -> folder.id.toString()
            else -> "main"
        }
    },
    restore = { saved ->
        when (saved) {
            "archive" -> ChatFolder.Archive
            "main" -> ChatFolder.Main
            else -> saved.toIntOrNull()?.let { ChatFolder.Custom(it) } ?: ChatFolder.Main
        }
    },
)
