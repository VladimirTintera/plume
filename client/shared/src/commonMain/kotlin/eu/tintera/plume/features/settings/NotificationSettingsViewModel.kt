package eu.tintera.plume.features.settings

import eu.tintera.plume.core.telegram.DEFAULT_SOUND_ID
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.notifications.NotificationPermission
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.settings.QuietHours
import eu.tintera.plume.core.telegram.NotificationChannelKind
import eu.tintera.plume.core.telegram.NotificationException
import eu.tintera.plume.core.telegram.NotificationScope
import eu.tintera.plume.core.telegram.NotificationSettingsRepository
import eu.tintera.plume.core.telegram.ScopeSettings
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.settings_notif_error
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationSettingsState(
    val permissionGranted: Boolean = true,
    /** `null` u rozsahu = ještě se načítá. */
    val scopes: Map<NotificationScope, ScopeSettings?> = emptyMap(),
    /** Výjimky podle rozsahu; chybějící klíč = ještě nenačtené. */
    val exceptions: Map<NotificationScope, List<NotificationException>> = emptyMap(),
    val contactJoined: Boolean? = null,
    /** Rozsah, jehož výjimky jsou otevřené v sheetu. */
    val openExceptions: NotificationScope? = null,
    /** Jednorázová hláška pro snackbar; obrazovka ji po zobrazení vynuluje. */
    val notice: UiText? = null,
)

class NotificationSettingsViewModel(
    private val repository: NotificationSettingsRepository,
    private val permission: NotificationPermission,
    private val settings: AppSettings,
) : ViewModel() {

    private val local = MutableStateFlow(NotificationSettingsState(permissionGranted = permission.isGranted()))

    val supportsChannelSettings: Boolean = permission.supportsChannelSettings

    val supportsQuietHours: Boolean = permission.supportsQuietHours

    /**
     * Tiché hodiny. Zápis běží v rozsahu [AppSettings], ne ve `viewModelScope` — volba těsně před
     * odchodem z obrazovky se tak neztratí (viz [AppearanceViewModel]).
     */
    val quietHours: StateFlow<QuietHours> = settings.quietHours

    fun setQuietHoursEnabled(enabled: Boolean) = settings.setQuietHours(quietHours.value.copy(enabled = enabled))

    fun setQuietHoursStart(minuteOfDay: Int) = settings.setQuietHours(quietHours.value.copy(start = minuteOfDay))

    fun setQuietHoursEnd(minuteOfDay: Int) = settings.setQuietHours(quietHours.value.copy(end = minuteOfDay))

    /** Rozsahy jsou živé (změna z jiného zařízení se tu ukáže), zbytek se čte při otevření. */
    val state: StateFlow<NotificationSettingsState> = combine(
        local,
        repository.scopeSettings(NotificationScope.Private),
        repository.scopeSettings(NotificationScope.Groups),
        repository.scopeSettings(NotificationScope.Channels),
    ) { local, private, groups, channels ->
        local.copy(
            scopes = mapOf(
                NotificationScope.Private to private,
                NotificationScope.Groups to groups,
                NotificationScope.Channels to channels,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        loadExceptions()
        viewModelScope.launch {
            repository.contactJoinedEnabled().onSuccess { value -> local.update { it.copy(contactJoined = value) } }
        }
    }

    /**
     * Při návratu na obrazovku: oprávnění mohl uživatel přepnout v systému, výjimky v chatu
     * (ztlumení z nabídky chatu) nebo na jiném zařízení.
     */
    fun refresh() {
        local.update { it.copy(permissionGranted = permission.isGranted()) }
        loadExceptions()
    }

    private fun loadExceptions() {
        NotificationScope.entries.forEach { scope ->
            viewModelScope.launch {
                repository.exceptions(scope).onSuccess { list ->
                    local.update { it.copy(exceptions = it.exceptions + (scope to list)) }
                }
            }
        }
    }

    fun update(scope: NotificationScope, transform: (ScopeSettings) -> ScopeSettings) {
        val current = state.value.scopes[scope] ?: return
        viewModelScope.launch {
            repository.setScopeSettings(scope, transform(current)).onFailure(::fail)
        }
    }

    fun setContactJoined(enabled: Boolean) {
        val previous = local.value.contactJoined
        local.update { it.copy(contactJoined = enabled) }
        viewModelScope.launch {
            repository.setContactJoinedEnabled(enabled).onFailure { failure ->
                local.update { it.copy(contactJoined = previous) }
                fail(failure)
            }
        }
    }

    fun openExceptions(scope: NotificationScope) = local.update { it.copy(openExceptions = scope) }

    fun closeExceptions() = local.update { it.copy(openExceptions = null) }

    /** Chat se vrátí k výchozímu nastavení; seznam se pak čte znovu — co zbylo, ví jen server. */
    fun resetException(scope: NotificationScope, chatId: Long) {
        viewModelScope.launch {
            repository.resetToDefault(chatId)
                .onSuccess {
                    repository.exceptions(scope).onSuccess { list ->
                        local.update {
                            it.copy(
                                exceptions = it.exceptions + (scope to list),
                                // Prázdný sheet nemá co ukázat.
                                openExceptions = it.openExceptions.takeUnless { list.isEmpty() },
                            )
                        }
                    }
                }
                .onFailure(::fail)
        }
    }

    fun openSystemSettings() = permission.openSystemSettings()

    fun openChannelSettings(channel: NotificationChannelKind, soundId: Long = DEFAULT_SOUND_ID) = permission.openChannelSettings(channel, soundId)

    fun noticeShown() = local.update { it.copy(notice = null) }

    private fun fail(failure: Throwable) {
        local.update { it.copy(notice = failure.userMessage(Res.string.settings_notif_error)) }
    }
}
