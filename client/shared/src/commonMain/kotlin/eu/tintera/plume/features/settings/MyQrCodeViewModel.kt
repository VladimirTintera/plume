package eu.tintera.plume.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.files.MediaSharer
import eu.tintera.plume.core.navigation.LinkOpenRequests
import eu.tintera.plume.core.navigation.isTelegramLink
import eu.tintera.plume.core.telegram.AccountInfo
import eu.tintera.plume.core.telegram.AccountRepository
import eu.tintera.plume.core.telegram.ContactLink
import eu.tintera.plume.core.ui.TextClipboard
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.invite_links_share_failed
import eu.tintera.plume.resources.my_qr_not_telegram
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * „Můj QR kód": odkaz na vlastní účet ([AccountRepository.contactLink]) jako QR kód, sdílení
 * a kopírování odkazu a čtečka kódu od druhého ([LinkOpenRequests]).
 *
 * **Dočasný odkaz se obnovuje sám, jen dokud je kód vidět** ([keepFresh] běží v efektu sheetu).
 * ViewModel žije dál s obrazovkou, ze které se sheet otevřel, a ptát se serveru na nový token
 * na pozadí by nemělo komu posloužit. Po znovuotevření se ukáže poslední odkaz hned a prošlý
 * se vymění.
 */
class MyQrCodeViewModel(
    private val accounts: AccountRepository,
    private val sharer: MediaSharer,
    private val clipboard: TextClipboard,
    private val linkRequests: LinkOpenRequests,
) : ViewModel() {

    val account: StateFlow<AccountInfo?> =
        accounts.account.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val _state = MutableStateFlow(MyQrCodeState())
    val state: StateFlow<MyQrCodeState> = _state.asStateFlow()

    /**
     * Načte odkaz a dočasný vymění chvíli před koncem platnosti — kód na obrazovce tak nikdy
     * nevede na prošlý token. Volá se z efektu sheetu a končí s ním; selhání čeká na [retry].
     */
    suspend fun keepFresh() {
        while (true) {
            val current = _state.value
            val now = Clock.System.now().epochSeconds
            val expiresAt = current.expiresAt
            val fresh = current.link != null && (expiresAt == null || expiresAt - now > RENEW_BEFORE_SECONDS)
            if (!fresh) {
                _state.update { it.copy(loading = it.link == null, failed = false) }
                val link = accounts.contactLink().getOrNull()
                if (link == null) {
                    _state.update { it.copy(loading = false, failed = true) }
                    return
                }
                _state.update { it.copy(link = link, expiresAt = link.expiresAt(now), loading = false) }
            }
            val until = _state.value.expiresAt ?: return
            delay((until - now - RENEW_BEFORE_SECONDS).coerceAtLeast(MIN_WAIT_SECONDS).seconds)
        }
    }

    /** Po selhání: sheet zavolá [keepFresh] znovu (mění se klíč jeho efektu). */
    fun retry() = _state.update { it.copy(failed = false, attempt = it.attempt + 1) }

    fun share() {
        val link = _state.value.link ?: return
        if (!sharer.shareText(link.url)) _state.update { it.copy(message = UiText(Res.string.invite_links_share_failed)) }
    }

    /** Potvrzení nese tlačítko samo ([MyQrCodeState.copied]) — je to stav ovládacího prvku. */
    fun copy() {
        val link = _state.value.link ?: return
        viewModelScope.launch {
            clipboard.copy(link.url)
            _state.update { it.copy(copied = true) }
            delay(COPIED_SHOWN)
            _state.update { it.copy(copied = false) }
        }
    }

    /**
     * Přečtený kód od druhého. Odkaz na Telegram otevře shell ([LinkOpenRequests]) a vrací
     * `true` — sheet se pak zavře; cokoli jiného jen ohlásí, že to kód Telegramu není.
     */
    fun onScanned(content: String): Boolean {
        val text = content.trim()
        if (!isTelegramLink(text)) {
            _state.update { it.copy(message = UiText(Res.string.my_qr_not_telegram)) }
            return false
        }
        linkRequests.request(text)
        return true
    }

    fun showMessage(message: UiText) = _state.update { it.copy(message = message) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private companion object {
        /** Nový token se vyrábí minutu před koncem — druhý ho mezitím ještě stihne načíst. */
        const val RENEW_BEFORE_SECONDS = 60L
        const val MIN_WAIT_SECONDS = 5L
        const val COPIED_SHOWN = 2_000L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun ContactLink.expiresAt(now: Long): Long? = expiresInSeconds.takeIf { it > 0 }?.let { now + it }

data class MyQrCodeState(
    val link: ContactLink? = null,
    /** Konec platnosti v sekundách od epochy; `null` = trvalý odkaz (veřejné jméno). */
    val expiresAt: Long? = null,
    val loading: Boolean = true,
    val failed: Boolean = false,
    /** Počítadlo pokusů — klíč efektu, který odkaz načítá ([MyQrCodeViewModel.retry]). */
    val attempt: Int = 0,
    val copied: Boolean = false,
    /** Hláška v sheetu (nesdíleno, není to kód Telegramu). */
    val message: UiText? = null,
)
