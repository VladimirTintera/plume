package eu.tintera.plume.features.notices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.AccountNotices
import eu.tintera.plume.core.telegram.ServiceNotice
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.core.telegram.TermsOfServiceUpdate
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.notice_terms_accept_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Oznámení od Telegramu a nové podmínky služby jako dialogy nad celou aplikací. Ukazuje se vždy
 * jedno: napřed oznámení (bývá to bezpečnost relace), pak podmínky.
 */
class AccountNoticesViewModel(
    private val notices: AccountNotices,
    private val session: TelegramSession,
) : ViewModel() {

    private val logger = Logger.withTag("AccountNotices")

    /** Nejstarší nezavřené oznámení. */
    val serviceNotice: StateFlow<ServiceNotice?> = notices.serviceNotices
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, notices.serviceNotices.value.firstOrNull())

    /**
     * Podmínky k přijetí — jen ty, u kterých Telegram chce popup (`showPopup`). Bez něj je
     * oficiální klienti přijímají až při další příležitosti a vnucovat je dialogem by bylo víc,
     * než Telegram chce.
     */
    val terms: StateFlow<TermsOfServiceUpdate?> = notices.termsOfService
        .map { it?.takeIf { terms -> terms.blocking } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, notices.termsOfService.value?.takeIf { it.blocking })

    private val _accepting = MutableStateFlow(false)
    val accepting: StateFlow<Boolean> = _accepting.asStateFlow()

    private val _error = MutableStateFlow<UiText?>(null)
    val error: StateFlow<UiText?> = _error.asStateFlow()

    fun dismiss(notice: ServiceNotice) = notices.dismissServiceNotice(notice.id)

    /**
     * „Odhlásit" u neplatného klíče relace: lokální data pryč (`destroy`, tedy totéž co návrat
     * na zadání čísla), protože `logOut` by s klíčem, který server zahodil, neprošel.
     */
    fun logOut(notice: ServiceNotice) {
        notices.dismissServiceNotice(notice.id)
        viewModelScope.launch {
            session.restartLogin().onFailure { logger.w(it) { "Zahodit lokální data po AUTH_KEY_DROP se nepodařilo." } }
        }
    }

    fun acceptTerms() {
        if (_accepting.value) return
        viewModelScope.launch {
            _accepting.value = true
            _error.value = null
            notices.acceptTermsOfService().onFailure { failure ->
                logger.w(failure) { "Podmínky služby se nepodařilo přijmout." }
                _error.value = failure.userMessage(Res.string.notice_terms_accept_failed)
            }
            _accepting.value = false
        }
    }

    fun postponeTerms() {
        _error.value = null
        notices.postponeTermsOfService()
    }
}
