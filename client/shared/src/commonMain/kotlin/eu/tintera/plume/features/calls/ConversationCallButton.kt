package eu.tintera.plume.features.calls

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.CallPhase
import eu.tintera.plume.core.telegram.CallRepository
import eu.tintera.plume.core.telegram.ChatInfo
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.features.conversation.CallRequest
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.call_24dp
import eu.tintera.plume.resources.call_start
import eu.tintera.plume.resources.call_start_video
import eu.tintera.plume.resources.videocam_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Hovor z hlavičky konverzace: sluchátko v akcích lišty, ťuknutí nabídne hlasový hovor
 * a videohovor (když ho protějšek přijímá; jinak volá rovnou). Jen v soukromém a tajném chatu
 * s člověkem, který hovory přijímá ([ChatInfo.callUserId]), a jen když zrovna žádný hovor neběží.
 *
 * Vyřizuje i zavolání zpátky ze zprávy o hovoru ([request]) — vytočení potřebuje dotaz na
 * mikrofon a kameru, a to umí jen kompozice, ne ViewModel. Je ve vlastní funkci, ne
 * v `ConversationScreen`: ta je v dexu na hraně (viz CLAUDE.md, Gotchas).
 */
@Composable
fun ConversationCallButton(info: ChatInfo?, request: CallRequest?, onRequestHandled: () -> Unit) {
    val userId = info?.callUserId
    val start = rememberStartCall(userId) ?: return
    val calls = koinInject<CallRepository>()
    val active by calls.active.collectAsStateWithLifecycle()
    val busy = active?.phase?.let { it != CallPhase.Ended && it != CallPhase.Failed } == true

    LaunchedEffect(request) {
        if (request == null) return@LaunchedEffect
        onRequestHandled()
        if (!busy) start(request.video)
    }
    if (busy) return

    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { if (info?.callSupportsVideo == true && calls.supportsVideo) open = true else start(false) },
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(painterResource(Res.drawable.call_24dp), contentDescription = stringResource(Res.string.call_start))
        }
        ExpressiveMenu(
            expanded = open,
            onDismiss = { open = false },
            groups = listOf(
                listOf(
                    MenuAction(stringResource(Res.string.call_start), Res.drawable.call_24dp, onClick = {
                        open = false
                        start(false)
                    }),
                    MenuAction(stringResource(Res.string.call_start_video), Res.drawable.videocam_24dp, onClick = {
                        open = false
                        start(true)
                    }),
                ),
            ),
        )
    }
}
