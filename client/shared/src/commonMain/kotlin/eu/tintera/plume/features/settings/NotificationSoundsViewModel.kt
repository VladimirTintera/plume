package eu.tintera.plume.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.notifications.NotificationPermission
import eu.tintera.plume.core.telegram.NotificationScope
import eu.tintera.plume.core.telegram.NotificationSettingsRepository
import eu.tintera.plume.core.telegram.NotificationSoundRepository
import eu.tintera.plume.core.telegram.NotificationTone
import eu.tintera.plume.core.telegram.ToneLimits
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.notif_sound_delete_failed
import eu.tintera.plume.resources.notif_sound_save_failed
import eu.tintera.plume.resources.notif_sound_upload_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @property picking rozsah, jehož zvuk se právě vybírá; `null` = výběr zavřený.
 * @property uploading právě se nahrává tón.
 * @property error chyba — na snackbar, pak [consumeError].
 */
data class NotificationSoundsState(
    val picking: NotificationScope? = null,
    val limits: ToneLimits? = null,
    val uploading: Boolean = false,
    val error: UiText? = null,
)

/**
 * Zvuky notifikací z Telegramu: tóny účtu (živě) a výběr zvuku rozsahu. Zvuk samotný se zapisuje
 * do nastavení rozsahu (`setScopeSound`); řádek v sekci ho ukáže, až ho TDLib potvrdí updatem.
 */
class NotificationSoundsViewModel(
    private val sounds: NotificationSoundRepository,
    private val settings: NotificationSettingsRepository,
    private val permission: NotificationPermission? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationSoundsState())
    val state: StateFlow<NotificationSoundsState> = _state.asStateFlow()

    val tones: StateFlow<List<NotificationTone>> =
        sounds.tones.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { _state.update { it.copy(limits = sounds.limits()) } }
    }

    fun open(scope: NotificationScope) = _state.update { it.copy(picking = scope) }

    fun close() = _state.update { it.copy(picking = null) }

    fun select(scope: NotificationScope, soundId: Long) {
        viewModelScope.launch {
            settings.setScopeSound(scope, soundId).onFailure { failure ->
                _state.update { it.copy(error = failure.userMessage(Res.string.notif_sound_save_failed)) }
            }
        }
    }

    /** Zvuk jednoho chatu (profil); `null` = podle výchozího nastavení jeho rozsahu. */
    fun selectForChat(chatId: Long, soundId: Long?) {
        viewModelScope.launch {
            settings.setChatSound(chatId, soundId).onFailure { failure ->
                _state.update { it.copy(error = failure.userMessage(Res.string.notif_sound_save_failed)) }
            }
        }
    }

    /** Zvuk tématu skupiny; `null` = jako skupina. */
    fun selectForTopic(chatId: Long, topicId: Int, soundId: Long?) {
        viewModelScope.launch {
            settings.setTopicSound(chatId, topicId, soundId).onFailure { failure ->
                _state.update { it.copy(error = failure.userMessage(Res.string.notif_sound_save_failed)) }
            }
        }
    }

    /** Nahraje zvukový soubor; strop hlídá server, hláška ho připomene. */
    fun upload(path: String) {
        if (_state.value.uploading) return
        _state.update { it.copy(uploading = true) }
        viewModelScope.launch {
            val limits = _state.value.limits ?: sounds.limits()
            sounds.add(path)
                .onSuccess { _state.update { it.copy(uploading = false) } }
                .onFailure {
                    _state.update {
                        it.copy(
                            uploading = false,
                            error = UiText(Res.string.notif_sound_upload_failed, (limits.maxBytes / 1024).toInt(), limits.maxSeconds),
                        )
                    }
                }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            sounds.remove(id).onSuccess { permission?.forgetTone(id) }.onFailure { failure ->
                _state.update { it.copy(error = failure.userMessage(Res.string.notif_sound_delete_failed)) }
            }
        }
    }

    /** Tón na disku pro ukázku. */
    suspend fun tonePath(id: Long): String? = sounds.tonePath(id)

    fun consumeError() = _state.update { it.copy(error = null) }
}
