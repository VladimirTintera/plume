package eu.tintera.plume.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.AccountInfo
import eu.tintera.plume.core.telegram.TelegramSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val session: TelegramSession,
) : ViewModel() {

    private val _account = MutableStateFlow<AccountInfo?>(null)
    val account: StateFlow<AccountInfo?> = _account.asStateFlow()

    init {
        viewModelScope.launch {
            session.me().onSuccess { _account.value = it }
        }
    }

    fun logOut() {
        viewModelScope.launch { session.logOut() }
    }
}
