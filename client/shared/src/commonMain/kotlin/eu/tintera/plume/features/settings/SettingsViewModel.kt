package eu.tintera.plume.features.settings

import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import eu.tintera.plume.core.telegram.AccountRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.AccountInfo
import eu.tintera.plume.core.telegram.TelegramSession
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val session: TelegramSession,
    accounts: AccountRepository,
) : ViewModel() {

    /** Živě — po úpravě profilu (i z jiného zařízení) karta účtu nezůstane stará. */
    val account: StateFlow<AccountInfo?> = accounts.account
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun logOut() {
        viewModelScope.launch { session.logOut() }
    }
}
