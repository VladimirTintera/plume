package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import eu.tintera.plume.BuildConfiguration
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.AuthorizationState
import dev.g000sha256.tdl.dto.AuthorizationStateClosed
import dev.g000sha256.tdl.dto.AuthorizationStateClosing
import dev.g000sha256.tdl.dto.AuthorizationStateLoggingOut
import dev.g000sha256.tdl.dto.AuthorizationStateReady
import dev.g000sha256.tdl.dto.AuthorizationStateWaitCode
import dev.g000sha256.tdl.dto.AuthorizationStateWaitOtherDeviceConfirmation
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPassword
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPhoneNumber
import dev.g000sha256.tdl.dto.AuthorizationStateWaitRegistration
import dev.g000sha256.tdl.dto.AuthorizationStateWaitTdlibParameters
import dev.g000sha256.tdl.toResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Implementace [TelegramSession] nad `dev.g000sha256:tdl-coroutines`.
 *
 * Řeší **jen přihlášení**. Seznam chatů je v [TdlChatRepository]; obě třídy sdílejí klienta přes
 * [TelegramClientHolder]. Import `dev.g000sha256.tdl.*` smí být jen v balíčku `core.telegram` —
 * když se změní upstream API (a mění se i v patch verzích TDLib), mění se jen tenhle balíček.
 */
class TdlTelegramSession(
    holder: TelegramClientHolder,
    private val paths: TelegramPaths,
    private val scope: CoroutineScope,
) : TelegramSession {

    private val logger = Logger.withTag("Telegram")

    private val client: TdlClient = holder.client

    private val _authStage = MutableStateFlow<AuthStage>(AuthStage.Connecting)
    override val authStage: StateFlow<AuthStage> = _authStage.asStateFlow()

    private var started = false

    override suspend fun start() {
        if (started) return
        started = true

        // Odběr MUSÍ běžet dřív, než odejde první request. TDLib odpoví na `getAuthorizationState`
        // updatem, ne návratovou hodnotou — kdybychom se přihlásili až po requestu, první stav se
        // ztratí a přihlašovací automat se nikdy nerozjede.
        scope.launch {
            client.authorizationStateUpdates.collect { update ->
                handleAuthorizationState(update.authorizationState)
            }
        }

        client.getAuthorizationState()
            .toResult()
            .onFailure { logger.e(it) { "Nepodařilo se přečíst authorization state." } }
    }

    private suspend fun handleAuthorizationState(state: AuthorizationState) {
        logger.d { "AuthorizationState -> ${state::class.simpleName}" }

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

            is AuthorizationStateLoggingOut,
            is AuthorizationStateClosing,
            is AuthorizationStateClosed,
                -> _authStage.value = AuthStage.LoggedOut

            // Stavy kolem e-mailového ověření a Premium nákupu spike neřeší. Padnout na nich ale
            // nesmíme — uživatel by viděl zamrzlou obrazovku bez vysvětlení.
            else -> _authStage.value =
                AuthStage.Failed("Tahle fáze přihlášení zatím není podporovaná: ${state::class.simpleName}")
        }
    }

    private suspend fun sendTdlibParameters() {
        if (BuildConfiguration.TELEGRAM_API_ID == 0 || BuildConfiguration.TELEGRAM_API_HASH.isBlank()) {
            _authStage.value = AuthStage.Failed(
                "Chybí api_id/api_hash. Vygeneruj si je na my.telegram.org a vlož do local.properties " +
                    "jako telegramApiId a telegramApiHash.",
            )
            return
        }

        // Pojmenované argumenty schválně: upstream do těchhle parametrů přidává a přejmenovává
        // i v patch verzích, poziční volání by se tiše rozsypalo.
        client.setTdlibParameters(
            useTestDc = false,
            databaseDirectory = paths.databaseDirectory,
            filesDirectory = paths.filesDirectory,
            // Prázdný klíč = databáze bez dodatečného šifrování. Pro spike v pořádku; před ostrým
            // nasazením sem patří klíč z Keystore / Keychain.
            databaseEncryptionKey = ByteArray(0),
            useFileDatabase = true,
            useChatInfoDatabase = true,
            useMessageDatabase = true,
            useSecretChats = false,
            apiId = BuildConfiguration.TELEGRAM_API_ID,
            apiHash = BuildConfiguration.TELEGRAM_API_HASH,
            systemLanguageCode = "cs",
            deviceModel = platformDeviceModel,
            systemVersion = platformSystemVersion,
            applicationVersion = "0.1.0",
        )
            .toResult()
            .onFailure {
                logger.e(it) { "setTdlibParameters selhalo." }
                _authStage.value = AuthStage.Failed(it.message ?: "setTdlibParameters selhalo.")
            }
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
}
