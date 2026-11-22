package eu.tintera.plume.features.settings

import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import eu.tintera.plume.core.telegram.AccountRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.ai.core.OnDeviceAi
import eu.tintera.plume.core.telegram.AccountInfo
import eu.tintera.plume.core.telegram.TelegramSession
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val session: TelegramSession,
    accounts: AccountRepository,
    ai: OnDeviceAi,
) : ViewModel() {

    /** Živě — po úpravě profilu (i z jiného zařízení) karta účtu nezůstane stará. */
    val account: StateFlow<AccountInfo?> = accounts.account
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Jestli ukázat sekci AI v zařízení. Ve ViewModelu, ne `collectAsState(initial = false)`
     * v obrazovce: seznam se po návratu z podstránky skládá znovu a s výchozím `false` by řádek
     * v prvním snímku chyběl — obsah by byl kratší a obnovená pozice scrollu by se ořízla
     * (viz `rememberRestoringScrollState`). ViewModel přežije a poslední hodnotu zná hned.
     */
    val aiAvailable: StateFlow<Boolean> = ai.availability()
        .map { it.providers.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun logOut() {
        viewModelScope.launch { session.logOut() }
    }
}
