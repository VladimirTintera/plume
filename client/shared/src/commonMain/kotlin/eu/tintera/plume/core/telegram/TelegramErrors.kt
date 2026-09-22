package eu.tintera.plume.core.telegram

import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.error_about_too_long
import eu.tintera.plume.resources.error_admin_required
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
import eu.tintera.plume.resources.error_message_cannot_be_edited
import eu.tintera.plume.resources.error_message_cannot_be_pinned
import eu.tintera.plume.resources.error_no_write_access
import eu.tintera.plume.resources.error_not_enough_rights
import eu.tintera.plume.resources.error_password_invalid
import eu.tintera.plume.resources.error_phone_code_expired
import eu.tintera.plume.resources.error_phone_code_invalid
import eu.tintera.plume.resources.error_phone_number_invalid
import eu.tintera.plume.resources.error_pin_limit
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
fun Throwable.userMessage(fallback: StringResource): UiText {
    val raw = message ?: return UiText(fallback)

    floodWaitSeconds(raw)?.let { seconds ->
        return if (seconds < 60) {
            UiText(Res.string.error_flood_wait_seconds, seconds)
        } else {
            UiText(Res.string.error_flood_wait_minutes, (seconds + 59) / 60)
        }
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

        // Konstanty od serveru. TDLib je nepřekládá, posílá je dál, jak přišly.
        "PINNED_DIALOGS_TOO_MUCH" -> Res.string.error_pin_limit
        "CHAT_ADMIN_REQUIRED" -> Res.string.error_admin_required
        "PHONE_NUMBER_INVALID" -> Res.string.error_phone_number_invalid
        "PHONE_CODE_INVALID" -> Res.string.error_phone_code_invalid
        "PHONE_CODE_EXPIRED" -> Res.string.error_phone_code_expired
        "PASSWORD_HASH_INVALID" -> Res.string.error_password_invalid
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
private const val TOO_MANY_REQUESTS_PREFIX = "Too Many Requests: retry after "
