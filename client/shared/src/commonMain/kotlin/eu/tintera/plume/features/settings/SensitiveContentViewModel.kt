package eu.tintera.plume.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.SensitiveContentSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @property show `null` = volbu změnit nejde (nebo se ještě načítá) a řádek se nekreslí.
 * @property failed poslední změna neprošla; přepínač se vrátil a řádek řekne proč.
 */
data class SensitiveContentState(val show: Boolean? = null, val failed: Boolean = false)

/**
 * Přepínač „Zobrazovat citlivý obsah" ve skupině Soukromí.
 *
 * TDLib na změnu volby update nepošle tak, aby ho šlo snadno odebírat, a zápis je lokálně
 * rychlý — přepínač se proto pohne hned a při selhání se vrátí, jako v nastavení archivu.
 */
class SensitiveContentViewModel(private val settings: SensitiveContentSettings) : ViewModel() {

    private val _state = MutableStateFlow(SensitiveContentState())
    val state: StateFlow<SensitiveContentState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.showsSensitiveContent().onSuccess { show -> _state.update { it.copy(show = show) } }
        }
    }

    fun setShow(show: Boolean) {
        val previous = _state.value.show ?: return
        _state.update { it.copy(show = show, failed = false) }
        viewModelScope.launch {
            settings.setShowsSensitiveContent(show).onFailure {
                _state.update { it.copy(show = previous, failed = true) }
            }
        }
    }
}
