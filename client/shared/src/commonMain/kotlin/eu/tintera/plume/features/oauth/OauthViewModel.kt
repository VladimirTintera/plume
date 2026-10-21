package eu.tintera.plume.features.oauth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.OauthLogin
import eu.tintera.plume.core.telegram.OauthRequest
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.oauth_failed
import eu.tintera.plume.resources.oauth_expired
import eu.tintera.plume.resources.oauth_wrong_code
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Kde je žádost o přihlášení. */
sealed interface OauthStage {
    data object Loading : OauthStage

    /** Vybrat číslo, které ukazuje web (`matchCodeFirst`) — teprve pak potvrzení. */
    data object MatchCode : OauthStage

    /** Potvrdit, nebo odmítnout. */
    data object Confirm : OauthStage

    /** Přihlášeno; web nic otevřít nechce, stačí to říct. */
    data object Done : OauthStage

    /** Žádost propadla nebo selhala; [message] říká proč. */
    data class Failed(val message: UiText) : OauthStage
}

data class OauthState(
    val url: String,
    val request: OauthRequest? = null,
    val stage: OauthStage = OauthStage.Loading,
    /** Vybrané číslo; bez čísel z webu prázdné. */
    val matchCode: String? = null,
    val busy: Boolean = false,
)

/**
 * Žádost webu o přihlášení účtem Telegramu, jedna po druhé ([OauthLogin.pending]).
 *
 * - **Ukazuje se, kdo a odkud** (doména, ověřená aplikace, prohlížeč, IP adresa, místo) — žádost
 *   může poslat kdokoli, kdo zná telefonní číslo, a odmítnutí je jediná ochrana.
 * - **Číslo se vybírá dřív než potvrzení** tam, kde to TDLib chce (`matchCodeFirst`); špatné číslo
 *   žádost zruší. Jinde se číslo vybírá v potvrzení a jde s ním.
 * - **Zavření bez rozhodnutí = odmítnutí**, ať žádost nevisí a web ví, že má skončit.
 */
class OauthViewModel(
    private val oauth: OauthLogin,
    /** Otevře adresu, kterou web po přihlášení chce (`UrlOpener.open`). */
    private val openUrl: (String) -> Unit,
) : ViewModel() {

    private val logger = Logger.withTag("OauthLogin")

    private val _state = MutableStateFlow<OauthState?>(null)
    val state: StateFlow<OauthState?> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            oauth.pending.map { it.firstOrNull() }.distinctUntilChanged().collect { url ->
                if (url == null) {
                    _state.value = null
                } else if (_state.value?.url != url) {
                    load(url)
                }
            }
        }
    }

    private fun load(url: String) {
        _state.value = OauthState(url)
        viewModelScope.launch {
            oauth.requestInfo(url)
                .onSuccess { request ->
                    _state.update { current ->
                        current?.takeIf { it.url == url }?.copy(
                            request = request,
                            stage = if (request.matchCodeFirst && request.matchCodes.isNotEmpty()) OauthStage.MatchCode else OauthStage.Confirm,
                        )
                    }
                }
                .onFailure { _state.update { it?.takeIf { s -> s.url == url }?.copy(stage = OauthStage.Failed(UiText(Res.string.oauth_expired))) } }
        }
    }

    /** Vybrané číslo. Před potvrzením (`matchCodeFirst`) ho ověří server; jinak se jen zapamatuje. */
    fun pickCode(code: String) {
        val current = _state.value ?: return
        val request = current.request ?: return
        if (current.stage != OauthStage.MatchCode) {
            _state.update { it?.copy(matchCode = code) }
            return
        }
        _state.update { it?.copy(busy = true) }
        viewModelScope.launch {
            oauth.checkMatchCode(current.url, code)
                .onSuccess { _state.update { it?.copy(busy = false, matchCode = code, stage = OauthStage.Confirm) } }
                .onFailure { failure ->
                    logger.w(failure) { "Číslo pro přihlášení na ${request.domain} nesedí." }
                    _state.update { it?.copy(busy = false, stage = OauthStage.Failed(UiText(Res.string.oauth_wrong_code))) }
                }
        }
    }

    fun accept(allowWriteAccess: Boolean, allowPhoneNumber: Boolean) {
        val current = _state.value ?: return
        val request = current.request ?: return
        if (current.busy) return
        _state.update { it?.copy(busy = true) }
        viewModelScope.launch {
            oauth.accept(
                url = current.url,
                matchCode = current.matchCode.orEmpty(),
                allowWriteAccess = request.requestWriteAccess && allowWriteAccess,
                allowPhoneNumber = request.requestPhoneNumber && allowPhoneNumber,
            )
                .onSuccess { target ->
                    if (target.isBlank()) {
                        _state.update { it?.copy(busy = false, stage = OauthStage.Done) }
                    } else {
                        openUrl(target)
                        oauth.dismiss(current.url)
                    }
                }
                .onFailure { failure ->
                    logger.w(failure) { "Přihlášení na ${request.domain} selhalo." }
                    _state.update { it?.copy(busy = false, stage = OauthStage.Failed(failure.userMessage(Res.string.oauth_failed))) }
                }
        }
    }

    /** Odmítnout. Žádost zmizí hned; odmítnutí serveru se pošle na pozadí. */
    fun decline() {
        val url = _state.value?.url ?: return
        oauth.dismiss(url)
        viewModelScope.launch { oauth.decline(url).onFailure { logger.w(it) { "Odmítnutí přihlášení selhalo." } } }
    }

    /** Zavřít sheet: rozhodnutou nebo propadlou žádost jen schovat, nerozhodnutou odmítnout. */
    fun close() {
        val current = _state.value ?: return
        when (current.stage) {
            OauthStage.MatchCode, OauthStage.Confirm, OauthStage.Loading -> decline()
            OauthStage.Done, is OauthStage.Failed -> oauth.dismiss(current.url)
        }
    }
}
