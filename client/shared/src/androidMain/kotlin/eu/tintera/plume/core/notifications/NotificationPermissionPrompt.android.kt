package eu.tintera.plume.core.notifications

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import eu.tintera.plume.core.telegram.CallRepository
import eu.tintera.plume.core.ui.ConfirmDialog
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.call_24dp
import eu.tintera.plume.resources.full_screen_calls_prompt_open
import eu.tintera.plume.resources.full_screen_calls_prompt_text
import eu.tintera.plume.resources.full_screen_calls_prompt_title
import eu.tintera.plume.resources.notification_permission_later
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
actual fun NotificationPermissionPrompt() {
    // Před Androidem 13 se o nic nežádá.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val permission = koinInject<NotificationPermission>()
    val calls = koinInject<CallRepository>()
    var showRationale by remember { mutableStateOf(false) }
    var showFullScreen by remember { mutableStateOf(false) }
    // Po odpovědi na systémový dotaz se znovu zjistí, jestli je ještě o co žádat.
    var checks by remember { mutableIntStateOf(0) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { checks++ /* Odmítnutí nic nerozbije; zapnout to jde v Nastavení. */ }

    LaunchedEffect(Unit) {
        // Ptáme se jen jednou. Když uživatel řekne „Teď ne", další cesta vede přes Nastavení —
        // opakované vyskakování dialogu by bylo otravné a stejně by skončilo odmítnutím.
        if (!permission.isGranted() && !permission.wasAsked()) showRationale = true else checks++
    }

    /*
     * Celoobrazovková výzva hovoru až s povolenými notifikacemi: bez nich by stejně nebylo co
     * ukázat. Plume z Play ji od Androidu 14 nemusí mít (deklarace v Play Console) a bez ní
     * zamčený telefon při hovoru jen zvoní — kdo do Nastavení nezajde, nedozvěděl by se to.
     * Taky jen jednou; potom je stav v Nastavení → Notifikace.
     */
    LaunchedEffect(checks) {
        if (checks == 0) return@LaunchedEffect
        showFullScreen = calls.supported && permission.isGranted() &&
            permission.fullScreenCallsAllowed() == false && !permission.wasAskedFullScreen()
    }

    if (showRationale) {
        NotificationRationaleDialog(
            onEnable = {
                permission.markAsked()
                showRationale = false
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onDismiss = {
                permission.markAsked()
                showRationale = false
            },
        )
    } else if (showFullScreen) {
        ConfirmDialog(
            title = stringResource(Res.string.full_screen_calls_prompt_title),
            text = stringResource(Res.string.full_screen_calls_prompt_text),
            confirmLabel = stringResource(Res.string.full_screen_calls_prompt_open),
            onConfirm = {
                permission.markAskedFullScreen()
                showFullScreen = false
                permission.openFullScreenCallSettings()
            },
            onDismiss = {
                permission.markAskedFullScreen()
                showFullScreen = false
            },
            icon = Res.drawable.call_24dp,
            dismissLabel = stringResource(Res.string.notification_permission_later),
        )
    }
}
