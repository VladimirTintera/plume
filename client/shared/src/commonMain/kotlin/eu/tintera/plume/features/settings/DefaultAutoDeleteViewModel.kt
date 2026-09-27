package eu.tintera.plume.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.telegram.DefaultAutoDeleteSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @property seconds `null` = ještě se načítá (nebo se zjistit nepodařilo) a řádek se nekreslí;
 *   `0` = vypnuto.
 * @property failed poslední změna neprošla; volba se vrátila a řádek řekne proč.
 */
data class DefaultAutoDeleteState(
    val seconds: Int? = null,
    val failed: Boolean = false,
    val choosing: Boolean = false,
)

/**
 * Řádek „Automatické mazání v nových chatech" ve skupině Soukromí. Stejný vzor jako přepínač
 * citlivého obsahu: volba se pohne hned a při selhání se vrátí — TDLib na změnu update nepošle.
 */
class DefaultAutoDeleteViewModel(private val settings: DefaultAutoDeleteSettings) : ViewModel() {

    private val _state = MutableStateFlow(DefaultAutoDeleteState())
    val state: StateFlow<DefaultAutoDeleteState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.defaultAutoDeleteTime().onSuccess { seconds -> _state.update { it.copy(seconds = seconds) } }
        }
    }

    fun openChooser() = _state.update { it.copy(choosing = it.seconds != null) }

    fun closeChooser() = _state.update { it.copy(choosing = false) }

    fun choose(seconds: Int) {
        val previous = _state.value.seconds ?: return
        _state.update { it.copy(seconds = seconds, failed = false, choosing = false) }
        if (seconds == previous) return
        viewModelScope.launch {
            settings.setDefaultAutoDeleteTime(seconds).onFailure {
                _state.update { it.copy(seconds = previous, failed = true) }
            }
        }
    }
}
