package eu.tintera.plume.features.videochat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.calls.LiveStreamMedia
import eu.tintera.plume.core.telegram.VideoChatRepository
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.video_chat_hand_failed
import eu.tintera.plume.resources.video_chat_microphone_denied
import eu.tintera.plume.resources.video_chat_mute_failed
import eu.tintera.plume.resources.video_chat_unmute_failed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Obrazovka videochatu: poslouchá se (a mluví), dokud je otevřená. Zavření (zpět
 * i Opustit) z hovoru odejde — poslech na pozadí s pruhem nad aplikací zatím není.
 */
class VideoChatViewModel(
    private val chatId: Long,
    groupCallId: Int,
    private val repository: VideoChatRepository,
    appScope: CoroutineScope,
    /** Přenos; `null` = platforma ho neumí (obrazovka to řekne větou). */
    media: LiveStreamMedia?,
) : ViewModel() {

    private val watch = media?.let { VideoChatWatch(repository, it, viewModelScope, appScope) }

    val supported: Boolean get() = watch != null

    val state: StateFlow<VideoChatState?> = watch?.state ?: MutableStateFlow(null)

    private val _chatTitle = MutableStateFlow("")

    /** Nadpis, když správce videochat nepojmenoval. */
    val chatTitle: StateFlow<String> = _chatTitle.asStateFlow()

    init {
        watch?.watch(groupCallId)
        viewModelScope.launch { repository.chatTitle(chatId)?.let { _chatTitle.value = it } }
    }

    private val _notice = MutableStateFlow<UiText?>(null)

    /** Hláška pro snackbar (mikrofon nejde zapnout, ruka nejde zvednout). */
    val notice: StateFlow<UiText?> = _notice.asStateFlow()

    fun noticeShown() {
        _notice.value = null
    }

    /** Mluvit, nebo ztlumit se. Oprávnění k mikrofonu má mít obrazovka vyřízené předem. */
    fun setMuted(muted: Boolean) {
        val watch = watch ?: return
        viewModelScope.launch {
            watch.setMuted(muted).onFailure { failure ->
                _notice.value = failure.userMessage(
                    if (muted) Res.string.video_chat_mute_failed else Res.string.video_chat_unmute_failed,
                )
            }
        }
    }

    fun setHandRaised(raised: Boolean) {
        val watch = watch ?: return
        viewModelScope.launch {
            watch.setHandRaised(raised).onFailure { failure ->
                _notice.value = failure.userMessage(Res.string.video_chat_hand_failed)
            }
        }
    }

    /** Mikrofon zamítnutý v systému — mluvit nejde, poslouchat dál ano. */
    fun microphoneDenied() {
        _notice.value = UiText(Res.string.video_chat_microphone_denied)
    }

    fun leave() {
        watch?.stop()
    }

    override fun onCleared() {
        watch?.stop()
    }
}
