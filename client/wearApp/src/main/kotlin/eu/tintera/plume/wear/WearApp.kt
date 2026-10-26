package eu.tintera.plume.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.runtime.remember
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.dynamicColorScheme
import androidx.wear.compose.material3.MaterialTheme
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.TelegramSession
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.error_24dp
import eu.tintera.plume.resources.connection_connecting
import eu.tintera.plume.resources.wear_login_signed_in
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Rozcestník podle stavu přihlášení: na hodinkách jen QR kód (a heslo), pak seznam chatů.
 * [openChat] je chat k otevření zvenčí (dlaždice); otevře se až po přihlášení.
 */
@Composable
fun WearApp(openChat: Long? = null, onChatOpened: () -> Unit = {}) {
    val session = koinInject<TelegramSession>()
    val stage by session.authStage.collectAsState()
    val scope = rememberCoroutineScope()
    // Fajfka po přihlášení (QR kódem i telefonem): jen po průchodu přihlašováním, ne při startu
    // už přihlášené aplikace. Patří sem, ne do QR obrazovky — ta s `Ready` zmizí i s dialogem.
    var signingIn by remember { mutableStateOf(false) }
    var signedIn by remember { mutableStateOf(false) }
    LaunchedEffect(stage) {
        when (stage) {
            is AuthStage.WaitQrConfirmation, is AuthStage.WaitPassword -> signingIn = true
            AuthStage.Ready -> if (signingIn) {
                signingIn = false
                signedIn = true
            }
            else -> {}
        }
    }

    WearTheme {
        SuccessConfirmation(visible = signedIn, text = stringResource(Res.string.wear_login_signed_in), onDismiss = { signedIn = false })
        when (val current = stage) {
            AuthStage.Ready -> {
                NotificationPermissionRequest()
                WearNavigation(openChat = openChat, onChatOpened = onChatOpened)
            }
            is AuthStage.WaitQrConfirmation -> QrLoginScreen(link = current.link)
            is AuthStage.WaitPassword -> PasswordScreen(stage = current)
            // Telefonní číslo se na hodinkách nezadává: rovnou QR kód.
            AuthStage.WaitPhoneNumber -> {
                LaunchedEffect(Unit) { session.startQrLogin() }
                LoadingScreen(stringResource(Res.string.connection_connecting))
            }
            is AuthStage.Failed -> MessageScreen(current.message.resolve(), Res.drawable.error_24dp, error = true)
            AuthStage.Connecting, AuthStage.LoggingOut -> LoadingScreen(stringResource(Res.string.connection_connecting))
            // Kód, e-mail a registrace: na hodinkách nepsat, vrátit se k QR kódu.
            else -> QrOnlyScreen(onShowQr = { scope.launch { session.restartLogin() } })
        }
    }
}

/**
 * Téma a kostra každé obrazovky hodinek (aplikace i rychlá poznámka z dlaždice). Barvy
 * z ciferníku (Wear OS 6+), jinak výchozí paleta Wear M3. Na hodinkách si barvu volí uživatel
 * ciferníkem, vlastní výběr jako na telefonu (ThemeColor) tu nedává smysl.
 */
@Composable
fun WearTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = remember(context) { dynamicColorScheme(context) ?: ColorScheme() }
    MaterialTheme(colorScheme = colorScheme) {
        AppScaffold { content() }
    }
}

/** Android 13+ se na notifikace ptá za běhu; hodinky se zeptají jednou, hned po přihlášení. */
@Composable
private fun NotificationPermissionRequest() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
