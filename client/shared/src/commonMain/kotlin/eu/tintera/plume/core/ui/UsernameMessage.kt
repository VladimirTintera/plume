package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import eu.tintera.plume.core.telegram.UsernameCheck
import eu.tintera.plume.core.telegram.UsernameFormatError
import eu.tintera.plume.core.telegram.usernameFormatError
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.edit_profile_username_available
import eu.tintera.plume.resources.edit_profile_username_characters
import eu.tintera.plume.resources.edit_profile_username_checking
import eu.tintera.plume.resources.edit_profile_username_end
import eu.tintera.plume.resources.edit_profile_username_invalid
import eu.tintera.plume.resources.edit_profile_username_occupied
import eu.tintera.plume.resources.edit_profile_username_purchasable
import eu.tintera.plume.resources.edit_profile_username_start
import eu.tintera.plume.resources.edit_profile_username_too_long
import eu.tintera.plume.resources.edit_profile_username_too_many
import eu.tintera.plume.resources.edit_profile_username_too_short
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * Kontrola uživatelského jména, jak ji ukazuje pole — sdílí ji úprava vlastního profilu
 * a veřejný odkaz nové skupiny nebo kanálu. Pravidla jsou tatáž (`usernameFormatError`),
 * liší se jen to, koho se jméno týká.
 */

/** Pauza v psaní, po které se dostupnost jména ptá serveru — dotaz za každý znak je zbytečný. */
const val USERNAME_CHECK_DELAY_MS = 400L

/** Stav kontroly uživatelského jména, jak ho ukazuje pole. */
sealed interface UsernameStatus {
    /** Beze změny, prázdné, nebo se teprve píše. */
    data object Idle : UsernameStatus
    data object Checking : UsernameStatus
    data object Available : UsernameStatus
    data class Unavailable(val check: UsernameCheck) : UsernameStatus
}

/**
 * Věta pod polem uživatelského jména a jestli je to chyba; `null` = nápověda. Nejdřív místní
 * pravidla (hned při psaní), pak odpověď serveru.
 */
@Composable
fun usernameMessage(
    username: String,
    original: String,
    status: UsernameStatus,
    /** U skupiny a kanálu jiná věta — strop je na veřejné chaty, ne na jména účtu. */
    tooManyPublic: StringResource = Res.string.edit_profile_username_too_many,
): Pair<String, Boolean>? {
    if (username == original) return null
    usernameFormatError(username)?.let { error ->
        val text = when (error) {
            UsernameFormatError.Characters -> Res.string.edit_profile_username_characters
            UsernameFormatError.Start -> Res.string.edit_profile_username_start
            UsernameFormatError.TooShort -> Res.string.edit_profile_username_too_short
            UsernameFormatError.TooLong -> Res.string.edit_profile_username_too_long
            UsernameFormatError.End -> Res.string.edit_profile_username_end
        }
        return stringResource(text) to true
    }
    return when (status) {
        UsernameStatus.Idle -> null
        UsernameStatus.Checking -> stringResource(Res.string.edit_profile_username_checking) to false
        UsernameStatus.Available -> stringResource(Res.string.edit_profile_username_available) to false
        is UsernameStatus.Unavailable -> stringResource(
            when (status.check) {
                UsernameCheck.Occupied -> Res.string.edit_profile_username_occupied
                UsernameCheck.Purchasable -> Res.string.edit_profile_username_purchasable
                UsernameCheck.TooManyPublic -> tooManyPublic
                UsernameCheck.Invalid, UsernameCheck.Available -> Res.string.edit_profile_username_invalid
            },
        ) to true
    }
}
