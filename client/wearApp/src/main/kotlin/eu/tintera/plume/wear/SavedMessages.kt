package eu.tintera.plume.wear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.saved_messages
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Id chatu Uložené zprávy (= id přihlášeného účtu), `null` dokud ho TDLib neohlásil.
 *
 * Uložené zprávy nesou v TDLib jméno a fotku vlastního účtu; všichni klienti je ukazují jako
 * „Uložené zprávy" se záložkou (telefon: `chatTitle` / `isSavedMessages` ve `shared`). Bez toho
 * byly na hodinkách jako chat se sebou samým — se jménem a fotkou uživatele.
 */
@Composable
fun savedMessagesChatId(): Long? {
    val id by koinInject<ChatRepository>().savedMessagesChatId.collectAsState()
    return id
}

/** Název chatu, jak ho má vidět uživatel: Uložené zprávy svým jménem, ne jménem účtu. */
@Composable
fun chatTitle(chatId: Long, title: String, savedMessagesId: Long?): String =
    if (chatId == savedMessagesId) stringResource(Res.string.saved_messages) else title
