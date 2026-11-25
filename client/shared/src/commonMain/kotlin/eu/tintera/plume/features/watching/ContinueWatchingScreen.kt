package eu.tintera.plume.features.watching

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.accounts.AccountTopAppBar
import eu.tintera.plume.core.adaptive.ContentWidth
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.WithTooltip
import eu.tintera.plume.core.ui.contextClickable
import eu.tintera.plume.core.ui.groupRow
import eu.tintera.plume.core.ui.groupedItem
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.features.conversation.formatDuration
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.chat_24dp
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.continue_watching_empty
import eu.tintera.plume.resources.continue_watching_empty_title
import eu.tintera.plume.resources.continue_watching_progress
import eu.tintera.plume.resources.continue_watching_remove
import eu.tintera.plume.resources.continue_watching_removed
import eu.tintera.plume.resources.continue_watching_title
import eu.tintera.plume.resources.continue_watching_undo
import eu.tintera.plume.resources.media_show_in_chat
import eu.tintera.plume.resources.resume_24dp
import eu.tintera.plume.resources.sender_you
import eu.tintera.plume.resources.saved_messages
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Rozkoukaná videa (menu účtu): videa ze zpráv, která uživatel nedokoukal, nejnověji sledovaná
 * nahoře. Ťuknutí otevře video v prohlížeči — ten sám pokračuje z pamatované pozice („Pokračuje
 * od 12:34 · Od začátku"). Odebrání jen zapomene pozici (zmizí i proužek v bublině), zpráva
 * ani soubor se nemažou; s „Vrátit".
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ContinueWatchingScreen(
    viewModel: ContinueWatchingViewModel,
    onBack: () -> Unit,
    onOpenVideo: (chatId: Long, messageId: Long) -> Unit,
    onShowInChat: (chatId: Long, messageId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Snackbar s „Vrátit" běží mimo efekt: spotřebování události mění stav a efekt by ho zrušil.
    val snackbarScope = rememberCoroutineScope()
    val removal = state.removal
    LaunchedEffect(removal) {
        if (removal == null) return@LaunchedEffect
        viewModel.consumeRemoval()
        snackbarScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = getString(Res.string.continue_watching_removed),
                actionLabel = getString(Res.string.continue_watching_undo),
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undo(removal)
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AccountTopAppBar(
                title = { Text(stringResource(Res.string.continue_watching_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            val items = state.items
            val content = Modifier.widthIn(max = ContentWidth.Reading)
            when {
                items == null -> Box(content.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                items.isEmpty() -> IntroEmptyState(
                    icon = Res.drawable.resume_24dp,
                    title = stringResource(Res.string.continue_watching_empty_title),
                    text = stringResource(Res.string.continue_watching_empty),
                    modifier = content.padding(top = 48.dp),
                )
                else -> LazyColumn(
                    // Bez vodorovného okraje: řádky bloku (`groupedItem`) si ho dávají samy.
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = content.fillMaxSize(),
                ) {
                    itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
                        WatchingRow(
                            item = item,
                            index = index,
                            count = items.size,
                            onOpen = { onOpenVideo(item.chatId, item.messageId) },
                            onShowInChat = { onShowInChat(item.chatId, item.messageId) },
                            onRemove = { viewModel.remove(item) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchingRow(
    item: WatchingItem,
    index: Int,
    count: Int,
    onOpen: () -> Unit,
    onShowInChat: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = MaterialTheme.colorScheme.groupRow
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        ListItem(
            supportingContent = {
                val sender = if (item.isOutgoing) stringResource(Res.string.sender_you) else item.senderName
                val progress = stringResource(
                    Res.string.continue_watching_progress,
                    formatDuration((item.position.positionMs / 1000).toInt()),
                    formatDuration(item.video.durationSeconds),
                )
                Text(
                    listOfNotNull(sender?.takeIf { it.isNotBlank() }, progress).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingContent = { WatchingThumbnail(item) },
            trailingContent = {
                val label = stringResource(Res.string.continue_watching_remove)
                WithTooltip(label) {
                    IconButton(onClick = onRemove, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.close_24dp), contentDescription = label)
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = container),
            modifier = Modifier.groupedItem(index, count, container).contextClickable(onClick = onOpen, onLongClick = { menu = true }),
        ) {
            Text(
                if (item.isSavedMessages) stringResource(Res.string.saved_messages) else item.chatTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ExpressiveMenu(
            expanded = menu,
            onDismiss = { menu = false },
            groups = listOf(
                listOf(
                    MenuAction(stringResource(Res.string.media_show_in_chat), Res.drawable.chat_24dp) {
                        menu = false
                        onShowInChat()
                    },
                    MenuAction(stringResource(Res.string.continue_watching_remove), Res.drawable.close_24dp) {
                        menu = false
                        onRemove()
                    },
                ),
            ),
        )
    }
}

/**
 * Náhled videa s proužkem zhlédnutého dole — totéž, co ukazuje bublina v konverzaci. Bílý na
 * tmavé dráze: leží na libovolném obrázku a barva z motivu by na něm nebyla čitelná.
 */
@Composable
private fun WatchingThumbnail(item: WatchingItem) {
    Box(
        Modifier
            .size(width = THUMBNAIL_WIDTH, height = THUMBNAIL_HEIGHT)
            .clip(ShapeDefaults.Small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        val thumbnail = item.video.thumbnailFileId
        if (thumbnail != null) {
            RemoteImage(
                fileId = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = item.video.minithumbnail,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painterResource(Res.drawable.resume_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(WATCHED_BAR_HEIGHT)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
        ) {
            Box(Modifier.fillMaxWidth(item.position.fraction).fillMaxHeight().background(Color.White))
        }
    }
}

private val THUMBNAIL_WIDTH = 96.dp
private val THUMBNAIL_HEIGHT = 54.dp
private val WATCHED_BAR_HEIGHT = 3.dp
