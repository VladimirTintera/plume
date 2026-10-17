package eu.tintera.plume.features.settings

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
 * Hudba ve vlastním profilu v úpravě profilu: přehrát, odebrat. Vlastní profil se jinde
 * neotevírá (Uložené zprávy profil nemají), takže správa patří sem, k bio a osobnímu kanálu.
 * Přidává se z nabídky nad zprávou se skladbou („Přidat do profilu"); prázdná sekce to říká.
 *
 * Vlastní ViewModel, ať úprava profilu nenese frontu hudby a přehrávač kvůli jedné sekci.
 */
class OwnProfileMusicViewModel(
    private val accounts: AccountRepository,
    private val profiles: ProfileRepository,
    private val musicQueue: MusicQueue,
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

    init {
        viewModelScope.launch {
            val id = accounts.account.filterNotNull().first().id
            _userId.value = id
            profiles.profileAudios(id)
                .onSuccess { _audios.value = it }
                .onFailure { failure ->
                    logger.w(failure) { "Hudbu ve vlastním profilu se nepodařilo načíst." }
                    _audios.value = emptyList()
                }
        }
    }

    fun toggle(index: Int) {
        val id = _userId.value ?: return
        val audios = _audios.value ?: return
        musicQueue.togglePlaylist(id, audios.map { QueueTrack(profileAudioKey(it.fileId), it) }, index)
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
@Composable
internal fun OwnProfileMusicSection(onNotice: (UiText) -> Unit) {
    val viewModel = koinViewModel<OwnProfileMusicViewModel>()
    val audios by viewModel.audios.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    LaunchedEffect(failure) {
        failure?.let {
            onNotice(it)
            viewModel.failureShown()
        }
    }
    val list = audios ?: return
    if (list.isEmpty()) {
        // Kde se skladba přidává, by jinak nikdo nezjistil: z úpravy profilu to nejde.
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(painterResource(Res.drawable.music_note_24dp), contentDescription = null) },
            headlineContent = { Text(stringResource(Res.string.profile_music)) },
            supportingContent = { Text(stringResource(Res.string.profile_music_empty)) },
        )
        return
    }
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val userId by viewModel.userId.collectAsStateWithLifecycle()
    ProfileMusic(
        audios = list,
        chatId = userId ?: return,
        playback = playback,
        onToggle = viewModel::toggle,
        onRemove = viewModel::remove,
    )
}
