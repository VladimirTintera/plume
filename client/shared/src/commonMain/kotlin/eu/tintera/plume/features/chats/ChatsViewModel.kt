package eu.tintera.plume.features.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatsViewModel(
    private val repository: ChatRepository,
) : ViewModel() {

    /**
     * Stav se bere přímo z repository — ta je singleton a drží živý seznam nezávisle na tom,
     * jestli obrazovka existuje. ViewModel tu nic nekešuje, jen přeposílá a spouští dotažení.
     */
    val state: StateFlow<ChatListState> = repository.chats

    fun loadMore() {
        viewModelScope.launch { repository.loadMore() }
    }
}
