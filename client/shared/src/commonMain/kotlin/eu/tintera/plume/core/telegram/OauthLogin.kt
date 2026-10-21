package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.StateFlow

/**
 * Přihlášení na webu účtem Telegramu (OAuth): web pošle žádost (`updateNewOauthRequest`), nebo
 * uživatel otevře přihlašovací odkaz zvenčí (`InternalLinkTypeOauth`), a aplikace ji potvrdí nebo
 * odmítne. Kdo má jen Plume, by se bez toho na webu nepřihlásil.
 *
 * **Eagerně**: update bez odběratele se ztratí a žádost platí jen minutu.
 */
interface OauthLogin {
    /** Žádosti čekající na rozhodnutí (adresy pro [requestInfo]), nejstarší první. */
    val pending: StateFlow<List<String>>

    /** Žádost z odkazu zvenčí (aplikace ho otevřela z prohlížeče nebo QR kódu). */
    fun open(url: String)

    /** Žádost vyřízená nebo zavřená — zmizí z [pending]. */
    fun dismiss(url: String)

    /** Kdo a odkud se přihlašuje (`getOauthLinkInfo`). */
    suspend fun requestInfo(url: String): Result<OauthRequest>

    /** Číslo shodné s webem (`checkOauthRequestMatchCode`); selhání = žádost propadla. */
    suspend fun checkMatchCode(url: String, code: String): Result<Unit>

    /** Přihlásit; vrátí adresu k otevření, prázdná = stačí oznámit, že se povedlo. */
    suspend fun accept(url: String, matchCode: String, allowWriteAccess: Boolean, allowPhoneNumber: Boolean): Result<String>

    suspend fun decline(url: String): Result<Unit>
}

/**
 * Žádost o přihlášení, jak ji předkládá TDLib. [appName] `null` = neověřená aplikace (tak to
 * má UI napsat). [matchCodes] jsou čísla na výběr — správné ukazuje web; s [matchCodeFirst] se
 * číslo ověří dřív, než se ukáže potvrzení.
 */
data class OauthRequest(
    val url: String,
    val domain: String,
    val appName: String?,
    val fromApp: Boolean,
    val botName: String,
    val requestWriteAccess: Boolean,
    val requestPhoneNumber: Boolean,
    val browser: String,
    val platform: String,
    val ipAddress: String,
    val location: String,
    val matchCodeFirst: Boolean,
    val matchCodes: List<String>,
)
