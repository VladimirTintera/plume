package eu.tintera.plume.core.telegram

import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.error_about_too_long
import eu.tintera.plume.resources.error_admin_required
import eu.tintera.plume.resources.error_code_resend_unavailable
import eu.tintera.plume.resources.error_email_not_allowed
import eu.tintera.plume.resources.error_email_reset_pending
import eu.tintera.plume.resources.error_first_name_invalid
import eu.tintera.plume.resources.error_username_invalid
import eu.tintera.plume.resources.error_username_occupied
import eu.tintera.plume.resources.error_flood_wait_minutes
import eu.tintera.plume.resources.error_invite_invalid
import eu.tintera.plume.resources.error_flood_wait_seconds
import eu.tintera.plume.resources.error_fresh_session
import eu.tintera.plume.resources.error_qr_login_expired
import eu.tintera.plume.resources.error_message_cannot_be_deleted
import eu.tintera.plume.resources.error_message_cannot_be_deleted_for_everyone
import eu.tintera.plume.resources.error_member_admin_forbidden
import eu.tintera.plume.resources.error_message_cannot_be_edited
import eu.tintera.plume.resources.error_message_cannot_be_pinned
import eu.tintera.plume.resources.error_no_write_access
import eu.tintera.plume.resources.error_not_enough_rights
import eu.tintera.plume.resources.error_password_invalid
import eu.tintera.plume.resources.error_phone_code_expired
import eu.tintera.plume.resources.error_phone_code_invalid
import eu.tintera.plume.resources.error_phone_number_invalid
import eu.tintera.plume.resources.error_pin_limit
import eu.tintera.plume.resources.error_recovery_code_expired
import eu.tintera.plume.resources.error_recovery_code_invalid
import eu.tintera.plume.resources.error_recovery_unavailable
import eu.tintera.plume.resources.settings_two_step_error_email_invalid
import eu.tintera.plume.resources.error_summary_premium_limit
import eu.tintera.plume.resources.error_transfer_needs_password
import eu.tintera.plume.resources.error_transfer_too_fresh
import eu.tintera.plume.resources.error_you_blocked_user
import eu.tintera.plume.resources.error_resend_too_early
import eu.tintera.plume.resources.error_slowmode_wait_minutes
import eu.tintera.plume.resources.error_slowmode_wait_seconds
import eu.tintera.plume.resources.error_verification_failed
import org.jetbrains.compose.resources.StringResource

/**
 * Text chyby pro uživatele.
 *
 * **Hláška z TDLib se uživateli NIKDY nevypisuje.** Chodí ve dvou podobách a ani jedna není věta
 * pro člověka: buď konstanta od serveru (`PHONE_CODE_INVALID`), nebo anglická věta pro vývojáře
 * od samotné TDLib („Have no write access to the chat"). Navíc by tím text z TDLib prosakoval
 * přes hranici `core/telegram` až na obrazovku, takže by změna znění v novější verzi měnila,
 * co čte uživatel — a nešel by přeložit.
 *
 * Dřív to tak bylo: na dvaceti místech stálo `failure.message ?: "Česká věta"`. To vypadá jako
 * pojistka, ale byl to **mrtvý kód** — výjimka z TDLib má `message` vždycky vyplněné, takže elvis
 * nikdy nesepnul a uživatel viděl vždycky tu anglickou hlášku. Pořadí je proto obrácené: základ
 * je věta od nás ([fallback]) a hláška z TDLib se použije jen tam, kde z ní umíme vyrobit něco
 * konkrétnějšího.
 *
 * Vrací se [UiText], ne řetězec — přeložit se to musí až v UI, viz tam.
 *
 * Surová hláška patří do logu, ne na obrazovku; volající ji tam loguje i s výjimkou, takže skončí
 * i v kontextu hlášení pádů. **Tabulka níž se tím dá rozšiřovat podle toho, co se v logu opravdu
 * objeví**; hádat dopředu nemá cenu, protože o tom, jestli chybu vyrobí server nebo TDLib sama,
 * rozhoduje pokaždé konkrétní volání.
 *
 * Porovnává se text, ne typ výjimky: tutéž hlášku někde zabalíme do vlastní výjimky (repository
 * vrací `Result`, ne typ z TDLib) a naše vlastní chyby se sem trefit nemají — ty spadnou na
 * [fallback].
 */
fun Throwable.userMessage(fallback: StringResource): UiText = errorText(message, fallback)

