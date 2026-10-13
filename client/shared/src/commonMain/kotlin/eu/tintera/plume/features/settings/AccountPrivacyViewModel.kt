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
    /** Po kolika dnech bez přihlášení se účet smaže; `null` = ještě se načítá (nebo selhalo). */
    val accountTtlDays: Int? = null,
    val accountTtlFailed: Boolean = false,
)

/**
 * Volby samosmazání účtu jako v oficiálním klientu: 1, 3, 6, 12, 18 a 24 měsíců ve dnech, které
 * server bere.
 */
internal val ACCOUNT_TTL_OPTIONS = listOf(30, 90, 182, 365, 548, 730)

/**
 * Kolik měsíců hodnota znamená, nebo `null`, když neodpovídá žádné volbě (jiný klient,
 * výchozí hodnota serveru). Server vrací i 180 nebo 183 dní místo našich 182 — pořád „6 měsíců"
 * a pořád vybraná volba, jinak by v nabídce stály dvě stejné.
 */
internal fun accountTtlMonths(days: Int): Int? = when (days) {
    in 28..31 -> 1
    in 89..92 -> 3
    in 180..184 -> 6
    in 365..366 -> 12
    in 545..550 -> 18
    in 729..732 -> 24
    else -> null
}

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
        viewModelScope.launch {
            privacy.accountTtlDays()
                .onSuccess { days -> _state.update { it.copy(accountTtlDays = days) } }
                .onFailure { _state.update { it.copy(accountTtlFailed = true) } }
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

    /** Jako čas přečtení: volba se změní hned a při selhání se vrátí s větou pod sebou. */
    fun setAccountTtl(days: Int) {
        val previous = _state.value.accountTtlDays ?: return
        // 180 → 182 je pořád „6 měsíců"; zápis by nic nezměnil.
        if (previous == days || accountTtlMonths(previous)?.let { it == accountTtlMonths(days) } == true) return
        _state.update { it.copy(accountTtlDays = days, accountTtlFailed = false) }
        viewModelScope.launch {
            privacy.setAccountTtlDays(days).onFailure {
                _state.update { it.copy(accountTtlDays = previous, accountTtlFailed = true) }
            }
        }
    }
}
