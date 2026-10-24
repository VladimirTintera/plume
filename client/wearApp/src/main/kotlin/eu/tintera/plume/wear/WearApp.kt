package eu.tintera.plume.wear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.connection_connecting
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Rozcestník podle stavu přihlášení: na hodinkách jen QR kód (a heslo), pak seznam chatů. */
@Composable
fun WearApp() {
    val session = koinInject<TelegramSession>()
    val stage by session.authStage.collectAsState()
    val scope = rememberCoroutineScope()

    MaterialTheme {
        AppScaffold {
            when (val current = stage) {
                AuthStage.Ready -> ChatListScreen()
                is AuthStage.WaitQrConfirmation -> QrLoginScreen(link = current.link)
                is AuthStage.WaitPassword -> PasswordScreen(stage = current)
                // Telefonní číslo se na hodinkách nezadává: rovnou QR kód.
                AuthStage.WaitPhoneNumber -> {
                    LaunchedEffect(Unit) { session.startQrLogin() }
                    LoadingScreen(stringResource(Res.string.connection_connecting))
                }
                is AuthStage.Failed -> MessageScreen(current.message.resolve())
                AuthStage.Connecting, AuthStage.LoggingOut -> LoadingScreen(stringResource(Res.string.connection_connecting))
                // Kód, e-mail a registrace: na hodinkách nepsat, vrátit se k QR kódu.
                else -> QrOnlyScreen(onShowQr = { scope.launch { session.restartLogin() } })
            }
        }
    }
}
