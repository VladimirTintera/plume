package eu.tintera.plume.features.settings

/**
 * Co uživatel s dvoufázovým ověřením dělá. Každá akce je průvodce z několika kroků
 * ([TwoStepStep]); pořadí kroků a to, kde se volá server, jsou čisté funkce níž — testy
 * `TwoStepFlowTest`.
 */
enum class TwoStepAction {
    /** Zapnutí: nové heslo, nápověda, e-mail, a když se e-mail zadal, kód z něj. */
    Enable,

    /** Změna hesla: ověřit současné, pak nové a nápověda. */
    ChangePassword,

    /** Nový obnovovací e-mail: ověřit heslo, adresa, kód z ní. */
    ChangeEmail,

    /** Vypnutí: ověřit heslo, pak potvrzení s následkem. */
    Disable,

    /** Zapomenuté heslo: kód z obnovovacího e-mailu, nové heslo a nápověda. */
    Recover,

    /** Potvrzení e-mailu, na které se čeká (kód přišel dřív, třeba po návratu do aplikace). */
    ConfirmEmail,
}

enum class TwoStepStep { CurrentPassword, RecoveryCode, NewPassword, Hint, Email, EmailCode }

/** Kroky průvodce v pořadí. Kód z e-mailu je poslední a dojde se na něj, jen když ho server chce. */
fun TwoStepAction.steps(): List<TwoStepStep> = when (this) {
    TwoStepAction.Enable -> listOf(TwoStepStep.NewPassword, TwoStepStep.Hint, TwoStepStep.Email, TwoStepStep.EmailCode)
    TwoStepAction.ChangePassword -> listOf(TwoStepStep.CurrentPassword, TwoStepStep.NewPassword, TwoStepStep.Hint)
    TwoStepAction.ChangeEmail -> listOf(TwoStepStep.CurrentPassword, TwoStepStep.Email, TwoStepStep.EmailCode)
    TwoStepAction.Disable -> listOf(TwoStepStep.CurrentPassword)
    TwoStepAction.Recover -> listOf(TwoStepStep.RecoveryCode, TwoStepStep.NewPassword, TwoStepStep.Hint)
    TwoStepAction.ConfirmEmail -> listOf(TwoStepStep.EmailCode)
}

/** Další krok, `null` = tenhle byl poslední. */
fun TwoStepAction.next(step: TwoStepStep): TwoStepStep? = steps().let { it.getOrNull(it.indexOf(step) + 1) }

/**
 * Krok, na který vede zpět, `null` = zpět opouští průvodce.
 *
 * **Z kódu z e-mailu se zpět nevrací do e-mailu**: změna na serveru už čeká na potvrzení
 * a opakované odeslání adresy by poslalo další kód. Zpět průvodce zavře a čekající potvrzení
 * zůstane vidět na obrazovce stavu (s kódem, „Poslat znovu" a „Zrušit").
 */
fun TwoStepAction.previous(step: TwoStepStep): TwoStepStep? =
    if (step == TwoStepStep.EmailCode) null else steps().let { it.getOrNull(it.indexOf(step) - 1) }

/**
 * Na kterém kroku se volá server, který něco ZMĚNÍ. Ostatní kroky jen sbírají údaje (nebo
 * ověřují — současné heslo, kód z e-mailu k obnově) a změna odejde až tady, naráz.
 */
fun TwoStepAction.commitsAt(step: TwoStepStep): Boolean = when (this) {
    TwoStepAction.Enable -> step == TwoStepStep.Email
    TwoStepAction.ChangePassword, TwoStepAction.Recover -> step == TwoStepStep.Hint
    TwoStepAction.ChangeEmail -> step == TwoStepStep.Email
    TwoStepAction.Disable -> step == TwoStepStep.CurrentPassword
    TwoStepAction.ConfirmEmail -> false
}

/** Proč nové heslo nejde použít. */
enum class NewPasswordProblem { Empty, Mismatch, TooLong }

/**
 * Kontrola nového hesla ještě před serverem.
 *
 * **Heslo se neořezává** — mezera na konci je jeho součást; s ořezem by šlo nastavit heslo, které
 * jinde nejde zadat (tatáž úvaha jako při přihlášení). Délku omezuje TDLib („Password is too
 * long", ověřeno v knihovně); mez neříká, takže strop je velkorysý, jen aby vložený román
 * neodešel na server.
 */
fun newPasswordProblem(password: String, repeat: String): NewPasswordProblem? = when {
    password.isEmpty() -> NewPasswordProblem.Empty
    password.length > MAX_PASSWORD_LENGTH -> NewPasswordProblem.TooLong
    password != repeat -> NewPasswordProblem.Mismatch
    else -> null
}

/** Nápověda shodná s heslem by heslo prozradila; server ji odmítne obecnou chybou. */
fun hintRevealsPassword(hint: String, password: String): Boolean = hint.isNotBlank() && hint.trim() == password.trim()

/**
 * Vypadá to jako e-mailová adresa? Jen hrubé síto proti překlepu (chybí zavináč nebo tečka
 * v doméně); skutečnou kontrolu dělá server (`EMAIL_INVALID`) a hlavně kód, který na adresu
 * přijde.
 */
fun isPlausibleEmail(email: String): Boolean {
    val trimmed = email.trim()
    val at = trimmed.indexOf('@')
    if (at <= 0 || at != trimmed.lastIndexOf('@') || trimmed.any { it.isWhitespace() }) return false
    val domain = trimmed.substring(at + 1)
    val dot = domain.lastIndexOf('.')
    return dot > 0 && dot < domain.length - 1
}

internal const val MAX_PASSWORD_LENGTH = 256
