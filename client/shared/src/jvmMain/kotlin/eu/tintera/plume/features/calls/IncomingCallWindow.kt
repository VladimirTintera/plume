package eu.tintera.plume.features.calls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import eu.tintera.plume.SettingsTheme
import eu.tintera.plume.core.calls.CallRequests
import eu.tintera.plume.core.platform.DesktopWindow
import eu.tintera.plume.core.telegram.ActiveCall
import eu.tintera.plume.core.telegram.CallPhase
import eu.tintera.plume.core.telegram.CallRepository
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.call_24dp
import eu.tintera.plume.resources.call_answer
import eu.tintera.plume.resources.call_incoming
import eu.tintera.plume.resources.notification_call_decline
import eu.tintera.plume.resources.videocam_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Okénko příchozího hovoru na desktopu: malé okno vždy navrchu, nahoře uprostřed obrazovky,
 * dokud hovor zvoní a hlavní okno není vpředu. Tak to dělá i Telegram Desktop.
 *
 * - **Proč ne jen oznámení systému**: na macOS je to banner, který za pár vteřin odjede,
 *   a tlačítka schovává pod „Volby"; trvalé a s tlačítky ho dělá jen styl „Výstrahy", který
 *   si volí uživatel a který by platil i pro zprávy. Oznámení zůstává jako záznam v Centru
 *   oznámení, okénko je to, na co se kliká.
 * - **Nahoře uprostřed**, ne v rohu: v pravém horním rohu ukazuje oznámení macOS, v pravém
 *   dolním Windows — okénko by se s nimi překrylo.
 * - **Nebere fokus** (`focusable = false`): vyskočí uprostřed psaní v jiné aplikaci a stisky
 *   kláves mají jít dál tam, kam šly.
 * - **Přijmout** vytáhne hlavní okno a hovor přijme obrazovka hovoru v něm ([CallRequests],
 *   stejně jako z oznámení) — ovládání hovoru je tam. **Odmítnout** hovor zavěsí a okénko zmizí.
 */
@Composable
fun IncomingCallWindow() {
    val calls = koinInject<CallRepository>()
    if (!calls.supported) return
    // `collectAsState`, ne `…WithLifecycle`: tady ještě nejsme v okně a `application {}`
    // LifecycleOwner nemá — `collectAsStateWithLifecycle` by shodil celou aplikaci při startu.
    val call by calls.active.collectAsState()
    val focused by DesktopWindow.focused.collectAsState()
    val ringing = call?.takeIf { it.phase == CallPhase.Ringing } ?: return
    if (focused) return

    val state = rememberWindowState(
        position = WindowPosition.Aligned(Alignment.TopCenter),
        width = WINDOW_WIDTH,
        height = WINDOW_HEIGHT,
    )
    Window(
        onCloseRequest = {},
        state = state,
        title = stringResource(Res.string.call_incoming),
        undecorated = true,
        transparent = true,
        resizable = false,
        focusable = false,
        alwaysOnTop = true,
    ) {
        SettingsTheme {
            IncomingCallPanel(ringing, calls)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IncomingCallPanel(call: ActiveCall, calls: CallRepository) {
    val requests = koinInject<CallRequests>()
    val scope = rememberCoroutineScope()
    val (name, avatarFileId) = rememberPeer(call.userId)
    // Okraj kolem plochy, ať se do průhledného okna vejde stín.
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxSize().padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Avatar(chatId = call.userId, title = name, avatarFileId = avatarFileId, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    callStatus(call),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HangUpButton(stringResource(Res.string.notification_call_decline), large = false) {
                scope.launch { calls.hangUp() }
            }
            FilledIconButton(
                onClick = {
                    requests.answer(call.id)
                    DesktopWindow.activate()
                },
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
            ) {
                Icon(
                    painterResource(if (call.isVideo) Res.drawable.videocam_24dp else Res.drawable.call_24dp),
                    contentDescription = stringResource(Res.string.call_answer),
                    modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                )
            }
        }
    }
}

private val WINDOW_WIDTH = 420.dp
private val WINDOW_HEIGHT = 112.dp
