package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import eu.tintera.plume.BuildConfiguration
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.AuthorizationState
import dev.g000sha256.tdl.dto.CountryInfo
import dev.g000sha256.tdl.dto.AuthorizationStateClosed
import dev.g000sha256.tdl.dto.AuthorizationStateClosing
import dev.g000sha256.tdl.dto.AuthorizationStateLoggingOut
import dev.g000sha256.tdl.dto.AuthorizationStateReady
import dev.g000sha256.tdl.dto.AuthorizationStateWaitCode
import dev.g000sha256.tdl.dto.AuthorizationStateWaitEmailAddress
import dev.g000sha256.tdl.dto.AuthorizationStateWaitEmailCode
import dev.g000sha256.tdl.dto.AuthorizationStateWaitOtherDeviceConfirmation
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPassword
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPhoneNumber
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPremiumPurchase
import dev.g000sha256.tdl.dto.AuthorizationStateWaitRegistration
import dev.g000sha256.tdl.dto.AuthorizationStateWaitTdlibParameters
import dev.g000sha256.tdl.dto.OptionValueInteger
import dev.g000sha256.tdl.TdlThrowable
import dev.g000sha256.tdl.toResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path.Companion.toPath
import eu.tintera.plume.core.secrets.DatabaseKeyStore
import eu.tintera.plume.core.ui.foldDiacritics
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.auth_error_email_unsupported
import eu.tintera.plume.resources.auth_error_premium_required
import eu.tintera.plume.resources.auth_error_start_failed
import eu.tintera.plume.resources.auth_error_missing_api_credentials

/**
 * Implementace [TelegramSession] nad `dev.g000sha256:tdl-coroutines`.
 *
 * Řeší **jen přihlášení**. Seznam chatů je v [TdlChatRepository]; obě třídy sdílejí klienta přes
 * [TelegramClientHolder]. Import `dev.g000sha256.tdl.*` smí být jen v balíčku `core.telegram` —
 * když se změní upstream API (a mění se i v patch verzích TDLib), mění se jen tenhle balíček.
 */
