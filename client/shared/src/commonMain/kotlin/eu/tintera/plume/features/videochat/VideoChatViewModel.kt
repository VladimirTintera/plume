package eu.tintera.plume.features.videochat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.calls.LiveStreamMedia
import eu.tintera.plume.core.telegram.VideoChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Obrazovka videochatu: poslouchá se, dokud je otevřená. Zavření (zpět i Opustit) z hovoru
 * odejde — poslech na pozadí s pruhem nad aplikací zatím není.
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

    fun leave() {
        watch?.stop()
    }

    override fun onCleared() {
        watch?.stop()
    }
}