/**
 * Totéž jako [userMessage] pro hlášku, která nepřišla jako výjimka — chyba, se kterou zpráva
 * neodešla, je součástí zprávy (`MessageSendingStateFailed.error`).
 */
internal fun errorText(raw: String?, fallback: StringResource): UiText {
    raw ?: return UiText(fallback)

    floodWaitSeconds(raw)?.let { seconds ->
        return if (seconds < 60) {
            UiText(Res.string.error_flood_wait_seconds, seconds)
        } else {
            UiText(Res.string.error_flood_wait_minutes, (seconds + 59) / 60)
        }
    }

    // Pomalý režim ve skupině: další zprávu pustí až po uplynutí doby (dokumentace
    // `messages.sendMessage`). Počet sekund je za podtržítkem jako u `FLOOD_WAIT_`.
    raw.takeIf { it.startsWith(SLOWMODE_WAIT_PREFIX) }?.removePrefix(SLOWMODE_WAIT_PREFIX)?.toIntOrNull()?.let { seconds ->
        return if (seconds < 60) {
            UiText(Res.string.error_slowmode_wait_seconds, seconds)
        } else {
            UiText(Res.string.error_slowmode_wait_minutes, (seconds + 59) / 60)
        }
    }

    // Čerstvé heslo nebo relace u převodu vlastnictví; za podtržítkem je počet sekund, ale
    // uživateli stačí, že to zatím nejde — přesnou chvíli stejně určuje server.
    if (raw.startsWith(PASSWORD_TOO_FRESH_PREFIX) || raw.startsWith(SESSION_TOO_FRESH_PREFIX)) {
        return UiText(Res.string.error_transfer_too_fresh)
    }

    val mapped = when (raw) {
        // Věty od samotné TDLib (ověřené v `libtdjsonjava.so`). Vznikají z její vlastní kontroly
        // ještě před dotazem na server, takže na ně dojde dřív než na konstantu od serveru.
        "The maximum number of pinned chats exceeded" -> Res.string.error_pin_limit
        "Message can't be pinned" -> Res.string.error_message_cannot_be_pinned
        "Message can't be edited" -> Res.string.error_message_cannot_be_edited
        "Message can't be deleted" -> Res.string.error_message_cannot_be_deleted
        "Message can't be deleted for everyone" -> Res.string.error_message_cannot_be_deleted_for_everyone
        "Have no write access to the chat" -> Res.string.error_no_write_access
        "Have not enough rights" -> Res.string.error_not_enough_rights
        // Správu členů smí správce jen v rozsahu vlastních práv; cizího správce vůbec.
        "Can't edit chat administrators" -> Res.string.error_member_admin_forbidden
        "Invalid recovery code" -> Res.string.error_recovery_code_invalid
        // `resendAuthenticationCode` bez další cesty (`nextType` je null) — TDLib to odmítne sama.
        "Authentication code can't be resend" -> Res.string.error_code_resend_unavailable
        // `resendMessages` před uplynutím `retryAfter` (`MessagesManager::resend_messages`).
        "Message can't be re-sent yet" -> Res.string.error_resend_too_early
        // Ověření aplikace, které Plume odmítlo, protože ho udělat neumí (`declineVerification`
        // v `TdlTelegramSession`). Konstantu si TDLib píše sama (`NetQueryVerifier`).
        "VERIFICATION_FAILED" -> Res.string.error_verification_failed

        // Konstanty od serveru. TDLib je nepřekládá, posílá je dál, jak přišly.
        "PINNED_DIALOGS_TOO_MUCH" -> Res.string.error_pin_limit
        "CHAT_ADMIN_REQUIRED" -> Res.string.error_admin_required
        "PHONE_NUMBER_INVALID" -> Res.string.error_phone_number_invalid
        // Zpráva člověku, kterého má uživatel zablokovaného (dokumentace `messages.sendMessage`).
        "YOU_BLOCKED_USER" -> Res.string.error_you_blocked_user
        "CHAT_WRITE_FORBIDDEN" -> Res.string.error_no_write_access
        "PHONE_CODE_INVALID" -> Res.string.error_phone_code_invalid
        "PHONE_CODE_EXPIRED" -> Res.string.error_phone_code_expired
        // `auth.resendCode`, když server další cestu doručení nemá (dokumentace metody v API).
        "SEND_CODE_UNAVAILABLE" -> Res.string.error_code_resend_unavailable
        "PASSWORD_HASH_INVALID" -> Res.string.error_password_invalid
        // Kód z e-mailu — obnova hesla i přihlašovací e-mail (`account.verifyEmail`). `CODE_INVALID`
        // je obecná konstanta, ale jiný kód než ten z e-mailu se v přihlášení touhle cestou
        // neposílá (SMS kód má `PHONE_CODE_INVALID`).
        "CODE_INVALID" -> Res.string.error_recovery_code_invalid
        "PASSWORD_RECOVERY_EXPIRED", "EMAIL_VERIFY_EXPIRED" -> Res.string.error_recovery_code_expired
        // Přihlašovací e-mail (`account.sendVerifyEmailCode`, `auth.resetLoginEmail`; dokumentace metod).
        "EMAIL_NOT_ALLOWED" -> Res.string.error_email_not_allowed
        "TASK_ALREADY_EXISTS" -> Res.string.error_email_reset_pending
        "PASSWORD_RECOVERY_NA" -> Res.string.error_recovery_unavailable
        // Adresa, kterou server nevezme a hrubé síto propustilo (dvoufázové ověření, přihlašovací e-mail).
        "EMAIL_INVALID" -> Res.string.settings_two_step_error_email_invalid
        // Pozvánka do chatu (`checkChatInviteLink`): neplatný odkaz hlásí server jako vypršelý.
        "INVITE_HASH_EXPIRED", "INVITE_HASH_INVALID" -> Res.string.error_invite_invalid
        // Zařízení přihlášené teprve nedávno (asi 24 h) nesmí odhlašovat ostatní — ochrana
        // proti tomu, kdo účet právě ukradl. Bez vlastní věty by tlačítko vypadalo rozbitě.
        "FRESH_RESET_AUTHORISATION_FORBIDDEN" -> Res.string.error_fresh_session
        // QR kód k přihlášení jiného zařízení platí jen pár desítek vteřin a jen jednou.
        "AUTH_TOKEN_EXPIRED", "AUTH_TOKEN_INVALID", "AUTH_TOKEN_ALREADY_ACCEPTED" ->
            Res.string.error_qr_login_expired

        // Úprava profilu. Dostupnost jména se ověřuje předem, tohle je souboj o jméno mezi
        // kontrolou a uložením, nebo pravidlo, které místní kontrola nezná.
        "USERNAME_OCCUPIED" -> Res.string.error_username_occupied
        "USERNAME_INVALID" -> Res.string.error_username_invalid
        "FIRSTNAME_INVALID" -> Res.string.error_first_name_invalid
        "ABOUT_TOO_LONG" -> Res.string.error_about_too_long

        // Převod vlastnictví. Obojí se ptáme dopředu (`canTransferOwnership`), tohle je pro
        // případ, že se stav účtu mezi dotazem a potvrzením změní.
        "PASSWORD_MISSING", "2-step verification is disabled" -> Res.string.error_transfer_needs_password

        // Shrnutí od serveru (`summarizeMessage`): bez Premium jich server pustí jen omezeně.
        // Ověřeno na účtu bez Premium — přišlo to hned u prvního pokusu.
        "SUMMARY_FLOOD_PREMIUM" -> Res.string.error_summary_premium_limit

        else -> fallback
    }
    return UiText(mapped)
}

/**
 * Počet sekund z hlášky o překročení limitu. Server posílá `FLOOD_WAIT_23`, vlastní omezovač
 * TDLib `Too Many Requests: retry after 23` (obojí ověřeno v knihovně) — pro uživatele je to totéž.
 */
private fun floodWaitSeconds(raw: String): Int? = when {
    raw.startsWith(FLOOD_WAIT_PREFIX) -> raw.removePrefix(FLOOD_WAIT_PREFIX).toIntOrNull()
    raw.startsWith(TOO_MANY_REQUESTS_PREFIX) -> raw.removePrefix(TOO_MANY_REQUESTS_PREFIX).trim().toIntOrNull()
    else -> null
}

private const val FLOOD_WAIT_PREFIX = "FLOOD_WAIT_"
private const val SLOWMODE_WAIT_PREFIX = "SLOWMODE_WAIT_"
private const val TOO_MANY_REQUESTS_PREFIX = "Too Many Requests: retry after "
private const val PASSWORD_TOO_FRESH_PREFIX = "PASSWORD_TOO_FRESH_"
private const val SESSION_TOO_FRESH_PREFIX = "SESSION_TOO_FRESH_"
