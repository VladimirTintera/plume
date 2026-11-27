package eu.tintera.plume.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import eu.tintera.plume.core.telegram.ChatFolder

/**
 * Dvě úrovně: seznam chatů a otevřená konverzace; zpět tlačítkem ovladače. Na dvě obrazovky
 * stačí stav — knihovna navigace by tu jen přidala závislost.
 *
 * Seznam drží pozici a poslední otevřený chat tady, ne v sobě: s konverzací odchází z kompozice
 * a po návratu musí stát tam, kde byl, s fokusem na chatu, ze kterého uživatel odešel. Pozice
 * je zvlášť pro každou složku, ať přepnutí složky nezačne uprostřed jiného seznamu.
 */
@Composable
fun TvNavigation() {
    var folder by rememberSaveable(stateSaver = FolderSaver) { mutableStateOf<ChatFolder>(ChatFolder.Main) }
    var openChat by rememberSaveable { mutableStateOf<Long?>(null) }
    var lastOpened by rememberSaveable { mutableStateOf<Long?>(null) }
    val listStates = remember { mutableMapOf<ChatFolder, LazyListState>() }

    val current = openChat
    if (current != null) {
        BackHandler { openChat = null }
        key(current) { ConversationScreen(chatId = current) }
    } else {
        key(folder) {
            val listState = listStates.getOrPut(folder) { LazyListState() }
            ChatListScreen(
                folder = folder,
                onFolder = {
                    folder = it
                    lastOpened = null
                },
                listState = listState,
                lastOpenedChat = lastOpened,
                onOpen = { chatId ->
                    lastOpened = chatId
                    openChat = chatId
                },
            )
        }
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
