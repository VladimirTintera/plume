package eu.tintera.plume.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.PrivacyChoice
import eu.tintera.plume.core.telegram.PrivacyRepository
import eu.tintera.plume.core.telegram.PrivacySetting
import eu.tintera.plume.core.telegram.privacyChoiceOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @property choices stav každého nastavení; chybí-li klíč, ještě se načítá a řádek nic netvrdí.
 * @property failed nastavení, která se nepodařilo přečíst.
 * @property readDate „Ukazovat čas přečtení"; `null` = ještě se načítá (nebo selhalo).
 */
data class AccountPrivacyState(
    val choices: Map<PrivacySetting, PrivacyChoice> = emptyMap(),
    val failed: Set<PrivacySetting> = emptySet(),
    val readDate: Boolean? = null,
    val readDateFailed: Boolean = false,
    val isPremium: Boolean = false,
)

/**
 * Seznam „Soukromí účtu". Každé nastavení je živé ([PrivacyRepository.rules]), takže se tu
 * projeví i změna z detailu nebo z jiného zařízení bez nového čtení.
 */
class AccountPrivacyViewModel(
    private val privacy: PrivacyRepository,
    private val chats: ChatRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountPrivacyState())
    val state: StateFlow<AccountPrivacyState> = _state.asStateFlow()

    init {
        for (setting in PrivacySetting.entries) {
            viewModelScope.launch {
                privacy.rules(setting).collect { result ->
                    result
                        .onSuccess { rules ->
                            _state.update { it.copy(choices = it.choices + (setting to privacyChoiceOf(rules)), failed = it.failed - setting) }
                        }
                        .onFailure { _state.update { it.copy(failed = it.failed + setting) } }
                }
            }
        }
        viewModelScope.launch {
            privacy.showsReadDate()
                .onSuccess { show -> _state.update { it.copy(readDate = show) } }
                .onFailure { _state.update { it.copy(readDateFailed = true) } }
        }
        viewModelScope.launch { chats.isPremium.collect { premium -> _state.update { it.copy(isPremium = premium) } } }
    }

    /**
     * Přepínač se pohne hned a při selhání se vrátí s větou pod sebou — TDLib na tuhle volbu
     * update nepošle, takže obrazovka je jediný zdroj pravdy (jako citlivý obsah).
     */
    fun setShowReadDate(show: Boolean) {
        val previous = _state.value.readDate ?: return
        if (previous == show) return
        _state.update { it.copy(readDate = show, readDateFailed = false) }
        viewModelScope.launch {
            privacy.setShowsReadDate(show).onFailure {
                _state.update { it.copy(readDate = previous, readDateFailed = true) }
            }
        }
    }
}
