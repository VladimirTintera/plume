package eu.tintera.plume.features.settings

import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.SheetTitle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.profileAudioMoves
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.ReorderRow
import eu.tintera.plume.core.ui.draggedItem
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.core.ui.rememberAttachmentPicker
import eu.tintera.plume.core.ui.rememberDragReorderState
import eu.tintera.plume.resources.add_24dp
import eu.tintera.plume.resources.drag_indicator_24dp
import eu.tintera.plume.resources.profile_music_add
import eu.tintera.plume.resources.profile_music_add_failed
import eu.tintera.plume.resources.profile_music_not_audio
import eu.tintera.plume.resources.profile_music_reorder
import eu.tintera.plume.resources.profile_music_reorder_failed
import eu.tintera.plume.resources.profile_music_reorder_title
import eu.tintera.plume.resources.reorder_done
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.audio.MusicQueue
import eu.tintera.plume.core.audio.QueueTrack
import eu.tintera.plume.core.audio.VoicePlayback
import eu.tintera.plume.core.audio.VoicePlayer
import eu.tintera.plume.core.telegram.AccountRepository
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.ProfileRepository
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.features.profile.ProfileMusic
import eu.tintera.plume.features.profile.profileAudioKey
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.music_note_24dp
import eu.tintera.plume.resources.profile_music
import eu.tintera.plume.resources.profile_music_empty
import eu.tintera.plume.resources.profile_music_remove_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Hudba ve vlastním profilu v úpravě profilu: přehrát, odebrat, přidat skladbu ze souboru
 * a uspořádat. Vlastní profil se jinde neotevírá (Uložené zprávy profil nemají), takže správa
 * patří sem, k bio a osobnímu kanálu. Přidat jde i z nabídky nad zprávou se skladbou
 * („Přidat do profilu").
 *
 * Vlastní ViewModel, ať úprava profilu nenese frontu hudby a přehrávač kvůli jedné sekci.
 */
class OwnProfileMusicViewModel(
    private val accounts: AccountRepository,
    private val profiles: ProfileRepository,
    private val musicQueue: MusicQueue,
    /** Účet profilu: skladby z něj hraje fronta jeho účtem. */
    private val slot: AccountSlot,
    voicePlayer: VoicePlayer,
) : ViewModel() {

    private val logger = Logger.withTag("OwnProfileMusic")

    /** Co právě hraje — tentýž přehrávač jako všude, zvuk je v aplikaci jen jeden. */
    val playback: StateFlow<VoicePlayback?> = voicePlayer.state

    private val _audios = MutableStateFlow<List<MessageMedia.Audio>?>(null)

    /** Skladby v profilu; `null` = ještě se načítají (sekce se do té doby nekreslí). */
    val audios: StateFlow<List<MessageMedia.Audio>?> = _audios.asStateFlow()

    private val _userId = MutableStateFlow<Long?>(null)

    /** Vlastní id = id chatu Uložených zpráv; tam vede ťuknutí na přehrávač v notifikaci. */
    val userId: StateFlow<Long?> = _userId.asStateFlow()

    private val _failure = MutableStateFlow<UiText?>(null)

    /** Jednorázová hláška o nepovedeném odebrání; obrazovka ji předá do svého snackbaru. */
    val failure: StateFlow<UiText?> = _failure.asStateFlow()

    private val _busy = MutableStateFlow(false)

    /** Skladba se nahrává, nebo se ukládá nové pořadí — tlačítka čekají. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        viewModelScope.launch {
            val id = accounts.account.filterNotNull().first().id
            _userId.value = id
            reload(id)
        }
    }

    /** Seznam znovu od serveru — po přidání a přeskládání; lokálně se neskládá. */
    private suspend fun reload(id: Long) {
        profiles.profileAudios(id)
            .onSuccess { _audios.value = it }
            .onFailure { failure ->
                logger.w(failure) { "Hudbu ve vlastním profilu se nepodařilo načíst." }
                if (_audios.value == null) _audios.value = emptyList()
            }
    }

    /**
     * Skladba ze souboru v zařízení. Přichází ze stejného výběru jako příloha zprávy, takže
     * hudba je už [OutgoingAttachment.Audio] i se značkami; cokoli jiného skladba není.
     */
    fun add(picked: List<OutgoingAttachment>) {
        val id = _userId.value ?: return
        val audio = picked.firstOrNull() as? OutgoingAttachment.Audio
        if (audio == null) {
            _failure.value = UiText(Res.string.profile_music_not_audio)
            return
        }
        viewModelScope.launch {
            _busy.value = true
            profiles.addProfileAudioFile(audio)
                .onSuccess { reload(id) }
                .onFailure { failure ->
                    logger.w(failure) { "Skladbu se do profilu nepodařilo přidat." }
                    _failure.value = failure.userMessage(Res.string.profile_music_add_failed)
                }
            _busy.value = false
        }
    }

    /** Výběr souboru selhal (soubor nejde přečíst); hláška od výběru. */
    fun pickFailed(reason: UiText) {
        _failure.value = reason
    }

    /**
     * Nové pořadí [target] (id souborů). Na server jde jen tolik tahů, kolik je potřeba
     * ([profileAudioMoves]); nepovedený tah zbytek zastaví a seznam se načte, jak skutečně je.
     */
    fun reorder(target: List<Int>) {
        val id = _userId.value ?: return
        val current = _audios.value?.map { it.fileId } ?: return
        val moves = profileAudioMoves(current, target)
        if (moves.isEmpty()) return
        viewModelScope.launch {
            _busy.value = true
            _audios.update { list -> list?.sortedBy { target.indexOf(it.fileId) } }
            for ((fileId, after) in moves) {
                val failure = profiles.moveProfileAudio(fileId, after).exceptionOrNull() ?: continue
                logger.w(failure) { "Pořadí hudby v profilu se nepodařilo uložit." }
                _failure.value = failure.userMessage(Res.string.profile_music_reorder_failed)
                break
            }
            reload(id)
            _busy.value = false
        }
    }

    fun toggle(index: Int) {
        val id = _userId.value ?: return
        val audios = _audios.value ?: return
        musicQueue.togglePlaylist(slot, id, audios.map { QueueTrack(profileAudioKey(it.fileId), it) }, index)
    }

    fun remove(fileId: Int) {
        viewModelScope.launch {
            profiles.removeProfileAudio(fileId)
                .onSuccess { _audios.update { list -> list?.filterNot { it.fileId == fileId } } }
                .onFailure { failure ->
                    logger.w(failure) { "Skladbu se z profilu nepodařilo odebrat." }
                    _failure.value = UiText(Res.string.profile_music_remove_failed)
                }
        }
    }

    fun failureShown() {
        _failure.value = null
    }
}

/** Sekce hudby v úpravě profilu; [onNotice] předá hlášku snackbaru obrazovky. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun OwnProfileMusicSection(onNotice: (UiText) -> Unit) {
    val viewModel = koinViewModel<OwnProfileMusicViewModel>()
    val audios by viewModel.audios.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    LaunchedEffect(failure) {
        failure?.let {
            onNotice(it)
            viewModel.failureShown()
        }
    }
    // Tentýž výběr jako „Soubor" u přílohy: hudbu z něj platforma přečte i se značkami.
    val picker = rememberAttachmentPicker(onPicked = viewModel::add, onError = viewModel::pickFailed)
    var reordering by remember { mutableStateOf(false) }
    val list = audios ?: return
    Column {
        if (list.isEmpty()) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(painterResource(Res.drawable.music_note_24dp), contentDescription = null) },
                supportingContent = { Text(stringResource(Res.string.profile_music_empty)) },
            ) { Text(stringResource(Res.string.profile_music)) }
        } else {
            val playback by viewModel.playback.collectAsStateWithLifecycle()
            val userId by viewModel.userId.collectAsStateWithLifecycle()
            ProfileMusic(
                audios = list,
                chatId = userId ?: return@Column,
                playback = playback,
                onToggle = viewModel::toggle,
                onRemove = viewModel::remove,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        ) {
            TextButton(onClick = picker::pickAudio, enabled = !busy, shapes = ButtonDefaults.shapes()) {
                Icon(painterResource(Res.drawable.add_24dp), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(Res.string.profile_music_add))
            }
            if (list.size > 1) {
                TextButton(onClick = { reordering = true }, enabled = !busy, shapes = ButtonDefaults.shapes()) {
                    Icon(
                        painterResource(Res.drawable.drag_indicator_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(Res.string.profile_music_reorder))
                }
            }
            if (busy) LoadingIndicator(Modifier.size(24.dp))
        }
    }
    if (reordering) {
        ProfileMusicReorderSheet(
            audios = list,
            onDone = { order ->
                reordering = false
                viewModel.reorder(order)
            },
            onDismiss = { reordering = false },
        )
    }
}

/**
 * Uspořádání hudby: celý seznam (i skladby, které sekce schovává pod „Zobrazit všech") a úchyty
 * jako u připnutých chatů. Pořadí se drží tady a na server jde až „Hotovo" — tah po tahu by
 * každé přetažení byl dotaz a nepovedený by nechal profil napůl přeskládaný.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileMusicReorderSheet(
    audios: List<MessageMedia.Audio>,
    onDone: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    val order = remember(audios) { audios.toMutableStateList() }
    val sheetState = rememberExpandedSheetState()
    val listState = rememberLazyListState()
    fun move(from: Int, to: Int) {
        if (from !in order.indices || to !in order.indices) return
        order.add(to, order.removeAt(from))
    }
    val dragState = rememberDragReorderState(
        listState = listState,
        canMove = { true },
        onMove = { from, to -> move(order.indexOfFirst { it.fileId == from }, order.indexOfFirst { it.fileId == to }) },
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
            ) {
                SheetTitle(stringResource(Res.string.profile_music_reorder_title), modifier = Modifier.weight(1f))
                TextButton(onClick = { onDone(order.map { it.fileId }) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.reorder_done)) }
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
                modifier = Modifier.fillMaxWidth().heightIn(max = REORDER_LIST_HEIGHT),
            ) {
                itemsIndexed(order, key = { _, audio -> audio.fileId }) { index, audio ->
                    ReorderRow(
                        key = audio.fileId,
                        title = audio.title.ifBlank { audio.fileName },
                        shape = groupItemShape(index, order.size),
                        dragState = dragState,
                        onMoveUp = if (index > 0) { { move(index, index - 1) } } else null,
                        onMoveDown = if (index < order.lastIndex) { { move(index, index + 1) } } else null,
                        modifier = Modifier.draggedItem(dragState, audio.fileId),
                    ) {
                        Icon(painterResource(Res.drawable.music_note_24dp), contentDescription = null)
                    }
                }
            }
        }
    }
}

/** Strop výšky seznamu v sheetu; delší se roluje a „Hotovo" zůstane nahoře vidět. */
private val REORDER_LIST_HEIGHT = 480.dp
