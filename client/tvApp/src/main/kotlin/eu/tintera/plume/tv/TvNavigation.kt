package eu.tintera.plume.tv

import androidx.activity.compose.BackHandler
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.launch
import eu.tintera.plume.resources.tv_link_unsupported
import eu.tintera.plume.core.telegram.InternalLink
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateListOf
import android.widget.Toast
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
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.telegram.ChatRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import eu.tintera.plume.core.telegram.ChatInfo
import eu.tintera.plume.core.telegram.ConversationRepository
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
    // Zpráva, na kterou má otevřený chat skočit (hledání, Rozkoukaná videa); `null` = konec historie.
    var openedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var lastOpened by rememberSaveable { mutableStateOf<Long?>(null) }
    var section by rememberSaveable { mutableStateOf(ListSection.Chats) }
    val search = remember { TvSearchState() }
    var storiesOf by remember { mutableStateOf<Long?>(null) }
    var lastStories by remember { mutableStateOf<Long?>(null) }
    val listStates = remember { mutableMapOf<ChatFolder, LazyListState>() }
    var playing by remember { mutableStateOf<VideoRequest?>(null) }
    var viewer by remember { mutableStateOf<Pair<List<ViewerItem>, Int>?>(null) }
    // Otevřený článek (Instant View): adresa stránky.
    var article by remember { mutableStateOf<String?>(null) }
    val audio = rememberTvAudioPlayer()
    val videoNotes = remember { VideoNotePlayback() }
    // Chaty, ze kterých se přešlo odkazem (chat a zpráva s odkazem): Zpět se vrací po nich,
    // teprve pak na seznam. Otevření chatu ze seznamu historii zahodí.
    val linkHistory = remember { mutableStateListOf<Pair<Long, Long?>>() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chatsRepository = koinInject<ChatRepository>()
    val actions = remember(audio, videoNotes) {
        MediaActions(
            playVideo = { playing = it },
            openViewer = { items, index -> viewer = items to index },
            playAudio = audio::play,
            toggleVideoNote = videoNotes::toggle,
            openArticle = { article = it },
            openLink = { url, fromMessageId ->
                scope.launch {
                    val target = when (val link = chatsRepository.resolveLink(url).getOrNull()) {
                        is InternalLink.Chat -> link.chatId to null
                        is InternalLink.Message -> link.chatId to link.messageId
                        // Pozvánka, bot, samolepky…: televize je neumí ukázat.
                        else -> null
                    }
                    if (target == null) {
                        Toast.makeText(context, getString(Res.string.tv_link_unsupported), Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    opened?.let { from -> linkHistory.add(from to fromMessageId) }
                    viewer = null
                    lastOpened = target.first
                    openedAt = target.second
                    opened = target.first
                }
            },
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
        openedAt = null
        opened = openChat
        onChatOpened()
    }

    CompositionLocalProvider(LocalAudioPlayer provides audio, LocalVideoNotes provides videoNotes) {
        // Tlačítko hledání na ovladači (KEYCODE_SEARCH, má ho část ovladačů): odkudkoli rovnou
        // na hledání s hlasovým zadáním. Mikrofon na ovladači Google TV si bere Asistent systému.
        Box(
            Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || event.nativeKeyEvent.keyCode != AndroidKeyEvent.KEYCODE_SEARCH) return@onPreviewKeyEvent false
                opened = null
                viewer = null
                playing = null
                storiesOf = null
                section = ListSection.Search
                search.voiceRequests++
                true
            },
        ) {
            val current = opened
            if (current != null) {
                BackHandler {
                    viewer = null
                    playing = null
                    article = null
                    // Přišlo se odkazem z jiného chatu: zpátky do něj, na zprávu s odkazem.
                    val previous = linkHistory.removeLastOrNull()
                    if (previous != null) {
                        lastOpened = previous.first
                        openedAt = previous.second
                        opened = previous.first
                    } else {
                        opened = null
                    }
                }
                key(current, openedAt) {
                    ChatScreen(
                        chatId = current,
                        startAt = openedAt,
                        overlayOpen = playing != null || viewer != null || article != null,
                        playingMessage = playing?.takeIf { it.chatId == current }?.messageId,
                        actions = actions,
                    )
                }
                article?.let { url ->
                    key(url) { InstantViewScreen(url = url, onClose = { article = null }) }
                }
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
                    section = section,
                    onFolder = {
                        folder = it
                        section = ListSection.Chats
                        lastOpened = null
                    },
                    onSection = { section = it },
                    listStateFor = { listStates.getOrPut(it) { LazyListState() } },
                    lastOpenedChat = lastOpened,
                    overlayOpen = playing != null || storiesOf != null,
                    onPlay = actions.playVideo,
                    onOpenStories = {
                        lastStories = it
                        storiesOf = it
                    },
                    lastStoriesChat = lastStories,
                    search = search,
                    onOpen = { chatId ->
                        linkHistory.clear()
                        lastOpened = chatId
                        openedAt = null
                        opened = chatId
                    },
                    onOpenMessage = { chatId, messageId ->
                        linkHistory.clear()
                        lastOpened = chatId
                        openedAt = messageId
                        opened = chatId
                    },
                )
            }
            storiesOf?.let { chatId ->
                key(chatId) { StoriesViewerScreen(startChatId = chatId, onClose = { storiesOf = null }) }
            }
            if (playing == null && storiesOf == null) AudioDock(audio, Modifier.align(Alignment.BottomCenter))
            playing?.let { request ->
                key(request.video.fileId) { VideoPlayerScreen(request = request, onNext = { playing = it }, onClose = { playing = null }) }
            }
        }
    }
}

/**
 * Otevřený chat: kanál jako feed příspěvků ([ChannelFeedScreen]), ostatní jako konverzace.
 * Druh chatu se zjistí z `chatInfo`, než se cokoli nakreslí. [playingMessage] je zpráva, jejíž
 * video právě hraje (s frontou se mění): obrazovka podle ní dočítá historii a po zavření
 * přehrávače stojí na ní.
 */
@Composable
private fun ChatScreen(chatId: Long, startAt: Long?, overlayOpen: Boolean, playingMessage: Long?, actions: MediaActions) {
    val conversations = koinInject<ConversationRepository>()
    val info by produceState<Result<ChatInfo>?>(null, chatId) { value = conversations.chatInfo(chatId) }
    val loaded = info?.getOrNull()
    when {
        info == null -> LoadingScreen(stringResource(Res.string.connection_connecting))
        loaded?.isChannel == true -> ChannelFeedScreen(chatId, loaded, overlayOpen, playingMessage, actions, startAt)
        else -> ConversationScreen(chatId = chatId, playerOpen = overlayOpen, playingMessage = playingMessage, actions = actions, startAt = startAt)
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
