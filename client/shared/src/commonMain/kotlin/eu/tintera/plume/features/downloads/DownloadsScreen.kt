package eu.tintera.plume.features.downloads

import eu.tintera.plume.core.accounts.AccountTopAppBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.accounts.AccountBadge
import eu.tintera.plume.core.accounts.AccountScope
import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.adaptive.ContentWidth
import eu.tintera.plume.core.telegram.AccountInfo
import eu.tintera.plume.core.telegram.FileDownloadItem
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.WithTooltip
import eu.tintera.plume.core.ui.contextClickable
import eu.tintera.plume.core.ui.fileSizeText
import eu.tintera.plume.core.ui.groupRow
import eu.tintera.plume.core.ui.groupedItem
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_back
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.description_24dp
import eu.tintera.plume.resources.download_24dp
import eu.tintera.plume.resources.downloads_clear_completed
import eu.tintera.plume.resources.downloads_completed
import eu.tintera.plume.resources.downloads_empty
import eu.tintera.plume.resources.downloads_empty_title
import eu.tintera.plume.resources.downloads_in_progress
import eu.tintera.plume.resources.downloads_pause
import eu.tintera.plume.resources.downloads_pause_all
import eu.tintera.plume.resources.downloads_paused
import eu.tintera.plume.resources.downloads_remove
import eu.tintera.plume.resources.downloads_remove_and_delete
import eu.tintera.plume.resources.downloads_resume
import eu.tintera.plume.resources.downloads_resume_all
import eu.tintera.plume.resources.downloads_title
import eu.tintera.plume.resources.more_vert_24dp
import eu.tintera.plume.resources.pause_24dp
import eu.tintera.plume.resources.play_arrow_24dp
import eu.tintera.plume.resources.profile_more
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import eu.tintera.plume.core.files.DocumentKind
import eu.tintera.plume.core.files.documentKind
import eu.tintera.plume.core.files.opensInViewer
import eu.tintera.plume.core.telegram.DownloadKind
import eu.tintera.plume.core.ui.CountdownSnackbarHost
import eu.tintera.plume.core.ui.UNDO_COUNTDOWN_MS
import eu.tintera.plume.core.ui.asRelativeTime
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.core.ui.showCountdownSnackbar
import eu.tintera.plume.resources.chat_24dp
import eu.tintera.plume.resources.downloads_cleared
import eu.tintera.plume.resources.downloads_deleted
import eu.tintera.plume.resources.downloads_removed
import eu.tintera.plume.resources.downloads_undo
import eu.tintera.plume.resources.image_24dp
import eu.tintera.plume.resources.media_show_in_chat
import eu.tintera.plume.resources.music_note_24dp
import eu.tintera.plume.resources.videocam_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Stahování: co se stahuje (s průběhem a pozastavením) a co se nedávno stáhlo. Ťuknutí na
 * stažený soubor ho otevře (video a obrázek v prohlížeči médií, ostatní v systému), na
 * rozestahovaný zprávu v chatu; podržení nabídne zprávu v chatu a odebrání ze seznamu, i se
 * smazáním souboru — obojí s „Vrátit".
 *
 * V režimu Všechny účty je to společný seznam všech účtů ([sources], [mergeDownloads]) s odznakem
 * účtu u řádku; „Pozastavit vše" a „Vymazat stažené" platí pro všechny, řádek patří svému účtu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadsScreen(
    sources: List<DownloadSource>,
    onBack: () -> Unit,
    onOpenMessage: (slot: AccountSlot, chatId: Long, messageId: Long) -> Unit,
    onOpenMedia: (slot: AccountSlot, chatId: Long, messageId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val states = sources.map { source -> key(source.account.slot) { source.viewModel.state.collectAsStateWithLifecycle().value } }
    val merged = remember(states) { mergeDownloads(states) }
    val snackbarHostState = remember { SnackbarHostState() }
    // Snackbar s „Vrátit" běží mimo efekt: spotřebování události mění stav a efekt by ho zrušil.
    val snackbarScope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }

    sources.forEachIndexed { index, source ->
        key(source.account.slot) {
            val errorText = states[index].error?.resolve()
            LaunchedEffect(errorText) {
                if (errorText != null) {
                    source.viewModel.consumeError()
                    snackbarHostState.showSnackbar(errorText)
                }
            }
        }
    }

    // Odebrání čeká na lhůtu (`PendingDownloadRemovals`); soubor už ze seznamu zmizel.
    // „Vymazat stažené" ve společném seznamu odebírá ve všech účtech naráz: jeden snackbar
    // a „Vrátit" vrátí všechny — dva by se navzájem zahodily (předchozí se zavírá).
    val removals = states.map { it.removal }
    LaunchedEffect(removals) {
        val pending = removals.mapIndexedNotNull { index, removal -> removal?.let { sources[index] to it } }
        if (pending.isEmpty()) return@LaunchedEffect
        pending.forEach { (source, _) -> source.viewModel.consumeRemoval() }
        val removal = if (pending.size > 1) DownloadRemoval(batch = -1, DownloadRemoval.Kind.Cleared) else pending.single().second
        snackbarScope.launch {
            // Předchozí snackbar pryč: odpočet běží, až je tenhle vidět, a ve frontě by
            // odebrání doběhlo dřív, než by se „Vrátit" ukázalo.
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showCountdownSnackbar(
                message = readString(
                    when (removal.kind) {
                        DownloadRemoval.Kind.Removed -> Res.string.downloads_removed
                        DownloadRemoval.Kind.Deleted -> Res.string.downloads_deleted
                        DownloadRemoval.Kind.Cleared -> Res.string.downloads_cleared
                    },
                ),
                actionLabel = readString(Res.string.downloads_undo),
                durationMs = UNDO_COUNTDOWN_MS,
            )
            if (result == SnackbarResult.ActionPerformed) pending.forEach { (source, batch) -> source.viewModel.undoRemoval(batch) }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) { CountdownSnackbarHost(it) } },
        topBar = {
            AccountTopAppBar(
                showAccount = sources.size == 1,
                title = { Text(stringResource(Res.string.downloads_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                    }
                },
                actions = {
                    // Pozastavit / pokračovat vše: jen když se něco stahuje.
                    if (merged != null && merged.active.isNotEmpty()) {
                        val running = merged.anyRunning
                        val label = stringResource(if (running) Res.string.downloads_pause_all else Res.string.downloads_resume_all)
                        WithTooltip(label) {
                            val onClick = { states.forEachIndexed { index, state -> if (state.active.isNotEmpty()) sources[index].viewModel.setAllPaused(running) } }
                            IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
                                Icon(painterResource(if (running) Res.drawable.pause_24dp else Res.drawable.play_arrow_24dp), contentDescription = label)
                            }
                        }
                    }
                    if (merged != null && merged.completed.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { menu = true }, shapes = IconButtonDefaults.shapes()) {
                                Icon(painterResource(Res.drawable.more_vert_24dp), contentDescription = stringResource(Res.string.profile_more))
                            }
                            ExpressiveMenu(
                                expanded = menu,
                                onDismiss = { menu = false },
                                groups = listOf(
                                    listOf(
                                        MenuAction(stringResource(Res.string.downloads_clear_completed), Res.drawable.delete_24dp) {
                                            menu = false
                                            sources.forEach { it.viewModel.clearCompleted() }
                                        },
                                    ),
                                ),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            DownloadsContent(
                merged = merged,
                sources = sources,
                onOpen = { (source, item) ->
                    val slot = sources[source].account.slot
                    when {
                        !item.isCompleted -> onOpenMessage(slot, item.chatId, item.messageId)
                        item.showsInViewer() -> onOpenMedia(slot, item.chatId, item.messageId)
                        else -> sources[source].viewModel.openFile(item) { onOpenMessage(slot, item.chatId, item.messageId) }
                    }
                },
                onShowInChat = { (source, item) -> onOpenMessage(sources[source].account.slot, item.chatId, item.messageId) },
                onPause = { (source, item), paused -> sources[source].viewModel.setPaused(item, paused) },
                onRemove = { (source, item), deleteFile -> sources[source].viewModel.remove(item, deleteFile) },
                modifier = Modifier.widthIn(max = ContentWidth.Reading),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadsContent(
    merged: MergedDownloads?,
    sources: List<DownloadSource>,
    onOpen: (SourcedDownload) -> Unit,
    onShowInChat: (SourcedDownload) -> Unit,
    onPause: (SourcedDownload, Boolean) -> Unit,
    onRemove: (SourcedDownload, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        merged == null -> Box(modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
        merged.isEmpty -> IntroEmptyState(
            icon = Res.drawable.download_24dp,
            title = stringResource(Res.string.downloads_empty_title),
            text = stringResource(Res.string.downloads_empty),
            modifier = modifier.padding(top = 48.dp),
        )
        else -> LazyColumn(
            // Bez vodorovného okraje: řádky bloku (`groupedItem`) si ho dávají samy.
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = modifier.fillMaxSize(),
        ) {
            listOf(
                Res.string.downloads_in_progress to merged.active,
                Res.string.downloads_completed to merged.completed,
            ).forEach { (title, section) ->
                if (section.isEmpty()) return@forEach
                item(key = "header-$title") {
                    Text(
                        stringResource(title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 32.dp, top = 16.dp, bottom = 8.dp).semantics { heading() },
                    )
                }
                itemsIndexed(section, key = { _, download -> download.key }) { index, download ->
                    val row = @Composable { account: AccountInfo? ->
                        DownloadRow(download, index, section.size, account, onOpen, onShowInChat, onPause, onRemove, Modifier.animateItem())
                    }
                    // Ve společném seznamu se řádek skládá v účtu souboru: průběh čte z jeho TDLib.
                    val source = sources.getOrNull(download.source)
                    if (sources.size > 1 && source != null) AccountScope(source.account) { row(source.info) } else row(null)
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    download: SourcedDownload,
    index: Int,
    count: Int,
    account: AccountInfo?,
    onOpen: (SourcedDownload) -> Unit,
    onShowInChat: (SourcedDownload) -> Unit,
    onPause: (SourcedDownload, Boolean) -> Unit,
    onRemove: (SourcedDownload, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = download.item
    val container = MaterialTheme.colorScheme.groupRow
    var menu by remember { mutableStateOf(false) }
    // Průběh si řádek odebírá sám — seznam se kvůli každému procentu číst znovu nemusí.
    val progress = if (item.isCompleted) {
        null
    } else {
        val files = koinInject<FileRepository>()
        val file by remember(item.fileId) { files.observe(item.fileId) }.collectAsStateWithLifecycle(FileState.Pending)
        (file as? FileState.Downloading)?.progress ?: (if (file is FileState.Ready) 1f else 0f)
    }
    Box(modifier) {
        ListItem(
            headlineContent = { Text(item.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val parts = listOfNotNull(
                        item.chatTitle.takeIf { it.isNotBlank() },
                        item.size.takeIf { it > 0 }?.let { fileSizeText(it) },
                        if (item.isCompleted) item.completeDate.asRelativeTime() else null,
                        if (item.isPaused && !item.isCompleted) stringResource(Res.string.downloads_paused) else null,
                    )
                    Text(
                        parts.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (progress != null) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            leadingContent = {
                val icon = @Composable {
                    Icon(painterResource(item.icon()), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (account == null) {
                    icon()
                } else {
                    // Komu soubor patří: fotka účtu pod ikonou, jako odznak u avataru v seznamu chatů.
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        icon()
                        AccountBadge(account, rim = container, modifier = Modifier.align(Alignment.BottomStart))
                    }
                }
            },
            trailingContent = if (item.isCompleted) {
                null
            } else {
                {
                    val label = stringResource(if (item.isPaused) Res.string.downloads_resume else Res.string.downloads_pause)
                    WithTooltip(label) {
                        IconButton(onClick = { onPause(download, !item.isPaused) }, shapes = IconButtonDefaults.shapes()) {
                            Icon(painterResource(if (item.isPaused) Res.drawable.play_arrow_24dp else Res.drawable.pause_24dp), contentDescription = label)
                        }
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = container),
            modifier = Modifier.groupedItem(index, count, container).contextClickable(onClick = { onOpen(download) }, onLongClick = { menu = true }),
        )
        ExpressiveMenu(
            expanded = menu,
            onDismiss = { menu = false },
            groups = listOf(
                listOf(
                    MenuAction(stringResource(Res.string.media_show_in_chat), Res.drawable.chat_24dp) {
                        menu = false
                        onShowInChat(download)
                    },
                    MenuAction(stringResource(Res.string.downloads_remove), Res.drawable.close_24dp) {
                        menu = false
                        onRemove(download, false)
                    },
                ),
                // Smazání souboru je nevratné: vlastní skupina v chybové barvě.
                listOf(
                    MenuAction(stringResource(Res.string.downloads_remove_and_delete), Res.drawable.delete_24dp, destructive = true) {
                        menu = false
                        onRemove(download, true)
                    },
                ),
            ),
        )
    }
}

/** Ikona podle druhu souboru; u dokumentu podle přípony, ne podle `mimeType` od odesílatele. */
private fun FileDownloadItem.icon(): DrawableResource = when (kind) {
    DownloadKind.Audio -> Res.drawable.music_note_24dp
    DownloadKind.Video -> Res.drawable.videocam_24dp
    DownloadKind.Document -> when (documentKind(fileName, mimeType)) {
        DocumentKind.Image -> Res.drawable.image_24dp
        DocumentKind.Video -> Res.drawable.videocam_24dp
        DocumentKind.Other -> Res.drawable.description_24dp
    }
}

/**
 * Otevře se soubor v prohlížeči médií? Video ze zprávy vždycky (hraje ho prohlížeč i v chatu),
 * dokument s obrázkem nebo videem, jen když ho prohlížeč na téhle platformě umí — jako
 * ve sdílených souborech v profilu.
 */
private fun FileDownloadItem.showsInViewer(): Boolean = when (kind) {
    DownloadKind.Video -> true
    DownloadKind.Document -> opensInViewer(fileName, mimeType)
    DownloadKind.Audio -> false
}
