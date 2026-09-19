package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.saved_messages
import org.jetbrains.compose.resources.stringResource

/**
 * Id chatu Uložené zprávy (= id přihlášeného účtu), poskytuje ho shell. `null` mimo přihlášenou
 * část.
 *
 * Záměrně se NEPOUŽÍVÁ uvnitř [Avatar] k samočinnému rozpoznání: `Avatar` kreslí i odesílatele
 * (reakce, zhlédnutí, profilovky ve skupině) a tam má být vlastní tvář, ne záložka. Kdo kreslí
 * CHAT, zeptá se [isSavedMessages] a předá to výslovně.
 */
val LocalSavedMessagesChatId = staticCompositionLocalOf<Long?> { null }

@Composable
fun isSavedMessages(chatId: Long): Boolean = chatId == LocalSavedMessagesChatId.current

/**
 * Název chatu, jak ho má vidět uživatel. Uložené zprávy nesou v TDLib jméno vlastního účtu;
 * všichni klienti je ale ukazují jako „Uložené zprávy" — jméno uživatele by mátlo.
 */
@Composable
fun chatTitle(chatId: Long, title: String): String =
    if (isSavedMessages(chatId)) stringResource(Res.string.saved_messages) else title
