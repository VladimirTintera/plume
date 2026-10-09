package eu.tintera.plume.features.settings

import androidx.lifecycle.ViewModel
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.settings.ChatSwipeAction
import eu.tintera.plume.core.settings.ChatSwipeActions
import kotlinx.coroutines.flow.StateFlow

/**
 * Akce tahu v seznamu chatů (Obecné → Chaty). Zápis běží v rozsahu [AppSettings], ne ve
 * `viewModelScope` — volba těsně před odchodem z obrazovky se tak neztratí (viz [AppearanceViewModel]).
 */
class ChatSwipeSettingsViewModel(
    private val settings: AppSettings,
) : ViewModel() {

    val actions: StateFlow<ChatSwipeActions> = settings.chatSwipeActions

    fun setRight(action: ChatSwipeAction) = settings.setChatSwipeActions(actions.value.copy(right = action))

    fun setLeft(action: ChatSwipeAction) = settings.setChatSwipeActions(actions.value.copy(left = action))
}
