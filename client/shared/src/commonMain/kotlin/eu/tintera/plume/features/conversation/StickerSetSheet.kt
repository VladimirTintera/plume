package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import eu.tintera.plume.core.files.MediaSharer
import eu.tintera.plume.core.share.ShareRequests
import eu.tintera.plume.core.telegram.stickerSetLink
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.TextClipboard
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.resources.content_copy_24dp
import eu.tintera.plume.resources.emoji_count
import eu.tintera.plume.resources.forward_24dp
import eu.tintera.plume.resources.invite_links_share_failed
import eu.tintera.plume.resources.share_24dp
import eu.tintera.plume.resources.sticker_set_copy_link
import eu.tintera.plume.resources.sticker_set_link_copied
import eu.tintera.plume.resources.sticker_set_send_to_chat
import eu.tintera.plume.resources.sticker_set_share
import eu.tintera.plume.resources.sticker_set_share_elsewhere
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.StickerItem
import eu.tintera.plume.core.telegram.StickerRepository
import eu.tintera.plume.core.telegram.StickerSetDetail
import eu.tintera.plume.core.ui.toggle
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_send
import eu.tintera.plume.resources.stickers_add_set
import eu.tintera.plume.resources.stickers_count
import eu.tintera.plume.resources.stickers_remove_set
import eu.tintera.plume.resources.stickers_set_load_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

sealed interface StickerSetState {
    data object Loading : StickerSetState
    data object Failed : StickerSetState
    data class Loaded(
        val detail: StickerSetDetail,
        val favoriteFileIds: Set<Int>,
        /** Přidání/odebrání právě běží — tlačítko se nedá zmáčknout dvakrát. */
        val changing: Boolean,
    ) : StickerSetState
}

/**
 * Sada samolepek otevřená ze zprávy.
 *
 * ViewModel má klíč s id sady (viz [StickerSetSheet]) — jinak by druhá otevřená sada dostala
 * ViewModel té první, stejná past jako u konverzace.
 */
