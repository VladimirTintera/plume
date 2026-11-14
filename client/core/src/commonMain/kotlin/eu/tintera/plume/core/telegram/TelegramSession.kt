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

    /**
     * Jazykový balíček Telegramu podle jazyka aplikace ([languageCode]) — server podle něj
     * lokalizuje texty, které posílá sám (kategorie emoji, podmínky služby…). Volá se při startu
     * i po změně jazyka; jazyk bez oficiálního balíčku vrátí výchozí (angličtinu).
     */
    suspend fun syncLanguagePack(languageCode: String) {}

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

    /**
     * Pošle kód znovu, jinou cestou — tou, kterou hlásí [AuthStage.WaitCode.next]. Jde jen
     * v [AuthStage.WaitCode] s nenulovým `next` a po uplynutí [AuthStage.WaitCode.resendTimeout];
     * kudy kód odešel doopravdy, ohlásí TDLib novým stavem.
     */
    suspend fun resendCode(): Result<Unit>

    /**
     * E-mail pro přihlašovací kódy — v [AuthStage.WaitEmailAddress], a znovu (překlep v adrese)
     * v [AuthStage.WaitEmailCode] s [AuthStage.WaitEmailCode.isSetup]. Kód na adresu odejde hned.
     */
    suspend fun submitEmailAddress(email: String): Result<Unit>

    /** Kód z e-mailu v [AuthStage.WaitEmailCode]. */
    suspend fun submitEmailCode(code: String): Result<Unit>

    /**
     * Kód na tutéž adresu znovu. Jen s [AuthStage.WaitEmailCode.isSetup]: adresu, kterou zná
     * jen server (přihlašovací e-mail nastavený dřív), TDLib znovu obeslat neumí.
     */
    suspend fun resendEmailCode(): Result<Unit>

    /**
     * Reset přihlašovacího e-mailu — pro toho, kdo k němu ztratil přístup. Jde jen
     * s [AuthStage.WaitEmailCode.reset]. U [EmailReset.Available] s čekáním se tím čekání
     * rozjede (nový stav s [EmailReset.Pending]); hotový reset pošle kód na telefonní číslo
     * a automat přejde do [AuthStage.WaitCode].
     */
    suspend fun resetEmailAddress(): Result<Unit>

    /** Heslo dvoufázového ověření (cloud password), ne kód z SMS. */
    suspend fun submitPassword(password: String): Result<Unit>

    /**
     * Pošle kód na obnovu hesla na obnovovací e-mail. Jde jen v [AuthStage.WaitPassword]
     * s [AuthStage.WaitPassword.canRecover]; kam kód odešel, ohlásí TDLib novým stavem
     * ([AuthStage.WaitPassword.recoveryEmailPattern]).
     */
    suspend fun requestPasswordRecovery(): Result<Unit>

    /**
     * Ověří kód z e-mailu, ale ještě nic nemění. Samostatný krok proto, aby uživatel nové heslo
     * vymýšlel až nad kódem, o kterém je jisté, že platí — jinak by se překlep v kódu ukázal
     * až po napsání hesla a musel by ho psát znovu.
     */
    suspend fun checkPasswordRecoveryCode(code: String): Result<Unit>

    /** Nastaví nové heslo kódem z e-mailu a tím přihlášení dokončí. [newHint] smí být prázdná. */
    suspend fun recoverPassword(code: String, newPassword: String, newHint: String): Result<Unit>

    /** Přepne přihlášení do QR režimu — výsledkem je [AuthStage.WaitQrConfirmation] s odkazem. */
    suspend fun startQrLogin(): Result<Unit>

    /**
     * Zahodí rozjeté přihlášení (QR, kód, heslo) a vrátí automat na začátek, k zadání čísla.
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

    /**
     * Ukončí klienta účtu natrvalo, bez náhrady: účet z aplikace mizí (odhlášený vedle jiných
     * účtů, nebo zrušené přidání účtu). S [destroy] TDLib zahodí i data, jinak klienta jen
     * zavře. Vrátí se, až je klient zavřený (nejdéle po pár sekundách), takže pak jde smazat
     * jeho adresář.
     */
    suspend fun shutdown(destroy: Boolean) {}

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

/**
 * Podoba kódu, který server čeká. Doménový obraz TDLib `AuthenticationCodeInfo`.
 *
 * **Kódy nejsou pořád stejně dlouhé** — z Telegramu chodí pětimístný, SMS a Fragment posílají
 * i šestimístné. Hádat se to ale nemusí: délku pošle TDLib u každého přihlášení zvlášť, takže
 * se podle ní nakreslí přesně tolik políček, kolik jich bude potřeba.
 *
 * **A nemusí to být ani číslo.** Slovo a fráze z SMS a prozvonění mají [digits] `null` —
 * tam zůstane obyčejné pole, protože do políček na číslice se slovo nevejde.
 */
