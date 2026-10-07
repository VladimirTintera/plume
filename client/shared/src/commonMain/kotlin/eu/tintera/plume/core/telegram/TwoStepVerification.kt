package eu.tintera.plume.core.telegram

/**
 * Stav dvoufázového ověření (cloud hesla Telegramu) — doménová podoba `PasswordState`.
 *
 * **Heslo samo tu není a nikdy nebude.** Stav nese jen to, co server o hesle prozradí, a to se
 * smí ukázat i zalogovat; heslo jde z pole rovnou do volání [PasswordRepository].
 *
 * @property hint nápověda k heslu, může být prázdná.
 * @property hasRecoveryEmail k heslu je potvrzený obnovovací e-mail.
 * @property hasPassportData účet má uložené údaje Telegram Passport — vypnutí hesla je smaže.
 * @property pendingEmail na e-mail odešel kód a změna čeká na jeho potvrzení. **Nové heslo
 *   s e-mailem začne platit až po potvrzení** (`setPassword`: „the change will not be applied
 *   until the new recovery email address is confirmed"), do té doby je [hasPassword] `false`.
 * @property resetDate kdy jde heslo bez e-mailu zrušit ([PasswordRepository.reset]),
 *   v sekundách epochy; `0` = žádný reset neběží.
 */
data class TwoStepStatus(
    val hasPassword: Boolean,
    val hint: String,
    val hasRecoveryEmail: Boolean,
    val hasPassportData: Boolean,
    val pendingEmail: EmailCodeInfo?,
    val resetDate: Long,
)

/** Kam odešel kód: maska adresy (`a***@g***.com`) a délka kódu (0 = neznámá). */
data class EmailCodeInfo(val pattern: String, val length: Int)

/** Výsledek zrušení hesla bez e-mailu (`resetPassword`). */
sealed interface PasswordResetResult {
    /** Heslo je zrušené hned. */
    data object Done : PasswordResetResult

    /** Heslo se zruší až v [at] (sekundy epochy); do té doby jde reset zrušit. */
    data class Pending(val at: Long) : PasswordResetResult

    /** Server reset odmítl (nedávno zrušený reset); znovu to jde zkusit od [retryAt]. */
    data class Declined(val retryAt: Long) : PasswordResetResult
}

/**
 * Jak to vypadá s čekajícím resetem hesla. Čistá funkce kvůli testu: hranice „teď" je to jediné,
 * co se na tom dá splést.
 */
sealed interface ResetProgress {
    data object None : ResetProgress

    /** Reset běží a heslo se zruší v [at]; do té doby jde reset zrušit. */
    data class Waiting(val at: Long) : ResetProgress

    /** Čekání uplynulo: `resetPassword` teď heslo zruší hned. */
    data object Ready : ResetProgress
}

fun resetProgress(resetDate: Long, nowSeconds: Long): ResetProgress = when {
    resetDate <= 0 -> ResetProgress.None
    resetDate > nowSeconds -> ResetProgress.Waiting(resetDate)
    else -> ResetProgress.Ready
}

/**
 * Hranice kolem dvoufázového ověření. Všechna volání jdou na server.
 *
 * **Heslo se nikde neloguje** — ani tady, ani ve volajících. Do logu smí jen hláška chyby
 * z TDLib (konstanta jako `PASSWORD_HASH_INVALID`), ta heslo nenese.
 */
interface PasswordRepository {

    suspend fun status(): Result<TwoStepStatus>

    /**
     * Ověří současné heslo a vrátí obnovovací e-mail (prázdný = není). Je to jediné volání, které
     * heslo ověří bez změny — špatné heslo skončí `PASSWORD_HASH_INVALID`.
     */
    suspend fun recoveryEmail(password: String): Result<String>

    /**
     * Zapnutí (prázdné [oldPassword]), změna, nebo vypnutí hesla (prázdné [newPassword]).
     * [newEmail] `null` = e-mail neměnit; prázdný = bez e-mailu.
     */
    suspend fun setPassword(
        oldPassword: String,
        newPassword: String,
        hint: String,
        newEmail: String?,
    ): Result<TwoStepStatus>

    /** Změna obnovovacího e-mailu; čeká na kód z nové adresy ([TwoStepStatus.pendingEmail]). */
    suspend fun setRecoveryEmail(password: String, email: String): Result<TwoStepStatus>

    suspend fun checkEmailCode(code: String): Result<TwoStepStatus>

    suspend fun resendEmailCode(): Result<TwoStepStatus>

    /** Zahodí čekající změnu e-mailu — a s ní i nové heslo, které na potvrzení čekalo. */
    suspend fun cancelEmailVerification(): Result<TwoStepStatus>

    /** Pošle kód na obnovovací e-mail (zapomenuté heslo). */
    suspend fun requestRecovery(): Result<EmailCodeInfo>

    suspend fun checkRecoveryCode(code: String): Result<Unit>

    /** Nové heslo kódem z e-mailu, bez znalosti toho starého. */
    suspend fun recover(code: String, newPassword: String, hint: String): Result<TwoStepStatus>

    /** Zrušení hesla bez e-mailu i bez znalosti hesla — s čekáním, viz [PasswordResetResult]. */
    suspend fun reset(): Result<PasswordResetResult>

    suspend fun cancelReset(): Result<Unit>
}