class StickerSetViewModel(
    private val setId: Long,
    private val repository: StickerRepository,
    private val clipboard: TextClipboard,
    private val sharer: MediaSharer,
    private val shareRequests: ShareRequests,
) : ViewModel() {

    /** Hláška v sheetu (odkaz zkopírován, sdílení nevyšlo); po zobrazení se vynuluje. */
    private val _notice = MutableStateFlow<UiText?>(null)
    val notice: StateFlow<UiText?> = _notice

    fun consumeNotice() {
        _notice.value = null
    }

    private fun link(): String? =
        (state.value as? StickerSetState.Loaded)?.detail?.name?.takeIf { it.isNotBlank() }?.let(::stickerSetLink)

    fun copyLink() {
        val link = link() ?: return
        viewModelScope.launch {
            clipboard.copy(link)
            if (!clipboard.confirmsCopy) _notice.value = UiText(Res.string.sticker_set_link_copied)
        }
    }

    /** Systémový dialog sdílení — poslat odkaz mimo Telegram (zpráva, e-mail). */
    fun shareLink() {
        val link = link() ?: return
        if (!sharer.shareText(link)) _notice.value = UiText(Res.string.invite_links_share_failed)
    }

    /**
     * Poslat odkaz do chatu v Plume: tentýž čip „Sdílíte: …" jako sdílení z jiné aplikace
     * (`ShareRequests`). Odkaz u příjemce v Telegramu otevře náhled sady.
     */
    fun sendLinkToChat() {
        val link = link() ?: return
        shareRequests.start(link, hasFiles = false)
    }

    private val changing = MutableStateFlow(false)

    val state: StateFlow<StickerSetState> = combine(
        repository.stickerSetDetail(setId),
        repository.favoriteStickers().map { list -> list.map { it.sendFileId }.toSet() },
        changing,
    ) { detail, favorites, isChanging ->
        if (detail == null) {
            StickerSetState.Failed
        } else {
            StickerSetState.Loaded(detail, favorites, isChanging)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StickerSetState.Loading)

    /**
     * Přidá sadu, nebo ji odebere. Stav tlačítka se lokálně nepřepisuje — potvrzení přijde updatem
     * nainstalovaných sad. Do té doby je tlačítko neaktivní, aby nešlo zmáčknout dvakrát.
     */
    fun toggleInstalled() {
        val loaded = state.value as? StickerSetState.Loaded ?: return
        if (loaded.changing) return
        changing.value = true

        viewModelScope.launch {
            repository.setInstalled(setId, !loaded.detail.isInstalled)
            changing.value = false
        }
    }

    fun toggleFavorite(sticker: StickerItem) {
        val loaded = state.value as? StickerSetState.Loaded ?: return
        val favorite = sticker.sendFileId in loaded.favoriteFileIds
        viewModelScope.launch { repository.setFavorite(sticker.sendFileId, !favorite) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * Sada samolepek v bottom sheetu — otevírá se ťuknutím na samolepku v konverzaci.
 *
 * Ukazuje celou sadu a dá se z ní **přidat** mezi své (nebo odebrat). Mřížka se chová jako ve výběru:
 * ťuknutí samolepku pošle do tohoto chatu, podržení nabídne hvězdičku oblíbených. Sada hraje celá.
 *
 * Odkaz na sadu jde poslat do chatu, zkopírovat nebo sdílet jinam (nabídka vedle názvu).
 *
 * Bez [onPick] (otevřená z Nastavení) se jen prohlíží: ťuknutí otevře velký náhled a v něm
 * tlačítko Poslat není — není kam.
 * Výška mřížky je stejná jako ve výběru, aby oba sheety působily jako jedna věc.
 */
@Composable
fun StickerSetSheet(
    setId: Long,
    onPick: ((StickerItem) -> Unit)?,
    onDismiss: () -> Unit,
) {
    val viewModel: StickerSetViewModel = koinViewModel(
        key = "sticker-set-$setId",
        parameters = { parametersOf(setId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    /*
     * Rovnou celý, bez napůl rozbaleného stavu. Obsah je vyšší než polovina obrazovky, takže
     * napůl rozbalený sheet schoval spodek pod okraj — tlačítko skončilo pod navigační lištou.
     */
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    // Samolepka ve velkém náhledu (podržením), jako ve výběru.
    var previewSticker by remember { mutableStateOf<StickerItem?>(null) }
    // Hlášky patří DO sheetu: snackbar obrazovky pod ním by zakrylo jeho okno.
    val snackbarHostState = remember { SnackbarHostState() }
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    LaunchedEffect(notice) {
        val text = notice ?: return@LaunchedEffect
        viewModel.consumeNotice()
        scope.launch { snackbarHostState.showSnackbar(text.resolveNow()) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Box {
            Column {
                when (val current = state) {
                    StickerSetState.Loading -> Box(
                        Modifier.fillMaxWidth().height(STICKER_PICKER_HEIGHT),
                        contentAlignment = Alignment.Center,
                    ) { LoadingIndicator() }

                    StickerSetState.Failed -> Box(
                        Modifier.fillMaxWidth().height(STICKER_PICKER_HEIGHT),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(Res.string.stickers_set_load_failed),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }

                    is StickerSetState.Loaded -> {
                        val detail = current.detail
                        val cells = StickerCells(
                            activeStickerId = previewSticker?.id,
                            favoriteFileIds = current.favoriteFileIds,
                            onPick = { sticker ->
                                if (onPick == null) {
                                    // Vlastní emoji náhled nemá: hvězdička oblíbených patří samolepkám.
                                    if (!detail.isCustomEmoji) previewSticker = sticker
                                } else {
                                    scope.launch { sheetState.hide() }.invokeOnCompletion { onPick(sticker) }
                                }
                            },
                            // Podržení si haptiku dělá `combinedClickable` samo — viz `Haptics`.
                            onPreview = { sticker -> if (!detail.isCustomEmoji) previewSticker = sticker },
                            onToggleFavorite = { sticker ->
                                haptics.toggle(on = sticker.sendFileId !in current.favoriteFileIds)
                                viewModel.toggleFavorite(sticker)
                            },
                            // Jedna sada na celý sheet — hraje celá, jako mřížka ve výběru.
                            playing = true,
                        )
                        StickerPreviewHost(
                            sticker = previewSticker,
                            cells = cells,
                            pickLabel = onPick?.let { stringResource(Res.string.conversation_send) },
                            onClose = { previewSticker = null },
                        )

                        Row(
                            Modifier.padding(start = 24.dp, end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = detail.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = if (detail.isCustomEmoji) {
                                pluralStringResource(Res.plurals.emoji_count, detail.stickers.size, detail.stickers.size)
                            } else {
                                stickerCount(detail.stickers.size)
                            },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            // Sada bez jména (nemělo by se stát) odkaz mít nemůže, nabídka pak není.
                            if (detail.name.isNotBlank()) {
                                StickerSetLinkMenu(
                                    onSendToChat = {
                                        // Výběr chatu se otevře nad aplikací; sheet by mu překážel.
                                        viewModel.sendLinkToChat()
                                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                                    },
                                    onCopy = viewModel::copyLink,
                                    onShare = viewModel::shareLink,
                                )
                            }
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(STICKER_CELL),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.fillMaxWidth().height(STICKER_PICKER_HEIGHT),
                        ) {
                            items(detail.stickers, key = { it.id }) { sticker ->
                                StickerCell(sticker, cells, animateStickerItem())
                            }
                        }

                        /*
                         * Přidat je hlavní akce (plné tlačítko), Odebrat vedlejší (tonální) — kdo si sadu
                         * otevřel z cizí zprávy, chce ji spíš přidat. Oficiální sady odebrat nejdou,
                         * tlačítko u nich není.
                         */
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            when {
                                !detail.isInstalled -> Button(
                                    onClick = viewModel::toggleInstalled,
                                    enabled = !current.changing,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(stringResource(Res.string.stickers_add_set)) }

                                !detail.isOfficial -> FilledTonalButton(
                                    onClick = viewModel::toggleInstalled,
                                    enabled = !current.changing,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(stringResource(Res.string.stickers_remove_set)) }
                            }
                        }
                    }
                }
            }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** Nabídka odkazu na sadu: poslat do chatu, zkopírovat, sdílet jinam. */
@Composable
private fun StickerSetLinkMenu(onSendToChat: () -> Unit, onCopy: () -> Unit, onShare: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(Res.drawable.share_24dp), contentDescription = stringResource(Res.string.sticker_set_share))
        }
        ExpressiveMenu(
            expanded = open,
            onDismiss = { open = false },
            groups = listOf(
                listOf(
                    MenuAction(stringResource(Res.string.sticker_set_send_to_chat), Res.drawable.forward_24dp) {
                        open = false
                        onSendToChat()
                    },
                    MenuAction(stringResource(Res.string.sticker_set_copy_link), Res.drawable.content_copy_24dp) {
                        open = false
                        onCopy()
                    },
                    MenuAction(stringResource(Res.string.sticker_set_share_elsewhere), Res.drawable.share_24dp) {
                        open = false
                        onShare()
                    },
                ),
            ),
        )
    }
}

/** Skloňování řeší `plurals` v prostředcích — čeština má tři tvary, angličtina dva. */
@Composable
private fun stickerCount(count: Int): String =
    pluralStringResource(Res.plurals.stickers_count, count, count)
