package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.StateFlow

/**
 * Zařízení přihlášená k účtu (relace) a weby přihlášené přes Telegram.
 *
 * Hranice kolem TDLib jako ostatní repository — obrazovka vidí jen [Devices], [Device]
 * a [ConnectedWebsite]. Seznamy se čtou při otevření a po každé změně znovu; živé je jen
 * [unconfirmed], které TDLib hlásí samo.
 */
interface DeviceRepository {
    suspend fun devices(): Result<Devices>

    suspend fun terminate(sessionId: Long): Result<Unit>

    /** Odhlásí všechna zařízení kromě tohoto. */
    suspend fun terminateOthers(): Result<Unit>

    /** Po kolika dnech nečinnosti server zařízení sám odhlásí. */
    suspend fun setInactiveTtl(days: Int): Result<Unit>

    suspend fun setCanAcceptCalls(sessionId: Long, value: Boolean): Result<Unit>

    suspend fun setCanAcceptSecretChats(sessionId: Long, value: Boolean): Result<Unit>

    suspend fun websites(): Result<List<ConnectedWebsite>>

    suspend fun disconnectWebsite(websiteId: Long): Result<Unit>

    suspend fun disconnectAllWebsites(): Result<Unit>

    /**
     * Přihlásí jiné zařízení naskenovaným QR kódem (`tg://login?token=…`). Vrací zařízení,
     * které se přihlásilo.
     */
    suspend fun confirmQrLogin(link: String): Result<Device>

    /**
     * Nové přihlášení, které uživatel ještě nepotvrdil („Byli jste to vy?"), `null` = žádné.
     * Z nepotvrzeného zařízení server nedovolí některé citlivé akce, dokud ho uživatel neuzná.
     */
    val unconfirmed: StateFlow<UnconfirmedDevice?>

    /** „Ano, to jsem byl já" — nepotvrzené přihlášení se uzná. Odmítnutí je [terminate]. */
    suspend fun confirm(sessionId: Long): Result<Unit>
}

data class Devices(
    /** Tohle zařízení; `null` jen teoreticky, server ho vrací vždycky. */
    val current: Device?,
    /** Ostatní zařízení, naposledy aktivní napřed. */
    val others: List<Device>,
    val inactiveTtlDays: Int,
)

data class Device(
    val id: Long,
    val isCurrent: Boolean,
    /** Přihlášení čeká na heslo dvoufázového ověření — zařízení zatím k účtu nemá přístup. */
    val isPasswordPending: Boolean,
    val isUnconfirmed: Boolean,
    val canAcceptCalls: Boolean,
    val canAcceptSecretChats: Boolean,
    val kind: DeviceKind,
    val applicationName: String,
    val applicationVersion: String,
    val isOfficialApplication: Boolean,
    val deviceModel: String,
    val platform: String,
    val systemVersion: String,
    /** Unixový čas v sekundách. */
    val logInDate: Int,
    val lastActiveDate: Int,
    val ipAddress: String,
    val location: String,
)

/** Druh zařízení — jen tolik, kolik rozliší ikona. */
enum class DeviceKind { Phone, Tablet, Desktop, Browser, Unknown }

data class ConnectedWebsite(
    val id: Long,
    val domain: String,
    val browser: String,
    val platform: String,
    val logInDate: Int,
    val lastActiveDate: Int,
    val ipAddress: String,
    val location: String,
)

data class UnconfirmedDevice(
    val sessionId: Long,
    val deviceModel: String,
    val location: String,
    val date: Int,
)

/**
 * Volby automatického odhlášení nečinných zařízení, ve dnech — tytéž nabízí oficiální klient.
 * Půl roku je 183 dní, ne 180: tak ho server posílá u účtu, který volbu nikdy neměnil.
 */
val INACTIVE_TTL_OPTIONS = listOf(7, 30, 90, 183, 365)
