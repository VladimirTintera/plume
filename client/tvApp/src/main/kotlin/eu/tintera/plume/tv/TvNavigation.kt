package eu.tintera.plume.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.telegram.MessageMedia
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

/**
 * Dvě úrovně: seznam chatů a otevřená konverzace; zpět tlačítkem ovladače. Na dvě obrazovky
 * stačí stav — knihovna navigace by tu jen přidala závislost.
 *
 * Seznam drží pozici a poslední otevřený chat tady, ne v sobě: s konverzací odchází z kompozice
 * a po návratu musí stát tam, kde byl, s fokusem na chatu, ze kterého uživatel odešel. Pozice
 * je zvlášť pro každou složku, ať přepnutí složky nezačne uprostřed jiného seznamu.
 */
@Composable
fun TvNavigation(openChat: Long? = null, onChatOpened: () -> Unit = {}) {
    var folder by rememberSaveable(stateSaver = FolderSaver) { mutableStateOf<ChatFolder>(ChatFolder.Main) }
    var opened by rememberSaveable { mutableStateOf<Long?>(null) }
    var lastOpened by rememberSaveable { mutableStateOf<Long?>(null) }
    val listStates = remember { mutableMapOf<ChatFolder, LazyListState>() }
    LaunchedEffect(openChat) {
        if (openChat == null) return@LaunchedEffect
        lastOpened = openChat
        opened = openChat
        onChatOpened()
    }

    val current = opened
    if (current != null) {
        BackHandler { opened = null }
        // Přehrávač leží NAD konverzací, ta zůstává v kompozici: po zavření videa je seznam
        // zpráv na stejném místě a nenačítá se znovu. Zpět zavře nejdřív video (jeho
        // `BackHandler` je složený později, takže má přednost).
        var playing by remember(current) { mutableStateOf<Pair<MessageMedia.Video, String>?>(null) }
        Box(Modifier.fillMaxSize()) {
            key(current) { ConversationScreen(
                    chatId = current,
                    playerOpen = playing != null,
                    onPlayVideo = { video, caption -> playing = video to caption },
                ) }
            playing?.let { (video, caption) ->
                key(video.fileId) { VideoPlayerScreen(video = video, caption = caption, onClose = { playing = null }) }
            }
        }
    } else {
        // Záložky složek jsou součástí obrazovky a přežijí přepnutí složky (fokus v nich zůstane);
        // pozice seznamu je zvlášť pro každou složku.
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
}

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
