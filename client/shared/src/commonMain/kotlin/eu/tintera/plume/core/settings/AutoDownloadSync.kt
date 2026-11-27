package eu.tintera.plume.core.settings

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.AutoDownloadPresets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Kde stojí nastavení vůči serveru Telegramu.
 *
 * Ukládá se názvem jako ostatní výčty v [AppSettings].
 */
enum class SettingsSync {
    /** Při příštím přihlášení se nastavení vezme ze serveru. Platí od odhlášení. */
    Pull,

    /** Lokální změnu se ještě nepovedlo zapsat na server; zkusí se znovu po přihlášení. */
    Push,

    /** Lokální stav je na serveru. */
    Synced,
}

/**
 * Automatické stahování ze serveru **jen při přihlášení**, změna z Plume **hned na server**.
 *
 * Telegram drží pro účet tři předvolby (nízká / střední / vysoká spotřeba dat) a TDLib je
 * mapuje na roaming, mobilní data a Wi-Fi ([AutoDownloadPresets]). Zařízení se po přihlášení
 * chová samostatně: změna na jiném zařízení se sem nepropíše, dokud se uživatel nepřihlásí
 * znovu. Nové zařízení tak začne z posledního nastavení, ale tablet a telefon si můžou každý
 * nastavit své — přesně proto se nečte při každém startu.
 *
 * „Přihlášení" = [AuthStage.Ready] po přihlašovací obrazovce. Každý stav přihlašování
 * ([signedOut]) proto přepne na [SettingsSync.Pull]; přihlášený účet jde při startu rovnou
 * do `Ready` a přihlašovací stavy nevidí.
 */
class AutoDownloadSync(
    private val settings: AppSettings,
    /** Stav synchronizace je po účtech: každý účet má na serveru své předvolby. */
    private val account: AccountSettings,
    private val presets: AutoDownloadPresets,
    /** `TelegramSession.authStage`. */
    private val authStage: StateFlow<AuthStage>,
    private val scope: CoroutineScope,
) {

    private val logger = Logger.withTag("AutoDownloadSync")

    /** Zápis, pull po přihlášení a změny od uživatele se nesmí prolnout. */
    private val mutex = Mutex()

    /**
     * Poslední změna od uživatele, kterou server ještě nemá. Dokud tu je, stažení ze serveru
     * ([pull]) lokální volbu nepřepíše. Maže ho jen kód za [mutex].
     */
    private val pending = MutableStateFlow<AutoDownloadSettings?>(null)

    fun start() {
        scope.launch {
            authStage.collectLatest { stage ->
                when {
                    stage == AuthStage.Ready -> mutex.withLock { afterReady() }
                    stage.signedOut -> mutex.withLock {
                        // Nezapsaná změna patřila odhlášenému účtu; po přihlášení platí server.
                        pending.value = null
                        if (account.autoDownloadSync() != SettingsSync.Pull) account.setAutoDownloadSync(SettingsSync.Pull)
                    }
                    else -> Unit
                }
            }
        }
    }

    /** Uživatel změnil úrovně. Uloží je hned lokálně a pošle na server. */
    fun change(new: AutoDownloadSettings) {
        // Napřed [pending], pak lokální zápis: [pull] o přepsání rozhoduje uvnitř zápisu, a když
        // se zápisy seřadí „změna, pak server", musí změnu v [pending] už vidět.
        pending.value = new
        settings.setAutoDownload(new)
        scope.launch {
            mutex.withLock {
                val target = pending.value ?: return@withLock
                // Odhlášený účet nemá kam zapisovat; po přihlášení se nastavení stejně stáhne.
                if (authStage.value != AuthStage.Ready) return@withLock
                account.setAutoDownloadSync(SettingsSync.Push)
                push(target)
            }
        }
    }

    private suspend fun afterReady() {
        when (account.autoDownloadSync()) {
            SettingsSync.Synced -> Unit
            SettingsSync.Push -> push(settings.loadedAutoDownload())
            SettingsSync.Pull -> pull()
            // Aktualizace z verze bez synchronizace: kdo si vybral, tomu se volba nepřepíše
            // a jde na server. Kdo ne, dostane nastavení účtu.
            null -> {
                val local = settings.loadedAutoDownload()
                if (local != AutoDownloadSettings()) push(local) else pull()
            }
        }
    }

    private suspend fun pull() {
        presets.load()
            .onSuccess { server ->
                logger.i { "Ze serveru: $server" }
                // Uživatel mohl úrovně změnit, zatímco se stahovalo: jeho volba je novější a jde
                // na server hned za zámkem. Rozhoduje se UVNITŘ zápisu, ne předem — lokální zápis
                // změny ([change]) běží mimo zámek a předem přečtené [pending] by se s ním minulo.
                settings.updateAutoDownload { local -> if (pending.value != null) local else server }
                account.setAutoDownloadSync(SettingsSync.Synced)
            }
            .onFailure { logger.w(it) { "Předvolby automatického stahování se nenačetly." } }
    }

    private suspend fun push(target: AutoDownloadSettings) {
        presets.save(target)
            .onSuccess {
                if (pending.value == target) pending.value = null
                // Novější změna čeká za zámkem a stav nastaví sama.
                if (pending.value == null) account.setAutoDownloadSync(SettingsSync.Synced)
            }
            .onFailure { logger.w(it) { "Automatické stahování se na server nezapsalo." } }
    }
}

/** Stav, ve kterém účet přihlášený není. Po něm [AuthStage.Ready] znamená přihlášení. */
internal val AuthStage.signedOut: Boolean
    get() = when (this) {
        AuthStage.WaitPhoneNumber,
        is AuthStage.WaitCode,
        AuthStage.WaitEmailAddress,
        is AuthStage.WaitEmailCode,
        is AuthStage.WaitPassword,
        is AuthStage.WaitQrConfirmation,
        AuthStage.WaitRegistration,
        AuthStage.LoggingOut,
            -> true
        AuthStage.Connecting,
        AuthStage.Ready,
        is AuthStage.Failed,
            -> false
    }