class TdlTelegramSession(
    private val holder: TelegramClientHolder,
    private val paths: TelegramPaths,
    private val keys: DatabaseKeyStore,
    private val scope: CoroutineScope,
) : TelegramSession {

    private val logger = Logger.withTag("Telegram")

    private val client: TdlClient get() = holder.client

    private val _authStage = MutableStateFlow<AuthStage>(AuthStage.Connecting)
    override val authStage: StateFlow<AuthStage> = _authStage.asStateFlow()

    private var started = false

    /**
     * Běží restart přihlášení z QR zpátky na číslo (`destroy`). Zavřený klient se jinak hlásí
     * jako [AuthStage.LoggingOut] („Odhlašuji"), což by tady lhalo — uživatel se odhlásit nemohl,
     * protože přihlášený nikdy nebyl.
     */
    private var restartingLogin = false

    override suspend fun start() {
        if (started) return
        started = true

        attach(client)
    }

    /**
     * Rozjede přihlašovací automat nad [target].
     *
     * Odběr MUSÍ běžet dřív, než odejde první request. TDLib odpoví na `getAuthorizationState`
     * updatem, ne návratovou hodnotou — kdybychom se přihlásili až po requestu, první stav se
     * ztratí a přihlašovací automat se nikdy nerozjede. `UNDISPATCHED` to zaručuje: korutina
     * se až k prvnímu uspání (tedy k registraci odběru) provede hned, ne až ji dispatcher naplánuje.
     *
     * **Konec odběru = zavřený klient, a teprve podle něj se vyrábí nový.** Na samotný stav
     * `AuthorizationStateClosed` se spolehnout nejde: wrapper ukončuje update flows jedním
     * příznakem `stopped` SPOLEČNÝM pro všechny odběratele klienta. Kdo `Closed` zpracuje první,
     * příznak nastaví a ostatním odběrům flow skončí dřív, než k nim `Closed` dojde — odběratelů
     * jsou desítky (repositories přes `holder.updates`), takže tenhle o něj přicházel. Při
     * vzdáleném odhlášení (jiné zařízení ukončilo relaci) tak aplikace zůstala natrvalo na
     * „Odhlašování". Flow ale jinak než zavřením klienta neskončí, takže konec `collect` je
     * spolehlivý signál v obou případech.
     */
    private suspend fun attach(target: TdlClient) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            target.authorizationStateUpdates.collect { update ->
                handleAuthorizationState(update.authorizationState)
            }
            onClientClosed(target)
        }

        target.getAuthorizationState()
            .toResult()
            .onFailure { logger.e(it) { "Nepodařilo se přečíst authorization state." } }
    }

    private suspend fun handleAuthorizationState(state: AuthorizationState) {
        logger.d { "AuthorizationState -> ${state.stateName()}" }

        when (state) {
            is AuthorizationStateWaitTdlibParameters -> sendTdlibParameters()

            is AuthorizationStateWaitPhoneNumber -> _authStage.value = AuthStage.WaitPhoneNumber
            is AuthorizationStateWaitCode -> _authStage.value = AuthStage.WaitCode
            is AuthorizationStateWaitPassword ->
                _authStage.value = AuthStage.WaitPassword(state.passwordHint.takeIf { it.isNotBlank() })

            is AuthorizationStateWaitOtherDeviceConfirmation ->
                _authStage.value = AuthStage.WaitQrConfirmation(state.link)

            is AuthorizationStateWaitRegistration -> _authStage.value = AuthStage.WaitRegistration
            is AuthorizationStateReady -> _authStage.value = AuthStage.Ready

            // Při návratu z QR na číslo se klient taky zavírá, jenže odhlášení to není —
            // uživatel přihlášený nikdy nebyl. „Odhlašuji" by na ten okamžik lhalo.
            is AuthorizationStateLoggingOut,
            is AuthorizationStateClosing,
                -> _authStage.value = closingStage()

            // Novou instanci vyrábí až konec odběru (`onClientClosed`), ne tenhle stav — ten
            // sem dojít nemusí, viz `attach`.
            is AuthorizationStateClosed -> _authStage.value = closingStage()

            // Stavy kolem e-mailového ověření a Premium nákupu spike neřeší. Padnout na nich ale
            // nesmíme — uživatel by viděl zamrzlou obrazovku bez vysvětlení. Text pojmenovává
            // KONKRÉTNÍ fázi: dřív se do hlášky vkládal název třídy, což uživateli neřeklo nic
            // (a po zapnutí R8 to byl rovnou obfuskovaný nesmysl — „… není podporovaná: mu").
            is AuthorizationStateWaitEmailAddress,
            is AuthorizationStateWaitEmailCode,
                -> _authStage.value = AuthStage.Failed(UiText(Res.string.auth_error_email_unsupported))

            is AuthorizationStateWaitPremiumPurchase -> _authStage.value = AuthStage.Failed(UiText(Res.string.auth_error_premium_required))
        }
    }

    private fun closingStage(): AuthStage =
        if (restartingLogin) AuthStage.Connecting else AuthStage.LoggingOut

    /**
     * Zavřený klient už nic neudělá — ani nepřijme nové přihlášení. Bez nové instance by aplikace
     * zůstala natrvalo stát na odhlášení. Nový klient začne od `WaitTdlibParameters`, takže se
     * sám dostane až k zadání čísla.
     *
     * Kontrola [target] proti aktuálnímu klientovi je pojistka, aby jeden zavřený klient nevyrobil
     * náhradu dvakrát.
     */
    private suspend fun onClientClosed(target: TdlClient) {
        if (holder.client !== target) return
        logger.i { "Klient TDLib zavřený, zakládám nový." }
        _authStage.value = closingStage()
        restartingLogin = false
        attach(holder.recreate())
    }

    /**
     * Název stavu do logu. **Ručně psaný, ne `state::class.simpleName`** — R8 třídy přejmenovává,
     * takže v release buildu z `AuthorizationStateReady` zbylo „du" a log (a s ním i kontext
     * přiložený k hlášení pádu) přestal cokoli říkat. Literál přejmenovat nejde.
     *
     * `when` je schválně bez `else`: `AuthorizationState` je sealed, takže nový stav v novější
     * TDLib rozbije překlad a někdo ho tu musí pojmenovat. To je lepší než tiché „neznámý stav"
     * právě ve chvíli, kdy se přihlášení zaseklo na něčem novém.
     */
    private fun AuthorizationState.stateName(): String = when (this) {
        is AuthorizationStateWaitTdlibParameters -> "WaitTdlibParameters"
        is AuthorizationStateWaitPhoneNumber -> "WaitPhoneNumber"
        is AuthorizationStateWaitEmailAddress -> "WaitEmailAddress"
        is AuthorizationStateWaitEmailCode -> "WaitEmailCode"
        is AuthorizationStateWaitCode -> "WaitCode"
        is AuthorizationStateWaitOtherDeviceConfirmation -> "WaitOtherDeviceConfirmation"
        is AuthorizationStateWaitRegistration -> "WaitRegistration"
        is AuthorizationStateWaitPassword -> "WaitPassword"
        is AuthorizationStateWaitPremiumPurchase -> "WaitPremiumPurchase"
        is AuthorizationStateReady -> "Ready"
        is AuthorizationStateLoggingOut -> "LoggingOut"
        is AuthorizationStateClosing -> "Closing"
        is AuthorizationStateClosed -> "Closed"
    }

    private suspend fun sendTdlibParameters() {
        if (BuildConfiguration.TELEGRAM_API_ID == 0 || BuildConfiguration.TELEGRAM_API_HASH.isBlank()) {
            _authStage.value = AuthStage.Failed(UiText(Res.string.auth_error_missing_api_credentials))
            return
        }

        if (openEncryptedDatabase()) enableNotifications()
    }

    /**
     * Otevře databázi TDLib, ať už je v jakémkoli stavu. Pravidlo i s vysvětlením kroků je
     * v [unlockDatabase] — tady zůstala jen volání, která potřebují klienta.
     */
    private suspend fun openEncryptedDatabase(): Boolean {
        val failure = unlockDatabase(
            keys = keys,
            isWrongKey = { it.isWrongDatabaseKey() },
            open = { key -> openDatabase(key) },
            encrypt = { key -> encryptDatabase(key) },
            wipe = { cause ->
                logger.e(cause) { "Databázi TDLib nejde odemknout, mažu ji a začínám od přihlášení." }
                wipeLocalData()
            },
        )
        if (failure != null) return failStart(failure)
        return true
    }

    /**
     * Vrací chybu, ne výsledek — volajícího zajímá jen to, jestli (a proč) otevření selhalo.
     *
     * Pojmenované argumenty schválně: upstream do těchhle parametrů přidává a přejmenovává
     * i v patch verzích, poziční volání by se tiše rozsypalo.
     */
    private suspend fun openDatabase(databaseKey: ByteArray): Throwable? {
        paths.prepare()
        return client.setTdlibParameters(
            useTestDc = false,
            databaseDirectory = paths.databaseDirectory,
            filesDirectory = paths.filesDirectory,
            databaseEncryptionKey = databaseKey,
            useFileDatabase = true,
            useChatInfoDatabase = true,
            useMessageDatabase = true,
            useSecretChats = false,
            apiId = BuildConfiguration.TELEGRAM_API_ID,
            apiHash = BuildConfiguration.TELEGRAM_API_HASH,
            systemLanguageCode = platformLanguageCode,
            deviceModel = platformDeviceModel,
            systemVersion = platformSystemVersion,
            applicationVersion = "0.1.0",
        )
            .toResult()
            .exceptionOrNull()
    }

    /**
     * Přešifruje otevřenou databázi uloženým klíčem.
     *
     * Při selhání se klíč NEZAHAZUJE: databáze ho zatím nemá, ale příští start ji zase otevře
     * prázdným klíčem a zkusí to znovu (krok 2 v [openEncryptedDatabase]). Aplikace zatím běží
     * dál, jen s nešifrovanou databází — proto `e` s výjimkou, ať je to v hlášeních vidět.
     */
    private suspend fun encryptDatabase(key: ByteArray) {
        client.setDatabaseEncryptionKey(newEncryptionKey = key)
            .toResult()
            .onFailure {
                logger.e(it) { "Databázi TDLib se nepodařilo zašifrovat, zůstává nešifrovaná." }
                return
            }

        logger.i { "Databáze TDLib zašifrovaná." }
    }

    /**
     * Zahodí databázi i stažená média. **Soubory jdou pryč s databází, ne zvlášť**: `fileId` platí
     * jen v jedné databázi TDLib, takže bez ní jsou stažená média neadresovatelné bajty.
     */
    private fun wipeLocalData() {
        FileSystem.SYSTEM.deleteRecursively(paths.databaseDirectory.toPath(), mustExist = false)
        FileSystem.SYSTEM.deleteRecursively(paths.filesDirectory.toPath(), mustExist = false)
    }

    /**
     * Chyba, která znamená „tímhle klíčem se databáze otevřít nedá".
     *
     * Dokumentace `setTdlibParameters` slibuje kód 401, hlášku si píše sama TDLib (ověřeno
     * v knihovně: `strings libtdjsonjava.so | grep "encryption key"`). Bere se obojí, protože
     * na tomhle rozhodnutí visí smazání lokálních dat — a kdyby se nepoznalo, uživatel by místo
     * nového přihlášení dostal obrazovku s chybou, ze které nevede cesta ven.
     */
    private fun Throwable.isWrongDatabaseKey(): Boolean =
        this is TdlThrowable && (code == WRONG_KEY_CODE || message == WRONG_KEY_MESSAGE)

    /** Vrací `false`, aby šlo psát `return failStart(…)`. */
    private fun failStart(failure: Throwable): Boolean {
        logger.e(failure) { "setTdlibParameters selhalo." }
        _authStage.value = AuthStage.Failed(failure.userMessage(Res.string.auth_error_start_failed))
        return false
    }

    /**
     * Zapne notifikační API TDLib.
     *
     * **Ve výchozím stavu je vypnuté**: `notification_group_count_max` je 0, což znamená, že TDLib
     * `UpdateNotificationGroup` vůbec negeneruje. Bez tohohle volání je celá notifikační vrstva
     * mrtvá a nedá se to nijak poznat — nepřijde žádná chyba, prostě se nic neděje.
     *
     * Povolený rozsah obou voleb je 1–25.
     */
    private suspend fun enableNotifications() {
        client.setOption(
            name = "notification_group_count_max",
            value = OptionValueInteger(value = NOTIFICATION_GROUP_COUNT_MAX),
        )
            .toResult()
            .onFailure { logger.e(it) { "Nepodařilo se zapnout notifikace." } }

        client.setOption(
            name = "notification_group_size_max",
            value = OptionValueInteger(value = NOTIFICATION_GROUP_SIZE_MAX),
        )
            .toResult()
            .onFailure { logger.e(it) { "Nepodařilo se nastavit velikost skupiny notifikací." } }

        logger.d { "Notifikační API zapnuto." }
    }

    override suspend fun submitPhoneNumber(phoneNumber: String): Result<Unit> =
        client.setAuthenticationPhoneNumber(phoneNumber = phoneNumber)
            .toResult()
            .map { }

    override suspend fun submitCode(code: String): Result<Unit> =
        client.checkAuthenticationCode(code = code)
            .toResult()
            .map { }

    override suspend fun submitPassword(password: String): Result<Unit> =
        client.checkAuthenticationPassword(password = password)
            .toResult()
            .map { }

    override suspend fun startQrLogin(): Result<Unit> =
        client.requestQrCodeAuthentication(otherUserIds = LongArray(0))
            .toResult()
            .map { }

    /**
     * `destroy`, ne `logOut`: odhlašovat není co (účet ještě není) a `logOut` navíc chce funkční
     * připojení — právě to nemusí mít ten, kdo se z QR vrací kvůli tomu, že nemá čím kód
     * naskenovat. `destroy` funguje offline a smí se volat před autorizací.
     *
     * Zavřený klient se nahradí novým v `onClientClosed`; ten začne od `WaitTdlibParameters`
     * a sám dojde k zadání čísla.
     */
    override suspend fun restartLogin(): Result<Unit> {
        restartingLogin = true
        return client.destroy()
            .toResult()
            .map { }
            .onFailure { restartingLogin = false }
    }

    override suspend fun countries(): Result<List<Country>> =
        client.getCountries()
            .toResult()
            .map { countries ->
                countries.countries
                    .filterNot { it.isHidden }
                    .mapNotNull { info -> info.toCountry() }
                    // TDLib řadí podle KÓDU země (AD, AE, AF…), což pro čtenáře není pořadí.
                    // Výjimka z pravidla „pořadí necháváme od TDLib": u chatů je to pořadí,
                    // na které je uživatel zvyklý z jiných klientů, tady je to náhodná řada.
                    .sortedBy { foldDiacritics(it.name).lowercase() }
            }

    /**
     * Překlad názvu dává systém, ne TDLib — ten před autorizací vrací anglické názvy. Anglický
     * název se přesto nese dál, protože se podle něj taky hledá.
     */
    private fun CountryInfo.toCountry(fallbackCallingCode: String? = null): Country? {
        val calling = callingCodes.firstOrNull() ?: fallbackCallingCode ?: return null
        return Country(
            code = countryCode,
            name = localizedCountryName(countryCode) ?: name,
            englishName = englishName,
            flagEmoji = flagEmoji,
            callingCode = "+$calling",
        )
    }

    override suspend fun suggestedCountryCode(): Result<String> =
        client.getCountryCode()
            .toResult()
            .map { it.text }

    override suspend fun phoneNumberInfo(prefix: String): Result<PhoneNumberDetails> =
        client.getPhoneNumberInfoSync(languageCode = platformLanguageCode, phoneNumberPrefix = prefix)
            .toResult()
            .map { info ->
                PhoneNumberDetails(
                    country = info.country?.toCountry(fallbackCallingCode = info.countryCallingCode),
                    callingCode = info.countryCallingCode,
                    mask = info.formattedPhoneNumber,
                )
            }

    override suspend fun logOut(): Result<Unit> =
        client.logOut()
            .toResult()
            .map { }

    override suspend fun me(): Result<AccountInfo> =
        client.getMe()
            .toResult()
            .map { user ->
                AccountInfo(
                    id = user.id,
                    firstName = user.firstName,
                    lastName = user.lastName,
                    phoneNumber = user.phoneNumber,
                    username = user.usernames?.editableUsername?.takeIf { it.isNotBlank() },
                )
            }

    private companion object {
        /** Kolik chatů naráz může mít aktivní notifikace. Povolený rozsah TDLib je 1–25. */
        const val NOTIFICATION_GROUP_COUNT_MAX = 5L

        /** Kolik notifikací se drží v jedné skupině (chatu). */
        const val NOTIFICATION_GROUP_SIZE_MAX = 10L


        const val WRONG_KEY_CODE = 401
        const val WRONG_KEY_MESSAGE = "Wrong database encryption key"
    }
}
