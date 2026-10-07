package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.EmailAddressAuthenticationCodeInfo
import dev.g000sha256.tdl.dto.PasswordState
import dev.g000sha256.tdl.dto.ResetPasswordResultDeclined
import dev.g000sha256.tdl.dto.ResetPasswordResultOk
import dev.g000sha256.tdl.dto.ResetPasswordResultPending
import dev.g000sha256.tdl.toResult

/**
 * [PasswordRepository] nad TDLib.
 *
 * **Logují se jen chyby, nikdy argumenty.** Hláška z TDLib je konstanta od serveru nebo věta
 * TDLib a heslo ani e-mail nenese; `PasswordState.toString()` by nesl nápovědu, proto se stav
 * nevypisuje vůbec.
 */
class TdlPasswordRepository(private val holder: TelegramClientHolder) : PasswordRepository {

    private val client: TdlClient get() = holder.client

    private val logger = Logger.withTag("TwoStep")

    override suspend fun status(): Result<TwoStepStatus> =
        client.getPasswordState().toResult().map { it.toStatus() }.onFailure { logger.w(it) { "Stav hesla nejde zjistit" } }

    override suspend fun recoveryEmail(password: String): Result<String> =
        client.getRecoveryEmailAddress(password = password).toResult()
            .map { it.recoveryEmailAddress }
            .onFailure { logger.w(it) { "Heslo se nepodařilo ověřit" } }

    override suspend fun setPassword(
        oldPassword: String,
        newPassword: String,
        hint: String,
        newEmail: String?,
    ): Result<TwoStepStatus> = client.setPassword(
        oldPassword = oldPassword,
        newPassword = newPassword,
        newHint = hint,
        setRecoveryEmailAddress = newEmail != null,
        newRecoveryEmailAddress = newEmail.orEmpty(),
    ).toResult().map { it.toStatus() }.onFailure { logger.w(it) { "Heslo se nepodařilo nastavit" } }

    override suspend fun setRecoveryEmail(password: String, email: String): Result<TwoStepStatus> =
        client.setRecoveryEmailAddress(password = password, newRecoveryEmailAddress = email).toResult()
            .map { it.toStatus() }
            .onFailure { logger.w(it) { "Obnovovací e-mail se nepodařilo nastavit" } }

    override suspend fun checkEmailCode(code: String): Result<TwoStepStatus> =
        client.checkRecoveryEmailAddressCode(code = code).toResult().map { it.toStatus() }
            .onFailure { logger.w(it) { "Kód z e-mailu neprošel" } }

    override suspend fun resendEmailCode(): Result<TwoStepStatus> =
        client.resendRecoveryEmailAddressCode().toResult().map { it.toStatus() }
            .onFailure { logger.w(it) { "Kód se nepodařilo poslat znovu" } }

    override suspend fun cancelEmailVerification(): Result<TwoStepStatus> =
        client.cancelRecoveryEmailAddressVerification().toResult().map { it.toStatus() }
            .onFailure { logger.w(it) { "Ověření e-mailu se nepodařilo zrušit" } }

    override suspend fun requestRecovery(): Result<EmailCodeInfo> =
        client.requestPasswordRecovery().toResult().map { it.toInfo() }
            .onFailure { logger.w(it) { "Kód k obnově hesla se nepodařilo poslat" } }

    override suspend fun checkRecoveryCode(code: String): Result<Unit> =
        client.checkPasswordRecoveryCode(recoveryCode = code).toResult().map { }
            .onFailure { logger.w(it) { "Kód k obnově hesla neprošel" } }

    override suspend fun recover(code: String, newPassword: String, hint: String): Result<TwoStepStatus> =
        client.recoverPassword(recoveryCode = code, newPassword = newPassword, newHint = hint).toResult()
            .map { it.toStatus() }
            .onFailure { logger.w(it) { "Heslo se nepodařilo obnovit" } }

    override suspend fun reset(): Result<PasswordResetResult> =
        client.resetPassword().toResult().map { result ->
            when (result) {
                is ResetPasswordResultOk -> PasswordResetResult.Done
                is ResetPasswordResultPending -> PasswordResetResult.Pending(result.pendingResetDate.toLong())
                is ResetPasswordResultDeclined -> PasswordResetResult.Declined(result.retryDate.toLong())
            }
        }.onFailure { logger.w(it) { "Heslo se nepodařilo resetovat" } }

    override suspend fun cancelReset(): Result<Unit> =
        client.cancelPasswordReset().toResult().map { }.onFailure { logger.w(it) { "Reset hesla se nepodařilo zrušit" } }

}

private fun PasswordState.toStatus() = TwoStepStatus(
    hasPassword = hasPassword,
    hint = passwordHint,
    hasRecoveryEmail = hasRecoveryEmailAddress,
    hasPassportData = hasPassportData,
    pendingEmail = recoveryEmailAddressCodeInfo?.toInfo(),
    resetDate = pendingResetDate.toLong(),
)

private fun EmailAddressAuthenticationCodeInfo.toInfo() = EmailCodeInfo(pattern = emailAddressPattern, length = length)
