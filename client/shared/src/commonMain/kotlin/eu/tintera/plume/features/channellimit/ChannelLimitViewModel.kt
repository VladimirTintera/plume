package eu.tintera.plume.features.channellimit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.navigation.ChannelLimitRequests
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.channel_limit_failed
import eu.tintera.plume.resources.channel_limit_leave_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChannelLimitState(
    /** Neaktivní skupiny a kanály; `null`, dokud se nenačtou. */
    val chats: List<ChatSummary>? = null,
    val selected: Set<Long> = emptySet(),
    val leaving: Boolean = false,
    val error: UiText? = null,
)

/**
 * Výběr neaktivních skupin a kanálů k opuštění, když uživatel narazí na jejich limit
 * ([ChannelLimitRequests]). Napřed vybrané není nic: opouští se chaty, a o tom má rozhodnout
 * uživatel, ne pořadí od serveru.
 */
class ChannelLimitViewModel(
    private val chats: ChatRepository,
    private val requests: ChannelLimitRequests,
) : ViewModel() {

    private val logger = Logger.withTag("ChannelLimit")

    private val _state = MutableStateFlow(ChannelLimitState())
    val state: StateFlow<ChannelLimitState> = _state.asStateFlow()

    val isPremium: StateFlow<Boolean> get() = chats.isPremium

    /** Sheet se otevřel: seznam se čte pokaždé znovu, neaktivní se mezitím mohly změnit. */
    fun load() {
        _state.value = ChannelLimitState()
        viewModelScope.launch {
            chats.inactiveGroups()
                .onSuccess { found -> _state.update { it.copy(chats = found) } }
                .onFailure { _state.update { it.copy(chats = emptyList(), error = UiText(Res.string.channel_limit_failed)) } }
        }
    }

    fun toggle(chatId: Long) {
        _state.update { state ->
            if (state.leaving) return@update state
            state.copy(selected = if (chatId in state.selected) state.selected - chatId else state.selected + chatId)
        }
    }

    /**
     * Opustí vybrané, po jednom (`leaveChat` přes [ChatRepository.deleteOrLeave], tatáž cesta jako
     * „Opustit" v seznamu chatů). Když všechno prošlo, sheet se zavře — místo se uvolnilo a vstup
     * jde zkusit znovu. Co neprošlo, zůstane v seznamu i vybrané.
     */
    fun leaveSelected() {
        val current = _state.value
        if (current.leaving || current.selected.isEmpty()) return
        _state.update { it.copy(leaving = true, error = null) }
        viewModelScope.launch {
            val failed = current.selected.filter { chatId ->
                chats.deleteOrLeave(chatId)
                    .onFailure { logger.w(it) { "Chat $chatId se nepodařilo opustit." } }
                    .isFailure
            }.toSet()
            if (failed.isEmpty()) {
                dismiss()
            } else {
                _state.update { state ->
                    state.copy(
                        chats = state.chats?.filter { it.id !in current.selected || it.id in failed },
                        selected = failed,
                        leaving = false,
                        error = UiText(Res.string.channel_limit_leave_failed),
                    )
                }
            }
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }

    fun dismiss() {
        requests.dismiss()
        _state.value = ChannelLimitState()
    }
}
