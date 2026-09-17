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

    suspend fun logOut(): Result<Unit>

    /** Přihlášený účet. Dává smysl až v [AuthStage.Ready]. */
    suspend fun me(): Result<AccountInfo>
}

/** Základní údaje o přihlášeném účtu. */
data class AccountInfo(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val phoneNumber: String,
    val username: String?,
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
