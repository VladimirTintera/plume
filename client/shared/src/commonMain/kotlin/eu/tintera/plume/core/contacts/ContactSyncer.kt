package eu.tintera.plume.core.contacts

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.connectivity.AppForeground
import eu.tintera.plume.core.settings.AccountSettings
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.ContactSync
import eu.tintera.plume.core.telegram.TelegramSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * Synchronizace adresáře telefonu s Telegramem — jen se zapnutou volbou
 * ([AccountSettings.contactSyncEnabled], po účtech, výchozí vypnuto) a s oprávněním ke kontaktům.
 *
 * - **Kdy**: po přihlášení a při každém návratu aplikace do popředí, nejvýš jednou za
 *   [MIN_INTERVAL_MS]. Změnu adresáře systém hlásí jen živému procesu (`ContentObserver`),
 *   a v pozadí by Plume stejně nesmělo běžet kvůli kontaktům.
 * - **Co**: celý adresář `changeImportedContacts` — TDLib si pamatuje minule poslaný seznam
 *   a server dostane jen rozdíl (přidané, smazané), ne celý adresář pokaždé.
 * - **Vypnutí** ([disable]) smaže nahraná čísla ze serveru; kontakty v Telegramu zůstanou.
 * - **Jen jedno volání naráz** ([lock]): dokumentace `changeImportedContacts` to výslovně chce.
 */
class ContactSyncer(
    private val settings: AccountSettings,
    private val contacts: PhoneContacts,
    private val sync: ContactSync,
    session: TelegramSession,
    foreground: AppForeground,
    private val scope: CoroutineScope,
) {
    private val logger = Logger.withTag("ContactSync")
    private val lock = Mutex()
    private var lastSyncAt = 0L

    /** Je synchronizace zapnutá u tohoto účtu (řádek v Nastavení). */
    val enabled: StateFlow<Boolean> get() = settings.contactSyncEnabled

    init {
        scope.launch {
            combine(settings.contactSyncEnabled, session.authStage, foreground.isForeground) { enabled, stage, visible ->
                enabled && stage == AuthStage.Ready && visible
            }
                .distinctUntilChanged()
                .filter { it }
                .collect { syncNow(force = false) }
        }
    }

    /** Synchronizuje hned (zapnutí volby), jinak jen po uplynutí [MIN_INTERVAL_MS]. */
    fun syncNow(force: Boolean = true) {
        scope.launch {
            lock.withLock {
                val now = Clock.System.now().toEpochMilliseconds()
                if (!settings.contactSyncEnabled.value || !contacts.hasPermission()) return@withLock
                if (!force && now - lastSyncAt < MIN_INTERVAL_MS) return@withLock
                val phoneContacts = contacts.read()
                sync.sync(phoneContacts)
                    .onSuccess { found ->
                        lastSyncAt = now
                        logger.i { "Adresář synchronizován: ${phoneContacts.size} čísel, $found s Telegramem" }
                    }
                    .onFailure { logger.w(it) { "Adresář se nepodařilo synchronizovat" } }
            }
        }
    }

    /**
     * Zapne synchronizaci (oprávnění už je). Pustí ji tok nastavení v `init`, jakmile se volba
     * zapíše — přímé volání by se s asynchronním zápisem DataStore předběhlo a nic nenahrálo.
     */
    fun enable() {
        settings.setContactSyncEnabled(true)
    }

    /** Vypne synchronizaci a smaže nahraná čísla ze serveru. */
    fun disable() {
        settings.setContactSyncEnabled(false)
        scope.launch {
            lock.withLock {
                lastSyncAt = 0L
                sync.clear()
                    .onSuccess { logger.i { "Nahraná čísla smazána ze serveru" } }
                    .onFailure { logger.w(it) { "Nahrané kontakty se nepodařilo smazat" } }
            }
        }
    }

    private companion object {
        const val MIN_INTERVAL_MS = 30 * 60 * 1000L
    }
}
