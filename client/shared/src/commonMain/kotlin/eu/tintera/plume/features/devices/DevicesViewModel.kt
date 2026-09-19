package eu.tintera.plume.features.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ConnectedWebsite
import eu.tintera.plume.core.telegram.DeviceRepository
import eu.tintera.plume.core.telegram.Devices
import eu.tintera.plume.core.telegram.UnconfirmedDevice
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.devices_error_action
import eu.tintera.plume.resources.devices_error_load
import eu.tintera.plume.resources.devices_error_qr
import eu.tintera.plume.resources.devices_qr_confirmed
import eu.tintera.plume.resources.devices_qr_not_login
import eu.tintera.plume.resources.devices_terminated
import eu.tintera.plume.resources.devices_terminated_others
import eu.tintera.plume.resources.devices_website_disconnected
import eu.tintera.plume.resources.devices_websites_disconnected
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

data class DevicesState(
    /** `null` = ještě se načítá. */
    val devices: Devices? = null,
    val websites: List<ConnectedWebsite> = emptyList(),
    val loadFailed: Boolean = false,
    /** Právě běží akce (odhlášení, QR přihlášení) — tlačítka se mezitím nedají ťuknout znovu. */
    val busy: Boolean = false,
    /** Jednorázová hláška pro snackbar; obrazovka ji po zobrazení vynuluje. */
    val notice: UiText? = null,
)

/**
 * Sekce Zařízení: kde je účet přihlášený, odhlašování, automatické odhlášení nečinných,
 * weby přihlášené přes Telegram a přihlášení jiného zařízení QR kódem.
 *
 * Po každé změně se seznam **čte znovu** a nepřepisuje se lokálně — co se po odhlášení stane
 * s ostatními relacemi (a jestli server vůbec poslechl), ví jen server.
 */
class DevicesViewModel(
    private val repository: DeviceRepository,
) : ViewModel() {

    private val logger = Logger.withTag("Devices")

    private val _state = MutableStateFlow(DevicesState())
    val state: StateFlow<DevicesState> = _state.asStateFlow()

    val unconfirmed: StateFlow<UnconfirmedDevice?> = repository.unconfirmed

    // Načítá se z obrazovky při každém návratu (`LifecycleResumeEffect`), tedy i poprvé —
    // v `init` by se seznam při otevření četl dvakrát.

    fun refresh() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val devices = repository.devices()
        // Weby jsou doplněk; když selžou, zařízení se ukážou i tak.
        val websites = repository.websites()
            .onFailure { logger.w(it) { "Přihlášené weby se nepodařilo načíst" } }
            .getOrDefault(emptyList())
        devices
            .onSuccess { loaded ->
                _state.update { it.copy(devices = loaded, websites = websites, loadFailed = false) }
            }
            .onFailure { failure ->
                logger.w(failure) { "Zařízení se nepodařilo načíst" }
                _state.update { it.copy(loadFailed = it.devices == null, notice = failure.userMessage(Res.string.devices_error_load)) }
            }
    }

    fun terminate(sessionId: Long) = act(done = Res.string.devices_terminated) { repository.terminate(sessionId) }

    fun terminateOthers() = act(done = Res.string.devices_terminated_others) { repository.terminateOthers() }

    fun confirm(sessionId: Long) = act(done = null) { repository.confirm(sessionId) }

    fun setInactiveTtl(days: Int) = act(done = null) { repository.setInactiveTtl(days) }

    fun setCanAcceptCalls(sessionId: Long, value: Boolean) =
        act(done = null) { repository.setCanAcceptCalls(sessionId, value) }

    fun setCanAcceptSecretChats(sessionId: Long, value: Boolean) =
        act(done = null) { repository.setCanAcceptSecretChats(sessionId, value) }

    fun disconnectWebsite(websiteId: Long) =
        act(done = Res.string.devices_website_disconnected) { repository.disconnectWebsite(websiteId) }

    fun disconnectAllWebsites() =
        act(done = Res.string.devices_websites_disconnected) { repository.disconnectAllWebsites() }

    /**
     * Naskenovaný QR kód. Čtečka přečte cokoli, takže se napřed pozná, jestli je to vůbec
     * přihlašovací kód Telegramu — jinak by server dostal nesmysl a vrátil nesrozumitelnou chybu.
     */
    fun onQrScanned(content: String) {
        if (!content.isLoginLink()) {
            _state.update { it.copy(notice = UiText(Res.string.devices_qr_not_login)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            repository.confirmQrLogin(content)
                .onSuccess { device ->
                    _state.update { it.copy(notice = UiText(Res.string.devices_qr_confirmed, device.applicationName)) }
                    load()
                }
                .onFailure { failure ->
                    logger.w(failure) { "QR přihlášení selhalo" }
                    _state.update { it.copy(notice = failure.userMessage(Res.string.devices_error_qr)) }
                }
            _state.update { it.copy(busy = false) }
        }
    }

    fun noticeShown() = _state.update { it.copy(notice = null) }

    private fun act(done: StringResource?, action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            action()
                .onSuccess { if (done != null) _state.update { it.copy(notice = UiText(done)) } }
                .onFailure { failure ->
                    logger.w(failure) { "Akce nad zařízením selhala" }
                    _state.update { it.copy(notice = failure.userMessage(Res.string.devices_error_action)) }
                }
            load()
            _state.update { it.copy(busy = false) }
        }
    }
}

/** `tg://login?token=…` — jediná podoba, kterou přihlašovací QR kód Telegramu má. */
internal fun String.isLoginLink(): Boolean = trim().startsWith("tg://login?token=", ignoreCase = true)
