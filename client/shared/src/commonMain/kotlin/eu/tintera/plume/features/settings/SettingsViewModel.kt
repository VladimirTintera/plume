package eu.tintera.plume.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.notifications.NotificationPermission
import eu.tintera.plume.core.telegram.AccountInfo
import eu.tintera.plume.core.telegram.TelegramSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val session: TelegramSession,
    private val permission: NotificationPermission,
) : ViewModel() {

    private val _account = MutableStateFlow<AccountInfo?>(null)
    val account: StateFlow<AccountInfo?> = _account.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(permission.isGranted())
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    init {
        viewModelScope.launch {
            session.me().onSuccess { _account.value = it }
        }
    }

    /**
     * Přečte stav oprávnění znovu.
     *
     * Volá se při návratu na obrazovku: uživatel mohl notifikace přepnout v systémovém nastavení
     * a bez tohohle by tu zůstal viset starý stav.
     */
    fun refreshNotificationState() {
        _notificationsEnabled.value = permission.isGranted()
    }

    fun openNotificationSettings() {
        permission.openSystemSettings()
    }

    fun logOut() {
        viewModelScope.launch { session.logOut() }
    }
}
