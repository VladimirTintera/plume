package eu.tintera.plume.core.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Narazilo se na limit skupin a kanálů (`isChannelLimit`) a má se nabídnout opuštění
 * neaktivních. Hlásí to místo, kde vstup nebo založení selhalo (konverzace, odkaz zvenčí,
 * založení chatu); sheet ukazuje shell, protože patří nad kteroukoli obrazovku.
 *
 * STAV, ne událost — jako [ContactOffers]: sheet ho vyzvedne, i kdyby zrovna nebyl složený.
 */
class ChannelLimitRequests {

    private val _shown = MutableStateFlow(false)
    val shown: StateFlow<Boolean> = _shown.asStateFlow()

    fun request() {
        _shown.value = true
    }

    fun dismiss() {
        _shown.value = false
    }
}
