package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.StateFlow
import eu.tintera.plume.core.ui.UiText

/**
 * Hranice mezi aplikací a TDLib.
 *
 * **Tohle rozhraní je jediná věc, kterou zbytek appky o Telegramu ví.** Za ním žije
 * `dev.g000sha256:tdl-coroutines` a přes něj nativní TDLib. Důvody, proč šev existuje:
 *
 * 1. Upstream TDLib mění API i v patch verzích. Když se to stane, přepíše se [TdlTelegramSession]
 *    a zbytek projektu se nezmění.
 * 2. Wrapper `tdl-coroutines` je komunitní projekt jednoho autora. Kdyby přestal vycházet, dá se
 *    nahradit vlastním `cinterop` nad C API `tdjson` (`td_create_client_id`, `td_send`, `td_receive`,
 *    `td_execute`) — a zase se mění jen implementace pod tímhle rozhraním.
 * 3. Testovatelnost: ViewModely mockují tohle, ne TDLib.
 *
 * Pravidlo: **žádný typ z `dev.g000sha256.tdl.*` nesmí prosáknout do signatur v tomhle balíčku.**
 */
interface TelegramSession {

    /** Aktuální fáze přihlášení. TDLib ji řídí jako stavový automat, my ho jen překládáme do domény. */
    val authStage: StateFlow<AuthStage>

    /**
     * Nastartuje klienta a přihlásí se k odběru update flows.
     *
     * Musí se zavolat dřív než cokoliv jiného. Pořadí je podstatné: odběr update flow musí běžet
     * dřív, než odejde první request — jinak se odpověď (typicky `AuthorizationStateWaitTdlibParameters`)
     * ztratí a přihlášení zůstane viset.
     */
    suspend fun start()

    suspend fun submitPhoneNumber(phoneNumber: String): Result<Unit>

    suspend fun submitCode(code: String): Result<Unit>

    /** Heslo dvoufázového ověření (cloud password), ne kód z SMS. */
    suspend fun submitPassword(password: String): Result<Unit>

    /** Přepne přihlášení do QR režimu — výsledkem je [AuthStage.WaitQrConfirmation] s odkazem. */
    suspend fun startQrLogin(): Result<Unit>

    /**
     * Zahodí rozjeté QR přihlášení a vrátí automat na začátek, k zadání čísla.
     *
     * **Jinak se z QR režimu ven nedostaneme.** TDLib u `setAuthenticationPhoneNumber`
     * i `requestQrCodeAuthentication` vyjmenovává stavy, ve kterých se smí volat, a
     * `authorizationStateWaitOtherDeviceConfirmation` v žádném z těch seznamů není. Bez tohohle
     * by uživatel, který nemá čím QR kód naskenovat, neměl jinou cestu než aplikaci odinstalovat.
     */
    suspend fun restartLogin(): Result<Unit>

    /**
     * Země pro výběr předvolby, bez těch, které TDLib schovává. Seznam **dává TDLib**
     * (`getCountries`), nedržíme vlastní tabulku: názvy jsou přeložené, vlajky a předvolby
     * aktuální a nic se u nás nerozejde se skutečností.
     */
    suspend fun countries(): Result<List<Country>>

    /** Země odhadnutá podle IP — jen na předvyplnění, uživatel ji může změnit. */
    suspend fun suggestedCountryCode(): Result<String>

    /**
     * Rozbor rozepsaného čísla. Volá se při psaní, takže jde o lokální (synchronní) dotaz
     * do dat, která má TDLib u sebe — na síť se nechodí.
     */
    suspend fun phoneNumberInfo(prefix: String): Result<PhoneNumberDetails>

    suspend fun logOut(): Result<Unit>

    /** Přihlášený účet. Dává smysl až v [AuthStage.Ready]. */
    suspend fun me(): Result<AccountInfo>
}

/**
 * Země v seznamu předvoleb.
 *
 * [callingCode] je ta, kterou ukazujeme; zemí s víc předvolbami je hrstka a k PÁROVÁNÍ
 * rozepsaného čísla se stejně nepoužívá — to dělá TDLib v [TelegramSession.phoneNumberInfo].
 */
data class Country(
    /** Dvoupísmenný kód ISO 3166-1 alpha-2. Slouží i jako klíč v seznamu. */
    val code: String,
    /**
     * Název v jazyce zařízení. Překlad dává OPERAČNÍ SYSTÉM podle [code] (viz
     * `localizedCountryName`), ne TDLib — ten před autorizací vrací anglické názvy.
     */
    val name: String,
    /**
     * Anglický název od TDLib. Nekreslí se, ale hledá se podle něj taky: kdo zná zemi jen
     * anglicky, ji v českém seznamu jinak nenajde.
     */
    val englishName: String,
    /** Emoji vlajky; může být prázdné. */
    val flagEmoji: String,
    /** Předvolba včetně `+`. */
    val callingCode: String,
)

/**
 * Co TDLib pozná z rozepsaného čísla.
 *
 * [mask] je předpis formátování, ve kterém **`-` znamená očekávanou číslici** a cokoli jiného
 * je oddělovač. Vlastní tabulku formátů tím držet nemusíme.
 */
data class PhoneNumberDetails(
    val country: Country?,
    /** Předvolba bez `+`, jak ji vrací TDLib. */
    val callingCode: String,
    val mask: String,
)

/** Základní údaje o přihlášeném účtu. */
data class AccountInfo(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val phoneNumber: String,
    val username: String?,
    /** `fileId` malé varianty profilové fotky, nebo `null` bez fotky. */
    val photoFileId: Int? = null,
) {
    val displayName: String get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
}

/** Fáze přihlašovacího automatu. Doménový obraz TDLib `AuthorizationState`. */
sealed interface AuthStage {

    /** Klient startuje nebo čeká na odpověď serveru. */
    data object Connecting : AuthStage

    data object WaitPhoneNumber : AuthStage

    data object WaitCode : AuthStage

    data class WaitPassword(val hint: String?) : AuthStage

    /** QR přihlášení: [link] se vykreslí jako QR kód a potvrdí se v už přihlášeném klientovi. */
    data class WaitQrConfirmation(val link: String) : AuthStage

    /** Účet existuje, ale ještě nemá jméno — registrace nového uživatele. */
    data object WaitRegistration : AuthStage

    data object Ready : AuthStage

    /**
     * Odhlašování běží: TDLib maže lokální data a zavírá klienta, pak se rozjede nový a skončí
     * v [WaitPhoneNumber]. Není to cílový stav — „odhlášený" uživatel je prostě na začátku přihlášení.
     */
    data object LoggingOut : AuthStage

    /**
     * Chyba, ze které se spike sám nedostane — typicky chybějící nebo neplatné `api_id`/`api_hash`.
     * Nese text, protože nejčastější příčina je konfigurační a uživatel ji musí vidět.
     */
    data class Failed(val message: UiText) : AuthStage
}
