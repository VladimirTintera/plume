package eu.tintera.plume.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import eu.tintera.plume.core.accounts.Accounts
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.connection_connecting
import eu.tintera.plume.resources.error_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.KoinIsolatedContext
import org.koin.compose.koinInject

/**
 * Televize má jeden účet: `koinInject` míří do kontejneru primárního účtu (`Accounts`), jako hodinky.
 * Účet se čte jako stav, ne `.value`: kdyby se výchozí účet vyměnil (nový klient po odhlášení),
 * obsah se složí znovu nad novým kontejnerem, ne nad starým (tentýž `key(slot)` jako telefon).
 */
@Composable
fun PrimaryAccountScope(content: @Composable () -> Unit) {
    val account by koinInject<Accounts>().default.collectAsState()
    key(account.slot) {
        KoinIsolatedContext(account.application, content)
    }
}

/**
 * Rozcestník podle stavu přihlášení: na televizi jen QR kód (a heslo dvoufázového ověření),
 * pak seznam chatů. Telefonní číslo a kód se ovladačem nepíšou. [openChat] je chat k otevření
 * zvenčí; otevře se až po přihlášení.
 */
@Composable
fun TvApp(openChat: Long? = null, onChatOpened: () -> Unit = {}) {
    val session = koinInject<TelegramSession>()
    val stage by session.authStage.collectAsState()
    val scope = rememberCoroutineScope()

    TvTheme {
        when (val current = stage) {
            AuthStage.Ready -> TvNavigation(openChat = openChat, onChatOpened = onChatOpened)
            is AuthStage.WaitQrConfirmation -> QrLoginScreen(link = current.link)
            is AuthStage.WaitPassword -> PasswordScreen(stage = current)
            AuthStage.WaitPhoneNumber -> {
                LaunchedEffect(Unit) { session.startQrLogin() }
                LoadingScreen(stringResource(Res.string.connection_connecting))
            }
            is AuthStage.Failed -> MessageScreen(current.message.resolve(), Res.drawable.error_24dp, error = true)
            AuthStage.Connecting, AuthStage.LoggingOut -> LoadingScreen(stringResource(Res.string.connection_connecting))
            // Kód, e-mail a registrace: na televizi nepsat, vrátit se k QR kódu.
            else -> QrOnlyScreen(onShowQr = { scope.launch { session.restartLogin() } })
        }
    }
}

/**
 * Téma a plocha každé obrazovky. Televize je vždy tmavá (doporučení Google pro TV: světlá
 * plocha v tmavém obýváku oslňuje) a barvy z tapety nemá; paleta je z barvy Plume jako na
 * telefonu ([TvColors]). Písma jsou tatáž jako na telefonu ([tvTypography]).
 */
@Composable
fun TvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TvColors.scheme, typography = tvTypography()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}