data class AuthCode(
    /** Počet číslic, nebo `null`, když kód číslo není. */
    val digits: Int?,
    val delivery: CodeDelivery,
)

/**
 * Kudy kód dorazí. Rozhoduje o větě nad polem — dokud se nesledovalo, stála tam jedna věta
 * pro všechny případy („do jiného klienta nebo SMS"), tedy odhad, který byl půlkrát vedle.
 */
enum class CodeDelivery {
    /** Zpráva v Telegramu na jiném přihlášeném zařízení. */
    TelegramApp,

    /** SMS. Sem patří i Firebase varianty — to je pořád SMS, jen s ověřením zařízení navíc. */
    Sms,

    /** Hovor, ve kterém se kód nadiktuje. */
    Call,

    /** Zavoláme a zavěsíme: kód je konec čísla, ze kterého hovor přišel. */
    CallerNumber,

    /** V SMS je jedno slovo, ne číslo. */
    Word,

    /** V SMS je krátká fráze, ne číslo. */
    Phrase,

    /** Číslo koupené na Fragmentu — kód čeká tam. */
    Fragment,
}

/**
 * Reset přihlašovacího e-mailu. Po něm kód přijde na telefonní číslo jako dřív — cesta pro
 * toho, kdo k e-mailu ztratil přístup. Doménový obraz TDLib `EmailAddressResetState`.
 */
sealed interface EmailReset {
    /** Reset jde zažádat; proběhne za [waitSeconds] od žádosti (0 = hned, s Premium). */
    data class Available(val waitSeconds: Int) : EmailReset

    /**
     * Reset je zažádaný a proběhne za [resetInSeconds]. TDLib čas přepočítá jen v novém stavu,
     * průběžně ne — odpočítává se proto od příchodu stavu.
     */
    data class Pending(val resetInSeconds: Int) : EmailReset
}

/** Fáze přihlašovacího automatu. Doménový obraz TDLib `AuthorizationState`. */
sealed interface AuthStage {

    /** Klient startuje nebo čeká na odpověď serveru. */
    data object Connecting : AuthStage

    data object WaitPhoneNumber : AuthStage

    /** Server čeká kód; jeho podobu nese [code], podle ní se kreslí políčka. */
    data class WaitCode(
        val code: AuthCode,
        /**
         * Kudy půjde kód, když si ho uživatel nechá poslat znovu, nebo `null`, když jinou
         * cestu server nenabízí. Typicky SMS nebo hovor po kódu poslaném do Telegramu —
         * bez toho by se nepřihlásil nikdo, kdo k přihlášenému zařízení nemá přístup.
         */
        val next: CodeDelivery? = null,
        /**
         * Za kolik sekund od příchodu stavu smí kód odejít znovu. TDLib posílá ZBÝVAJÍCÍ čas,
         * ne původní lhůtu, takže se odpočítává od chvíle, kdy stav dorazil.
         */
        val resendTimeout: Int = 0,
    ) : AuthStage

    /**
     * Server chce e-mail, na který bude posílat přihlašovací kódy místo SMS — u nového čísla,
     * nebo u účtu, který se přihlašuje často (dokumentace `auth.sentCodeTypeSetUpEmailRequired`).
     *
     * Přihlášení přes Google a Apple, které TDLib v tomhle stavu nabízí, tu není: token musí
     * vydat Google či Apple pro OAuth klienta TELEGRAMU, a ten je svázaný s podpisem
     * oficiální aplikace — cizí aplikace ho nedostane.
     */
    data object WaitEmailAddress : AuthStage

    /** Kód odešel e-mailem: buď na adresu právě zadanou, nebo na dřív nastavený přihlašovací e-mail. */
    data class WaitEmailCode(
        /** Maska adresy („a***@g***.com"), nebo `null`, když ji server neposlal. */
        val emailPattern: String?,
        /** Počet číslic, nebo `null`, když ho server neřekl — pak obyčejné pole. */
        val digits: Int?,
        /**
         * Adresu zadal uživatel v tomhle přihlášení ([WaitEmailAddress]), takže ji jde opravit
         * a kód poslat znovu. U přihlašovacího e-mailu nastaveného dřív zná adresu jen server.
         */
        val isSetup: Boolean,
        /** Jestli a kdy jde e-mail resetovat; `null`, když reset server nenabízí. */
        val reset: EmailReset?,
    ) : AuthStage

    data class WaitPassword(
        val hint: String?,
        /** Účet má obnovovací e-mail, takže zapomenuté heslo jde obnovit kódem z něj. */
        val canRecover: Boolean = false,
        /**
         * Maska e-mailu, kam odešel kód na obnovu („a***@g***.com"). **`null`, dokud se o obnovu
         * nikdo neřekl** — TDLib ji posílá až v novém stavu po `requestAuthenticationPasswordRecovery`.
         */
        val recoveryEmailPattern: String? = null,
    ) : AuthStage

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
