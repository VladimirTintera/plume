package eu.tintera.plume.features.videochat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.video_chat_hand_failed
import eu.tintera.plume.resources.video_chat_microphone_denied
import eu.tintera.plume.resources.video_chat_mute_failed
import eu.tintera.plume.resources.video_chat_unmute_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Obrazovka videochatu nad [VideoChatSession]: otevření se k videochatu připojí (k běžícímu
 * jen naváže), zavření z něj NEODEJDE — posluchač zůstává s pruhem nad aplikací. Odejde se
 * jen [leave].
 */
class VideoChatViewModel(
    chatId: Long,
    private val groupCallId: Int,
    private val session: VideoChatSession,
) : ViewModel() {

    val supported: Boolean get() = session.supported

    /** Jen tenhle videochat; když běží jiný (pruh z jiné skupiny), obrazovka ho nepřebírá. */
    val state: StateFlow<VideoChatState?> = session.active
        .map { active -> active?.takeIf { it.groupCallId == groupCallId }?.state }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Nadpis, když správce videochat nepojmenoval. */
    val chatTitle: StateFlow<String> = session.active
        .map { active -> active?.takeIf { it.groupCallId == groupCallId }?.chatTitle.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    init {
        session.join(chatId, groupCallId)
    }

    private val _notice = MutableStateFlow<UiText?>(null)

    /** Hláška pro snackbar (mikrofon nejde zapnout, ruka nejde zvednout). */
    val notice: StateFlow<UiText?> = _notice.asStateFlow()

    fun noticeShown() {
        _notice.value = null
    }

    /** Obrazovka je vidět — pruh nad aplikací se schová. */
    fun screenVisible(visible: Boolean) = session.screenVisible(visible)

    /** Mluvit, nebo ztlumit se. Oprávnění k mikrofonu má mít obrazovka vyřízené předem. */
    fun setMuted(muted: Boolean) {
        viewModelScope.launch {
            session.setMuted(muted).onFailure { failure ->
                _notice.value = failure.userMessage(
                    if (muted) Res.string.video_chat_mute_failed else Res.string.video_chat_unmute_failed,
                )
            }
        }
    }

    fun setHandRaised(raised: Boolean) {
        viewModelScope.launch {
            session.setHandRaised(raised).onFailure { failure ->
                _notice.value = failure.userMessage(Res.string.video_chat_hand_failed)
            }
        }
    }

    /** Mikrofon zamítnutý v systému — mluvit nejde, poslouchat dál ano. */
    fun microphoneDenied() {
        _notice.value = UiText(Res.string.video_chat_microphone_denied)
    }

    fun leave() = session.leave()
}
